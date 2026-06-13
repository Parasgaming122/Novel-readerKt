# AGENTS.md — Novel Reader (Wtr-Lab Browser)

> AI agent onboarding document. Provides permanent context to prevent regressions.
> **Do not remove or modify unless explicitly instructed.**

---

## 1. Project Identity

| Field | Value |
|---|---|
| **Name** | Novel Reader (Wtr-Lab Browser) |
| **Type** | Single-module Android app (Jetpack Compose + WebView + TTS) |
| **App ID** | `com.paras.novelreader` |
| **Package** | `com.example` |
| **Min SDK** | 24 (Android 7.0) |
| **Target SDK** | 36 |
| **Namespace** | `com.example` |

> **DO NOT refactor the `com.example` package to `com.paras.novelreader`.** ProGuard keep rules, `-keepattributes` annotations, and the `namespace` in `build.gradle.kts` all reference `com.example`. Changing the package breaks release builds silently.

---

## 2. Tech Stack

| Component | Version / Details |
|---|---|
| **Kotlin** | 2.2.10 (compose compiler plugin) |
| **AGP** | 9.1.1 |
| **Gradle** | Configuration cache enabled |
| **Compose BOM** | 2024.09.00 |
| **Material 3** | `androidx.compose.material3` |
| **Room** | 2.7.0 (KSP, `fallbackToDestructiveMigration`, DB v4) |
| **Coroutines** | 1.10.2 (core + android) |
| **StateFlow / SharedFlow** | Reactive state throughout ViewModel and Bridge objects |
| **Google Generative AI** | 0.9.0 (Gemini 2.5 Flash, `responseMimeType = "application/json"`) |
| **OkHttp** | 4.10.0 + logging-interceptor |
| **Retrofit** | 2.12.0 + converter-moshi |
| **Moshi** | 1.15.2 + codegen (KSP) |
| **Coil** | 2.7.0 |
| **KSP** | 2.3.5 |
| **ProGuard** | `proguard-android-optimize.txt` + custom `proguard-rules.pro` |
| **Secrets** | Secrets Gradle Plugin 2.0.1 (`.env` / `.env.example`) |
| **Security Crypto** | `androidx.security:security-crypto:1.1.0-alpha06` for storage encryption |
| **Web Engine** | Android WebView (WebKit), `setJavaScriptEnabled = true` |

---

## 3. Critical Rules

### Rule 1: Active Tab URL Synchronization

**Background tab `onPageFinished` MUST NOT hijack Tab A's address bar.**

In `WtrWebAppInterface.syncUrl()`, the tab checks `WtrAudioControlBridge.currentlyActiveTabId.value == tab.id` before invoking the callback:

```kotlin
// WtrWebAppInterface.kt line 12
@JavascriptInterface
fun syncUrl(url: String, title: String) {
    if (WtrAudioControlBridge.currentlyActiveTabId.value == tabId) {
        onUrlSynced(url, title)
    }
}
```

In `BrowserAppScreen`, the `onUrlSynced` lambda performs a triple-gate check — active tab ID match, triggering tab ID match, and WebView URL match — before updating state:

```kotlin
// BrowserAppScreen.kt ~line 643
if (isWebUrl && currentActive?.id == tab.id
    && triggeringTab?.id == tab.id
    && isWebViewMatchingActive
    && (currentActive.url != syncedUrl || currentActive.title != htmlTitle)
    && currentActive.url != "chrome://newtab") { ... }
```

**Never** use stale closures like `activeTab?.id` where references can get mismatched during page swaps.

### Rule 2: Modern User-Agent String

WebView MUST use a current, well-formed UA. Older/truncated UAs trigger bot detection on novel sites (403, blank pages, CAPTCHA loops).

```kotlin
// Mobile (default) — BrowserAppScreen.kt line 411
"Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

// Desktop — BrowserAppScreen.kt line 409
"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
```

UA is also forced in the `AndroidView` update block (~line 703) if a mismatch is detected.

### Rule 3: Coroutine Telemetry via WtrLogManager

