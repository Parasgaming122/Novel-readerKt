package com.paras.novelreaderkt

import android.util.LruCache
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

object GeminiTranslator {
    private const val BASE_SYSTEM_INSTRUCTION = """
You are a professional literary translator specializing in Chinese web novels (Xianxia, Wuxia, Xuanhuan, Danmei, LitRPG/System, historical court intrigue). Translate each Chinese text segment into polished, immersive English.

INPUT FORMAT: JSON array [{"id": 0, "text": "..."}, ...]
OUTPUT FORMAT: JSON array [{"id": 0, "text": "..."}, ...] — raw JSON only, no markdown, no explanations.

TRANSLATION RULES:

1. CONTENT PRESERVATION — ABSOLUTE PRIORITY:
   - NEVER remove, omit, or merge source sentences. Every sentence must have a corresponding translation.
   - If a sentence seems trivial or repetitive, still translate it. The reader chose to read it.
   - Preserve the EXACT number of text blocks. Never combine or split blocks.

2. PRESERVE ICONIC NOVEL PHRASES:
   - These are beloved by readers and MUST be preserved, not paraphrased away:
     * "You court death!" — keep as "You court death!" (iconic, do NOT soften)
     * "Courting death!" — keep as "Courting death!"
     * "Coughed up a mouthful of blood" — keep as is, this is a dramatic staple
     * "His face turned ashen" — keep as is
     * "Didn't know whether to laugh or cry" — keep as is or very close
     * "Given an inch, they'd take a mile" — keep the idiom
     * "Eyes filled with killing intent" — keep as is
     * "A cold light flashed in his eyes" — keep as is
     * "Flew into a rage" — keep as is
     * "With a wave of his hand" — keep as is
     * " spat blood " / " vomited blood " — keep these dramatic moments
     * "cold snort" / "sneered" — keep the exact tone
   - RULE: When a Chinese phrase already has a well-established English equivalent in the web novel community, USE that equivalent. Do NOT over-literary-fy it or replace it with a fancy alternative.

3. NOISE REMOVAL — REMOVE THESE FROM TRANSLATION OUTPUT:
   - Do NOT translate: "friendly reminder", "温馨提示", "本章未完", "点击下一页", "继续阅读",
     "手机用户请浏览", "更多精彩内容", "投推荐票", "最新网址", "最新更新时间",
     "join our discord", "patreon", "support the author", "rate this novel",
     "read online free", "sign up to unlock", "this chapter was posted by",
     "report any errors", "found a bug", "advertisement", "sponsored content"
   - If a paragraph is PURELY noise (ads, site announcements, recruitment text), translate it as an empty string "".
   - If a paragraph has story content mixed with a noise suffix, translate only the story content and drop the noise.

4. PROPER NOUNS & NAME HIGHLIGHTING:
   - Character personal names: Keep in Pinyin (Xiao Yan, Xie Lian, Lin Feng). Consistent spelling throughout.
   - Wrap ALL proper nouns (character names, place names, sect names) in <wtr-name data-term="pinyin-or-term">Name</wtr-name> tags.
   - Example: "<wtr-name data-term="Xiao Yan">Xiao Yan</wtr-name>'s Dou Qi erupted..."
   - Sect/Org/Title names: Translate to English (e.g., "Heavenly Sword <wtr-name data-term="Sect">Sect</wtr-name>")
   - Cultivation terms: Keep established English terms (Dou Qi, Qi, Spiritual Energy, Dantian, Foundation Establishment, Nascent Soul, Core Formation, Soul Formation, Deity Transformation, etc.)

5. USE THE GLOSSARY (if provided below):
   - If a NOVEL-SPECIFIC GLOSSARY section is present, use those exact translations for the listed terms.
   - Apply the <wtr-name> tags to ALL glossary terms in the output.
   - Keep translations consistent with the glossary across all paragraphs.

6. NUMBER SCALING:
   - 万 (10,000) → "100,000" or "a hundred thousand"
   - 亿 (100 million) → "100,000,000" or "a hundred million"

7. FORMATTING:
   - Preserve 【】 and 『』 brackets exactly.
   - NO bold, italics, markdown, or code formatting in the output.
   - NO outer conversational wrappers or explanations.
   - Output ONLY the raw JSON array.

8. NATURAL BUT FAITHFUL:
   - Make dialogue sound natural in English — contractions, varied sentence structure, appropriate register.
   - Internal monologue should feel intimate and immediate.
   - Scene descriptions should be vivid and cinematic.
   - BUT: Never sacrifice content for style. Every source sentence must be present.
"""

    private var cachedModel: GenerativeModel? = null
    private var cachedApiKey: String? = null
    private var cachedSystemInstruction: String? = null

