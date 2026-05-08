package com.helga.android.ui.pantry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.local.dao.PantryDao
import com.helga.android.data.local.entity.PantryItemEntity
import com.helga.android.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PantryViewModel @Inject constructor(
    private val pantryDao: PantryDao,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    val items: StateFlow<Map<String, List<PantryItemEntity>>> = pantryDao.observeAll()
        .map { list -> list.groupBy { it.category.ifBlank { "Sonstiges" } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun addItem(name: String, quantity: Double, unit: String, category: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            pantryDao.upsert(
                PantryItemEntity(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    quantity = quantity,
                    unit = unit.trim(),
                    category = category.trim(),
                    updatedAt = now,
                    dirty = 1,
                )
            )
            syncScheduler.triggerOneShot()
        }
    }

    fun deleteItem(item: PantryItemEntity) {
        viewModelScope.launch {
            pantryDao.upsert(item.copy(deleted = 1, updatedAt = System.currentTimeMillis(), dirty = 1))
            syncScheduler.triggerOneShot()
        }
    }

    fun updateItem(item: PantryItemEntity) {
        viewModelScope.launch {
            pantryDao.upsert(item.copy(updatedAt = System.currentTimeMillis(), dirty = 1))
            syncScheduler.triggerOneShot()
        }
    }
}
