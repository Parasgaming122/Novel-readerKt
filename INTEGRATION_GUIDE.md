# Integration Guide: Modular Site Extractors

## What Changed

### New File Structure
```
sites/
├── WebsiteSupport.kt              (MODIFIED — added customJsExtractor + isSPA)
├── WebsiteSupportRegistry.kt      (MODIFIED — imports from subfolders)
├── SiteExtractorHelper.kt          (NEW — loads and runs custom JS extractors)
├── commons/Commons.kt              (UNCHANGED)
├── wtrlab/WtrLabSupport.kt         (NEW — was in WebsiteSupportImpls.kt)
├── webnovel/WebNovelSupport.kt       (NEW — was in WebsiteSupportImpls.kt)
├── novelhall/NovelHallSupport.kt    (NEW — was in WebsiteSupportImpls.kt)
├── fanmtl/FanMtlSupport.kt         (NEW — was in WebsiteSupportImpls.kt)
├── novelbin/NovelBinSupport.kt      (NEW — was in WebsiteSupportImpls.kt)
├── freewebnovel/FreeWebNovelSupport.kt (NEW — was in WebsiteSupportImpls.kt)
├── timotxt/TimoTxtSupport.kt       (NEW — was in WebsiteSupportImpls.kt, FIXED selectors)
├── novel543/Novel543Support.kt     (NEW — was in WebsiteSupportImpls.kt, FIXED selectors)
├── twkan/TwkanSupport.kt           (NEW — was TwkanSupport.kt, moved here)
├── novelhub/NovelHubSupport.kt      (NEW — was in WebsiteSupportImpls.kt, FIXED selectors)
└── novelhubapp/NovelHubAppSupport.kt (NEW — was in WebsiteSupportImpls.kt)

assets/sites/
├── _shared/extractor-utils.js      (NEW — shared JS utilities)
├── wtrlab/extractor.js             (NEW — handles div.wtr-line extraction)
├── webnovel/extractor.js           (NEW — __NEXT_DATA__ nav extraction)
├── twkan/extractor.js              (NEW — replaces dead TwkanReader.js)
├── timotxt/extractor.js             (NEW — auto-detects p vs BR)
├── novel543/extractor.js            (NEW — auto-detects p vs BR)
└── novelhubapp/extractor.js         (NEW — SPA content extraction)

DELETED:
- sites/WebsiteSupportImpls.kt       (replaced by per-site folders)
- sites/TwkanSupport.kt              (moved to twkan/TwkanSupport.kt)
- assets/TwkanReader.js              (moved to assets/sites/twkan/extractor.js)
- root TwkanReader.js                (duplicate, deleted)
```

### Fixed Selectors

| Site | Issue | Fix |
|------|-------|-----|
| **wtrlab** | All 4 selectors wrong | `.chapter-body`, `.chapter-container`, `div.wtr-line` |
| **timotxt** | `.show_txt` doesn't exist | `.chapter-content .content`, `.chapter-content` |
| **novel543** | `#content` is ambiguous | `.chapter-content .content`, `.chapter-content` |
| **novelhub** | `#chr-content` wrong ID | `#chapter-content` added as first selector |
| **novelhubapp** | Pure SPA, no SSR | Custom JS extractor with fallback heuristics |
| **webnovel** | No prev chapter in DOM | Custom JS extracts nav from `__NEXT_DATA__` |

### Interface Changes (backward-compatible)

```kotlin
// New properties with defaults — ALL existing implementations still compile
val customJsExtractor: String? get() = null   // Path to custom JS in assets
val isSPA: Boolean get() = false             // SPA detection flag
```

## Required Changes to BrowserAppScreen.kt

There are **3 extraction flows** in BrowserAppScreen.kt that should be updated.
Each needs the same pattern: check for custom JS extractor, use it if available,
fall back to existing inline JS.

### Step 1: Add Import (near top of file)

```kotlin
import com.example.sites.SiteExtractorHelper
```

### Step 2: Update Flow B — `runHtmlTextExtractionAndPlay` (~line 1048)

Find this block:
```kotlin
val matchedSupport = WebsiteSupportRegistry.findSupport(currentUrl)
val containerSels = matchedSupport?.containerSelectors ?: listOf(...)
```

