package com.example.sites

import com.example.sites.commons.CommonSelectors

class WtrLabSupport : WebsiteSupport {
    override val siteId = "wtr-lab"
    override val domains = listOf("wtr-lab.com", "wtr-lab.co")
    override val keywords = listOf("wtr", "wtrlab")
    override val requiresAutoTranslate = false

    override val containerSelectors = listOf(".read-content", "#content", ".wtr-reader-content", ".chapter-content")
    override val paragraphSelector = CommonSelectors.STANDARD_PARAGRAPH
    override val excludeSelectors = CommonSelectors.COMMON_EXCLUDE
    override val requiresBrPreparation = false

    override val siteSpecificJunkKeywords = emptyList<String>()
    override val adBlockKeywords = emptyList<String>()
    override val titleSuffixes = emptyList<String>()
}

class WebNovelSupport : WebsiteSupport {
    override val siteId = "webnovel"
    override val domains = listOf("webnovel.com")
    override val keywords = listOf("webnovel")
    override val requiresAutoTranslate = false

    override val containerSelectors = listOf(".cha-content", ".chapter-content", ".cha-words", ".chapter-inner")
    override val paragraphSelector = "p, .cha-paragraph, .pirate"
    override val excludeSelectors = CommonSelectors.COMMON_EXCLUDE
    override val requiresBrPreparation = false

    override val siteSpecificJunkKeywords = listOf("webnovel")
    override val adBlockKeywords = emptyList<String>()
    override val titleSuffixes = listOf(" - WebNovel")
}

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

class FanMtlSupport : WebsiteSupport {
    override val siteId = "fanmtl"
    override val domains = listOf("fanmtl.com")
    override val keywords = listOf("fanmtl")
    override val requiresAutoTranslate = false

    override val containerSelectors = listOf(".chapter-content", ".read-content", "#chapter-content", ".content-area")
    override val paragraphSelector = CommonSelectors.STANDARD_PARAGRAPH
    override val excludeSelectors = CommonSelectors.COMMON_EXCLUDE
    override val requiresBrPreparation = false

    override val siteSpecificJunkKeywords = listOf("fanmtl")
    override val adBlockKeywords = emptyList<String>()
    override val titleSuffixes = listOf(" - FanMTL")
}

class NovelBinSupport : WebsiteSupport {
    override val siteId = "novelbin"
    override val domains = listOf("novelbin.com", "novelbin.net", "novelbin.me")
    override val keywords = listOf("novelbin")
    override val requiresAutoTranslate = false

    override val containerSelectors = listOf("#chr-content", ".chr-c", "#chapter-content", ".chapter-container")
    override val paragraphSelector = CommonSelectors.STANDARD_PARAGRAPH
    override val excludeSelectors = CommonSelectors.COMMON_EXCLUDE
    override val requiresBrPreparation = false

    override val siteSpecificJunkKeywords = listOf("novelbin")
    override val adBlockKeywords = emptyList<String>()
    override val titleSuffixes = listOf(" - NovelBin")
}

class FreeWebNovelSupport : WebsiteSupport {
    override val siteId = "freewebnovel"
    override val domains = listOf("freewebnovel.com")
    override val keywords = listOf("free")
    override val requiresAutoTranslate = false

    override val containerSelectors = listOf(".txt", "#htmlContent", ".chapter-content")
    override val paragraphSelector = CommonSelectors.STANDARD_PARAGRAPH
    override val excludeSelectors = CommonSelectors.COMMON_EXCLUDE
    override val requiresBrPreparation = false

    override val siteSpecificJunkKeywords = listOf("freewebnovel")
    override val adBlockKeywords = emptyList<String>()
    override val titleSuffixes = listOf(" - FreeWebNovel")
}

class TimoTxtSupport : WebsiteSupport {
    override val siteId = "timotxt"
    override val domains = listOf("timotxt.com", "timotxt.cn")
    override val keywords = listOf("timo", "timotxt")
    override val requiresAutoTranslate = true

    override val containerSelectors = listOf(".show_txt", "#content", ".read-content")
    override val paragraphSelector = CommonSelectors.STANDARD_PARAGRAPH
    override val excludeSelectors = CommonSelectors.COMMON_EXCLUDE
    override val requiresBrPreparation = true

    override val siteSpecificJunkKeywords = listOf("timotxt", "手机小说阅读网", "手机用户", "最新更新时间")
    override val adBlockKeywords = listOf("timotxt.com")
    override val titleSuffixes = listOf(" - timotxt", "_timotxt", "_timotxt.com", " - timotxt.com")
}

class Novel543Support : WebsiteSupport {
    override val siteId = "novel543"
    override val domains = listOf("novel543.com")
    override val keywords = listOf("n543", "novel543")
    override val requiresAutoTranslate = true

    override val containerSelectors = listOf("#content", ".content", ".chapter-content", ".article-content")
    override val paragraphSelector = CommonSelectors.STANDARD_PARAGRAPH
    override val excludeSelectors = CommonSelectors.COMMON_EXCLUDE
    override val requiresBrPreparation = true

    override val siteSpecificJunkKeywords = listOf("novel543")
    override val adBlockKeywords = emptyList<String>()
    override val titleSuffixes = listOf(" - novel543", "_novel543.com", " - novel543.com")
}

class TwkanSupport : WebsiteSupport {
    override val siteId = "twkan"
    override val domains = listOf("twkan.com")
    override val keywords = listOf("twkan")
    override val requiresAutoTranslate = true

    override val containerSelectors = listOf("#htmlContent", "#content", ".active", ".read-content", ".article-content")
    override val paragraphSelector = CommonSelectors.STANDARD_PARAGRAPH
    override val excludeSelectors = CommonSelectors.COMMON_EXCLUDE
    override val requiresBrPreparation = false

    override val siteSpecificJunkKeywords = listOf("twkan")
    override val adBlockKeywords = emptyList<String>()
    override val titleSuffixes = listOf(" - twkan", "_twkan.com", " - twkan.com")
}

class NovelHubSupport : WebsiteSupport {
    override val siteId = "novelhub"
    override val domains = listOf("novelhub.net")
    override val keywords = listOf("nhub", "novelhub")
    override val requiresAutoTranslate = false

    override val containerSelectors = listOf("#chr-content", ".chapter-content", ".read-content", ".entry-content", ".reader-content", "main article")
    override val paragraphSelector = CommonSelectors.STANDARD_PARAGRAPH
    override val excludeSelectors = CommonSelectors.COMMON_EXCLUDE
    override val requiresBrPreparation = false

    override val siteSpecificJunkKeywords = listOf("novelhub")
    override val adBlockKeywords = emptyList<String>()
    override val titleSuffixes = listOf(" - NovelHub", "_novelhub.net", " - novelhub.net")
}

class NovelHubAppSupport : WebsiteSupport {
    override val siteId = "novelhubapp"
    override val domains = listOf("novelhubapp.com")
    override val keywords = listOf("nhubapp", "novelhubapp")
    override val requiresAutoTranslate = true

    override val containerSelectors = listOf("#chr-content", ".chapter-content", ".read-content", ".entry-content", ".reader-content", "main article")
    override val paragraphSelector = CommonSelectors.STANDARD_PARAGRAPH
    override val excludeSelectors = CommonSelectors.COMMON_EXCLUDE
    override val requiresBrPreparation = false

    override val siteSpecificJunkKeywords = listOf("novelhubapp")
    override val adBlockKeywords = emptyList<String>()
    override val titleSuffixes = listOf(" - NovelHubApp", "_novelhubapp.com", " - novelhubapp.com")
}
