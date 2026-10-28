package com.verbum.app.widget

import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.os.Build
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.annotation.RequiresApi
import com.verbum.app.data.VerbumDatabase
import com.verbum.app.R
import com.verbum.app.data.model.WidgetConfigEntity
import com.verbum.app.data.model.LiturgicalProfileEntity
import com.verbum.app.ui.VerbumTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

class WidgetConfigActivity : ComponentActivity() {
    private var config by mutableStateOf<WidgetConfigEntity?>(null)
    private var installedTranslations by mutableStateOf<List<String>>(emptyList())
    private var liturgicalProfiles by mutableStateOf<List<LiturgicalProfileEntity>>(emptyList())
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var widgetType: WidgetType = WidgetType.DAILY_VERSE
    private var wallpaperColor: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        widgetType = typeFor(appWidgetId)
        wallpaperColor = loadWallpaperColor()
        lifecycleScope.launch {
            val database = withContext(Dispatchers.IO) { VerbumDatabase.getInstance(this@WidgetConfigActivity) }
            val existingAndTranslations = withContext(Dispatchers.IO) {
                Triple(database.widgetConfigDao().get(appWidgetId), database.translationDao().getInstalledIds(), database.liturgicalProfileDao().observeAll().first())
            }
            val saved = existingAndTranslations.first
            installedTranslations = existingAndTranslations.second
            liturgicalProfiles = existingAndTranslations.third
            config = saved ?: defaultConfig(appWidgetId, widgetType).copy(
                translationId = installedTranslations.firstOrNull { it == "eng_webc" }
                    ?: installedTranslations.firstOrNull().orEmpty(),
            )
            config = config?.copy(
                translationId = getSharedPreferences("verbum_preferences", MODE_PRIVATE)
                    .getString("active_translation_id", config?.translationId) ?: config?.translationId.orEmpty(),
            )
        }
        setContent {
            VerbumTheme {
                val current = config
                if (current == null) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading widget settings…") }
                    }
                } else {
                    WidgetSettingsScreen(
                        config = current,
                        translations = installedTranslations,
                        profiles = liturgicalProfiles,
                        wallpaperColor = wallpaperColor,
                        onChange = { config = it },
                        onSave = ::saveAndFinish,
                        onCancel = { finish() },
                    )
                }
            }
        }
    }

    private fun typeFor(id: Int): WidgetType {
        val providerName = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider?.className.orEmpty()
        return when {
            providerName.endsWith("LectionaryWidgetProvider") -> WidgetType.LECTIONARY
            providerName.endsWith("ShortVerseWidgetProvider") -> WidgetType.SHORT_VERSE
            else -> WidgetType.DAILY_VERSE
        }
    }

    private fun saveAndFinish(value: WidgetConfigEntity) {
        lifecycleScope.launch {
            val database = withContext(Dispatchers.IO) { VerbumDatabase.getInstance(this@WidgetConfigActivity) }
            val normalized = value.copy(
                appWidgetId = appWidgetId,
                widgetType = widgetType.name,
                bgOpacityPercent = value.bgOpacityPercent.coerceIn(0, 100),
                refreshHours = value.refreshHours.coerceAtLeast(1),
            )
            withContext(Dispatchers.IO) { database.widgetConfigDao().upsert(normalized) }
            withContext(Dispatchers.IO) {
                WidgetRefreshWorker.syncTranslation(this@WidgetConfigActivity, database, normalized.translationId)
            }
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }

    private fun loadWallpaperColor(): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
        return runCatching { readWallpaperPrimaryColor() }.getOrNull()
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private fun readWallpaperPrimaryColor(): Int? = WallpaperManager.getInstance(this)
        .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        ?.primaryColor
        ?.toArgb()

    companion object {
        fun defaultConfig(id: Int, type: WidgetType) = WidgetConfigEntity(
            appWidgetId = id,
            widgetType = type.name,
            translationId = "eng_webc",
            bgOpacityPercent = 35,
            bgTintMode = "DARK",
            textColorMode = "WHITE",
            useDropShadow = true,
            fontFamily = "SERIF",
            refreshHours = 24,
        )
    }
}

