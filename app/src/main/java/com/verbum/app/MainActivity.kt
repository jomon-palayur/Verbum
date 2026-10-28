package com.verbum.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.verbum.app.data.ScriptureRepository
import com.verbum.app.data.VerbumDatabase
import com.verbum.app.audio.BiblePlaybackService
import com.verbum.app.audio.BiblePlaybackState
import com.verbum.app.audio.PlaybackSnapshot
import com.verbum.app.ui.BibleReaderScreen
import com.verbum.app.ui.LibraryScreen
import com.verbum.app.ui.SettingsScreen
import com.verbum.app.ui.LectionaryScreen
import com.verbum.app.ui.TodayScreen
import com.verbum.app.ui.VerbumTheme
import com.verbum.app.widget.WidgetRefreshWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppTab(val label: Int, val icon: ImageVector) {
    TODAY(R.string.tab_today, Icons.Filled.WbSunny),
    LECTIONARY(R.string.tab_readings, Icons.AutoMirrored.Filled.EventNote),
    BIBLE(R.string.tab_bible, Icons.AutoMirrored.Filled.MenuBook),
    LIBRARY(R.string.tab_library, Icons.Filled.Bookmarks),
    SETTINGS(R.string.tab_settings, Icons.Filled.Settings),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID)
        val startTab = tabFor(intent)
        setContent {
            var themeMode by remember {
                mutableStateOf(getSharedPreferences("verbum_preferences", MODE_PRIVATE).getString("theme_mode", "system") ?: "system")
            }
            VerbumTheme(themeMode) {
                VerbumApp(
                    startTab,
                    widgetId,
                    themeMode,
                    onThemeModeChanged = { mode ->
                        themeMode = mode
                        getSharedPreferences("verbum_preferences", MODE_PRIVATE).edit().putString("theme_mode", mode).apply()
                    },
                )
            }
        }
    }

    private fun tabFor(intent: Intent): AppTab = when (intent.getStringExtra("destination") ?: intent.action) {
        "LECTIONARY", "com.verbum.app.action.LECTIONARY" -> AppTab.LECTIONARY
        "TODAY", "com.verbum.app.action.TODAY" -> AppTab.TODAY
        else -> AppTab.TODAY
    }
}

