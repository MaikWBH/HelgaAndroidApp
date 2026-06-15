package com.helga.android.ui.receipts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.local.dao.CostByDate
import com.helga.android.data.local.dao.CostByStore
import com.helga.android.data.local.dao.ReceiptDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

enum class TimePeriod {
    WEEK, MONTH, ALL
}

@HiltViewModel
class CostOverviewViewModel @Inject constructor(
    private val receiptDao: ReceiptDao,
) : ViewModel() {

    private val _period = MutableStateFlow(TimePeriod.MONTH)
    val period: StateFlow<TimePeriod> = _period

    private val _costByStore = MutableStateFlow<List<CostByStore>>(emptyList())
    val costByStore: StateFlow<List<CostByStore>> = _costByStore

    private val _costByDate = MutableStateFlow<List<CostByDate>>(emptyList())
    val costByDate: StateFlow<List<CostByDate>> = _costByDate

    private val _totalCost = MutableStateFlow(0.0)
    val totalCost: StateFlow<Double> = _totalCost

    private val _receiptCount = MutableStateFlow(0)
    val receiptCount: StateFlow<Int> = _receiptCount

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        loadData()
    }

    fun setPeriod(period: TimePeriod) {
        _period.value = period
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val (startEpoch, endEpoch) = getEpochRange(_period.value)

                // Load cost by store (all time for context)
                val stores = receiptDao.costByStore()
                _costByStore.value = stores

                // Load cost by date (within range)
                val dates = if (_period.value == TimePeriod.ALL) {
                    receiptDao.costByDate()
                } else {
                    receiptDao.costByDateRange(startEpoch, endEpoch)
                }
                _costByDate.value = dates

                // Load summary
                val summary = receiptDao.totalCostForRange(startEpoch, endEpoch)
                _totalCost.value = summary.totalAmount
                _receiptCount.value = summary.receiptCount
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun getEpochRange(period: TimePeriod): Pair<Long, Long> {
        val today = LocalDate.now()
        val startDate = when (period) {
            TimePeriod.WEEK -> today.minus(6, ChronoUnit.DAYS)
            TimePeriod.MONTH -> today.minusMonths(1)
            TimePeriod.ALL -> LocalDate.ofEpochDay(0) // very old date
        }

        val zone = java.time.ZoneId.systemDefault()
        val startEpoch = startDate.atStartOfDay(zone).toEpochSecond()
        val endEpoch = today.plusDays(1).atStartOfDay(zone).toEpochSecond()

        return startEpoch to endEpoch
    }

    fun getMaxCost(): Double {
        val stores = _costByStore.value
        val dates = _costByDate.value

        val maxStore = stores.maxOfOrNull { it.totalAmount } ?: 0.0
        val maxDate = dates.maxOfOrNull { it.totalAmount } ?: 0.0

        return maxOf(maxStore, maxDate, 0.1) // min 0.1 to avoid division by zero
    }

    fun formatDate(dateStr: String): String {
        // dateStr format: "2026-06-15 HH:MM:SS" or "2026-06-15"
        return dateStr.split(" ")[0]
    }

    fun formatCurrency(amount: Double): String {
        return String.format("€%.2f", amount)
    }
}
