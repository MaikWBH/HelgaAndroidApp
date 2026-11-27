package com.helga.android.ui.components

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.helga.android.ui.theme.HelgaTheme

@Preview(name = "Hell", showBackground = true)
@Preview(name = "Dunkel", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class ThemePreviews

@Composable
fun PreviewSurface(content: @Composable () -> Unit) {
    HelgaTheme(accentColorIndex = 0) {
        Surface { content() }
    }
}
