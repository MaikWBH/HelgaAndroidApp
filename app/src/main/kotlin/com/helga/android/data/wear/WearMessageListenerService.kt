package com.helga.android.data.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.helga.android.data.local.dao.ShoppingDao
import com.helga.android.data.repository.ShoppingRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val TOGGLE_ITEM_PATH = "/toggle_item"

/**
 * Empfängt Abhak-Nachrichten von der Wear-App (Gegenstück zu [WearShoppingRepository] im
 * :wear-Modul — dessen `toggleItem()` sendet genau hierher). Vom System per Manifest-Intent-Filter
 * gestartet, auch wenn die Handy-App gerade nicht läuft.
 */
@AndroidEntryPoint
class WearMessageListenerService : WearableListenerService() {

    @Inject lateinit var shoppingDao: ShoppingDao
    @Inject lateinit var shoppingRepository: ShoppingRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != TOGGLE_ITEM_PATH) return
        val itemId = String(event.data, Charsets.UTF_8)
        scope.launch {
            val item = shoppingDao.findItemById(itemId)
            if (item == null) {
                Timber.w("Wear-Sync: Item $itemId zum Abhaken nicht gefunden")
                return@launch
            }
            shoppingRepository.toggleChecked(item)
        }
    }
}
