package com.example.sites

import android.net.Uri
import com.example.sites.commons.CommonPatterns

object WebsiteSupportRegistry {
    val supports = listOf(
        WtrLabSupport(),
        WebNovelSupport(),
        NovelHallSupport(),
        FanMtlSupport(),
        NovelBinSupport(),
        FreeWebNovelSupport(),
        TimoTxtSupport(),
        Novel543Support(),
        TwkanSupport(),
        NovelHubSupport(),
        NovelHubAppSupport()
    )

    private val domainMap: Map<String, WebsiteSupport>
    private val keywordMap: Map<String, WebsiteSupport>

    init {
        val dMap = mutableMapOf<String, WebsiteSupport>()
        val kMap = mutableMapOf<String, WebsiteSupport>()

        for (support in supports) {
            for (domain in support.domains) {
                dMap[domain.lowercase()] = support
            }
            for (keyword in support.keywords) {
                kMap[keyword.lowercase()] = support
            }
        }

        domainMap = dMap
        keywordMap = kMap
    }

    /**
     * Find support for a given URL
     */
    fun findSupport(url: String): WebsiteSupport? {
        if (url.isEmpty()) return null
        val host = try {
            val uri = Uri.parse(url)
            uri.host?.lowercase() ?: ""
        } catch (e: Exception) {
            ""
        }
        
        // Clean out subdomains, translate proxies etc.
        val isTranslated = host.contains("translate.goog")
        var cleanHost = host.replace("www.", "")
            .replace(".translate.goog", "")
            .replace("translate.goog", "")
        if (isTranslated) {
            cleanHost = cleanHost.replace("--", "__HYPHEN__")
                .replace("-", ".")
                .replace("__HYPHEN__", "-")
        }
            
        // First do exact domain match
        val exact = domainMap[cleanHost]
        if (exact != null) return exact
        
        // Fallback to partial host contains
        for (support in supports) {
            for (domain in support.domains) {
                if (cleanHost.contains(domain) || url.lowercase().contains(domain)) {
                    return support
                }
            }
        }
        return null
    }

    /**
     * Find support by keyword navigation shortcut
     */
    fun findSupportByKeyword(keyword: String): WebsiteSupport? {
        return keywordMap[keyword.lowercase().trim()]
    }

    /**
     * Get a list of all domain strings that are handled by support classes requiresAutoTranslate
     */
    fun getAutoTranslateSites(): List<String> {
        return supports.filter { it.requiresAutoTranslate }.flatMap { it.domains }
    }

    /**
     * Unified, clean title and block extraction helper
     */
    fun extractNovelAndChapter(title: String, url: String): Pair<String, String> {
        if (title.isEmpty()) return Pair("Wtr-Lab Browser", "Web Chapter")

        // Look up site support to gather custom suffixes
        val matchSupport = findSupport(url)
        val suffixes = matchSupport?.titleSuffixes ?: emptyList()

        var cleanTitle = title
        for (suffix in suffixes) {
            cleanTitle = cleanTitle.replace(suffix, "", ignoreCase = true)
        }
        
        // Generic replacements to clean titles automatically
        cleanTitle = cleanTitle
            .replace(" online free", "", ignoreCase = true)
            .replace(" read online", "", ignoreCase = true)
            .trim()

        if (cleanTitle.startsWith("《") && cleanTitle.endsWith("》")) {
            cleanTitle = cleanTitle.substring(1, cleanTitle.length - 1).trim()
        }

        var extractedChapter = ""
        var extractedNovel = ""

        val separators = listOf(" - ", " | ", " – ", " — ")
        for (sep in separators) {
            if (cleanTitle.contains(sep)) {
                val parts = cleanTitle.split(sep)
                if (parts.size >= 2) {
                    val part0 = parts[0].trim()
                    val part1 = parts.drop(1).joinToString(" - ").trim()

                    var isPart1Chapter = false
                    for (pattern in CommonPatterns.TITLE_PATTERNS) {
                        if (pattern.containsMatchIn(part1)) {
                            isPart1Chapter = true
                            break
                        }
                    }

                    var isPart0Chapter = false
                    for (pattern in CommonPatterns.TITLE_PATTERNS) {
                        if (pattern.containsMatchIn(part0)) {
                            isPart0Chapter = true
                            break
                        }
                    }

                    if (isPart1Chapter && !isPart0Chapter) {
                        return Pair(part0, part1)
                    } else if (isPart0Chapter && !isPart1Chapter) {
                        return Pair(part1, part0)
                    } else {
                        return Pair(part0, part1)
                    }
                }
            }
        }

        for (pattern in CommonPatterns.TITLE_PATTERNS) {
            val match = pattern.find(cleanTitle)
            if (match != null) {
                val fullMatch = match.value
                val idx = cleanTitle.indexOf(fullMatch)
                if (idx > 0) {
                    extractedNovel = cleanTitle.substring(0, idx).trim(' ', ',', '-', '_', '(', ')', '《', '》', ':').trim()
                    extractedChapter = cleanTitle.substring(idx).trim()
                    break
                } else if (idx == 0) {
                    extractedChapter = fullMatch
                    extractedNovel = cleanTitle.substring(fullMatch.length).trim(' ', ',', '-', '_', ':', '(', ')').trim()
                    break
                }
            }
        }

        if (extractedChapter.isEmpty()) {
            for (pattern in CommonPatterns.URL_PATTERNS) {
                val match = pattern.find(url)
                if (match != null) {
                    val num = match.groupValues.getOrNull(1) ?: match.value
                    extractedChapter = "Chapter $num"
                    break
                }
            }
        }

        if (extractedNovel.isEmpty()) {
            extractedNovel = cleanTitle
        }

        if (extractedChapter.isEmpty()) {
            extractedChapter = "Chapter 1"
        }

        if (extractedNovel.startsWith("《") && extractedNovel.endsWith("》")) {
            extractedNovel = extractedNovel.substring(1, extractedNovel.length - 1).trim()
        }

        if (extractedNovel.isEmpty()) {
            extractedNovel = "Web Novel"
        }

        return Pair(extractedNovel, extractedChapter)
    }
}
