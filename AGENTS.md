# AI Agent Onboarding Checklist & Architectural Rules (AGENTS.md)

This file serves as the permanent context, directory roadmap, and operational memory for any future AI Coding Agent continuing development on the Novel Reader application. **Do not remove or modify this file unless explicitly instructed by the user.**

---

## 🧭 Project Coordinates & Tech Stack

- **Context**: A premium, native Android web browser optimized specifically for reading, listening (Text-To-Speech), and auto-translating web novels.
- **UI Architecture**: Jetpack Compose, Material Design 3.
- **Async Concurrency**: Kotlin Coroutines & Kotlin Flows (`StateFlow`, `collectAsStateWithLifecycle`).
- **Data Persistence**: Android Room SQLite Local Database (`tabs`, `history`, `bookmarks`).
- **Web Engine**: Android System WebKit WebViews managed in a global concurrent pool (`MainActivity.activeWebViewsPool`) to avoid context leaks.

---

## 🧭 Project Documentation Index

For deep architectural and implementation details, refer to:

- [🚀 Architecture Overview](docs/ARCHITECTURE_OVERVIEW.md): System-wide topology and state flow synchronization rules.
- [📱 Core Engine Manual](docs/CORE_ENGINE.md): Detailed internals of background services, bridges, view models, and telemetry.
- [🎨 UI Subsystem Guide](docs/UI_LAYER.md): Compose layouts, views, settings overlays, and JS scrapers.
- [🗄️ Data Layer Schema](docs/DATA_LAYER.md): Room database entities, DAO queries, repositories, and Regex parsing heuristics.
- [🛠️ Complete Fixes Log](docs/fixes.md): Exhaustive debugging history, memory limiters, safe streams, and anti-CAPTCHA implementations.

---

## ⚠️ Critical Lessons & Past Defect Diagnostics

Ensure you read this section before making any changes to WebView behaviors, lifecycle hooks, or navigation methods to prevent regressions:

### 1. Active Tab URL Synchronization & Background Hijacking (CRITICAL)

- **Defect**: Inactive background tabs finishing page loads or executing timers in the background would trigger standard `@JavascriptInterface` bridge callbacks (`onUrlSynced`). If left unchecked, this would overwrite the active tab's address bar, resulting in flashing, random redirects, or freezing the screen back to previous URLs.
- **Rule**: You **MUST** always safely resolve the _currently active tab_ via `viewModel.currentTab.value` and verify that the triggering WebView belongs to the active tab:
  ```kotlin
  val currentActive = viewModel.currentTab.value
  val isWebUrl = syncedUrl.startsWith("http://") || syncedUrl.startsWith("https://")
  if (isWebUrl && currentActive?.id == tab.id && currentActive.url != syncedUrl) {
      // Execute UI address updates only if active!
  }
  ```
- **Constraint**: Do not use stale closures (`activeTab?.id`) where references can get mismatched during page swaps.

### 2. Modern Web Client User-Agents (UA)

- **Defect**: Truncated, malformed, or typo-ridden client user-agents (e.g. `Android 14; K; K`) trigger strict browser bot safeguards, causing host sites to dump into blank pages, display infinite loops of security challenge validations, or reject connections with 403 Forbidden screens.
- **Rule**: When setting user-agent, always apply complete, standard, representative mobile or desktop client headers:
  ```kotlin
  // Standard Mobile User-Agent:
  val mobileUA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
  // Standard Desktop User-Agent:
  val desktopUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
  ```

### 3. Integrated Coroutine Telemetry (`com.example.WtrLogManager`)

- **Utility**: In-app diagnostic logging operates off an in-memory thread-safe state list capped at 100 historical records, displaying system actions, page loads, and audio bounds.
- **Rule**: Write logs proactively using `WtrLogManager.log(context, "message")`. Disk writing of serialized logs is delegated cleanly to a dedicated background Coroutine Scope `loggerScope` running on `Dispatchers.IO` to protect UI responsiveness.
- **Settings Toggle**: Logging is user-controlled via `enable_logs` in Shared Preferences. Respect this flag internally.
- **Exporting Logs**: Under active debugging, users can download logs as plain text files through the "Save as TXT" option in the Diagnostic popup modal, which launches standard Storage Access Framework loaders.

