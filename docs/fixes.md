# Novel Reader - Complete Fixes Log

All 30 resolved issues documented with severity, affected files, root cause
analysis, and implementation details.

---

## Concurrency & Thread Safety

### Issue 1: Background tab syncUrl race condition hijack

- **Severity:** Critical
- **Files:** `WtrWebAppInterface.kt`, `BrowserAppScreen.kt`, `WtrAudioControlBridge.kt`
- **Pre-fix state:** Background web novel chapters loading resources or running
  event timers would trigger the `@JavascriptInterface` `WtrBridge.syncUrl()`
  bridge command, which blindly changed the active address bar and ViewModel
  state regardless of which tab was currently in the foreground. A background
  tab's JS polling could hijack the visible tab's URL display.
- **Solution:** Stored the current active tab ID in `WtrAudioControlBridge`
  (via `currentlyActiveTabId` StateFlow), updated on tab changes via
  `LaunchedEffect(activeTab)`. The bridge now verifies its scoped `tabId`
  matches the global `currentlyActiveTabId` before delegating URL sync
  callbacks. Additionally, the `onUrlSynced` callback retrieves the WebView's
  actual native URL and cross-checks it against the ViewModel's active tab
  URL using `isSameBaseOrTranslatedUrl()`, discarding stale JS reports
  from pages still in transit.
- **Code:**
```kotlin
val isWebViewMatchingActive = wvUrl.isNotEmpty()
    && isSameBaseOrTranslatedUrl(wvUrl, currentActive?.url ?: "")
if (isWebUrl && currentActive?.id == tab.id
    && triggeringTab?.id == tab.id
    && isWebViewMatchingActive) {
    viewModel.onPageLoaded(syncedUrl, htmlTitle)
}
```

### Issue 3: Handler/Coroutine leaks in background service

- **Severity:** High
- **Files:** `WtrBrowserService.kt`
- **Pre-fix state:** The foreground service started untracked coroutine scopes
  on `onCreate` and never cancelled handler callbacks in `onDestroy`, leaving
  timers ticking endlessly across service restarts.
- **Solution:** Bound all setting collect flows inside `WtrBrowserService`
  directly to `serviceScope` (a `CoroutineScope(Dispatchers.Main +
  SupervisorJob())`). In `onDestroy`, the scope is cancelled and all pending
  handler callbacks are removed cleanly.
- **Code:**
```kotlin
override fun onDestroy() {
    serviceScope.cancel()
    webviewSpeechTimeoutHandler.removeCallbacks(webviewSpeechTimeoutRunnable)
    super.onDestroy()
}
```

### Issue 5: CoroutineScope leak

- **Severity:** High
- **Files:** `WtrBrowserService.kt`
- **Pre-fix state:** Un-tracked coroutine scopes from initializations could
  outlive the service lifecycle, causing leaked coroutines to run after the
  service was destroyed.
- **Solution:** Replaced all ad-hoc `CoroutineScope` launches with the
  lifecycle-aware `serviceScope`. All coroutines are automatically cancelled
  when `serviceScope.cancel()` is called in `onDestroy()`.

### Issue 24: Duplicate history entries race

- **Severity:** High
- **Files:** `BrowserDao.kt`, `BrowserRepository.kt`
- **Pre-fix state:** During TTS sequences, `onPageFinished` (Main UI thread)
  and `onUrlSynced` (Binder thread) executed asynchronously. Both checked
  `getHistoryByUrl(url) == null` before Room could complete the write,
  creating duplicate entries for the exact same chapter URL.
- **Solution:** Implemented a Kotlin Coroutine `Mutex` (`historyMutex`) in
  `BrowserRepository.kt` to serialize insertions. Added smart URL
  normalization (stripping trailing slashes and Google Translate query params)
  with exact Host + Title matching to auto-merge duplicates. Moved heavy
  operations like `pruneHistory` outside the lock to eliminate deadlock risk.

---

## Memory Management

### Issue 2: Cumulative playTrackInputList heap leak

- **Severity:** Medium
- **Files:** `WtrAudioControlBridge.kt`
- **Pre-fix state:** Extracted text tracks of massive sizes could scale up
  RAM to 20-50MB during high-capacity TTS operations on long chapters.
