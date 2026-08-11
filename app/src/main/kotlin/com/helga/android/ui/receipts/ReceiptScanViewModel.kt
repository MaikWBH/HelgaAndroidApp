package com.helga.android.ui.receipts

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.local.ScanSource
import com.helga.android.data.local.toDbValue
import com.helga.android.data.local.entity.ReceiptItemEntity
import com.helga.android.data.local.entity.StoreEntity
import com.helga.android.data.repository.ReceiptRepository
import com.helga.android.data.repository.StoreRepository
import com.helga.android.data.sync.SyncScheduler
import com.helga.android.data.util.ReceiptImagePreprocessor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
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
        val lowConfidenceItemIds: Set<String> = emptySet(),
        val needsReview: Boolean = false,
        val source: ScanSource = ScanSource.ON_DEVICE,
    ) : ReceiptScanUiState
    data object Saved : ReceiptScanUiState
    data class Error(val message: String) : ReceiptScanUiState
}

@HiltViewModel
class ReceiptScanViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
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
    // Quelle des aufgenommenen Bildes – wird beim Speichern in den App-Speicher kopiert.
    private var sourceImageUri: Uri? = null

    fun setShoppingListId(listId: String?) {
        shoppingListId = listId.orEmpty()
    }

    fun scanImage(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = ReceiptScanUiState.Scanning
            try {
                sourceImageUri = uri
                // Aufrecht ausrichten (EXIF) + speicherschonend laden – entscheidend
                // für die Erkennungsrate von OCR und KI-Vision.
                val bitmap = withContext(Dispatchers.IO) {
                    ReceiptImagePreprocessor.loadUprightBitmap(context, uri)
                } ?: throw IllegalStateException("Bild konnte nicht geladen werden")

                val result = receiptRepository.scanReceipt(bitmap)
                scannedReceiptId = result.receipt.id
                _uiState.value = ReceiptScanUiState.Preview(
                    storeName = result.receipt.storeName,
                    storeId = result.receipt.storeId,
                    totalAmount = result.receipt.totalAmount,
                    purchaseDate = result.receipt.purchaseDate,
                    rawOcrText = result.receipt.rawOcrText,
                    items = result.items,
                    lowConfidenceItemIds = result.lowConfidenceItemIds,
                    needsReview = result.needsReview,
                    source = result.source,
                )
            } catch (e: Exception) {
                Timber.e(e, "Receipt scan failed")
                _uiState.value = ReceiptScanUiState.Error("Kassenzettel konnte nicht gelesen werden")
            }
        }
    }

    /** Kopiert das aufgenommene Bild nach filesDir/receipts/{id}.jpg und gibt den Pfad zurück. */
    private fun persistImage(receiptId: String): String {
        val uri = sourceImageUri ?: return ""
        return try {
            val dir = File(context.filesDir, "receipts").also { it.mkdirs() }
            val dest = File(dir, "$receiptId.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            dest.absolutePath
        } catch (e: Exception) {
            Timber.w(e, "Receipt image persist failed")
            ""
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

    fun save() {
        val current = _uiState.value as? ReceiptScanUiState.Preview ?: return
        viewModelScope.launch {
            try {
                val localImageUri = withContext(Dispatchers.IO) { persistImage(scannedReceiptId) }
                val receipt = com.helga.android.data.local.entity.ReceiptEntity(
                    id = scannedReceiptId,
                    storeId = current.storeId,
                    storeName = current.storeName,
                    shoppingListId = shoppingListId,
                    purchaseDate = current.purchaseDate,
                    totalAmount = current.totalAmount,
                    currency = "EUR",
                    localImageUri = localImageUri,
                    rawOcrText = current.rawOcrText,
                    status = "scanned",
                    source = current.source.toDbValue(),
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
