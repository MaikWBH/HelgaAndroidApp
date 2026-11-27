package com.helga.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Serienfarben der Diagramme — ausschließlich aus dem Material-Farbschema (folgt Akzent, Hell/Dunkel, Dynamic Color). */
@Composable
fun chartSeriesColors(): List<Color> = with(MaterialTheme.colorScheme) {
    listOf(primary, tertiary, secondary, error, inversePrimary)
}

/** Farbe für „Sonstige" im Ringdiagramm. */
@Composable
fun chartOtherColor(): Color = MaterialTheme.colorScheme.outline
