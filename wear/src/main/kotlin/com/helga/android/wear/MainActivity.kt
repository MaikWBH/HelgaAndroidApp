package com.helga.android.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val repository: WearShoppingRepository
        get() = (application as WearApp).shoppingRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by repository.state.collectAsState()
            LaunchedEffect(Unit) { repository.refresh() }
            WearShoppingScreen(
                state = state,
                onToggle = { itemId -> lifecycleScope.launch { repository.toggleItem(itemId) } },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        repository.start()
    }

    override fun onStop() {
        repository.stop()
        super.onStop()
    }
}
