package com.paras.novelreaderkt.sites

interface WebsiteSupport {
    val siteId: String
    val domains: List<String>
    val keywords: List<String>
    val requiresAutoTranslate: Boolean

    // For DOM Extraction
    val containerSelectors: List<String>
    val paragraphSelector: String
    val excludeSelectors: List<String>
    val requiresBrPreparation: Boolean

    // For Junk Filtering
    val siteSpecificJunkKeywords: List<String>
    val adBlockKeywords: List<String>

    // For Title Suffixes
    val titleSuffixes: List<String>

    /**
     * Optional path to a custom JS extractor file in the assets directory.
     * When non-null, BrowserAppScreen will load this script before running
     * the standard inline extraction logic. The script must define:
     *
     *   window.__siteExtractor = function(opts) {
     *       // opts.containerSelectors, opts.paragraphSelector, etc.
     *       return { paragraphs: string[], startIndex?: number };
     *   };
     *
     * This allows per-site custom extraction without modifying BrowserAppScreen.kt.
     * The shared utility file `sites/_shared/extractor-utils.js` is always loaded
     * before the site-specific extractor and provides:
     *   - WtrExtractorUtils.isJunk(text, url, junkKeywords)
     *   - WtrExtractorUtils.prepareBrParagraphs(container)
     *   - WtrExtractorUtils.findScrollStartIndex(container)
     */
    val customJsExtractor: String? get() = null

    /**
     * Whether this site uses a single-page app architecture where content
     * is rendered entirely via JavaScript (no SSR). Sites marked true
     * require the WebView to fully execute before extraction can occur.
     */
    val isSPA: Boolean get() = false
}
