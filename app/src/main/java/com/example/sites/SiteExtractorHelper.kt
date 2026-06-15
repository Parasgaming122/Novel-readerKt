package com.example.sites

import android.content.Context
import android.webkit.WebView
import com.example.WtrLogManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Helper to load and execute per-site custom JS extractors.
 *
 * This is the ONLY new class that needs to be called from BrowserAppScreen.kt
 * to activate the modular extractor system. It handles:
 *   1. Loading `assets/sites/_shared/extractor-utils.js` (shared utilities)
 *   2. Loading `assets/sites/{siteId}/extractor.js` (site-specific extractor)
 *   3. Calling `window.__siteExtractor(opts)` with the site's configuration
 *   4. Returning the extracted paragraphs + start index
 *
 * ## Usage in BrowserAppScreen.kt
 * At each of the 3 extraction points, add this BEFORE the inline JS:
 *
 * ```kotlin
 * val customJs = matchedSupport?.customJsExtractor
 * if (customJs != null) {
 *     val result = SiteExtractorHelper.extractWithCustomJs(
 *         context, webView, matchedSupport
 *     )
 *     if (result != null) {
 *         list = result.paragraphs
 *         startIdx = result.startIndex
 *         extractionSuccess = list.isNotEmpty()
 *     }
 * } else {
 *     // ... existing inline JS extraction (unchanged) ...
 * }
 * ```
 */
object SiteExtractorHelper {

    data class ExtractionResult(
        val paragraphs: List<String>,
        val startIndex: Int,
        val rawJson: String
    )

    /**
     * Extract paragraphs using a site's custom JS extractor.
     * Returns null if the site has no customJsExtractor or extraction fails.
     *
     * @param context Android context (for reading assets)
     * @param webView The WebView to execute JS in
     * @param support The matched WebsiteSupport
     * @return ExtractionResult or null on failure
     */
    suspend fun extractWithCustomJs(
        context: Context,
        webView: WebView,
        support: WebsiteSupport
    ): ExtractionResult? {
        val jsPath = support.customJsExtractor ?: return null

        return try {
            // 1. Load shared utils
            val sharedJs = context.assets.open("sites/_shared/extractor-utils.js")
                .bufferedReader().use { it.readText() }

            // 2. Load site-specific extractor
            val siteJs = context.assets.open(jsPath)
                .bufferedReader().use { it.readText() }

            // 3. Build options JSON
            val containerSels = support.containerSelectors
            val pSel = support.paragraphSelector
            val excludeSels = support.excludeSelectors
            val junkKw = support.siteSpecificJunkKeywords

            val optionsJson = buildString {
                append("{")
                append("\"containerSelectors\":[")
                containerSels.forEachIndexed { i, s ->
                    if (i > 0) append(",")
                    append("\"${s.replace("\"", "\\\"")}\"")
                }
                append("],")
                append("\"paragraphSelector\":\"${pSel.replace("\"", "\\\"")}\",")
                append("\"excludeSelectors\":[")
                excludeSels.forEachIndexed { i, s ->
                    if (i > 0) append(",")
                    append("\"${s.replace("\"", "\\\"")}\"")
                }
                append("],")
                append("\"requiresBrPreparation\":${support.requiresBrPreparation},")
                append("\"siteJunkKeywords\":[")
                junkKw.forEachIndexed { i, s ->
                    if (i > 0) append(",")
                    append("\"${s.replace("\"", "\\\"")}\"")
                }
                append("],")
                append("\"url\":\"${webView.url ?: ""}\"")
                append("}")
            }

            // 4. Execute: load shared utils, then site extractor, then call it
            val fullJs = """
                $sharedJs
                $siteJs
                (function() {
                    try {
                        if (typeof window.__siteExtractor === 'function') {
                            var opts = $optionsJson;
                            var result = window.__siteExtractor(opts);
                            return JSON.stringify(result);
                        } else {
                            return JSON.stringify({error: '__siteExtractor not defined'});
                        }
                    } catch(e) {
                        return JSON.stringify({error: e.toString()});
                    }
                })();
            """.trimIndent()

            val jsonResult = evaluateJsSuspend(webView, fullJs)

            // 5. Parse result
            if (jsonResult != null && !jsonResult.contains("\"error\"")) {
                // Parse: { paragraphs: [...], startIndex: N }
                val paragraphs = mutableListOf<String>()
                var startIndex = 0

                // Quick JSON parse without pulling in org.json
                val paraArrayMatch = Regex(""""paragraphs"\s*:\s*\[((?:\[[^\]]*\]|[^\[\]])*)\]""").find(jsonResult)
                if (paraArrayMatch != null) {
                    val arrayContent = paraArrayMatch.groupValues[1]
                    // Extract individual string values
                    val stringPattern = Regex(""""((?:[^"\\]|\\.)*)"""")
                    val matches = stringPattern.findAll(arrayContent)
                    for (match in matches) {
                        var text = match.groupValues[1]
                            .replace("\\\"", "\"")
                            .replace("\\\\", "\\")
                            .replace("\\n", "\n")
                            .replace("\\t", "\t")
                        if (text.length > 3) {
                            paragraphs.add(text)
                        }
                    }
                }

                val startMatch = Regex(""""startIndex"\s*:\s*(\d+)""").find(jsonResult)
                if (startMatch != null) {
                    startIndex = startMatch.groupValues[1].toIntOrNull() ?: 0
                }

                if (paragraphs.isNotEmpty()) {
                    ExtractionResult(paragraphs, startIndex, jsonResult)
                } else {
                    WtrLogManager.log(context, "SiteExtractorHelper: no paragraphs from ${support.siteId} custom extractor")
                    null
                }
            } else {
                WtrLogManager.log(context, "SiteExtractorHelper: extractor error for ${support.siteId}: $jsonResult")
                null
            }
        } catch (e: Exception) {
            WtrLogManager.log(context, "SiteExtractorHelper: failed for ${support.siteId}: ${e.message}")
            null
        }
    }

    /**
     * Check if a nav info was stored by the webnovel extractor.
     * Returns a map with keys: nextUrl, prevUrl, nextChapterId, prevChapterId, etc.
     * Returns null if no nav info is available.
     */
    suspend fun getNavInfo(webView: WebView): Map<String, String>? {
        val js = """
            (function() {
                if (window.__wtrNavInfo) {
                    return JSON.stringify(window.__wtrNavInfo);
                }
                return null;
            })();
        """.trimIndent()

        val result = evaluateJsSuspend(webView, js) ?: return null
        if (result == "null" || result.contains("\"error\"")) return null

        val navInfo = mutableMapOf<String, String>()
        val pairs = Regex(""""(\w+)\"\s*:\s*"?([^",}]+)"?""").findAll(result)
        for (pair in pairs) {
            navInfo[pair.groupValues[1]] = pair.groupValues[2]
        }
        return if (navInfo.isNotEmpty()) navInfo else null
    }

    /**
     * Check if a site has a custom JS extractor (without executing it).
     */
    fun hasCustomExtractor(support: WebsiteSupport?): Boolean {
        return support?.customJsExtractor != null
    }

    private suspend fun evaluateJsSuspend(webView: WebView, js: String): String? {
        return suspendCancellableCoroutine { continuation ->
            webView.post {
                try {
                    webView.evaluateJavascript(js) { result ->
                        continuation.resume(result?.trim()?.removeSurrounding("\"")?.trim())
                    }
                } catch (e: Exception) {
                    continuation.resume(null)
                }
            }
        }
    }
}