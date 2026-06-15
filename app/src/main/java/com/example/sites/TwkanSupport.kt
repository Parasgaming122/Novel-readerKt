package com.example.sites

import com.example.sites.commons.CommonSelectors

class TwkanSupport : WebsiteSupport {
    override val siteId = "twkan"
    override val domains = listOf("twkan.com", "twkan.co", "ttkan.co", "ttkan.com")
    override val keywords = listOf("twkan", "ttkan")
    override val requiresAutoTranslate = true

    // Strictly target `#txtcontent0` or general chapter-oriented `txtcontent` wrappers while avoiding ad/recommend widgets
    override val containerSelectors = listOf("#txtcontent0")
    override val paragraphSelector = CommonSelectors.STANDARD_PARAGRAPH
    override val excludeSelectors = CommonSelectors.COMMON_EXCLUDE
    override val requiresBrPreparation = true

    override val siteSpecificJunkKeywords = listOf("twkan", "ttkan")
    override val adBlockKeywords = emptyList<String>()
    override val titleSuffixes = listOf(" - twkan", "_twkan.com", " - twkan.com", " - ttkan", "_ttkan.co", " - ttkan.co")
}
