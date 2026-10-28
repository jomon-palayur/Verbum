package com.verbum.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.verbum.app.data.model.VerseAnnotationEntity
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BundledSeedDatabaseTest {
    @Test fun packagedSeedOpensWithRoomAndContainsTheDefaultModernCatholicEdition() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "verbum-seed-${UUID.randomUUID()}.db"
        val database = Room.databaseBuilder(context, VerbumDatabase::class.java, databaseName)
            .createFromAsset("database/verbum_seed.db")
            .addMigrations(*VerbumDatabase.MIGRATIONS)
            .build()
        try {
            val books = database.bookDao().observeCanonicalBooks().first()
            val translations = database.translationDao().observeAll().first()
            val genesisVerse = database.verseDao().getRange("eng_dra", 1, 1, 1, 1)
            val modernGenesis = database.verseDao().getRange("eng_webc", 1, 1, 1, 1)
            val modernTobit = database.verseDao().getRange("eng_webc", 17, 1, 1, 1)
            val search = database.verseDao().search("eng_webc", "In the beginning", 10)
            val profiles = database.liturgicalProfileDao().observeAll().first()
            val tob = database.bookDao().getById(17)
            assertEquals(73, books.size)
            assertEquals("Tobit", tob?.name)
            assertTrue(translations.any { it.id == "eng_dra" && it.isBundled })
            assertTrue(translations.any { it.id == "eng_webc" && it.isBundled })
            assertTrue(translations.any { it.id == "lat_vuc" && it.languageCode == "la" })
            assertEquals(1, genesisVerse.size)
            assertFalse(genesisVerse.single().text.isBlank())
            assertFalse(modernGenesis.single().text.isBlank())
            assertFalse(modernTobit.single().text.isBlank())
            assertTrue(search.isNotEmpty())
            assertEquals("roman", profiles.single().id)
            assertEquals(73, translations.first { it.id == "eng_webc" }.canonBookCount)
            val annotationId = database.verseAnnotationDao().upsert(
                VerseAnnotationEntity(bookId = 1, chapter = 1, verseStart = 1, verseEnd = 1, translationId = "eng_webc", kind = "BOOKMARK"),
            )
            assertEquals("BOOKMARK", database.verseAnnotationDao().getById(annotationId)?.kind)
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }
}
