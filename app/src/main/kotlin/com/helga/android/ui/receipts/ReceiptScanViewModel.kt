package com.helga.android.ui.receipts

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.local.entity.ReceiptItemEntity
import com.helga.android.data.local.entity.StoreEntity
import com.helga.android.data.repository.ReceiptRepository
import com.helga.android.data.repository.StoreRepository
import com.helga.android.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed interface ReceiptScanUiState {
    data object Idle : ReceiptScanUiState
    data object Scanning : ReceiptScanUiState
    data class Preview(
        val storeName: String,
        val storeId: String,
        val totalAmount: Double,
        val purchaseDate: Long,
        val rawOcrText: String,
        val items: List<ReceiptItemEntity>,
    ) : ReceiptScanUiState
    data object Saved : ReceiptScanUiState
    data class Error(val message: String) : ReceiptScanUiState
}

@HiltViewModel
class ReceiptScanViewModel @Inject constructor(
    private val receiptRepository: ReceiptRepository,
    private val storeRepository: StoreRepository,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReceiptScanUiState>(ReceiptScanUiState.Idle)
    val uiState: StateFlow<ReceiptScanUiState> = _uiState

    val stores: StateFlow<List<StoreEntity>> = storeRepository.observeStores()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Während des Scans aufbewahrt, um beim Speichern den Original-Receipt zu behalten.
    private var scannedReceiptId: String = ""
    private var shoppingListId: String = ""

    fun setShoppingListId(listId: String?) {
        shoppingListId = listId.orEmpty()
    }

    fun scanBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.value = ReceiptScanUiState.Scanning
            try {
                val result = receiptRepository.scanReceipt(bitmap)
                scannedReceiptId = result.receipt.id
                _uiState.value = ReceiptScanUiState.Preview(
                    storeName = result.receipt.storeName,
                    storeId = result.receipt.storeId,
                    totalAmount = result.receipt.totalAmount,
                    purchaseDate = result.receipt.purchaseDate,
                    rawOcrText = result.receipt.rawOcrText,
                    items = result.items,
                )
            } catch (e: Exception) {
                Timber.e(e, "Receipt scan failed")
                _uiState.value = ReceiptScanUiState.Error("Kassenzettel konnte nicht gelesen werden")
            }
        }
    }

    fun updateStoreName(name: String) {
        val current = _uiState.value as? ReceiptScanUiState.Preview ?: return
        _uiState.value = current.copy(storeName = name)
    }

    fun selectStore(store: StoreEntity) {
        val current = _uiState.value as? ReceiptScanUiState.Preview ?: return
        _uiState.value = current.copy(storeId = store.id, storeName = store.name)
    }

    fun updateTotal(amount: Double) {
        val current = _uiState.value as? ReceiptScanUiState.Preview ?: return
        _uiState.value = current.copy(totalAmount = amount)
    }

    fun removeItem(item: ReceiptItemEntity) {
        val current = _uiState.value as? ReceiptScanUiState.Preview ?: return
        _uiState.value = current.copy(items = current.items - item)
    }

    fun save() {
        val current = _uiState.value as? ReceiptScanUiState.Preview ?: return
        viewModelScope.launch {
            try {
                val receipt = com.helga.android.data.local.entity.ReceiptEntity(
                    id = scannedReceiptId,
                    storeId = current.storeId,
                    storeName = current.storeName,
                    shoppingListId = shoppingListId,
                    purchaseDate = current.purchaseDate,
                    totalAmount = current.totalAmount,
                    currency = "EUR",
                    rawOcrText = current.rawOcrText,
                    status = "scanned",
                    updatedAt = System.currentTimeMillis(),
                    deleted = 0,
                    dirty = 1,
                )
                receiptRepository.saveReceipt(receipt, current.items)
                syncScheduler.triggerOneShot()
                _uiState.value = ReceiptScanUiState.Saved
            } catch (e: Exception) {
                Timber.e(e, "Receipt save failed")
                _uiState.value = ReceiptScanUiState.Error("Speichern fehlgeschlagen")
            }
        }
    }

    fun reset() {
        _uiState.value = ReceiptScanUiState.Idle
    }
}
