package com.paras.novelreaderkt

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

/**
 * Pure-Kotlin next chapter URL resolver.
 * Works entirely without WebView — safe to call from a background foreground service.
 *
 * Strategy:
 *  1. Parse the current URL and fetch the page HTML via OkHttp on IO thread.
 *  2. Find the "next chapter" link by looking at common CSS selectors and Chinese keywords.
 *  3. Return the absolute URL of the next chapter page.
 *  4. If no link is found, fall back to numeric chapter increment.
 */
object WtrChapterUrlResolver {

    private const val TAG = "WtrChapterResolver"

    /**
     * Resolves the next chapter URL from the given page URL.
     * Called from a background coroutine (Dispatchers.IO).
     *
     * @param currentUrl The URL of the current chapter page.
     * @param autoTranslateDomains Comma-separated domains that need Google Translate proxy.
     * @param autoTranslateEnabled Whether auto-translate is active.
     * @return The resolved next chapter URL, or null if nothing could be found.
     */
    fun resolveNextChapterUrl(
        currentUrl: String,
        autoTranslateDomains: String = "",
        autoTranslateEnabled: Boolean = false
    ): String? {
        try {
            val html = fetchPageHtml(currentUrl) ?: return null
            val baseUrl = currentUrl.substringBefore("?").substringBefore("#")
            val host = try { URL(currentUrl).host?.lowercase() ?: "" } catch (e: Exception) { "" }
            val hostNoWww = host.removePrefix("www.")

            // ---- Site-specific resolution ----
            // Webnovel
            if (hostNoWww.contains("webnovel.com")) {
                val bookMatch = Regex("/book/(\\d+)/(\\d+)").find(currentUrl)
                if (bookMatch != null) {
                    val bookId = bookMatch.groupValues[1]
                    val currentChapId = bookMatch.groupValues[2]
                    // Look for links to other chapters of the same book
                    val chapterPattern = Regex("/book/$bookId/\\d+")
                    val nextLink = findLinkByHrefPattern(html, chapterPattern)
                        .firstOrNull { !it.contains(currentChapId) }
                    if (nextLink != null) {
                        return makeAbsoluteUrl(nextLink, currentUrl)
                    }
                }
            }

            // Timotxt, novel543, twkan, ttkan — look for Chinese "next chapter" text
            if (hostNoWww.contains("timotxt") || hostNoWww.contains("novel543") ||
                hostNoWww.contains("twkan") || hostNoWww.contains("ttkan")) {
                val nextUrl = findLinkByTextKeywords(html, listOf("下一章", "Next Chapter", "下一頁", "下一页", "Next Page"))
                if (nextUrl != null) return makeAbsoluteUrl(nextUrl, currentUrl)
            }

            // ---- General: CSS selector-based next chapter link ----
            val genericResult = findGenericNextLink(html, currentUrl)
            if (genericResult != null) return genericResult

            // ---- Fallback: numeric chapter increment ----
            val numericResult = tryNumericIncrement(currentUrl)
            if (numericResult != null) return numericResult

            Log.w(TAG, "Could not resolve next chapter URL for $currentUrl")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving next chapter URL: ${e.message}")
            return null
        }
    }

