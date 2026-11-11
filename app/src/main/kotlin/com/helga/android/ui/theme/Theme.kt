package com.helga.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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
private fun accent(
    pl: AccentTones, sl: AccentTones, tl: AccentTones,
    pd: AccentTones, sd: AccentTones, td: AccentTones,
): Pair<ColorScheme, ColorScheme> = Pair(
    helgaLightScheme(pl, sl, tl, inversePrimary = pd.main),
    helgaDarkScheme(pd, sd, td, inversePrimary = pl.main),
)

private val accentSchemes: List<Pair<ColorScheme, ColorScheme>> = listOf(
    accent(GreenLight, AmberLight, PurpleBrightLight, GreenDark, AmberDark, PurpleBrightDark),
    accent(BlueLight, CyanLight, PurpleLight, BlueDark, CyanDark, PurpleDark),
    accent(PurpleLight, BlueLight, TealLight, PurpleDark, BlueDark, TealDark),
    accent(OrangeLight, GreenLight, BlueLight, OrangeDark, GreenDark, BlueDark),
    accent(RedLight, OrangeLight, BlueLight, RedDark, OrangeDark, BlueDark),
    accent(TealLight, BlueLight, PurpleLight, TealDark, BlueDark, PurpleDark),
)

@Composable
fun HelgaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentColorIndex: Int = ACCENT_SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        accentColorIndex == ACCENT_SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
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
