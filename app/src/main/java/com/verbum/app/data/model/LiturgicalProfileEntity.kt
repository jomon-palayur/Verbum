package com.verbum.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A calendar/lectionary profile supplied by a rights-cleared liturgical data pack. */
@Entity(tableName = "liturgical_profiles", indices = [Index(value = ["tradition", "regionCode"])])
data class LiturgicalProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val tradition: String,
    val regionCode: String,
    val sourceName: String,
    val sourceUrl: String,
    val license: String,
    val isBundled: Boolean = false,
)
