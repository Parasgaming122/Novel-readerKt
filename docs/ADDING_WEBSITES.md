# Adding Support for New Websites

Comprehensive developer guide for adding new novel website support to the
Wtr-Browser extraction engine.

---

## 1. Overview

The extraction engine separates novel text from layout bloat, ads, and user
commentaries using a centralized site registry with CSS selectors and keyword
filtering. When a user opens a chapter page, the engine identifies the hosting
site via domain matching, applies the corresponding extraction configuration,
and produces a clean paragraph list suitable for TTS audiobook playback.

The entire pipeline runs inside a WebView `evaluateJavascript` call, with
retry/exponential-backoff logic in Kotlin, ensuring zero main-thread blocking
while gracefully handling slow AJAX content and proxy translation delays.

---

## 2. Core Architectural Principles

### 2.1 Non-Overlapping Containers (Anti-Nesting)

JavaScript filters nested containers and keeps only top-level matches. If your
`containerSelectors` include both `#content` and `.chapter-inner` where the
latter is a child of the former, the engine automatically removes the child
from the candidate list, preventing double extraction.

### 2.2 Seen-Element Tracking (De-duplication)

A strict in-flight `Set` of processed HTML element references guarantees that
each paragraph DOM node is processed at most once, even if multiple selectors
match the same element. This prevents chapters from being read twice (e.g.,
extracting 186 items instead of 93).

### 2.3 Robust Sanitization (Junk Mitigation)

Global exclusion selectors remove navigation, ads, comments, and share widgets
before text is extracted. A two-tier keyword filter (global `GENERIC_PROMO`
+ site-specific `siteSpecificJunkKeywords`) strips promotional text, author
notes, and navigation prompts from the final paragraph list.

---

## 3. The WebsiteSupport Interface (Full API)

Defined in `app/src/main/java/com/example/sites/WebsiteSupport.kt`:

```kotlin
interface WebsiteSupport {
    val siteId: String              // Unique identifier (e.g. "my-site")
    val domains: List<String>       // Domain match list (e.g. ["mysite.com"])
    val keywords: List<String>      // URL bar keyword shortcuts
    val requiresAutoTranslate: Boolean  // Google Translate proxy redirect
    val containerSelectors: List<String>  // CSS selectors for content containers
    val paragraphSelector: String   // Paragraph element selector within containers
    val excludeSelectors: List<String>    // Elements to exclude from extraction
    val requiresBrPreparation: Boolean    // <br> to <span> conversion needed
    val siteSpecificJunkKeywords: List<String>  // Text to filter from paragraphs
    val adBlockKeywords: List<String>     // Ad domains to block at resource level
    val titleSuffixes: List<String>       // Suffixes to strip from page titles
}
```

**Property details:**

- **siteId**: Unique string identifier. Must not collide with existing IDs.
  Convention: lowercase, hyphen-separated.
- **domains**: The registry strips `www.` and `.translate.goog` before
  comparing. Supports multiple domains per site.
- **keywords**: Case-insensitive URL bar shortcuts for navigation lookup.
- **requiresAutoTranslate**: When true, chapter URLs redirect through
  `translate.goog` before extraction. Used for non-English source sites.
- **containerSelectors**: Priority-ordered CSS selectors. Nested containers
  are automatically de-duplicated by the anti-nesting resolver.
- **paragraphSelector**: Defaults to `CommonSelectors.STANDARD_PARAGRAPH`
  (`"p, .wtr-line-segment"`). Override only for non-standard markup.
- **excludeSelectors**: Typically `CommonSelectors.COMMON_EXCLUDE` (40+
  selectors). Append site-specific selectors as needed.
- **requiresBrPreparation**: Set true when text is a single block separated
  by `<br>` tags instead of individual `<p>` elements.
- **siteSpecificJunkKeywords**: Combined with global `GENERIC_PROMO` list
  at runtime to filter promotional/nav text from paragraphs.
- **adBlockKeywords**: Domain strings blocked in WebView resource interception.
- **titleSuffixes**: Stripped by `extractNovelAndChapter()` before regex-based
  novel/chapter title splitting.

---

