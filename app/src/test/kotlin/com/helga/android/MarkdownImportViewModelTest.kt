package com.helga.android

import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.repository.AiRepository
import com.helga.android.data.repository.RecipeRepository
import com.helga.android.data.sync.SyncScheduler
import com.helga.android.data.util.MarkdownErrorCode
import com.helga.android.data.util.MarkdownFatal
import com.helga.android.ui.recipes.MarkdownImportViewModel
import com.helga.android.ui.recipes.MarkdownSaveTarget
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MarkdownImportViewModelTest {

    private val repository: RecipeRepository = mockk(relaxed = true)
    private val aiRepository: AiRepository = mockk(relaxed = true)
    private val scheduler: SyncScheduler = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { repository.allRecipes() } returns emptyList()
        coEvery { aiRepository.classifyRecipe(any()) } throws IOException("offline")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = MarkdownImportViewModel(repository, aiRepository, scheduler)

    private fun recipe(title: String) = "# $title\n## Zutaten\n- 1 Prise Salz\n## Zubereitung\n1. Würzen."

    @Test
    fun auswerten_erzeugt_vorschau_mit_auswahl() = runTest {
        val vm = viewModel()
        vm.setInput(recipe("A") + "\n---\n" + recipe("B") + "\n---\nOhne Titel\n## Zutaten\n- Ei")
        vm.evaluate()
        val state = vm.state.value
        assertEquals(listOf("A", "B"), state.preview?.map { it.recipe.name })
        assertTrue(state.preview.orEmpty().all { it.selected && !it.duplicate })
        assertEquals(MarkdownErrorCode.MISSING_TITLE, state.errors.single().code)
        vm.toggle(0)
        assertEquals(listOf(false, true), vm.state.value.preview?.map { it.selected })
        vm.setInput("neu")
        assertNull(vm.state.value.preview)
        assertTrue(vm.state.value.errors.isEmpty())
    }

    @Test
    fun gleicher_name_ist_abgewaehlt() = runTest {
        coEvery { repository.allRecipes() } returns listOf(RecipeEntity(id = "1", name = " Suppe "))
        val vm = viewModel()
        vm.setInput(recipe("suppe") + "\n---\n" + recipe("Salat") + "\n---\n" + recipe("SALAT"))
        vm.evaluate()
        val preview = vm.state.value.preview.orEmpty()
        assertEquals(listOf(true, false, true), preview.map { it.duplicate })
        assertEquals(listOf(false, true, false), preview.map { it.selected })
    }

    @Test
    fun speichern_je_auswahl_und_trigger_einmal() = runTest {
        val vm = viewModel()
        vm.setInput(recipe("A") + "\n---\n" + recipe("B") + "\n---\n" + recipe("C"))
        vm.evaluate()
        vm.toggle(2)
        var target: MarkdownSaveTarget? = null
        vm.save { target = it }
        coVerify(exactly = 2) { repository.saveRecipe(any(), any(), any(), any()) }
        verify(exactly = 1) { scheduler.triggerOneShot() }
        assertEquals(MarkdownSaveTarget.RecipeList, target)
        assertEquals(false, vm.state.value.isSaving)
    }

    @Test
    fun ziel_detail_bei_einem_rezept_sonst_liste() = runTest {
        val saved = slot<RecipeEntity>()
        coEvery { repository.saveRecipe(capture(saved), any(), any(), any()) } returns Unit
        val vm = viewModel()
        vm.setInput(recipe("Allein"))
        vm.evaluate()
        var target: MarkdownSaveTarget? = null
        vm.save { target = it }
        assertNotNull(target)
        assertEquals(MarkdownSaveTarget.Detail(saved.captured.id), target)

        val none = viewModel()
        none.setInput(recipe("X"))
        none.evaluate()
        none.toggle(0)
        var called = false
        none.save { called = true }
        assertEquals(false, called)
    }

    @Test
    fun fatale_eingabe_ohne_vorschau() = runTest {
        val vm = viewModel()
        vm.setInput("   ")
        vm.evaluate()
        assertNull(vm.state.value.preview)
        assertEquals(MarkdownFatal.EMPTY_INPUT, vm.state.value.fatal)
    }
}