ALL coroutine launches in important operations MUST log start/completion via `WtrLogManager.log()`:

```kotlin
WtrLogManager.log(context, "description of operation")
```

- Ring buffer of 100 entries, newest-first (`_logs.add(0, formatted)`).
- Persisted to `SharedPreferences("wtr_browser_settings")` under key `"saved_logs_serialized"`, serialized with `"||LC||"` delimiter.
- Background writes via dedicated `loggerScope` on `Dispatchers.IO`.
- User toggle: `"enable_logs"` boolean in SharedPreferences.
- Export: "Save as TXT" in Diagnostic popup via Storage Access Framework.
- **Always pass a valid `context` parameter.** Passing `null` skips persistence.

### Rule 4: Smart Paragraph Saving / Translate / TTS Coordination

The TTS pipeline and Gemini translation have overlapping timing. The sequence MUST be:

1. Extract paragraphs from DOM
2. If Gemini is active: translate paragraphs → inject translated text back into DOM
3. **THEN** extract paragraphs for TTS (after translation completes)

The `isGeminiTranslating` boolean state flag gates extraction in `BrowserAppScreen` (~line 266):

```kotlin
var isGeminiTranslating by remember { mutableStateOf(false) }
// ...
isGeminiTranslating = true
try { /* translate */ } finally {
    isGeminiTranslating = false
    // only then trigger TTS extraction
}
```

Never extract TTS paragraphs before translation completes — it would feed untranslated text to the speech engine.

### Rule 5: CRITICAL — Wtr-Lab Ad-Blocker Detection

**Wtr-Lab.com actively detects ad-blockers by checking `window.speechSynthesis`.**

The JS bridge (`window.WtrBridge` / `WtrWebAppInterface`) MUST never be disconnected, even during native TTS fallback. Removing the bridge triggers "Ad-Blocker Active" warnings that **block all content**.

- `speakNative()`, `cancelNative()`, `pauseNative()`, `resumeNative()` all route through `WtrAudioControlBridge` callbacks.
- The bridge is injected in the `AndroidView` factory with `addJavascriptInterface`.
- When native TTS takes over from JS `speechSynthesis`, the bridge must remain connected so Wtr-Lab's detection script continues to see valid `window.speechSynthesis` bindings.

### Rule 6: Tab-Scoped TTS Isolation

Audio stream belongs exclusively to `WtrAudioControlBridge.activeTtsTabId`:

```kotlin
// WtrAudioControlBridge.kt
private val _activeTtsTabId = MutableStateFlow<Long?>(null)
val activeTtsTabId: StateFlow<Long?> = _activeTtsTabId
```

- Changing tabs MUST NOT disrupt background TTS on other tabs.
- Each WebView gets its own `WtrWebAppInterface` with a unique `tabId`.
- `playTrackInputList` and `webSpeakNativeFallbackList` are both capped at 300 items to prevent memory bloat.
- Starting TTS on a new tab transfers ownership; previous tab's TTS is cleared.

### Rule 7: Infinite Layout Chapter Scroll Alignment

Auto-focus highlighting uses site-specific paragraph selectors from `WebsiteSupport` implementations. The JS extraction uses:

```javascript
element.scrollIntoView({block: "center", behavior: "smooth"})
```

Must handle both standard `<p>` tags and `.wtr-line-segment` spans (defined in `CommonSelectors.STANDARD_PARAGRAPH`):

```kotlin
// commons/Commons.kt
const val STANDARD_PARAGRAPH = "p, .wtr-line-segment"
```

Sites like `webnovel.com` load chapters dynamically inside sequential visual containers — scraper must extract across multiple concurrent content containers and calculate viewport position (`rect.top - 100`).

### Rule 8: Streaming JSON Backup Parser

**MUST use `StreamingJsonParser` (pull parser). NEVER load full JSON into memory.**

```kotlin
// StreamingJsonParser.kt — uses android.util.JsonReader
val reader = JsonReader(InputStreamReader(inputStream, "UTF-8"))
reader.beginObject()
while (reader.hasNext()) {
    when (reader.nextName()) {
        "settings" -> { /* parse type-safe key-values */ }
        "history"   -> { reader.beginArray(); /* parse entries */ reader.endArray() }
        // ...
    }
}
```

