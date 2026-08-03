package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookmarks",
    indices = [
        Index(value = ["url"], name = "idx_bookmark_url"),
        Index(value = ["domain"], name = "idx_bookmark_domain"),
        Index(value = ["isNovel"], name = "idx_bookmark_isnovel")
    ]
)
data class BookmarkEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    
    // Novel bookmark properties (automatic and expandable)
    val isNovel: Boolean = false,
    val novelTitle: String? = null,
    val chapterTitle: String? = null,
    val imageUrl: String? = null,
    val domain: String? = null,
    val lastViewedChapterUrl: String? = null,
    val lastViewedChapterTitle: String? = null
)
