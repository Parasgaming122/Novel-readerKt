package com.paras.novelreaderkt

import android.util.JsonReader
import android.util.JsonToken
import com.paras.novelreaderkt.data.BookmarkEntry
import com.paras.novelreaderkt.data.HistoryEntry
import com.paras.novelreaderkt.data.TabEntry
import java.io.InputStream
import java.io.InputStreamReader

object StreamingJsonParser {

    class BackupData(
        val version: Int,
        val timestamp: Long,
        val settings: Map<String, Any>,
        val history: List<HistoryEntry>,
        val bookmarks: List<BookmarkEntry>,
        val tabs: List<TabEntry>
    )

    fun parseBackupStream(inputStream: InputStream): BackupData {
        var version = 2
        var timestamp = System.currentTimeMillis()
        val settings = mutableMapOf<String, Any>()
        val history = mutableListOf<HistoryEntry>()
        val bookmarks = mutableListOf<BookmarkEntry>()
        val tabs = mutableListOf<TabEntry>()

        val reader = JsonReader(InputStreamReader(inputStream, "UTF-8"))
        try {
            reader.beginObject()
            while (reader.hasNext()) {
                val sectionName = reader.nextName()
                when (sectionName) {
                    "version" -> {
                        version = reader.nextInt()
                    }
                    "timestamp" -> {
                        timestamp = reader.nextLong()
                    }
                    "settings" -> {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val key = reader.nextName()
                            val token = reader.peek()
                            val value: Any = when (token) {
                                JsonToken.BOOLEAN -> reader.nextBoolean()
                                JsonToken.NUMBER -> {
                                    val doubleVal = reader.nextDouble()
                                    if (doubleVal == doubleVal.toInt().toDouble()) doubleVal.toInt() else doubleVal
                                }
                                JsonToken.STRING -> reader.nextString()
                                else -> {
                                    reader.skipValue()
                                    ""
                                }
                            }
                            settings[key] = value
                        }
                        reader.endObject()
                    }
                    "history" -> {
                        reader.beginArray()
                        var count = 0
                        while (reader.hasNext()) {
                            if (count >= 1000) {
                                throw Exception("Backup history exceeds security limit of 1000 items")
                            }
                            history.add(parseHistoryEntry(reader))
                            count++
                        }
                        reader.endArray()
                    }
                    "bookmarks" -> {
                        reader.beginArray()
                        var count = 0
                        while (reader.hasNext()) {
                            if (count >= 1000) {
                                throw Exception("Backup bookmarks exceed security limit of 1000 items")
                            }
                            bookmarks.add(parseBookmarkEntry(reader))
                            count++
                        }
                        reader.endArray()
                    }
                    "tabs" -> {
                        reader.beginArray()
                        var count = 0
                        while (reader.hasNext()) {
                            if (count >= 100) {
                                throw Exception("Backup tabs exceed security limit of 100 items")
                            }
                            tabs.add(parseTabEntry(reader))
                            count++
                        }
                        reader.endArray()
                    }
                    else -> {
                        reader.skipValue()
                    }
                }
            }
            reader.endObject()
        } catch (e: Exception) {
            throw Exception("Failed to parse backup JSON: ${e.message}", e)
        } finally {
            try {
                reader.close()
            } catch (ignored: Exception) {}
        }

        return BackupData(version, timestamp, settings, history, bookmarks, tabs)
    }

    private fun parseHistoryEntry(reader: JsonReader): HistoryEntry {
        var url = ""
        var title = ""
        var timestamp = System.currentTimeMillis()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "url" -> url = reader.nextString().take(2048)
                "title" -> title = reader.nextString().take(512)
                "timestamp" -> timestamp = reader.nextLong()
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return HistoryEntry(url = url, title = title, timestamp = timestamp)
    }

    private fun parseBookmarkEntry(reader: JsonReader): BookmarkEntry {
        var url = ""
        var title = ""
        var timestamp = System.currentTimeMillis()
        var isNovel = false
        var novelTitle: String? = null
        var chapterTitle: String? = null
        var imageUrl: String? = null
        var domain: String? = null
        var lastViewedChapterUrl: String? = null
        var lastViewedChapterTitle: String? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "url" -> url = reader.nextString().take(2048)
                "title" -> title = reader.nextString().take(512)
                "timestamp" -> timestamp = reader.nextLong()
                "isNovel" -> isNovel = reader.nextBoolean()
                "novelTitle" -> {
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull()
                    } else {
                        novelTitle = reader.nextString()
                    }
                }
                "chapterTitle" -> {
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull()
                    } else {
                        chapterTitle = reader.nextString()
                    }
                }
                "imageUrl" -> {
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull()
                    } else {
                        imageUrl = reader.nextString()
                    }
                }
                "domain" -> {
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull()
                    } else {
                        domain = reader.nextString()
                    }
                }
                "lastViewedChapterUrl" -> {
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull()
                    } else {
                        lastViewedChapterUrl = reader.nextString()
                    }
                }
                "lastViewedChapterTitle" -> {
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull()
                    } else {
                        lastViewedChapterTitle = reader.nextString()
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return BookmarkEntry(
            url = url,
            title = title,
            timestamp = timestamp,
            isNovel = isNovel,
            novelTitle = novelTitle,
            chapterTitle = chapterTitle,
            imageUrl = imageUrl,
            domain = domain,
            lastViewedChapterUrl = lastViewedChapterUrl,
            lastViewedChapterTitle = lastViewedChapterTitle
        )
    }

    private fun parseTabEntry(reader: JsonReader): TabEntry {
        var url = ""
        var title = ""
        var isCurrent = false
        var isDesktopMode = false
        var groupId: Long? = null
        var timestamp = System.currentTimeMillis()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "url" -> url = reader.nextString().take(2048)
                "title" -> title = reader.nextString().take(512)
                "isCurrent" -> isCurrent = reader.nextBoolean()
                "isDesktopMode" -> isDesktopMode = reader.nextBoolean()
                "groupId" -> {
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull()
                    } else {
                        groupId = reader.nextLong()
                    }
                }
                "timestamp" -> timestamp = reader.nextLong()
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return TabEntry(
            url = url,
            title = title,
            isCurrent = isCurrent,
            isDesktopMode = isDesktopMode,
            groupId = groupId,
            timestamp = timestamp
        )
    }
}
