# Worklog

---
Task ID: 4
Agent: Main Agent
Task: Performance fixes — eliminate real bottlenecks (not theoretical scalability)

Work Log:
- Read all critical source files: BrowserAppScreen.kt (3540 lines), BrowserViewModel.kt, WtrBrowserService.kt, WebScripts.kt, WtrAudioControlBridge.kt, WtrWebAppInterface.kt
- Identified 10 concrete performance issues affecting user-perceived speed
- Fixed JS TTS poll interval: 500ms → 1500ms, skip ALL work when idle (WebScripts.kt)
- Moved metadata sync (og:image, cover) out of poll loop → runs once on load + visibility change
- Replaced speechSynthesis reset check (1s setInterval → visibilitychange event only)
- Replaced SHA-256 MessageDigest with djb2 string hash in shouldInterceptRequest (every wtr-lab resource)
- Reduced TTS auto-next delays: Gemini 400ms→150ms, translate redirect 1.5s→0.9s, non-translate 800ms→400ms
- Moved updateReadingProgress() from main thread to Dispatchers.IO
- Changed tabNavigationHistory from mutableListOf to LinkedHashSet
- Added reusable mainHandler in WtrBrowserService instead of new Handler per TTS paragraph
- Added WebView memory cap (max 10, destroy oldest non-active)
- Optimized extractNovelAndChapter title cleanup: 20+ chained .replace() → single-pass indexOf loop
- Built final APK successfully (22MB)
- Created source ZIP (1.2MB)
- Updated HANDOVER.md with performance fixes table
- Updated README.md (cache description, CI trigger branch)

Stage Summary:
- 4 modified files: BrowserAppScreen.kt, BrowserViewModel.kt, WtrBrowserService.kt, WebScripts.kt
- 2 updated docs: HANDOVER.md, README.md
- APK: /download/NovelReaderV3-final-debug.apk
- ZIP: /download/NovelReaderV3-modular-final.zip

---
Task ID: 3
Agent: Main Agent
Task: Modular refactoring of Novel-readerKt scraper system

Work Log:
- Read all 16 documentation files (README, AGENTS.md, 7 docs/*.md, CODE_AUDIT_REPORT, metadata.json)
- Read all 14 source code files (sites/, ui/, data/, core files)
- Analyzed BrowserAppScreen.kt (3225 lines) extraction architecture — found 3 duplicate JS extraction flows
- Visited all 11 supported websites to verify current HTML structure
- Found 5 broken scrapers: wtrlab (CRITICAL), timotxt, novel543, novelhub, novelhubapp
- Designed modular per-website folder architecture
- Updated WebsiteSupport interface with backward-compatible `customJsExtractor` and `isSPA` properties
- Created 11 per-site Kotlin files in isolated subfolders
- Created 7 JS extractor files in assets/sites/ (shared utils + 5 site-specific)
- Created SiteExtractorHelper.kt for BrowserAppScreen integration
- Updated WebsiteSupportRegistry.kt with new imports
- Deleted old consolidated files (WebsiteSupportImpls.kt, old TwkanSupport.kt, TwkanReader.js)
- Wrote INTEGRATION_GUIDE.md with exact BrowserAppScreen.kt patch instructions

Stage Summary:
- 11 new Kotlin files: sites/{wtrlab,webnovel,novelhall,fanmtl,novelbin,freewebnovel,timotxt,novel543,twkan,novelhub,novelhubapp}/
- 7 new JS files: assets/sites/{_shared,wtrlab,webnovel,twkan,timotxt,novel543,novelhubapp}/
- 2 modified files: WebsiteSupport.kt, WebsiteSupportRegistry.kt
- 1 new utility: SiteExtractorHelper.kt
- 1 guide: INTEGRATION_GUIDE.md
- 3 deleted files: WebsiteSupportImpls.kt, TwkanSupport.kt, TwkanReader.js (×2)
- Fixed selectors: wtrlab, timotxt, novel543, novelhub, novelhubapp, webnovel