package com.helga.android.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.local.dao.QuickEmojiDao
import com.helga.android.data.local.entity.QuickEmojiEntity
import com.helga.android.data.local.entity.ShoppingItemEntity
import com.helga.android.data.local.entity.ShoppingListEntity
import com.helga.android.data.local.entity.ShoppingListStapleEntity
import com.helga.android.data.local.entity.StoreAisleEntity
import com.helga.android.data.local.entity.StoreEntity
import com.helga.android.data.remote.SyncApiFactory
import com.helga.android.data.repository.ShoppingRepository
import com.helga.android.data.repository.StoreRepository
import com.helga.android.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    private val repository: ShoppingRepository,
    private val storeRepository: StoreRepository,
    private val quickEmojiDao: QuickEmojiDao,
    private val apiFactory: SyncApiFactory,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    private val _activeListId = MutableStateFlow<String?>(null)

    val lists: StateFlow<List<ShoppingListEntity>> = repository.observeLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeListId: StateFlow<String?> = combine(lists, _activeListId) { lists, selected ->
        selected ?: lists.firstOrNull()?.id
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val itemsByAisle: StateFlow<Map<String, List<ShoppingItemEntity>>> = activeListId
        .flatMapLatest { listId ->
            if (listId == null) flowOf(emptyMap())
            else repository.observeItemsByList(listId).map { items ->
                items.groupBy { it.aisle }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val activeStore: StateFlow<StoreEntity?> = storeRepository.observeActiveStore()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val storeAisles: StateFlow<List<StoreAisleEntity>> = activeStore
        .flatMapLatest { store ->
            if (store == null) flowOf(emptyList())
            else storeRepository.observeAisles(store.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val aisleSortMap: StateFlow<Map<String, Int>> = storeAisles
        .map { aisles -> aisles.associate { it.aisleName to it.sortOrder } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val quickEmojis: StateFlow<List<QuickEmojiEntity>> = quickEmojiDao.observeEmojis()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val staples: StateFlow<List<ShoppingListStapleEntity>> = activeListId
        .flatMapLatest { listId ->
            if (listId == null) flowOf(emptyList())
            else storeRepository.observeStaples(listId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectList(id: String) {
        _activeListId.value = id
    }

    fun createList(name: String) {
        viewModelScope.launch {
            val id = repository.createList(name)
            _activeListId.value = id
            syncScheduler.triggerOneShot()
        }
    }

    fun addItem(name: String) {
        val listId = activeListId.value ?: return
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isBlank()) return@launch
            val storeId = activeStore.value?.id
            val aisle = if (storeId != null)
                storeRepository.findAisleForProduct(trimmed, storeId) ?: ""
            else ""
            repository.addItem(listId = listId, name = trimmed, aisle = aisle)
            syncScheduler.triggerOneShot()
        }
    }

    fun addEmojiItem(emoji: QuickEmojiEntity) {
        val listId = activeListId.value ?: return
        viewModelScope.launch {
            val storeId = activeStore.value?.id
            val aisle = if (storeId != null)
                storeRepository.findAisleForProduct(emoji.food, storeId) ?: ""
            else ""
            repository.addItem(
                listId = listId,
                name = emoji.food,
                quantity = emoji.quantity,
                unit = emoji.unit,
                aisle = aisle,
            )
            syncScheduler.triggerOneShot()
        }
    }

    fun assignAisle(item: ShoppingItemEntity, aisleName: String) {
        viewModelScope.launch {
            repository.updateItemAisle(item, aisleName)
            val storeId = activeStore.value?.id
            if (storeId != null) {
                storeRepository.saveAisleProduct(item.name, aisleName, storeId)
            }
            syncScheduler.triggerOneShot()
        }
    }

    fun addStaplesToList() {
        val listId = activeListId.value ?: return
        viewModelScope.launch {
            val stapleList = storeRepository.staplesForList(listId)
            if (stapleList.isEmpty()) return@launch
            val storeId = activeStore.value?.id
            stapleList.forEach { staple ->
                val aisle = if (storeId != null)
                    storeRepository.findAisleForProduct(staple.name, storeId) ?: ""
                else ""
                repository.addItem(
                    listId = listId,
                    name = staple.name,
                    quantity = staple.quantity,
                    aisle = aisle,
                )
            }
            syncScheduler.triggerOneShot()
        }
    }

    fun addStaple(name: String) {
        val listId = activeListId.value ?: return
        viewModelScope.launch {
            storeRepository.addStaple(listId, name)
            syncScheduler.triggerOneShot()
        }
    }

    fun deleteStaple(staple: ShoppingListStapleEntity) {
        viewModelScope.launch {
            storeRepository.deleteStaple(staple)
            syncScheduler.triggerOneShot()
        }
    }

    fun toggleChecked(item: ShoppingItemEntity) {
        viewModelScope.launch {
            repository.toggleChecked(item)
            syncScheduler.triggerOneShot()
        }
    }

    fun deleteItem(item: ShoppingItemEntity) {
        viewModelScope.launch {
            repository.softDeleteItem(item)
            syncScheduler.triggerOneShot()
        }
    }

    fun deleteCheckedItems() {
        val listId = activeListId.value ?: return
        viewModelScope.launch {
            repository.deleteCheckedItems(listId)
            syncScheduler.triggerOneShot()
        }
    }

    suspend fun suggestItems(query: String): List<String> {
        if (query.length < 2) return emptyList()
        return try {
            apiFactory.api().suggestItems(query).suggestions
        } catch (e: Exception) {
            emptyList()
        }
    }
}
