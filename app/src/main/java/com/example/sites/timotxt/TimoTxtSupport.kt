package com.example.sites.timotxt

import com.example.sites.WebsiteSupport
import com.example.sites.commons.CommonSelectors

/**
 * TimoTxt — Chinese novels (auto-translate via Google Translate proxy).
 *
 * FIXED: Old selector `.show_txt` does NOT exist on the current site.
 * Content structure: <div class="chapter-content px-3 pb-5"> → <div class="content py-5"> with <p> tags.
 * Uses traditional Chinese (繁體中文).
 *
 * BR preparation is needed for some chapters that use <br> separators.
 */
class TimoTxtSupport : WebsiteSupport {
    override val siteId = "timotxt"
    override val domains = listOf("timotxt.com", "timotxt.cn")
    override val keywords = listOf("timo", "timotxt")
    override val requiresAutoTranslate = true

    // FIXED: Replaced broken `.show_txt` with `.chapter-content .content`
    override val containerSelectors = listOf(".chapter-content .content", ".chapter-content", ".content")
    override val paragraphSelector = CommonSelectors.STANDARD_PARAGRAPH
    override val excludeSelectors = CommonSelectors.COMMON_EXCLUDE + listOf(
        ".gadBlock", "ins.clickforceads", "ins.PopIn"
    )
    override val requiresBrPreparation = true

    override val siteSpecificJunkKeywords = listOf("timotxt", "手機小說閱讀網", "手機用戶", "最新更新時間")
    override val adBlockKeywords = listOf("timotxt.com")
    override val titleSuffixes = listOf(" - timotxt", "_timotxt", "_timotxt.com", " - timotxt.com")
}