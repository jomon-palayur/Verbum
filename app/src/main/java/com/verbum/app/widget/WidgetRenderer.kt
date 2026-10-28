package com.verbum.app.widget

import android.app.PendingIntent
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import kotlin.math.roundToInt
import com.verbum.app.MainActivity
import com.verbum.app.R
import com.verbum.app.data.model.WidgetConfigEntity
import com.verbum.app.liturgy.LiturgicalCalendarEngine
import com.verbum.app.liturgy.LiturgicalColor
import java.time.LocalDate

object WidgetRenderer {
    private val primaryIds = listOf(
        R.id.widget_primary_serif,
        R.id.widget_primary_serif_shadow,
        R.id.widget_primary_sans,
        R.id.widget_primary_sans_shadow,
        R.id.widget_primary_mono,
        R.id.widget_primary_mono_shadow,
    )

    fun update(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        config: WidgetConfigEntity,
        type: WidgetType,
        content: WidgetContent,
    ) {
        if (type == WidgetType.SHORT_VERSE) {
            manager.updateAppWidget(appWidgetId, makeShortVerseViews(context, appWidgetId, config, content))
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.updateAppWidget(
                appWidgetId,
                RemoteViews(
                    mapOf(
                    SizeF(160f, 100f) to makeViews(context, appWidgetId, config, type, content, SizeBucket.SMALL),
                    SizeF(220f, 140f) to makeViews(context, appWidgetId, config, type, content, SizeBucket.MEDIUM),
                    SizeF(320f, 220f) to makeViews(context, appWidgetId, config, type, content, SizeBucket.LARGE),
                    ),
                ),
            )
        } else {
            val options = manager.getAppWidgetOptions(appWidgetId)
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            val bucket = when {
                height < 110 || width < 180 -> SizeBucket.SMALL
                height <= 180 || width <= 280 -> SizeBucket.MEDIUM
                else -> SizeBucket.LARGE
            }
            manager.updateAppWidget(appWidgetId, makeViews(context, appWidgetId, config, type, content, bucket))
        }
    }

