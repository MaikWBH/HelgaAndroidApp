package com.helga.android.ui.receipts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.R
import com.helga.android.data.local.entity.ReceiptEntity
import com.helga.android.data.repository.ReceiptRepository
import com.helga.android.ui.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReceiptListViewModel @Inject constructor(
    private val receiptRepository: ReceiptRepository,
) : ViewModel() {

    val receipts: StateFlow<List<ReceiptEntity>> = receiptRepository.observeReceipts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _loadingOlder = MutableStateFlow(false)
    val loadingOlder: StateFlow<Boolean> = _loadingOlder.asStateFlow()

    private val _olderMessage = MutableSharedFlow<UiText>(extraBufferCapacity = 1)
    val olderMessage: SharedFlow<UiText> = _olderMessage.asSharedFlow()

    /** Lädt den nächsten älteren Monat vom Server; bei Fehler (offline) erscheint eine Meldung. */
    fun loadOlder() {
        if (_loadingOlder.value) return
        viewModelScope.launch {
            _loadingOlder.value = true
            try {
                if (!receiptRepository.loadOlderMonth()) {
                    _olderMessage.tryEmit(UiText.Res(R.string.receipt_list_older_failed))
                }
            } finally {
                _loadingOlder.value = false
            }
        }
    }
}
