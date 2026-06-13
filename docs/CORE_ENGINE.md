# Core Engine Reference Manual

> Technical reference for every core Kotlin class in the `com.example` root package
> of the Novel Reader Android app (Wtr-Lab Browser).

---

## 1. Main Entry & Bootloader — `MainActivity.kt` (117 lines)

**Extends:** `ComponentActivity`

### Companion Object
```kotlin
companion object {
    val activeWebViewsPool = java.util.Collections.synchronizedList(ArrayList<WebView>())
}
```
Process-wide synchronized `ArrayList<WebView>` tracking all live WebViews for centralized lifecycle management and leak prevention.

### `onCreate(savedInstanceState: Bundle?)`
Ordered boot sequence:
1. `CrashReportManager.init(this)` — installs custom `UncaughtExceptionHandler` that persists
   crash reports with stack traces and last 20 log entries before rethrowing to the system handler.
2. `CrashReportManager.clearOldCrashReports(this)` — deletes crash logs older than 7 days
   from `{filesDir}/crash_reports/`.
3. `WtrLogManager.initialize(this)` — restores persisted logs from `SharedPreferences`
   (key `"saved_logs_serialized"`, delimiter `"||LC||"`) into the Compose-observable
   `mutableStateListOf`. Logs are capped at 100 entries, newest first.
4. `enableEdgeToEdge()` — activates edge-to-edge display mode via `Activity`.
5. On Android 13+ (`TIRAMISU`): requests `android.Manifest.permission.POST_NOTIFICATIONS`
   (request code `101`).
6. Starts `WtrBrowserService` via `startForegroundService()` (Android O+) or `startService()`
   (legacy). Wrapped in try/catch; failure logged via `WtrLogManager.log()`.
7. `setContent { ... }` — Sets up the Compose root:
   - Reads `app_theme` from `SharedPreferences("wtr_browser_settings")`, defaulting to `"Dark"`.
   - Wraps `BrowserAppScreen` inside `MyApplicationTheme(themeName = activeThemeName)`.
   - Passes a placeholder `WebView(this)` to satisfy the signature; actual WebViews are
     managed via the companion pool.
   - Exposes `onThemeChanged` callback to dynamically swap `activeThemeName` state.

### `onDestroy()`
1. Nulls `WtrAudioControlBridge.onWebViewProgressTrigger`.
2. Under `synchronized(activeWebViewsPool)`: for each WebView calls `stopLoading()`, `removeAllViews()`, `destroy()` (each try/caught), then `clear()`.
3. `super.onDestroy()`.

### Package-Level: `fun getProxyTranslatedUrl(url: String): String`
Converts a URL to Google Translate proxy (`.translate.goog`). Returns early if already a translate URL. Strips `www.`, applies host encoding: hyphens→double-hyphens, dots→single-hyphens, appends `.translate.goog`. Preserves path/query, appends `_x_tr_sl=auto&_x_tr_tl=en`, forces `https`. Falls back to original on parse failure.

---

## 2. Tab States & Central Logic Broker — `BrowserViewModel.kt` (605 lines)

**Extends:** `AndroidViewModel`

### Fields
| Field | Type | Description |
|-------|------|-------------|
| `repository` | `BrowserRepository` | Room DAO wrapper |
| `allHistory` | `StateFlow<List<HistoryEntry>>` | WhileSubscribed(5000) |
| `allBookmarks` | `StateFlow<List<BookmarkEntry>>` | WhileSubscribed(5000) |
| `allTabs` | `StateFlow<List<TabEntry>>` | WhileSubscribed(5000) |
| `_currentTab` | `MutableStateFlow<TabEntry?>` | Active tab |
| `_currentUrlInput` | `MutableStateFlow<String>` | URL bar text |
| `_userNavigateTrigger` | `MutableSharedFlow<String>` | replay=0, buffer=8 |
| `_searchEngine` | `MutableStateFlow<String>` | Default Google search URL |
| `tabNavigationHistory` | `MutableList<Long>` | MRU-ordered tab ID list |

### `init` Block
1. Opens Room database via `AppDatabase.getDatabase(application)`.
2. Creates `BrowserRepository(db.browserDao())`.
3. Converts DAO `Flow` outputs to `StateFlow` with `SharingStarted.WhileSubscribed(5000)`:
   `allHistory`, `allBookmarks`, `allTabs`.