- Backup encryption: `AES/CBC/PKCS7Padding` via `AndroidKeyStore` (`BackupEncryption.kt`).
- Import has a **30-second coroutine timeout**: `withTimeout(30000L) { StreamingJsonParser.parseBackupStream(...) }`.
- Memory footprint stays under 10MB for any backup size.
- Export uses streaming encrypt + buffered writer (no full-JSON in memory).
- Import detects encrypted vs plain by peeking first non-whitespace byte (`{` = plain, else encrypted).

### Rule 9: Google Translate CAPTCHA Anti-Looping

Google Translate proxy can trigger CAPTCHA pages causing redirect loops during rapid chapter-flipping.

Anti-loop guard in `BrowserAppScreen` (~line 202-258):

```kotlin
val translationAttempts = remember { mutableStateMapOf<String, Int>() }
val lastTranslationTime = remember { mutableState(mutableMapOf<String, Long>()) }
// Tracks attempts per cleaned URL — blocks after 2 attempts within 10 seconds
```

Configurable anti-CAPTCHA delay (default 4500ms):

```kotlin
var antiCaptchaDelay by remember { mutableStateOf(sharedPrefs.getBoolean("anti_captcha_delay", false)) }
// When active: Handler.postDelayed(4500) before loading next translated chapter
```

### Rule 10: Local WebView Asset Caching (Wtr-Lab)

Static assets for `wtr-lab.com` are intercepted in `shouldInterceptRequest` (~line 533):

```kotlin
override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
    val url = request?.url?.toString()
    // For wtr-lab.com .js/.css/.woff/.png/.jpg/.jpeg/.svg:
    val messageDigest = MessageDigest.getInstance("SHA-256")
    val hashBytes = messageDigest.digest(url.toByteArray(Charsets.UTF_8))
    val safeFileName = hashBytes.joinToString("") { "%02x".format(it) }
    val cacheFolder = File(context.cacheDir, "wtr_static_cache")
    // Cache hit → return local WebResourceResponse; miss → fall through to network
}
```

### Rule 11: Gemini Translation Isolation

Gemini translation ONLY triggers for novel chapter URLs — not new tab, not settings pages.

Guard conditions in `BrowserAppScreen` (~line 274):

```kotlin
val isTranslateTarget = currentGeminiTranslateEnabled
    && currentGeminiApiKey.isNotEmpty()
    && isDomainMatchedForTranslation(urlVal)
    && isNovelChapterUrl(urlVal)
```

- `isNovelChapterUrl()` checks `WebsiteSupportRegistry.findSupport(url) != null` OR URL contains `chapter`, `-ch-`, `/ch/`, `novelhubapp`, or `wtr-lab`.
- When Gemini is active and the URL matches, `shouldTranslateUrl` returns `false` to **block** standard Google Translate proxy.
- Uses `temperature = 0.3f` for consistent translations via `generationConfig`.
- Model: `gemini-2.5-flash`.

### Rule 12: CRITICAL — Background Execution via onPageFinished

**Use `WebViewClient.onPageFinished` for ALL post-load operations (TTS extraction, translation, paragraph saving).**

NEVER use Compose `LaunchedEffect` for background page operations — it fires too early (before DOM is ready) and **pauses when the app is backgrounded**, stalling auto-advance TTS and translation.

```kotlin
// BrowserAppScreen.kt line 1476-1478:
// "Removed LaunchedEffect(...) and Gemini translation effects
//  because they fail in the background when the Compose View is suspended.
//  They are now manually triggered via pageLoadBackgroundLogic inside onPageFinished!"
```

`onPageFinished` is the ONLY reliable signal that DOM is ready. Launch coroutines via `viewModelScope.launch(Dispatchers.Main)` from inside the callback.

### Rule 13: Dynamic Language-Switching TTS Stalls

`WtrBrowserService.isPlaylistPrimarilyEnglish()` samples first 15 paragraphs to decide language:

