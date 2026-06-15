package com.example.sites.novel543

import com.example.sites.WebsiteSupport
import com.example.sites.commons.CommonSelectors

/**
 * Novel543 — Taiwanese novels (auto-translate via Google Translate proxy).
 *
 * FIXED: `#content` was ambiguous (could match <body id="read">).
 * Content structure: <div class="chapter-content px-3"> → <div class="content py-5"> with <p> tags.
 * Uses traditional Chinese. Heavy ads (OneAd, PopIn).
 */
class Novel543Support : WebsiteSupport {
    override val siteId = "novel543"
    override val domains = listOf("novel543.com")
    override val keywords = listOf("n543", "novel543")
    override val requiresAutoTranslate = true

    // FIXED: Removed unreliable `#content`, reordered to `.chapter-content .content` first
    override val containerSelectors = listOf(".chapter-content .content", ".chapter-content", ".content", ".article-content")
    override val paragraphSelector = CommonSelectors.STANDARD_PARAGRAPH
    override val excludeSelectors = CommonSelectors.COMMON_EXCLUDE + listOf(
        ".gadBlock", "ins.clickforceads", "ins.PopIn"
    )
    override val requiresBrPreparation = true

    override val siteSpecificJunkKeywords = listOf("novel543")
    override val adBlockKeywords = emptyList<String>()
    override val titleSuffixes = listOf(" - novel543", "_novel543.com", " - novel543.com")
}