package com.helga.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    companion object {
        fun fromString(s: String) = when (s) {
            "light" -> LIGHT
            "dark" -> DARK
            else -> SYSTEM
        }
    }
}

// Accent color presets — Pair(lightScheme, darkScheme)
// Index 0 = Grün, 1 = Blau, 2 = Lila, 3 = Orange, 4 = Rot, 5 = Petrol
private val accentSchemes: List<Pair<ColorScheme, ColorScheme>> = listOf(
    Pair(
        lightColorScheme(primary = Color(0xFF2E7D32), secondary = Color(0xFFFF8F00), tertiary = Color(0xFF8E24AA)),
        darkColorScheme(primary = Color(0xFF81C784), secondary = Color(0xFFFFB74D), tertiary = Color(0xFFCE93D8)),
    ),
    Pair(
        lightColorScheme(primary = Color(0xFF1565C0), secondary = Color(0xFF00838F), tertiary = Color(0xFF6A1B9A)),
        darkColorScheme(primary = Color(0xFF90CAF9), secondary = Color(0xFF80DEEA), tertiary = Color(0xFFCE93D8)),
    ),
    Pair(
        lightColorScheme(primary = Color(0xFF6A1B9A), secondary = Color(0xFF1565C0), tertiary = Color(0xFF00695C)),
        darkColorScheme(primary = Color(0xFFCE93D8), secondary = Color(0xFF90CAF9), tertiary = Color(0xFF80CBC4)),
    ),
    Pair(
        lightColorScheme(primary = Color(0xFFE65100), secondary = Color(0xFF2E7D32), tertiary = Color(0xFF1565C0)),
        darkColorScheme(primary = Color(0xFFFFCC80), secondary = Color(0xFF81C784), tertiary = Color(0xFF90CAF9)),
    ),
    Pair(
        lightColorScheme(primary = Color(0xFFB71C1C), secondary = Color(0xFFE65100), tertiary = Color(0xFF1565C0)),
        darkColorScheme(primary = Color(0xFFEF9A9A), secondary = Color(0xFFFFCC80), tertiary = Color(0xFF90CAF9)),
    ),
    Pair(
        lightColorScheme(primary = Color(0xFF00695C), secondary = Color(0xFF1565C0), tertiary = Color(0xFF6A1B9A)),
        darkColorScheme(primary = Color(0xFF80CBC4), secondary = Color(0xFF90CAF9), tertiary = Color(0xFFCE93D8)),
    ),
)

// Exposed for the Settings color picker circles
val accentPrimaryColors: List<Color> = listOf(
    Color(0xFF2E7D32),
    Color(0xFF1565C0),
    Color(0xFF6A1B9A),
    Color(0xFFE65100),
    Color(0xFFB71C1C),
    Color(0xFF00695C),
)

@Composable
fun HelgaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentColorIndex: Int = 0,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        accentColorIndex == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        else -> {
            val idx = accentColorIndex.coerceIn(0, accentSchemes.lastIndex)
            if (darkTheme) accentSchemes[idx].second else accentSchemes[idx].first
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
