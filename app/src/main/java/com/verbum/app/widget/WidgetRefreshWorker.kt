package com.verbum.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.verbum.app.data.VerbumDatabase
import java.util.concurrent.TimeUnit

class WidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val appWidgetId = inputData.getInt(KEY_APP_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return Result.failure()
        val type = runCatching { WidgetType.valueOf(inputData.getString(KEY_WIDGET_TYPE).orEmpty()) }
            .getOrNull() ?: return Result.failure()
        return runCatching {
            val database = VerbumDatabase.getInstance(applicationContext)
            val config = database.widgetConfigDao().get(appWidgetId) ?: return Result.success()
            val manager = AppWidgetManager.getInstance(applicationContext)
            if (!manager.getAppWidgetIds(android.content.ComponentName(applicationContext, providerClass(type)))
                    .contains(appWidgetId)
            ) return Result.success()
            val content = WidgetContent.load(applicationContext, database, config, type)
            WidgetRenderer.update(applicationContext, manager, appWidgetId, config, type, content)
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        private const val KEY_APP_WIDGET_ID = "app_widget_id"
        private const val KEY_WIDGET_TYPE = "widget_type"

        fun schedule(context: Context, config: com.verbum.app.data.model.WidgetConfigEntity) {
            val type = runCatching { WidgetType.valueOf(config.widgetType) }.getOrNull() ?: return
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
                config.refreshHours.coerceAtLeast(1).toLong(),
                TimeUnit.HOURS,
            )
                .setInputData(workDataOf(KEY_APP_WIDGET_ID to config.appWidgetId, KEY_WIDGET_TYPE to type.name))
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                uniqueWorkName(config.appWidgetId),
                ExistingPeriodicWorkPolicy.REPLACE,
                request,
            )
        }

        suspend fun syncTranslation(context: Context, database: VerbumDatabase, translationId: String) {
            context.getSharedPreferences("verbum_preferences", Context.MODE_PRIVATE)
                .edit().putString("active_translation_id", translationId).apply()
            database.widgetConfigDao().updateAllTranslations(translationId)
            refreshAllNow(context, database)
        }

        suspend fun refreshAllNow(context: Context, database: VerbumDatabase) {
            val manager = AppWidgetManager.getInstance(context)
            val preferredTranslation = context.getSharedPreferences("verbum_preferences", Context.MODE_PRIVATE)
                .getString("active_translation_id", "eng_webc") ?: "eng_webc"
            WidgetType.entries.forEach { type ->
                val ids = manager.getAppWidgetIds(android.content.ComponentName(context, providerClass(type)))
                ids.forEach { id ->
                    val saved = database.widgetConfigDao().get(id)
                    val config = (saved ?: WidgetConfigActivity.defaultConfig(id, type))
                        .copy(translationId = preferredTranslation)
                    if (saved != null) database.widgetConfigDao().upsert(config)
                    schedule(context, config)
                    val content = WidgetContent.load(context, database, config, type)
                    WidgetRenderer.update(context, manager, id, config, type, content)
                }
            }
        }

        fun cancel(context: Context, appWidgetId: Int) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(uniqueWorkName(appWidgetId))
        }

        private fun uniqueWorkName(appWidgetId: Int) = "verbum_widget_refresh_$appWidgetId"

        private fun providerClass(type: WidgetType) = when (type) {
            WidgetType.DAILY_VERSE -> DailyVerseWidgetProvider::class.java
            WidgetType.LECTIONARY -> LectionaryWidgetProvider::class.java
            WidgetType.SHORT_VERSE -> ShortVerseWidgetProvider::class.java
        }
    }
}
