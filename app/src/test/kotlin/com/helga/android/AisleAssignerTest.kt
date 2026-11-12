package com.helga.android

import com.helga.android.data.local.dao.ShoppingDao
import com.helga.android.data.local.dao.StoreDao
import com.helga.android.data.local.entity.AisleProductEntity
import com.helga.android.data.local.entity.ShoppingItemEntity
import com.helga.android.data.local.entity.StoreAisleEntity
import com.helga.android.data.local.entity.StoreEntity
import com.helga.android.data.remote.SyncApi
import com.helga.android.data.remote.dto.CategorizeResponse
import com.helga.android.data.remote.dto.CategorizeResultDto
import com.helga.android.data.remote.ApiProvider
import com.helga.android.data.shopping.AisleAssigner
import com.helga.android.data.util.AisleProductKey
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Liste C aus `aisle-testfaelle.md`: Reihenfolge der Quellen beim lokalen Zuordnen. */
class AisleAssignerTest {

    private val storeDao = mockk<StoreDao>()
    private val shoppingDao = mockk<ShoppingDao>(relaxed = true)
    private val apiProvider = mockk<ApiProvider>()
    private lateinit var assigner: AisleAssigner

    private fun aisle(name: String, order: Int, key: String) =
        StoreAisleEntity(id = "a$order", storeId = "M", aisleName = name, sortOrder = order, categoryKey = key)

    @Before
    fun setUp() {
        coEvery { storeDao.aislesForStore("M") } returns listOf(
            aisle("Obst & Gemüse", 0, "obst_gemuese"),
            aisle("Kühlregal", 1, "milch_eier"),
            aisle("Getränke", 2, "getraenke"),
            aisle("Drogerie & Haushalt", 3, "drogerie"),
            aisle("Kasse", 4, ""),
        )
        coEvery { storeDao.findAisleForProduct(any(), any()) } returns null
        coEvery { storeDao.findAisleForProduct("hafermilch", "M") } returns "Getränke"
        coEvery { storeDao.findAisleForProduct("tofu", "M") } returns "Kühlregal"
        coEvery { storeDao.findActiveStore() } returns StoreEntity(id = "M", name = "Markt", isActive = 1)
        coEvery { storeDao.findAisleProductEntry(any(), any()) } returns null
        coEvery { storeDao.upsertAisleProduct(any()) } answers { learned += firstArg<AisleProductEntity>() }
        coEvery { shoppingDao.upsertItem(any()) } answers { updated += firstArg<ShoppingItemEntity>() }
        coEvery { apiProvider.api() } returns api
        assigner = AisleAssigner(storeDao, shoppingDao, apiProvider)
    }

    private val api = mockk<SyncApi>()
    private val learned = mutableListOf<AisleProductEntity>()
    private val updated = mutableListOf<ShoppingItemEntity>()

    private fun open(vararg names: String) = names.mapIndexed { i, n ->
        ShoppingItemEntity(id = "i$i", listId = "L", name = n)
    }

    @Test
    fun `Fall 10 - bekannte Kategorie ohne Gang lernt nichts`() = runTest {
        coEvery { shoppingDao.uncheckedItemsWithoutAisle() } returns open("Hackfleisch")
        coEvery { api.categorizeItems(any()) } returns
            CategorizeResponse(listOf(CategorizeResultDto("Hackfleisch", "fleisch_wurst")))
        // Hackfleisch ist lokal bekannt, hat aber keinen Gang -> geht an den Server, der dieselbe Kategorie meldet
        assertEquals(0, assigner.assignRemote())
        assertTrue(learned.isEmpty())
        assertTrue(updated.isEmpty())
    }

    @Test
    fun `Fall 11 - Server meldet haushalt, Ausweichkette ergibt Drogerie und Haushalt, wird gelernt`() = runTest {
        coEvery { shoppingDao.uncheckedItemsWithoutAisle() } returns open("Geschenkpapier")
        val request = slot<com.helga.android.data.remote.dto.CategorizeRequest>()
        coEvery { api.categorizeItems(capture(request)) } returns
            CategorizeResponse(listOf(CategorizeResultDto("Geschenkpapier", "haushalt")))

        assertEquals(1, assigner.assignRemote())

        assertEquals(listOf("Geschenkpapier"), request.captured.names)
        assertEquals("Drogerie & Haushalt", updated.single().aisle)
        assertEquals(1, updated.single().dirty)
        assertEquals(AisleProductKey.normalize("Geschenkpapier"), learned.single().productName)
        assertEquals("Drogerie & Haushalt", learned.single().aisleName)
    }

    @Test
    fun `Fall 12 - Server nicht erreichbar, Artikel bleibt ohne Gang`() = runTest {
        coEvery { shoppingDao.uncheckedItemsWithoutAisle() } returns open("Geschenkpapier")
        coEvery { api.categorizeItems(any()) } throws java.io.IOException("offline")

        assertEquals(0, assigner.assignRemote())
        assertTrue(updated.isEmpty())
        assertTrue(learned.isEmpty())
    }

