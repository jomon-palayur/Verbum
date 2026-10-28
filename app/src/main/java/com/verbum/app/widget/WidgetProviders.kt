package com.verbum.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import com.verbum.app.data.VerbumDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DailyVerseWidgetProvider : ConfigurableWidgetProvider(WidgetType.DAILY_VERSE)

class LectionaryWidgetProvider : ConfigurableWidgetProvider(WidgetType.LECTIONARY)

class ShortVerseWidgetProvider : ConfigurableWidgetProvider(WidgetType.SHORT_VERSE)

abstract class ConfigurableWidgetProvider(private val type: WidgetType) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgetsAsync(context, manager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        updateWidgetsAsync(context, manager, intArrayOf(appWidgetId))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetRefreshWorker.cancel(context, it) }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            appWidgetIds.forEach { VerbumDatabase.getInstance(context).widgetConfigDao().delete(it) }
        }
    }

    private fun updateWidgetsAsync(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val database = VerbumDatabase.getInstance(context)
                appWidgetIds.forEach { id ->
                    val config = database.widgetConfigDao().get(id) ?: WidgetConfigActivity.defaultConfig(id, type)
                    val data = WidgetContent.load(context, database, config, type)
                    WidgetRenderer.update(context, manager, id, config, type, data)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

enum class WidgetType { DAILY_VERSE, LECTIONARY, SHORT_VERSE }