    // --- Translation result cache (LRU) ---
    // Avoids re-translating the same page on revisit. Keyed by SHA-256 of (apiKey + paragraphs hash + context hash).
    private val translationCache: LruCache<String, List<String>> = LruCache<String, List<String>>(20) // ~20 pages

    @Synchronized
    private fun getModel(apiKey: String, novelContext: String?): GenerativeModel {
        val fullInstruction = if (!novelContext.isNullOrBlank()) {
            BASE_SYSTEM_INSTRUCTION.trimIndent() + "\n\n" + novelContext
        } else {
            BASE_SYSTEM_INSTRUCTION.trimIndent()
        }

        // Reuse cached model if API key AND system instruction match
        if (cachedModel != null && cachedApiKey == apiKey && cachedSystemInstruction == fullInstruction) {
            return cachedModel!!
        }

        val newModel = GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = apiKey,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                temperature = 0.3f
            },
            systemInstruction = com.google.ai.client.generativeai.type.content {
                text(fullInstruction)
            }
        )
        cachedModel = newModel
        cachedApiKey = apiKey
        cachedSystemInstruction = fullInstruction
        return newModel
    }

    /**
     * Generate a stable cache key from the input parameters.
     */
    private fun buildCacheKey(apiKey: String, paragraphs: List<String>, novelContext: String?): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(apiKey.toByteArray())
        for (p in paragraphs) md.update(p.toByteArray())
        if (!novelContext.isNullOrBlank()) md.update(novelContext.toByteArray())
        val hash = md.digest()
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Translate paragraphs with optional novel-specific glossary context.
     * Results are cached in an LRU cache to avoid re-translating on page revisit.
     *
     * @param paragraphs List of Chinese text paragraphs to translate
     * @param apiKey Gemini API key
     * @param novelContext Optional novel-specific glossary context string
     * @return List of translated English paragraphs (with <wtr-name> tags for proper nouns)
     */
    suspend fun translateParagraphs(
        paragraphs: List<String>,
        apiKey: String,
        novelContext: String? = null
    ): List<String> = withContext(Dispatchers.IO) {
        if (paragraphs.isEmpty()) return@withContext emptyList()
        if (apiKey.trim().isEmpty()) {
            throw IllegalArgumentException("Gemini API key is empty.")
        }

        // Check cache first
        val cacheKey = buildCacheKey(apiKey, paragraphs, novelContext)
        translationCache.get(cacheKey)?.let { cached ->
            android.util.Log.d("GeminiTranslator", "Cache hit for page (${paragraphs.size} paragraphs)")
            return@withContext cached
        }

        try {
            // 1. Construct JSON input
            val jsonInput = JSONArray()
            paragraphs.forEachIndexed { index, text ->
                val obj = JSONObject()
                obj.put("id", index)
                obj.put("text", text)
                jsonInput.put(obj)
            }
            val inputText = jsonInput.toString()

            // 2. Initialize or get cached model instance
            val model = getModel(apiKey, novelContext)

            // 3. Call generate content
            val response = model.generateContent(inputText)
            val responseText = response.text ?: ""
            if (responseText.isEmpty()) {
                throw Exception("Received empty response from Gemini API")
            }

            // 4. Parse JSON translation response
            val cleanResponse = try {
                var cleaned = responseText.trim()
                if (cleaned.startsWith("```json")) {
                    cleaned = cleaned.substringBeforeLast("```").substringAfter("```json")
                } else if (cleaned.startsWith("```")) {
                    cleaned = cleaned.substringBeforeLast("```").substringAfter("```")
                }
                cleaned.trim()
            } catch (e: Exception) {
                responseText
            }

            val jsonArray = JSONArray(cleanResponse)
            val translatedMap = mutableMapOf<Int, String>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getInt("id")
                val text = obj.getString("text")
                translatedMap[id] = text
            }

            // 5. Build translated paragraphs matching the original index
            val result = mutableListOf<String>()
            paragraphs.forEachIndexed { index, originalText ->
                val translatedText = translatedMap[index]
                if (translatedText != null && translatedText.isNotEmpty()) {
                    result.add(translatedText)
                } else {
                    result.add(originalText)
                }
            }

            // Store in cache
            translationCache.put(cacheKey, result)

            result
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    /**
     * Strip <wtr-name> tags from translated text for TTS/plain-text use.
     * Returns just the plain text content.
     */
    fun stripNameTags(text: String): String {
        return text.replace(Regex("""<wtr-name[^>]*>"""), "").replace("</wtr-name>", "")
    }

    /** Clear the translation cache */
    fun clearCache() {
        translationCache.evictAll()
    }
}