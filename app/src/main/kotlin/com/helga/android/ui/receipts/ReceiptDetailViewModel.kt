package com.helga.android.ui.receipts

import androidx.compose.ui.res.stringResource
import com.helga.android.R
import com.helga.android.ui.common.UiText
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
    data class Error(val message: UiText) : ReconcileState
}

sealed interface ReparseState {
    data object Idle : ReparseState
    data object Running : ReparseState
    data class Error(val message: UiText) : ReparseState
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

    private val _reparseState = MutableStateFlow<ReparseState>(ReparseState.Idle)
    val reparseState: StateFlow<ReparseState> = _reparseState

    /** Liest den Bon aus dem Server-Foto neu aus; die neuen Werte ersetzen die bisherigen in Room. */
    fun reparse() {
        if (_reparseState.value is ReparseState.Running) return
        viewModelScope.launch {
            _reparseState.value = ReparseState.Running
            _reparseState.value = try {
                if (receiptRepository.reparse(receiptId)) {
                    syncScheduler.triggerOneShot()
                    ReparseState.Idle
                } else {
                    ReparseState.Error(UiText.Res(R.string.receipt_detail_reparse_failed))
                }
            } catch (e: Exception) {
                Timber.e(e, "Receipt reparse failed")
                ReparseState.Error(UiText.Res(R.string.receipt_detail_reparse_failed))
            }
        }
    }

    fun dismissReparseError() {
        if (_reparseState.value is ReparseState.Error) _reparseState.value = ReparseState.Idle
    }

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
                _reconcileState.value = ReconcileState.Error(UiText.Res(R.string.receipt_reconcile_failed))
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
