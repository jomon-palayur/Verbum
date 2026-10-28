package com.verbum.app.data.model

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(tableName = "widget_configs")
data class WidgetConfigEntity(
    @PrimaryKey val appWidgetId: Int,
    val widgetType: String,
    val translationId: String,
    val bgOpacityPercent: Int = 35,
    val bgTintMode: String = "DARK",
    val textColorMode: String = "WHITE",
    val useDropShadow: Boolean = true,
    val fontFamily: String = "SERIF",
    val refreshHours: Int = 24,
    val activeCuratedVerseId: Int? = null,
    @ColumnInfo(defaultValue = "'roman'") val liturgicalProfileId: String = "roman",
)
