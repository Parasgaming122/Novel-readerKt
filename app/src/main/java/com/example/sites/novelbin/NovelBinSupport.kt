package com.example.sites.novelbin

import com.example.sites.WebsiteSupport
import com.example.sites.commons.CommonSelectors

/**
 * NovelBin — English novels.
 *
 * Content structure: <div id="chr-content" class="chr-c"> with standard <p> tags.
 * Verified working — no changes needed from original.
 */
class NovelBinSupport : WebsiteSupport {
    override val siteId = "novelbin"
    override val domains = listOf("novelbin.com", "novelbin.net", "novelbin.me")
    override val keywords = listOf("novelbin")
    override val requiresAutoTranslate = false

    override val containerSelectors = listOf("#chr-content", ".chr-c", "#chapter-content", ".chapter-container")
    override val paragraphSelector = CommonSelectors.STANDARD_PARAGRAPH
    override val excludeSelectors = CommonSelectors.COMMON_EXCLUDE + listOf(
        ".chr-end", ".chr-start", ".chr-jump"
    )
    override val requiresBrPreparation = false

    override val siteSpecificJunkKeywords = listOf("novelbin")
    override val adBlockKeywords = emptyList<String>()
    override val titleSuffixes = listOf(" - NovelBin")
}