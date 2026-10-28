package com.verbum.app.data.model

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(tableName = "translations")
data class TranslationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val shortName: String,
    val languageCode: String,
    val isBundled: Boolean = false,
    val installedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val canonBookCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val catholicApproved: Boolean = false,
    @ColumnInfo(defaultValue = "''") val attribution: String = "",
    @ColumnInfo(defaultValue = "''") val approvalAuthority: String = "",
)