### 4. Smart Saving of Paragraphs, Translate, and TTS Auto-Next Coordination

- **Context**: The TTS polyfill replaces standard browser WebSpeech bindings. Reading position autosave (`remember_paragraphs`), auto-translation scraper, and next-chapter loading have overlapping timing cycles.
- **Rule**: Keep state clean. Prior to starting new speech sequences or navigating, cancel previous translation scraping coroutines and flush active speaker channels (`QUEUE_FLUSH`) inside the Foreground service context to prevent multi-voice overlays or database state conflicts.

### 5. Integrity of the JavaScript Bridge for Wtr-Lab (CRITICAL AD-BLOCKER DETECTION WARNING)

- **Context**: The JavaScript bridge (`WtrWebAppInterface` and the window injection bindings) must **NEVER** be removed, bypassed, or globally disabled for wtr-lab.com or companion reader engines. Wtr-Lab employs an extremely sensitive, proprietary script-monitoring system that tracks background TTS playback and WebSpeech api signals.
- **Rule**: Bypassing or deleting the bridge interactions causes the target website script tracking to fail, which triggers its automated security defenses, labeling the browser as having a hostile **ad-blocker** fully active. This will instantly halt webpage loading or lock the reader view. Always route speech play/pause/cancel events back through the bridge callbacks or let the background timer mechanism handle takeovers seamlessly.

### 6. Tab-scoped TTS Isolation and Concurrent Playback

- **Context**: Users expect to keep listening to TTS audio on tab A while switching to tab B to search or browse other chapters.
- **Rule**: Standard player controls are scoped to the active TTS tab ID (`WtrAudioControlBridge.activeTtsTabId`). Playback state resets or transitions must never occur upon tab switching unless the user explicitly starts a new TTS session on the newly opened tab, in which case the previous tab's TTS is cleared and the new tab takes ownership.

### 7. Infinite Layout Chapters and Visual Scroll Alignment

- **Context**: Sites like `webnovel.com` load chapters dynamically inside sequential visual containers when the reading user scrolls vertically.
- **Rule**: Scraper routines must extract paragraphs across multiple concurrent content containers, keeping track of elements in the DOM. To start reading naturally from the user's current reading point rather than always starting from paragraph 1, the script must calculate elements' positions in the viewport, selecting the paragraph closest to the top of the viewport (`rect.top - 100`). Additionally, junk filter arrays must explicitly purge any background-inserted ad-blocker detection warn texts.

### 8. Robust Backup & Restore Synchronization via SAF Uri (STREAMING & SECURE)

- **Context**: Processing large backup archives (e.g. 100MB+) previously resulted in severe memory spikes (~150-200MB), ANR warnings, and memory-fatality crashes on devices with restricted RAM, because the entire backup file was loaded into memory as a massive flat String before parsing via traditional `JSONObject`.
- **Rule**: You **MUST** always utilize `StreamingJsonParser.kt` and its native Android `JsonReader`-based pull-parsing mechanism for JSON imports. This processes backup files incrementally from the input stream, parsing Settings, History, Bookmarks, and Tabs sequentially to ensure a static, ultra-low memory footprint (<10MB) on any device.
- **Rules on Restore**: Restores must execute inside `Dispatchers.IO` with a strict `30000mL` (30-second) coroutine timeout. SharedPreferences settings must be restored type-safely, and database tables (`clearHistory`, `clearBookmarks`, `clearTabs`) MUST be wiped in proper sequential order before inserting the restored lists. The ViewModel must successfully re-evaluate the active tab ID state (`_currentTab.value = restoredTab`) to prevent empty WebViews from initializing on startup.
- **Database Integrity Validation**: Invoke `BrowserRepository.validateDatabaseIntegrity(context)` to verify the health of history, bookmarks, and tabs tables. Avoid passing `null` contexts to `WtrLogManager.log`—always feed a valid context parameter down the flow.

