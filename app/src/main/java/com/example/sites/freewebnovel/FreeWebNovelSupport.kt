package com.example.sites.freewebnovel

import com.example.sites.WebsiteSupport
import com.example.sites.commons.CommonSelectors

/**
 * FreeWebNovel — English novels.
 *
 * Content structure: <div class="txt"> → <div id="article"> with <p> tags.
 * Verified working — no changes needed from original.
 */
class FreeWebNovelSupport : WebsiteSupport {
    override val siteId = "freewebnovel"
    override val domains = listOf("freewebnovel.com")
    override val keywords = listOf("free")
    override val requiresAutoTranslate = false

    override val containerSelectors = listOf(".txt", "#htmlContent", ".chapter-content")
    override val paragraphSelector = CommonSelectors.STANDARD_PARAGRAPH
    override val excludeSelectors = CommonSelectors.COMMON_EXCLUDE + listOf(
        ".read-ads", ".chapter-end", ".chapter-start"
    )
    override val requiresBrPreparation = false

    override val siteSpecificJunkKeywords = listOf("freewebnovel")
    override val adBlockKeywords = emptyList<String>()
    override val titleSuffixes = listOf(" - FreeWebNovel")
}