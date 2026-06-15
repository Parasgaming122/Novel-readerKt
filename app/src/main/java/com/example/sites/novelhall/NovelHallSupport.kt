package com.example.sites.novelhall

import com.example.sites.WebsiteSupport
import com.example.sites.commons.CommonSelectors

/**
 * NovelHall — English novels.
 *
 * Content structure: <div class="entry-content" id="htmlContent">
 * Uses <br><br> separators (NO <p> tags). BR preparation is essential.
 */
class NovelHallSupport : WebsiteSupport {
    override val siteId = "novelhall"
    override val domains = listOf("novelhall.com", "novelhall.net")
    override val keywords = listOf("novelhall")
    override val requiresAutoTranslate = false

    override val containerSelectors = listOf("#htmlContent", ".entry-content", ".active")
    override val paragraphSelector = CommonSelectors.STANDARD_PARAGRAPH
    override val excludeSelectors = CommonSelectors.COMMON_EXCLUDE
    override val requiresBrPreparation = true

    override val siteSpecificJunkKeywords = listOf("novelhall", "read novel free", "novelxo")
    override val adBlockKeywords = listOf("novelhall.com")
    override val titleSuffixes = listOf(
        " - NovelHall",
        " - Read Novel Free",
        "_novelhall.com",
        "_novelhall",
        " - novelhall.com"
    )
}