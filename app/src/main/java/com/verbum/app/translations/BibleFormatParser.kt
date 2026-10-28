package com.verbum.app.translations

import android.database.sqlite.SQLiteDatabase
import android.util.Xml
import com.verbum.app.data.model.VerseEntity
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser

enum class TranslationFormat { USFM_ZIP, ZEFANIA_XML, JSON, SQLITE, OSIS_ZIP, PSFM }

object CanonicalBookIds {
    private val codes = mapOf(
        "GEN" to 1, "EXO" to 2, "LEV" to 3, "NUM" to 4, "DEU" to 5, "JOS" to 6, "JDG" to 7,
        "RUT" to 8, "1SA" to 9, "2SA" to 10, "1KI" to 11, "2KI" to 12, "1CH" to 13, "2CH" to 14,
        "EZR" to 15, "NEH" to 16, "TOB" to 17, "JDT" to 18, "EST" to 19, "ESG" to 19, "1MA" to 20, "2MA" to 21,
        "JOB" to 22, "PSA" to 23, "PRO" to 24, "ECC" to 25, "SNG" to 26, "WIS" to 27, "SIR" to 28,
        "ISA" to 29, "JER" to 30, "LAM" to 31, "BAR" to 32, "EZK" to 33, "DAN" to 34, "DAG" to 34, "HOS" to 35,
        "JOL" to 36, "AMO" to 37, "OBA" to 38, "JON" to 39, "MIC" to 40, "NAM" to 41, "HAB" to 42,
        "ZEP" to 43, "HAG" to 44, "ZEC" to 45, "MAL" to 46, "MAT" to 47, "MRK" to 48, "LUK" to 49,
        "JHN" to 50, "ACT" to 51, "ROM" to 52, "1CO" to 53, "2CO" to 54, "GAL" to 55, "EPH" to 56,
        "PHP" to 57, "COL" to 58, "1TH" to 59, "2TH" to 60, "1TI" to 61, "2TI" to 62, "TIT" to 63,
        "PHM" to 64, "HEB" to 65, "JAS" to 66, "1PE" to 67, "2PE" to 68, "1JN" to 69, "2JN" to 70,
        "3JN" to 71, "JUD" to 72, "REV" to 73,
    )
    private val names = listOf(
        "Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy", "Joshua", "Judges", "Ruth", "1 Samuel", "2 Samuel",
        "1 Kings", "2 Kings", "1 Chronicles", "2 Chronicles", "Ezra", "Nehemiah", "Tobit", "Judith", "Esther", "1 Maccabees",
        "2 Maccabees", "Job", "Psalms", "Proverbs", "Ecclesiastes", "Song of Solomon", "Wisdom", "Sirach", "Isaiah", "Jeremiah",
        "Lamentations", "Baruch", "Ezekiel", "Daniel", "Hosea", "Joel", "Amos", "Obadiah", "Jonah", "Micah", "Nahum", "Habakkuk",
        "Zephaniah", "Haggai", "Zechariah", "Malachi", "Matthew", "Mark", "Luke", "John", "Acts", "Romans", "1 Corinthians",
        "2 Corinthians", "Galatians", "Ephesians", "Philippians", "Colossians", "1 Thessalonians", "2 Thessalonians", "1 Timothy",
        "2 Timothy", "Titus", "Philemon", "Hebrews", "James", "1 Peter", "2 Peter", "1 John", "2 John", "3 John", "Jude", "Revelation",
    )
    private val nameMap = buildMap {
        names.forEachIndexed { index, name -> put(normalize(name), index + 1) }
        put("psalm", 23); put("ps", 23); put("songofsongs", 26); put("canticles", 26)
        put("ecclesiasticus", 28); put("ben-sira", 28); put("ecclesiastes", 25)
        put("firstsamuel", 9); put("secondsamuel", 10); put("firstkings", 11); put("secondkings", 12)
        put("firstchronicles", 13); put("secondchronicles", 14); put("firstmaccabees", 20); put("secondmaccabees", 21)
        put("firstcorinthians", 53); put("secondcorinthians", 54); put("firstthessalonians", 59); put("secondthessalonians", 60)
        put("firsttimothy", 61); put("secondtimothy", 62); put("firstpeter", 67); put("secondpeter", 68)
        put("firstjohn", 69); put("secondjohn", 70); put("thirdjohn", 71)
    }