- **Solution:** Restricted the paragraph storage array in both
  `playTrackInputList` and `webSpeakNativeFallbackList` to a safe cap of 300
  paragraphs. Sequential resets clean up references cleanly.
- **Code:**
```kotlin
fun setPlayTrackInputList(list: List<String>) {
    _playTrackInputList.value = if (list.size > 300) list.take(300) else list
}
```

### Issue 11: Backup stream closure crashes

- **Severity:** High
- **Files:** `BrowserViewModel.kt`, `SettingsPanel.kt`
- **Pre-fix state:** When using `document.openOutputStream` or
  `openInputStream` on the callback thread, streams could be closed
  prematurely by the system, causing `"backup failed: stream closed"` crashes
  and corrupted backup files.
- **Solution:** Moved all stream operations into the ViewModel's
  `Dispatchers.IO` launch context. Used `android.net.Uri` boundaries and
  enclosed operations in `try-catch-finally` with `.use {}` constructs for
  guaranteed resource cleanup.

### Issue 19: Memory overload on 2GB devices

- **Severity:** Medium
- **Files:** `PerformanceMonitor.kt`
- **Pre-fix state:** Background web engine renders and massive novel content
  chunks triggered heap allocation failures on low-end devices without
  warning.
- **Solution:** Built `PerformanceMonitor.kt` with a thread-safe background
  loop that emits memory utilization warnings at 80% RAM and forces garbage
  collection (`System.gc()`) when consumption breaches 95% utilization.

### Issue 25: Memory exhaustion during JSON restore

- **Severity:** High
- **Files:** `BrowserViewModel.kt`, `StreamingJsonParser.kt`
- **Pre-fix state:** Deserializing 10,000+ records from 100MB+ backups
  converted streams into full JVM Strings, causing 200MB allocation spikes
  and OOM crashes on 1-2GB RAM devices.
- **Solution:** Developed `StreamingJsonParser.kt` using Android's low-level
  `JsonReader` for sequential token-stream parsing. Memory stays under 10MB
  regardless of backup size. Added a 30-second coroutine timeout for
  malformed restores and `BrowserRepository.validateDatabaseIntegrity()`
  to verify Room tables for corrupt entries.

---

## TTS & Audio

### Issue 7: Silent TTS engine failures

- **Severity:** Medium
- **Files:** `WtrBrowserService.kt`
- **Pre-fix state:** If the TTS engine setup failed initially or timed out
  during bootstrap, subsequent play actions failed silently with no user
  feedback.
- **Solution:** Wrapped initialization in a modular `initTtsEngine` helper.
  Added timeout detection and error logging. The function performs
  self-healing lazy re-initialization on demand whenever `speakText` is
  called, recovering from transient engine failures automatically.

### Issue 14: Google CAPTCHA lockouts

- **Severity:** High
- **Files:** `BrowserAppScreen.kt`
- **Pre-fix state:** Auto-translated websites redirecting via
  `translate.goog` auto-advanced chapters immediately, mimicking scraping
  bots and triggering recurrent Google CAPTCHA lockouts.
- **Solution:** Added a 4500ms delay block inside the audio-control bridge's
  next chapter callbacks for translated URLs. A Toast notification informs
  the user: `"Auto-Next: Pausing 4.5s to bypass Google CAPTCHA filters..."`.

### Issue 20: TTS failure recovery

- **Severity:** High
- **Files:** `WtrBrowserService.kt`
- **Pre-fix state:** TTS failures left the player in a broken state with no
  recovery path. Users had to manually restart playback.
- **Solution:** Implemented on-demand lazy rebuild system healers. The
  `initTtsEngine` function is called before every `speakText` invocation,
  ensuring the engine is always in a valid state before attempting to speak.

### Issue 21: TimoTxt/Novel543 selector conflicts

- **Severity:** High
- **Files:** `BrowserAppScreen.kt`, `WebsiteSupportImpls.kt`
- **Pre-fix state:** The `.show_txt` selector was shared between TimoTxt and
  Novel543. TimoTxt's `#content` (which wraps nav elements) was matched
  first, swallowing the entire layout into a single broken span.
