package com.verbum.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.verbum.app.data.DailyLectionaryContent
import com.verbum.app.data.ScripturePassage
import com.verbum.app.data.ScriptureRepository
import com.verbum.app.R
import com.verbum.app.data.model.LiturgicalProfileEntity
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LectionaryScreen(
    repository: ScriptureRepository,
    translationId: String,
    profileId: String,
    onProfileSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenPassage: (Int, Int) -> Unit,
) {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var content by remember { mutableStateOf<DailyLectionaryContent?>(null) }
    val profiles by repository.observeLiturgicalProfiles().collectAsState(initial = emptyList())
    var profileMenuExpanded by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(date, translationId, profileId) {
        loading = true
        content = withContext(Dispatchers.IO) { repository.getLectionary(date, translationId, profileId) }
        loading = false
    }
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Text(stringResource(R.string.lectionary_heading), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp))
        androidx.compose.foundation.layout.Box {
            FilledTonalButton(onClick = { profileMenuExpanded = true }) {
                Text(profiles.firstOrNull { it.id == profileId }?.name ?: "Roman Rite")
            }
            DropdownMenu(expanded = profileMenuExpanded, onDismissRequest = { profileMenuExpanded = false }) {
                profiles.forEach { profile ->
                    DropdownMenuItem(
                        text = { Text("${profile.name} · ${profile.regionCode}") },
                        onClick = { onProfileSelected(profile.id); profileMenuExpanded = false },
                    )
                }
            }
        }
        Text(stringResource(R.string.additional_rites_coming_soon), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { date = date.minusDays(1) }) { Text("‹") }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(date.toString(), style = MaterialTheme.typography.titleMedium)
                Button(onClick = { date = LocalDate.now() }, enabled = date != LocalDate.now()) { Text(stringResource(R.string.today_button)) }
            }
            Button(onClick = { date = date.plusDays(1) }) { Text("›") }
        }
        when {
            loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(24.dp))
            content == null -> Text(stringResource(R.string.calendar_unavailable), Modifier.padding(16.dp))
            else -> {
                val day = content!!
                // Use the theme's accessible text color; liturgical colors can be too light
                // (especially white/gold) to read as text on card surfaces.
                val accent = MaterialTheme.colorScheme.primary
                if (day.readings == null) {
                    Card(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (profileId == "roman") {
                                Text(day.day.title, color = accent, style = MaterialTheme.typography.titleLarge)
                                Text("${day.day.season.name.replace('_', ' ')} · ${day.day.color.name.lowercase().replaceFirstChar(Char::uppercase)}")
                                Text("Sunday cycle ${day.day.sundayCycle.name} · Weekday cycle ${day.day.weekdayCycle.name}")
                            } else {
                                Text(profiles.firstOrNull { it.id == profileId }?.name ?: "Liturgical tradition", color = accent, style = MaterialTheme.typography.titleLarge)
                            }
                            Text(stringResource(R.string.coming_soon), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelMedium)
                            Text(
                                stringResource(if (profileId == "roman") R.string.readings_for_date_coming_soon else R.string.readings_data_coming_soon),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                } else {
                    Text(day.day.title, color = accent, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 10.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(day.passages) { passage ->
                            ReadingCard(passage, accent, onOpenPassage)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingCard(passage: ScripturePassage, accent: Color, onOpenPassage: (Int, Int) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(passage.label, color = accent, style = MaterialTheme.typography.labelLarge)
            Text(passage.citation, style = MaterialTheme.typography.titleMedium)
            if (passage.text.isNotBlank()) Text(passage.text, style = MaterialTheme.typography.bodyLarge)
            Button(onClick = { onOpenPassage(passage.bookId, passage.chapter) }) { Text(stringResource(R.string.open_in_bible)) }
        }
    }
}

