package com.verbum.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "verse_annotations",
    indices = [Index(value = ["bookId", "chapter", "verseStart", "verseEnd"])],
)
data class VerseAnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Int,
    val chapter: Int,
    val verseStart: Int,
    val verseEnd: Int,
    val translationId: String,
    val kind: String,
    val colorArgb: Long = 0xFFFFD166,
    val note: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)
