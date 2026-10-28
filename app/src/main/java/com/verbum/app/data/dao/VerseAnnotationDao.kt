package com.verbum.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.verbum.app.data.model.VerseAnnotationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VerseAnnotationDao {
    @Query("SELECT * FROM verse_annotations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<VerseAnnotationEntity>>

    @Query("SELECT * FROM verse_annotations WHERE bookId = :bookId AND chapter = :chapter ORDER BY verseStart, verseEnd")
    fun observeChapter(bookId: Int, chapter: Int): Flow<List<VerseAnnotationEntity>>

    @Query("SELECT * FROM verse_annotations WHERE kind = 'BOOKMARK' ORDER BY updatedAt DESC")
    fun observeBookmarks(): Flow<List<VerseAnnotationEntity>>

    @Query("SELECT * FROM verse_annotations WHERE id = :id")
    suspend fun getById(id: Long): VerseAnnotationEntity?

    @Query("SELECT * FROM verse_annotations WHERE bookId = :bookId AND chapter = :chapter AND verseStart = :verse AND verseEnd = :verse AND kind = :kind LIMIT 1")
    suspend fun findVerseAnnotation(bookId: Int, chapter: Int, verse: Int, kind: String): VerseAnnotationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(annotation: VerseAnnotationEntity): Long

    @Query("DELETE FROM verse_annotations WHERE id = :id")
    suspend fun delete(id: Long)
}