## 4. Step-by-Step Implementation Guide

### Step 1: Analyze the Site Layout

Open the target chapter URL in a desktop browser (F12 DevTools) and inspect:

1. **Outer Container** - Find the CSS class/ID wrapping all chapter text.
   Walk up the DOM from a paragraph to find the smallest containing element.
2. **Paragraph Elements** - Check if paragraphs use `<p>` tags or are
   `<br>`-separated blocks within a single `<div>`.
3. **Excluded Content** - Note CSS classes of ads, share widgets, nav links,
   author notes, and comment sections.
4. **Title Format** - Observe the `<title>` tag and note site-specific
  suffixes appended after the novel/chapter name.

### Step 2: Create the Implementation Class

Add your class to `WebsiteSupportImpls.kt`:

```kotlin
import com.example.sites.WebsiteSupport
import com.example.sites.commons.CommonSelectors

class MyNovelSiteSupport : WebsiteSupport {
    override val siteId = "mynovelsite"
    override val domains = listOf("mynovelsite.com", "mynovelsite.org")
    override val keywords = listOf("mns", "mynovel")
    override val requiresAutoTranslate = false
    override val containerSelectors = listOf(
        "#chapter-content", ".chapter-inner", ".read-area"
    )
    override val paragraphSelector = CommonSelectors.STANDARD_PARAGRAPH
    override val excludeSelectors = CommonSelectors.COMMON_EXCLUDE + listOf(
        ".site-specific-ad-banner", "#chapter-author-note"
    )
    override val requiresBrPreparation = false
    override val siteSpecificJunkKeywords = listOf(
        "read at mynovelsite", "bookmark this chapter"
    )
    override val adBlockKeywords = emptyList<String>()
    override val titleSuffixes = listOf(
        " - MyNovelSite", "_mynovelsite.com"
    )
}
```

### Step 3: Register in the WebsiteSupportRegistry

Add your instance to the `supports` list in `WebsiteSupportRegistry.kt`:

```kotlin
val supports = listOf(
    WtrLabSupport(), WebNovelSupport(), /* ... */
    MyNovelSiteSupport()  // <-- Register here
)
```

### Step 4: Test with the In-App Log Viewer

1. Enable logging: **Settings > Enable Logs**.
2. Navigate to a chapter page on the target site.
3. Trigger audiobook mode (play button).
4. Open **Settings > View Diagnostic Logs**.
5. Verify correct paragraph count and no `"JS Extraction Error"` lines.
6. Confirm the Toast: `"Ready! Starting at Paragraph N"`.

---

## 5. Reusable Commons (Full API Reference)

All commons are in `app/src/main/java/com/example/sites/commons/Commons.kt`.

### 5.1 CommonSelectors

| Constant | Value | Purpose |
|---|---|---|
| `STANDARD_PARAGRAPH` | `"p, .wtr-line-segment"` | Default paragraph selector |
| `COMMON_EXCLUDE` | (40+ selectors) | Universal exclusion list |

**COMMON_EXCLUDE covers:** author notes (`.author-note`), recommendations
(`.recommend-box`, `.j_recommendation`), comments (`.comment-area`,
`.user-opinion`), navigation (`.cha-nav`, `.next_chap`, `.prev_chap`,
`.chapter-nav`, `.txtnav`), ads (`.ads`, `.adsbygoogle`), meta/layout
(`.desc`, `.title-book`, `.bottem`), and page chrome (`.footer`, `.header`,
`#header`, `#footer`).

### 5.2 CommonJunkKeywords.GENERIC_PROMO

Global junk phrase list applied to paragraphs under 250 characters. Contains
30+ English phrases (`join our discord`, `patreon`, `support the author`,
`author's note`, `next chapter`, `ad blocker detected`, etc.) and 16+ Chinese
phrases (`本章未完`, `点击下一页`, `继续阅读`, `下一章`, `目录`, `书架`, etc.).

### 5.3 CommonPatterns

**TITLE_PATTERNS** (6 regex patterns):