    fun fromUsfm(code: String): Int? = codes[code.trim().uppercase(Locale.ROOT)]
    fun fromIdentifier(value: String?, numericFallback: Int? = null): Int? {
        val text = value?.trim().orEmpty()
        fromUsfm(text)?.let { return it }
        text.toIntOrNull()?.let { if (it in 1..73) return it }
        nameMap[normalize(text)]?.let { return it }
        if (numericFallback in 1..73) return numericFallback
        return null
    }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)
}

object UsfmZipParser {
    private val marker = Regex("\\\\(c|v)\\s+(\\d+)\\s*")
    private val wordMarkup = Regex("\\\\w\\s+([^|]+)\\|[^\\\\]*\\\\w\\*")
    private val otherMarkup = Regex("\\\\[+a-zA-Z0-9]+\\*?")

    fun parse(input: InputStream, translationId: String): List<VerseEntity> {
        val verses = ArrayList<VerseEntity>(36_000)
        val seenBooks = HashSet<Int>()
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.endsWith(".usfm", ignoreCase = true)) {
                    val source = zip.readBytes().toString(Charsets.UTF_8).removePrefix("\uFEFF")
                    val id = Regex("(?m)^\\\\id\\s+(\\S+)").find(source)?.groupValues?.get(1)
                        ?: throw IllegalArgumentException("USFM file is missing its book identifier: ${entry.name}")
                    val bookId = CanonicalBookIds.fromUsfm(id)
                        ?: throw IllegalArgumentException("Unknown USFM book identifier '$id' in ${entry.name}")
                    require(seenBooks.add(bookId)) { "Duplicate USFM book $id" }
                    val markers = marker.findAll(source).toList()
                    var chapter = 0
                    markers.forEachIndexed { index, item ->
                        val type = item.groupValues[1]
                        val number = item.groupValues[2].toInt()
                        val end = markers.getOrNull(index + 1)?.range?.first ?: source.length
                        if (type == "c") {
                            chapter = number
                        } else {
                            require(chapter > 0) { "USFM verse appears before a chapter in ${entry.name}" }
                            val raw = source.substring(item.range.last + 1, end)
                            val plain = raw.replace(wordMarkup) { it.groupValues[1] }
                                .replace(otherMarkup, " ")
                                .replace(Regex("\\s+"), " ")
                                .trim()
                            if (plain.isNotEmpty()) verses += VerseEntity(translationId, bookId, chapter, number, plain)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        return verses
    }
}

object ZefaniaXmlParser {
    fun parse(input: InputStream, translationId: String): List<VerseEntity> {
        val parser = Xml.newPullParser()
        parser.setInput(input, "UTF-8")
        val result = ArrayList<VerseEntity>()
        var bookId: Int? = null
        var chapter = 0
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name.uppercase(Locale.ROOT)) {
                    "BIBLEBOOK" -> {
                        val sourceNumber = parser.getAttributeValue(null, "bnumber")?.toIntOrNull()
                        val sourceName = parser.getAttributeValue(null, "bname") ?: parser.getAttributeValue(null, "bsname")
                        bookId = CanonicalBookIds.fromIdentifier(sourceName, sourceNumber)
                            ?: throw IllegalArgumentException("Unknown Zefania book '$sourceName'")
                    }
                    "CHAPTER" -> chapter = parser.getAttributeValue(null, "cnumber")?.toIntOrNull() ?: 0
                    "VERS" -> {
                        val number = parser.getAttributeValue(null, "vnumber")?.toIntOrNull()
                            ?: throw IllegalArgumentException("Zefania verse has no numeric vnumber")
                        val text = parser.nextText().trim()
                        val selectedBook = bookId ?: throw IllegalArgumentException("Zefania verse is outside a BIBLEBOOK")
                        require(chapter > 0) { "Zefania verse is outside a CHAPTER" }
                        if (text.isNotEmpty()) result += VerseEntity(translationId, selectedBook, chapter, number, text)
                    }
                }
            }
            event = parser.next()
        }
        return result
    }
}

