package com.verbum.app.data.model

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "verses",
    primaryKeys = ["translationId", "bookId", "chapter", "verse"],
    indices = [Index(value = ["translationId", "bookId", "chapter"])],
)
data class VerseEntity(
    val translationId: String,
    val bookId: Int,
    val chapter: Int,
    val verse: Int,
    val text: String,
)
