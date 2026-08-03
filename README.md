<div align="center">

# Novel Reader

### Premium Web Novel Browser & Foreground Speech Engine

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://github.com/Parasgaming122/Novel-readerKt)
[![API 24+](https://img.shields.io/badge/Min_SDK-24_(Android_7.0)-4FC3F7?logo=android&logoColor=white)](https://developer.android.com/about/versions/marshmallow/android-7.0)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-2024.09.00-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-TBD-lightgrey)](#license)

An advanced, high-performance Android web browser purpose-built for reading, auto-translating, and listening to web novels globally. Converts standard web chapter paragraphs into sequential audio nodes via a bidirectional JavaScript-to-Native TTS bridge — complete with lockscreen media controls, AI-powered translation, ad blocking, and encrypted backup.

[Repository](https://github.com/Parasgaming122/Novel-readerKt) · [Documentation](#-technical-documentation) · [Build Instructions](#-build--run) · [Supported Sites](#-supported-websites)

</div>

---

## Table of Contents

- [Overview](#-overview)
- [Features](#-features)
  - [Reading Experience](#-reading-experience)
  - [Speech Engine & Audio](#-speech-engine--audio)
  - [Translation](#-translation)
  - [Browser & Navigation](#-browser--navigation)
  - [Privacy & Security](#-privacy--security)
  - [Diagnostics & Reliability](#-diagnostics--reliability)
- [Supported Websites](#-supported-websites)
- [Architecture](#-architecture)
- [Tech Stack & Dependencies](#-tech-stack--dependencies)
- [Project Structure](#-project-structure)
- [Build & Run](#-build--run)
- [CI/CD](#-cicd)
- [Permissions](#-permissions)
- [Technical Documentation](#-technical-documentation)
- [License](#-license)

---

## Overview

Novel Reader is a native Android application built with **Jetpack Compose**, **Kotlin Coroutines / Flow**, and **Android WebKit**. It bridges the gap between static web-based content and rich, fluid media-player-like playback for web novels.

By establishing a bidirectional JavaScript-to-Native synchronization bridge, Novel Reader converts standard web chapter paragraphs into sequential audio nodes, supported by lockscreen controls, smart autoscroll pagination, secure automated translation routing, and an active resource-filtering ad-blocker. The app targets readers of translated Chinese, Korean, and Japanese web novels who need a dedicated, ad-free, and offline-capable reading experience with TTS playback.

---

## Features

### Reading Experience

#### 1. Visual Themes — 6 Reading Profiles
Six carefully crafted color schemes implemented via **Material Design 3** dynamic theming:

| Profile | Description | Best For |
|---------|-------------|----------|
| **Dark** | Pure dark background with light text | Night reading, AMOLED displays |
| **Grey** | Reduced-brightness dark grey | Low-light indoor reading |
| **White** | Clean white background | Daytime reading |
| **Sepia** | Warm parchment tones | Extended reading sessions |
| **Forest** | Deep green tones | Natural, eye-friendly reading |
| **Ocean** | Cool blue tones | Relaxed, immersive reading |

Themes are applied system-wide — including the browser content area via CSS injection — for a consistent reading environment.

#### 2. Smart URL Bar
The address bar doubles as a search-and-navigate powerhouse:
- **Keyword shortcuts**: Type `wtr`, `wn`, `fn`, etc. to jump directly to supported novel sites
- **Search engine integration**: Non-URL queries are routed to the configured search engine
- **Auto-detection**: Recognizes 11 supported novel domains and provides contextual suggestions

### Speech Engine & Audio

#### 3. Bidirectional JS-to-Native TTS
The core innovation of Novel Reader — a **WebKit `speechSynthesis` polyfill** that bridges browser-based TTS calls to the native **Android TextToSpeech** engine:

```
┌──────────────────┐         ┌──────────────────────┐
│   WebView JS     │ ◄─────► │  WtrWebAppInterface  │
│  speechSynthesis  │  Bridge  │  (JavascriptInterface) │
│    polyfill       │         └──────────┬───────────┘
└──────────────────┘                    │
                                  ┌─────▼─────┐
                                  │  Android   │
                                  │  TTS Engine│
                                  └───────────┘
```

- Replaces browser-native speech APIs with a fully controllable Android TTS pipeline
- Supports play, pause, resume, stop, and paragraph-by-paragraph navigation
- Dynamic language detection with engine re-initialization for multilingual content

#### 4. Background Audio with MediaSession
A dedicated **foreground service** (`WtrBrowserService`) ensures uninterrupted audio playback:

- **Lockscreen controls**: Full MediaSession integration with play/pause/next/prev on lock screens and notification shade
- **Wake lock**: `PARTIAL_WAKE_LOCK` prevents CPU sleep during playback
- **Wi-Fi lock**: `WIFI_MODE_FULL_HIGH_PERF` maintains network connectivity for streaming chapters
- **Notification throttling**: Media notification updates gated at 1.5s intervals to prevent ANR
- **Tab-scoped isolation**: Switching tabs preserves ongoing TTS playback on the source tab

### Translation

#### 5. Google Translate Auto-Translation
Automatic proxy-based translation for Chinese novel sites:
- **URL rewriting**: Intercepts requests and routes through Google Translate proxy
- **Domain matching**: Automatically activates for supported Chinese/foreign novel domains (TimoTxt, Novel543, Twkan)
- **Page integration**: Translated content is injected seamlessly into the reading view

#### 6. Gemini AI Translation (NoveLM Style)
Contextual, high-fidelity literary novel translation powered by **Google Gemini 2.5 Flash**:

- **Literary-Level Localization (NoveLM Style)**: Uses an advanced literary translation pipeline that converts dry literal text to fluid, emotionally evocative prose. Specialized for Chinese webnovel genres (Xianxia, Wuxia, Xuanhuan, Danmei, LitRPG).
- **Genre & Idiom Translation**: Translates complex Chinese idioms (Chengyu like 画蛇添足), titles, sects, and cultivation realms consistently (e.g. converting "Dou Qi", "Dantian", or "Nascent Soul" instead of raw transliterations).
- **Bracket & Layout Preservation**: Maintains unique layout formatting features (such as bracket structures `【】` and `『』`) and accurately scales huge numeral figures (such as 万 and 亿) to Western notations.
- **Secure Encrypted Storage**: The API key is stored with system hardware-backed encryption at rest via `EncryptedSharedPreferences` to maximize privacy.
- **Smart Activation**: Translates only on novel chapter pages dynamically. Metadata pages, catalog pages, and search engines leverage standard Google Translate routing to conserve API limit space.
- **TTS Integration**: Intercepts extracted translation nodes and feeds them directly to the native TextToSpeech speech synthesizer loop for seamless, hands-free listening.

### Browser & Navigation

#### 7. Ad-Blocker
Network-level ad blocking via **WebView request interception**:
- Blocks **15+ major ad networks** including Google Ads, Cloudflare challenges, and tracking scripts
- Operates at the `shouldInterceptRequest` level for zero-overhead filtering
- Customizable per-site — the Wtr-Lab bridge system is always preserved to avoid triggering the site's anti-adblock defenses
- Significant bandwidth savings and faster page loads on ad-heavy novel sites

#### 8. Tab Grid Manager
Full-featured tab management system:
- **Tab grouping**: Organize tabs into labeled folders/groups
- **Desktop mode toggle**: Per-tab switch between mobile and desktop user agents
- **Visual grid layout**: Double-grid UI for managing standalone tabs and nested groups
- **Navigation history**: Per-tab history stack with back gesture support

#### 9. Static Asset Caching
SHA-256 based local caching for `wtr-lab.com` assets:
- Intercepts static resource requests (`.js`, `.css`, `.png`, `.woff`, `.woff2`, `.ttf`)
- Caches to `cacheDir/wtr_static_cache` with content-hash verification
- Dramatically speeds up tab switching and chapter navigation on the primary site
- Transparent — cached resources load instantly without network round-trips

### Privacy & Security

#### 10. Backup/Restore with AES-256 Encryption
Full app state backup with military-grade encryption:
- **AES-256 encryption**: Keys generated and stored in hardware-backed **Android KeyStore**
- **Streaming JSON parser**: Memory-safe `StreamingJsonParser.kt` processes large backups (<10MB footprint even for 100MB+ files)
- **Comprehensive**: Backs up settings, history, bookmarks, and tabs
- **SAF integration**: Uses Storage Access Framework for user-controlled file picking
- **Integrity validation**: `BrowserRepository.validateDatabaseIntegrity()` verifies restored data

#### 11. Secure API Key Storage and Encryption-at-Rest
Hardware-backed encryption-at-rest for sensitive user data:
- **EncryptedSharedPreferences**: Sensitive fields like the optional user-provided Gemini API key are stored in hardware/keystore-backed `EncryptedSharedPreferences` rather than unencrypted cleartext XML files.
- **Auto-Migration**: Secure preferences automatically scan, migrate, and erase any legacy cleartext XML configs on first boot.
- **Anti-Extraction Hardening**: System-level cloud backups (`android:allowBackup="false"`) are completely disabled, heavily locking down the app sandbox from ADB physical extraction or data cloning attempts.

#### 12. Novel Bookmarks with Progress Tracking
Intelligent bookmarking for novel readers:
- **Auto-detection**: Automatically identifies novel pages vs. standard web pages
- **Progress tracking**: Saves reading position, last chapter visited, and timestamp
- **Chapter parsing**: Regex-based chapter title extraction for accurate progress display
- **Quick access**: Dedicated bookmarks panel with novel-specific metadata

### Diagnostics & Reliability

#### 13. In-App Diagnostics
Comprehensive diagnostic toolkit for troubleshooting:
- **Log viewer**: In-app popup displaying a **100-entry ring buffer** of system actions, page loads, and audio events
- **Crash reports**: Automatic crash log capture with **7-day retention** in private app storage
- **Export**: Save diagnostic logs as `.txt` files via Storage Access Framework

#### 14. Performance Monitoring
Real-time heap monitoring:
- Background thread loop validates system RAM consumption
- Triggers automatic `System.gc()` when heap reaches **95% utilization**
- Prevents OOM (Out of Memory) crashes on memory-constrained devices

#### 15. Network Retry with Exponential Backoff
Generic retry wrapper for resilient network operations:
- Exponential backoff algorithm for transient failures
- Configurable retry count and base delay
- Applied to translation requests, page loads, and API calls

#### 16. Anti-CAPTCHA Delay
Protection against automated translation blocking:
- **4.5-second configurable delay** between translated page loads during TTS auto-advance
- Prevents Google Translate from triggering CAPTCHA challenge screens
- Toast notifications inform users during active delays
- Toggle via `anti_captcha_delay` preference

---

## Supported Websites

Novel Reader has specialized scraper logic, TTS integration, and translation support for **11 novel websites**:

| Site | Domain(s) | Type | Features |
|------|-----------|------|----------|
| **Wtr-Lab** | `wtr-lab.com`, `wtr-lab.co` | Primary | Deep JS bridge, asset caching, ad-blocker bypass |
| **WebNovel** | `webnovel.com` | English/Translated | Dynamic container extraction, viewport-aware start position |
| **NovelHall** | `novelhall.com` | English | CSS selector extraction |
| **FanMTL** | `fanmtl.com` | Translated | Standard paragraph extraction |
| **NovelBin** | `novelbin.com` | English | Standard paragraph extraction |
| **FreeWebNovel** | `freewebnovel.com` | English | Standard paragraph extraction |
| **TimoTxt** | `timotxt.com` | Chinese (Auto-translate) | Google Translate proxy, junk filtering |
| **Novel543** | `novel543.com` | Taiwanese (Auto-translate) | Google Translate proxy, junk filtering |
| **Twkan** | `twkan.com` | Chinese (Auto-translate) | Google Translate proxy, junk filtering |
| **NovelHub** | `novelhub.net` | English | `#chr-content` / `.chapter-content` extraction |
| **NovelHubApp** | `novelhubapp.com` | English | Single-page reader, hash-based tracking |

> **Note**: Sites marked "Auto-translate" automatically route through Google Translate or Gemini AI for in-app translation. The Gemini AI translator activates only on chapter URLs — catalog and search pages are never sent to the API.

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                    MainActivity                       │
│  Edge-to-Edge │ Permissions │ WebView Pool           │
└──────────────────────┬──────────────────────────────┘
                       │
         ┌─────────────┼──────────────┐
         ▼             ▼               ▼
  BrowserViewModel  WtrLogManager  CrashReportManager
  (MVVM State)     (Ring Buffer)  (UncaughtHandler)
         │
    ┌────┴────────────────────┐
    ▼                         ▼
 AppDatabase              WebsiteSupportRegistry
 (Room v4)                 (11 site implementations)
    │
    ▼
 BrowserRepository → BrowserDao
```

### Layer Breakdown

| Layer | Component | Responsibility |
|-------|-----------|----------------|
| **UI** | `BrowserAppScreen.kt`, `TabsPanel.kt`, `BookmarksPanel.kt`, etc. | Jetpack Compose screens, Material 3 theming, user interactions |
| **ViewModel** | `BrowserViewModel.kt` | MVVM state management, tab operations, search, backup orchestration |
| **Service** | `WtrBrowserService.kt` | Foreground TTS service with MediaSession, wake/Wi-Fi locks |
| **Bridge** | `WtrWebAppInterface.kt`, `WtrAudioControlBridge.kt` | Bidirectional JS ↔ Native communication, global audio state |
| **Data** | `AppDatabase.kt`, `BrowserDao.kt`, `BrowserRepository.kt` | Room persistence for tabs, history, bookmarks |
| **Sites** | `WebsiteSupportRegistry.kt`, `WebsiteSupportImpls.kt` | Per-site scraper logic, URL matching, paragraph extraction |
| **Engine** | `GeminiTranslator.kt`, `BackupEncryption.kt`, `PerformanceMonitor.kt` | AI translation, encryption, monitoring |

### Key Design Patterns

- **MVVM**: `BrowserViewModel` manages all UI state via `StateFlow`, collected in Compose with `collectAsStateWithLifecycle()`
- **WebView Pool**: Global `activeWebViewsPool` in `MainActivity` manages WebView instances to prevent context leaks
- **Streaming Parser**: `StreamingJsonParser.kt` uses Android `JsonReader` pull-parsing for memory-safe backup imports
- **Ring Buffer Logging**: `WtrLogManager` maintains a capped 100-entry in-memory log with background disk serialization

---

## Tech Stack & Dependencies

### Build Configuration

| Tool | Version |
|------|---------|
| **AGP** | 9.1.1 |
| **Kotlin** | 2.2.10 |
| **KSP** | 2.3.5 |
| **JDK** | 17 |
| **Min SDK** | 24 (Android 7.0) |
| **Target SDK** | 36 |
| **Compile SDK** | 36 |

### Core Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| **Compose BOM** | 2024.09.00 | Jetpack Compose dependency alignment |
| **Material 3** | (via BOM) | UI components, dynamic theming |
| **Room** | 2.7.0 | Local SQLite database (tabs, history, bookmarks) |
| **Generative AI** | 0.9.0 | Google Gemini 2.5 Flash for AI translation |
| **OkHttp** | 4.10.0 | HTTP client, interceptors, connection pooling |
| **Retrofit** | 2.12.0 | REST API client with Moshi converter |
| **Moshi** | 1.15.2 | JSON serialization/deserialization |
| **Coil** | 2.7.0 | Image loading for favicons and thumbnails |
| **Kotlinx Coroutines** | 1.10.2 | Async concurrency (Android + Core) |
| **Lifecycle** | 2.8.7 | ViewModel, runtime, Compose integration |
| **Activity Compose** | 1.10.1 | Compose-aware Activity |

### Build Plugins

| Plugin | Purpose |
|--------|---------|
| `com.android.application` (9.1.1) | Android app build |
| `org.jetbrains.kotlin.plugin.compose` (2.2.10) | Compose compiler |
| `com.google.devtools.ksp` (2.3.5) | Room & Moshi annotation processing |
| `secrets-gradle-plugin` (2.0.1) | `.env` file-based API key management |

---

## Project Structure

```
app/src/main/java/com/example/
├── MainActivity.kt                  # Entry point, permissions, WebView pool
├── BrowserViewModel.kt              # MVVM state management, backup logic
├── BrowserSection.kt                # Navigation section enum
├── WtrBrowserService.kt             # Foreground TTS service (MediaSession)
├── WtrWebAppInterface.kt             # JS ↔ Native bridge (@JavascriptInterface)
├── WtrAudioControlBridge.kt         # Global audio state mediator
├── WtrLogManager.kt                 # Telemetry logging (100-entry ring buffer)
├── BackupEncryption.kt              # AES-256 KeyStore encryption
├── StreamingJsonParser.kt            # Memory-safe JSON pull parser
├── CrashReportManager.kt            # Uncaught exception handler (7-day retention)
├── PerformanceMonitor.kt            # Heap monitoring, auto-GC at 95%
├── NetworkErrorHandler.kt           # Exponential backoff retry wrapper
├── GeminiTranslator.kt              # Google Gemini 2.5 Flash AI translation
├── data/                            # Room database layer
│   ├── AppDatabase.kt               # Room database configuration
│   ├── BookmarkEntry.kt             # Novel/website bookmark entity
│   ├── BrowserDao.kt                # Data access object (queries)
│   ├── BrowserRepository.kt         # Repository pattern wrapper
│   ├── HistoryEntry.kt              # Browsing history entity
│   └── TabEntry.kt                  # Browser tab entity
├── sites/                           # Website support system
│   ├── WebsiteSupport.kt            # Support interface definition
│   ├── WebsiteSupportImpls.kt       # 11 site implementations
│   ├── WebsiteSupportRegistry.kt    # Central domain → impl registry
│   └── commons/
│       └── Commons.kt                # Shared CSS selectors & patterns
└── ui/                              # Jetpack Compose UI
    ├── BrowserAppScreen.kt           # Main screen (3226 lines)
    ├── BookmarksPanel.kt             # Novel & website bookmarks
    ├── HistoryPanel.kt               # Browsing history
    ├── SettingsPanel.kt              # 5-section settings (speech, display, privacy, etc.)
    ├── TabsPanel.kt                  # Tab grid with grouping
    ├── ChromeNewTabPage.kt           # New tab page (shortcuts, recent history)
    ├── WebScripts.kt                 # JavaScript injection scripts
    └── theme/                        # Material 3 theming
        ├── Color.kt                  # Color palette definitions
        ├── Theme.kt                  # 6 color scheme compositions
        └── Type.kt                   # Typography definitions
```

### Configuration Files

```
project-root/
├── build.gradle.kts                  # Root build config (AGP, KSP, Compose plugins)
├── app/build.gradle.kts              # App module (dependencies, signing, proguard)
├── gradle/libs.versions.toml         # Version catalog (all dependency versions)
├── gradle.properties                 # Gradle daemon & JVM settings
├── settings.gradle.kts              # Project & plugin resolution
├── .env.example                      # Template for API keys
└── .github/workflows/
    └── build-apk.yml                 # CI/CD pipeline (GitHub Actions)
```

---

## Build & Run

### Prerequisites

- **Android Studio Koala+** (2024.1+) or newer
- **JDK 17** (bundled or standalone)
- Android SDK with API level 36 installed
- A physical device or emulator running **API 24+** (Android 7.0+)

### Steps

1. **Clone the repository**
   ```bash
   git clone https://github.com/Parasgaming122/Novel-readerKt.git
   cd Novel-readerKt
   ```

2. **Configure API keys** (optional — only needed for Gemini AI Translation)
   ```bash
   cp .env.example .env
   # Edit .env and add your Gemini API key:
   GEMINI_API_KEY=your_api_key_here
   ```
   > The app works fully without this key. Gemini translation is an optional feature.

3. **Open in Android Studio**
   - Launch Android Studio and select **Open an existing project**
   - Navigate to the cloned repository root
   - Wait for Gradle sync to complete (this resolves all dependencies via the version catalog)

4. **Run**
   - Select a device/emulator (API 24+)
   - Click **Run** or use `./gradlew assembleDebug`
   - The debug build is signed with the debug keystore automatically

### Build Variants

| Variant | Minification | Signing | Use Case |
|---------|-------------|---------|----------|
| **debug** | Off | Debug keystore | Development, testing |
| **release** | ProGuard enabled | Release keystore (env vars) | Production distribution |

### Environment Variables (Release Builds)

| Variable | Description | Required |
|----------|-------------|---------|
| `KEYSTORE_PATH` | Path to release keystore (`.jks`) | Yes (for release) |
| `STORE_PASSWORD` | Keystore password | Yes (for release) |
| `KEY_PASSWORD` | Key password | Yes (for release) |
| `GEMINI_API_KEY` | Google Gemini API key | No (optional feature) |

---

## CI/CD

Novel Reader uses **GitHub Actions** for automated builds and manual deployments:

### 1. Automated Debug Builder

- **Workflow File**: `.github/workflows/build-apk.yml`
- **Trigger**: Push to `main` branch
- **Build**: Compiles the debug APK using the latest Gradle and JDK 17
- **Release**: Automatically creates a **GitHub Release** with SemVer patch increment
- **Artifact**: Uploads the signed debug APK to the release

```
Push to main → GitHub Actions → Gradle assembleDebug → SemVer bump → GitHub Release
```

### 2. Manual Release Deployment Builder

- **Workflow File**: `.github/workflows/build-release-apk.yml`
- **Trigger**: Manual trigger only (`workflow_dispatch`); does not auto-run upon push. The user runs it manually.
- **Keystore Signing**: Generates/utilizes a custom production release keystore signed with standard secrets values (`STORE_PASSWORD`, `KEY_PASSWORD`).
- **Build**: Compiles the release-optimized obfuscated production APK using R8.
- **Deployment**: Automatically increments SemVer version, crafts a custom production-ready tagged release, and uploads the `.apk` release production binary as an artifact.

---

## Permissions

Novel Reader requests the following Android permissions:

| Permission | Purpose | Justification |
|------------|---------|----------------|
| `INTERNET` | Web browsing, API calls, translation | Core browser functionality |
| `FOREGROUND_SERVICE` | Run TTS as a foreground service | Android requirement for media playback |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Media-type foreground service | Ensures correct service categorization for API 34+ |
| `WAKE_LOCK` | Prevent CPU sleep during TTS playback | Prevents audio interruption when screen is off |
| `POST_NOTIFICATIONS` | Show media controls in notification shade | Required on Android 13+ for media notifications |

All permissions are requested at runtime with user-facing justifications. The foreground service is declared with `android:foregroundServiceType="mediaPlayback"` for proper Android 14+ compliance.

---

## Technical Documentation

For deep architectural and implementation details, refer to the `docs/` directory:

| Document | Description |
|----------|-------------|
| [Architecture Overview](docs/ARCHITECTURE_OVERVIEW.md) | System-wide topology, component diagram, and state flow synchronization rules |
| [Core Engine Manual](docs/CORE_ENGINE.md) | Detailed internals of background services, JS ↔ Native bridges, and telemetry logging |
| [UI Subsystem Guide](docs/UI_LAYER.md) | Compose layouts, injected CSS/JS, scraper logic, and settings panels |
| [Data Layer Schema](docs/DATA_LAYER.md) | Room database entities, DAO queries, serialization, and backup/restore procedures |
| [Adding New Websites](docs/ADDING_WEBSITES.md) | Step-by-step guide for adding new domain scrapers to the registry |
| [Fixes Log](docs/fixes.md) | Historical bug-fix log, crash preventions, safe stream allocations, and anti-CAPTCHA implementations |
| [Agent Onboarding](AGENTS.md) | Critical rules, defect history, and operational memory for AI coding agents |

---

## License

This project does not currently have a specified license. If you wish to use, modify, or distribute this code, please contact the repository owner.

> **Recommendation**: Consider adding an open-source license (e.g., MIT, Apache 2.0, or GPL) to `LICENSE` in the repository root to clearly define usage rights.

---

<div align="center">

**Built with Kotlin, Jetpack Compose, and a love for web novels.**

</div>
