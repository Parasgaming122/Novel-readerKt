package com.example

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Translation engine abstraction supporting multiple backends.
 *
 * Currently supported:
 * - [Engine.GOOGLE_TRANSLATE] — Free Google Translate web endpoint (no key needed, high quality)
 * - [Engine.MYMEMORY]       — Free MyMemory API (no key needed, 5000 chars/day anonymous)
 * - [Engine.GEMINI]        — Google Gemini 2.5 Flash via Generative AI SDK (requires API key)
 */
enum class TranslationEngine(val key: String, val displayName: String) {
    GOOGLE_TRANSLATE("google_translate", "Google Translate (Free, No API Key)"),
    MYMEMORY("mymemory", "MyMemory (Free, No API Key)"),
    GEMINI("gemini", "Gemini AI (High Quality)");

    companion object {
        fun fromKey(key: String): TranslationEngine =
            entries.firstOrNull { it.key == key } ?: GOOGLE_TRANSLATE
    }
}

object TranslationCache {
    private val cache = mutableMapOf<String, String>()
    private val mutex = Mutex()
    private const val MAX_CACHE_SIZE = 2000

    private fun makeKey(text: String, engine: TranslationEngine): String {
        // Simple hash-based key to avoid storing full text as key (memory concern)
        val textHash = text.take(200).hashCode().toUInt().toString(16)
        return "${engine.key}:$textHash"
    }

    suspend fun get(text: String, engine: TranslationEngine): String? = mutex.withLock {
        cache[makeKey(text, engine)]
    }

    suspend fun put(text: String, engine: TranslationEngine, translated: String) = mutex.withLock {
        if (cache.size >= MAX_CACHE_SIZE) {
            // Evict oldest 25% entries
            val toRemove = cache.keys.take(MAX_CACHE_SIZE / 4)
            toRemove.forEach { cache.remove(it) }
        }
        cache[makeKey(text, engine)] = translated
    }

    fun clear() {
        cache.clear()
    }
}

/**
 * Unified translation orchestrator.
 *
 * Routes translation requests to the selected engine (Google Translate, MyMemory, or Gemini),
 * with caching support and fallback logic.
 *
 * Default engine is now GOOGLE_TRANSLATE which uses the free, high-quality
 * Google Translate web endpoint requiring no API key.
 */
object UnifiedTranslator {

    /**
     * Translates paragraphs using the specified engine, with caching.
     *
     * @param paragraphs Text paragraphs to translate
     * @param engine Which translation backend to use
     * @param geminiApiKey Required only when engine == GEMINI
     * @param targetLang Target language code (default: "en")
     * @return Translated paragraphs (same order as input, falls back to original on error)
     */
    suspend fun translate(
        paragraphs: List<String>,
        engine: TranslationEngine,
        geminiApiKey: String = "",
        targetLang: String = "en"
    ): List<String> = withContext(Dispatchers.IO) {
        if (paragraphs.isEmpty()) return@withContext emptyList()

        val results = mutableListOf<String>()

        when (engine) {
            TranslationEngine.GOOGLE_TRANSLATE -> {
                // Translate using free Google Translate web endpoint
                paragraphs.forEach { text ->
                    val cached = TranslationCache.get(text, TranslationEngine.GOOGLE_TRANSLATE)
                    if (cached != null) {
                        results.add(cached)
                    } else {
                        val translated = GoogleTranslateWebTranslator.translateParagraphs(
                            listOf(text), targetLang
                        ).firstOrNull() ?: text
                        TranslationCache.put(text, TranslationEngine.GOOGLE_TRANSLATE, translated)
                        results.add(translated)
                    }
                }
            }
            TranslationEngine.MYMEMORY -> {
                paragraphs.forEach { text ->
                    val cached = TranslationCache.get(text, TranslationEngine.MYMEMORY)
                    if (cached != null) {
                        results.add(cached)
                    } else {
                        val translated = MyMemoryTranslator.translateParagraphs(listOf(text)).firstOrNull() ?: text
                        TranslationCache.put(text, TranslationEngine.MYMEMORY, translated)
                        results.add(translated)
                    }
                }
            }
            TranslationEngine.GEMINI -> {
                if (geminiApiKey.trim().isEmpty()) {
                    android.util.Log.w("UnifiedTranslator", "Gemini engine selected but no API key provided")
                    return@withContext paragraphs
                }

                // Check cache for entire batch first
                val uncachedIndices = mutableListOf<Int>()
                val uncachedTexts = mutableListOf<String>()

                paragraphs.forEachIndexed { index, text ->
                    val cached = TranslationCache.get(text, TranslationEngine.GEMINI)
                    if (cached != null) {
                        results.add(cached)
                    } else {
                        results.add("") // placeholder
                        uncachedIndices.add(index)
                        uncachedTexts.add(text)
                    }
                }

                if (uncachedTexts.isNotEmpty()) {
                    try {
                        val translatedList = GeminiTranslator.translateParagraphs(uncachedTexts, geminiApiKey)
                        uncachedIndices.forEachIndexed { batchIndex, originalIndex ->
                            val translated = translatedList.getOrElse(batchIndex) { uncachedTexts[batchIndex] }
                            TranslationCache.put(uncachedTexts[batchIndex], TranslationEngine.GEMINI, translated)
                            results[originalIndex] = translated
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("UnifiedTranslator", "Gemini translation failed: ${e.message}")
                        // Fill remaining with originals
                        uncachedIndices.forEach { idx ->
                            if (results[idx].isEmpty()) results[idx] = paragraphs[idx]
                        }
                    }
                }
            }
        }

        results
    }
}
