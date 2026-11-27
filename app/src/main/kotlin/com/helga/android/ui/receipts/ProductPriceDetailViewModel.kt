package com.helga.android.ui.receipts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.model.TrendPoint
import com.helga.android.data.receipts.ReceiptStats
import com.helga.android.data.repository.ProductPriceHistory
import com.helga.android.data.repository.ReceiptRepository
import com.helga.android.data.repository.ReceiptStatsRepository
import com.helga.android.ui.common.formatEuro
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProductPriceDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ReceiptRepository,
    statsRepository: ReceiptStatsRepository,
) : ViewModel() {

    private val normalizedKey: String = savedStateHandle.get<String>("productKey") ?: ""

    private val _history = MutableStateFlow<ProductPriceHistory?>(null)
    val history: StateFlow<ProductPriceHistory?> = _history

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    /** Monatlicher Durchschnittspreis über alle Monate (aus dem Auswertungs-Abzug, nicht aus den Einzelkäufen). */
    val trend: StateFlow<List<TrendPoint>> = statsRepository.observeAggregates()
        .map { ReceiptStats.productTrend(it, normalizedKey) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    fun formatCurrency(amount: Double): String = formatEuro(amount)
}
