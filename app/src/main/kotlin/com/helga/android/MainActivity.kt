package com.helga.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.helga.android.data.preferences.AppPreferences
import com.helga.android.ui.theme.HelgaTheme
import com.helga.android.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var preferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sharedUrl = resolveSharedUrl(intent)
        setContent {
            val themeModeStr by preferences.themeMode.collectAsState(initial = "system")
            val accentColor by preferences.accentColor.collectAsState(initial = 0)
            HelgaTheme(
                themeMode = ThemeMode.fromString(themeModeStr),
                accentColorIndex = accentColor,
            ) {
                HelgaNavGraph(preferences = preferences, initialImportUrl = sharedUrl)
            }
        }
    }

    private fun resolveSharedUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (!intent.type.orEmpty().startsWith("text/")) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        return if (text.startsWith("http://") || text.startsWith("https://")) text else null
    }
}