@Composable
private fun WidgetSettingsScreen(
    config: WidgetConfigEntity,
    translations: List<String>,
    profiles: List<LiturgicalProfileEntity>,
    wallpaperColor: Int?,
    onChange: (WidgetConfigEntity) -> Unit,
    onSave: (WidgetConfigEntity) -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Widget appearance", style = MaterialTheme.typography.headlineSmall)
        WidgetPreview(config, wallpaperColor)
        Text("Background opacity · ${config.bgOpacityPercent}%")
        Slider(
            value = config.bgOpacityPercent.toFloat(),
            onValueChange = { onChange(config.copy(bgOpacityPercent = it.toInt())) },
            valueRange = 0f..100f,
        )
        OptionSelector(
            label = "Surface tint",
            selected = config.bgTintMode,
            options = listOf("DARK", "LIGHT", "DYNAMIC", "LITURGICAL"),
            onSelect = { onChange(config.copy(bgTintMode = it)) },
        )
        OptionSelector(
            label = "Text color",
            selected = config.textColorMode,
            options = listOf("AUTO", "WHITE", "BLACK", "ACCENT"),
            onSelect = { onChange(config.copy(textColorMode = it)) },
        )
        Text(
            stringResource(R.string.widget_contrast_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Text shadow")
            Switch(checked = config.useDropShadow, onCheckedChange = { onChange(config.copy(useDropShadow = it)) })
        }
        OptionSelector(
            label = "Scripture typeface",
            selected = config.fontFamily,
            options = listOf("SERIF", "SANS", "MONOSPACE"),
            onSelect = { onChange(config.copy(fontFamily = it)) },
        )
        OptionSelector(
            label = "Refresh interval (hours)",
            selected = config.refreshHours.toString(),
            options = listOf("1", "6", "12", "24"),
            onSelect = { onChange(config.copy(refreshHours = it.toInt())) },
        )
        Text("Bible version is shared with Verbum and all widgets.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TranslationSelector(
            selected = config.translationId,
            translations = translations,
            onSelect = { onChange(config.copy(translationId = it)) },
        )
        if (config.widgetType == WidgetType.LECTIONARY.name) {
            ProfileSelector(
                selected = config.liturgicalProfileId,
                profiles = profiles,
                onSelect = { onChange(config.copy(liturgicalProfileId = it)) },
            )
            Text(stringResource(R.string.additional_rites_coming_soon), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(onClick = { onSave(config) }, modifier = Modifier.weight(1f)) { Text("Save") }
        }
    }
}

@Composable
private fun ProfileSelector(selected: String, profiles: List<LiturgicalProfileEntity>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Liturgical tradition")
        Box {
            Button(onClick = { expanded = true }) {
                Text(profiles.firstOrNull { it.id == selected }?.name ?: "Roman Rite")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                profiles.forEach { profile ->
                    DropdownMenuItem(text = { Text("${profile.name} · ${profile.regionCode}") }, onClick = { onSelect(profile.id); expanded = false })
                }
            }
        }
    }
}

@Composable
private fun WidgetPreview(config: WidgetConfigEntity, wallpaperColor: Int?) {
    val surface = when (config.bgTintMode) {
        "LIGHT" -> Color.White
        "LITURGICAL" -> Color(0xFF315B45)
        "DYNAMIC" -> Color(0xFF546E7A)
        else -> Color(0xFF121212)
    }
    val foreground = when (config.textColorMode) {
        "BLACK" -> Color.Black
        "ACCENT" -> Color(0xFFFFD166)
        else -> Color.White
    }
    Box(
        modifier = Modifier.fillMaxWidth().height(190.dp).clip(MaterialTheme.shapes.extraLarge),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize().background(wallpaperColor?.let { Color(it) } ?: Color(0xFF405060)))
        Box(
            Modifier.fillMaxSize()
                .padding(18.dp)
                .clip(MaterialTheme.shapes.large)
                .background(surface.copy(alpha = config.bgOpacityPercent / 100f)),
        )
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 34.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                when (config.widgetType) {
                    WidgetType.LECTIONARY.name -> "● Daily Mass readings"
                    WidgetType.SHORT_VERSE.name -> "A short verse for your lock screen"
                    else -> "Today's verse"
                },
                color = foreground.copy(alpha = 0.88f),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = when (config.widgetType) {
                    WidgetType.LECTIONARY.name -> "Today's Gospel and readings"
                    WidgetType.SHORT_VERSE.name -> "The Lord is my shepherd. · Ps 23:1"
                    else -> "The Lord is my shepherd; I shall not want."
                },
                color = foreground,
                style = when (config.fontFamily) {
                    "SANS" -> MaterialTheme.typography.titleMedium
                    "MONOSPACE" -> MaterialTheme.typography.bodyLarge.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    else -> MaterialTheme.typography.titleMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
                },
            )
            Text(if (config.translationId.isBlank()) "No translation installed" else config.translationId, color = foreground.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun OptionSelector(label: String, selected: String, options: List<String>, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelect(option) },
                    label = { Text(option.lowercase().replaceFirstChar(Char::uppercase)) },
                )
            }
        }
    }
}

@Composable
private fun TranslationSelector(selected: String, translations: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Translation override")
        Box {
            Button(onClick = { expanded = true }) {
                Text(translations.firstOrNull { it == selected } ?: if (translations.isEmpty()) "No translations installed" else "Choose translation")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                translations.forEach { id ->
                    DropdownMenuItem(text = { Text(id) }, onClick = { onSelect(id); expanded = false })
                }
            }
        }
    }
}
