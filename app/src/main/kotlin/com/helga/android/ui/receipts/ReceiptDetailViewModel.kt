package com.helga.android.ui.receipts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.local.entity.ReceiptEntity
import com.helga.android.data.local.entity.ReceiptItemEntity
import com.helga.android.data.preferences.AppPreferences
import com.helga.android.data.repository.ReconcileOutcome
import com.helga.android.data.repository.ReceiptRepository
import com.helga.android.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed interface ReconcileState {
    data object Idle : ReconcileState
    data object Running : ReconcileState
    data class Done(val outcome: ReconcileOutcome) : ReconcileState
    data class Error(val message: String) : ReconcileState
}

@HiltViewModel
class ReceiptDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val receiptRepository: ReceiptRepository,
    private val syncScheduler: SyncScheduler,
    preferences: AppPreferences,
) : ViewModel() {

    private val receiptId: String = savedStateHandle.get<String>("receiptId").orEmpty()

    val receipt: StateFlow<ReceiptEntity?> = receiptRepository.observeReceiptDetail(receiptId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val items: StateFlow<List<ReceiptItemEntity>> = receiptRepository.observeReceiptItems(receiptId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val serverUrl: StateFlow<String> = preferences.connection
        .map { it.serverUrl }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val reconcileEnabled: StateFlow<Boolean> = preferences.receiptReconciliationEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _reconcileState = MutableStateFlow<ReconcileState>(ReconcileState.Idle)
    val reconcileState: StateFlow<ReconcileState> = _reconcileState

    fun reconcile() {
        if (_reconcileState.value is ReconcileState.Running) return
        viewModelScope.launch {
            _reconcileState.value = ReconcileState.Running
            try {
                val outcome = receiptRepository.reconcile(receiptId)
                syncScheduler.triggerOneShot()
                _reconcileState.value = ReconcileState.Done(outcome)
            } catch (e: Exception) {
                Timber.e(e, "Receipt reconcile failed")
                _reconcileState.value = ReconcileState.Error("Abgleich fehlgeschlagen (Server erreichbar?)")
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            receiptRepository.deleteReceipt(receiptId)
            syncScheduler.triggerOneShot()
            onDone()
        }
    }
}