**Insert BEFORE the `while (attempts < maxAttempts)` loop:**

```kotlin
// ── Try custom JS extractor (modular per-site) ──
if (SiteExtractorHelper.hasCustomExtractor(matchedSupport)) {
    val customResult = SiteExtractorHelper.extractWithCustomJs(context, webView, matchedSupport!!)
    if (customResult != null) {
        list = customResult.paragraphs
        startIdx = customResult.startIndex
        extractionSuccess = list.isNotEmpty()

        // Check for WebNovel navigation info
        val navInfo = SiteExtractorHelper.getNavInfo(webView)
        if (navInfo != null) {
            // Store nav URLs for auto-next-chapter
            // You can use navInfo["nextUrl"] and navInfo["prevUrl"]
            // to drive auto-advance in the TTS engine
        }

        if (extractionSuccess) {
            // Skip the inline JS retry loop entirely
            // Continue to the post-extraction code below
        }
    }
}
if (!extractionSuccess) {
    // ... existing inline JS extraction (UNCHANGED) ...
}
```

### Step 3: Update Flow A — `pageLoadBackgroundLogic` (~line 262)

Same pattern. Before the Gemini translation extraction JS, add:

```kotlin
// For sites with custom JS extractors, the extraction is handled
// in Flow B (runHtmlTextExtractionAndPlay). The standard inline
// JS here only needs to handle sites WITHOUT custom extractors.
// This is already the correct behavior — no change needed here
// if Flow B is updated. The Gemini translation path only uses
// inline JS for paragraph extraction, which falls back to standard
// extraction when customJsExtractor is null.
```

### Step 4: Update Flow C — Pre-cache LaunchedEffect (~line 1682)

Find the pre-cache extraction. Add before the inline JS:

```kotlin
// Try custom JS extractor for pre-cache
if (SiteExtractorHelper.hasCustomExtractor(matchedSupport)) {
    val customResult = SiteExtractorHelper.extractWithCustomJs(
        context, currentActiveWebView!!, matchedSupport
    )
    if (customResult != null && customResult.paragraphs.isNotEmpty()) {
        WtrAudioControlBridge.setWebSpeakNativeFallbackList(customResult.paragraphs)
        WtrAudioControlBridge.setWebSpeakNativeFallbackIndex(-1)
        // Skip inline JS
        return@LaunchedEffect
    }
}
```

## Adding a New Website (User Guide)

1. **Create the folder:**
   ```
   sites/mysite/
   ```

2. **Create `sites/mysite/MySiteSupport.kt`:**
   ```kotlin
   package com.example.sites.mysite
   import com.example.sites.WebsiteSupport
   import com.example.sites.commons.CommonSelectors

   class MySiteSupport : WebsiteSupport {
       override val siteId = "mysite"
       override val domains = listOf("mysite.com")
       override val keywords = listOf("mysite")
       override val requiresAutoTranslate = false
       override val containerSelectors = listOf(".chapter-text", "#content")
       override val paragraphSelector = CommonSelectors.STANDARD_PARAGRAPH
       override val excludeSelectors = CommonSelectors.COMMON_EXCLUDE
       override val requiresBrPreparation = false
       override val siteSpecificJunkKeywords = listOf("mysite")
       override val adBlockKeywords = emptyList()
       override val titleSuffixes = listOf(" - MySite")
       // Optional: override val customJsExtractor = "sites/mysite/extractor.js"
   }
   ```

3. **(Optional) Create `assets/sites/mysite/extractor.js`:**
   ```javascript
   (function() {
       'use strict';
       var utils = window.WtrExtractorUtils;
       window.__siteExtractor = function(opts) {
           // Your custom extraction logic here
           var paragraphs = utils.extractStandardParagraphs(opts);
           return { paragraphs: paragraphs, startIndex: 0 };
       };
   })();
   ```

4. **Add to `WebsiteSupportRegistry.kt`:**
   ```kotlin
   import com.example.sites.mysite.MySiteSupport
   // In the supports list:
   MySiteSupport(),
   ```

**That's it.** No other files need to be touched. Each website is fully isolated.