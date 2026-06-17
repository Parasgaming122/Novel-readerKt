package com.paras.novelreaderkt

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.paras.novelreaderkt.data.BrowserDao
import com.paras.novelreaderkt.data.NovelGlossaryEntry
import com.paras.novelreaderkt.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages per-novel translation context (glossary) for Gemini translation.
 *
 * A glossary is created progressively as the user reads (auto-detected terms).
 * When the novel's info page is bookmarked, the glossary is also used as context in prompts.
 *
 * Categories: character, sect, system, technique, item, location, relationship, title, other
 */
object NovelContextManager {

    private val contextCache = ConcurrentHashMap<String, String>()
    private const val MAX_CONTEXT_LENGTH = 4000
    private const val MIN_CHINESE_RATIO = 0.3

    // --- Novel reading info cache (lightweight, for context UI) ---
    private const val NOVEL_INFO_PREFS = "wtr_novel_reading_info"
    private val novelInfoCache = ConcurrentHashMap<String, NovelReadingInfo>()

    data class NovelReadingInfo(
        val novelKey: String,
        val novelTitle: String,
        val lastChapterUrl: String,
        val hasBookmark: Boolean,
        val lastReadTime: Long = System.currentTimeMillis(),
        val termCount: Int = 0
    )

    /** Derive a novelKey from URL and title (e.g., "timotxt.com:斗破苍穹") */
    fun buildNovelKey(url: String, novelTitle: String): String {
        val host = try {
            Uri.parse(url).host?.replace("www.", "")?.replace(".translate.goog", "")?.trim('.') ?: "unknown"
        } catch (e: Exception) { "unknown" }
        val cleanTitle = novelTitle.take(30) // Limit title length for key stability
        return "$host:$cleanTitle"
    }

    /** Build a novelKey using only the host and a path-derived novel identifier */
    fun buildNovelKeyFromUrl(url: String): String {
        val host = try {
            Uri.parse(url).host?.replace("www.", "")?.replace(".translate.goog", "")?.trim('.') ?: "unknown"
        } catch (e: Exception) { "unknown" }
        val path = try { Uri.parse(url).pathSegments } catch (e: Exception) { emptyList() }
        val novelSlug = if (path.size >= 2) path.take(2).joinToString("/") else path.firstOrNull() ?: "unknown"
        return "$host:$novelSlug"
    }

