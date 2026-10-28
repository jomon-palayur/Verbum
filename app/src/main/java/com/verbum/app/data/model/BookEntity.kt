package com.verbum.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val shortName: String,
    val testament: String,
    val canonicalOrder: Int,
)
