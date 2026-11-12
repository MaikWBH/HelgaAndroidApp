package com.helga.android.ui.receipts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.model.KeyAmount
import com.helga.android.data.model.PeriodAmount
import com.helga.android.data.model.StatsRange
import com.helga.android.data.receipts.ReceiptStats
import com.helga.android.data.repository.ReceiptRepository
import com.helga.android.data.repository.ReceiptStatsRepository
import com.helga.android.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class Granularity { MONTH, WEEK }

/** Zustand der Kostenübersicht; alle Werte kommen aus dem lokalen Abzug der Server-Auswertung (offline verfügbar). */
data class CostOverviewUiState(
    val loaded: Boolean = false,
    val empty: Boolean = true,
    val provisionalCount: Int = 0,
    val granularity: Granularity = Granularity.MONTH,
    val range: StatsRange = StatsRange.THREE_MONTHS,
    val bars: List<PeriodAmount> = emptyList(),
    val barBudget: Double? = null,
    val average: Double = 0.0,
    val monthSpending: Double = 0.0,
    val budget: Double = 0.0,
    val warnThreshold: Double = 0.8,
    val stores: List<KeyAmount> = emptyList(),
    val categories: List<KeyAmount> = emptyList(),
    val categoryDiscount: Double = 0.0,
    val topProducts: List<KeyAmount> = emptyList(),
)

@HiltViewModel
class CostOverviewViewModel internal constructor(
    private val statsRepository: ReceiptStatsRepository,
    private val receiptRepository: ReceiptRepository,
    private val syncScheduler: SyncScheduler,
    private val today: () -> LocalDate,
) : ViewModel() {

    @Inject
    constructor(
        statsRepository: ReceiptStatsRepository,
        receiptRepository: ReceiptRepository,
        syncScheduler: SyncScheduler,
    ) : this(statsRepository, receiptRepository, syncScheduler, LocalDate::now)

    private val granularity = MutableStateFlow(Granularity.MONTH)
    private val range = MutableStateFlow(StatsRange.THREE_MONTHS)

    val uiState: StateFlow<CostOverviewUiState> = combine(
        statsRepository.observeAggregates(),
        receiptRepository.observeBudget(),
        statsRepository.observeProvisionalCount(),
        granularity,
        range,
    ) { rows, budgetEntity, provisional, gran, selectedRange ->
        val now = today()
        val budget = budgetEntity?.amount ?: 0.0
        val series = when (gran) {
            Granularity.MONTH -> ReceiptStats.monthSeries(rows, now, 12)
            Granularity.WEEK -> ReceiptStats.weekSeries(rows, now, 12)
        }
        CostOverviewUiState(
            loaded = true,
            empty = rows.isEmpty(),
            provisionalCount = provisional,
            granularity = gran,
            range = selectedRange,
            bars = series,
            barBudget = when {
                budget <= 0.0 -> null
                gran == Granularity.MONTH -> budget
                else -> budget * 12.0 / 52.0 // Monatsbudget auf eine Woche umgerechnet
            },
            average = ReceiptStats.monthlyAverage(series),
            monthSpending = ReceiptStats.currentMonthTotal(rows, now),
            budget = budget,
            warnThreshold = budgetEntity?.warnThreshold ?: 0.8,
            stores = ReceiptStats.storeTotals(rows, selectedRange, now),
            categories = ReceiptStats.categoryTotals(rows, selectedRange, now),
            categoryDiscount = ReceiptStats.discountTotal(rows, selectedRange, now),
            topProducts = ReceiptStats.topProducts(rows, selectedRange, now),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CostOverviewUiState())

    init {
        // Auswertung vom Server auffrischen; offline bleibt der lokale Abzug stehen.
        viewModelScope.launch { statsRepository.refresh() }
    }

    fun setGranularity(value: Granularity) {
        granularity.value = value
    }

    fun setRange(value: StatsRange) {
        range.value = value
    }

    fun saveBudget(amount: Double, warnThreshold: Double) {
        viewModelScope.launch {
            receiptRepository.saveBudget(amount, warnThreshold)
            syncScheduler.triggerOneShot()
        }
    }
}