4. Restores session tabs from the database:
   - If no tabs exist → creates a default tab (`chrome://newtab`, title `"New Tab"`,
     `isCurrent = true`), inserts into DB, updates `_currentTab` and `_currentUrlInput`.
   - If tabs exist → finds the tab with `isCurrent = true` (or first tab as fallback).
     Validates the URL: if it doesn't start with `chrome://`, `http://`, or `https://`,
     resets to `chrome://newtab` and logs a warning.
   - On any exception → creates a fallback default tab.
5. Records the current tab visit via `recordTabVisit(id)`.
6. Initializes `lastHistoryUrl` to `null` for history deduplication.

### Key Methods

```kotlin
fun addNewTab(url: String = "chrome://newtab", title: String = "New Tab", groupId: Long? = null)
```
Marks all tabs `isCurrent=false`, inserts new tab with `isCurrent=true`, records MRU visit.

```kotlin
fun switchToTab(tab: TabEntry)
```
Updates `isCurrent` flags in DB for target and previous tab. Updates internal state + MRU.

```kotlin
fun closeTab(tab: TabEntry)
```
Forgets MRU entry, deletes from DB. If last tab, resets to new-tab state. If active, switches to first remaining.

```kotlin
fun handleBackNavigation(onFinish: () -> Unit)
```
MRU-based: closes current tab, switches to last visited tab from `tabNavigationHistory`, falls back to any remaining or calls `onFinish()`.

```kotlin
private fun cleanInputUrl(input: String, searchEngineUrl: String): String
```
Resolution chain: `chrome://newtab` passthrough → `WebsiteSupportRegistry.findSupportByKeyword()` keyword→domain → `http://`→`https://` upgrade → heuristic URL detection (no spaces, has dot, length>3) → search engine fallback with URL encoding.

```kotlin
fun loadUrl(url: String)
```
Cleans URL, updates tab in DB, emits to `_userNavigateTrigger`.

```kotlin
fun onPageLoaded(url: String, title: String)
```
Sanitizes: ignores `data:`/`blob:`/length>2048, strips control chars (`\p{Cc}`), truncates title to 512. Updates tab, inserts deduplicated history, calls `updateReadingProgress()`.

```kotlin
fun toggleBookmark(url: String, title: String, imageUrl: String? = null)
```
Checks `isBookmarked` Flow; if exists→deletes, otherwise→inserts.

```kotlin
fun exportBackup(uri: android.net.Uri, onSuccess: () -> Unit, onError: (Exception) -> Unit)
```
Runs on `Dispatchers.IO`. Attempts to wrap output in `BackupEncryption.getEncryptingStream()`;
falls back to raw plaintext if encryption init fails (logs the failure).
Hand-crafted streaming JSON v2 — not using a serialization library:
- **Header:** `{"version":2,"timestamp":<epoch>,"settings":{...}}`
- **Settings:** 11 SharedPreferences keys serialized via `org.json.JSONObject`:
  `app_theme`, `custom_text_zoom`, `force_dark_content`, `enable_web_trackplayer`,
  `auto_focus_paragraphs`, `remember_paragraphs`, `auto_translate_enabled`,
  `auto_translate_domains`, `gemini_translate_enabled`, `gemini_api_key`, `ad_blocker_enabled`.
- **History:** Streaming array of `{url, title, timestamp}`.
- **Bookmarks:** Streaming array with full fields including nullable `novelTitle`, `chapterTitle`,
  `imageUrl`, `domain`, `lastViewedChapterUrl`, `lastViewedChapterTitle` (as `JSONObject.NULL`).
- **Tabs:** Streaming array with `url, title, isCurrent, isDesktopMode, groupId, timestamp`.
- Three-layer `finally` block closes writer, processing stream, and raw stream independently.
- Callbacks dispatched to `Dispatchers.Main`.

```kotlin
fun importBackup(uri: android.net.Uri, onSuccess: () -> Unit, onError: (Exception) -> Unit)
```
Runs on `Dispatchers.IO`. Auto-detects encryption by peeking the first non-whitespace byte
from a `BufferedInputStream(100)`:
- `{` (ASCII 123) → raw plaintext JSON.
- Any other byte → wraps in `BackupEncryption.getDecryptingStream()`. Falls back to raw
  parsing if decryption wrapper throws.