    @Test
    fun `ein Name mit zwei offenen Artikeln aendert beide und lernt einmal`() = runTest {
        coEvery { shoppingDao.uncheckedItemsWithoutAisle() } returns open("Geschenkpapier", "geschenkpapier")
        val request = slot<com.helga.android.data.remote.dto.CategorizeRequest>()
        coEvery { api.categorizeItems(capture(request)) } returns
            CategorizeResponse(listOf(CategorizeResultDto("Geschenkpapier", "haushalt")))

        assertEquals(2, assigner.assignRemote())

        assertEquals(1, request.captured.names.size)
        assertEquals(2, updated.size)
        assertEquals(1, learned.size)
    }

    @Test
    fun `lokal inzwischen zuordenbare Artikel werden ohne Server-Aufruf gesetzt`() = runTest {
        coEvery { shoppingDao.uncheckedItemsWithoutAisle() } returns open("Tomaten")

        assertEquals(1, assigner.assignRemote())

        assertEquals("Obst & Gemüse", updated.single().aisle)
        coVerify(exactly = 0) { apiProvider.api() }
    }

    @Test
    fun `ohne aktiven Markt gibt es keinen API-Aufruf`() = runTest {
        coEvery { storeDao.findActiveStore() } returns null
        coEvery { shoppingDao.uncheckedItemsWithoutAisle() } returns open("Geschenkpapier")

        assertEquals(0, assigner.assignRemote())
        coVerify(exactly = 0) { apiProvider.api() }
    }

    @Test
    fun `Fall 1 - Lexikon schlaegt Stichwort`() = runTest {
        assertEquals("Getränke", assigner.resolveLocal("Hafermilch", "M"))
    }

    @Test
    fun `Fall 2 - Klammerzusatz wird fuer das Lexikon entfernt`() = runTest {
        assertEquals("Getränke", assigner.resolveLocal("Hafermilch (Barista)", "M"))
    }

    @Test
    fun `Fall 3 - Tofu aus dem Lexikon`() = runTest {
        assertEquals("Kühlregal", assigner.resolveLocal("Tofu", "M"))
    }

    @Test
    fun `Fall 3b - ohne Lexikon fuehrt feinkost ueber die Ausweichkette ins Kuehlregal`() = runTest {
        coEvery { storeDao.findAisleForProduct("tofu", "M") } returns null
        assertEquals("Kühlregal", assigner.resolveLocal("Tofu", "M"))
    }

    @Test
    fun `Fall 4 - Stichwort Tomaten`() = runTest {
        assertEquals("Obst & Gemüse", assigner.resolveLocal("Tomaten", "M"))
    }

    @Test
    fun `Fall 5 - Bier faellt auf Getraenke zurueck`() = runTest {
        assertEquals("Getränke", assigner.resolveLocal("Bier", "M"))
    }

    @Test
    fun `Fall 6 - Spuelmittel faellt auf Drogerie und Haushalt zurueck`() = runTest {
        assertEquals("Drogerie & Haushalt", assigner.resolveLocal("Spülmittel", "M"))
    }

    @Test
    fun `Fall 7 - Hackfleisch ohne Fleisch-Gang bleibt leer`() = runTest {
        assertEquals("", assigner.resolveLocal("Hackfleisch", "M"))
    }

    @Test
    fun `Fall 8 - Geschenkpapier ohne Stichwort bleibt leer`() = runTest {
        assertEquals("", assigner.resolveLocal("Geschenkpapier", "M"))
    }

    @Test
    fun `Fall 9 - ohne aktiven Markt leer und kein Server-Aufruf`() = runTest {
        assertEquals("", assigner.resolveLocal("Tomaten", null))
        coVerify(exactly = 0) { apiProvider.api() }
    }

    @Test
    fun `bei zwei Gaengen mit gleichem Schluessel gewinnt die kleinere sortOrder`() = runTest {
        coEvery { storeDao.aislesForStore("M") } returns listOf(
            aisle("Gemüse hinten", 7, "obst_gemuese"),
            aisle("Gemüse vorne", 2, "obst_gemuese"),
        )
        assertEquals("Gemüse vorne", assigner.aisleForCategory("M", "obst_gemuese"))
    }

    @Test
    fun `sonstiges pfand und Unbekanntes ergeben keinen Gang`() = runTest {
        assertEquals("", assigner.aisleForCategory("M", "sonstiges"))
        assertEquals("", assigner.aisleForCategory("M", "pfand"))
        assertEquals("", assigner.aisleForCategory("M", "quatsch"))
        assertEquals("", assigner.aisleForCategory("M", ""))
    }
}
