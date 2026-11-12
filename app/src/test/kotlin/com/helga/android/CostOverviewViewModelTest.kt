package com.helga.android

import app.cash.turbine.test
import com.helga.android.data.local.entity.MonthlyBudgetEntity
import com.helga.android.data.local.entity.ReceiptAggregateEntity
import com.helga.android.data.model.StatsRange
import com.helga.android.data.repository.ReceiptRepository
import com.helga.android.data.repository.ReceiptStatsRepository
import com.helga.android.data.sync.SyncScheduler
import com.helga.android.ui.receipts.CostOverviewViewModel
import com.helga.android.ui.receipts.Granularity
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class CostOverviewViewModelTest {

    private val aggregates = MutableStateFlow<List<ReceiptAggregateEntity>>(emptyList())
    private val budget = MutableStateFlow<MonthlyBudgetEntity?>(null)
    private val provisional = MutableStateFlow(0)
    private val stats: ReceiptStatsRepository = mockk()
    private val receipts: ReceiptRepository = mockk(relaxed = true)
    private val scheduler: SyncScheduler = mockk(relaxed = true)

    private fun row(dimension: String, period: String, key: String = "", label: String = "", amount: Double = 0.0, count: Int = 0) =
        ReceiptAggregateEntity("$dimension|$period|$key", period, dimension, key, label, amount = amount, count = count)

    private val dataset = listOf(
        row("total", "2026-11", amount = 100.0, count = 3),
        row("total", "2026-10", amount = 80.0, count = 2),
        row("week", "2026-W46", amount = 40.0, count = 1),
        row("store", "2026-11", "name:rewe", "REWE", 70.0, 2),
        row("store", "2026-10", "name:aldi", "Aldi", 50.0, 1),
        row("category", "2026-11", "milch_eier", amount = 20.0, count = 4),
        row("product", "2026-11", "milch", "Milch", 10.0, 5),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { stats.observeAggregates() } returns aggregates
        every { stats.observeProvisionalCount() } returns provisional
        coEvery { stats.refresh() } returns true
        every { receipts.observeBudget() } returns budget
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = CostOverviewViewModel(stats, receipts, scheduler) { LocalDate.of(2026, 11, 11) }

    @Test
    fun `state for a fixed dataset`() = runTest {
        aggregates.value = dataset
        budget.value = MonthlyBudgetEntity(id = "global", amount = 120.0, warnThreshold = 0.9)
        provisional.value = 2
        viewModel().uiState.test {
            val state = expectMostRecentItem()
            assertTrue(state.loaded)
            assertEquals(false, state.empty)
            assertEquals(2, state.provisionalCount)
            assertEquals(12, state.bars.size)
            assertEquals(100.0, state.bars.last().amount, 1e-9)
            assertEquals(120.0, state.barBudget!!, 1e-9)
            assertEquals(100.0, state.monthSpending, 1e-9)
            assertEquals(0.9, state.warnThreshold, 1e-9)
            assertEquals(90.0, state.average, 1e-9) // (80 + 100) / 2 Monate mit Ausgaben
            assertEquals(listOf("name:rewe", "name:aldi"), state.stores.map { it.key })
            assertEquals(listOf("milch_eier"), state.categories.map { it.key })
            assertEquals(listOf("milch"), state.topProducts.map { it.key })
        }
    }

    @Test
    fun `switching to weeks changes the bars and converts the budget`() = runTest {
        aggregates.value = dataset
        budget.value = MonthlyBudgetEntity(id = "global", amount = 520.0)
        val vm = viewModel()
        vm.uiState.test {
            assertEquals(100.0, expectMostRecentItem().bars.last().amount, 1e-9)
            vm.setGranularity(Granularity.WEEK)
            val weekly = expectMostRecentItem()
            assertEquals(Granularity.WEEK, weekly.granularity)
            assertEquals(40.0, weekly.bars.last().amount, 1e-9)
            assertEquals(120.0, weekly.barBudget!!, 1e-9) // 520 × 12 / 52
        }
    }

    @Test
    fun `no budget means no budget line`() = runTest {
        aggregates.value = dataset
        viewModel().uiState.test { assertNull(expectMostRecentItem().barBudget) }
    }

    @Test
    fun `changing the range changes the stores`() = runTest {
        aggregates.value = dataset
        val vm = viewModel()
        vm.uiState.test {
            assertEquals(2, expectMostRecentItem().stores.size) // 3 Monate: REWE (11), Aldi (10)
            vm.setRange(StatsRange.THIS_MONTH)
            assertEquals(listOf("name:rewe"), expectMostRecentItem().stores.map { it.key })
        }
    }

    @Test
    fun `empty aggregates are reported as empty`() = runTest {
        viewModel().uiState.test {
            val state = expectMostRecentItem()
            assertTrue(state.loaded && state.empty)
            assertEquals(0.0, state.average, 0.0)
        }
    }

    @Test
    fun `refresh is requested on start`() = runTest {
        viewModel().uiState.first()
        io.mockk.coVerify { stats.refresh() }
    }
}