Streaming parse via `StreamingJsonParser.parseBackupStream()` with a 30-second `withTimeout`.
Restore sequence:
1. **SharedPreferences** — iterates settings map, dispatches by runtime type
   (String/Int/Boolean/Long/Float/Double→Float).
2. **History** — `dao.clearHistory()`, then inserts each entry.
3. **Bookmarks** — `dao.clearBookmarks()`, then inserts each entry.
4. **Tabs** — `dao.clearTabs()`, then inserts each entry. Identifies the tab with
   `isCurrent = true` and updates `_currentTab` / `_currentUrlInput` on Main.
Callbacks dispatched to `Dispatchers.Main`.

```kotlin
fun groupTabs(tabIds: List<Long>, targetGroupId: Long)
fun removeFromGroup(tab: TabEntry)
fun toggleDesktopMode(tab: TabEntry, enabled: Boolean)
fun clearAllTabs()
fun deleteBookmark(id: Long) / deleteHistory(id: Long) / clearHistory()
fun isUrlBookmarked(url: String): Flow<Boolean>
fun updateNovelMetadata(url: String, novelTitle: String, chapterTitle: String, coverImage: String)
```

---

## 3. Background Audio Playback Engine — `WtrBrowserService.kt` (901 lines)

**Extends:** `Service`, foregroundServiceType = `mediaPlayback`

### Key Fields
| Field | Type | Description |
|-------|------|-------------|
| `mediaSession` | `MediaSession?` | Lock screen / Bluetooth controls |
| `wakeLock` | `PowerManager.WakeLock?` | `PARTIAL_WAKE_LOCK` |
| `wifiLock` | `WifiManager.WifiLock?` | `WIFI_MODE_FULL_HIGH_PERF` |
| `tts` | `TextToSpeech?` | Android TTS engine |
| `notificationHandler` | `Handler` (Main) | 1500ms notification throttle |
| `webviewSpeechTimeoutHandler` | `Handler` (Main) | Backup takeover scheduler |
| `isBackupTakeoverActive` | `Boolean` | Native TTS takeover flag |
| `serviceScope` | `CoroutineScope` | Main + SupervisorJob |

### `onCreate()`
1. Creates notification channel (`IMPORTANCE_LOW`, no sound/vibration).
2. Sets up `MediaSession("WtrLabSession")` with play/pause/next/prev callbacks.
3. Hooks 6 bridge callbacks: `onStateChangedCallback`→notification, `onSpeakNative`→speakText, `onCancelNative`→handleCancelNative, `onPauseNative`→pauseText, `onResumeNative`→resumeText, `playCustomParagraphAction`→playCustomParagraph.
4. Loads TTS prefs: `tts_speed` (4.0f), `tts_pitch` (1.0f), `tts_accent` ("US"), `tts_voice_name`.
5. Initializes TTS engine with self-healing recovery.
6. Starts collectors for `ttsSpeed`→`tts.setSpeechRate()` and `ttsPitch`→`tts.setPitch()`.

### `speakText(text: String, rate: Float, pitch: Float, lang: String)`
Lazy TTS recovery if uninitialized: calls `initTtsEngine { speakText(...) }` and returns.
Cancels the debounce `cancelJob` (100ms delay via `serviceScope`). Stores utterance params in
instance fields (`currentSpeechText/Rate/Pitch/Lang`, `lastWordIndex = 0`).

Voice selection logic:
- If `ttsVoiceName` is non-empty, looks up the `Voice` by name from `tts?.voices`.
- Otherwise resolves locale: for `"en-US"` maps `ttsAccent` to `Locale.UK` (UK), `Locale("en","AU")` (AU),
  `Locale("en","IN")` (IN), or `Locale.US` (default). For other languages: `Locale.forLanguageTag(lang)`.

Speaks with `TextToSpeech.QUEUE_FLUSH` (stops any current utterance immediately without queuing).
Fires `WtrAudioControlBridge.updatePlaybackState(isPlaying = true)`.

### `setupTtsUtteranceListener()`
Attaches `UtteranceProgressListener` to the TTS engine with four callbacks:
- **onStart(utteranceId?)**: Sets playback state to playing. If not in backup takeover,
  fires `onWebViewProgressTrigger("start", 0)`.
