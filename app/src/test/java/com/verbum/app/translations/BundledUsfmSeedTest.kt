package com.verbum.app.translations

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledUsfmSeedTest {
    @Test fun publicDomainSourceNormalizesToAllSeventyThreeBooks() {
        val source = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .map { it.resolve("tools/seed_sources/engDRA_usfm.zip") }
            .first { Files.isRegularFile(it) }
        val verses = Files.newInputStream(source).use { UsfmZipParser.parse(it, "eng_dra") }
        assertEquals(73, verses.map { it.bookId }.toSet().size)
        assertEquals(35_811, verses.size)
        assertTrue(verses.any { it.bookId == 17 && it.chapter == 1 && it.verse == 1 })
        assertTrue(verses.any {
            it.bookId == 50 && it.chapter == 3 && it.verse == 16 && it.text.contains("God so loved the world")
        })
    }

    @Test fun modernCatholicEnglishAndLatinSourcesCoverTheFullCanon() {
        val root = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .first { Files.isDirectory(it.resolve("tools/seed_sources")) }
        listOf(
            "eng-web-c_usfm.zip" to "eng_webc",
            "latVUC_usfm.zip" to "lat_vuc",
        ).forEach { (archive, translationId) ->
            val verses = Files.newInputStream(root.resolve("tools/seed_sources/$archive"))
                .use { UsfmZipParser.parse(it, translationId) }
            assertEquals("$translationId must map all books", (1..73).toSet(), verses.map { it.bookId }.toSet())
            assertTrue("$translationId must include Tobit", verses.any { it.bookId == 17 })
            assertTrue("$translationId must include John 3:16", verses.any { it.bookId == 50 && it.chapter == 3 && it.verse == 16 })
        }
    }
}
