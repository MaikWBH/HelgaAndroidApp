package com.helga.android.wear

import android.content.Context
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await
import org.json.JSONArray

data class WearShoppingItem(
    val id: String,
    val name: String,
    val quantity: Double,
    val unit: String,
    val aisle: String,
    val checked: Boolean,
)

data class WearShoppingListState(
    val listName: String = "",
    val items: List<WearShoppingItem> = emptyList(),
    val connected: Boolean = false,
)

/**
 * Data-Layer-Brücke zur Handy-App (plattform A3 / einkaufsliste A7). Die Uhr hat keine eigene
 * Room-DB/Server-Anbindung — Listeninhalt kommt als [DataItem] unter [SHOPPING_LIST_PATH] von
 * `WearSyncPublisher` (app-Modul), Abhaken geht als [MessageClient]-Nachricht unter
 * [TOGGLE_ITEM_PATH] an `WearMessageListenerService` (app-Modul) zurück. Pfade müssen auf beiden
 * Seiten übereinstimmen — es gibt kein gemeinsames Modul für die zwei Konstanten.
 */
class WearShoppingRepository(context: Context) : DataClient.OnDataChangedListener {

    private val appContext = context.applicationContext
    private val dataClient = Wearable.getDataClient(appContext)
    private val messageClient = Wearable.getMessageClient(appContext)
    private val nodeClient = Wearable.getNodeClient(appContext)

    private val _state = MutableStateFlow(WearShoppingListState())
    val state: StateFlow<WearShoppingListState> = _state.asStateFlow()

    fun start() {
        dataClient.addListener(this)
    }

    fun stop() {
        dataClient.removeListener(this)
    }

    /** Initialer Ladevorgang — [onDataChanged] deckt nur spätere Änderungen ab. */
    suspend fun refresh() {
        val connectedNodes = runCatching { nodeClient.connectedNodes.await() }.getOrDefault(emptyList())
        _state.update { it.copy(connected = connectedNodes.isNotEmpty()) }

        val dataItems = runCatching { dataClient.dataItems.await() }.getOrNull() ?: return
        try {
            dataItems.forEach { item -> if (item.uri.path == SHOPPING_LIST_PATH) applyDataItem(item) }
        } finally {
            dataItems.release()
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        try {
            dataEvents.forEach { event ->
                if (event.dataItem.uri.path == SHOPPING_LIST_PATH) applyDataItem(event.dataItem)
            }
        } finally {
            dataEvents.release()
        }
    }

    private fun applyDataItem(dataItem: DataItem) {
        val map = DataMapItem.fromDataItem(dataItem).dataMap
        val listName = map.getString("listName") ?: ""
        val itemsJson = map.getString("itemsJson") ?: "[]"
        _state.update { it.copy(listName = listName, items = parseItems(itemsJson), connected = true) }
    }

    private fun parseItems(json: String): List<WearShoppingItem> {
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            WearShoppingItem(
                id = obj.optString("id"),
                name = obj.optString("name"),
                quantity = obj.optDouble("quantity", 0.0),
                unit = obj.optString("unit"),
                aisle = obj.optString("aisle"),
                checked = obj.optInt("checked", 0) == 1,
            )
        }
    }

    /** Optimistisches lokales Toggle + Nachricht ans Handy; die Bestätigung kommt als neuer DataItem-Push zurück. */
    suspend fun toggleItem(itemId: String) {
        _state.update { state ->
            state.copy(items = state.items.map { if (it.id == itemId) it.copy(checked = !it.checked) else it })
        }
        val nodes = runCatching { nodeClient.connectedNodes.await() }.getOrDefault(emptyList())
        val payload = itemId.toByteArray(Charsets.UTF_8)
        nodes.forEach { node ->
            runCatching { messageClient.sendMessage(node.id, TOGGLE_ITEM_PATH, payload).await() }
        }
    }

    companion object {
        const val SHOPPING_LIST_PATH = "/shopping_list"
        const val TOGGLE_ITEM_PATH = "/toggle_item"
    }
}