- **onDone(utteranceId?)**: Core paragraph advance logic:
  - If `playTrackInputList` is non-empty (native track mode): advances to `nextIndex`.
    If at end of list and `isAudiobookModeActive` → triggers `triggerNextChapter()` and
    re-sets audiobook mode. Otherwise → marks playback as completed, sets subtitle "Completed Reading".
  - If `playTrackInputList` is empty (WebView JS-driven mode): fires `onTtsDone`, then posts
    `webviewSpeechTimeoutRunnable` with different delays:
    - `100ms` if `isBackupTakeoverActive` (fast sequential native advance).
    - `3000ms` otherwise (grace period for WebView JS to post the next paragraph).
- **onError(utteranceId?)**: Fires `onWebViewProgressTrigger("error", 0)`.
- **onRangeStart(utteranceId?, start, end, frame)**: Captures `start` as `lastWordIndex`,
  fires `onWebViewProgressTrigger("boundary", start)` for word-level progress highlighting.

### `detectLanguageTag(text: String): String`
Short-circuits to `"en-US"` if `isPlaylistPrimarilyEnglish()`. Otherwise samples 250 chars, counts Chinese (`\u4e00..\u9fa5`), Russian (`\u0400..\u04FF`), English (ASCII letters). Returns `"zh-CN"`, `"ru-RU"`, or `"en-US"`.

### `isPlaylistPrimarilyEnglish(): Boolean`
Samples first 15 paragraphs from `playTrackInputList`. Returns true if English chars ≥ foreign chars. Prevents jarring voice switches on stray foreign lines.

### `updateNotification()`
Throttled notification update at 1500ms intervals. Two-phase approach:
1. **MediaSession update (instant, not rate-limited by system):** Sets `PlaybackState` with
   actions PLAY/PAUSE/SKIP_TO_NEXT/SKIP_TO_PREVIOUS and state STATE_PLAYING or STATE_PAUSED.
   Sets `MediaMetadata` with title (novel name + chapter), artist (paragraph progress + website),
   album ("Wtr-Lab Novel Reader"), duration=-1 (disables timeline on lock screens).
2. **Android Notification (rate-limited):** Constructs `displayTitle` and `displaySubtitle`,
   then checks if `isPlaying`, title, and subtitle are all unchanged from last render — if so,
   returns immediately. On play/pause state change, forces immediate update. Otherwise,
   schedules a deferred `Runnable` for the remaining throttle interval via `notificationHandler`.

### `performActualNotificationUpdate(isPlaying: Boolean, displayTitle: String, displaySubtitle: String)`
Records rendered state to prevent duplicate notifications. Builds a `MediaStyle` notification:
- Three `Notification.Action` buttons: **Previous** (`ic_media_previous`), **Play/Pause**
  (toggles `ic_media_play`/`ic_media_pause`), **Next** (`ic_media_next`).
- `setShowActionsInCompactView(0, 1, 2)` — all three visible in compact notification.
- Content intent opens `MainActivity` (FLAG_ACTIVITY_SINGLE_TOP, FLAG_IMMUTABLE).
- `VISIBILITY_PUBLIC`, `setShowWhen(false)`, `setOngoing(isPlaying)`.
- Calls `startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)`
  on Q+. Falls back to plain `startForeground()` on older versions. Try/caught.
- Manages `WakeLock` and `WifiLock` acquisition/release based on `isPlaying` state.

### `handleAction(action: String)`
Routes string action constants to the appropriate handler. Checks whether native tracks are active
(`WtrAudioControlBridge.playTrackInputList.value.isNotEmpty()`):
- **Native track mode:** `PLAY`→`resumeText()`, `PAUSE`→`pauseText()`, `PLAY_PAUSE`→toggle,
  `NEXT`→`handleNextTrack()`, `PREV`→`handlePrevTrack()`.
- **WebView bridge mode:** delegates to `WtrAudioControlBridge.playAction/pauseAction/nextAction/prevAction`
  which route back to WebView JavaScript via `WtrWebAppInterface`.

### `onStartCommand(intent, flags, startId): Int`
Returns `START_STICKY`. Calls `handleAction(intent?.action)` then `updateNotification()`.

### `playCustomParagraph(index: Int)`
Autosaves paragraph index to `SharedPreferences("wtr_browser_paragraphs")` if `remember_paragraphs`
is enabled. Updates `currentTrackIndex`, `currentlySpeakingText`, `isPlayerRunning`. Calls
`detectLanguageTag()` then `speakText()`.

