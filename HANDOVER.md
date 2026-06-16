# NovelReaderV3 — Handover Document

> **Last Updated:** 2026-06-16
> **Branch:** `modular`
> **Package:** `com.paras.novelreaderkt`
> **App ID:** `com.paras.novelreaderkt`
> **Display Name:** NovelReaderV3

---

## Quick Start

### Prerequisites
- **JDK 17** (Temurin recommended)
- **Android SDK** with API 36 installed
- **Android Studio Koala+** (2024.1+) or command-line build

### Build
```bash
cd Novel-readerKt
./gradlew assembleDebug
# APK output: app/build/outputs/apk/debug/app-debug.apk
```

### Release Build
```bash
./gradlew assembleRelease
# Requires signing config (keystore path, passwords)
```

### Clean Build (if gradle cache is stale)
```bash
./gradlew clean assembleDebug --no-daemon
```

---

## Project Structure

```
app/src/main/java/com/paras/novelreaderkt/
├── MainActivity.kt              # Entry point, permissions, WebView pool
├── BrowserViewModel.kt          # MVVM state, tab CRUD, backup/restore
├── BrowserSection.kt            # Navigation enum (WEB, TABS, BOOKMARKS, HISTORY, SETTINGS)
├── WtrBrowserService.kt         # Foreground TTS service with MediaSession
├── WtrAudioControlBridge.kt     # Singleton: TTS state, callbacks, playlist
├── WtrWebAppInterface.kt         # @JavascriptInterface bridge (one per tab)
├── WtrChapterUrlResolver.kt     # Pure-Kotlin next chapter URL resolver (background-safe)
├── WtrNextChapterHandler.kt      # Background auto-next chapter orchestrator
├── WtrLogManager.kt             # 100-entry ring buffer, debounced disk persistence
├── BackupEncryption.kt          # AES-256 KeyStore encryption
├── StreamingJsonParser.kt        # Pull parser for backup imports (<10MB memory)
├── CrashReportManager.kt        # Uncaught exception handler (7-day retention)
├── PerformanceMonitor.kt        # Heap monitor, auto-GC at 95%
├── NetworkErrorHandler.kt       # Exponential backoff retry wrapper
├── GeminiTranslator.kt          # Gemini 2.5 Flash AI translation
├── SecurePreferences.kt         # EncryptedSharedPreferences wrapper
├── data/
│   ├── AppDatabase.kt           # Room v4, fallbackToDestructiveMigration
│   ├── BookmarkEntry.kt         # Bookmark entity with novel metadata
│   ├── BrowserDao.kt            # DAO with targeted queries + indexes
│   ├── BrowserRepository.kt     # Repository with Mutex, URL normalization
│   ├── HistoryEntry.kt          # History entity
│   └── TabEntry.kt              # Tab entity
├── sites/
│   ├── WebsiteSupport.kt        # Interface for site scrapers
│   ├── SiteExtractorHelper.kt   # JS extractor invocation helper
│   ├── WebsiteSupportRegistry.kt # Domain → support impl registry
│   ├── commons/Commons.kt       # Shared selectors, patterns, junk keywords
│   ├── wtrlab/                  # Wtr-Lab.com (primary)
│   ├── webnovel/                # WebNovel.com
│   ├── novelhall/               # NovelHall.com
│   ├── fanmtl/                  # FanMTL.com
│   ├── novelbin/                # NovelBin.com
│   ├── freewebnovel/            # FreeWebNovel.com
│   ├── timotxt/                 # TimoTxt.com (auto-translate)
│   ├── novel543/                # Novel543.com (auto-translate)
│   ├── twkan/                   # Twkan.com (auto-translate)
│   ├── novelhub/                # NovelHub.net
│   └── novelhubapp/             # NovelHubApp.com (auto-translate)
└── ui/
    ├── BrowserAppScreen.kt      # Main screen (~3530 lines)
    ├── SettingsPanel.kt         # 5-section settings
    ├── TabsPanel.kt             # Tab grid with grouping
    ├── BookmarksPanel.kt        # Novel bookmarks
    ├── HistoryPanel.kt          # Browsing history
    ├── ChromeNewTabPage.kt      # New tab shortcuts
    ├── WebScripts.kt            # JS injection utilities
    └── theme/                   # Material 3 theming (6 color schemes)
```

---

## Tech Stack

