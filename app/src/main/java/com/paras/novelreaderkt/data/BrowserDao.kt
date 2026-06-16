package com.paras.novelreaderkt.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowserDao {
    // History
    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    suspend fun getAllHistoryList(): List<HistoryEntry>

    @Query("SELECT * FROM history WHERE url = :url LIMIT 1")
    suspend fun getHistoryByUrl(url: String): HistoryEntry?

    @Query("SELECT * FROM history WHERE url = :url OR (title = :title AND id != 0) LIMIT 1")
    suspend fun getHistoryByUrlOrTitle(url: String, title: String): HistoryEntry?

    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun pruneHistory(limit: Int)

    @Query("SELECT COUNT(*) FROM history")
    suspend fun getHistoryCount(): Int

    @Query("DELETE FROM history WHERE id IN (SELECT id FROM history ORDER BY timestamp DESC LIMIT -1 OFFSET :limit)")
    suspend fun pruneHistoryOffset(limit: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: HistoryEntry)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteHistory(id: Long)

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    @Query("DELETE FROM history WHERE url = :url AND id != :keepId")
    suspend fun deleteHistoryDuplicates(url: String, keepId: Long)

    // Bookmarks
    @Query("SELECT * FROM bookmarks ORDER BY timestamp DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntry>>

    @Query("SELECT * FROM bookmarks ORDER BY timestamp DESC")
    suspend fun getAllBookmarksList(): List<BookmarkEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(entry: BookmarkEntry)

    @Update
    suspend fun updateBookmark(entry: BookmarkEntry)

    @Query("SELECT * FROM bookmarks WHERE isNovel = 1 AND novelTitle = :novelTitle LIMIT 1")
    suspend fun getNovelBookmark(novelTitle: String): BookmarkEntry?

    @Query("SELECT * FROM bookmarks WHERE isNovel = 1")
    suspend fun getAllNovelBookmarks(): List<BookmarkEntry>

    @Query("SELECT * FROM bookmarks WHERE isNovel = 1 AND domain = :host AND (novelTitle = :novelTitle OR url = :url) LIMIT 1")
    suspend fun getNovelBookmarkByHostAndTitle(host: String, novelTitle: String, url: String): BookmarkEntry?

    @Query("SELECT * FROM bookmarks WHERE isNovel = 1 AND domain = :host AND (url LIKE '%' || :urlPrefix || '%' OR novelTitle LIKE :titlePrefix || '%' OR url = :url) LIMIT 1")
    suspend fun getNovelBookmarkByHost(host: String, urlPrefix: String, titlePrefix: String, url: String): BookmarkEntry?

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Long)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteBookmarkByUrl(url: String)

    @Query("DELETE FROM bookmarks")
    suspend fun clearBookmarks()

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url LIMIT 1)")
    fun isBookmarked(url: String): Flow<Boolean>

    // Tabs
    @Query("SELECT * FROM tabs ORDER BY timestamp ASC")
    fun getAllTabsFlow(): Flow<List<TabEntry>>

    @Query("SELECT * FROM tabs ORDER BY timestamp ASC")
    suspend fun getAllTabs(): List<TabEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTab(tab: TabEntry): Long

    @Update
    suspend fun updateTab(tab: TabEntry)

    @Query("DELETE FROM tabs WHERE id = :id")
    suspend fun deleteTab(id: Long)

    @Query("DELETE FROM tabs")
    suspend fun clearTabs()
}
