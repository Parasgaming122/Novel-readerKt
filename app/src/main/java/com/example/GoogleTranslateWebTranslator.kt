package com.example

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Google Translate Web Translator
 *
 * Uses the unofficial Google Translate endpoint (translate_a/single) which is what
 * many translation apps and extensions use. This provides:
 * - Free, high-quality Google Translate translations
 * - No API key required
 * - Auto language detection
 * - Support for 100+ languages including Chinese, Japanese, Korean
 *
 * The endpoint `translate.google.com/translate_a/single` with client=at
 * is the same method used by Google Translate on the web.
 */
object GoogleTranslateWebTranslator {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private const val BASE_URL = "https://translate.google.com/translate_a/single"

    /**
     * Translates a list of text paragraphs using Google Translate web endpoint.
     *
     * Processes paragraphs in small batches with delays to avoid rate limiting.
     * Each paragraph is translated individually for reliability.
     *
     * @param paragraphs List of text paragraphs to translate
     * @param targetLang Target language code (default: "en" for English)
     * @return List of translated paragraphs (same order, falls back to original on error)
     */
    suspend fun translateParagraphs(
        paragraphs: List<String>,
        targetLang: String = "en"
    ): List<String> = withContext(Dispatchers.IO) {
        if (paragraphs.isEmpty()) return@withContext emptyList()

        val results = mutableListOf<String>()

        paragraphs.forEachIndexed { index, text ->
            try {
                val translated = translateSingle(text, targetLang)
                results.add(translated)
            } catch (e: Exception) {
                android.util.Log.w(
                    "GoogleTranslateWeb",
                    "Failed to translate paragraph $index: ${e.message}"
                )
                results.add(text) // Fallback to original
            }

            // Small delay between requests to avoid rate limiting
            // Google Translate web endpoint allows ~5-10 requests per second
            if (index < paragraphs.size - 1) {
                try {
                    Thread.sleep(150)
                } catch (_: InterruptedException) {}
            }
        }

        results
    }

    /**
     * Translate a single text using the Google Translate web endpoint.
     *
     * Uses the `client=at` parameter which is the same client identifier
     * used by the Google Translate website itself.
     *
     * Response format from Google:
     * [["translated text", "original text", null, null, 10], ...]
     * The response is a nested array where the first element of each sub-array
     * is a translated text fragment.
     */
    private fun translateSingle(text: String, targetLang: String): String {
        if (text.isBlank()) return text
        if (text.length <= 2) return text // Too short to translate meaningfully

        val encodedText = URLEncoder.encode(text, "UTF-8")
        val url = buildString {
            append(BASE_URL)
            append("?client=at")
            append("&sl=auto") // Auto-detect source language
            append("&tl=")
            append(targetLang)
            append("&dt=t") // Translation
            append("&dt=bd") // Alternative translations (for disambiguation)
            append("&dj=1") // Return JSON with named fields
            append("&source=input")
            append("&q=")
            append(encodedText)
        }

        val request = Request.Builder()
            .url(url)
            .get()
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
            )
            .header(
                "Accept",
                "*/*"
            )
            .header(
                "Accept-Language",
                "en-US,en;q=0.9"
            )
            .header(
                "Referer",
                "https://translate.google.com/"
            )
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                android.util.Log.w(
                    "GoogleTranslateWeb",
                    "API returned ${response.code()}, falling back to original"
                )
                return text
            }

            val responseBody = response.body()?.string() ?: return text

            return try {
                parseTranslationResponse(responseBody)
            } catch (e: Exception) {
                android.util.Log.e(
                    "GoogleTranslateWeb",
                    "Parse error: ${e.message}"
                )
                text
            }
        }
    }

    /**
     * Parse the Google Translate web response.
     *
     * With `dj=1`, the response is a JSON object with a "sentences" array:
     * {"sentences":[{"trans":"translated text","orig":"original",...},...]}
     *
     * Without `dj=1` (legacy format), it's a nested array:
     * [["translated","original",...],...]
     *
     * This method handles both formats.
     */
    private fun parseTranslationResponse(responseBody: String): String {
        val trimmed = responseBody.trim()

        // Try dj=1 format first (structured JSON)
        return try {
            val json = org.json.JSONObject(trimmed)
            val sentences = json.optJSONArray("sentences") ?: return ""

            val translatedParts = StringBuilder()
            for (i in 0 until sentences.length()) {
                val sentence = sentences.getJSONObject(i)
                val trans = sentence.optString("trans", "")
                if (trans.isNotEmpty()) {
                    if (translatedParts.isNotEmpty()) {
                        translatedParts.append(" ")
                    }
                    translatedParts.append(trans)
                }
            }

            val result = translatedParts.toString().trim()
            if (result.isNotEmpty()) result else ""
        } catch (e: Exception) {
            // Fallback: try legacy array format
            parseLegacyFormat(trimmed)
        }
    }

    /**
     * Parse the legacy Google Translate array format.
     *
     * Format: [["translated", "original", null, null, confidence], ...]
     * The top-level array contains pairs of translation batches.
     * Only odd-indexed top-level elements contain translations.
     */
    private fun parseLegacyFormat(responseBody: String): String {
        return try {
            val topArray = JSONArray(responseBody)
            val translatedParts = StringBuilder()

            // The response has pairs: [translations_array, ..., translations_array, ...]
            // Odd-indexed top-level arrays contain the actual translations
            for (i in 0 until topArray.length()) {
                if (i % 2 != 1) continue // Only process odd-indexed arrays

                val batch = topArray.getJSONArray(i)
                for (j in 0 until batch.length()) {
                    val item = batch.getJSONArray(j)
                    val translated = item.optString(0, "")
                    if (translated.isNotEmpty()) {
                        if (translatedParts.isNotEmpty()) {
                            translatedParts.append(" ")
                        }
                        translatedParts.append(translated)
                    }
                }
            }

            val result = translatedParts.toString().trim()
            if (result.isNotEmpty()) result else ""
        } catch (e: Exception) {
            android.util.Log.e(
                "GoogleTranslateWeb",
                "Legacy parse failed: ${e.message}"
            )
            ""
        }
    }
}
