package com.verbum.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "curated_verses")
data class CuratedVerseEntity(
    @PrimaryKey val id: Int,
    val bookId: Int,
    val chapter: Int,
    val verseStart: Int,
    val verseEnd: Int,
    val charLengthEstimate: Int,
    val stateTags: String,
    val tone: String,
    val season: String,
    val contextSummary: String,
    val patristicQuote: String,
    val patristicAuthor: String,
    val practicalTakeaway: String,
)
