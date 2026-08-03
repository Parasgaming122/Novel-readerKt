package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tabs")
data class TabEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val isCurrent: Boolean = false,
    val isDesktopMode: Boolean = false,
    val groupId: Long? = null,
    val timestamp: Long = System.currentTimeMillis()
)
