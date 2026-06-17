package com.paras.novelreaderkt.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Per-novel glossary entry for Gemini translation context. */
@Entity(
    tableName = "novel_glossary",
    indices = [
        Index(value = ["novelKey"], name = "idx_glossary_novel_key"),
        Index(value = ["novelKey", "category"], name = "idx_glossary_novel_category"),
        Index(value = ["sourceText"], name = "idx_glossary_source_text")
    ]
)
data class NovelGlossaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Composite key: domain:novelTitle */
    val novelKey: String = "",
    /** Term category: character, sect, system, technique, item, location, relationship, title, other */
    val category: String = "",
    /** Original Chinese term */
    val sourceText: String = "",
    /** Translated English term or Pinyin */
    val translatedText: String = "",
    /** Optional Pinyin romanization */
    val pinyin: String? = null,
    /** Optional user notes */
    val notes: String? = null,
    /** How many times this term has been encountered */
    val frequency: Int = 1,
    /** Last time this term was seen */
    val lastSeen: Long = 0L,
    /** Whether this term was auto-detected vs manually added */
    val isAutoDetected: Boolean = true
)