### 9. Google Translate CAPTCHA Anti-Looping and Interceptors

- **Context**: Rapid chapter-flipping on regional untrusted links translation proxy loops mimics automated search bots, which triggers aggressive Google CAPTCHA challenge screens.
- **Rule**: Include an explicit anti-CAPTCHA timing lock (4500ms Handler post delay and user notification Toasts) on translated chapter change requests when executing auto-advancing TTS routines. This delay is customizable via a user preference switch `anti_captcha_delay`.

### 10. Local WebView Asset Caching Engine

- **Context**: Rapidly switching tabs, loading next chapters, or loading assets on `wtr-lab.com` incurs significant speed penality on pure network roundtrips.
- **Rule**: Keep dynamic in-memory interceptors active in `shouldInterceptRequest` for `wtr-lab.com` static extension types (`.js`, `.css`, `.png`, `.woff`, `.woff2`, `.ttf`). Load resources from local disk cache (`cacheDir/wtr_static_cache`) instead of hitting the network to guarantee maximum tab switching/scrolling speed.

### 11. Premium Gemini Translation Isolation & Bypass Logic (CRITICAL)

- **Context**: Users expect Chinese/foreign web novels on auto-translation domains to translate with high-quality contextual localizations using Gemini API. Normal webpages (like catalog interfaces, search engines, standard portals) should not be processed via expensive Gemini APIs and continue utilizing the default Google Translate proxy instead.
- **Rule**: If `gemini_translate_enabled` is active with an API key, the browser intercepts matched domain routes and blocks standard proxy redirects (i.e. `shouldTranslateUrl` returns `false`) ONLY IF the URL matches the `isNovelChapterUrl(...)` query format. Once loaded, the browser triggers the visual paragraph extraction and replacement, subsequently feeding the translated text array back to the active reader TTS speech engine.

### 12. Background Execution & Jetpack Compose Lifecycle Restraints (CRITICAL)

- **Context**: The `BrowserAppScreen` handles primary extraction logic via WebView execution. However, `LaunchedEffect` hooks strictly pause operations if the application is running in the background while chapters are auto-advancing, causing features like automatic translation proxy delays or Gemini translation injections to stall indefinitely until the user reopens the app.
- **Rule**: You **MUST NOT** use Compose state observers (`LaunchedEffect`) to trigger asynchronous web page loads, DOM JS extractions, or API calls if they require background resiliency. You **MUST** run all post-page load triggers directly inside the `WebViewClient`'s `onPageFinished` event hook, bypassing Compose dispatchers, launching via `viewModelScope.launch(Dispatchers.Main)` for guaranteed sustained background thread execution!

### 13. Dynamic Language-Switching TTS Stalls (Stray Lines)

- **Context**: On translated foreign pages, Google Translate may occasional miss paragraphs, leaving brief untranslated phrases. Standard TTS dynamic language checks would switch dialects on these lines, triggering an expensive 5-second TTS engine re-initialization sequence that froze playback.
- **Rule**: Implement `isPlaylistPrimarilyEnglish` checking the core active queue characters. Under primarily English novels, enforce a static `"en-US"` tag output bypass to completely avoid engine re-init delays.

### 14. Multi-Tab Navigation Isolation & Browser-standard Back Gesture Tab Closing

- **Context**: Updating global `activeTab` states directly inside an unbounded `AndroidView` `update` block during tab-swapping causes the outgoing WebView to incorrectly navigate to the incoming URL before being disposed, causing duplicate pages.
- **Rule**: Always isolate `AndroidView` factory and update lambda configurations using localized snapshot references (`val tabForView = activeTab!!`). Additionally, maintain `tabNavigationHistory` to register visits, enabling the system's back handler to close empty-history tabs and seamlessly scale backwards through active parent tabs.