### Backup Takeover Mechanism
Android throttles WebView JS `speechSynthesis` when the app is in the background. The service
detects this via a timeout mechanism:
1. When `onSpeakNative` fires, it cancels any pending timeout and resets `isBackupTakeoverActive`.
2. It matches the incoming text against `webSpeakNativeFallbackList` to find the current index.
3. If the WebView doesn't call `onSpeakNative` for the next paragraph within 3000ms (normal mode)
   or 100ms (already in backup mode), the `webviewSpeechTimeoutRunnable` fires:
   - Sets `isBackupTakeoverActive = true`.
   - Increments `webSpeakNativeFallbackIndex` and calls `speakText()` with the next paragraph directly.
   - In backup mode, `onRangeStart`/`onStart`/`onDone` progress triggers are suppressed (not sent
     back to the WebView) to avoid confusing the now-throttled JS side.

### `onDestroy()`
1. `serviceScope.cancel()` — cancels all coroutines including speed/pitch collectors.
2. `webviewSpeechTimeoutHandler.removeCallbacks(webviewSpeechTimeoutRunnable)`.
3. Nulls 6 bridge callbacks: `onStateChangedCallback`, `onSpeakNative`, `onCancelNative`,
   `onPauseNative`, `onResumeNative`, `playCustomParagraphAction`.
4. Under `synchronized(this)`: removes utterance progress listener, calls `tts?.stop()`
   and `tts?.shutdown()` (each try/caught), sets `tts = null`.
5. `releaseWakeLock()`, `releaseWifiLock()`, `mediaSession?.release()`, `mediaSession = null`.

---

## 4. JS-to-Native Bridge — `WtrWebAppInterface.kt` (63 lines)

Per-tab instance with unique `tabId`. Constructor:
```kotlin
class WtrWebAppInterface(
    val tabId: Long,
    private val onPlaybackStateChanged: (isPlaying: Boolean, title: String, subtitle: String) -> Unit,
    private val onUrlSynced: (url: String, title: String) -> Unit = { _, _ -> }
)
```

### 8 `@JavascriptInterface` Methods
| Method | Signature | Routes To |
|--------|-----------|-----------|
| `syncUrl` | `(url: String, title: String)` | `onUrlSynced` (only if tab is `currentlyActiveTabId`) |
| `syncMetadata` | `(novelTitle: String, chapterTitle: String, coverImage: String)` | `WtrAudioControlBridge.onMetadataExtracted` |
| `postPlaybackState` | `(isPlaying: Boolean, title: String, subtitle: String)` | Sets `activeTtsTabId` if playing, then `onPlaybackStateChanged` |
| `syncPollState` | `(isPlaying: Boolean, title: String, subtitle: String = "")` | Same as above; provides default subtitle |
| `speakNative` | `(text: String, rate: Float, pitch: Float, lang: String)` | Sets `activeTtsTabId`, then `WtrAudioControlBridge.onSpeakNative` |
| `cancelNative` | `()` | `WtrAudioControlBridge.onCancelNative` |
| `pauseNative` | `()` | `WtrAudioControlBridge.onPauseNative` |
| `resumeNative` | `()` | `WtrAudioControlBridge.onResumeNative` |

Tab ownership enforced by `activeTtsTabId` check on playback methods.

---

## 5. Global State Mediator — `WtrAudioControlBridge.kt` (224 lines)

**Type:** `object` (Singleton)

### StateFlows (30+)
- **Playback:** `isPlaying` (Bool, false), `title` (Str, "Wtr-Lab Browser"), `subtitle` (Str, "Tap to browse novels")
- **Novel metadata:** `novelName`, `chapterTitle`, `activeWebsite`, `extractedUrl`
- **TTS config:** `ttsSpeed` (4.0f), `ttsPitch` (1.0f), `ttsVoiceName` (""), `availableVoices` (emptyList), `ttsAccent` ("US")
- **Track player:** `playTrackInputList` (cap 300), `currentTrackIndex` (0), `isPlayerRunning` (false), `isAudiobookModeActive` (false), `currentlySpeakingText` ("")
- **WebView fallback:** `webSpeakNativeFallbackList` (cap 300), `webSpeakNativeFallbackIndex` (-1)
- **Tab ownership:** `activeTtsTabId` (null), `currentlyActiveTabId` (null)
- **Mutable var:** `bookTitle` ("Wtr-Lab Novel Reader")

