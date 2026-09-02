package com.helga.android.data.wear

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.helga.android.data.local.dao.ShoppingDao
import com.helga.android.data.local.entity.ShoppingItemEntity
import com.helga.android.data.preferences.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schiebt die aktive Einkaufsliste (dieselbe, die [com.helga.android.ui.shopping.ShoppingListViewModel]
 * als `activeListId` zeigt) bei jeder Änderung als [com.google.android.gms.wearable.DataItem] zur
 * gepaarten Uhr (plattform A3 / einkaufsliste A7). Gegenstück: `WearShoppingRepository` im
 * :wear-Modul liest denselben Pfad. Läuft app-weit wie [NetworkObserver]/[ForegroundSyncObserver],
 * kein WorkManager nötig — DataClient hält den zuletzt gepushten Stand ohnehin persistent vor,
 * solange der Prozess mindestens einmal nach jeder Änderung kurz gelaufen ist.
 */
@Singleton
class WearSyncPublisher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shoppingDao: ShoppingDao,
    private val preferences: AppPreferences,
) {
    private val dataClient by lazy { Wearable.getDataClient(context) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    fun start() {
        if (started) return
        started = true
        activeListItems()
            .onEach { (listName, items) -> publish(listName, items) }
            .launchIn(scope)
    }

    private fun activeListItems(): Flow<Pair<String, List<ShoppingItemEntity>>> =
        preferences.defaultShoppingListId
            .flatMapLatest { defaultId ->
                shoppingDao.observeLists().map { lists ->
                    lists.firstOrNull { it.id == defaultId } ?: lists.firstOrNull()
                }
            }
            .distinctUntilChanged()
            .flatMapLatest { list ->
                if (list == null) flowOf("" to emptyList())
                else shoppingDao.observeItemsByList(list.id).map { list.name to it }
            }

    private suspend fun publish(listName: String, items: List<ShoppingItemEntity>) {
        val itemsJson = JSONArray()
        items.forEach { item ->
            itemsJson.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("quantity", item.quantity)
                    put("unit", item.unit)
                    put("aisle", item.aisle)
                    put("checked", item.isChecked)
                }
            )
        }
        val request = PutDataMapRequest.create("/shopping_list").apply {
            dataMap.putString("listName", listName)
            dataMap.putString("itemsJson", itemsJson.toString())
            dataMap.putLong("updatedAt", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        runCatching { dataClient.putDataItem(request).await() }
            .onFailure { Timber.w(it, "Wear-Sync: Einkaufsliste konnte nicht gepusht werden") }
    }
}
