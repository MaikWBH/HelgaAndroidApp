package com.helga.android.ui.receipts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.repository.ProductSummary
import com.helga.android.data.repository.ReceiptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProductPriceListViewModel @Inject constructor(
    private val repository: ReceiptRepository,
) : ViewModel() {

    private val _allProducts = MutableStateFlow<List<ProductSummary>>(emptyList())
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    val products: StateFlow<List<ProductSummary>> = combine(_allProducts, _query) { all, q ->
        if (q.isBlank()) all
        else all.filter { it.displayName.contains(q, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        loadProducts()
    }

    private fun loadProducts() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _allProducts.value = repository.productSummaries()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setQuery(q: String) { _query.value = q }

    fun formatCurrency(amount: Double): String = String.format("€%.2f", amount)
}