@Composable
private fun VerbumApp(
    startTab: AppTab,
    widgetId: Int,
    themeMode: String,
    onThemeModeChanged: (String) -> Unit,
) {
    val context = LocalContext.current
    val database = remember { VerbumDatabase.getInstance(context) }
    val repository = remember(database) { ScriptureRepository(database) }
    var selectedTab by remember { mutableStateOf(startTab) }
    var activeTranslationId by remember { mutableStateOf("eng_webc") }
    var activeTranslationLanguage by remember { mutableStateOf("en-US") }
    var readerTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var liturgicalProfileId by remember { mutableStateOf("roman") }
    var readerTextScale by remember {
        mutableFloatStateOf(context.getSharedPreferences("verbum_preferences", android.content.Context.MODE_PRIVATE)
            .getFloat("reader_text_scale", 1f).coerceIn(0.8f, 1.4f))
    }
    val appScope = rememberCoroutineScope()
    val playback by BiblePlaybackState.state.collectAsState()
    val haptics = LocalHapticFeedback.current
    val onTranslationChanged: (String, String) -> Unit = { id, language ->
        activeTranslationId = id
        activeTranslationLanguage = language
        context.getSharedPreferences("verbum_preferences", android.content.Context.MODE_PRIVATE)
            .edit().putString("active_translation_id", id).apply()
        appScope.launch(Dispatchers.IO) { WidgetRefreshWorker.syncTranslation(context, database, id) }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        appScope.launch {
            val preferredId = context.getSharedPreferences("verbum_preferences", android.content.Context.MODE_PRIVATE)
                .getString("active_translation_id", "eng_webc") ?: "eng_webc"
            val preferred = withContext(Dispatchers.IO) { database.translationDao().getById(preferredId) }
            if (preferred != null && preferred.id != activeTranslationId) {
                activeTranslationId = preferred.id
                activeTranslationLanguage = preferred.languageCode
            }
        }
    }

    LaunchedEffect(Unit) {
        val initial = withContext(Dispatchers.IO) {
            val translations = database.translationDao().observeAll().first()
            val preferredId = context.getSharedPreferences("verbum_preferences", android.content.Context.MODE_PRIVATE)
                .getString("active_translation_id", "eng_webc")
            val configured = if (widgetId != android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID) {
                database.widgetConfigDao().get(widgetId)
            } else null
            val selected = translations.firstOrNull { it.id == preferredId } ?: translations.firstOrNull()
            selected to configured
        }
        initial.first?.let { activeTranslationId = it.id; activeTranslationLanguage = it.languageCode }
        initial.first?.let { selected ->
            appScope.launch(Dispatchers.IO) { WidgetRefreshWorker.syncTranslation(context, database, selected.id) }
        }
        liturgicalProfileId = initial.second?.liturgicalProfileId
            ?: context.getSharedPreferences("verbum_preferences", android.content.Context.MODE_PRIVATE)
                .getString("liturgical_profile_id", "roman") ?: "roman"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                if (playback.active) {
                    PlaybackMiniPlayer(
                        snapshot = playback,
                        onToggle = { sendPlaybackAction(context, if (playback.playing) BiblePlaybackService.ACTION_PAUSE else BiblePlaybackService.ACTION_PLAY) },
                        onStop = { sendPlaybackAction(context, BiblePlaybackService.ACTION_STOP) },
                        onPrevious = { sendPlaybackAction(context, BiblePlaybackService.ACTION_PREVIOUS) },
                        onNext = { sendPlaybackAction(context, BiblePlaybackService.ACTION_NEXT) },
                        onSpeed = { sendPlaybackAction(context, BiblePlaybackService.ACTION_SPEED, if (playback.speed >= 2f) 0.75f else playback.speed + 0.25f) },
                    )
                }
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    AppTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = {
                                if (selectedTab != tab) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedTab = tab
                            },
                            icon = { Icon(tab.icon, contentDescription = stringResource(tab.label)) },
                            label = { Text(stringResource(tab.label)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                (fadeIn(tween(180)) + slideInHorizontally(tween(180)) { it / 16 }) togetherWith
                    (fadeOut(tween(120)) + slideOutHorizontally(tween(120)) { -it / 16 })
            },
            label = "verbum-tab-transition",
        ) { tab -> when (tab) {
            AppTab.TODAY -> TodayScreen(
                repository = repository,
                translationId = activeTranslationId,
                forcedCuratedId = null,
                modifier = Modifier.fillMaxSize().padding(padding),
                onDailyVerseNeedChanged = {
                    appScope.launch(Dispatchers.IO) { WidgetRefreshWorker.refreshAllNow(context, database) }
                },
                onOpenPassage = { bookId, chapter -> readerTarget = bookId to chapter; selectedTab = AppTab.BIBLE },
            )
            AppTab.LECTIONARY -> LectionaryScreen(
                repository = repository,
                translationId = activeTranslationId,
                profileId = liturgicalProfileId,
                onProfileSelected = { profile ->
                    liturgicalProfileId = profile
                    context.getSharedPreferences("verbum_preferences", android.content.Context.MODE_PRIVATE)
                        .edit().putString("liturgical_profile_id", profile).apply()
                },
                modifier = Modifier.fillMaxSize().padding(padding),
                onOpenPassage = { bookId, chapter -> readerTarget = bookId to chapter; selectedTab = AppTab.BIBLE },
            )
            AppTab.BIBLE -> BibleReaderScreen(
                database = database,
                initialTranslationId = activeTranslationId,
                initialTarget = readerTarget,
                onTargetConsumed = { readerTarget = null },
                onTranslationChanged = onTranslationChanged,
                readerTextScale = readerTextScale,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            AppTab.LIBRARY -> LibraryScreen(
                database = database,
                activeTranslationId = activeTranslationId,
                onTranslationSelected = { translation -> onTranslationChanged(translation.id, translation.languageCode) },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            AppTab.SETTINGS -> SettingsScreen(
                database = database,
                themeMode = themeMode,
                onThemeModeChanged = onThemeModeChanged,
                activeTranslationId = activeTranslationId,
                onTranslationChanged = onTranslationChanged,
                readerTextScale = readerTextScale,
                onReaderTextScaleChanged = { scale ->
                    readerTextScale = scale
                    context.getSharedPreferences("verbum_preferences", android.content.Context.MODE_PRIVATE)
                        .edit().putFloat("reader_text_scale", scale).apply()
                },
                liturgicalProfileId = liturgicalProfileId,
                onLiturgicalProfileChanged = { profile ->
                    liturgicalProfileId = profile
                    context.getSharedPreferences("verbum_preferences", android.content.Context.MODE_PRIVATE)
                        .edit().putString("liturgical_profile_id", profile).apply()
                },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        } }
    }
}

@Composable
private fun PlaybackMiniPlayer(
    snapshot: PlaybackSnapshot,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSpeed: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, tonalElevation = 2.dp) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(snapshot.reference, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Text("Bible audio · ${snapshot.speed}×", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onPrevious() }) { Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.previous_verse)) }
            IconButton(onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onToggle() }) { Icon(if (snapshot.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = stringResource(if (snapshot.playing) R.string.pause else R.string.play)) }
            IconButton(onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onNext() }) { Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.next_verse)) }
            androidx.compose.material3.FilledTonalButton(onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSpeed() }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                Text("${snapshot.speed}×", style = MaterialTheme.typography.labelLarge)
            }
            IconButton(onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onStop() }) { Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.stop_playback)) }
        }
    }
}

private fun sendPlaybackAction(context: android.content.Context, action: String, speed: Float? = null) {
    val intent = Intent(context, BiblePlaybackService::class.java).setAction(action)
    speed?.let { intent.putExtra(BiblePlaybackService.EXTRA_SPEED, it) }
    ContextCompat.startForegroundService(context, intent)
}

