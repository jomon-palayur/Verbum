package com.verbum.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.verbum.app.data.VerbumDatabase
import com.verbum.app.R
import com.verbum.app.data.model.TranslationEntity
import com.verbum.app.translations.TranslationCatalogEntry
import com.verbum.app.translations.TranslationCatalogRepository
import com.verbum.app.translations.TranslationDownloadWorker
import com.verbum.app.widget.DailyVerseWidgetProvider
import com.verbum.app.widget.LectionaryWidgetProvider
import com.verbum.app.widget.ShortVerseWidgetProvider
import com.verbum.app.widget.WidgetConfigActivity
import com.verbum.app.widget.WidgetType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LibraryScreen(
    database: VerbumDatabase,
    activeTranslationId: String,
    onTranslationSelected: (TranslationEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val installed by database.translationDao().observeAll().collectAsState(initial = emptyList())
    val work by WorkManager.getInstance(context).getWorkInfosByTagFlow(TranslationDownloadWorker.WORK_TAG)
        .collectAsState(initial = emptyList())
    var catalog by remember { mutableStateOf<List<TranslationCatalogEntry>>(emptyList()) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var widgetInstances by remember { mutableStateOf<List<WidgetInstance>>(emptyList()) }
    var pinSupported by remember { mutableStateOf(false) }
    var pinMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val manager = AppWidgetManager.getInstance(context)
        pinSupported = manager.isRequestPinAppWidgetSupported
        widgetInstances = listOf(
            WidgetType.DAILY_VERSE to DailyVerseWidgetProvider::class.java,
            WidgetType.LECTIONARY to LectionaryWidgetProvider::class.java,
            WidgetType.SHORT_VERSE to ShortVerseWidgetProvider::class.java,
        ).flatMap { (type, provider) ->
            manager.getAppWidgetIds(ComponentName(context, provider)).map { id -> WidgetInstance(id, type) }
        }.sortedBy(WidgetInstance::id)
    }

    LaunchedEffect(Unit) {
        loading = true
        runCatching { withContext(Dispatchers.IO) { TranslationCatalogRepository.load(context) } }
            .onSuccess { catalog = it; catalogError = null }
            .onFailure { catalogError = it.message ?: "Could not load the translation catalog" }
        loading = false
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(stringResource(R.string.library_heading), style = MaterialTheme.typography.headlineMedium)
        }
        item {
            WidgetManagerSection(
            instances = widgetInstances,
            pinSupported = pinSupported,
            message = pinMessage,
            onAdd = { type ->
                val provider = when (type) {
                    WidgetType.DAILY_VERSE -> DailyVerseWidgetProvider::class.java
                    WidgetType.LECTIONARY -> LectionaryWidgetProvider::class.java
                    WidgetType.SHORT_VERSE -> ShortVerseWidgetProvider::class.java
                }
                val manager = AppWidgetManager.getInstance(context)
                pinMessage = if (runCatching { manager.requestPinAppWidget(ComponentName(context, provider), null, null) }.getOrDefault(false)) {
                    "Widget request sent to the launcher."
                } else {
                    "This launcher could not start the widget pin request. Add it from the home-screen widget picker."
                }
            },
            onEdit = { instance ->
                context.startActivity(
                    Intent(context, WidgetConfigActivity::class.java)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, instance.id)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
            )
        }
        item { Text(stringResource(R.string.translations_heading), style = MaterialTheme.typography.titleMedium) }
        item { RegionalEditionsComingSoonCard() }
        when {
            loading -> item {
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            catalog.isEmpty() -> item { Text(catalogError ?: "The catalog is empty.", modifier = Modifier.padding(16.dp)) }
            else -> {
                items(catalog, key = TranslationCatalogEntry::id) { entry ->
                    val translation = installed.firstOrNull { it.id == entry.id }
                    val request = work.firstOrNull { it.tags.contains(TranslationDownloadWorker.WORK_TAG) && it.tags.contains("translation:${entry.id}") }
                    TranslationCard(
                        entry = entry,
                        installed = translation,
                        active = activeTranslationId == entry.id,
                        workInfo = request,
                        onSelect = { translation?.let(onTranslationSelected) },
                        onDownload = { scheduleDownload(context, entry) },
                    )
                }
            }
        }
        if (!catalogError.isNullOrBlank()) item { Text(catalogError!!, color = MaterialTheme.colorScheme.error) }
        item {
            Text("Widget-specific translation choices are managed in each widget’s settings.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 10.dp))
        }
    }
}

private data class WidgetInstance(val id: Int, val type: WidgetType)

@Composable
private fun WidgetManagerSection(
    instances: List<WidgetInstance>,
    pinSupported: Boolean,
    message: String?,
    onAdd: (WidgetType) -> Unit,
    onEdit: (WidgetInstance) -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.widgets_heading), style = MaterialTheme.typography.titleMedium)
            if (instances.isEmpty()) {
                Text(stringResource(R.string.widgets_empty), style = MaterialTheme.typography.bodyMedium)
            } else {
                instances.forEach { instance ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            val label = when (instance.type) {
                                WidgetType.DAILY_VERSE -> R.string.daily_verse_widget
                                WidgetType.LECTIONARY -> R.string.daily_lectionary_widget
                                WidgetType.SHORT_VERSE -> R.string.short_verse_widget
                            }
                            Text(stringResource(label))
                            Text(stringResource(R.string.widget_instance, instance.id), style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { onEdit(instance) }) { Text(stringResource(R.string.edit)) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onAdd(WidgetType.DAILY_VERSE) }, enabled = pinSupported) { Text(stringResource(R.string.add_verse)) }
                Button(onClick = { onAdd(WidgetType.LECTIONARY) }, enabled = pinSupported) { Text(stringResource(R.string.add_lectionary)) }
            }
            Button(onClick = { onAdd(WidgetType.SHORT_VERSE) }, enabled = pinSupported, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.add_short_verse))
            }
            if (!pinSupported) {
                Text("Use your home screen’s widget picker to add a Verbum widget.", style = MaterialTheme.typography.bodySmall)
            }
            if (!message.isNullOrBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TranslationCard(
    entry: TranslationCatalogEntry,
    installed: TranslationEntity?,
    active: Boolean,
    workInfo: WorkInfo?,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(entry.name, style = MaterialTheme.typography.titleMedium)
            if (entry.id == "eng_webc") {
                Text(stringResource(R.string.full_catholic_set_default), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
            Text("${entry.shortName} · ${entry.languageCode} · ${entry.bookCount} books", style = MaterialTheme.typography.bodyMedium)
            if (entry.catholicApproved && entry.bookCount == 73) {
                Text("Catholic-recognized edition${entry.approvalAuthority.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            } else if (entry.bookCount == 73) {
                Text("Full Catholic canon coverage · approval authority not recorded", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
            Text("${entry.format.name.replace('_', ' ')} · ${entry.license}", style = MaterialTheme.typography.bodySmall)
            if (entry.bookCount < 73) {
                Text(stringResource(R.string.complete_catholic_edition_coming_soon), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelMedium)
                Text(
                    stringResource(R.string.region_66_warning),
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (entry.comingSoon) {
                Text(stringResource(R.string.coming_soon), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelLarge)
            }
            if (entry.attribution.isNotBlank()) {
                Text(entry.attribution, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { runCatching { uriHandler.openUri(entry.sourceUrl) } }) { Text(stringResource(R.string.source_license)) }
            when (workInfo?.state) {
                WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                    val progress = workInfo.progress.getInt(TranslationDownloadWorker.KEY_PROGRESS, 0)
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Text(if (workInfo.state == WorkInfo.State.RUNNING) "Downloading and installing…" else "Waiting to download…")
                }
                WorkInfo.State.FAILED -> Text(workInfo.outputData.getString(TranslationDownloadWorker.KEY_ERROR) ?: "Install failed", color = MaterialTheme.colorScheme.error)
                WorkInfo.State.SUCCEEDED -> Text(stringResource(R.string.installed), color = MaterialTheme.colorScheme.primary)
                WorkInfo.State.CANCELLED -> Unit
                null -> Unit
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    installed != null -> {
                        TextButton(onClick = onSelect, enabled = !active) { Text(stringResource(if (active) R.string.active_translation else R.string.use_translation)) }
                        Text(if (installed.isBundled) "Bundled" else "Installed", style = MaterialTheme.typography.labelMedium)
                    }
                    entry.comingSoon -> Unit
                    workInfo?.state in listOf(WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED) -> Unit
                    entry.bundled -> Button(onClick = onDownload) { Text("Restore bundled edition") }
                    else -> Button(onClick = onDownload) { Text(stringResource(R.string.download)) }
                }
            }
        }
    }
}

@Composable
private fun RegionalEditionsComingSoonCard() {
    Card(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.complete_catholic_edition_coming_soon), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.coming_soon), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelMedium)
            }
            Text(stringResource(R.string.regional_catholic_editions_coming_soon), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun scheduleDownload(context: android.content.Context, entry: TranslationCatalogEntry) {
    val request = OneTimeWorkRequestBuilder<TranslationDownloadWorker>()
        .setInputData(TranslationDownloadWorker.workData(entry))
        .setConstraints(TranslationDownloadWorker.networkConstraints())
        .addTag(TranslationDownloadWorker.WORK_TAG)
        .addTag("translation:${entry.id}")
        .build()
    WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
        "verbum_translation_${entry.id}",
        ExistingWorkPolicy.KEEP,
        request,
    )
}