object JsonSqliteImporter {
    fun parseJson(file: File, translationId: String): List<VerseEntity> {
        val rootText = file.readText(Charsets.UTF_8)
        val result = ArrayList<VerseEntity>()
        val root = runCatching { JSONObject(rootText) }.getOrNull()
        if (root != null) {
            val flat = root.optJSONArray("verses") ?: root.optJSONArray("data")
            if (flat != null && flat.length() > 0 && flat.opt(0) is JSONObject && (flat.optJSONObject(0)?.has("chapter") == true || flat.optJSONObject(0)?.has("chapter_id") == true)) {
                for (i in 0 until flat.length()) flat.optJSONObject(i)?.let { item ->
                    addFlatJsonVerse(result, item, translationId)
                }
                return result
            }
            val books = root.optJSONArray("books") ?: root.optJSONArray("bible") ?: JSONArray()
            for (bookIndex in 0 until books.length()) {
                books.optJSONObject(bookIndex)?.let { parseNestedJsonBook(result, it, translationId, bookIndex) }
            }
            if (result.isNotEmpty()) return result
        }
        val array = runCatching { JSONArray(rootText) }.getOrNull()
            ?: throw IllegalArgumentException("JSON Bible must contain books and chapters or a flat verse list")
        for (i in 0 until array.length()) {
            array.optJSONObject(i)?.let { book ->
                if (book.has("chapters")) parseNestedJsonBook(result, book, translationId, i)
                else addFlatJsonVerse(result, book, translationId)
            }
        }
        require(result.isNotEmpty()) { "JSON Bible contains no supported verse records" }
        return result
    }

    private fun parseNestedJsonBook(result: MutableList<VerseEntity>, book: JSONObject, translationId: String, bookIndex: Int) {
        val name = jsonString(book, "usfm", "code", "book_id", "id", "name", "book")
        val fallback = jsonInt(book, "bnumber", "number") ?: (bookIndex + 1).takeIf { name.isNullOrBlank() }
        val bookId = CanonicalBookIds.fromIdentifier(name, fallback)
            ?: throw IllegalArgumentException("Unknown JSON book '$name'")
        val chapters = book.optJSONArray("chapters") ?: return
        for (chapterIndex in 0 until chapters.length()) {
            val chapterValue = chapters.opt(chapterIndex)
            val chapter = if (chapterValue is JSONObject) {
                jsonInt(chapterValue, "chapter", "chapter_number", "number") ?: chapterIndex + 1
            } else chapterIndex + 1
            val verseArray = if (chapterValue is JSONObject) {
                chapterValue.optJSONArray("verses") ?: chapterValue.optJSONArray("verse")
            } else chapterValue as? JSONArray
            if (verseArray == null) continue
            for (verseIndex in 0 until verseArray.length()) {
                when (val verse = verseArray.opt(verseIndex)) {
                    is JSONObject -> {
                        val number = jsonInt(verse, "verse", "verse_number", "number", "verse_id") ?: verseIndex + 1
                        val text = jsonString(verse, "text", "content", "value").orEmpty()
                        if (text.isNotBlank()) result += VerseEntity(translationId, bookId, chapter, number, text)
                    }
                    is String -> if (verse.isNotBlank()) result += VerseEntity(translationId, bookId, chapter, verseIndex + 1, verse)
                }
            }
        }
    }