```kotlin
// WtrBrowserService.kt line 361-378
private fun isPlaylistPrimarilyEnglish(): Boolean {
    val list = WtrAudioControlBridge.playTrackInputList.value
    if (list.isEmpty()) return true
    val sampleSize = minOf(list.size, 15)
    // Counts ASCII letters (< 128) vs CJK (\u4e00..\u9fa5) / Cyrillic (\u0400..\u04FF)
    return enCharCount >= foreignCharCount
}
```

Prevents expensive 5-second TTS engine re-initialization on stray foreign lines in otherwise-English chapters.

### Rule 14: Multi-Tab Navigation Isolation

Each tab has independent back/forward navigation via `tabNavigationHistory` (MRU list in `BrowserViewModel`):

```kotlin
private val tabNavigationHistory = mutableListOf<Long>()

fun recordTabVisit(tabId: Long) {
    tabNavigationHistory.removeAll { it == tabId }
    tabNavigationHistory.add(tabId) // MRU = most recent at end
}

fun handleBackNavigation(onFinish: () -> Unit) {
    // Close current tab → switch to last visited tab via MRU
    val lastTabId = tabNavigationHistory.lastOrNull()
    val targetTab = tabs.find { it.id == lastTabId && it.id != current.id }
    // Fallback: any remaining tab
}
```

Tab switching uses local snapshot references (`val tabForView = activeTab!!`) inside the `AndroidView` update block to prevent the outgoing WebView from navigating to the incoming URL.

### Rule 15: Secure Storage for Sensitive Credentials (API Keys)

**Sensitive fields like `gemini_api_key` MUST store their values using `SecurePreferences` with hardware/keystore-backed `EncryptedSharedPreferences`.**

- Legacy storage via standard unencrypted `SharedPreferences` is highly vulnerable and has been deprecated.
- Uses `SecurePreferences.kt` which implements a thread-safe `EncryptedSharedPreferences` lookup using standard keystore alias `"wtr_secure_settings"` (`AES256_SIV` and `AES256_GCM`).
- Implements an elegant fallback-on-failure using MODE_PRIVATE local SharedPreferences named `"wtr_secure_fallback_settings"`.
- Performs an auto-migration upon first launch: reads the legacy unencrypted `gemini_api_key` under `"wtr_browser_settings"`, saves it to `SecurePreferences`, and removes the legacy unencrypted copy.

---

## 4. Complete Codebase Map

### Root Layer (14 files)