    /**
     * Fetches page HTML content. Uses a short timeout to avoid blocking the service.
     */
    private fun fetchPageHtml(pageUrl: String, timeoutMs: Int = 8000): String? {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(pageUrl)
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36")
            conn.instanceFollowRedirects = true

            if (conn.responseCode == 200) {
                return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else if (conn.responseCode in 300..399) {
                // Follow redirect manually
                val location = conn.getHeaderField("Location")
                if (location != null) {
                    return fetchPageHtml(makeAbsoluteUrl(location, pageUrl) ?: pageUrl, timeoutMs)
                }
            }
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch HTML for $pageUrl: ${e.message}")
            return null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Finds an <a> tag whose href matches the given regex pattern.
     * Returns the href value of the first match.
     */
    private fun findLinkByHrefPattern(html: String, hrefPattern: Regex): List<String> {
        val results = mutableListOf<String>()
        // Match <a href="..."> patterns — handle both single and double quotes
        val anchorRegex = Regex("""<a\s[^>]*href=["']([^"']*)["'][^>]*>""", RegexOption.IGNORE_CASE)
        for (match in anchorRegex.findAll(html)) {
            val href = match.groupValues[1]
            if (hrefPattern.containsMatchIn(href)) {
                results.add(href)
            }
        }
        return results
    }

    /**
     * Finds an <a> tag whose inner text contains one of the given keywords.
     * Returns the href value.
     */
    private fun findLinkByTextKeywords(html: String, keywords: List<String>): String? {
        // Match <a href="...">text</a> — simplified but effective
        val anchorRegex = Regex(
            """<a\s[^>]*href=["']([^"']*)["'][^>]*>(.*?)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        for (match in anchorRegex.findAll(html)) {
            val href = match.groupValues[1]
            val text = match.groupValues[2].trim()
                .replace(Regex("<[^>]+>"), "") // Strip inner HTML tags
                .trim()
            for (keyword in keywords) {
                if (text.contains(keyword, ignoreCase = true)) {
                    return href
                }
            }
        }
        return null
    }

    /**
     * Generic next chapter link finder using common CSS class names.
     */
    private fun findGenericNextLink(html: String, currentUrl: String): String? {
        // Look for links with next-related classes or IDs
        val classKeywords = listOf(
            "btn-next", ".next", ".next-chapter", ".next_chap", ".next-page",
            "btn_next", "next_chapter", "next_chap", "next_page"
        )
        val anchorRegex = Regex(
            """<a\s[^>]*href=["']([^"']*)["'][^>]*>""",
            RegexOption.IGNORE_CASE
        )
        for (match in anchorRegex.findAll(html)) {
            val fullTag = match.value
            val href = match.groupValues[1]
            if (href.startsWith("#") || href.startsWith("javascript:")) continue

            // Check class and id attributes
            val classMatch = Regex("""class=["']([^"']*)["']""").find(fullTag)
            val idMatch = Regex("""id=["']([^"']*)["']""").find(fullTag)
            val classAttr = classMatch?.groupValues?.get(1)?.lowercase() ?: ""
            val idAttr = idMatch?.groupValues?.get(1)?.lowercase() ?: ""

            for (keyword in classKeywords) {
                if (classAttr.contains(keyword, ignoreCase = true) ||
                    idAttr.contains(keyword, ignoreCase = true)) {
                    return makeAbsoluteUrl(href, currentUrl)
                }
            }
        }

        // Look for links whose text contains "next chapter" or "下一章"
        val textKeywords = listOf("next chapter", "next page", "下一章", "下一页")
        return findLinkByTextKeywords(html, textKeywords)
    }

    /**
     * Tries to increment a numeric chapter ID in the URL.
     * e.g. /novel/chapter-5.html -> /novel/chapter-6.html
     */
    private fun tryNumericIncrement(currentUrl: String): String? {
        val match = Regex("chapter-(\\d+)").find(currentUrl)
        if (match != null) {
            val num = match.groupValues[1].toInt()
            return currentUrl.replace(Regex("chapter-\\d+"), "chapter-${num + 1}")
        }
        // Try _N.html pattern (common on some CN novel sites)
        val match2 = Regex("_(\\d+)\\.html$").find(currentUrl)
        if (match2 != null) {
            val num = match2.groupValues[1].toInt()
            return currentUrl.replace(Regex("_\\d+\\.html$"), "_${num + 1}.html")
        }
        return null
    }

    /**
     * Converts a potentially relative URL to an absolute URL.
     */
    private fun makeAbsoluteUrl(href: String, baseUrl: String): String? {
        if (href.isEmpty()) return null
        // Already absolute
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        try {
            val base = URL(baseUrl)
            return when {
                href.startsWith("/") -> {
                    "${base.protocol}://${base.authority}$href"
                }
                href.startsWith("?") -> {
                    "${base.protocol}://${base.authority}${base.path}$href"
                }
                else -> {
                    val basePath = base.path?.substringBeforeLast("/") ?: ""
                    "${base.protocol}://${base.authority}$basePath/$href"
                }
            }
        } catch (e: Exception) {
            return null
        }
    }
}