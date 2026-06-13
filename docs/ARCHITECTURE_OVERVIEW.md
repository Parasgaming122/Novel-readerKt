# Novel Reader (Wtr-Lab) — Architecture Overview

> **LLM-Reconstructable Blueprint**: This document contains sufficient detail for any
> developer or AI agent to understand, replicate, or extend the entire architecture of
> the Wtr-Lab Novel Reader Android application.

---

## Table of Contents

1. [System-Wide Topological Blueprint](#1-system-wide-topological-blueprint)
2. [Component Interlocking & Data Flow](#2-component-interlocking--data-flow)
3. [Concurrency & State Flow Architecture](#3-concurrency--state-flow-architecture)
4. [JavaScript Bridge Architecture](#4-javascript-bridge-architecture)
5. [Translation Pipeline Architecture](#5-translation-pipeline-architecture)
6. [Backup/Restore Pipeline](#6-backuprestore-pipeline)
7. [Security Model](#7-security-model)
8. [Design Patterns Used](#8-design-patterns-used)

---

## 1. System-Wide Topological Blueprint

### 1.1 Presentation & Engine Layer

```
+------------------------------------------------------------------+
|                        MainActivity                                |
|  ComponentActivity | enableEdgeToEdge() | CrashReportManager.init()|
|  Permission Requests (Android 13+ notifications)                  |
|  Foreground Service start (WtrBrowserService)                     |
|  WebView Pool (synchronized ArrayList)                            |
|  WtrLogManager.initialize()                                        |
+--------------------------------+----------------------------------+
                                 | setContent
                                 v
+------------------------------------------------------------------+
|                   BrowserAppScreen (Jetpack Compose)               |
|                                                                   |
|  +------------+  +------------+  +---------------+  +-----------+|
|  | WebView    |  | TopAppBar  |  | BottomAudio   |  | Overlay   ||
|  | Per-Tab    |  | URL Bar    |  | Control Shelf |  | Panels    ||
|  | Pool       |  | Search     |  |               |  |           ||
|  | (via       |  | Google     |  | Play/Pause    |  | Tabs      ||
|  |  remember  |  | Translate  |  | Next/Prev     |  | Bookmarks ||
|  |  key=id)   |  | Toggle    |  | Speed/Pitch   |  | History   ||
|  |            |  | Desktop    |  | Audiobook     |  | Settings  ||
|  +-----+------+  +------------+  | Mode Toggle   |  | Chrome    ||
|        |                         +------+--------+  | NewTabPage ||
|        |                                |            +-----------+|
+--------+--------------------------------+------------------------+
         | @JavascriptInterface            | StateFlow collect
         v                                v
+------------------+          +-----------------------------------+
|WtrWebAppInterface |          |     WtrAudioControlBridge         |
| (per-tab instance)|--------->|     (Global Kotlin `object`)      |
|                  |          |                                   |
| 8 Methods:       |          | 30+ StateFlows:                   |
|  - syncUrl       |          |   isPlaying, title, subtitle,     |
|  - syncMetadata  |          |   novelName, chapterTitle,       |
|  - postPlayback  |          |   activeWebsite, ttsSpeed,        |
|  - syncPollState |          |   ttsPitch, ttsVoiceName,         |
|  - speakNative   |          |   ttsAccent, availableVoices,     |
|  - cancelNative  |          |   webSpeakNativeFallbackList,     |
|  - pauseNative   |          |   webSpeakNativeFallbackIndex,    |
|  - resumeNative  |          |   activeTtsTabId, playTrackInput  |
|                  |          |   List, currentTrackIndex,         |
| tabId scoping    |          |   isPlayerRunning,                 |
| for multi-tab    |          |   isAudiobookModeActive,          |
| isolation        |          |   currentlySpeakingText,          |
|                  |          |   currentlyActiveTabId,            |
| Injected as      |          |   extractedUrl                     |
| "WtrBridge"      |          |                                   |
+------------------+          | 8+ callback lambdas:              |
                               |   onStateChangedCallback          |
                               |   onMetadataExtracted             |
                               |   onSpeakNative                  |
                               |   onCancelNative                |
                               |   onPauseNative                 |
                               |   onResumeNative                |
                               |   onWebViewProgressTrigger      |
                               |   onTtsDone                      |
                               |                                   |
                               | Actions: play/pause/next/prev/   |
                               |          nextChapter              |
                               | Paragraph list cap: 300 items     |
                               +----------------+------------------+
                                                |
                                                v
                               +-----------------------------------+
                               |      WtrBrowserService            |
                               |      (Foreground Service)         |
                               |                                   |
                               |  - TextToSpeech engine            |
                               |  - MediaSession + lockscreen      |
                               |  - Persistent Notification        |
                               |    (ID: 4048, throttled 1.5s)      |
                               |  - WakeLock (PARTIAL)             |
                               |  - WifiLock (HIGH_PERF)           |
                               |  - Backup Takeover Mechanism      |
                               |    (webviewSpeechTimeoutHandler)  |
                               |  - Language Detection (zh/en/ru)  |
                               |  - TTS Utterance Progress Listener|
                               |  - serviceScope (SupervisorJob)   |
                               |  - startForeground type:          |
                               |    FOREGROUND_SERVICE_TYPE_       |
                               |    MEDIA_PLAYBACK                 |
                               +-----------------------------------+
```

### 1.2 Data & Persistence Layer

```
+------------------------------------------------------------------+
|                BrowserViewModel (AndroidViewModel)                  |
|                                                                   |
|  StateFlows (exposed to UI):                                      |
|    allHistory: StateFlow<List<HistoryEntry>>                      |
|    allBookmarks: StateFlow<List<BookmarkEntry>>                   |
|    allTabs: StateFlow<List<TabEntry>>                             |
|    currentTab: StateFlow<TabEntry?>                               |
|    currentUrlInput: StateFlow<String>                             |
|    searchEngine: StateFlow<String>                                |
|                                                                   |
|  SharedFlow (event bus):                                          |
|    userNavigateTrigger: SharedFlow<String>                       |
|                                                                   |
|  Internal state:                                                  |
|    tabNavigationHistory: MutableList<Long>                       |
|    lastHistoryUrl: String?                                        |
|                                                                   |
|  Operations (all launch in viewModelScope):                       |
|    addNewTab / switchToTab / closeTab / clearAllTabs              |
|    loadUrl / onPageLoaded / handleBackNavigation                  |
|    toggleBookmark / deleteBookmark / deleteHistory / clearHistory |
|    toggleDesktopMode / groupTabs / removeFromGroup                |
|    updateNovelMetadata / exportBackup / importBackup              |
|                                                                   |
|  SharingStarted.WhileSubscribed(5000) for flow lifecycle         |
+--------------------------------+----------------------------------+
                                 |
                                 v
+------------------------------------------------------------------+
|                  BrowserRepository                                |
|                                                                   |
|  Wraps BrowserDao with business logic:                            |
|                                                                   |
|  1. URL Normalization                                              |
|     - Strips Google Translate params (_x_tr_sl, _x_tr_tl, etc.)   |
|     - Strips UTM parameters (utm_source, utm_medium, etc.)       |
|     - Trailing slash removal                                     |
|                                                                   |
|  2. History Deduplication (Mutex-guarded)                         |
|     - Matches by normalized URL OR (title + host)                  |
|     - Keeps longest title, shortest HTTPS URL                     |
|     - Deletes duplicate entries after merge                       |
|     - Prune to 500 entries max                                   |
|                                                                   |
|  3. Novel Detection                                               |
|     - WebsiteSupportRegistry.findSupport(url) for known sites     |
|     - Chapter keyword detection ("Chapter", "Ch.", "Ch ")       |
|     - Title parsing via extractNovelAndChapter()                  |
|                                                                   |
|  4. Reading Progress Tracking                                     |
|     - Fuzzy matching: novelTitle -> bookmark domain              |
|     - Path root matching for translated titles                    |
|     - Translated title detection (non-CJK, longer)               |
|     - Updates lastViewedChapterUrl/Title                          |
|                                                                   |
|  5. Database Integrity Validation                                  |
|     - Validates non-empty URL fields across all tables           |
+--------------------------------+----------------------------------+
                                 |
                                 v
+------------------------------------------------------------------+
|            BrowserDao (Room @Dao, 22 queries)                     |
|                                                                   |
|  History (7 queries):                                              |
|    getAllHistory, getAllHistoryList, getHistoryByUrl               |
|    pruneHistory, insertHistory (REPLACE)                          |
|    deleteHistory, clearHistory, deleteHistoryDuplicates           |
|                                                                   |
|  Bookmarks (10 queries):                                           |
|    getAllBookmarks, getAllBookmarksList, insertBookmark           |
|    updateBookmark, getNovelBookmark, getAllNovelBookmarks         |
|    deleteBookmark, deleteBookmarkByUrl, clearBookmarks            |
|    isBookmarked                                                    |
|                                                                   |
|  Tabs (6 queries):                                                 |
|    getAllTabsFlow, getAllTabs, insertTab (REPLACE)                |
|    updateTab, deleteTab, clearTabs                                |
+--------------------------------+----------------------------------+
                                 |
                                 v
+------------------------------------------------------------------+
|         AppDatabase (Room v4, fallbackToDestructiveMigration)     |
|                                                                   |
|  Database name: "wtr_browser_db"                                   |
|  Thread-safe singleton: @Volatile + synchronized companion       |
|                                                                   |
|  3 Entities:                                                       |
|  +------------------+--------------------------------------------+|
|  | history          | id (PK auto), url (indexed), title,         ||
|  |                  | timestamp (indexed)                          ||
|  +------------------+--------------------------------------------+|
|  | bookmarks       | id (PK auto), url (indexed), title,         ||
|  |                  | timestamp, isNovel (indexed),                ||
|  |                  | novelTitle?, chapterTitle?, imageUrl?,       ||
|  |                  | domain? (indexed),                            ||
|  |                  | lastViewedChapterUrl?, lastViewedChapterTitle?||
|  +------------------+--------------------------------------------+|
|  | tabs            | id (PK auto), url, title, isCurrent,         ||
|  |                  | isDesktopMode, groupId?, timestamp           ||
|  +------------------+--------------------------------------------+|
+------------------------------------------------------------------+
```

### 1.3 Sites & Content Intelligence Layer

```
+------------------------------------------------------------------+
|         WebsiteSupportRegistry (Kotlin `object`, singleton)       |
|                                                                   |
|  Registry Maps (built at init):                                    |
|    domainMap: Map<String, WebsiteSupport>                          |
|    keywordMap: Map<String, WebsiteSupport>                        |
|                                                                   |
|  Public API:                                                       |
|    findSupport(url): WebsiteSupport?                              |
|      - Exact domain match first                                    |
|      - Partial host contains fallback                              |
|      - Google Translate proxy reversal (-- -> -, - -> .)          |
|    findSupportByKeyword(keyword): WebsiteSupport?                 |
|    getAutoTranslateSites(): List<String>                           |
|    extractNovelAndChapter(title, url): Pair<String, String>       |
|                                                                   |
|  11 WebsiteSupport Implementations:                               |
|  +--------------------+-----------+------+------------------------+|
|  | Class              | siteId    | Auto | Domains               ||
|  +--------------------+-----------+------+------------------------+|
|  | WtrLabSupport      | wtr-lab   | No   | wtr-lab.com/co        ||
|  | WebNovelSupport    | webnovel  | No   | webnovel.com           ||
|  | NovelHallSupport   | novelhall | No   | novelhall.com/net      ||
|  | FanMtlSupport      | fanmtl    | No   | fanmtl.com             ||
|  | NovelBinSupport    | novelbin  | No   | novelbin.com/net       ||
|  | FreeWebNovelSupport| freeweb   | No   | freewebnovel.com       ||
|  | TimoTxtSupport     | timotxt   | Yes  | timotxt.com/cn         ||
|  | Novel543Support    | novel543  | Yes  | novel543.com            ||
|  | TwkanSupport       | twkan     | Yes  | twkan.com              ||
|  | NovelHubSupport    | novelhub  | No   | novelhub.net           ||
|  | NovelHubAppSupport | nhubapp   | Yes  | novelhubapp.com        ||
|  +--------------------+-----------+------+------------------------+|
|                                                                   |
|  Each provides:                                                   |
|    - containerSelectors: CSS selectors for chapter content       |
|    - paragraphSelector: CSS for individual paragraphs              |
|    - excludeSelectors: CSS for junk/ad/nav elements               |
|    - siteSpecificJunkKeywords: text-based junk filter              |
|    - adBlockKeywords: ad-related content markers                  |
|    - titleSuffixes: suffixes to strip for clean title extraction   |
|    - requiresBrPreparation: <br> tag normalization flag             |
|                                                                   |
|  Shared via Commons.kt:                                           |
|    CommonSelectors.STANDARD_PARAGRAPH = "p, .wtr-line-segment"   |
|    CommonSelectors.COMMON_EXCLUDE = [40+ selectors]               |
|    CommonJunkKeywords.GENERIC_PROMO = [50+ keywords]             |
|    CommonPatterns.TITLE_PATTERNS = [6 regexes]                    |
|    CommonPatterns.URL_PATTERNS = [4 regexes]                     |
+------------------------------------------------------------------+
```

---

## 2. Component Interlocking & Data Flow

The application is organized into five interacting layers. Each layer has a clear
responsibility boundary and communicates through well-defined interfaces.

### 2.1 Compose UI Layer (Presentation)

- `BrowserAppScreen` is the single root composable, rendered via `setContent` in
  `MainActivity`.
- All UI state is observed via `collectAsStateWithLifecycle()` on the
  ViewModel's `StateFlow` properties.
- `userNavigateTrigger` is a `SharedFlow<String>` (replay=0) that emits URL
  navigation events to the WebView layer without backpressure buildup.
- Five overlay panels (Web, Tabs, Bookmarks, History, Settings) are toggled
  via `BrowserSection` enum.
- WebView instances are keyed by tab ID using `remember(tabId)` and managed in
  a synchronized pool (`MainActivity.activeWebViewsPool`).
- The bottom audio shelf reads `WtrAudioControlBridge` StateFlows directly to
  display TTS controls.

### 2.2 WebView JavaScript Bridge (Browser Engine)

- Each WebView gets a unique `WtrWebAppInterface` instance injected as
  `"WtrBridge"` via `addJavascriptInterface`.
- The bridge is tab-scoped via `tabId` — only the active tab's events update
  global state (`WtrAudioControlBridge.currentlyActiveTabId` guard).
- `WebScripts.kt` provides three JavaScript injection functions:
  - `injectForceDarkCss()` — forced dark mode CSS injection
  - `injectTranslateCssCleanup()` — hides Google Translate UI chrome
  - `injectTtsBridgeScript()` — 420-line SpeechSynthesis polyfill with
    periodic polling, paragraph progress extraction, metadata sync, and
    audio element hooks
- The TTS polyfill overrides `window.speechSynthesis`, `SpeechSynthesisUtterance`,
  and `SpeechSynthesisVoice` with mock implementations that route through
  `WtrBridge.speakNative()`.

### 2.3 WtrAudioControlBridge (Central Mediator)

- Global Kotlin `object` (singleton) — exists for the lifetime of the process.
- **Upstream**: Receives state from WebView JS via `WtrWebAppInterface` callbacks.
- **Downstream**: Exposes StateFlows consumed by `WtrBrowserService` (notification),
  the Compose UI (audio shelf), and other listeners.
- **Bidirectional routing**:
  - JS → Bridge → Service: `speakNative`, `cancelNative`, `pauseNative`, `resumeNative`
  - Service → Bridge → JS: `onWebViewProgressTrigger` fires `WtrTtsTriggerEvent()`
  - Notification/Lockscreen → Bridge → JS: `playAction`, `pauseAction`,
    `nextAction`, `prevAction`
- Paragraph lists are capped at 300 items to prevent memory pressure.

### 2.4 Foreground Service (Background Playback)

- `WtrBrowserService` runs as a foreground service with
  `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`.
- Holds `MediaSession` for lockscreen controls and `TextToSpeech` engine.
- Manages `WakeLock` (PARTIAL) and `WifiLock` (HIGH_PERF) during active playback.
- Notification throttling: 1.5-second minimum interval between updates, with
  deduplication of identical state.
- **Backup Takeover Mechanism**: If the WebView JS is throttled (background/tab
  switched), a `webviewSpeechTimeoutHandler` (3s fallback / 100ms in takeover mode)
  automatically advances to the next paragraph natively.

### 2.5 Room Database (Persistence)

- `AppDatabase` is a thread-safe singleton using `@Volatile` + `synchronized`.
- `BrowserRepository` wraps all DAO access with business logic (normalization,
  deduplication, novel detection, progress tracking).
- All writes use `viewModelScope.launch` (or `Dispatchers.IO` for backup ops).
- Database version 4 with `fallbackToDestructiveMigration()` — no manual migrations.

---

## 3. Concurrency & State Flow Architecture

### 3.1 Reactive State Model

| Layer | Scope | Dispatcher | Lifecycle |
|-------|-------|-----------|-----------|
| ViewModel | `viewModelScope` | `Dispatchers.Main` (default) | Activity lifecycle |
| Service | `serviceScope` | `Dispatchers.Main` (SupervisorJob) | Service lifecycle |
| Repository | Caller's scope | Inherited (IO for heavy ops) | N/A |
| DAO | Room internal | `Dispatchers.IO` | N/A |
| LogManager | `loggerScope` | `Dispatchers.IO` (SupervisorJob) | Process lifetime |

### 3.2 Flow Sharing Strategy

- `SharingStarted.WhileSubscribed(5000)` for `allHistory`, `allBookmarks`,
  `allTabs` — flows stay active for 5 seconds after last subscriber leaves,
  preventing database re-queries on rapid screen rotations.
- `SharedFlow<String>(replay=0, extraBufferCapacity=8)` for `userNavigateTrigger`
  — fire-and-forget navigation events with small buffer to handle rapid typing.

### 3.3 Thread Safety Mechanisms

| Mechanism | Usage Location | Purpose |
|-----------|---------------|---------|
| `Mutex` | `BrowserRepository.historyMutex` | Prevents race conditions in history deduplication |
| `synchronized` block | `MainActivity.activeWebViewsPool` | Thread-safe WebView creation/destruction |
| `synchronized` block | `WtrLogManager.lock` | Thread-safe log list access |
| `synchronized` block | `WtrBrowserService` (wake/wifi locks) | Prevents double-acquire/release |
| `@Volatile` | `AppDatabase.INSTANCE` | Double-checked locking singleton |
| `Handler(Looper.getMainLooper)` | Notification throttling, TTS timeout | Main-thread-posted callbacks |
| `SupervisorJob` | `serviceScope`, `loggerScope` | Child failure doesn't cancel siblings |

### 3.4 Key Concurrency Patterns

- **Coroutines, not RxJava**: All async work uses Kotlin coroutines.
- **`withContext(Dispatchers.IO)`**: Used explicitly for database backup/restore
  operations and SharedPreferences writes in `WtrLogManager`.
- **`withTimeout(30000L)`**: Backup import parsing has a 30-second hard timeout.
- **Debouncing**: `WtrBrowserService.cancelJob` debounces state transitions during
  fast paragraph switching (100ms delay).

---

## 4. JavaScript Bridge Architecture

### 4.1 Bridge Interface

`WtrWebAppInterface` is instantiated per-tab with a unique `tabId` and injected
into each WebView as `"WtrBridge"`.

```
WebView JavaScript                     Native Kotlin
---------------------                  -------------
WtrBridge.syncUrl(url, title)      -->  onUrlSynced callback (tabId guard)
WtrBridge.syncMetadata(n, c, img)  -->  WtrAudioControlBridge.onMetadataExtracted
WtrBridge.postPlaybackState(...)    -->  onPlaybackStateChanged (tabId guard)
WtrBridge.syncPollState(...)        -->  onPlaybackStateChanged (tabId guard)
WtrBridge.speakNative(text, r, p, l)-->  WtrAudioControlBridge.onSpeakNative
WtrBridge.cancelNative()            -->  WtrAudioControlBridge.onCancelNative
WtrBridge.pauseNative()             -->  WtrAudioControlBridge.onPauseNative
WtrBridge.resumeNative()            -->  WtrAudioControlBridge.onResumeNative
```

### 4.2 SpeechSynthesis Polyfill (WebScripts.kt)

The polyfill (~420 lines) intercepts all Web Speech API calls:

- **MockVoice**: Provides 7 pre-defined voices (en-US, en-GB, vi-VN, zh-CN, es-ES).
- **MockSpeechSynthesisUtterance**: Intercepts text, rate, pitch, lang properties.
- **MockSpeechSynthesis**: Overrides `speak()`, `cancel()`, `pause()`, `resume()`
  to route through `WtrBridge.speakNative()` etc.
- **Event Dispatch**: `window.WtrTtsTriggerEvent(event, charIndex)` fires
  `onstart`, `onend`, `onpause`, `onresume`, `onerror`, `onboundary` events on
  the active utterance.
- **Periodic Polling** (500ms interval):
  - Detects playing state from `synthInstance.speaking`, audio elements, CSS classes
  - Extracts paragraph progress (`findParagraphProgress()`) from TTS UI containers
  - Syncs URL via `WtrBridge.syncUrl()`
  - Extracts metadata (cover image, title) via `WtrBridge.syncMetadata()`
- **SPA Hash Tracking**: For SPA-style sites (NovelHubApp), appends `#wtr=` hash
  to URLs to make chapter transitions detectable.
- **Audio Element Hooks**: Monkey-patches `HTMLAudioElement.prototype.play/pause`
  to sync playback state.
- **Guard**: `window.WtrTtsPolyfilled` flag prevents double injection.
- **Self-Healing**: A 1-second `setInterval` re-applies the polyfill if
  `window.speechSynthesis` is overwritten by site scripts.

### 4.3 Critical Warning: Ad-Blocker Detection

Some novel sites detect ad-blockers and may block or disrupt the page when the
bridge is disconnected. The bridge must **never** be removed during page lifecycle.

---

## 5. Translation Pipeline Architecture

### 5.1 Google Translate Proxy (URL Rewriting)

Implemented in `getProxyTranslatedUrl()`:

```
Original: https://www.novel543.com/chapter/123
Transform: hyphens → double-hyphens, dots → single-hyphens, append .translate.goog
Result: https://www----novel543-com.translate.goog/chapter/123?_x_tr_sl=auto&_x_tr_tl=en
```

- Auto-redirect is handled in `shouldOverrideUrlLoading` within the WebViewClient.
- Sites requiring auto-translate are identified by `WebsiteSupport.requiresAutoTranslate`.
- `WebsiteSupportRegistry.getAutoTranslateSites()` returns domains needing
  automatic translation.
- `injectTranslateCssCleanup()` hides Google Translate UI artifacts (toolbar,
  banner, iframe).

### 5.2 Gemini AI Translation

Implemented in `GeminiTranslator` (singleton `object`):

```
Flow:
  1. Paragraph extraction from DOM (via WebsiteSupport selectors)
  2. Build JSON array: [{"id": 0, "text": "..."}, {"id": 1, "text": "..."}]
  3. API call to Gemini 2.5 Flash (temperature=0.3, responseMimeType=JSON)
  4. Parse response JSON array, map by id
  5. DOM injection of translated paragraphs
```

- System prompt is a specialized localizer for Chinese web novels to English.
- API key stored in SharedPreferences (`gemini_api_key`).
- Fallback to original text if translation fails.
- Markdown code fence stripping handles raw LLM output.
- Runs on `Dispatchers.IO` via `withContext`.

### 5.3 Anti-Loop Guard

Translation proxy URLs are detected and skipped to prevent infinite loops:
- URLs containing `translate.goog` or `translate.google` are returned as-is.
- `normalizeUrl()` strips `_x_tr_*` parameters from stored URLs.

---

## 6. Backup/Restore Pipeline

### 6.1 Export Flow

```
Room Data (history, bookmarks, tabs)
    |
    v
SharedPreferences settings snapshot
    |
    v
JSON streaming writer (BufferedWriter)
    |
    v
BackupEncryption.getEncryptingStream()
    ├── AndroidKeyStore AES-256-CBC
    ├── 16-byte random IV prepended
    └── Base64OutputStream wrapping
    |
    v
SAF URI (user-selected via ActivityResultContracts.CreateDocument)
```

### 6.2 Import Flow

```
SAF URI (user-selected via ActivityResultContracts.OpenDocument)
    |
    v
BufferedInputStream → inspect first non-whitespace byte
    ├── '{' → plaintext JSON (pass through)
    └── other → encrypted (BackupEncryption.getDecryptingStream)
         ├── Base64InputStream unwrapping
         ├── Read 16-byte IV
         └── CipherInputStream (AES-256-CBC)
    |
    v
StreamingJsonParser.parseBackupStream()
    ├── 30-second timeout (withTimeout)
    ├── android.util.JsonReader (streaming, no full JSON load)
    └── Produces BackupData(version, timestamp, settings, history, bookmarks, tabs)
    |
    v
Restore:
    1. SharedPreferences editor.apply()
    2. dao.clearHistory() → batch insertHistory()
    3. dao.clearBookmarks() → batch insertBookmark()
    4. dao.clearTabs() → batch insertTab() → restore currentTab StateFlow
    |
    v
UI state update on Dispatchers.Main
```

### 6.3 Design Considerations

- **Streaming**: Both export and import use streaming I/O to avoid OOM on large databases.
- **Encrypted/plaintext auto-detection**: Import inspects the first byte to determine
  format without user input.
- **Encryption fallback**: If KeyStore initialization fails during export,
  falls back to plaintext writing.
- **Backup format version**: `version: 2` with timestamp for future migration support.

---

## 7. Security Model

### 7.1 Encryption

- **Algorithm**: AES-256-CBC with PKCS7 padding.
- **Key Storage**: Android KeyStore (`AndroidKeyStore` provider), hardware-backed
  on supported devices.
- **Key Alias**: `"wtr_backup_key"`.
- **IV Handling**: 16-byte random IV generated per encryption, prepended to ciphertext.
- **Stream Support**: `getEncryptingStream()` / `getDecryptingStream()` wrap
  `CipherOutputStream` / `CipherInputStream` over Base64 streams for
  arbitrary-length data.

### 7.2 Obfuscation

- **ProGuard**: Enabled in release builds (`isMinifyEnabled = true`).
- Config files: `proguard-rules.pro` + default `proguard-android-optimize.txt`.

### 7.3 Network Security

- **Cleartext Traffic**: `android:usesCleartextTraffic="true"` in manifest.
  Required because many novel sites (especially Chinese web novel platforms)
  serve content over HTTP.
- **No HTTPS Enforcement**: WebView does not enforce HTTPS; this is intentional
  for maximum site compatibility.
- **Hardware Acceleration**: `android:hardwareAccelerated="true"` for WebView rendering.

### 7.4 Permissions

| Permission | Purpose |
|-----------|---------|
| `INTERNET` | WebView network access |
| `FOREGROUND_SERVICE` | TTS playback service |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Android 14+ media type |
| `WAKE_LOCK` | Prevent CPU sleep during TTS |
| `POST_NOTIFICATIONS` | TTS control notification (Android 13+) |

---

## 8. Design Patterns Used

### 8.1 MVVM (Model-View-ViewModel)

```
View (Compose)  ←collectAsState→  ViewModel (StateFlow)  ←suspend→  Repository  ←Room→  Database
```

- `BrowserViewModel` is the single ViewModel, extending `AndroidViewModel`
  for application context access.
- UI state is exclusively exposed via `StateFlow<T>` properties.
- One-time events use `SharedFlow<T>` with `replay=0`.

### 8.2 Repository Pattern

- `BrowserRepository` wraps `BrowserDao` and adds all business logic:
  URL normalization, history deduplication, novel detection, reading progress
  fuzzy matching, database integrity validation.
- The ViewModel never directly accesses the DAO.

### 8.3 Strategy Pattern

- `WebsiteSupport` interface defines the contract for per-site configuration.
- 11 implementations provide site-specific CSS selectors, junk keywords,
  title suffixes, and translation flags.
- `WebsiteSupportRegistry` selects the correct strategy at runtime based on
  URL domain or keyword lookup.

### 8.4 Singleton Pattern

| Singleton | Mechanism | Purpose |
|-----------|-----------|---------|
| `WtrAudioControlBridge` | `object` keyword | Global state mediator |
| `WtrLogManager` | `object` keyword | Centralized logging |
| `CrashReportManager` | `object` keyword | Crash report persistence |
| `BackupEncryption` | `object` keyword | Keystore crypto operations |
| `WebsiteSupportRegistry` | `object` keyword | Site support lookup |
| `GeminiTranslator` | `object` keyword | AI translation API |
| `NetworkErrorHandler` | `object` keyword | Retry logic |
| `PerformanceMonitor` | `object` keyword | Memory monitoring |
| `AppDatabase` | Companion `@Volatile` + `synchronized` | Database singleton |

### 8.5 Mediator Pattern

- `WtrAudioControlBridge` is the central mediator between four layers:
  1. **WebView JS** (via `WtrWebAppInterface`)
  2. **Foreground Service** (via callback lambdas)
  3. **Compose UI** (via StateFlow observation)
  4. **MediaSession / Lockscreen** (via notification updates)
- No layer directly communicates with another layer; all flows through the bridge.

### 8.6 Observer Pattern

- All reactive state uses Kotlin `StateFlow` / `SharedFlow`.
- Subscribers use `collectAsStateWithLifecycle()` (Compose) or `.collect {}` (Service).
- `SharingStarted.WhileSubscribed(5000)` manages upstream flow lifecycle.

### 8.7 Factory Pattern

- WebView instances are created per-tab using a factory-like pattern in
  `BrowserAppScreen`:
  - `remember(tabId)` ensures one WebView per tab.
  - Each WebView gets its own `WtrWebAppInterface(tabId, ...)` instance.
  - `CookieManager` is set once globally in `BrowserAppScreen`.
- WebView configuration (JavaScript enabled, DOM storage, mixed content mode,
  user agent) is applied uniformly at creation time.

---

## Appendix: Key Source Files Reference

| File | Role | Lines (approx) |
|------|------|-----------------|
| `MainActivity.kt` | Activity entry, WebView pool, edge-to-edge | ~120 |
| `BrowserAppScreen.kt` | Root Composable, WebView management, all panels | ~2000 |
| `BrowserViewModel.kt` | State management, backup/restore orchestration | ~600 |
| `WtrAudioControlBridge.kt` | Global state mediator, 30+ StateFlows | ~225 |
| `WtrBrowserService.kt` | Foreground TTS service, MediaSession, WakeLock | ~900 |
| `WtrWebAppInterface.kt` | JS bridge interface, 8 methods | ~63 |
| `WebScripts.kt` | JS injection (TTS polyfill, dark mode, translate cleanup) | ~467 |
| `BrowserRepository.kt` | Data layer business logic, Mutex-guarded dedup | ~228 |
| `BrowserDao.kt` | Room DAO, 22 queries | ~83 |
| `AppDatabase.kt` | Room database definition | ~30 |
| `WebsiteSupportRegistry.kt` | Site support lookup, title/chapter extraction | ~202 |
| `WebsiteSupportImpls.kt` | 11 site implementations | ~186 |
| `Commons.kt` | Shared selectors, keywords, patterns | ~77 |
| `BackupEncryption.kt` | AES-256-CBC via AndroidKeyStore, stream support | ~119 |
| `StreamingJsonParser.kt` | Streaming JSON backup parser | ~237 |
| `GeminiTranslator.kt` | Gemini 2.5 Flash translation | ~101 |
| `WtrLogManager.kt` | Centralized logging with persistence | ~93 |
| `CrashReportManager.kt` | Uncaught exception handler, crash file persistence | ~71 |
| `NetworkErrorHandler.kt` | Generic retry with exponential backoff | ~31 |
| `PerformanceMonitor.kt` | Memory monitoring, GC trigger | ~59 |
| `ChromeNewTabPage.kt` | Custom new tab page with site shortcuts | ~372 |
