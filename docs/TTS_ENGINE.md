# Speech Synthesis & Background Text-To-Speech Engine

> Detailed architecture of the background Text-To-Speech (TTS) engine, the Web Speech
> API polyfill, and lockscreen/media session coordination in the Novel Reader app.

---

## Table of Contents

1. [TTS Architecture Diagram](#1-tts-architecture-diagram)
2. [SpeechSynthesis Polyfill (WebScripts.kt)](#2-speechsynthesis-polyfill-webscriptskt)
3. [Foreground Service & Notification Control](#3-foreground-service--notification-control)
4. [Audiobook Mode (TrackPlayer Engine)](#4-audiobook-mode-trackplayer-engine)
5. [Backup Takeover Mechanism](#5-backup-takeover-mechanism)
6. [Dynamic Accent & Voice Resolution](#6-dynamic-accent--voice-resolution)
7. [WtrTtsTriggerEvent Spec (JS-to-Native Coordination)](#7-wtrttstriggerevent-spec-js-to-native-coordination)
8. [Settings Reference](#8-settings-reference)

---

## 1. TTS Architecture Diagram

```
┌────────────────────────────────────────────────────────┐
│                      WebView (UI)                      │
│                                                        │
│  [ Web Speech API (Polyfill) ]                         │
│         │                                              │
│         ├── speak()                                    │
│         ▼                                              │
│  ┌───────────────────────┐                             │
│  │  WtrWebAppInterface   │                             │
│  │   "WtrBridge" (tabId) │                             │
│  └──────────┬────────────┘                             │
└─────────────┼──────────────────────────────────────────┘
              │ speakNative() / cancelNative()
              ▼
┌────────────────────────────────────────────────────────┐
│               WtrAudioControlBridge (Singleton)        │
│                                                        │
│  (StateFlows & callbacks routing)                      │
└─────────────┬──────────────────────────────────────────┘
              │ [speakNative] callback
              ▼
┌────────────────────────────────────────────────────────┐
│             WtrBrowserService (Foreground)             │
│                                                        │
│  ┌────────────────────────┐  ┌──────────────────────┐  │
│  │  Android TTS Engine    │  │  MediaSession        │  │
│  ├────────────────────────┤  ├──────────────────────┤  │
│  │  - speak(QUEUE_FLUSH)  │  │  - Lockscreen UI     │  │
│  │  - Range listener      │  │  - Headset hooks     │  │
│  └──────────┬─────────────┘  └──────────┬───────────┘  │
└─────────────┼───────────────────────────┼──────────────┘
              │ onWebViewProgressTrigger  │ onMediaButton
              ▼                           ▼
┌────────────────────────────────────────────────────────┐
│                      WebView (UI)                      │
│                                                        │
│  [ JS event listeners ]   ◄─── MediaSession routes     │
│  - Highlight sentences         back to JS actions       │
│  - Auto-scroll viewport                                │
└────────────────────────────────────────────────────────┘
```

---

## 2. SpeechSynthesis Polyfill (WebScripts.kt)

**Type:** Injected JavaScript String (~420 lines)

The polyfill is critical. It overrides standard Web Speech API bindings so that any web-based player (such as the one on `wtr-lab.com`) that calls `speechSynthesis` is intercepted and routed natively.

### Polyfill Invariants

- **Namespace Injection:** Injected into `window.WtrTtsPolyfill` with a guard (`window.WtrTtsPolyfilled = true`) to detect and prevent duplicate injections.
- **Self-Healing Loop:** A `setInterval` loop runs every 1 second. If any site script overrides `window.speechSynthesis`, the polyfill re-applies itself automatically.
- **Audio Hooking:** Patches `HTMLAudioElement.prototype.play` and `pause` to synchronize playback state indicators with the native player.

### Override Specs

1. **MockVoice:** Exposes 7 pre-defined system voices:
   - `en-US` (Google US English), `en-GB` (Google UK English), `zh-CN` (Google Chinese), `es-ES` (Spanish), `vi-VN` (Vietnamese).
2. **MockSpeechSynthesisUtterance:** Retains public fields for `text`, `volume`, `rate`, `pitch`, `lang`, and exposes event listeners (`onstart`, `onend`, `onpause`, `onresume`, `onerror`, `onboundary`).
3. **MockSpeechSynthesis:** Overrides `speak()`, `cancel()`, `pause()`, `resume()`:
   - Sets local flags (`speaking = true`, `paused = false`).
   - Delegates commands to native handler (`window.WtrBridge.speakNative()`, etc.).

### Periodic Polling

A 1500ms polling interval ensures that even if sites use non-standard play/pause configurations, the native bridge is updated:
- Automatically extracts novel cover image, title, and paragraph nodes via DOM queries (`WebsiteSupport`).
- Sends parsed metadata to `WtrBridge.syncMetadata()` for updating bookmark models.
- If audio elements are playing, synchronization state is pushed to `postPlaybackState()`.

---

## 3. Foreground Service & Notification Control

**File:** `WtrBrowserService.kt` (~900 lines)

The service acts as the native audiobook player. It manages the `TextToSpeech` engine and media session controls.

### Android 14+ Foreground Service

- **Type:** `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` in AndroidManifest.
- **Notification ID:** `4048`.
- **Ongoing:** Set to `true` during active TTS playback. Allows background execution under standby conditions.

### Notification Throttling & Coalescing

- **Frequency Gate:** 1500 milliseconds minimum between system notification draw updates.
- **Coalescing Pattern:** Rapid status queries (such as rapid paragraph word-boundary tracking) are filtered out. If the playing status, title, and paragraph subtitle have not changed from the last render state, updates are skipped.
- **Debounced Runs:** If updates occur within the 1500ms window, they are queued and executed after the remaining interval has elapsed using a Main-thread `Handler.postDelayed` runner. State transitions (PLAY to PAUSE) are executed immediately.

### MediaSession Actions

- Enforces standard audiobook playback controls: `PLAY`, `PAUSE`, `SKIP_TO_NEXT`, `SKIP_TO_PREVIOUS`.
- Updates `MediaSession` playback state dynamically:

```kotlin
val playbackState = PlaybackState.Builder()
    .setActions(PlaybackState.ACTION_PLAY
         or PlaybackState.ACTION_PAUSE
         or PlaybackState.ACTION_SKIP_TO_NEXT
         or PlaybackState.ACTION_SKIP_TO_PREVIOUS)
    .setState(if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
              lastWordIndex.toLong(), ttsSpeed)
    .build()
mediaSession?.setPlaybackState(playbackState)
```

---

## 4. Audiobook Mode (TrackPlayer Engine)

On standard websites (excluding `wtr-lab.com`), the WebView lacks its own speech synthesis scripts. The app provides a native **TrackPlayer engine** that extracts paragraphs from the DOM and plays them sequentially.

```
onPageFinished
    │
    ▼
Is Website TrackPlayer enabled in Settings?
    ├─ No: Do nothing
    └─ Yes:
        │
        ▼
1. Extract paragraphs via WebsiteSupport selectors
        │
        ▼
2. Slice into array of plain text (cap at 300 entries)
        │
        ▼
3. Set arrays in WtrAudioControlBridge:
   - setPlayTrackInputList(paragraphs)
   - setWebSpeakNativeFallbackList(paragraphs)
        │
        ▼
4. Activate TrackPlayer controls in bottom shelf UI
        │
        ▼
5. Play action:
   - setWebSpeakNativeFallbackIndex(currentParagraphIndex)
   - speaks native paragraph with track metadata
   - onDone callback increments fallbackIndex, speaks next track
        │
        ▼
6. Last paragraph finished:
   - calls `WtrNextChapterHandler.handleNativeNextChapter()` (background-safe)
   - Handler resolves next chapter URL via pure HTTP (no WebView JS)
   - Applies Google Translate proxy if needed
   - Loads URL in TTS-active tab's WebView via `onLoadUrlInWebView` callback
   - `onPageFinished` fires → `pageLoadBackgroundLogic` → extract → play
   - Cycle repeats automatically ✓
```

### Native Background-Safe Auto-Next (v4)

Starting from v4, the auto-next chapter flow no longer depends on WebView JavaScript when the app may be backgrounded. The old `triggerNextChapterNavigation` (which used `webView.evaluateJavascript()` to click buttons) is replaced by `WtrNextChapterHandler.handleNativeNextChapter()` for automatic advances:

```
Last paragraph onDone (WtrBrowserService)
    │
    ▼
WtrNextChapterHandler.handleNativeNextChapter()
    │
    ├─ Read settings from SharedPreferences via WtrAudioControlBridge.lastKnownContext
    │
    ├─ WtrChapterUrlResolver.resolveNextChapterUrl()  [IO thread, HttpURLConnection]
    │     ├─ Fetch current page HTML
    │     ├─ Parse <a> tags for next-chapter link
    │     │   ├─ Site-specific: timotxt → "下一章", webnovel → book/N patterns
    │     │   ├─ Generic: .btn-next, .next-chapter classes
    │     │   └─ Fallback: numeric chapter-N → chapter-(N+1)
    │     └─ Return next chapter URL
    │
    ├─ Apply Google Translate proxy if needed (getProxyTranslatedUrl)
    │
    ├─ Anti-CAPTCHA delay (4.5s) if translating
    │
    ├─ WtrAudioControlBridge.onLoadUrlInWebView(url)
    │     └─ Loads URL in the TTS-active tab's WebView (may not be visible)
    │
    └─ Poll for extraction completion (up to 25s)
         ├─ Success: playTrackInputList populated, isPlayerRunning = true
         └─ Timeout: trigger fallback via onManualExtractAndPlay
```

This ensures seamless background chapter transitions even when:
- The screen is off (WebView JS is throttled)
- The app is in the background (Compose `LaunchedEffect` is paused)
- Google Translate needs to load the page

### Invariants: Track Cap

To prevent extreme memory allocation spikes during DOM evaluations on large page lists, paragraph arrays are capped at 300 entries (`list.take(300)`).

---

## 5. Backup Takeover Mechanism

Background browser execution (tab-swapping, device locking) causes WebKit's JS engine to pause. On `wtr-lab.com`, this breaks the JS loop, preventing it from requesting the next paragraph.

`WtrBrowserService` implements a **Backup Takeover Mechanism** to handle this:

```
TTS starts speaking (via speakNative JS call)
    │
    ▼
Service schedules a takeover timeout timer (3000ms delay)
    │
    ▼
Utterance terminates (onDone callback)
    │
    ├── WebView JS sends next text within 3000ms:
    │     - Takeover timer is cancelled
    │     - isBackupTakeoverActive resets to false
    │     - Playback continues via normal JS bridge path ✓
    │
    └── WebView JS fails to send next text (throttled):
          - Takeover timer fires!
          - isBackupTakeoverActive set to true
          - Service scans webSpeakNativeFallbackList
          - Finds current index, increments it, speaks next text natively
          - TTS event updates to WebView JS are suppressed
          - Next paragraph onDone fires takeover timeout in 100ms
          - Continuous native background playback achieved ✓
```

Once the user reopens the app, the first manual play/pause action resets `isBackupTakeoverActive` to `false` and restores the standard JS-driven bridge path.

---

## 6. Dynamic Accent & Voice Resolution

### Accent Groups

For standard English TTS (`en-US`), the user can choose from 4 accents in Settings:
- `"US"` → `Locale.US`
- `"UK"` → `Locale.UK`
- `"AU"` → `Locale("en", "AU")`
- `"IN"` → `Locale("en", "IN")`

### Voice Selection

If a specific system voice matches the user configuration (`tts_voice_name`), it is selected. Otherwise, the locale's default voice is used.

### Language Detection

- If a paragraph contains primarily English characters, the selected English voice and accent are locked.
- If Chinese or Cyrillic characters dominate, the localized engine re-initializes (Chinese: `zh-CN`, Russian: `ru-RU`).
- **Optimization (`isPlaylistPrimarilyEnglish`):** Compares character counts in the first 15 paragraphs of the playlist. If English represents the majority, dynamic language switching is bypassed. This prevents jarring voice swaps and 5-second delays on stray untranslated Chinese lines.

---

## 7. WtrTtsTriggerEvent Spec (JS-to-Native Coordination)

When native TTS completes a paragraph or hits a word boundary, status events must be reported back to the WebView JS to keep sentence highlights synchronized.

```kotlin
fun invokeWtrTtsTriggerEvent(event: String, charIndex: Int = 0) {
    if (isBackupTakeoverActive) return // Suppressed in background
    val jsCall = "javascript:(function(){ if(window.WtrTtsTriggerEvent) window.WtrTtsTriggerEvent('$event', $charIndex); })()"
    // Dispatched to WebView on Main thread
}
```

### Supported Event Statuses

| Event | Fired When | JS Response |
|-------|------------|-------------|
| `"start"` | Audio starts speaking | Highlights first sentence of active paragraph |
| `"boundary"` | TTS reaches word boundary | Updates text highlight index to current character pointer |
| `"end"` | Utterance completes | Marks paragraph complete, triggers next speech |
| `"pause"` | Playback paused | Freezes active sentence highlight |
| `"resume"` | Playback resumed | Restores active highlights |
| `"error"` | Engine error | Resets UI playback state |

---

## 8. Settings Reference

All TTS and audio-related SharedPreferences keys in `wtr_browser_settings`:

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enable_web_trackplayer` | Boolean | `false` | Enable paragraph TrackPlayer extraction on non-polyfilled sites |
| `auto_focus_paragraphs` | Boolean | `true` | Auto-scrolls WebView to the currently spoken paragraph |
| `remember_paragraphs` | Boolean | `true` | Save reading paragraph index per chapter URL |
| `tts_speed` | String | `"4.0"` | Speech rate multiplier (normalized: 0.5f to 4.5f) |
| `tts_pitch` | String | `"1.0"` | Pitch multiplier (0.5f to 2.0f) |
| `tts_accent` | String | `"US"` | English dialect voice accent (US/UK/AU/IN) |
| `tts_voice_name` | String | `""` | Specific system Voice identifier name |
