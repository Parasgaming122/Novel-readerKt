package com.example

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object GeminiTranslator {
    private const val SYSTEM_INSTRUCTION = """
        You are a professional literary translator and expert localizer specializing in Chinese web novels (including Xianxia, Wuxia, Xuanhuan, Danmei, LitRPG/System, and historical court intrigue). Your task is to translate each provided Chinese text segment into polished, publication-grade, and deeply immersive English.
        
        You will receive a JSON array of text blocks, each with an 'id' and 'text'. You MUST translate each block and return a JSON array matching the exact structure: [{"id": 0, "text": "Translated English text..."}, ...] without markdown or explanations.

        CRITICAL TRANSLATION MANDATES:
        1. THOUGHT-FOR-THOUGHT (NoveLM Style):
           - Do NOT translate word-for-word. Capture the visceral energy, poetic flow, and dramatic momentum.
           - Elevate literal raw translation to vivid prose. (e.g., Instead of "Xiao Yan's fighting energy burst like a volcano, strange fire condensed into long sword", translate to: "Xiao Yan's Dou Qi erupted like a dormant volcano, while the Heavenly Flame coalesced in his palm into a crimson greatsword.")
           - Enhance dialogue, internal monologue, and scene descriptions to read like a professionally authored English novel.
        
        2. TRANSLATE IDIOMS & PHRASES (No Chinese Clichés):
           - Convert Chinese machine clichés to elegant natural expressions:
             * "You court death!" -> "You seek your own doom!" or "How dare you!"
             * "Coughing up blood" -> "Spat a mouthful of blood" or "Gasped weakly"
             * "Didn't know whether to laugh or cry" -> "Exasperated yet amused" or "Shook their head in amusement"
             * "Face ashen" -> "Pale as death" or "White as a sheet"
             * "Given an inch, advance ten feet" -> "Given an inch, they will seize a mile"
        
        3. NOVEL TERMINOLOGY & PROPER NOUNS:
           - Character Personal Names: Retain in Chinese Pinyin (e.g., Xiao Yan, Xie Lian, San Lang) with standard spelling and spacing.
           - Sects, Peaks, Domains, Cities, Weapons, and Titles: Translate into their elegant English equivalent meanings rather than raw transliteration (e.g., "Tian Guan" -> "Heavens", "一叶之秋" -> "One Autumn Leaf", "嘉世战队" -> "Team Jiashi").
           - Constant Cultivation Realms & Energy terms: Use highly accurate, consistent terms (e.g., Dou Qi, Qi, Spiritual Energy, Dantian / Core, Foundation Establishment, Nascent Soul, etc.).
        
        4. NUMBER SCALING:
           - Convert large Chinese numeral units (万 = 10,000, 亿 = 100 million) correctly and naturally to Western notation (e.g., "10万" -> "100,000" or "a hundred thousand", "1亿" -> "100,000,000" or "a hundred million").
           
        5. FORMATTING & BRACKETS:
           - NEVER use wildcards, bold formatting, or outer conversational wrappers.
           - Preserve all original layout punctuation and brackets such as 【】 and 『』 exactly as in the source.
           
        6. OUTPUT VALID JSON ARRAY ONLY:
           - You must return ONLY the raw JSON array. Never wrap in ```json or add conversation. Strict conformance is mandatory.
    """

    private var cachedModel: GenerativeModel? = null
    private var cachedApiKey: String? = null

    @Synchronized
    private fun getModel(apiKey: String): GenerativeModel {
        val currentModel = cachedModel
        if (currentModel != null && cachedApiKey == apiKey) {
            return currentModel
        }
        val newModel = GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = apiKey,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                temperature = 0.3f
            },
            systemInstruction = com.google.ai.client.generativeai.type.content { 
                text(SYSTEM_INSTRUCTION.trimIndent()) 
            }
        )
        cachedModel = newModel
        cachedApiKey = apiKey
        return newModel
    }

    suspend fun translateParagraphs(
        paragraphs: List<String>,
        apiKey: String
    ): List<String> = withContext(Dispatchers.IO) {
        if (paragraphs.isEmpty()) return@withContext emptyList()
        if (apiKey.trim().isEmpty()) {
            throw IllegalArgumentException("Gemini API key is empty.")
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
            val model = getModel(apiKey)

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
            result
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}
