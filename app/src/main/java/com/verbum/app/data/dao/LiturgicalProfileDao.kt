package com.verbum.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.verbum.app.data.model.LiturgicalProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LiturgicalProfileDao {
    @Query("SELECT * FROM liturgical_profiles ORDER BY name")
    fun observeAll(): Flow<List<LiturgicalProfileEntity>>

    @Query("SELECT * FROM liturgical_profiles WHERE id = :id")
    suspend fun getById(id: String): LiturgicalProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(profiles: List<LiturgicalProfileEntity>)
}
