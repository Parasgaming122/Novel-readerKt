package com.paras.novelreaderkt.sites.webnovel

import com.paras.novelreaderkt.sites.WebsiteSupport
import com.paras.novelreaderkt.sites.commons.CommonSelectors

/**
 * WebNovel — English/Translated novels.
 *
 * Content structure (m.webnovel.com):
 *   <div class="cha-content"> → <div class="cha-words"> → <div class="cha-paragraph"> → <p>text</p>
 *
 * Uses Next.js SSR. Chapter content is in `__NEXT_DATA__.props.pageProps.initialState.entities.chapter`.
 * Navigation: preChapterId / nextChapterId in __NEXT_DATA__ (NOT in DOM for prev chapter).
 *
 * Custom JS extractor handles m.webnovel.com __NEXT_DATA__ parsing for reliable navigation.
 */
class WebNovelSupport : WebsiteSupport {
    override val siteId = "webnovel"
    override val domains = listOf("webnovel.com", "m.webnovel.com")
    override val keywords = listOf("webnovel", "wn")
    override val requiresAutoTranslate = false

    override val containerSelectors = listOf(".cha-content", ".chapter-content", ".cha-words", ".chapter-inner")
    override val paragraphSelector = "p, .cha-paragraph, .pirate"
    override val excludeSelectors = CommonSelectors.COMMON_EXCLUDE + listOf(
        ".cha-page-ft", ".cha-fly", ".cha-tit", "script", "style",
        ".j_recommendation", ".comment-area", "#video-"
    )
    override val requiresBrPreparation = false

    override val siteSpecificJunkKeywords = listOf("webnovel")
    override val adBlockKeywords = emptyList<String>()
    override val titleSuffixes = listOf(" - WebNovel")

    // Custom extractor for __NEXT_DATA__ nav extraction and fallback parsing
    override val customJsExtractor = "sites/webnovel/extractor.js"
    override val isSPA = false
}