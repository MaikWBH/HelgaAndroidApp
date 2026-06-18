package com.helga.android.ui.receipts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.local.entity.OffProductEntity
import com.helga.android.data.preferences.AppPreferences
import com.helga.android.data.repository.NutritionRepository
import com.helga.android.data.repository.SuggestedMatch
import com.helga.android.data.util.ReceiptItemNormalizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ArticleLinkUiState {
    data object Loading : ArticleLinkUiState
    data class AlreadyLinked(val linkedDisplayName: String) : ArticleLinkUiState
    data class Suggestion(val match: SuggestedMatch) : ArticleLinkUiState
    data object Error : ArticleLinkUiState
}

@HiltViewModel
class ArticleLinkConfirmViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NutritionRepository,
    val preferences: AppPreferences,
) : ViewModel() {

    val displayName: String = savedStateHandle.get<String>("articleName") ?: ""
    private val normalizedName: String = ReceiptItemNormalizer.normalize(displayName)

    private val _uiState = MutableStateFlow<ArticleLinkUiState>(ArticleLinkUiState.Loading)
    val uiState: StateFlow<ArticleLinkUiState> = _uiState

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchResults = MutableStateFlow<List<OffProductEntity>>(emptyList())
    val searchResults: StateFlow<List<OffProductEntity>> = _searchResults

    /** Gerade bestätigtes Produkt, solange noch über die Packungsgröße entschieden wird. */
    private val _confirmedProduct = MutableStateFlow<OffProductEntity?>(null)
    val confirmedProduct: StateFlow<OffProductEntity?> = _confirmedProduct

    private val _linkSaved = MutableStateFlow(false)
    val linkSaved: StateFlow<Boolean> = _linkSaved

    init {
        viewModelScope.launch {
            val existing = repository.findLink(normalizedName)
            _uiState.value = if (existing != null && existing.confirmed == 1) {
                ArticleLinkUiState.AlreadyLinked(existing.displayName.ifBlank { displayName })
            } else {
                try {
                    ArticleLinkUiState.Suggestion(repository.suggestMatch(displayName))
                } catch (e: Exception) {
                    ArticleLinkUiState.Error
                }
            }
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _searchResults.value = repository.manualSearch(query)
        }
    }

    fun confirm(product: OffProductEntity) {
        viewModelScope.launch {
            repository.confirmLink(normalizedName, displayName, product)
            if (product.packageGrams > 0.0) {
                _linkSaved.value = true
            } else {
                _confirmedProduct.value = product
            }
        }
    }

    fun saveManualPackageGrams(grams: Double) {
        viewModelScope.launch {
            _confirmedProduct.value?.let { repository.setManualPackageGrams(it.id, grams) }
            _linkSaved.value = true
        }
    }

    fun skipManualPackageGrams() {
        _linkSaved.value = true
    }
}
