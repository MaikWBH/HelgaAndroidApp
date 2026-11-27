package com.helga.android

import com.helga.android.data.local.entity.ReceiptLexiconEntity
import com.helga.android.data.repository.ReceiptLexiconRepository
import com.helga.android.ui.receipts.ReceiptLexiconViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiptLexiconViewModelTest {

    private val all = MutableStateFlow(
        listOf(ReceiptLexiconEntity(id = "a", name = "Milch"), ReceiptLexiconEntity(id = "b", name = "Brot")),
    )
    private val repository: ReceiptLexiconRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { repository.observe() } returns all
        coEvery { repository.refresh() } returns true
        coEvery { repository.delete(any()) } returns true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loeschen_erst_nach_confirmDelete_nicht_nach_undo() = runTest(UnconfinedTestDispatcher()) {
        val vm = ReceiptLexiconViewModel(repository)
        val job = launch { vm.entries.collect { } }

        vm.delete("a")
        assertEquals(listOf("b"), vm.entries.value.map { it.id })
        coVerify(exactly = 0) { repository.delete(any()) }

        vm.undoDelete("a")
        vm.confirmDelete("a")
        assertEquals(listOf("a", "b"), vm.entries.value.map { it.id })
        coVerify(exactly = 0) { repository.delete(any()) }

        vm.delete("b")
        vm.confirmDelete("b")
        coVerify(exactly = 1) { repository.delete("b") }
        job.cancel()
    }
}
