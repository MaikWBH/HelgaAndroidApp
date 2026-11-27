package com.helga.android.ui.receipts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.R
import com.helga.android.data.local.entity.ReceiptEntity
import com.helga.android.data.model.OlderReceiptsResult
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
import java.time.format.DateTimeFormatter
import java.util.Locale
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

    /** Sucht bis zu 12 Monate zurück bis zum ersten Monat mit Bons und meldet das Ergebnis (F11, Phase 7). */
    fun loadOlder() {
        if (_loadingOlder.value) return
        viewModelScope.launch {
            _loadingOlder.value = true
            try {
                val message = when (val result = receiptRepository.loadOlderMonth()) {
                    is OlderReceiptsResult.Loaded -> UiText.Res(
                        R.string.receipt_list_older_loaded, listOf(result.count, result.month.format(MONTH)),
                    )
                    is OlderReceiptsResult.NothingFound -> UiText.Res(
                        R.string.receipt_list_older_none, listOf(result.before.format(MONTH)),
                    )
                    OlderReceiptsResult.Failed -> UiText.Res(R.string.receipt_list_older_failed)
                }
                _olderMessage.tryEmit(message)
            } finally {
                _loadingOlder.value = false
            }
        }
    }
}

private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)
