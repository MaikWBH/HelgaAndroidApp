package com.helga.android.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Index des Picker-Eintrags „System (Wallpaper)" (Dynamic Color); ab Android 12 wirksam. */
const val ACCENT_SYSTEM = -1

internal data class AccentTones(
    val main: Color,
    val on: Color,
    val container: Color,
    val onContainer: Color,
)

// --- Hell ---
internal val GreenLight = AccentTones(Color(0xFF2E7D32), Color(0xFFFFFFFF), Color(0xFFC8E6C9), Color(0xFF1B5E20))
internal val BlueLight = AccentTones(Color(0xFF1565C0), Color(0xFFFFFFFF), Color(0xFFBBDEFB), Color(0xFF0D47A1))
internal val PurpleLight = AccentTones(Color(0xFF6A1B9A), Color(0xFFFFFFFF), Color(0xFFE1BEE7), Color(0xFF4A148C))
internal val PurpleBrightLight = AccentTones(Color(0xFF8E24AA), Color(0xFFFFFFFF), Color(0xFFE1BEE7), Color(0xFF4A148C))
internal val OrangeLight = AccentTones(Color(0xFFE65100), Color(0xFFFFFFFF), Color(0xFFFFE0B2), Color(0xFF7A2E00))
internal val RedLight = AccentTones(Color(0xFFB71C1C), Color(0xFFFFFFFF), Color(0xFFFFCDD2), Color(0xFF7F1010))
internal val TealLight = AccentTones(Color(0xFF00695C), Color(0xFFFFFFFF), Color(0xFFB2DFDB), Color(0xFF004D40))
internal val AmberLight = AccentTones(Color(0xFFFF8F00), Color(0xFF000000), Color(0xFFFFECB3), Color(0xFF5C3A00))
internal val CyanLight = AccentTones(Color(0xFF00838F), Color(0xFFFFFFFF), Color(0xFFB2EBF2), Color(0xFF006064))

// --- Dunkel ---
internal val GreenDark = AccentTones(Color(0xFF81C784), Color(0xFF0A3D0E), Color(0xFF1B5E20), Color(0xFFC8E6C9))
internal val BlueDark = AccentTones(Color(0xFF90CAF9), Color(0xFF0A3059), Color(0xFF0D47A1), Color(0xFFBBDEFB))
internal val PurpleDark = AccentTones(Color(0xFFCE93D8), Color(0xFF3A0F6B), Color(0xFF4A148C), Color(0xFFE1BEE7))
internal val PurpleBrightDark = PurpleDark
internal val OrangeDark = AccentTones(Color(0xFFFFCC80), Color(0xFF4A2800), Color(0xFF7A3E00), Color(0xFFFFE0B2))
internal val RedDark = AccentTones(Color(0xFFEF9A9A), Color(0xFF5F0E0E), Color(0xFF7F1515), Color(0xFFFFCDD2))
internal val TealDark = AccentTones(Color(0xFF80CBC4), Color(0xFF00332B), Color(0xFF004D40), Color(0xFFB2DFDB))
internal val AmberDark = AccentTones(Color(0xFFFFB74D), Color(0xFF4A2800), Color(0xFF7A4A00), Color(0xFFFFECB3))
internal val CyanDark = AccentTones(Color(0xFF80DEEA), Color(0xFF00363B), Color(0xFF006064), Color(0xFFB2EBF2))

// Exposed for the Settings color picker circles
val accentPrimaryColors: List<Color> = listOf(
    Color(0xFF2E7D32),
    Color(0xFF1565C0),
    Color(0xFF6A1B9A),
    Color(0xFFE65100),
    Color(0xFFB71C1C),
    Color(0xFF00695C),
)

internal fun helgaLightScheme(
    p: AccentTones,
    s: AccentTones,
    t: AccentTones,
    inversePrimary: Color,
): ColorScheme = lightColorScheme(
    primary = p.main,
    onPrimary = p.on,
    primaryContainer = p.container,
    onPrimaryContainer = p.onContainer,
    secondary = s.main,
    onSecondary = s.on,
    secondaryContainer = s.container,
    onSecondaryContainer = s.onContainer,
    tertiary = t.main,
    onTertiary = t.on,
    tertiaryContainer = t.container,
    onTertiaryContainer = t.onContainer,
    background = Color(0xFFFCFCFC),
    onBackground = Color(0xFF1B1C1B),
    surface = Color(0xFFFCFCFC),
    onSurface = Color(0xFF1B1C1B),
    surfaceVariant = Color(0xFFE1E3DF),
    onSurfaceVariant = Color(0xFF444744),
    surfaceTint = p.main,
    inverseSurface = Color(0xFF303130),
    inverseOnSurface = Color(0xFFF2F1EF),
    inversePrimary = inversePrimary,
    outline = Color(0xFF747874),
    outlineVariant = Color(0xFFC4C7C2),
    scrim = Color(0xFF000000),
    surfaceDim = Color(0xFFDCDCDA),
    surfaceBright = Color(0xFFFCFCFC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F6F4),
    surfaceContainer = Color(0xFFF0F0EE),
    surfaceContainerHigh = Color(0xFFEAEAE8),
    surfaceContainerHighest = Color(0xFFE4E4E2),
)

internal fun helgaDarkScheme(
    p: AccentTones,
    s: AccentTones,
    t: AccentTones,
    inversePrimary: Color,
): ColorScheme = darkColorScheme(
    primary = p.main,
    onPrimary = p.on,
    primaryContainer = p.container,
    onPrimaryContainer = p.onContainer,
    secondary = s.main,
    onSecondary = s.on,
    secondaryContainer = s.container,
    onSecondaryContainer = s.onContainer,
    tertiary = t.main,
    onTertiary = t.on,
    tertiaryContainer = t.container,
    onTertiaryContainer = t.onContainer,
    background = Color(0xFF121312),
    onBackground = Color(0xFFE3E3E0),
    surface = Color(0xFF121312),
    onSurface = Color(0xFFE3E3E0),
    surfaceVariant = Color(0xFF444744),
    onSurfaceVariant = Color(0xFFC4C7C2),
    surfaceTint = p.main,
    inverseSurface = Color(0xFFE3E3E0),
    inverseOnSurface = Color(0xFF303130),
    inversePrimary = inversePrimary,
    outline = Color(0xFF8E918D),
    outlineVariant = Color(0xFF444744),
    scrim = Color(0xFF000000),
    surfaceDim = Color(0xFF121312),
    surfaceBright = Color(0xFF383938),
    surfaceContainerLowest = Color(0xFF0D0E0D),
    surfaceContainerLow = Color(0xFF1B1C1B),
    surfaceContainer = Color(0xFF1F201F),
    surfaceContainerHigh = Color(0xFF292A29),
    surfaceContainerHighest = Color(0xFF343534),
)
