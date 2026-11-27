package com.helga.android.ui.receipts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.R
import com.helga.android.data.local.entity.ReceiptLexiconEntity
import com.helga.android.data.repository.ReceiptLexiconRepository
import com.helga.android.ui.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ReceiptLexiconViewModel @Inject constructor(
    private val repository: ReceiptLexiconRepository,
) : ViewModel() {

    /** Zum Löschen vorgemerkte IDs: ausgeblendet, bis die Rückgängig-Frist endet (Phase 8, B8-S1). */
    private val pendingDelete = MutableStateFlow<Set<String>>(emptySet())

    val entries: StateFlow<List<ReceiptLexiconEntity>> = combine(repository.observe(), pendingDelete) { all, hidden ->
        all.filter { it.id !in hidden }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _deleteRequests = MutableSharedFlow<String>(extraBufferCapacity = 16)
    /** Je vorgemerktem Eintrag eine ID; der Screen zeigt „Rückgängig" und meldet [undoDelete] oder [confirmDelete]. */
    val deleteRequests: SharedFlow<String> = _deleteRequests.asSharedFlow()

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

    /** Blendet den Eintrag aus; gelöscht wird erst mit [confirmDelete]. */
    fun delete(id: String) {
        pendingDelete.update { it + id }
        _deleteRequests.tryEmit(id)
    }

    fun undoDelete(id: String) {
        pendingDelete.update { it - id }
    }

    fun confirmAllPending() {
        pendingDelete.value.forEach { confirmDelete(it) }
    }

    /** Löscht einen vorgemerkten Eintrag endgültig; läuft auch weiter, wenn der Screen gerade verlassen wird. */
    fun confirmDelete(id: String) {
        if (id !in pendingDelete.value) return
        viewModelScope.launch {
            withContext(NonCancellable) {
                val ok = repository.delete(id)
                pendingDelete.update { it - id }
                if (!ok) _message.tryEmit(UiText.Res(R.string.receipt_lexicon_unreachable))
            }
        }
    }
}
