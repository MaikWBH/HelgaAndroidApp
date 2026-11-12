package com.helga.android

import com.helga.android.data.local.dao.QuickEmojiDao
import com.helga.android.data.local.dao.ShoppingDao
import com.helga.android.data.local.dao.StoreDao
import com.helga.android.data.local.entity.ShoppingItemEntity
import com.helga.android.data.local.entity.StoreAisleEntity
import com.helga.android.data.remote.ApiProvider
import com.helga.android.data.repository.ReceiptRepository
import com.helga.android.data.repository.ShoppingRepository
import com.helga.android.data.repository.StoreRepository
import com.helga.android.data.shopping.AisleAssigner
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Neue Einkaufslisten-Artikel bekommen ihren Gang automatisch (Phase 3b). */
class ShoppingAisleAssignmentTest {

    private val shoppingDao = mockk<ShoppingDao>(relaxed = true)
    private val storeDao = mockk<StoreDao>()
    private val storeRepository = mockk<StoreRepository>()
    private val saved = mutableListOf<ShoppingItemEntity>()
    private lateinit var repository: ShoppingRepository

    @Before
    fun setUp() {
        coEvery { shoppingDao.upsertItem(any()) } answers { saved += firstArg<ShoppingItemEntity>() }
        coEvery { shoppingDao.findUncheckedItemsByName(any(), any()) } returns emptyList()
        coEvery { storeDao.aislesForStore("M") } returns listOf(
            StoreAisleEntity(id = "1", storeId = "M", aisleName = "Obst & Gemüse", sortOrder = 0, categoryKey = "obst_gemuese"),
            StoreAisleEntity(id = "2", storeId = "M", aisleName = "Kühlregal", sortOrder = 1, categoryKey = "milch_eier"),
        )
        coEvery { storeDao.findAisleForProduct(any(), any()) } returns null
        coEvery { storeRepository.findActiveStoreId() } returns "M"
        repository = ShoppingRepository(
            shoppingDao = shoppingDao,
            storeRepository = storeRepository,
            receiptRepository = mockk<ReceiptRepository>(relaxed = true),
            apiProvider = mockk<ApiProvider>(),
            quickEmojiDao = mockk<QuickEmojiDao>(relaxed = true),
            aisleAssigner = AisleAssigner(storeDao, shoppingDao, mockk<ApiProvider>()),
        )
    }

    @Test
    fun `addItem ordnet Tomaten dem Gemuese-Gang zu`() = runTest {
        repository.addItem(listId = "L", name = "Tomaten")
        assertEquals("Obst & Gemüse", saved.single().aisle)
    }

    @Test
    fun `ausdruecklich uebergebener Gang bleibt`() = runTest {
        repository.addItem(listId = "L", name = "Tomaten", aisle = "Kühlregal")
        assertEquals("Kühlregal", saved.single().aisle)
    }

    @Test
    fun `ohne aktiven Markt bleibt der Gang leer`() = runTest {
        coEvery { storeRepository.findActiveStoreId() } returns null
        repository.addItem(listId = "L", name = "Tomaten")
        assertEquals("", saved.single().aisle)
    }

    @Test
    fun `addOrMergeItem ordnet neue Artikel zu`() = runTest {
        repository.addOrMergeItem(listId = "L", name = "Milch", quantity = 1.0, unit = "l")
        assertEquals("Kühlregal", saved.single().aisle)
    }
}
