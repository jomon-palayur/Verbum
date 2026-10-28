package com.verbum.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.verbum.app.data.model.TranslationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TranslationDao {
    @Query("SELECT * FROM translations ORDER BY name")
    fun observeAll(): Flow<List<TranslationEntity>>

    @Query("SELECT * FROM translations WHERE id = :id")
    suspend fun getById(id: String): TranslationEntity?

    @Query("SELECT id FROM translations ORDER BY name")
    suspend fun getInstalledIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(translation: TranslationEntity)

    @Query("DELETE FROM translations WHERE id = :id AND isBundled = 0")
    suspend fun deleteInstalled(id: String): Int
}
