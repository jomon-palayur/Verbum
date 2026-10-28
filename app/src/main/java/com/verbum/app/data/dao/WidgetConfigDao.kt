package com.verbum.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.verbum.app.data.model.WidgetConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WidgetConfigDao {
    @Query("SELECT * FROM widget_configs WHERE appWidgetId = :appWidgetId")
    fun observe(appWidgetId: Int): Flow<WidgetConfigEntity?>

    @Query("SELECT * FROM widget_configs WHERE appWidgetId = :appWidgetId")
    suspend fun get(appWidgetId: Int): WidgetConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: WidgetConfigEntity)

    @Query("DELETE FROM widget_configs WHERE appWidgetId = :appWidgetId")
    suspend fun delete(appWidgetId: Int)

    @Query("UPDATE widget_configs SET translationId = :translationId")
    suspend fun updateAllTranslations(translationId: String)
}