### Callback Lambdas (13+)
- **Service→WebView JS:** `playAction`, `pauseAction`, `nextAction`, `prevAction`,
  `onWebViewProgressTrigger((event: String, charIndex: Int))`, `onTtsDone`
- **WebView JS→Service:** `onSpeakNative((text: String, rate: Float, pitch: Float, lang: String))`,
  `onCancelNative`, `onPauseNative`, `onResumeNative`
- **Cross-cutting:** `onStateChangedCallback` (triggers notification refresh),
  `onMetadataExtracted((tabId: Long, novelTitle: String, chapterTitle: String, coverImage: String))`,
  `nextChapterAction`, `playCustomParagraphAction((Int))`

### Audiobook Mode Behavior
When `isAudiobookModeActive` is `true`:
- `setIsPlayerRunning(running)` also updates `_isPlaying` to match.
- `updatePlaybackState()` ignores the `isPlaying` parameter and mirrors `isPlayerRunning`.
- On track completion, `triggerNextChapter()` is called instead of marking playback complete.
- Ensures lock screen / notification shows continuous playing state across chapter transitions.

### Key Methods
```kotlin
fun updatePlaybackState(isPlaying: Boolean, title: String? = null, subtitle: String? = null)
```
In audiobook mode, `isPlaying` mirrors `isPlayerRunning`. Updates state + fires `onStateChangedCallback`.

```kotlin
fun triggerPlay() / triggerPause() / triggerNext() / triggerPrev() / triggerNextChapter()
```
Invoke corresponding action lambdas.

```kotlin
fun setActiveTtsTabId(id: Long?)
fun setCurrentlyActiveTabId(id: Long?)
fun setPlayTrackInputList(list: List<String>)  // Caps at 300 via list.take(300)
fun setWebSpeakNativeFallbackList(list: List<String>)  // Same 300 cap
fun setWebSpeakNativeFallbackIndex(index: Int)
fun setCurrentTrackIndex(index: Int)
fun setIsPlayerRunning(running: Boolean)  // In audiobook mode, mirrors to isPlaying
fun setIsAudiobookModeActive(active: Boolean)  // Immediately syncs isPlaying = isPlayerRunning
fun setCurrentlySpeakingText(text: String)
fun setNovelAndChapter(novel: String, chapter: String)
fun setActiveWebsite(website: String)
fun setExtractedUrl(url: String)
fun setTtsSpeed(speed: Float) / setTtsPitch(pitch: Float) / setTtsAccent(accent: String)
fun setTtsVoiceName(name: String) / setAvailableVoices(voices: List<String>)
```
TTS config setters also fire `onStateChangedCallback` for notification refresh.

---

## 6. WtrLogManager.kt (93 lines)

**Type:** `object` (Singleton)

Compose-observable `mutableStateListOf<String>` (max 100 entries, newest first). Thread-safe via `synchronized(lock)` + `Handler(Looper.getMainLooper)`. `ThreadLocal<SimpleDateFormat>` for `"HH:mm:ss.SSS"` formatting. Persistence: `SharedPreferences` key `"saved_logs_serialized"` with `"||LC||"` delimiter, written on background `CoroutineScope(Dispatchers.IO + SupervisorJob)`.

```kotlin
fun initialize(context: Context)           // Load enable_logs pref + deserialize persisted logs
fun log(context: Context?, msg: String)    // Format, prepend to list, cap at 100, persist async
fun setLoggingEnabled(context, enabled)    // Toggle; if disabled, clears memory + storage
fun isLoggingEnabled(): Boolean            // Thread-safe getter
fun clear(context: Context)                // Clears memory list + SharedPreferences
```

---

## 7. BackupEncryption.kt (119 lines)

**Type:** `object` (Singleton) — AES/CBC/PKCS7Padding via AndroidKeyStore (hardware-backed)

Key: `"wtr_backup_key"` in `AndroidKeyStore`, AES-256, CBC mode, PKCS7 padding. Keys never leave secure hardware.

