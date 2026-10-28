package com.verbum.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.verbum.app.data.model.LectionaryReadingEntity

@Dao
interface LectionaryReadingDao {
    @Query("SELECT * FROM lectionary_readings WHERE profileId = :profileId AND liturgicalKey = :key")
    suspend fun getByKey(profileId: String, key: String): LectionaryReadingEntity?

    @Query("SELECT * FROM lectionary_readings ORDER BY title")
    suspend fun getAll(): List<LectionaryReadingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(readings: List<LectionaryReadingEntity>)
}
