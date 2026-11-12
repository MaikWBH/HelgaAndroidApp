package com.helga.android.ui.receipts

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.R
import com.helga.android.data.local.ScanSource
import com.helga.android.data.local.entity.ReceiptEntity
import com.helga.android.data.local.entity.ReceiptItemEntity
import com.helga.android.data.local.entity.StoreEntity
import com.helga.android.data.local.toScanSource
import com.helga.android.data.receipts.ReceiptDates
import com.helga.android.data.receipts.ReceiptReviewCheck
import com.helga.android.data.receipts.ReceiptScanOutcome
import com.helga.android.data.remote.dto.OffProductDto
import com.helga.android.data.repository.ReceiptRepository
import com.helga.android.data.repository.StoreRepository
import com.helga.android.data.sync.SyncScheduler
import com.helga.android.ui.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed interface ReceiptScanUiState {
    data object Idle : ReceiptScanUiState
    data object Scanning : ReceiptScanUiState
    data class Preview(
        val receiptId: String,
        val storeName: String,
        val storeId: String,
        val storeAddress: String,
        val purchaseDay: String,
        val purchaseTime: String,
        val totalAmount: Double,
        val discountTotal: Double,
        val purchaseDate: Long,
        val rawOcrText: String,
        val items: List<ReceiptItemEntity>,
        val lowConfidenceItemIds: Set<String> = emptySet(),
        val needsReview: Boolean = false,
        val source: ScanSource = ScanSource.ON_DEVICE,
    ) : ReceiptScanUiState
    /** Nur vorläufige Werte; [queuedOnServer] = Server wertet später aus, sonst wartet das Foto lokal auf den Upload. */
    data class Provisional(val receiptId: String, val queuedOnServer: Boolean) : ReceiptScanUiState
    data object Saved : ReceiptScanUiState
    data class Error(val message: UiText) : ReceiptScanUiState
}

