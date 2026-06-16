package com.paras.novelreaderkt.sites.wtrlab

import com.paras.novelreaderkt.sites.WebsiteSupport
import com.paras.novelreaderkt.sites.commons.CommonSelectors

/**
 * WTR-Lab — Primary site with deep JS bridge integration.
 *
 * Content structure: Each chapter is a `<div class="chapter-container" id="chapter-N">`.
 * Individual text lines are `<div class="wtr-line">` divs (NOT `<p>` tags).
 * Uses Tailwind CSS. SSR with multi-chapter infinite scroll.
 *
 * IMPORTANT: The ad-blocker detection on WTR-Lab checks `window.speechSynthesis`.
 * The TTS polyfill bridge MUST never be disconnected (see AGENTS.md Rule 5).
 *
 * To add/edit WTR-Lab support, modify ONLY this file + assets/sites/wtrlab/extractor.js
 */
class WtrLabSupport : WebsiteSupport {
    override val siteId = "wtr-lab"
    override val domains = listOf("wtr-lab.com", "wtr-lab.co")
    override val keywords = listOf("wtr", "wtrlab")
    override val requiresAutoTranslate = false

    // FIXED: Old selectors (.read-content, #content, .wtr-reader-content, .chapter-content)
    // were ALL wrong. WTR-Lab uses .chapter-body and .wtr-line divs.
    override val containerSelectors = listOf(".chapter-body", ".chapter-container", ".wtr-reader-content")
    override val paragraphSelector = "div.wtr-line, p, .wtr-line-segment"
    override val excludeSelectors = CommonSelectors.COMMON_EXCLUDE + listOf(
        ".wtr-adblock-off", ".ad-blocker-message", ".wtr-adblock-message",
        "script", "style", "noscript", ".chapter-nav", ".chapter-controls"
    )
    override val requiresBrPreparation = false

    override val siteSpecificJunkKeywords = emptyList<String>()
    override val adBlockKeywords = emptyList<String>()
    override val titleSuffixes = emptyList<String>()

    // WTR-Lab uses custom div-based lines, not standard <p> extraction
    override val customJsExtractor = "sites/wtrlab/extractor.js"
    override val isSPA = false
}