    /**
     * Check if a novel has a bookmark (which triggers context usage).
     * Uses the database to check for a matching novel bookmark.
     */
    suspend fun hasBookmarkedNovel(context: Context, url: String, novelTitle: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val dao = AppDatabase.getDatabase(context).browserDao()
                val host = try {
                    Uri.parse(url).host?.replace("www.", "")?.replace(".translate.goog", "")?.trim('.') ?: ""
                } catch (e: Exception) { "" }

                if (novelTitle.isNotEmpty() && novelTitle != "Wtr-Lab Browser") {
                    val byTitle = dao.getNovelBookmark(novelTitle)
                    if (byTitle != null) return@withContext true
                }

                if (host.isNotEmpty()) {
                    val pathSegments = try { Uri.parse(url).pathSegments } catch (e: Exception) { emptyList() }
                    val urlPrefix = pathSegments.firstOrNull() ?: ""
                    val byHost = dao.getNovelBookmarkByHost(host, urlPrefix, novelTitle.take(8), url)
                    if (byHost != null) return@withContext true
                }

                false
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * Build a formatted context string for the Gemini prompt.
     * Returns null if no glossary exists for this novel.
     */
    suspend fun buildContextString(context: Context, novelKey: String): String? {
        contextCache[novelKey]?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val dao = AppDatabase.getDatabase(context).browserDao()
                val entries = dao.getGlossaryForNovel(novelKey)
                if (entries.isEmpty()) return@withContext null

                val sb = StringBuilder()
                sb.append("NOVEL-SPECIFIC GLOSSARY & CONTEXT:\n")
                sb.append("(Use these translations consistently. Wrap all proper nouns in <wtr-name> tags.)\n\n")

                val grouped = entries.groupBy { it.category }
                val categoryHeaders = mapOf(
                    "character" to "CHARACTERS",
                    "sect" to "SECTS & ORGANIZATIONS",
                    "system" to "POWER SYSTEMS & CULTIVATION",
                    "technique" to "TECHNIQUES & ABILITIES",
                    "item" to "ITEMS & ARTIFACTS",
                    "location" to "LOCATIONS & REALMS",
                    "relationship" to "RELATIONSHIPS & TITLES",
                    "title" to "TITLES & HONORIFICS",
                    "other" to "OTHER TERMS"
                )

                for ((category, terms) in grouped) {
                    val header = categoryHeaders[category] ?: category.uppercase()
                    sb.append("[$header]\n")
                    for (term in terms) {
                        val pinyinPart = if (!term.pinyin.isNullOrBlank() && term.pinyin != term.translatedText) " (${term.pinyin})" else ""
                        val notesPart = if (!term.notes.isNullOrBlank()) " — ${term.notes}" else ""
                        sb.append("  ${term.sourceText} → ${term.translatedText}$pinyinPart$notesPart\n")
                    }
                    sb.append("\n")
                }

                val result = sb.toString()
                if (result.length <= MAX_CONTEXT_LENGTH) {
                    contextCache[novelKey] = result
                }
                result
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Store or update novel reading info for the context file UI.
     * Uses SharedPreferences for fast access (no Room overhead).
     */
    fun storeOrUpdateNovelReadingInfo(context: Context, novelKey: String, novelTitle: String, chapterUrl: String, hasBookmark: Boolean) {
        try {
            val prefs = context.getSharedPreferences(NOVEL_INFO_PREFS, Context.MODE_PRIVATE)
            val existingTime = prefs.getLong("${novelKey}_lastRead", 0L)
            // Only update if this is a newer chapter (simple heuristic: URL changed)
            val existingUrl = prefs.getString("${novelKey}_lastUrl", "") ?: ""
            if (chapterUrl != existingUrl || System.currentTimeMillis() - existingTime > 60000) {
                prefs.edit()
                    .putString("${novelKey}_title", novelTitle)
                    .putString("${novelKey}_lastUrl", chapterUrl)
                    .putLong("${novelKey}_lastRead", System.currentTimeMillis())
                    .putBoolean("${novelKey}_bookmarked", hasBookmark)
                    .apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Get all novel keys that have been read with Gemini translation active.
     */
    fun getAllReadNovelKeys(context: Context): List<NovelReadingInfo> {
        try {
            val prefs = context.getSharedPreferences(NOVEL_INFO_PREFS, Context.MODE_PRIVATE)
            val allKeys = prefs.all.keys
                .filter { it.endsWith("_title") }
                .map { it.removeSuffix("_title") }
                .distinct()

            return allKeys.mapNotNull { key ->
                val title = prefs.getString("${key}_title", "") ?: return@mapNotNull null
                val lastUrl = prefs.getString("${key}_lastUrl", "") ?: ""
                val lastRead = prefs.getLong("${key}_lastRead", 0L)
                val bookmarked = prefs.getBoolean("${key}_bookmarked", false)
                NovelReadingInfo(
                    novelKey = key,
                    novelTitle = title,
                    lastChapterUrl = lastUrl,
                    hasBookmark = bookmarked,
                    lastReadTime = lastRead
                )
            }.sortedByDescending { it.lastReadTime }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    /**
     * Get the novel info for a specific novelKey.
     */
    fun getNovelReadingInfo(context: Context, novelKey: String): NovelReadingInfo? {
        try {
            val prefs = context.getSharedPreferences(NOVEL_INFO_PREFS, Context.MODE_PRIVATE)
            val title = prefs.getString("${novelKey}_title", "") ?: return null
            val lastUrl = prefs.getString("${novelKey}_lastUrl", "") ?: ""
            val lastRead = prefs.getLong("${novelKey}_lastRead", 0L)
            val bookmarked = prefs.getBoolean("${novelKey}_bookmarked", false)
            return NovelReadingInfo(
                novelKey = novelKey,
                novelTitle = title,
                lastChapterUrl = lastUrl,
                hasBookmark = bookmarked,
                lastReadTime = lastRead
            )
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Auto-detect proper nouns from translated text and add to glossary.
     * Parses <wtr-name> tags from Gemini's output and extracts Chinese↔English mappings.
     * Enhanced to better match Chinese source text using parallel paragraph alignment.
     */
    suspend fun autoDetectTermsFromTranslation(
        context: Context,
        novelKey: String,
        chineseParagraphs: List<String>,
        translatedParagraphs: List<String>
    ) {
        withContext(Dispatchers.IO) {
            try {
                val dao = AppDatabase.getDatabase(context).browserDao()

                // Extract <wtr-name data-term="...">Name</wtr-name> patterns from translations
                val nameTagRegex = Regex("""<wtr-name\s+data-term="([^"]*)"[^>]*>([^<]+)</wtr-name>""")
                val allTranslatedText = translatedParagraphs.joinToString(" ")
                val matches = nameTagRegex.findAll(allTranslatedText)

                val newEntries = mutableListOf<NovelGlossaryEntry>()
                for (match in matches) {
                    val pinyin = match.groupValues[1].trim()
                    val translatedName = match.groupValues[2].trim()
                    if (translatedName.length < 2 || translatedName.length > 40) continue

                    // Try to find the corresponding Chinese term using parallel paragraph alignment
                    val sourceText = findChineseSourceParallel(chineseParagraphs, translatedParagraphs, translatedName, pinyin)
                    if (sourceText == null) continue

                    // Check if already exists
                    val existing = dao.getGlossaryTerm(novelKey, sourceText)
                    if (existing != null) {
                        dao.incrementTermFrequency(novelKey, sourceText)
                    } else {
                        val category = classifyTerm(sourceText, translatedName)
                        newEntries.add(
                            NovelGlossaryEntry(
                                novelKey = novelKey,
                                category = category,
                                sourceText = sourceText,
                                translatedText = translatedName,
                                pinyin = if (pinyin.isNotEmpty() && pinyin != translatedName) pinyin else null,
                                isAutoDetected = true
                            )
                        )
                    }
                }

                if (newEntries.isNotEmpty()) {
                    dao.insertGlossaryEntries(newEntries)
                    contextCache.remove(novelKey)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Better Chinese source matching using parallel paragraph alignment.
     * For each translated paragraph containing the English term, find the corresponding
     * Chinese paragraph and look for the most likely Chinese proper noun.
     */
    private fun findChineseSourceParallel(
        chineseParagraphs: List<String>,
        translatedParagraphs: List<String>,
        translatedName: String,
        pinyin: String
    ): String? {
        // Find paragraphs that contain this translated name
        for (i in translatedParagraphs.indices) {
            if (i >= chineseParagraphs.size) break
            val translated = translatedParagraphs[i]
            if (!translated.contains(translatedName)) continue

            val chinese = chineseParagraphs[i]

            // Strategy 1: Look for 2-4 char Chinese terms that appear frequently in this paragraph
            val chineseTerms = Regex("""[\u4e00-\u9fff]{2,6}""").findAll(chinese).map { it.value }.toList()
            if (chineseTerms.isNotEmpty()) {
                // Filter to terms that look like proper nouns (not common words)
                val properNounCandidates = chineseTerms.filter { term ->
                    // Proper nouns are typically 2-3 chars and appear as distinct entities
                    term.length in 2..4 && !isCommonChineseWord(term)
                }
                if (properNounCandidates.isNotEmpty()) {
                    // Return the first candidate (most likely to be the name in a name-first culture)
                    return properNounCandidates.first()
                }
            }

            // Strategy 2: If the pinyin matches, try harder
            if (pinyin.isNotBlank() && pinyin.length >= 2) {
                val candidate = chineseTerms.firstOrNull { it.length in 2..4 }
                if (candidate != null) return candidate
            }
        }

        return null
    }

    /** Common Chinese words that are NOT proper nouns */
    private val commonChineseWords = setOf(
        "的", "了", "是", "在", "我", "有", "和", "就", "不", "人", "都", "一", "一个",
        "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好",
        "自己", "这", "他", "她", "它", "们", "那", "里", "什么", "可以", "没", "把",
        "被", "从", "让", "用", "对", "而", "但", "又", "还", "只", "已", "已经",
        "这个", "那个", "一个", "什么", "怎么", "为什么", "因为", "所以", "如果",
        "虽然", "但是", "然后", "不过", "可是", "或者", "而且", "以及", "之后",
        "之前", "时候", "地方", "东西", "事情", "这样", "那样", "什么", "怎么",
        "出来", "起来", "下来", "过来", "回来", "出去", "进来", "上来", "下来",
        "知道", "觉得", "看到", "听到", "想到", "得到", "找到", "做到", "说到",
        "时候", "什么", "怎么", "为什么", "可是", "不过", "或者", "而且"
    )

    private fun isCommonChineseWord(term: String): Boolean {
        return commonChineseWords.contains(term)
    }

    /**
     * Classify a term into a category based on heuristics.
     */
    private fun classifyTerm(chineseSource: String, englishTranslation: String): String {
        val lowerEn = englishTranslation.lowercase()

        // Relationship keywords
        val relationSuffixes = listOf("之父", "之母", "之妻", "之夫", "师兄", "师妹", "师弟", "师姐", "师父", "师傅", "徒弟", "孙子", "爷爷", "奶奶", "外公", "外婆")
        if (relationSuffixes.any { chineseSource.contains(it) }) return "relationship"
        val relationEnKeywords = listOf("father", "mother", "brother", "sister", "wife", "husband", "son", "daughter", "master", "disciple", "elder brother", "younger sister")
        if (relationEnKeywords.any { lowerEn.contains(it) }) return "relationship"

        // Sect/Organization suffixes
        val sectSuffixes = listOf("宗", "门", "派", "阁", "殿", "城", "宫", "帮", "会", "盟", "族", "世家")
        if (sectSuffixes.any { chineseSource.contains(it) }) return "sect"
        if (lowerEn.contains("sect") || lowerEn.contains("clan") || lowerEn.contains("guild") ||
            lowerEn.contains("academy") || lowerEn.contains("faction") || lowerEn.contains("organization")) return "sect"

        // Cultivation/Power system terms
        val systemKeywords = listOf("气", "灵", "修", "丹", "境", "阶", "级", "道", "法", "力", "神", "仙", "魔", "妖", "圣", "帝", "王", "皇")
        if (systemKeywords.any { chineseSource.contains(it) } && (chineseSource.length <= 6)) return "system"
        if (lowerEn.contains("realm") || lowerEn.contains("stage") || lowerEn.contains("cultivation") ||
            lowerEn.contains("energy") || lowerEn.contains("qi ") || lowerEn.contains("core") ||
            lowerEn.contains("soul") || lowerEn.contains("spirit")) return "system"

        // Technique/Ability
        val techniqueSuffixes = listOf("功", "法", "诀", "术", "武", "剑", "掌", "拳", "指", "爪", "腿")
        if (techniqueSuffixes.any { chineseSource.endsWith(it) }) return "technique"
        if (lowerEn.contains("technique") || lowerEn.contains("art") || lowerEn.contains("method") ||
            lowerEn.contains("skill") || lowerEn.contains("move") || lowerEn.endsWith(" strike") ||
            lowerEn.endsWith(" palm") || lowerEn.endsWith(" finger")) return "technique"

        // Item/Artifact
        val itemSuffixes = listOf("剑", "刀", "枪", "鼎", "炉", "珠", "镜", "丹", "药", "符", "阵", "甲", "莲")
        if (itemSuffixes.any { chineseSource.endsWith(it) || chineseSource.contains(it) }) return "item"
        if (lowerEn.contains("sword") || lowerEn.contains("blade") || lowerEn.contains("pill") ||
            lowerEn.contains("artifact") || lowerEn.contains("weapon") || lowerEn.contains("treasure")) return "item"

        // Location
        val locationSuffixes = listOf("山", "海", "林", "谷", "洞", "域", "界", "州", "省", "国", "大陆", "天", "地")
        if (locationSuffixes.any { chineseSource.endsWith(it) }) return "location"
        if (lowerEn.contains("mountain") || lowerEn.contains("sea") || lowerEn.contains("forest") ||
            lowerEn.contains("valley") || lowerEn.contains("realm") || lowerEn.contains("continent") ||
            lowerEn.contains("region") || lowerEn.contains("city")) return "location"

        // Title/Honorific
        val titleKeywords = listOf("老祖", "前辈", "师", "弟", "兄", "姐", "妹", "长老", "宗主", "掌门", "帝", "皇", "王", "圣", "尊", "殿主")
        if (titleKeywords.any { chineseSource.contains(it) }) return "title"
        if (lowerEn.contains("elder") || lowerEn.contains("master") || lowerEn.contains("lord") ||
            lowerEn.contains("saint") || lowerEn.contains("emperor") || lowerEn.contains("king")) return "title"

        // Default: character (most common proper noun type)
        return "character"
    }

    /**
     * Detect if text is primarily Chinese content.
     * Returns true if at least MIN_CHINESE_RATIO of non-whitespace characters are CJK.
     */
    fun isChineseContent(text: String): Boolean {
        if (text.isBlank()) return false
        val nonWhitespace = text.filter { !it.isWhitespace() }
        if (nonWhitespace.length < 10) return false
        val chineseCount = nonWhitespace.count { it in '\u4e00'..'\u9fff' || it in '\u3400'..'\u4dbf' }
        return chineseCount.toDouble() / nonWhitespace.length >= MIN_CHINESE_RATIO
    }

    /**
     * Check if a URL is likely an info/book page rather than a chapter page.
     * Info pages typically don't have a chapter-number-like segment in the URL path.
     * Returns false for SPA-style sites (novelhubapp, wtr-lab) that use fragment routing.
     */
    fun isLikelyInfoPage(url: String): Boolean {
        val urlLower = url.lowercase()

        // SPA-style sites always use chapter-like URLs; never treat as info pages
        if (urlLower.contains("novelhubapp") || urlLower.contains("wtr-lab")) return false

        val path = try { Uri.parse(url).path ?: "" } catch (e: Exception) { return false }
        val segments = path.split("/").filter { it.isNotBlank() }

        // Very short paths (just domain + 0-1 segment) are likely info/home pages
        if (segments.size <= 1) return true

        val lastSegment = segments.last()

        // If the last segment is purely numeric (chapter ID), it's a chapter page
        if (lastSegment.matches(Regex("\\d+"))) return false

        // If the last segment ends with .html or .htm, it's likely a chapter page
        if (lastSegment.contains(".html") || lastSegment.contains(".htm")) {
            // But exclude if the URL also has info-page indicators
            val infoKeywords = listOf("/info", "/book/", "/novel/", "/detail", "/intro", "/summary",
                "/index", "/catalog", "/directory", "/list", "/toc")
            if (infoKeywords.any { urlLower.contains(it) }) return true
            return false
        }

        // No .html extension and not purely numeric — likely an info page
        // Exception: if the second-to-last segment is a numeric ID (e.g., /Article/12345/67890)
        if (segments.size >= 2 && segments[segments.size - 2].matches(Regex("\\d+")) && lastSegment.matches(Regex("\\d+"))) {
            return false
        }

        return true
    }

    /** Clear the context cache (e.g., after glossary changes) */
    fun invalidateCache(novelKey: String? = null) {
        if (novelKey != null) {
            contextCache.remove(novelKey)
        } else {
            contextCache.clear()
        }
    }
}