| # | Pattern | Matches |
|---|---|---|
| 1 | `(?i)(?:Chapter\|Ch\.\|Ch\|Episode)\s*(\d+...))` | `Chapter 42`, `Ch. 1.5` |
| 2 | `(?i)\b(?:chapter\|chap\|ch\|episode\|ep)\.?\s*([ivxldcm]+)` | `Chapter XII` |
| 3 | `(第\s*[0-9一二三四五六七八九十百千万]+[章回节集卷折篇])` | `第42章` |
| 4 | `(?i)Chapter\s*([a-zA-Z0-9]+)` | `Chapter 1A` |
| 5 | `(?i)Ch\s*([a-zA-Z0-9]+)` | `Ch 2b` |
| 6 | `\b(\d+)\s*$` | Trailing numbers |

**URL_PATTERNS** (4 regex patterns): `chapter[-_]?(\d+)`, `ch[-_]?(\d+)`,
`/(\d+)\.html`, `/(\d+)`.

---

## 6. Advanced Extraction Mechanics

### 6.1 BR Preparation (`requiresBrPreparation = true`)

Converts `<br>`-separated text blocks into `<span class="wtr-line-segment">`
elements so the standard paragraph selector can match them:

```javascript
function prepareBrParagraphs(contentEl) {
    if (!contentEl || contentEl.querySelector('.wtr-line-segment')) return;
    let children = Array.from(contentEl.childNodes);
    let newHtml = "", currentGroup = "";
    children.forEach(node => {
        if (node.nodeType === 3) currentGroup += node.textContent;
        else if (node.nodeType === 1) {
            if (node.tagName.toLowerCase() === 'br') {
                if (currentGroup.trim().length > 0) {
                    newHtml += '<span class="wtr-line-segment">'
                        + currentGroup.trim() + '</span><br>';
                    currentGroup = "";
                }
            } else if (node.tagName.toLowerCase() === 'p') {
                if (currentGroup.trim().length > 0) {
                    newHtml += '<span class="wtr-line-segment">'
                        + currentGroup.trim() + '</span>';
                    currentGroup = "";
                }
                newHtml += node.outerHTML;
            } else currentGroup += node.outerHTML;
        }
    });
    if (currentGroup.trim().length > 0)
        newHtml += '<span class="wtr-line-segment">' + currentGroup.trim() + '</span>';
    if (newHtml.length > 10) contentEl.innerHTML = newHtml;
}
```

### 6.2 Multi-Container Nesting Resolver

Filters nested containers to keep only top-level matches:

```javascript
let rawContainers = Array.from(document.querySelectorAll(containerSelector));
containers = rawContainers.filter(
    c => !rawContainers.some(other => other !== c && other.contains(c))
);
```

### 6.3 DOM Element De-duplication

A `Set` tracks processed paragraph nodes across all containers:

```javascript
let seenPTags = new Set();
pTags.forEach(p => {
    if (!p.closest(excludeClass) && !seenPTags.has(p)) {
        seenPTags.add(p);
        let text = p.innerText.trim();
        if (text.length > 5 && !isJunk(text)) {
            paragraphs.push(text);
            elements.push(p);
        }
    }
});
```

### 6.4 Junk Filtering

The `isJunk()` function combines global promo keywords, site-specific junk
keywords, ad-blocker detection warnings, and short URL-only line checks.
Global keyword matching only applies to paragraphs under 250 characters to
avoid false positives on legitimate long paragraphs.

### 6.5 Site-Specific Paragraph Selectors for Highlighting

In `BrowserAppScreen.kt`, the `matchedSupport?.paragraphSelector` is passed
into the JavaScript extractor and also used for highlighting the
currently-spoken paragraph in the WebView during TTS playback, ensuring
visual feedback aligns with extracted text.

---

## 7. Currently Supported Sites Reference

