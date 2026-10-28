package com.verbum.app.data.model

import androidx.room.Entity
import androidx.room.ColumnInfo

@Entity(tableName = "lectionary_readings", primaryKeys = ["profileId", "liturgicalKey"])
data class LectionaryReadingEntity(
    val liturgicalKey: String,
    val title: String,
    val color: String,
    val firstReadingRef: String,
    val psalmRef: String,
    val secondReadingRef: String?,
    @ColumnInfo(defaultValue = "''") val gospelAcclamationRef: String = "",
    val gospelRef: String,
    val gospelKeyVerseCoord: String,
    val profileId: String = "roman",
)