| File | Lines | Purpose & Key Types |
|---|---|---|
| `MainActivity.kt` | 117 | Entry point. `CrashReportManager.init()`, `WtrLogManager.initialize()`, starts `WtrBrowserService` as foreground service. Holds `activeWebViewsPool` (synchronized list). Contains `getProxyTranslatedUrl()`. |
| `BrowserViewModel.kt` | 605 | Core ViewModel. Tab CRUD, history recording, bookmark toggle, URL cleaning/search resolution, backup export/import (streaming + encrypted), `handleBackNavigation()` with MRU, `tabNavigationHistory`, `userNavigateTrigger` SharedFlow. |
| `WtrBrowserService.kt` | 900 | Foreground service. `MediaSession`, `WakeLock`, `WifiLock`, `TextToSpeech` engine. Notification with 1.5s throttle gate. `isPlaylistPrimarilyEnglish()`, `detectLanguageTag()`, paragraph queue management, `onStartCommand`/`onBind` lifecycle. |
| `WtrAudioControlBridge.kt` | 224 | Singleton object. All TTS state: `isPlaying`, `title`, `subtitle`, `ttsSpeed`, `ttsPitch`, `activeTtsTabId`, `currentlyActiveTabId`, `playTrackInputList` (cap 300), `webSpeakNativeFallbackList` (cap 300), `isPlayerRunning`, `isAudiobookModeActive`. Callback routing between WebView ↔ notification/lockscreen. |
| `WtrWebAppInterface.kt` | 80 | JS bridge class (one per tab). `@JavascriptInterface` methods: `syncUrl`, `syncMetadata`, `postPlaybackState`, `syncPollState`, `speakNative`, `cancelNative`, `pauseNative`, `resumeNative`. Clamps parameters/clipping safely for protection. |
| `SecurePreferences.kt` | 50 | Utility object wrapper for `EncryptedSharedPreferences`. Handles keystore-based encryption for `gemini_api_key`, fallback MODE_PRIVATE support, and automatic secure migration from cleartext SharedPreferences. |
| `WtrLogManager.kt` | 93 | Singleton. 100-entry ring buffer, SharedPreferences persistence, `loggerScope` on `Dispatchers.IO`, `"||LC||"` delimiter, user toggle `"enable_logs"`. |
| `GeminiTranslator.kt` | 135 | Singleton. Generates model using cached instances. Applies high-fidelity literal-to-literary translation rules matching NoveLM and contextual localization specialized for Xianxia, Wuxia, Wuxia-specific vocabulary, large figures, other genres of novels. Uses JSON translation mapping. |
| `StreamingJsonParser.kt` | 237 | Pull parser using `android.util.JsonReader`. Parses backup stream: version, timestamp, settings (type-safe), history, bookmarks, tabs. Inner class `BackupData` data class. |
| `BackupEncryption.kt` | 119 | `AES/CBC/PKCS7Padding` via `AndroidKeyStore`. `encryptBackup()`, `decryptBackup()`, `getEncryptingStream()`, `getDecryptingStream()` for streaming I/O. Keystore alias: `"wtr_backup_key"`. |
| `CrashReportManager.kt` | 75 | Uncaught exception handler. Saves crash reports utilizing WeakReference to avoid memory leaks. Auto-clears reports older than 7 days. Attaches last 20 logs. |
| `PerformanceMonitor.kt` | 60 | Background coroutine. Monitors heap dynamically against limits every 30s. GC trigger at >95%, warning log at >80%. `MemoryStats` data class. |
| `NetworkErrorHandler.kt` | 30 | Generic retry wrapper with exponential backoff. `executeWithRetry(context, maxRetries=3, backoffMs=1000)`. Logs retries via `WtrLogManager`. |
| `BrowserSection.kt` | 5 | Simple enum: `WEB`, `TABS`, `BOOKMARKS`, `HISTORY`, `SETTINGS`. |

### Data Layer (6 files)

| File | Lines | Purpose & Key Types |
|---|---|---|
| `AppDatabase.kt` | 30 | Room database v4. Entities: `HistoryEntry`, `BookmarkEntry`, `TabEntry`. Singleton pattern with `fallbackToDestructiveMigration()`. DB name: `"wtr_browser_db"`. |
| `HistoryEntry.kt` | 19 | `@Entity(tableName="history")`. Fields: `id`, `url`, `title`, `timestamp`. Indices on `url` and `timestamp`. |
| `BookmarkEntry.kt` | 29 | `@Entity(tableName="bookmarks")`. Fields: `id`, `url`, `title`, `timestamp`, `isNovel`, `novelTitle`, `chapterTitle`, `imageUrl`, `domain`, `lastViewedChapterUrl`, `lastViewedChapterTitle`. Indices on `url`, `domain`, `isNovel`. |
| `TabEntry.kt` | 15 | `@Entity(tableName="tabs")`. Fields: `id`, `url`, `title`, `isCurrent`, `isDesktopMode`, `groupId`, `timestamp`. |
| `BrowserDao.kt` | 82 | Room DAO. History CRUD + `pruneHistory(500)` + duplicate deletion. Bookmark CRUD + `getNovelBookmark()` + `isBookmarked()`. Tab CRUD. Returns `Flow` for reactive collection. |
| `BrowserRepository.kt` | 228 | Repository wrapping DAO. URL normalization (strips `_x_tr_*`, `utm_*`, trailing `/`). Mutex-protected `insertHistory()` with deduplication. Novel metadata extraction via `WebsiteSupportRegistry.extractNovelAndChapter()`. `validateDatabaseIntegrity()`. |

