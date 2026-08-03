package com.example.sites

interface WebsiteSupport {
    val siteId: String
    val domains: List<String>
    val keywords: List<String>
    val requiresAutoTranslate: Boolean

    // For DOM Extraction
    val containerSelectors: List<String>
    val paragraphSelector: String
    val excludeSelectors: List<String>
    val requiresBrPreparation: Boolean

    // For Junk Filtering
    val siteSpecificJunkKeywords: List<String>
    val adBlockKeywords: List<String>

    // For Title Suffixes
    val titleSuffixes: List<String>
}
