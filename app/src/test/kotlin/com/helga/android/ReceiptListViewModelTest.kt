package com.helga.android

import app.cash.turbine.test
import com.helga.android.data.model.OlderReceiptsResult
import com.helga.android.data.repository.ReceiptRepository
import com.helga.android.ui.common.UiText
import com.helga.android.ui.receipts.ReceiptListViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiptListViewModelTest {

    private val repo: ReceiptRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { repo.observeReceipts() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun messageFor(result: OlderReceiptsResult): UiText {
        coEvery { repo.loadOlderMonth(any(), any()) } returns result
        val viewModel = ReceiptListViewModel(repo)
        var message: UiText? = null
        viewModel.olderMessage.test {
            viewModel.loadOlder()
            message = awaitItem()
        }
        return message!!
    }

    @Test
    fun `loaded result reports count and month`() = runTest {
        val text = messageFor(OlderReceiptsResult.Loaded(3, YearMonth.of(2026, 8))) as UiText.Res
        assertEquals(R.string.receipt_list_older_loaded, text.id)
        assertEquals(listOf<Any>(3, "August 2026"), text.args)
    }

    @Test
    fun `nothing found reports the month before`() = runTest {
        val text = messageFor(OlderReceiptsResult.NothingFound(YearMonth.of(2026, 11))) as UiText.Res
        assertEquals(R.string.receipt_list_older_none, text.id)
        assertEquals(listOf<Any>("November 2026"), text.args)
    }

    @Test
    fun `failure reports the network message`() = runTest {
        val text = messageFor(OlderReceiptsResult.Failed) as UiText.Res
        assertEquals(R.string.receipt_list_older_failed, text.id)
    }
}