@HiltViewModel
class ReceiptScanViewModel @Inject constructor(
    private val receiptRepository: ReceiptRepository,
    private val storeRepository: StoreRepository,
    private val syncScheduler: SyncScheduler,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReceiptScanUiState>(ReceiptScanUiState.Idle)
    val uiState: StateFlow<ReceiptScanUiState> = _uiState

    val stores: StateFlow<List<StoreEntity>> = storeRepository.observeStores()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _suggestions = MutableStateFlow<List<OffProductDto>>(emptyList())
    val suggestions: StateFlow<List<OffProductDto>> = _suggestions.asStateFlow()
    private val _suggestionsLoading = MutableStateFlow(false)
    val suggestionsLoading: StateFlow<Boolean> = _suggestionsLoading.asStateFlow()

    private var shoppingListId: String = savedStateHandle.get<String>("listId").orEmpty()
    // Der Bon, wie er geladen wurde — beim Bestätigen behalten wir daraus alles, was der Nutzer nicht bearbeitet.
    private var baseReceipt: ReceiptEntity? = null
    // Ursprüngliche Artikelnamen: ein übernommenes Produkt überschreibt nur Namen, die der Nutzer nicht selbst geändert hat.
    private var originalNames: Map<String, String> = emptyMap()

    init {
        savedStateHandle.get<String>("reviewId")?.takeIf { it.isNotBlank() }?.let(::loadForReview)
    }

    fun setShoppingListId(listId: String?) {
        shoppingListId = listId.orEmpty()
    }

    fun scanImage(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = ReceiptScanUiState.Scanning
            try {
                when (val outcome = receiptRepository.scanFromUri(uri, shoppingListId)) {
                    is ReceiptScanOutcome.Parsed -> {
                        syncScheduler.triggerOneShot()
                        loadForReview(outcome.receiptId)
                    }
                    is ReceiptScanOutcome.Provisional -> {
                        _uiState.value = ReceiptScanUiState.Provisional(outcome.receiptId, outcome.queuedOnServer)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Receipt scan failed")
                _uiState.value = ReceiptScanUiState.Error(UiText.Res(R.string.receipt_scan_read_failed))
            }
        }
    }

    /** Lädt einen gespeicherten Bon (frisch gescannt oder zum erneuten Prüfen) in die Review-Ansicht. */
    fun loadForReview(receiptId: String) {
        viewModelScope.launch {
            _uiState.value = ReceiptScanUiState.Scanning
            try {
                val receipt = receiptRepository.findReceipt(receiptId)
                if (receipt == null) {
                    _uiState.value = ReceiptScanUiState.Error(UiText.Res(R.string.receipt_scan_read_failed))
                    return@launch
                }
                val items = receiptRepository.observeReceiptItems(receiptId).first()
                baseReceipt = receipt
                originalNames = items.associate { it.id to it.name }
                if (shoppingListId.isBlank()) shoppingListId = receipt.shoppingListId
                _uiState.value = ReceiptScanUiState.Preview(
                    receiptId = receipt.id,
                    storeName = receipt.storeName,
                    storeId = receipt.storeId,
                    storeAddress = receipt.storeAddress,
                    purchaseDay = receipt.purchaseDay,
                    purchaseTime = receipt.purchaseTime,
                    totalAmount = receipt.totalAmount,
                    discountTotal = receipt.discountTotal,
                    purchaseDate = receipt.purchaseDate,
                    rawOcrText = receipt.rawOcrText,
                    items = items,
                    lowConfidenceItemIds = ReceiptReviewCheck.lowConfidenceIds(items),
                    needsReview = ReceiptReviewCheck.needsReview(receipt, items),
                    source = receipt.source.toScanSource(),
                )
            } catch (e: Exception) {
                Timber.e(e, "Receipt load for review failed")
                _uiState.value = ReceiptScanUiState.Error(UiText.Res(R.string.receipt_scan_read_failed))
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

    /**
     * Übernimmt die Kopfdaten aus dem Bearbeiten-Dialog. Ungültiges Datum (`YYYY-MM-DD`) oder ungültige
     * Uhrzeit (`HH:MM`) lässt den bisherigen Wert unverändert; leer bedeutet „unbekannt" und ist erlaubt.
     */
    fun updateHeader(
        storeName: String, storeAddress: String, purchaseDay: String, purchaseTime: String,
        total: Double, discountTotal: Double,
    ) {
        val current = _uiState.value as? ReceiptScanUiState.Preview ?: return
        val day = if (purchaseDay.isEmpty() || ReceiptDates.isValidDay(purchaseDay)) purchaseDay else current.purchaseDay
        val time = if (purchaseTime.isEmpty() || ReceiptDates.isValidTime(purchaseTime)) purchaseTime else current.purchaseTime
        _uiState.value = current.copy(
            storeName = storeName,
            storeAddress = storeAddress,
            purchaseDay = day,
            purchaseTime = time,
            totalAmount = total.coerceAtLeast(0.0),
            discountTotal = discountTotal.coerceAtLeast(0.0),
        )
    }

    fun removeItem(item: ReceiptItemEntity) {
        val current = _uiState.value as? ReceiptScanUiState.Preview ?: return
        _uiState.value = current.copy(items = current.items - item)
    }

    /**
     * Ersetzt eine Position durch die vom Nutzer korrigierte Version (gleiche id).
     * Nach manueller Korrektur gilt die Position nicht mehr als unsicher, daher
     * wird ihre Markierung entfernt.
     */
    fun updateItem(updated: ReceiptItemEntity) {
        val current = _uiState.value as? ReceiptScanUiState.Preview ?: return
        val newItems = current.items.map { if (it.id == updated.id) updated else it }
        _uiState.value = current.copy(
            items = newItems,
            lowConfidenceItemIds = current.lowConfidenceItemIds - updated.id,
        )
    }

    /** Sucht Open-Food-Facts-Produkte zu einer Position (Name, sonst Bon-Text). */
    fun suggestProducts(item: ReceiptItemEntity) {
        viewModelScope.launch {
            _suggestionsLoading.value = true
            _suggestions.value = receiptRepository.suggestOffProducts(item.name.ifBlank { item.rawText })
            _suggestionsLoading.value = false
        }
    }

    fun clearSuggestions() {
        _suggestions.value = emptyList()
        _suggestionsLoading.value = false
    }

    /** Übernimmt ein Produkt: setzt `offBarcode` und den Namen, sofern der Nutzer ihn nicht selbst geändert hat. */
    fun applyProduct(itemId: String, product: OffProductDto) {
        val current = _uiState.value as? ReceiptScanUiState.Preview ?: return
        val item = current.items.firstOrNull { it.id == itemId } ?: return
        val nameUntouched = item.name == originalNames[itemId]
        updateItem(
            item.copy(
                offBarcode = product.barcode,
                name = if (nameUntouched && product.name.isNotBlank()) product.name else item.name,
            )
        )
        clearSuggestions()
    }

    fun save() {
        val current = _uiState.value as? ReceiptScanUiState.Preview ?: return
        viewModelScope.launch {
            try {
                val base = baseReceipt ?: ReceiptEntity(id = current.receiptId)
                val receipt = base.copy(
                    storeId = current.storeId,
                    storeName = current.storeName,
                    storeAddress = current.storeAddress,
                    shoppingListId = shoppingListId.ifBlank { base.shoppingListId },
                    purchaseDay = current.purchaseDay,
                    purchaseTime = current.purchaseTime,
                    purchaseDate = current.purchaseDate,
                    totalAmount = current.totalAmount,
                    discountTotal = current.discountTotal,
                )
                receiptRepository.confirmReceipt(receipt, current.items)
                syncScheduler.triggerOneShot()
                _uiState.value = ReceiptScanUiState.Saved
            } catch (e: Exception) {
                Timber.e(e, "Receipt save failed")
                _uiState.value = ReceiptScanUiState.Error(UiText.Res(R.string.receipt_scan_save_failed))
            }
        }
    }

    fun reset() {
        _uiState.value = ReceiptScanUiState.Idle
        baseReceipt = null
        originalNames = emptyMap()
        clearSuggestions()
    }
}
