package com.verbum.app.widget

import android.content.Context
import com.verbum.app.R
import com.verbum.app.data.VerbumDatabase
import com.verbum.app.data.model.WidgetConfigEntity
import java.time.LocalDate

data class WidgetContent(
    val title: String,
    val primary: String,
    val secondary: String,
    val compactPrimary: String = primary,
) {
    companion object {
        suspend fun load(context: Context, database: VerbumDatabase, config: WidgetConfigEntity, type: WidgetType): WidgetContent =
            when (type) {
                WidgetType.DAILY_VERSE -> dailyVerse(context, database, config)
                WidgetType.LECTIONARY -> lectionary(context, database, config)
                WidgetType.SHORT_VERSE -> dailyVerse(context, database, config).let { content ->
                    content.copy(primary = compactExcerpt(content.primary), compactPrimary = compactExcerpt(content.primary))
                }
            }

        private fun compactExcerpt(text: String): String {
            val firstTwoSentences = text.trim().split(Regex("(?<=[.!?।؟])\\s+")).take(2).joinToString(" ")
            if (firstTwoSentences.length <= 140) return firstTwoSentences
            val firstSentence = firstTwoSentences.split(Regex("(?<=[.!?।؟])\\s+")).firstOrNull().orEmpty()
            val source = firstSentence.ifBlank { firstTwoSentences }
            val words = source.split(Regex("\\s+"))
            val excerpt = StringBuilder()
            for (word in words) {
                val nextLength = excerpt.length + if (excerpt.isEmpty()) 0 else 1 + word.length
                if (nextLength > 136) break
                if (excerpt.isNotEmpty()) excerpt.append(' ')
                excerpt.append(word)
            }
            return excerpt.toString().trimEnd() + "…"
        }

        private suspend fun dailyVerse(context: Context, database: VerbumDatabase, config: WidgetConfigEntity): WidgetContent {
            val preferences = context.getSharedPreferences("verbum_preferences", Context.MODE_PRIVATE)
            val translationId = preferences.getString("active_translation_id", config.translationId.ifBlank { "eng_webc" })
                ?: "eng_webc"
            val need = preferences.getString("daily_verse_need", null)
            val daily = com.verbum.app.data.ScriptureRepository(database)
                .getDailyVerse(LocalDate.now(), translationId, need)
                ?: return WidgetContent("Today's verse", "This passage is unavailable in the selected Bible.", "Verbum")
            val text = daily.passage
                .filter { it.verse in daily.curated.verseStart..daily.curated.verseEnd }
                .joinToString(" ") { it.text }
                .ifBlank { return WidgetContent("Today's verse", "This passage is unavailable in the selected Bible.", "Verbum") }
            return WidgetContent("Today's verse", text, daily.citation, text)
        }

        private suspend fun lectionary(context: Context, database: VerbumDatabase, config: WidgetConfigEntity): WidgetContent {
            val today = LocalDate.now()
            val padded = "FEAST_%02d_%02d".format(today.monthValue, today.dayOfMonth)
            val compact = "FEAST_${today.monthValue}_${today.dayOfMonth}"
            val reading = database.lectionaryReadingDao().getByKey(config.liturgicalProfileId, padded)
                ?: database.lectionaryReadingDao().getByKey(config.liturgicalProfileId, compact)
                ?: database.lectionaryReadingDao().getByKey(config.liturgicalProfileId, "PROPER_${today.monthValue}_${today.dayOfMonth}")
            if (reading == null) {
                return WidgetContent(
                    context.getString(R.string.daily_lectionary_widget),
                    context.getString(R.string.coming_soon),
                    context.getString(R.string.widget_reading_data_missing),
                    context.getString(R.string.coming_soon),
                )
            }
            val coordinate = Coordinate.parse(reading.gospelKeyVerseCoord)
            val translationId = context.getSharedPreferences("verbum_preferences", Context.MODE_PRIVATE)
                .getString("active_translation_id", config.translationId.ifBlank { "eng_webc" }) ?: "eng_webc"
            val verseText = coordinate?.let {
                database.verseDao().getRange(translationId, it.bookId, it.chapter, it.startVerse, it.endVerse)
                    .joinToString(" ") { verse -> verse.text }
            }.orEmpty()
            val citations = listOfNotNull(reading.firstReadingRef, reading.psalmRef, reading.secondReadingRef, reading.gospelRef)
                .filter(String::isNotBlank)
                .joinToString(" · ")
            val body = verseText.ifBlank { citations.ifBlank { "Readings are available in Verbum." } }
            return WidgetContent(reading.title, body, citations)
        }
    }
}

private data class Coordinate(val bookId: Int, val chapter: Int, val startVerse: Int, val endVerse: Int) {
    companion object {
        fun parse(value: String): Coordinate? {
            val parts = value.split(':')
            if (parts.size != 3) return null
            val bookId = parts[0].toIntOrNull() ?: return null
            val chapter = parts[1].toIntOrNull() ?: return null
            val verseRange = parts[2].split('-', limit = 2)
            val start = verseRange[0].toIntOrNull() ?: return null
            val end = verseRange.getOrNull(1)?.toIntOrNull() ?: start
            if (bookId !in 1..73 || chapter <= 0 || start <= 0 || end < start) return null
            return Coordinate(bookId, chapter, start, end)
        }
    }
}
