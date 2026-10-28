package com.verbum.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.verbum.app.data.model.CuratedVerseEntity

@Dao
interface CuratedVerseDao {
    @Query("SELECT * FROM curated_verses WHERE id = :id")
    suspend fun getById(id: Int): CuratedVerseEntity?

    @Query("SELECT * FROM curated_verses WHERE (:maxLength IS NULL OR charLengthEstimate <= :maxLength) ORDER BY id")
    suspend fun getCandidates(maxLength: Int? = null): List<CuratedVerseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(verses: List<CuratedVerseEntity>)
}
