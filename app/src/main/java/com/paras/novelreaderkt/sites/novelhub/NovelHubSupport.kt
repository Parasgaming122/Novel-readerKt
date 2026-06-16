package com.paras.novelreaderkt.sites.novelhub

import com.paras.novelreaderkt.sites.WebsiteSupport
import com.paras.novelreaderkt.sites.commons.CommonSelectors

/**
 * NovelHub — English novels.
 *
 * FIXED: Old selector `#chr-content` was WRONG. The actual ID is `chapter-content`.
 * Content structure: <article id="chapter-content"> inside <main class="content-wrapper">
 * Uses Alpine.js for interactivity. Clean layout with minimal ads.
 * 19 <p> tags per chapter verified.
 */
class NovelHubSupport : WebsiteSupport {
    override val siteId = "novelhub"
    override val domains = listOf("novelhub.net")
    override val keywords = listOf("nhub", "novelhub")
    override val requiresAutoTranslate = false

    // FIXED: Added correct `#chapter-content` as first selector
    override val containerSelectors = listOf(
        "#chapter-content",
        ".chapter-content",
        ".read-content",
        ".entry-content",
        ".reader-content",
        "main article"
    )
    override val paragraphSelector = CommonSelectors.STANDARD_PARAGRAPH
    override val excludeSelectors = CommonSelectors.COMMON_EXCLUDE
    override val requiresBrPreparation = false

    override val siteSpecificJunkKeywords = listOf("novelhub")
    override val adBlockKeywords = emptyList<String>()
    override val titleSuffixes = listOf(" - NovelHub", "_novelhub.net", " - novelhub.net")
}