```kotlin
fun encryptBackup(plaintext: String): String
// Returns Base64(IV[16 bytes] + ciphertext). Throws RuntimeException.

fun decryptBackup(ciphertext: String): String
// Decodes Base64, extracts 16-byte IV, decrypts. Throws on <16 bytes.

fun getEncryptingStream(outputStream: OutputStream): OutputStream
// Pipeline: CipherOutputStream → Base64OutputStream → destination. Writes IV first. O(1) memory.

fun getDecryptingStream(inputStream: InputStream): InputStream
// Pipeline: source → Base64InputStream → CipherInputStream. Reads 16-byte IV first. O(1) memory.
```

---

## 8. StreamingJsonParser.kt (237 lines)

**Type:** `object` (Singleton) — O(1) memory pull parser using `android.util.JsonReader`.

```kotlin
class BackupData(
    val version: Int, val timestamp: Long,
    val settings: Map<String, Any>,
    val history: List<HistoryEntry>,
    val bookmarks: List<BookmarkEntry>,
    val tabs: List<TabEntry>
)

fun parseBackupStream(inputStream: InputStream): BackupData
```
Incrementally parses: `version` (Int), `timestamp` (Long), `settings` (dynamic `Map<String, Any>` using `reader.peek()` for type detection; numbers auto-detect Int vs Double via `doubleVal == doubleVal.toInt().toDouble()`), `history[]`, `bookmarks[]` (6 nullable fields with `peek()==NULL` guard), `tabs[]`. Unknown keys→`skipValue()`. Reader closed in finally.

---

## 9. CrashReportManager.kt (70 lines)

**Type:** `object` (Singleton) — `UncaughtExceptionHandler` chain.

```kotlin
fun init(context: Context)
// Captures originalHandler, installs new handler that saves report then rethrows.

private fun saveCrashReport(context, thread, exception)
// Writes to {filesDir}/crash_reports/crash_{epoch}.log: timestamp, thread, exception class/message,
// full stack trace, last 20 WtrLogManager entries.

fun getCrashReports(context: Context): List<File>
fun clearOldCrashReports(context: Context, olderThanDays: Int = 7)
// Deletes files with lastModified < (now - days*86400000).
```
Handler chaining preserves system crash dialog and ANR reporting.

---

## 10. PerformanceMonitor.kt (58 lines)

**Type:** `object` (Singleton)

```kotlin
data class MemoryStats(val nativeHeap: Long, val javaHeap: Long, val totalRss: Long)

fun getMemoryStats(context: Context): MemoryStats
// nativeHeap=Debug.getNativeHeapSize(), javaHeap=Runtime totalMemory()-freeMemory(), totalRss=ActivityManager.MemoryInfo.totalMem

suspend fun monitorPerformance(context: Context, intervalMs: Long = 30000L)
// Cancellable loop: heapUsagePercent > 80% → warning log; > 95% → System.gc(). Re-throws CancellationException.
```

---

## 11. NetworkErrorHandler.kt (30 lines)

**Type:** `object` (Singleton)

```kotlin
suspend fun <T> executeWithRetry(
    context: Context, maxRetries: Int = 3, backoffMs: Long = 1000L,
    block: suspend () -> T
): Result<T>
```
Attempts `block()` immediately. On failure: retries with linear backoff `delay(backoffMs * attempt)` (1s, 2s, 3s). Logs each retry via `WtrLogManager`. Returns `Result.success` or `Result.failure(lastException)`.

---

## 12. GeminiTranslator.kt (100 lines)

**Type:** `object` (Singleton) — Google Gemini 2.5 Flash for Chinese→English novel translation.

System prompt: expert light novel localizer, character consistency, idiom localization.

```kotlin
suspend fun translateParagraphs(paragraphs: List<String>, apiKey: String): List<String>
```
Dispatchers.IO. Pipeline: builds JSON `[{id, text}, ...]` input → `GenerativeModel("gemini-2.5-flash", temperature=0.3, responseMimeType="application/json")` → `generateContent()` → strips markdown code fences (`\`\`\`json...\`\`\``) → parses response `[{id, text}]` → maps by id back to original order. Graceful fallback: returns original text if translation missing for any index.

---

## 13. BrowserSection.kt (5 lines)

```kotlin
enum class BrowserSection {
    WEB,        // Main browser / WebView content
    TABS,       // Tab switcher panel
    BOOKMARKS,  // Bookmarks list panel
    HISTORY,    // Browsing history panel
    SETTINGS    // Application settings panel
}
```
Drives navigation state transitions in the Compose UI layer.
