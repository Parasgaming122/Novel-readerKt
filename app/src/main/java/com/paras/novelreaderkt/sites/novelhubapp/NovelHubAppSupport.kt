package com.paras.novelreaderkt.sites.novelhubapp

import com.paras.novelreaderkt.sites.WebsiteSupport
import com.paras.novelreaderkt.sites.commons.CommonSelectors

/**
 * NovelHubApp — English novels (auto-translate).
 *
 * CRITICAL: This is a pure SPA (Nuxt.js). ALL content is rendered client-side.
 * No SSR content — the HTML shell is empty for non-root routes.
 * Standard CSS selectors CANNOT work. A custom JS extractor is REQUIRED
 * that runs AFTER the WebView fully renders the page.
 *
 * The app already has SPA URL tracking in WebScripts.kt (hash-based uniqueness).
 * This custom extractor handles the content extraction part.
 */
class NovelHubAppSupport : WebsiteSupport {
    override val siteId = "novelhubapp"
    override val domains = listOf("novelhubapp.com")
    override val keywords = listOf("nhubapp", "novelhubapp")
    override val requiresAutoTranslate = true

    // These selectors are fallbacks — the custom JS extractor handles actual extraction
    override val containerSelectors = listOf(
        "#chr-content",
        ".chapter-content",
        ".read-content",
        ".entry-content",
        ".reader-content",
        "main article"
    )
    override val paragraphSelector = CommonSelectors.STANDARD_PARAGRAPH
    override val excludeSelectors = CommonSelectors.COMMON_EXCLUDE
    override val requiresBrPreparation = false

    override val siteSpecificJunkKeywords = listOf("novelhubapp")
    override val adBlockKeywords = emptyList<String>()
    override val titleSuffixes = listOf(" - NovelHubApp", "_novelhubapp.com", " - novelhubapp.com")

    // Custom JS extractor is MANDATORY — SPA with no SSR
    override val customJsExtractor = "sites/novelhubapp/extractor.js"
    override val isSPA = true
}