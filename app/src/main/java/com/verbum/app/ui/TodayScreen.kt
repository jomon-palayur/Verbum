package com.verbum.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import com.verbum.app.data.DailyVerseContent
import com.verbum.app.R
import com.verbum.app.data.ScriptureRepository
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val needs = listOf(null to R.string.need_all, "anxiety" to R.string.need_anxiety, "grief" to R.string.need_grief, "guidance" to R.string.need_guidance, "peace" to R.string.need_peace, "hope" to R.string.need_hope, "trust" to R.string.need_trust, "hardship" to R.string.need_hardship)

@Composable
fun TodayScreen(
    repository: ScriptureRepository,
    translationId: String,
    forcedCuratedId: Int?,
    modifier: Modifier = Modifier,
    onDailyVerseNeedChanged: (String?) -> Unit,
    onOpenPassage: (Int, Int) -> Unit,
) {
    val context = LocalContext.current
    var selectedNeed by remember {
        mutableStateOf(context.getSharedPreferences("verbum_preferences", android.content.Context.MODE_PRIVATE)
            .getString("daily_verse_need", null))
    }
    var content by remember { mutableStateOf<DailyVerseContent?>(null) }
    var loading by remember { mutableStateOf(true) }
    val today = LocalDate.now()
    LaunchedEffect(today, translationId, selectedNeed, forcedCuratedId) {
        loading = true
        content = withContext(Dispatchers.IO) {
            repository.getDailyVerse(today, translationId, selectedNeed, forcedCuratedId)
        }
        loading = false
    }
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text(stringResource(R.string.today_heading), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))
        Text(
            today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )
        Text(stringResource(R.string.today_need_prompt), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 2.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                needs.forEach { (tag, label) ->
                FilterChip(
                    selected = selectedNeed == tag,
                    onClick = {
                        selectedNeed = tag
                        context.getSharedPreferences("verbum_preferences", android.content.Context.MODE_PRIVATE)
                            .edit().putString("daily_verse_need", tag).apply()
                        onDailyVerseNeedChanged(tag)
                    },
                    label = { Text(stringResource(label)) },
                )
            }
        }
        when {
            loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(24.dp))
            content == null -> Text(stringResource(R.string.daily_reflection_unavailable), modifier = Modifier.padding(16.dp))
            else -> {
                val verse = content!!
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(top = 12.dp),
                    state = rememberLazyListState(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Card(
                            Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                        ) {
                            Column(Modifier.padding(horizontal = 22.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(stringResource(R.string.daily_verse_heading), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                                val centralVerses = verse.passage.filter { it.verse in verse.curated.verseStart..verse.curated.verseEnd }
                                Text(
                                    centralVerses.joinToString(" ") { it.text },
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontFamily = FontFamily.Serif,
                                        lineHeight = 34.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Text(verse.citation, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                                Button(
                                    onClick = { onOpenPassage(verse.book.id, verse.curated.chapter) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                                ) { Text(stringResource(R.string.read_this_chapter)) }
                            }
                        }
                    }
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.read_in_context), style = MaterialTheme.typography.titleMedium)
                                verse.passage.forEach { line ->
                                    Text(
                                        text = "${line.verse}. ${line.text}",
                                        style = if (line.verse in verse.curated.verseStart..verse.curated.verseEnd) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                                        color = if (line.verse in verse.curated.verseStart..verse.curated.verseEnd) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.context), style = MaterialTheme.typography.titleMedium)
                                Text(verse.curated.contextSummary)
                            }
                        }
                    }
                    if (verse.curated.patristicQuote.isNotBlank()) {
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(stringResource(R.string.church_fathers), style = MaterialTheme.typography.titleMedium)
                                    Text("“${verse.curated.patristicQuote}”", style = MaterialTheme.typography.bodyLarge)
                                    Text(verse.curated.patristicAuthor, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.carry_with_you), style = MaterialTheme.typography.titleMedium)
                                Text(verse.curated.practicalTakeaway)
                            }
                        }
                    }
                }
            }
        }
    }
}
