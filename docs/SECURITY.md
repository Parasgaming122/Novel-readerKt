# Security Model & Privacy Considerations

Documents the Novel Reader app's security architecture, data protection mechanisms, and privacy trade-offs.

---

## Table of Contents

1. [Encryption](#1-encryption)
2. [Code Obfuscation](#2-code-obfuscation)
3. [Network Security](#3-network-security)
4. [API Key Management](#4-api-key-management)
5. [WebView Security](#5-webview-security)
6. [Data Storage](#6-data-storage)
7. [Permissions Justification](#7-permissions-justification)
8. [Known Security Considerations](#8-known-security-considerations)

---

## 1. Encryption

### Backup Encryption (AES-256-CBC)

The app encrypts database backups using **AES-256-CBC** with hardware-backed keys stored in Android's **KeyStore** system.

**Implementation details:**

| Property | Value |
|----------|-------|
| **Algorithm** | AES-256-CBC |
| **Key storage** | `AndroidKeyStore` (hardware-backed on supported devices) |
| **Key alias** | `"wtr_backup_key"` |
| **Key protection** | Hardware-backed TEE/StrongBox (device-dependent) |
| **Key export** | Never — the key material never leaves secure hardware |

### How It Works

1. **Key generation:** On first use, an AES-256 key is generated inside `AndroidKeyStore`. The key is bound to the device and cannot be extracted.
2. **Encryption:** Backup data is encrypted using AES-CBC with a randomly generated IV (initialization vector). The IV is stored alongside the encrypted data.
3. **Streaming:** Encryption and decryption use streaming buffers (`CipherInputStream` / `CipherOutputStream`) for memory efficiency — the entire backup is never held in memory at once.
4. **Decryption:** The stored IV and KeyStore-backed key are used to decrypt. Decryption fails if the key has been corrupted or the device has been factory-reset.

### Graceful Fallback

If encryption fails for any reason (KeyStore corruption, hardware issue, etc.), the app falls back to **plaintext** backups rather than crashing or losing user data. A warning is logged to `WtrLogManager`.

> **Source:** `BackupEncryption.kt`

---

## 2. Code Obfuscation

### R8/ProGuard Minification

Release builds enable R8 full mode, which performs:

- **Shrinking** — removes unused classes, methods, and fields
- **Optimization** — inlines methods, removes dead code paths
- **Obfuscation** — renames classes, methods, and fields to short names

### Custom Keep Rules

Three keep rules are defined in `app/proguard-rules.pro` to prevent obfuscation of classes that are accessed via reflection or dynamic lookup:

| Rule | Target | Reason |
|------|--------|--------|
| `@JavascriptInterface` methods | All classes | WebView JS bridge must find methods by name |
| `com.example.data.**` | Room entities, DAOs, Repository | Room uses reflection for entity/DAO resolution |
| `WtrLogManager` | Entire class | Referenced dynamically in crash report handler |

### Consumer Rules

Third-party libraries (Retrofit, Moshi, OkHttp) bundle their own ProGuard consumer rules inside their AAR files. These are automatically applied by R8 and handle:
- Retrofit interface proxy generation
- Moshi JSON adapter class lookup
- OkHttp interceptor chain reflection

### Source File Retention

```proguard
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
```

Crash reports include meaningful stack traces with file names and line numbers (the source file attribute is renamed but retained), making debugging production issues possible.

---

## 3. Network Security

### Cleartext Traffic & Network Security Configuration

The app utilizes a custom `network_security_config.xml` to specify its cleartext traffic permissions.

**Why:** Many novel hosting websites use HTTP (not HTTPS). Enforcing HTTPS globally would break the core browsing functionality of the app when users access these sites.

**Configuration Details:**
- **Base Config:** Cleartext traffic is permitted as a base configuration (`cleartextTrafficPermitted="true"`) to allow compatibility with non-HTTPS novel sites.
- **Enforced Security Domains:** Cleartext traffic is strictly *disabled* (`cleartextTrafficPermitted="false"`) for `google.com` and `googleapis.com` (and their subdomains). This ensures all communication with Google Translate proxy and the Gemini API is encrypted via HTTPS.

**Implications:**
- HTTP traffic to third-party novel sites is transmitted in plaintext and could be intercepted. This is an intentional trade-off for compatibility.
- Any API key exchanges or translation payloads sent to Google/Gemini are mathematically forced to use HTTPS transit, eliminating transit eavesdropping on credentials.

### Certificate Pinning

The app does **not** implement certificate pinning.

**Why:** Novel sites frequently change certificates, use CDNs, and may use self-signed certificates. Pinning would cause constant connection failures.

### WebView HTTPS Enforcement

The app does not force HTTPS in WebViews. This allows users to browse both HTTP and HTTPS novel sites without redirect errors.

### Google Translate Proxy

The Google Translate proxy endpoint uses **HTTPS**, ensuring that translation requests are encrypted in transit.

---

## 4. API Key Management

### Gemini API Key Storage

| Stage | Mechanism | Security Level |
|-------|-----------|---------------|
| **Source control** | `.env` file (git-ignored) | Not committed |
| **Build time** | Secrets Gradle Plugin reads `.env` | Process memory only |
| **Compiled APK** | `BuildConfig.GEMINI_API_KEY` (string constant) | Embedded in DEX (obfuscated in release) |
| **Runtime** | `SecurePreferences` (`EncryptedSharedPreferences`) | Android OS hardware-backed Keystore encrypted |
| **Settings UI** | Show/hide toggle | User-controlled visibility |

### Protection Measures

1. **`.gitignore`** includes `.env` — the key is never pushed to version control.
2. **R8 obfuscation** renames the `BuildConfig` class and field in release builds, making static analysis slightly harder.
3. **Hardware Storage Encryption**: The key entered by the user at runtime is handled by `SecurePreferences.kt` via **Jetpack EncryptedSharedPreferences** using the `AES256_SIV_SCHEME` for keys and `AES256_GCM_SCHEME` for values, backed by standard Android KeyStore.
4. **Auto-Migration Pipeline**: Standard unencrypted `SharedPreferences` values are cleanly read on first launch, saved into encrypted preferences, and deleted from unencrypted storage to eliminate traces.
5. **No ADB/Cloning Backups**: The app disables backups (`android:allowBackup="false"`) in the manifest to completely block attackers from extracting private SQLite or API keys via ADB backup extraction.

### Limitations

- The API key fallback is ultimately a string constant embedded in the APK. A determined attacker can extract it via decompilation (even with R8 obfuscation).
- Rooted devices can observe runtime memory, although the persistent storage is safe from other software using normal sandbox access.

---

## 5. WebView Security

### JavaScript Bridge

The app exposes `WtrWebAppInterface` to JavaScript running inside WebViews via `@JavascriptInterface` annotations. This allows web content to invoke native Android methods (e.g., reporting reading progress, triggering TTS).

### Mitigations

| Mitigation | Description |
|-----------|-------------|
| **Tab-scoped isolation** | Each browser tab has a unique `tabId`. JS callbacks are validated against the active tab's ID before processing. A malicious script in one tab cannot impersonate another tab. |
| **URL validation** | The app validates the source URL before accepting JS callbacks. Callbacks from unexpected origins are ignored. |
| **Minimal interface surface** | Only the methods explicitly annotated with `@JavascriptInterface` are exposed. No other native methods are accessible from JavaScript. |
| **Ad-blocker** | The built-in ad-blocker reduces the attack surface by preventing ad network scripts from loading in the first place. |

### Residual Risk

The WebView JS bridge is inherently a potential XSS surface. If a novel site is compromised and serves malicious JavaScript, the exposed bridge methods could be called. The tab isolation and URL validation mitigate but do not eliminate this risk.

---

## 6. Data Storage

All persistent data is stored in app-private locations, inaccessible to other apps without root access.

| Data | Storage Location | Auto-Cleanup |
|------|-----------------|--------------|
| **Room database** (bookmarks, history, tabs) | Internal storage (`getDatabasePath()`) | No — persistent |
| **SharedPreferences** (general settings) | Internal storage (`getSharedPreferences()`) | No — persistent |
| **EncryptedSharedPreferences** (Gemini API key) | Private cryptographic keystore-backed storage | No — persistent |
| **Crash reports** | Internal storage (dedicated directory) | Yes — 7-day auto-cleanup |
| **Static asset cache** | `cacheDir` | Yes — system can reclaim when space is low |
| **TTS progress** | SharedPreferences (separate file) | No — cleared when tab is closed |

### Backup Data

- Encrypted backup files are stored in the user-chosen export location (typically Downloads or a user-selected directory via SAF).
- The app **cannot** access files outside its sandbox without explicit user permission (Storage Access Framework).

### Data Extraction Rules

The app declares `android:allowBackup="false"` in `AndroidManifest.xml` to completely prevent ADB and physical data cloning or extraction of sensitive components (such as private databases or encrypted SharedPreferences keystores).

---

## 7. Permissions Justification

The app requests the following permissions in `AndroidManifest.xml`:

| Permission | Protection Level | Justification |
|-----------|-----------------|---------------|
| **`INTERNET`** | Normal | Core functionality — browsing novel websites, fetching chapter content, TTS network requests, translation API calls |
| **`FOREGROUND_SERVICE`** | Normal | Required to run the text-to-speech (TTS) engine as a foreground service so it is not killed by the OS during playback |
| **`FOREGROUND_SERVICE_MEDIA_PLAYBACK`** | Normal | Android 14+ requires specifying the foreground service type. `mediaPlayback` is the correct type for audio/TTS services and triggers a media-style notification |
| **`WAKE_LOCK`** | Normal | Keeps the CPU awake during TTS playback to prevent audio from cutting out when the screen turns off |
| **`POST_NOTIFICATIONS`** | Dangerous (runtime) | Required on Android 13+ to display the media controls notification for TTS playback (play/pause/stop) |

### No Other Permissions

The app does **not** request:
- Storage permissions (uses SAF for file exports)
- Location permissions
- Camera permissions
- Microphone permissions
- Contact/phone permissions

---

## 8. Known Security Considerations

### Acceptable Trade-offs

These are intentional decisions made for app functionality:

| Consideration | Trade-off | Rationale |
|--------------|-----------|-----------|
| **Cleartext traffic enabled** | HTTP traffic is interceptable | Many novel sites don't support HTTPS. Breaking these sites would defeat the app's purpose. |
| **No certificate pinning** | Man-in-the-middle possible | Novel sites use varied CDNs and certificates. Pinning would cause constant connection failures. Acceptable risk for a reader app (not handling financial data). |
| **API key embedded in APK** | Key extractable via decompilation | Mitigated by R8 obfuscation. For production, a backend proxy is recommended. |

### Residual Risks

These are areas where improvements could reduce security risk:

| Risk | Severity | Mitigation Status |
|------|----------|-------------------|
| **WebView XSS via JS bridge** | Medium | Partially mitigated by tab-scoped isolation, URL validation, and ad-blocker. Full mitigation would require per-origin capability grants. |
| **Crash reports may contain sensitive URLs** | Low | Crash reports include recent log entries which could contain URLs of novel chapters being read. Logs auto-delete after 7 days. Consider scrubbing URLs from crash reports. |
| **Plaintext fallback for backups** | Low | If AndroidKeyStore fails, backups are saved unencrypted. This prevents data loss but exposes backup contents on disk. |
| **R8 keep rules expose class names** | Low | The `com.example.data.**` keep rule prevents obfuscation of the entire data layer. More granular rules could reduce exposure. |

### Recommendations & Hardening Status

The following list tracks the security status of core recommendations:

1. **Jetpack EncryptedSharedPreferences (DONE)** — Implemented `SecurePreferences` for standard API key runtime encryption-at-rest. Handles legacy cleartext migration transparently.
2. **Network Security Configuration (DONE)** — Created `network_security_config.xml` to lock down Gemini and Google domains to strict HTTPS transport while white-listing HTTP cleartext for unencrypted third-party novel sites.
3. **Backup Protection Tuning (DONE)** — Configured `android:allowBackup="false"` to prevent backup exploits.
4. **WebView Sandbox Tuning (DONE)** — Disabled file and content access (`allowFileAccess = false`, `allowContentAccess = false`) and switched to `WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE`.
5. **WebView Sandbox (FUTURE)** — Consider using Android's `WebViewAssetLoader` for local content and stricter origin checks.
6. **Proguard rule refinement** — Replace the broad `com.example.data.**` keep rule with specific class-level rules.
7. **URL scrubbing in crash reports** — Redact or hash URLs before including them in crash log files.
