package com.verbum.app.data

import com.verbum.app.data.model.BookEntity
import com.verbum.app.data.model.CuratedVerseEntity
import com.verbum.app.data.model.LectionaryReadingEntity
import com.verbum.app.data.model.VerseEntity
import com.verbum.app.data.model.LiturgicalProfileEntity
import com.verbum.app.liturgy.LiturgicalCalendarEngine
import com.verbum.app.liturgy.LiturgicalDay
import java.time.LocalDate
import kotlinx.coroutines.flow.first

data class ScripturePassage(
    val label: String,
    val bookId: Int,
    val chapter: Int,
    val verseStart: Int,
    val verseEnd: Int,
    val citation: String,
    val text: String,
)

data class DailyVerseContent(
    val curated: CuratedVerseEntity,
    val book: BookEntity,
    val citation: String,
    val passage: List<VerseEntity>,
)

data class DailyLectionaryContent(
    val day: LiturgicalDay,
    val readings: LectionaryReadingEntity?,
    val passages: List<ScripturePassage>,
)

class ScriptureRepository(private val database: VerbumDatabase) {
    suspend fun getDailyVerse(
        date: LocalDate,
        translationId: String,
        need: String? = null,
        curatedId: Int? = null,
    ): DailyVerseContent? {
        val allCandidates = database.curatedVerseDao().getCandidates(DAILY_VERSE_MAX_ESTIMATED_LENGTH)
            .ifEmpty { database.curatedVerseDao().getCandidates() }
        val preferredCandidates = allCandidates
            .filter { need == null || need in it.stateTags.split(',').map(String::trim) }
            .ifEmpty { allCandidates }
        val candidatesAvailableInTranslation = preferredCandidates.filter { candidate ->
            val centralRange = database.verseDao().getRange(
                translationId, candidate.bookId, candidate.chapter, candidate.verseStart, candidate.verseEnd,
            )
            centralRange.size == candidate.verseEnd - candidate.verseStart + 1
        }
        if (candidatesAvailableInTranslation.isEmpty()) return null
        val verse = curatedId?.let { id -> candidatesAvailableInTranslation.firstOrNull { it.id == id } }
            ?: candidatesAvailableInTranslation[Math.floorMod(date.toEpochDay().toInt(), candidatesAvailableInTranslation.size)]
        val book = database.bookDao().getById(verse.bookId) ?: return null
        val contextStart = (verse.verseStart - 3).coerceAtLeast(1)
        val contextEnd = verse.verseEnd + 3
        val passage = database.verseDao().getRange(translationId, verse.bookId, verse.chapter, contextStart, contextEnd)
        return DailyVerseContent(verse, book, formatReference(book, verse.chapter, verse.verseStart, verse.verseEnd), passage)
    }

    suspend fun getLectionary(date: LocalDate, translationId: String): DailyLectionaryContent {
        return getLectionary(date, translationId, "roman")
    }

    suspend fun getLectionary(date: LocalDate, translationId: String, profileId: String): DailyLectionaryContent {
        val day = LiturgicalCalendarEngine.calculate(date)
        val readings = database.lectionaryReadingDao().getByKey(profileId, "DATE_${date}")
            ?: if (profileId == "roman") database.lectionaryReadingDao().getByKey(profileId, day.key) else null
        if (readings == null) return DailyLectionaryContent(day, null, emptyList())
        val refs = listOfNotNull(
            "First Reading" to readings.firstReadingRef,
            "Responsorial Psalm" to readings.psalmRef,
            readings.secondReadingRef?.let { "Second Reading" to it },
            readings.gospelAcclamationRef.takeIf(String::isNotBlank)?.let { "Gospel Acclamation" to it },
            "Gospel" to readings.gospelRef,
        )
        val passages = refs.mapNotNull { (label, value) ->
            val reference = ScriptureCoordinate.parse(value) ?: return@mapNotNull null
            val book = database.bookDao().getById(reference.bookId) ?: return@mapNotNull null
            val verses = database.verseDao().getRange(
                translationId,
                reference.bookId,
                reference.chapter,
                reference.startVerse,
                reference.endVerse,
            )
            ScripturePassage(
                label = label,
                bookId = reference.bookId,
                chapter = reference.chapter,
                verseStart = reference.startVerse,
                verseEnd = reference.endVerse,
                citation = formatReference(book, reference.chapter, reference.startVerse, reference.endVerse),
                text = verses.joinToString(" ") { it.text },
            )
        }
        return DailyLectionaryContent(day.copy(title = readings.title, color = runCatching {
            com.verbum.app.liturgy.LiturgicalColor.valueOf(readings.color)
        }.getOrDefault(day.color)), readings, passages)
    }

    fun observeLiturgicalProfiles() = database.liturgicalProfileDao().observeAll()

    suspend fun defaultTranslationId(): String {
        val translations = database.translationDao().observeAll().first()
        return translations.firstOrNull { it.id == "eng_webc" }?.id
            ?: translations.firstOrNull()?.id.orEmpty()
    }

    companion object {
        private const val DAILY_VERSE_MAX_ESTIMATED_LENGTH = 120

        fun formatReference(book: BookEntity, chapter: Int, startVerse: Int, endVerse: Int): String = buildString {
            append(book.shortName)
            append(' ')
            append(chapter)
            append(':')
            append(startVerse)
            if (endVerse > startVerse) append("–$endVerse")
        }
    }
}

data class ScriptureCoordinate(val bookId: Int, val chapter: Int, val startVerse: Int, val endVerse: Int) {
    companion object {
        private val pattern = Regex("^(\\d+):(\\d+):(\\d+)(?:-(\\d+))?$")

        fun parse(value: String): ScriptureCoordinate? {
            val match = pattern.matchEntire(value.trim()) ?: return null
            val (bookId, chapter, startVerse, endVerse) = match.destructured
            val book = bookId.toIntOrNull() ?: return null
            val chapterNumber = chapter.toIntOrNull() ?: return null
            val start = startVerse.toIntOrNull() ?: return null
            val end = endVerse.toIntOrNull() ?: start
            if (book !in 1..73 || chapterNumber <= 0 || start <= 0 || end < start) return null
            return ScriptureCoordinate(book, chapterNumber, start, end)
        }
    }
}