---

## 📜 Complete Codebase Map

- `/.github/workflows/build-apk.yml`
  - _CI/CD pipeline: automates building debug APKs upon pushing to GitHub `main` branch, generating SemVer patches, and compiling Github releases with APK binaries._
- `/app/src/main/java/com/example/MainActivity.kt`
  - _Main entry point, bootstrap, permission handlers, theme, and WebView pool listeners._
- `/app/src/main/java/com/example/BrowserViewModel.kt`
  - _Core VM: tab operations, history logs, search inputs, query validation, and export/import JSON backup logic._
- `/app/src/main/java/com/example/StreamingJsonParser.kt`
  - _Highly optimized, memory-efficient streaming JSON pull-parser using native Android `JsonReader` for backup import parsing._
- `/app/src/main/java/com/example/GeminiTranslator.kt`
  - _High-throughput contextual novel localizer interface integrating Google Generative AI Android SDK to translate foreign paragraphs in-place._
- `/app/src/main/java/com/example/WtrLogManager.kt`
  - _Thread-safe ring-buffer list logging operations, persisted via split serialization inside SharedPreferences using background Coroutines._
- `/app/src/main/java/com/example/WtrWebAppInterface.kt`
  - _Bridges Javascript string variables, paragraph indexes, and media play states into Android native JVM streams._
- `/app/src/main/java/com/example/WtrBrowserService.kt`
  - _Foreground service handling CPU locks, lockscreen notifications throttled at 1.5s gates, and TextToSpeech queues._
- `/app/src/main/java/com/example/BackupEncryption.kt`
  - _Secure backup utility generating hardware Store-backed AES keys for database file encryption and decryption routines._
- `/app/src/main/java/com/example/CrashReportManager.kt`
  - _Thread boundary uncaught exception handler creating offline crash logs inside private application storage._
- `/app/src/main/java/com/example/PerformanceMonitor.kt`
  - _Background thread loop validating system RAM consumption, triggering heap alerts, and GC requests._
- `/app/src/main/java/com/example/NetworkErrorHandler.kt`
  - _Exponential backoff retry wrapper to automatically recover from slow or intermittent connection errors._
- `/app/src/main/java/com/example/ui/`
  - `BrowserAppScreen.kt`: _The core parent container rendering search bar, bottom audio shelf, and nested WebViews. Includes SAF txt logger savers._
  - `SettingsDialog.kt`: _Settings panel for speech parameters, force-dark css, ad-blocker, cookies, diagnostic options, and the interactive JSON Backup / Restore importer launcher using Uri streams._
  - `ChromeNewTabPage.kt`: _Default screen rendering shortcuts, recent history rows, and search inputs._
  - `TabsPanel.kt`: _Double-grid UI folders to manage standalone tabs or nested tab folders._
- `/app/src/main/java/com/example/data/`
  - _Room database configurations decoupling database tables (`BookmarkEntry`, `HistoryEntry`, `TabEntry`) with simple repository patterns, advanced RegEx chapter title parsers, and clean table-wipe queries._

---

## 🌐 Supported Website Registry & Scraper Patterns

The reader engine has specialized scraper logic for the following domains:

- **Wtr-Lab** (`wtr-lab.com`): Primary target with deep JS bridge integration.
- **WebNovel** (`webnovel.com`): Dynamic container extraction with ad-blocker detection bypass.
- **NovelHall / FanMtl / NovelBin / FreeWebNovel**: standard CSS selector extraction.
- **TimoTxt / Novel543 / Twkan**: High-priority auto-translation domains with specialized junk filtering.
- **NovelHub** (`novelhub.net`): English-first domain with standard `#chr-content` or `.chapter-content` extraction.
- **NovelHubApp** (`novelhubapp.com`): Single-page reader app with dynamic navigation. Tracking implemented via client-side hash injection to ensure chapter uniqueness in history.