### Sites Layer (4 files)

| File | Lines | Purpose & Key Types |
|---|---|---|
| `WebsiteSupport.kt` | 21 | Interface. Properties: `siteId`, `domains`, `keywords`, `requiresAutoTranslate`, `containerSelectors`, `paragraphSelector`, `excludeSelectors`, `requiresBrPreparation`, `siteSpecificJunkKeywords`, `adBlockKeywords`, `titleSuffixes`. |
| `WebsiteSupportImpls.kt` | 185 | 11 concrete implementations: `WtrLabSupport`, `WebNovelSupport`, `NovelHallSupport`, `FanMtlSupport`, `NovelBinSupport`, `FreeWebNovelSupport`, `TimoTxtSupport`, `Novel543Support`, `TwkanSupport`, `NovelHubSupport`, `NovelHubAppSupport`. |
| `WebsiteSupportRegistry.kt` | 202 | Singleton object. `findSupport(url)` with translate proxy URL cleaning (`--`/`.` decoding). `findSupportByKeyword()`. `extractNovelAndChapter()` with regex patterns for chapter/title splitting. `getAutoTranslateSites()`. |
| `commons/Commons.kt` | 76 | `CommonSelectors.STANDARD_PARAGRAPH = "p, .wtr-line-segment"`. `COMMON_EXCLUDE` (42 CSS selectors for ads/nav/comments). `CommonJunkKeywords.GENERIC_PROMO`. `CommonPatterns.TITLE_PATTERNS` and `URL_PATTERNS` (Regex list for chapter detection). |

### UI Layer (10 files)

| File | Lines | Purpose & Key Types |
|---|---|---|
| `BrowserAppScreen.kt` | 3225 | Core composable. WebView factory/update, `WebViewClient`/`WebChromeClient`, `onPageFinished` dispatches all post-load logic. Search bar, bottom audio shelf, tab switching, desktop mode toggle, settings navigation. `shouldInterceptRequest` for ad-blocking + Wtr-Lab static cache. `isSameBaseOrTranslatedUrl()`. Anti-loop translation guards. Gemini translation injection. |
| `SettingsPanel.kt` | 1128 | Settings overlay. TTS controls (speed, pitch, voice, accent), force-dark CSS, ad-blocker toggle, auto-translate domain list, Gemini API key, anti-CAPTCHA delay, diagnostic log viewer, backup export/import launchers via SAF. |
| `TabsPanel.kt` | 456 | Tab management grid. Tab cards with title/URL preview, close button, active indicator. Tab grouping support via `groupId`. |
| `BookmarksPanel.kt` | 364 | Bookmark list. Novel bookmarks with cover image, last-viewed chapter, domain badge. Delete/swipe-to-delete. Novel metadata card. |
| `HistoryPanel.kt` | 155 | History list sorted by timestamp. Delete individual entries, clear all. URL/title display. |
| `ChromeNewTabPage.kt` | 371 | Default new tab screen. Shortcut grid for supported sites, recent history rows, search input. |
| `WebScripts.kt` | 466 | JS injection utilities. `injectForceDarkCss()`, `injectTranslateCssCleanup()`, TTS bridge script (`injectTtsBridgeScript`), paragraph extraction JS, scroll-into-view highlighting. |
| `theme/Theme.kt` | 139 | Material 3 dynamic theme. Supports Dark/Light/System themes. Color scheme generation from `Color.kt`. |
| `theme/Color.kt` | 11 | Color constants for light/dark palettes. |
| `theme/Type.kt` | 36 | Typography definitions (Material 3 `Typography`). |

**Total: 33 source files, ~10,500 lines of Kotlin.**

---

## 5. Supported Website Registry

