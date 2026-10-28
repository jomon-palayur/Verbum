package com.verbum.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import android.os.Build

private val VerbumColors = lightColorScheme(
    primary = Color(0xFF315B4B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCEBE1),
    onPrimaryContainer = Color(0xFF10271D),
    secondary = Color(0xFF735C3F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEFE4D1),
    onSecondaryContainer = Color(0xFF281B0D),
    tertiary = Color(0xFF8A5338),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F6F1),
    onBackground = Color(0xFF1B1C19),
    surface = Color(0xFFFFFEFA),
    onSurface = Color(0xFF1B1C19),
    surfaceVariant = Color(0xFFEAE8E0),
    onSurfaceVariant = Color(0xFF494A44),
    outline = Color(0xFF77786F),
)

private val VerbumDarkColors = darkColorScheme(
    primary = Color(0xFF9BCAB0),
    onPrimary = Color(0xFF17382A),
    primaryContainer = Color(0xFF294C3C),
    onPrimaryContainer = Color(0xFFD6F0DF),
    secondary = Color(0xFFD4BC95),
    onSecondary = Color(0xFF382A18),
    secondaryContainer = Color(0xFF50412D),
    onSecondaryContainer = Color(0xFFF2E2C7),
    tertiary = Color(0xFFE2B39A),
    onTertiary = Color(0xFF482A1D),
    background = Color(0xFF111411),
    onBackground = Color(0xFFE2E4DE),
    surface = Color(0xFF191D19),
    onSurface = Color(0xFFE2E4DE),
    surfaceVariant = Color(0xFF3C433D),
    onSurfaceVariant = Color(0xFFC1C9C0),
    outline = Color(0xFF8B958A),
)

@Composable
fun VerbumTheme(themeMode: String = "system", content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = when (themeMode.lowercase()) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) VerbumDarkColors else VerbumColors
    MaterialTheme(colorScheme = scheme, content = content)
}