    private fun makeShortVerseViews(
        context: Context,
        appWidgetId: Int,
        config: WidgetConfigEntity,
        content: WidgetContent,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_short_verse)
        val tintColor = resolveTintColor(context, config.bgTintMode)
        val textColor = resolveTextColor(config.textColorMode, tintColor)
        views.setInt(R.id.widget_background, "setImageAlpha", config.bgOpacityPercent.coerceIn(0, 100) * 255 / 100)
        views.setInt(R.id.widget_background, "setColorFilter", tintColor)
        views.setTextColor(R.id.widget_secondary, textColor)
        views.setTextViewText(R.id.widget_secondary, content.secondary)
        val bodyId = when (config.fontFamily.uppercase()) {
            "SANS", "SANS_SERIF" -> if (config.useDropShadow) R.id.widget_primary_sans_shadow else R.id.widget_primary_sans
            "MONOSPACE", "MONO" -> if (config.useDropShadow) R.id.widget_primary_mono_shadow else R.id.widget_primary_mono
            else -> if (config.useDropShadow) R.id.widget_primary_serif_shadow else R.id.widget_primary_serif
        }
        primaryIds.forEach { id ->
            views.setViewVisibility(id, if (id == bodyId) View.VISIBLE else View.GONE)
            views.setTextColor(id, textColor)
        }
        views.setTextViewText(bodyId, content.primary)
        views.setInt(bodyId, "setMaxLines", 2)
        val intent = Intent(context, MainActivity::class.java)
            .setAction("com.verbum.app.action.TODAY")
            .putExtra("destination", "TODAY")
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        val pendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
        return views
    }

    private fun makeViews(
        context: Context,
        appWidgetId: Int,
        config: WidgetConfigEntity,
        type: WidgetType,
        content: WidgetContent,
        size: SizeBucket,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_remote_content)
        val tintColor = resolveTintColor(context, config.bgTintMode)
        val textColor = resolveTextColor(config.textColorMode, tintColor)
        views.setTextViewText(R.id.widget_title, content.title)
        views.setTextViewText(R.id.widget_secondary, content.secondary)
        views.setInt(R.id.widget_background, "setImageAlpha", (config.bgOpacityPercent.coerceIn(0, 100) * 255 / 100))
        views.setInt(R.id.widget_background, "setColorFilter", tintColor)
        views.setTextColor(R.id.widget_title, textColor)
        views.setTextColor(R.id.widget_secondary, textColor)
        views.setViewVisibility(R.id.widget_liturgical_dot, if (type == WidgetType.LECTIONARY && size != SizeBucket.SMALL) View.VISIBLE else View.GONE)
        views.setInt(R.id.widget_liturgical_dot, "setColorFilter", liturgicalColor(LocalDate.now()))
        views.setViewVisibility(R.id.widget_secondary, if (size == SizeBucket.SMALL) View.GONE else View.VISIBLE)
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).roundToInt()
        if (size == SizeBucket.SMALL) {
            views.setViewVisibility(R.id.widget_title_row, View.GONE)
            views.setViewPadding(R.id.widget_root, dp(14), dp(8), dp(14), dp(8))
        } else {
            views.setViewVisibility(R.id.widget_title_row, View.VISIBLE)
            views.setViewPadding(R.id.widget_root, dp(if (size == SizeBucket.LARGE) 20 else 16), dp(14), dp(if (size == SizeBucket.LARGE) 20 else 16), dp(14))
        }

        val selectedBodyId = when (config.fontFamily.uppercase()) {
            "SANS", "SANS_SERIF" -> if (config.useDropShadow) R.id.widget_primary_sans_shadow else R.id.widget_primary_sans
            "MONOSPACE", "MONO" -> if (config.useDropShadow) R.id.widget_primary_mono_shadow else R.id.widget_primary_mono
            else -> if (config.useDropShadow) R.id.widget_primary_serif_shadow else R.id.widget_primary_serif
        }
        primaryIds.forEach { id ->
            views.setViewVisibility(id, if (id == selectedBodyId) View.VISIBLE else View.GONE)
            views.setTextColor(id, textColor)
        }
        views.setTextViewText(selectedBodyId, if (size == SizeBucket.SMALL) content.compactPrimary else content.primary)
        views.setInt(selectedBodyId, "setMaxLines", if (size == SizeBucket.SMALL) 2 else 6)

        val action = if (type == WidgetType.LECTIONARY) "LECTIONARY" else "TODAY"
        val intent = Intent(context, MainActivity::class.java)
            .setAction("com.verbum.app.action.$action")
            .putExtra("destination", action)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        val pendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
        return views
    }

    private fun resolveTextColor(mode: String, background: Int): Int {
        val requested = when (mode.uppercase()) {
            "BLACK" -> Color.BLACK
            "ACCENT", "MATERIAL_ACCENT" -> 0xFFFFD166.toInt()
            "AUTO", "DYNAMIC" -> if (luminance(background) > 0.45) Color.BLACK else Color.WHITE
            else -> Color.WHITE
        }
        if (contrastRatio(requested, background) >= 4.5) return requested
        return if (contrastRatio(Color.BLACK, background) >= contrastRatio(Color.WHITE, background)) Color.BLACK else Color.WHITE
    }

    private fun contrastRatio(foreground: Int, background: Int): Double {
        val first = luminance(foreground)
        val second = luminance(background)
        val lighter = maxOf(first, second)
        val darker = minOf(first, second)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @SuppressLint("NewApi")
    private fun resolveTintColor(context: Context, mode: String): Int = when (mode.uppercase()) {
        "LIGHT", "WHITE" -> Color.WHITE
        "DYNAMIC", "MATERIAL" -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                runCatching {
                    WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                        ?.let(::wallpaperPrimaryColor)
                }.getOrNull() ?: 0xFF546E7A.toInt()
            } else 0xFF546E7A.toInt()
        }
        "LITURGICAL" -> liturgicalColor(LocalDate.now())
        else -> Color.BLACK
    }

    private fun liturgicalColor(date: LocalDate): Int = when (LiturgicalCalendarEngine.calculate(date).color) {
        LiturgicalColor.GREEN -> 0xFF315B45.toInt()
        LiturgicalColor.VIOLET -> 0xFF62418A.toInt()
        LiturgicalColor.WHITE -> 0xFFD6B25E.toInt()
        LiturgicalColor.RED -> 0xFF8B1E2D.toInt()
        LiturgicalColor.ROSE -> 0xFFB85C77.toInt()
    }

    private fun luminance(color: Int): Double {
        fun linear(value: Int): Double {
            val channel = value / 255.0
            return if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).let { it * it * it }
        }
        return 0.2126 * linear(Color.red(color)) + 0.7152 * linear(Color.green(color)) + 0.0722 * linear(Color.blue(color))
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private fun wallpaperPrimaryColor(colors: WallpaperColors): Int = colors.primaryColor.toArgb()

    private enum class SizeBucket { SMALL, MEDIUM, LARGE }
}
