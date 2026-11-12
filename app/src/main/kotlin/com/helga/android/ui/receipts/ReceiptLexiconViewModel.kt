package com.helga.android.ui.receipts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.R
import com.helga.android.data.local.entity.ReceiptLexiconEntity
import com.helga.android.data.repository.ReceiptLexiconRepository
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
class ReceiptLexiconViewModel @Inject constructor(
    private val repository: ReceiptLexiconRepository,
) : ViewModel() {

    val entries: StateFlow<List<ReceiptLexiconEntity>> = repository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _message = MutableSharedFlow<UiText>(extraBufferCapacity = 1)
    val message: SharedFlow<UiText> = _message.asSharedFlow()

    init {
        refresh()
    }

    /** Holt das Lexikon vom Server; offline bleibt der lokale Abzug stehen und eine Meldung erscheint. */
    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            if (!repository.refresh()) _message.tryEmit(UiText.Res(R.string.receipt_lexicon_unreachable))
            _refreshing.value = false
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            if (!repository.delete(id)) _message.tryEmit(UiText.Res(R.string.receipt_lexicon_unreachable))
        }
    }
}
