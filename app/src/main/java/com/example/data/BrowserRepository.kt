package com.example.data

import android.net.Uri
import com.example.WtrLogManager
import com.example.sites.WebsiteSupportRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BrowserRepository(private val browserDao: BrowserDao) {
    val allHistory: Flow<List<HistoryEntry>> = browserDao.getAllHistory()
    val allBookmarks: Flow<List<BookmarkEntry>> = browserDao.getAllBookmarks()
    val allTabsFlow: Flow<List<TabEntry>> = browserDao.getAllTabsFlow()

    private val historyMutex = Mutex()

    private fun normalizeUrl(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val builder = uri.buildUpon()
            val keysToClear = listOf(
                "_x_tr_sl", "_x_tr_tl", "_x_tr_hl", "_x_tr_pto", "_x_tr_sch",
                "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content"
            )
            val queryNames = uri.queryParameterNames
            if (queryNames?.isNotEmpty() == true) {
                builder.clearQuery()
                for (name in queryNames) {
                    if (name !in keysToClear) {
                        val values = uri.getQueryParameters(name)
                        for (value in values) {
                            builder.appendQueryParameter(name, value)
                        }
                    }
                }
            }
            var normalized = builder.build().toString().trim()
            if (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length - 1)
            }
            normalized
        } catch (e: Exception) {
            url.trim().trimEnd('/')
        }
    }

    private fun getHost(url: String): String {
        return try {
            val host = Uri.parse(url).host?.lowercase() ?: ""
            host.replace(".translate.goog", "").replace("translate.goog", "").replace("www.", "")
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun insertHistory(url: String, title: String) {
        val normalizedInputUrl = normalizeUrl(url)
        val inputHost = getHost(url)
        val cleanTitle = title.trim()

        historyMutex.withLock {
            val historyList = try {
                browserDao.getAllHistoryList()
            } catch (e: Exception) {
                emptyList()
            }

            val existing = historyList.firstOrNull { entry ->
                normalizeUrl(entry.url) == normalizedInputUrl || (
                    cleanTitle.isNotEmpty() &&
                    cleanTitle.length > 3 &&
                    entry.title.trim().equals(cleanTitle, ignoreCase = true) &&
                    getHost(entry.url) == inputHost
                )
            }

            if (existing != null) {
                val bestTitle = if (cleanTitle.length > existing.title.length) cleanTitle else existing.title
                val bestUrl = if (url.length < existing.url.length && url.startsWith("https")) url else existing.url

                val updated = existing.copy(
                    url = bestUrl,
                    title = bestTitle.ifEmpty { title },
                    timestamp = System.currentTimeMillis()
                )
                browserDao.insertHistory(updated)
                try {
                    browserDao.deleteHistoryDuplicates(existing.url, updated.id)
                    browserDao.deleteHistoryDuplicates(url, updated.id)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                browserDao.insertHistory(HistoryEntry(url = url, title = title))
            }
        }
        try {
            browserDao.pruneHistory(500)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun deleteHistory(id: Long) = browserDao.deleteHistory(id)
    suspend fun clearHistory() = browserDao.clearHistory()

    private fun extractNovelAndChapter(title: String, url: String): Pair<String, String> {
        return WebsiteSupportRegistry.extractNovelAndChapter(title, url)
    }

    suspend fun insertBookmark(url: String, title: String, imageUrl: String? = null) {
        val host = try { Uri.parse(url).host ?: "" } catch (e: Exception) { "" }.lowercase()
        val hasNovelHost = WebsiteSupportRegistry.findSupport(url) != null || host.contains("translate.goog")
        val parsed = extractNovelAndChapter(title, url)
        val hasChapterInTitle = title.contains("Chapter", ignoreCase = true) || title.contains("Ch.", ignoreCase = true) || title.contains("Ch ", ignoreCase = true)
        val isNovel = hasNovelHost || hasChapterInTitle || (parsed.second != "Web Novel" && parsed.second != "Web Chapter")

        if (isNovel) {
            val cleanHost = host.replace("www.", "").replace("translate.goog", "").trim('.')
            browserDao.insertBookmark(
                BookmarkEntry(
                    url = url,
                    title = title,
                    isNovel = true,
                    novelTitle = parsed.first,
                    chapterTitle = parsed.second,
                    domain = cleanHost,
                    imageUrl = imageUrl,
                    lastViewedChapterUrl = url,
                    lastViewedChapterTitle = parsed.second
                )
            )
        } else {
            browserDao.insertBookmark(BookmarkEntry(url = url, title = title, isNovel = false))
        }
    }

    suspend fun updateNovelMetadata(url: String, novelTitle: String, chapterTitle: String, coverImage: String) {
        val host = try { Uri.parse(url).host?.replace("www.", "")?.replace("translate.goog", "")?.trim('.') ?: "" } catch (e: Exception) { "" }
        val allNovels = browserDao.getAllNovelBookmarks()
        val existingNovelBookmark = allNovels.firstOrNull { bkmk ->
            bkmk.domain == host && (bkmk.novelTitle == novelTitle || bkmk.url == url)
        }
        
        if (existingNovelBookmark != null) {
            val updated = existingNovelBookmark.copy(
                novelTitle = novelTitle.ifEmpty { existingNovelBookmark.novelTitle },
                lastViewedChapterTitle = chapterTitle.ifEmpty { existingNovelBookmark.lastViewedChapterTitle },
                imageUrl = if (coverImage.isNotEmpty() && coverImage != "null") coverImage else existingNovelBookmark.imageUrl
            )
            browserDao.updateBookmark(updated)
        }
    }

    suspend fun updateReadingProgress(url: String, title: String) {
        val parsed = extractNovelAndChapter(title, url)
        val novelTitleVal = parsed.first
        if (novelTitleVal.isNotEmpty() && novelTitleVal != "Wtr-Lab Browser" && parsed.second != "Web Novel" && parsed.second != "Web Chapter") {
            
            // Try explicit title match
            var existingNovelBookmark = browserDao.getNovelBookmark(novelTitleVal)
            
            // If explicit match fails, try path root matching for cases where title is translated vs untranslated
            if (existingNovelBookmark == null) {
                val host = try { Uri.parse(url).host?.replace("www.", "")?.replace("translate.goog", "")?.trim('.') ?: "" } catch (e: Exception) { "" }
                val uriSegments = try { Uri.parse(url).pathSegments } catch (e: Exception) { emptyList() }
                val urlPrefix = if (uriSegments.isNotEmpty()) uriSegments.firstOrNull() ?: "" else ""

                if (host.isNotEmpty()) {
                    val allNovels = browserDao.getAllNovelBookmarks()
                    existingNovelBookmark = allNovels.firstOrNull { bkmk ->
                        val safeNovelTitle = bkmk.novelTitle ?: ""
                        bkmk.domain == host && (bkmk.url.contains(urlPrefix) || (safeNovelTitle.isNotEmpty() && url.contains(safeNovelTitle.take(5))) || bkmk.url == url)
                    }
                }
            }

            if (existingNovelBookmark != null) {
                val isTranslatedTitle = title.length > existingNovelBookmark.title.length || (novelTitleVal != existingNovelBookmark.novelTitle && !novelTitleVal.any { it in '\u4e00'..'\u9fa5' })
                
                var updateTitle = existingNovelBookmark.title
                var updateNovelTitle = existingNovelBookmark.novelTitle
                
                // Save translated title properly if we detect it
                if (isTranslatedTitle) {
                    updateTitle = title
                    updateNovelTitle = novelTitleVal
                }

                val updated = existingNovelBookmark.copy(
                    title = updateTitle,
                    novelTitle = updateNovelTitle,
                    lastViewedChapterUrl = url,
                    lastViewedChapterTitle = parsed.second
                )
                browserDao.updateBookmark(updated)
            }
        }
    }

    suspend fun deleteBookmark(id: Long) = browserDao.deleteBookmark(id)
    suspend fun deleteBookmarkByUrl(url: String) = browserDao.deleteBookmarkByUrl(url)
    fun isBookmarked(url: String): Flow<Boolean> = browserDao.isBookmarked(url)

    suspend fun getAllTabs(): List<TabEntry> = browserDao.getAllTabs()
    suspend fun insertTab(tab: TabEntry): Long = browserDao.insertTab(tab)
    suspend fun updateTab(tab: TabEntry) = browserDao.updateTab(tab)
    suspend fun deleteTab(id: Long) = browserDao.deleteTab(id)
    suspend fun clearTabs() = browserDao.clearTabs()

    suspend fun validateDatabaseIntegrity(context: android.content.Context? = null): Boolean {
        return try {
            val history = browserDao.getAllHistoryList()
            val bookmarks = browserDao.getAllBookmarksList()
            val tabs = browserDao.getAllTabs()
            
            // Check for empty/malformed entries in primary fields
            val historyValid = history.none { it.url.isEmpty() }
            val bookmarksValid = bookmarks.none { it.url.isEmpty() }
            val tabsValid = tabs.none { it.url.isEmpty() }
            
            historyValid && bookmarksValid && tabsValid
        } catch (e: Exception) {
            WtrLogManager.log(context, "Database integrity check failed: ${e.message}")
            false
        }
    }
}
