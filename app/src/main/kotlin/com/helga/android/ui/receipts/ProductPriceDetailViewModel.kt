package com.helga.android.ui.receipts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.repository.ProductPriceHistory
import com.helga.android.data.repository.ReceiptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProductPriceDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ReceiptRepository,
) : ViewModel() {

    private val normalizedKey: String = savedStateHandle.get<String>("productKey") ?: ""

    private val _history = MutableStateFlow<ProductPriceHistory?>(null)
    val history: StateFlow<ProductPriceHistory?> = _history

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _history.value = repository.productPriceHistory(normalizedKey)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun formatCurrency(amount: Double): String = String.format("€%.2f", amount)
}
