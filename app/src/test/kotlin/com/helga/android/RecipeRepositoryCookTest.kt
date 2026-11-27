package com.helga.android

import com.helga.android.data.local.dao.RecipeDao
import com.helga.android.data.local.dao.RecipeFeedbackDao
import com.helga.android.data.local.dao.RecipeHistoryDao
import com.helga.android.data.local.entity.RecipeHistoryEntity
import com.helga.android.data.repository.RecipeRepository
import com.helga.android.data.repository.ShoppingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeRepositoryCookTest {

    private val history: RecipeHistoryDao = mockk(relaxed = true)
    private val repo = RecipeRepository(
        recipeDao = mockk<RecipeDao>(relaxed = true),
        recipeFeedbackDao = mockk<RecipeFeedbackDao>(relaxed = true),
        recipeHistoryDao = history,
        shoppingRepository = mockk<ShoppingRepository>(relaxed = true),
    )

    @Test
    fun markCooked_legt_neuen_eintrag_an_wenn_keiner_existiert() = runTest {
        coEvery { history.markCooked("r1", "2026-11-11", 1000L) } returns 0
        val inserted = slot<List<RecipeHistoryEntity>>()
        coEvery { history.upsertAll(capture(inserted)) } returns Unit

        repo.markCooked("r1", "2026-11-11", 1000L)

        val entry = inserted.captured.single()
        assertEquals("r1", entry.recipeId)
        assertEquals("2026-11-11", entry.plannedDate)
        assertEquals(1, entry.cooked)
        assertEquals(1000L, entry.updatedAt)
        assertEquals(1, entry.dirty)
        assertTrue(entry.id.isNotBlank())
    }

    @Test
    fun markCooked_aktualisiert_vorhandenen_eintrag_ohne_neuen_insert() = runTest {
        coEvery { history.markCooked("r1", "2026-11-11", 1000L) } returns 1

        repo.markCooked("r1", "2026-11-11", 1000L)

        coVerify(exactly = 0) { history.upsertAll(any()) }
    }
}
