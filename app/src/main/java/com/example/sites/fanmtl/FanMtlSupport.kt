package com.example.sites.fanmtl

import com.example.sites.WebsiteSupport
import com.example.sites.commons.CommonSelectors

/**
 * FanMTL — Translated novels.
 *
 * Content structure: <div class="chapter-content"> inside <article id="chapter-article">
 * Standard <p> tag extraction works correctly.
 * Heavy ad presence (Pubfuture, Taboola) — filtered via exclude selectors.
 */
class FanMtlSupport : WebsiteSupport {
    override val siteId = "fanmtl"
    override val domains = listOf("fanmtl.com")
    override val keywords = listOf("fanmtl")
    override val requiresAutoTranslate = false

    override val containerSelectors = listOf(".chapter-content", ".read-content", "#chapter-content", ".content-area")
    override val paragraphSelector = CommonSelectors.STANDARD_PARAGRAPH
    override val excludeSelectors = CommonSelectors.COMMON_EXCLUDE + listOf(
        ".TPuhiHlg", "ins.PUBFUTURE", ".tbl-read-next-btn", ".taboola"
    )
    override val requiresBrPreparation = false

    override val siteSpecificJunkKeywords = listOf("fanmtl")
    override val adBlockKeywords = emptyList<String>()
    override val titleSuffixes = listOf(" - FanMTL")
}