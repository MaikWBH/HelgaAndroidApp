package com.helga.android.ui.receipts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.repository.NutritionRepository
import com.helga.android.data.repository.PurchasedNutritionSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NutritionOverviewViewModel @Inject constructor(
    private val repository: NutritionRepository,
) : ViewModel() {

    private val _summary = MutableStateFlow<PurchasedNutritionSummary?>(null)
    val summary: StateFlow<PurchasedNutritionSummary?> = _summary

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _summary.value = repository.purchasedNutritionTotals()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