    fun parseSqlite(file: File, translationId: String): List<VerseEntity> {
        val database = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        database.use { source ->
            val tables = source.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
            val verseTable = listOf("verses", "bible_verses", "scripture").firstOrNull { it in tables }
                ?: throw IllegalArgumentException("SQLite Bible has no supported verses table")
            val bookTable = listOf("books", "bible_books").firstOrNull { it in tables }
            val bookNames = HashMap<String, Int>()
            if (bookTable != null) {
                source.rawQuery("SELECT * FROM `$bookTable`", null).use { cursor ->
                    val idColumn = findColumn(cursor, "book_id", "bnumber", "number", "id")
                    val nameColumn = findColumn(cursor, "usfm", "code", "name", "book_name", "title")
                    while (cursor.moveToNext()) {
                        val rawId = idColumn?.let(cursor::getString)
                        val name = nameColumn?.let(cursor::getString)
                        val id = CanonicalBookIds.fromIdentifier(name ?: rawId, rawId?.toIntOrNull()) ?: continue
                        if (name != null) bookNames[normalizeBookName(name)] = id
                        rawId?.let { bookNames[normalizeBookName(it)] = id }
                    }
                }
            }
            source.rawQuery("SELECT * FROM `$verseTable`", null).use { cursor ->
                val bookColumn = findColumn(cursor, "book_id", "book", "biblebook_id", "book_number")
                    ?: throw IllegalArgumentException("SQLite verses table has no book column")
                val chapterColumn = findColumn(cursor, "chapter", "chapter_id", "chapter_number")
                    ?: throw IllegalArgumentException("SQLite verses table has no chapter column")
                val verseColumn = findColumn(cursor, "verse", "verse_id", "verse_number")
                    ?: throw IllegalArgumentException("SQLite verses table has no verse column")
                val textColumn = findColumn(cursor, "text", "verse_text", "content", "value")
                    ?: throw IllegalArgumentException("SQLite verses table has no text column")
                val result = ArrayList<VerseEntity>()
                while (cursor.moveToNext()) {
                    val rawBook = cursor.getString(bookColumn)
                    val bookId = bookNames[normalizeBookName(rawBook)]
                        ?: CanonicalBookIds.fromIdentifier(rawBook, rawBook.toIntOrNull())
                        ?: throw IllegalArgumentException("Unknown SQLite Bible book '$rawBook'")
                    val text = cursor.getString(textColumn)?.trim().orEmpty()
                    if (text.isNotEmpty()) result += VerseEntity(
                        translationId,
                        bookId,
                        cursor.getInt(chapterColumn),
                        cursor.getInt(verseColumn),
                        text,
                    )
                }
                return result
            }
        }
    }

    private fun addFlatJsonVerse(result: MutableList<VerseEntity>, item: JSONObject, translationId: String) {
        val bookName = jsonString(item, "usfm", "book_id", "book", "book_name", "bookName", "name")
        val fallback = jsonInt(item, "bnumber", "book_number")
        val bookId = CanonicalBookIds.fromIdentifier(bookName, fallback)
            ?: throw IllegalArgumentException("Unknown flat JSON Bible book '$bookName'")
        val chapter = jsonInt(item, "chapter", "chapter_id", "chapter_number") ?: return
        val verse = jsonInt(item, "verse", "verse_id", "verse_number") ?: return
        val text = jsonString(item, "text", "verse_text", "content", "value").orEmpty()
        if (text.isNotBlank()) result += VerseEntity(translationId, bookId, chapter, verse, text)
    }

    private fun jsonString(value: JSONObject, vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        if (!value.has(key) || value.isNull(key)) null else value.opt(key)?.toString()?.takeIf(String::isNotBlank)
    }

    private fun jsonInt(value: JSONObject, vararg keys: String): Int? = keys.firstNotNullOfOrNull { key ->
        if (!value.has(key) || value.isNull(key)) null else value.opt(key)?.toString()?.toIntOrNull()
    }

    private fun findColumn(cursor: android.database.Cursor, vararg candidates: String): Int? {
        val columns = cursor.columnNames.mapIndexed { index, name -> name.lowercase(Locale.ROOT) to index }.toMap()
        return candidates.firstNotNullOfOrNull { columns[it.lowercase(Locale.ROOT)] }
    }

    private fun normalizeBookName(value: String): String = value.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)
}