| Site | Domains | Auto-Translate | Special Notes |
|---|---|---|---|
| **Wtr-Lab** | `wtr-lab.com`, `wtr-lab.co` | No | Primary target. Deep JS bridge (`WtrBridge`). Ad-blocker detection via `window.speechSynthesis`. Static asset caching. |
| **WebNovel** | `webnovel.com` | No | Dynamic container extraction (`.cha-content`, `.cha-words`). Infinite layout scrolling. Junk filter for `"webnovel"`. |
| **NovelHall** | `novelhall.com`, `novelhall.net` | No | `#htmlContent` container. `requiresBrPreparation = true`. Ad-block keywords present. Multiple title suffixes. |
| **FanMTL** | `fanmtl.com` | No | `.chapter-content`, `.read-content` containers. Junk filter for `"fanmtl"`. |
| **NovelBin** | `novelbin.com`, `novelbin.net` | No | `#chr-content`, `.chr-c` containers. Standard extraction. |
| **FreeWebNovel** | `freewebnovel.com` | No | `.txt`, `#htmlContent` containers. Simple extraction. |
| **TimoTxt** | `timotxt.com`, `timotxt.cn` | **Yes** | Chinese site. `requiresBrPreparation = true`. Chinese junk keywords (`"本章未完"`, `"点击下一页"`, etc.). |
| **Novel543** | `novel543.com` | **Yes** | `#content`, `.article-content`. `requiresBrPreparation = true`. |
| **Twkan** | `twkan.com` | **Yes** | `#htmlContent`, `.article-content`. Standard extraction, no BR prep. |
| **NovelHub** | `novelhub.net` | No | English-first. `#chr-content`, `.chapter-content`, `main article` selectors. |
| **NovelHubApp** | `novelhubapp.com` | **Yes** | Single-page reader app. Dynamic navigation via client-side hash injection for chapter uniqueness. |

Auto-translate sites (4): TimoTxt, Novel543, Twkan, NovelHubApp — routed through Google Translate proxy unless Gemini is active.

---

## 6. Common Pitfalls

1. **Don't use `JSONObject` for backup parsing.** Always use `StreamingJsonParser` (pull parser). Loading full JSON into memory causes 150-200MB spikes and ANR crashes on low-RAM devices.

2. **Don't remove `@JavascriptInterface` annotations.** ProGuard's `proguard-rules.pro` has `-keepclassmembers class com.example.WtrWebAppInterface { *; }` but annotations are the canonical contract. Removing them risks R8 stripping the methods.

3. **Don't change the package from `com.example`.** ProGuard keep rules, `namespace` in `build.gradle.kts`, and room schema all reference `com.example`. Refactoring breaks release builds silently.

4. **Don't use `data:` or `blob:` URLs in history.** Filtered out in `BrowserViewModel.onPageLoaded()` — they're internal browser artifacts, not navigable pages. Also filtered: URLs > 2048 chars.

5. **Don't modify `WtrAudioControlBridge` StateFlow caps.** `playTrackInputList` and `webSpeakNativeFallbackList` are both capped at 300 items (`list.take(300)`). Raising this cap increases memory pressure during long audiobook sessions.

6. **Don't use `LaunchedEffect` for page-load triggers.** Compose lifecycle pauses `LaunchedEffect` when the app goes to background, stalling TTS auto-advance and Gemini translation. Always dispatch from `onPageFinished`.

7. **Don't disconnect the JS bridge on Wtr-Lab pages.** Wtr-Lab's anti-adblock system checks `window.speechSynthesis` availability. Removing the bridge triggers content blocks.

8. **Don't skip `fallbackToDestructiveMigration()` in Room.** The database is at version 4. Without destructive migration fallback, schema changes will crash on upgrade.

---

## 7. Documentation Index

For deep architectural details, refer to the project docs:

- `docs/ARCHITECTURE_OVERVIEW.md` — System topology, state flow synchronization rules
- `docs/CORE_ENGINE.md` — Background services, bridges, ViewModel internals, telemetry
- `docs/UI_LAYER.md` — Compose layouts, views, settings overlays, JS scrapers
- `docs/DATA_LAYER.md` — Room schema, DAO queries, repository patterns, Regex heuristics
- `docs/fixes.md` — Complete debugging history, memory limiters, anti-CAPTCHA implementations
- `docs/ADDING_WEBSITES.md` — Guide for adding new site scraper implementations