| Component | Version |
|-----------|---------|
| Kotlin | 2.2.10 |
| AGP | 9.1.1 |
| Gradle | 9.3.1 |
| Compose BOM | 2024.09.00 |
| Room | 2.7.0 (KSP) |
| Coroutines | 1.10.2 |
| Google Generative AI | 0.9.0 |
| OkHttp | 4.10.0 |
| Retrofit | 2.12.0 |
| Moshi | 1.15.2 |
| Coil | 2.7.0 |
| KSP | 2.3.5 |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 36 |

---

## Critical Rules (from AGENTS.md)

1. **Never change package back to `com.example`** — ProGuard, namespace, Room all reference `com.paras.novelreaderkt`
2. **Background tab `onPageFinished` MUST NOT hijack URL bar** — Triple-gate check in `onUrlSynced`
3. **Never remove JS bridge on Wtr-Lab pages** — Triggers anti-adblock detection
4. **Always use `onPageFinished` for post-load operations** — NOT `LaunchedEffect` (pauses in background)
5. **Never use `JSONObject` for backup parsing** — Always use `StreamingJsonParser`
6. **Never use `LaunchedEffect` for page-load triggers** — Compose lifecycle pauses it in background
7. **Auto-next chapter in background uses `WtrNextChapterHandler`** — NOT WebView JS (which is throttled in background)
8. **TTS bridge injection happens in `onPageStarted` + `onPageFinished`** — NOT in `onProgressChanged`
9. **`onPageFinished` must check BOTH active tab AND TTS-active tab** — Background chapter loads use the TTS tab, not the visible tab

---

## Key Architecture Patterns

### MVVM State Flow
- `BrowserViewModel` → `StateFlow<TabEntry?>` (currentTab) → Compose `collectAsStateWithLifecycle()`
- Tab CRUD → Room DAO → `Flow<List<TabEntry>>` → ViewModel → UI
- URL navigation → `_userNavigateTrigger: SharedFlow<String>` → `LaunchedEffect` loads in WebView

### TTS Pipeline
```
WebView JS (speechSynthesis) → WtrWebAppInterface (@JavascriptInterface)
  → WtrAudioControlBridge (singleton state) → WtrBrowserService
    → Android TextToSpeech → MediaSession → Lockscreen/Notification

Auto-Next Chapter (Background-Safe):
  WtrBrowserService.onDone() → WtrNextChapterHandler.handleNativeNextChapter()
    → WtrChapterUrlResolver (HTTP, no WebView) → getProxyTranslatedUrl()
    → WtrAudioControlBridge.onLoadUrlInWebView → WebView.loadUrl()
    → onPageFinished → pageLoadBackgroundLogic → extract → play
```

### Backup/Restore
- **Export:** Settings → JSON → AES-CBC/KeyStore encrypt → SAF output
- **Import:** SAF input → detect encrypted/plain → StreamingJsonParser (pull) → Room transaction

---

## Supported Websites (11)

| Site | Auto-Translate | Notes |
|------|---------------|-------|
| wtr-lab.com | No | Primary, deep JS bridge, asset caching |
| webnovel.com | No | Dynamic container extraction |
| novelhall.com | No | #htmlContent container |
| fanmtl.com | No | .chapter-content extraction |
| novelbin.com | No | #chr-content extraction |
| freewebnovel.com | No | Simple extraction |
| timotxt.com | Yes | Google Translate proxy |
| novel543.com | Yes | Google Translate proxy |
| twkan.com | Yes | Google Translate proxy |
| novelhub.net | No | English-first |
| novelhubapp.com | Yes | Single-page reader |

---

## CI/CD

- **`.github/workflows/build-apk.yml`** — Triggers on push to `modular` branch, creates release with "(Modular)" naming
- **`.github/workflows/build-release-apk.yml`** — Manual `workflow_dispatch`, produces signed release APK
- Both use **Gradle 9.3.1** and **JDK 17**
- `validate-wrappers: false` is set (official wrapper jar is present)

---

## Database

- **Room v4** with `fallbackToDestructiveMigration()`
- **History:** Indexed on `url` and `timestamp`, auto-pruned to 500 entries
- **Bookmarks:** Indexed on `url`, `domain`, `isNovel`
- **Tabs:** Simple CRUD, ordered by timestamp ASC

---

## Security