| # | Class | siteId | Domains | Containers | Translate | BR | Suffixes | Notes |
|---|---|---|---|---|---|---|---|---|
| 1 | `WtrLabSupport` | `wtr-lab` | wtr-lab.com, wtr-lab.co | `.read-content`, `#content`, `.wtr-reader-content`, `.chapter-content` | No | No | (none) | Primary companion site |
| 2 | `WebNovelSupport` | `webnovel` | webnovel.com | `.cha-content`, `.chapter-content`, `.cha-words`, `.chapter-inner` | No | No | ` - WebNovel` | Custom: `p, .cha-paragraph, .pirate` |
| 3 | `NovelHallSupport` | `novelhall` | novelhall.com, novelhall.net | `#htmlContent`, `.entry-content`, `.active` | No | Yes | 5 variants | Similar selectors to FanMTL |
| 4 | `FanMtlSupport` | `fanmtl` | fanmtl.com | `.chapter-content`, `.read-content`, `#chapter-content`, `.content-area` | No | No | ` - FanMTL` | |
| 5 | `NovelBinSupport` | `novelbin` | novelbin.com, novelbin.net | `#chr-content`, `.chr-c`, `#chapter-content`, `.chapter-container` | No | No | ` - NovelBin` | |
| 6 | `FreeWebNovelSupport` | `freewebnovel` | freewebnovel.com | `.txt`, `#htmlContent`, `.chapter-content` | No | No | ` - FreeWebNovel` | |
| 7 | `TimoTxtSupport` | `timotxt` | timotxt.com, timotxt.cn | `.show_txt`, `#content`, `.read-content` | Yes | Yes | 4 variants | `.show_txt` ranked first |
| 8 | `Novel543Support` | `novel543` | novel543.com | `#content`, `.content`, `.chapter-content`, `.article-content` | Yes | Yes | 3 variants | No `.show_txt` (avoids collision) |
| 9 | `TwkanSupport` | `twkan` | twkan.com | `#htmlContent`, `#content`, `.active`, `.read-content`, `.article-content` | Yes | No | 3 variants | Chinese aggregation site |
| 10 | `NovelHubSupport` | `novelhub` | novelhub.net | `#chr-content`, `.chapter-content`, `.read-content`, `.entry-content`, `.reader-content`, `main article` | No | No | 3 variants | Broad selector fallback |
| 11 | `NovelHubAppSupport` | `novelhubapp` | novelhubapp.com | (same as NovelHub) | Yes | No | 3 variants | SPA-style reader |

---

## 8. Testing Guide

### 8.1 Using WtrLogManager Diagnostic Panel

Enable logs in **Settings > Enable Logs**. After triggering extraction, open
**Settings > View Diagnostic Logs**. Look for `"onPageFinished"` (page loaded),
any `"JS Extraction Error"` lines (selector problems), and the Toast
`"Ready! Starting at Paragraph N"` (success).

### 8.2 Paragraph Count Verification

Compare the player UI count (e.g. "42/93") against visible paragraphs on the
page. Counts should match within 1-2 (allowing for filtered junk).

### 8.3 TTS Playback Without Duplicates

Play the full chapter. No paragraph should be read twice. If duplicates occur,
check `containerSelectors` for overlapping parent/child pairs.

### 8.4 Bookmark Auto-Detection

Close and reopen the app after reading partway. Re-trigger audiobook mode on
the same URL. The player should resume at the last-read paragraph index.

### 8.5 Title Parsing Verification

Check the notification/lockscreen. Novel name and chapter should be cleanly
separated without site suffixes. Add missing suffixes to `titleSuffixes`.

---

## 9. Troubleshooting

**Paragraphs read twice**: Selector overlap in `containerSelectors`. Remove
the broader parent selector or list child selectors first. The `seenPTags`
de-duplication guard handles most cases automatically.

**TTS skips text**: No elements match `paragraphSelector`, or content loads
via slow AJAX. Adjust the selector. The extraction loop retries up to 7s
with exponential backoff.

**Chinese text in audiobook mode**: Extraction fires before translation
completes. The engine retries when untranslated Chinese is detected. If
consistently incomplete, set `requiresAutoTranslate = true`.

**Title parsing fails**: Missing suffix in `titleSuffixes`, or chapter format
doesn't match `CommonPatterns.TITLE_PATTERNS`. Add the suffix or a new
regex pattern. URL-based fallback uses `CommonPatterns.URL_PATTERNS`.

**Ad-blocker warnings in TTS**: Site uses a unique detection message not in
the global list. Add it to `siteSpecificJunkKeywords`.
