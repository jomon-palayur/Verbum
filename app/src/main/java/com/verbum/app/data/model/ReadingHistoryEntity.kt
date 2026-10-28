package com.verbum.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_history")
data class ReadingHistoryEntity(
    @PrimaryKey val bookId: Int,
    val chapter: Int,
    val translationId: String,
    val updatedAt: Long = System.currentTimeMillis(),
)
