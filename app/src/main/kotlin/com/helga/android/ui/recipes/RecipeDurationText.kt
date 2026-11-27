package com.helga.android.ui.recipes

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.helga.android.R
import com.helga.android.data.util.RecipeDuration

/** Dauer lesbar („30 Min“, „1 Std 30 Min“); leer bleibt leer, Nicht-Erkanntes wird unverändert gezeigt (Phase 7, F19). */
@Composable
fun formatRecipeDuration(raw: String): String {
    if (raw.isBlank()) return ""
    val total = RecipeDuration.parseMinutes(raw) ?: return raw
    val h = total / 60
    val m = total % 60
    return when {
        h == 0 -> stringResource(R.string.duration_minutes, m)
        m == 0 -> stringResource(R.string.duration_hours, h)
        else -> stringResource(R.string.duration_hours_minutes, h, m)
    }
}
