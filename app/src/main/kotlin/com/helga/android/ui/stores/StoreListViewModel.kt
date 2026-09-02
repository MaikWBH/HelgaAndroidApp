package com.helga.android.ui.stores

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.local.entity.StoreAisleEntity
import com.helga.android.data.local.entity.StoreEntity
import com.helga.android.data.repository.StorePrefill
import com.helga.android.data.repository.StoreRepository
import com.helga.android.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StoreListViewModel @Inject constructor(
    private val repository: StoreRepository,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    val stores: StateFlow<List<StoreEntity>> = repository.observeStores()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedStoreId = MutableStateFlow<String?>(null)

    val selectedStoreAisles: StateFlow<List<StoreAisleEntity>> = _selectedStoreId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repository.observeAisles(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectStore(storeId: String?) {
        _selectedStoreId.value = storeId
    }

    fun createStore(name: String, prefill: StorePrefill = StorePrefill.None) {
        viewModelScope.launch {
            repository.createStore(name, prefill)
            syncScheduler.triggerOneShot()
        }
    }

    fun setActiveStore(storeId: String) {
        viewModelScope.launch {
            repository.setActiveStore(storeId)
            syncScheduler.triggerOneShot()
        }
    }

    fun deleteStore(store: StoreEntity) {
        viewModelScope.launch {
            repository.deleteStore(store)
            syncScheduler.triggerOneShot()
        }
    }

    fun addAisle(aisleName: String) {
        val storeId = _selectedStoreId.value ?: return
        viewModelScope.launch {
            repository.addAisle(storeId, aisleName)
            syncScheduler.triggerOneShot()
        }
    }

    fun deleteAisle(aisle: StoreAisleEntity) {
        viewModelScope.launch {
            repository.deleteAisle(aisle)
            syncScheduler.triggerOneShot()
        }
    }

    fun reorderAisles(orderedIds: List<String>) {
        val storeId = _selectedStoreId.value ?: return
        viewModelScope.launch {
            repository.reorderAisles(storeId, orderedIds)
            syncScheduler.triggerOneShot()
        }
    }
}