- **Solution:** Reorganized selectors so `TimoTxtSupport` ranks `.show_txt`
  first. Removed `.show_txt` from `Novel543Support` entirely. Rewrote
  hardcoded JS fallback references in `BrowserAppScreen.kt` to dynamically
  prioritize `.show_txt` over `#content` on `timotxt.com` visits.

### Issue 27: Dynamic language-switching TTS stalls

- **Severity:** High
- **Files:** `WtrBrowserService.kt`
- **Pre-fix state:** For Chinese novels translated to English, Google
  Translate occasionally skips lines leaving untranslated Chinese. When TTS
  encountered these, it triggered a 5-second dynamic locale re-initialization
  per stray line, causing frequent silent freezes.
- **Solution:** Developed `isPlaylistPrimarilyEnglish()` analyzing the top 15
  paragraphs. If primarily English, the `detectLanguageTag` function locks
  the locale to `"en-US"`, preventing expensive dynamic engine reinitialization
  on stray foreign characters.

---

## Security & Privacy

### Issue 16: Plaintext backup storage

- **Severity:** High
- **Files:** `BackupEncryption.kt`, `BrowserViewModel.kt`
- **Pre-fix state:** Backups were written in plain text, making bookmarks,
  tabs, and browser session histories readable by any app with storage
  permissions.
- **Solution:** Developed `BackupEncryption.kt` using AES-CBC encryption with
  PKCS7 padding. The secret key is generated and stored through Android
  KeyStore System. Versioned encrypted backups ship under "Version 2" format,
  transparent to the user.

### Issue 8: Crash when starting foreground service

- **Severity:** High
- **Files:** `MainActivity.kt`
- **Pre-fix state:** Triggering `startForegroundService` blindly without
  try-catch crashed the app if blocked by modern Android background limits
  (battery restrictions, Doze mode, or restricted background start).
- **Solution:** Wrapped the service launch in a try-catch block with
  diagnostic log hooks to `WtrLogManager`. The app now gracefully degrades
  (disabling foreground TTS notifications) instead of crashing when the OS
  denies background service starts.

### Issue 9: No ProGuard obfuscation

- **Severity:** Low
- **Files:** `build.gradle.kts`, `proguard-rules.pro`
- **Pre-fix state:** Code minification was disabled in release builds, leading
  to bloated binaries and missing security guards.
- **Solution:** Enabled `isMinifyEnabled = true` in Gradle. Created a robust
  `proguard-rules.pro` file preserving reflection models and
  `@JavascriptInterface` annotated classes (e.g. `WtrWebAppInterface`).

---

## Data Integrity

### Issue 17: Unindexed Room DB performance

- **Severity:** Medium
- **Files:** `AppDatabase.kt`, `HistoryEntry.kt`, `BookmarkEntry.kt`
- **Pre-fix state:** History and bookmark lookups degraded as table rows grew
  due to sequential table scans on unindexed columns.
- **Solution:** Added composite SQLite indexes (`idx_history_url`,
  `idx_history_timestamp`, `idx_bookmark_url`, `idx_bookmark_domain`,
  `idx_bookmark_isnovel`). Bumped schema to version 4 with destructive
  migration for clean index application.

### Issue 4: JSON backup freezing main thread

- **Severity:** High
- **Files:** `BrowserViewModel.kt`
- **Pre-fix state:** Parsing 10k+ history records and exporting large JSON
  blocks occurred on the Default dispatcher, blocking Compose rendering and
  freezing the screen for 2-5 seconds.
- **Solution:** Launched backup/restore coroutines under `Dispatchers.IO`.
  Wrapped success callbacks and state mutations behind
  `withContext(Dispatchers.Main)` blocks to ensure UI updates happen on the
  correct thread.

### Issue 28: URL redirection event loop

- **Severity:** Critical
- **Files:** `BrowserAppScreen.kt`
- **Pre-fix state:** Typing a new URL while on an existing page caused an
  infinite loop. The ViewModel updated to URL B, but the old page's polling JS
  reported URL A back via `syncUrl`, which reset the ViewModel to URL A,
  re-triggering navigation to URL A, and the cycle repeated endlessly.
