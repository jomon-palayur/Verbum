package com.verbum.app.data.dao

import androidx.room.Dao
import androidx.room.Query
import com.verbum.app.data.model.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY canonicalOrder")
    fun observeCanonicalBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: Int): BookEntity?
}
