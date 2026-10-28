package com.verbum.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.verbum.app.data.model.VerseEntity
import com.verbum.app.data.model.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VerseDao {
    data class SearchHit(
        val translationId: String,
        val bookId: Int,
        val chapter: Int,
        val verse: Int,
        val text: String,
        val bookName: String,
        val shortName: String,
    )
    @Query("SELECT * FROM verses WHERE translationId = :translationId AND bookId = :bookId AND chapter = :chapter ORDER BY verse")
    fun observeChapter(translationId: String, bookId: Int, chapter: Int): Flow<List<VerseEntity>>

    @Query("SELECT DISTINCT chapter FROM verses WHERE translationId = :translationId AND bookId = :bookId ORDER BY chapter")
    suspend fun getChapterNumbers(translationId: String, bookId: Int): List<Int>

    @Query("SELECT * FROM verses WHERE translationId = :translationId AND bookId = :bookId AND chapter = :chapter ORDER BY verse")
    suspend fun getChapter(translationId: String, bookId: Int, chapter: Int): List<VerseEntity>

    @Query("SELECT MIN(chapter) FROM verses WHERE translationId = :translationId AND bookId = :bookId AND chapter > :chapter")
    suspend fun getNextChapter(translationId: String, bookId: Int, chapter: Int): Int?

    @Query("SELECT * FROM verses WHERE translationId = :translationId AND bookId = :bookId AND chapter = :chapter AND verse BETWEEN :startVerse AND :endVerse ORDER BY verse")
    suspend fun getRange(translationId: String, bookId: Int, chapter: Int, startVerse: Int, endVerse: Int): List<VerseEntity>

    @Query("""SELECT verses.translationId, verses.bookId, verses.chapter, verses.verse, verses.text,
        books.name AS bookName, books.shortName AS shortName
        FROM verses INNER JOIN books ON books.id = verses.bookId
        WHERE verses.translationId = :translationId AND verses.text LIKE '%' || :query || '%'
        ORDER BY books.canonicalOrder, verses.chapter, verses.verse LIMIT :limit""")
    suspend fun search(translationId: String, query: String, limit: Int = 100): List<SearchHit>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(verses: List<VerseEntity>)

    @Query("DELETE FROM verses WHERE translationId = :translationId")
    suspend fun deleteTranslation(translationId: String)
}
