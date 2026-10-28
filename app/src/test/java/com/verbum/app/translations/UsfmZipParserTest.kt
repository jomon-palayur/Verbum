package com.verbum.app.translations

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsfmZipParserTest {
    @Test fun parserMapsDeuterocanonicalIdentifiersIntoCanonicalPositions() {
        val archive = zipOf(
            "41-TOBeng.usfm" to "\\id TOB\n\\c 1\n\\p\n\\v 1 Tobias of the tribe.\n\\v 2 Another verse.\n",
            "02-GENeng.usfm" to "\\id GEN\n\\c 1\n\\p\n\\v 1 \\w In|strong=\"H0430\"\\w* the beginning.\n",
        )
        val rows = UsfmZipParser.parse(ByteArrayInputStream(archive), "fixture")
        assertEquals(3, rows.size)
        assertEquals(17, rows.first().bookId)
        assertEquals(1, rows.first().chapter)
        assertEquals(1, rows.first().verse)
        assertEquals(1, rows.last().bookId)
        assertEquals("In the beginning.", rows.last().text)
    }

    @Test fun normalizationRecognizesCatholicBookAliases() {
        assertEquals(28, CanonicalBookIds.fromIdentifier("Ecclesiasticus"))
        assertEquals(20, CanonicalBookIds.fromIdentifier("1 Maccabees"))
        assertEquals(34, CanonicalBookIds.fromIdentifier("DAN"))
        assertTrue(CanonicalBookIds.fromIdentifier("unknown book") == null)
    }

    @Test fun jsonImporterAcceptsNabreNestedBookChapterVerseShape() {
        val source = """[{"book":"Genesis","chapters":[{"chapter":1,"verses":[{"verse":1,"text":"In the beginning."},{"verse":2,"text":"The earth was formless."}]}]}]"""
        val file = File.createTempFile("nabre-fixture", ".json")
        try {
            file.writeText(source)
            val rows = JsonSqliteImporter.parseJson(file, "nabre_fixture")
            assertEquals(2, rows.size)
            assertEquals(1, rows.first().bookId)
            assertEquals(1, rows.first().chapter)
            assertEquals(2, rows.last().verse)
        } finally {
            file.delete()
        }
    }

    private fun zipOf(vararg files: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            files.forEach { (name, body) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