- API keys stored in `EncryptedSharedPreferences` (AES-256 SIV/GCM via AndroidKeyStore)
- Auto-migration from legacy plaintext SharedPreferences on first boot
- `android:allowBackup="false"` blocks ADB extraction
- Backup import whitelists only known-safe SharedPreferences keys
- Network security config blocks cleartext to google.com/googleapis.com
- ProGuard/R8 enabled for release builds with comprehensive keep rules

---

## Adding a New Website

1. Create `sites/<sitename>/<Sitename>Support.kt` implementing `WebsiteSupport`
2. Add JS extractor at `assets/sites/<sitename>/extractor.js` (optional, for complex sites)
3. Register in `WebsiteSupportRegistry.kt`'s `siteImplementations` list
4. Add domain to `getAutoTranslateSites()` if it needs Google Translate
5. See `docs/ADDING_WEBSITES.md` for detailed guide

---

## Common Gotchas

- **Gradle wrapper requires 9.3.1** — AGP 9.1.1 will not work with older Gradle
- **KSP 2.3.5 vs Kotlin 2.2.10** — Minor version mismatch, build succeeds but may cause subtle codegen issues
- **WebView instances are managed in `MainActivity.activeWebViewsPool`** — Synchronized list, cleaned up in `onDestroy`
- **WtrLogManager persistence is debounced 2s** — No more disk I/O storm during TTS
- **TTS bridge is injected ONCE per page** in `onPageStarted`, NOT on every progress tick

---

## Performance Fixes (2026-06-16)

These fixes target real user-perceived slowness — not theoretical scalability.

| Fix | File | What Changed | User Impact |
|-----|------|-------------|-------------|
| JS poll interval | `WebScripts.kt` | TTS poll: 500ms → 1500ms. Skip ALL work when idle. | Less CPU drain, better battery, less lag while browsing |
| Metadata sync | `WebScripts.kt` | Moved og:image/cover extraction out of poll loop → runs once on load + visibility change | Eliminates 120+ unnecessary DOM queries/minute |
| speechSynthesis check | `WebScripts.kt` | 1s interval → visibilitychange event only | Removes 3600 useless checks/hour |
| SHA-256 → djb2 | `BrowserAppScreen.kt` | Resource cache filename hash: MessageDigest → djb2 string hash | Faster page loads on wtr-lab.com (no crypto per resource) |
| TTS auto-next delays | `BrowserAppScreen.kt` | Gemini done delay: 400ms→150ms. Translate redirect wait: 1.5s→0.9s. Non-translate: 800ms→400ms | Chapter switching in audiobook mode is noticeably snappier |
| Reading progress off main | `BrowserViewModel.kt` | `updateReadingProgress()` moved to Dispatchers.IO | Page loads no longer stutter on main thread |
| Tab navigation history | `BrowserViewModel.kt` | mutableListOf → LinkedHashSet | O(1) remove instead of O(n) scan per tab switch |
| Reusable Handler | `WtrBrowserService.kt` | Single `mainHandler` instance instead of new Handler per paragraph | No object allocation per TTS paragraph switch |
| WebView memory cap | `BrowserAppScreen.kt` | Max 10 WebViews; oldest non-active ones destroyed | Prevents OOM crashes with many tabs open |
| Title cleanup | `BrowserAppScreen.kt` | 20+ chained .replace() → single-pass indexOf loop | Faster title processing on every page load |
| Background auto-next | NEW `WtrChapterUrlResolver.kt` + `WtrNextChapterHandler.kt` | Pure HTTP next-chapter resolution + service-side orchestration replacing WebView JS dependency | Auto-next chapter works with screen off and app backgrounded; translation loads correctly every time |
| TTS-active tab extraction | `BrowserAppScreen.kt` | `onPageFinished` triggers for both active tab AND TTS-active tab; `runHtmlTextExtractionAndPlay` uses TTS tab's WebView | Paragraph extraction works in background, not just when the tab is visible |
| Translate.goog page handling | `BrowserAppScreen.kt` | `pageLoadBackgroundLogic` now properly waits for and handles translate.goog pages instead of giving up | Google Translate content loads correctly during background auto-next |

---

## Delivered Artifacts

| File | Path |
|------|------|
| Debug APK | `/download/NovelReaderV3-background-fix-debug.apk` (22MB) |
| Source ZIP | `/download/NovelReaderV3-modular-final.zip` (1.2MB) |