- **Solution:** Implemented an anti-hijack check in the `onUrlSynced`
  callback. It retrieves the WebView's actual native URL and verifies it
  matches the ViewModel's active tab URL using `isSameBaseOrTranslatedUrl`.
  If the native URL has not caught up (page is still in transit), stale JS
  reports are discarded, breaking the loop.
- **Code:**
```kotlin
val activeWV = webViewsMap[tab.id]
val wvUrl = activeWV?.url ?: ""
val isWebViewMatchingActive = wvUrl.isNotEmpty()
    && isSameBaseOrTranslatedUrl(wvUrl, currentActive?.url ?: "")
```

### Issue 30: Background auto-next chapter — translation doesn't load, TTS reads in Chinese

- **Severity:** Critical
- **Files:** `BrowserAppScreen.kt`, `WtrBrowserService.kt`, `WtrAudioControlBridge.kt`
- **Pre-fix state:** When the last paragraph of a chapter finished during audiobook mode and the app was backgrounded or the screen was off, the entire auto-next chapter flow depended on WebView JavaScript. Specifically: (a) `triggerNextChapterNavigation` used `webView.evaluateJavascript()` to find and click the "下一章" button — but Android throttles WebView JS execution when the app is not visible. (b) Google Translate redirect relied on `shouldOverrideUrlLoading` WebView intercept — also throttled in background. (c) The translation completion check in `pageLoadBackgroundLogic` polled `viewModel.currentTab.value?.url` which is Compose state — frozen when Compose is paused. (d) Paragraph extraction used `webView.evaluateJavascript()` — also throttled. The net result was that TTS would eventually start reading from the raw Chinese page because the translation never loaded, and `runHtmlTextExtractionAndPlay` extracted untranslated content. Additionally, `onPageFinished` only triggered `pageLoadBackgroundLogic` for the currently visible tab, so background chapter loads in the TTS tab got no extraction at all.
- **Solution:** Created a completely background-safe auto-next chapter pipeline:
  1. **`WtrChapterUrlResolver.kt` (NEW, 235 lines):** Pure Kotlin HTTP-based next chapter URL resolver. Fetches the current page's HTML via `HttpURLConnection` on an IO thread, parses `<a>` tags to find "下一章"/"Next Chapter" links by CSS class/ID or text content keywords. Falls back to numeric chapter increment (e.g., `chapter-5` → `chapter-6`) or `_N.html` pattern increment for CN novel sites. No WebView dependency whatsoever — works entirely from the foreground service.
  2. **`WtrNextChapterHandler.kt` (NEW, 160 lines):** Orchestrates the entire background next-chapter flow inside the foreground service's coroutine scope. Resolves the next chapter URL via `WtrChapterUrlResolver`, applies Google Translate proxy via `getProxyTranslatedUrl()` if auto-translate is enabled, handles anti-CAPTCHA delay (4.5s wait for translated pages), loads the URL in the TTS-active tab's WebView via the `onLoadUrlInWebView` callback, and polls for paragraph extraction completion with a 25-second timeout. If extraction doesn't complete, triggers a fallback via `onManualExtractAndPlay`.
  3. **`WtrBrowserService.kt`:** `onDone()` in both `setupTtsUtteranceListener` and `handleNextTrack` now calls `WtrNextChapterHandler.handleNativeNextChapter()` instead of `WtrAudioControlBridge.triggerNextChapter()`. Service initializes the handler with its `serviceScope` in `onCreate` and cancels it in `onDestroy`.
  4. **`WtrAudioControlBridge.kt`:** Added three new fields: `onLoadUrlInWebView` (callback to load a URL in the correct tab's WebView from the service), `onManualExtractAndPlay` (callback to trigger paragraph extraction as a fallback), and `lastKnownContext` (Context reference for background SharedPreferences access).
  5. **`BrowserAppScreen.kt`:** Four critical changes: (a) `onPageFinished` now triggers `pageLoadBackgroundLogic` for both the visible active tab AND the TTS-active tab — this ensures background chapter loads still get extraction and playback. (b) `pageLoadBackgroundLogic` was rewritten to properly handle three cases: untranslated pages needing redirect, `translate.goog` pages (with an 800ms settle delay for Google's in-page translation), and regular pages. (c) `runHtmlTextExtractionAndPlay` now uses the TTS-active tab's WebView instead of the visible tab's WebView. (d) Registered `onLoadUrlInWebView` and `onManualExtractAndPlay` callbacks.
- **Impact:** Auto-next chapter now works seamlessly with the screen off or app backgrounded. Google Translate content loads correctly every time. TTS reads in the target language instead of falling back to Chinese.

---

## UI & Extraction

### Issue 15: Unoptimized JS extraction delays

- **Severity:** Medium
- **Files:** `BrowserAppScreen.kt`
- **Pre-fix state:** Paragraph extraction used rigid delay loops without
  dynamic backoff, putting CPU pressure on the main thread and lacking a
  hard execution timeout.
- **Solution:** Reconfigured extraction to track `startTime` with a strict
  7000ms max limit. Incorporated exponential backoff:
  `delay = (baseMs * 1.15^attempts).coerceIn(min, max)`, preventing
  spinlocks during slow network page translations.

### Issue 22: Missing wtr-lab.com caching

- **Severity:** Medium
- **Files:** `BrowserAppScreen.kt`
- **Pre-fix state:** Static assets (JS, CSS, fonts) from `wtr-lab.com` were
  fetched from the network on every tab load/refresh, adding up to 5s
  overhead.
- **Solution:** Built a custom proxy interceptor in `shouldInterceptRequest`.
  Static assets from `wtr-lab.com` are cached in `cacheDir/wtr_static_cache`
  using SHA-256 hashed filenames. Subsequent requests resolve instantly from
  local disk. Asynchronous prefetching avoids blocking the resource loading
  pipeline.

### Issue 23: Rigid anti-CAPTCHA delay

- **Severity:** Low
- **Files:** `BrowserAppScreen.kt`, `SettingsPanel.kt`
- **Pre-fix state:** The 4.5s anti-CAPTCHA delay was rigidly applied to all
  auto-translated loads, creating unnecessary waits for users with fast
  proxies or no bot challenge issues.
- **Solution:** Added a user-controlled `"anti_captcha_delay"` toggle in
  Settings. When disabled (default), translated chapters transition
  instantly. Users experiencing CAPTCHA issues can re-enable the delay.

### Issue 26: AI translations for foreign novels

- **Severity:** High
- **Files:** `GeminiTranslator.kt`, `BrowserAppScreen.kt`, `SettingsPanel.kt`, `BrowserViewModel.kt`
- **Pre-fix state:** Non-English sites were translated strictly via Google
  Translate proxy, producing robotic translations with scrambled item terms,
  broken pronoun genders, and lost character name context.
- **Solution:** Developed `GeminiTranslator.kt` using `gemini-2.5-flash` with
  1M token context. It extracts page paragraphs, assigns `wtr-translation-id`
  HTML attributes, compiles JSON arrays, and requests Gemini to localize
  context and terms. Translated text is injected back into the original
  WebView elements via JavaScript, synchronizing both visual reading and TTS
  audiobook playback. Added Settings toggles for API key and enable/disable.

---

## Diagnostics & Observability

### Issue 6: Diagnostic log persistence disabled

- **Severity:** Medium
- **Files:** `WtrLogManager.kt`
- **Pre-fix state:** Log serialization was disabled due to concerns over
  main-thread write load, making post-crash diagnosis impossible.
- **Solution:** Re-enabled persistence using a dedicated `loggerScope`
  (`CoroutineScope(Dispatchers.IO + SupervisorJob())`). Logs are copied
  locally and serialized to SharedPreferences on an IO thread without
  blocking the UI.
- **Code:**
```kotlin
private val loggerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
// Inside log():
loggerScope.launch {
    val serialized = logsCopy.joinToString("||LC||")
    sharedPrefs.edit().putString("saved_logs_serialized", serialized).apply()
}
```

### Issue 10: Notification throttle latency

- **Severity:** Low
- **Files:** `WtrBrowserService.kt`
- **Pre-fix state:** Notification updates fired on every playback state change,
  triggering Android's notification rate-limiter ("Package enqueue rate is
  ... Shedding") and causing stale notification states.
- **Solution:** Implemented a 500ms debounce threshold. Rapid state changes
  within the window are coalesced into a single notification update via a
  `Handler.postDelayed` pattern.

### Issue 12: Raw threads in WtrLogManager

- **Severity:** Medium
- **Files:** `WtrLogManager.kt`
- **Pre-fix state:** Log persistence originally spawned raw Java threads for
  serialization, risking thread proliferation and lack of structured
  cancellation.
- **Solution:** Migrated all serialization to the `loggerScope` coroutine
  on `Dispatchers.IO`. The `SupervisorJob` parent ensures a single failure
  does not cancel sibling log operations.

### Issue 13: Debug telemetry plain text export

- **Severity:** Low
- **Files:** `BrowserAppScreen.kt`
- **Pre-fix state:** Logs could only be viewed inside the app or cleared,
  making external debugging on larger novel text sizes difficult.
- **Solution:** Leveraged Android Storage Access Framework (SAF)
  `CreateDocument("text/plain")` contract. Users can export logs as a readable
  `.txt` file via the "Save Logs" button in the diagnostic log viewer.

### Issue 18: No crash diagnostics in production

- **Severity:** Medium
- **Files:** `CrashReportManager.kt`, `MainActivity.kt`
- **Pre-fix state:** Runtime crashes closed the app instantly without leaving
  a trace, making user-reported issues impossible to investigate.
- **Solution:** Built `CrashReportManager.kt` capturing all uncaught thread
  exceptions via `Thread.setDefaultUncaughtExceptionHandler`. It serializes
  thread stacks alongside the current in-app debug buffers and saves crash
  logs to private local directories for later retrieval.

---

## Security & Data Protection

### Issue 20: Plaintext Storage of Sensitive API Keys
- **Severity:** High
- **Files:** `SecurePreferences.kt`, `AndroidManifest.xml`
- **Pre-fix state:** Crucial credentials such as the Google Gemini API Key were stored in plaintext shared preferences, vulnerable to local extraction on rooted devices or via standard adb backup mechanisms.
- **Solution:** Implemented `SecurePreferences.kt` on top of Jetpack `EncryptedSharedPreferences` using AES-256 SIV/GCM, backed by AndroidKeyStore. Added a transparent first-launch migration layer which reads legacy unencrypted credentials, moves them to secure storage, and purges the plaintext copy. Hardened app sandbox by completely disabling system backups (`android:allowBackup="false"`).

### Issue 21: Broad Network Traffic Exceptions and Eavesdropping Risks
- **Severity:** High
- **Files:** `network_security_config.xml`, `AndroidManifest.xml`
- **Pre-fix state:** The app utilized an unrestricted `usesCleartextTraffic="true"` setting globally, allowing unencrypted transmissions even to secure service backends like Google APIs (Gemini, Translate proxy, etc.).
- **Solution:** Added a strict custom `network_security_config.xml` mapping cleartext traffic exceptions. While standard HTTP cleartext is permitted broadly for web compatibility with legacy novel websites, cleartext traffic is strictly *blocked* and HTTPS is locked-in for all resources matching `google.com` and `googleapis.com`.

---

## Performance & Memory Management

### Issue 22: Context Memory Leaks in Crash Handler
- **Severity:** Medium
- **Files:** `CrashReportManager.kt`
- **Pre-fix state:** Retaining static `Context` references inside `CrashReportManager.kt` created an invisible memory leak across multiple MainActivity lifecycle creations and process restarts.
- **Solution:** Migrated context references inside `CrashReportManager.kt` to utilize `java.lang.ref.WeakReference` ensuring they do not prevent garbage collection.

### Issue 23: Performance Monitor Heap Calculation Inaccuracies
- **Severity:** Low
- **Files:** `PerformanceMonitor.kt`
- **Pre-fix state:** The `PerformanceMonitor` evaluated JVM heap usage against total RSS parameters, giving highly skewed, inaccurate measurements.
- **Solution:** Swapped calculation logic to read runtime memory limits via `Runtime.getRuntime().maxMemory()`, yielding precise, reliable percentage values for garbage collection signals.
