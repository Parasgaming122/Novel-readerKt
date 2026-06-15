package com.example.sites.twkan

import com.example.sites.WebsiteSupport
import com.example.sites.commons.CommonSelectors

/**
 * Twkan / Ttkan — Chinese novels (auto-translate via Google Translate proxy).
 *
 * Content structure: <div id="txtcontent0"> with BR-split text nodes and
 * <font> tags wrapping Google Translate output. No standard <p> tags.
 * Cloudflare protected — only accessible via WebView browser engine.
 *
 * Custom JS extractor handles the BR-split <font> tag extraction logic.
 * The old TwkanReader.js has been moved to assets/sites/twkan/extractor.js.
 */
class TwkanSupport : WebsiteSupport {
    override val siteId = "twkan"
    override val domains = listOf("twkan.com", "twkan.co", "ttkan.co", "ttkan.com")
    override val keywords = listOf("twkan", "ttkan")
    override val requiresAutoTranslate = true

    override val containerSelectors = listOf("#txtcontent0")
    override val paragraphSelector = CommonSelectors.STANDARD_PARAGRAPH
    override val excludeSelectors = CommonSelectors.COMMON_EXCLUDE
    override val requiresBrPreparation = true

    override val siteSpecificJunkKeywords = listOf("twkan", "ttkan")
    override val adBlockKeywords = emptyList<String>()
    override val titleSuffixes = listOf(
        " - twkan", "_twkan.com", " - twkan.com",
        " - ttkan", "_ttkan.co", " - ttkan.co"
    )

    // Custom JS extractor replaces the old inline twkan fast-path logic
    override val customJsExtractor = "sites/twkan/extractor.js"
    override val isSPA = false
}