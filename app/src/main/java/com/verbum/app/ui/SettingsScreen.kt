package com.verbum.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verbum.app.R
import com.verbum.app.data.VerbumDatabase

@Composable
fun SettingsScreen(
    database: VerbumDatabase,
    themeMode: String,
    onThemeModeChanged: (String) -> Unit,
    activeTranslationId: String,
    onTranslationChanged: (String, String) -> Unit,
    readerTextScale: Float,
    onReaderTextScaleChanged: (Float) -> Unit,
    liturgicalProfileId: String,
    onLiturgicalProfileChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val translations by database.translationDao().observeAll().collectAsState(initial = emptyList())
    val profiles by database.liturgicalProfileDao().observeAll().collectAsState(initial = emptyList())
    var translationMenu by remember { mutableStateOf(false) }
    var profileMenu by remember { mutableStateOf(false) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.settings_heading), style = MaterialTheme.typography.headlineMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.theme_mode), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        "system" to R.string.theme_system,
                        "light" to R.string.theme_light,
                        "dark" to R.string.theme_dark,
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { onThemeModeChanged(mode) },
                            label = { Text(stringResource(label), maxLines = 1) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Text(
                    stringResource(R.string.dynamic_colors_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.bible_display), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.default_translation), style = MaterialTheme.typography.titleSmall)
                Box {
                    FilledTonalButton(onClick = { translationMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(translations.firstOrNull { it.id == activeTranslationId }?.name ?: stringResource(R.string.offline_bible_missing))
                    }
                    DropdownMenu(expanded = translationMenu, onDismissRequest = { translationMenu = false }) {
                        translations.forEach { translation ->
                            DropdownMenuItem(
                                text = { Text(translation.name) },
                                onClick = {
                                    onTranslationChanged(translation.id, translation.languageCode)
                                    translationMenu = false
                                },
                            )
                        }
                    }
                }
                Text(stringResource(R.string.font_scale_label), style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = readerTextScale,
                    onValueChange = onReaderTextScaleChanged,
                    valueRange = 0.8f..1.4f,
                    steps = 5,
                )
                Text(
                    stringResource(R.string.reader_text_preview),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp * readerTextScale),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.liturgical_tradition), style = MaterialTheme.typography.titleLarge)
                Box {
                    FilledTonalButton(onClick = { profileMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(profiles.firstOrNull { it.id == liturgicalProfileId }?.name ?: "Roman Rite")
                    }
                    DropdownMenu(expanded = profileMenu, onDismissRequest = { profileMenu = false }) {
                        profiles.forEach { profile ->
                            DropdownMenuItem(
                                text = { Text("${profile.name} · ${profile.regionCode}") },
                                onClick = {
                                    onLiturgicalProfileChanged(profile.id)
                                    profileMenu = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
