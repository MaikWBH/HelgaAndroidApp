package com.helga.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.helga.android.data.preferences.AppPreferences
import com.helga.android.ui.theme.HelgaTheme
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
            HelgaTheme {
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
