package com.helga.android

import com.helga.android.data.local.dao.StoreDao
import com.helga.android.data.local.entity.StoreAisleEntity
import com.helga.android.data.local.entity.StoreEntity
import com.helga.android.data.model.ProductCategory
import com.helga.android.data.repository.StorePrefill
import com.helga.android.data.repository.StoreRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Gang-Kategorien (Phase 3b): Vorlage, Kopie, Namens-Erkennung und Ergänzung fehlender Standardgänge. */
class StoreRepositoryAisleTest {

    private val dao = mockk<StoreDao>(relaxed = true)
    private val upsertedAisles = mutableListOf<StoreAisleEntity>()
    private val existing = mutableListOf<StoreAisleEntity>()
    private lateinit var repository: StoreRepository

    @Before
    fun setUp() {
        coEvery { dao.upsertAisles(any()) } answers { upsertedAisles += firstArg<List<StoreAisleEntity>>() }
        coEvery { dao.upsertAisle(any()) } answers { upsertedAisles += firstArg<StoreAisleEntity>() }
        coEvery { dao.upsertStore(any<StoreEntity>()) } returns Unit
        coEvery { dao.aislesForStore(any()) } answers { existing.toList() }
        repository = StoreRepository(dao)
    }

    private fun aisle(name: String, order: Int, key: String = "") =
        StoreAisleEntity(id = "a$order", storeId = "s", aisleName = name, sortOrder = order, categoryKey = key)

    @Test
    fun `Vorlage erzeugt 15 Gaenge mit Schluesseln in Katalogreihenfolge`() = runTest {
        repository.createStore("REWE", StorePrefill.DefaultTemplate)

        assertEquals(15, upsertedAisles.size)
        assertEquals(ProductCategory.aisleCategories.map { it.key }, upsertedAisles.map { it.categoryKey })
        assertEquals(ProductCategory.aisleCategories.map { it.defaultAisleName }, upsertedAisles.map { it.aisleName })
        assertEquals((0..14).toList(), upsertedAisles.map { it.sortOrder })
        assertTrue(upsertedAisles.all { it.dirty == 1 })
    }

    @Test
    fun `Kopie uebernimmt Namen und Schluessel`() = runTest {
        existing += aisle("Frisches", 0, "obst_gemuese")
        existing += aisle("Kasse", 1)

        repository.createStore("Aldi", StorePrefill.CopyFrom("s"))

        assertEquals(listOf("Frisches" to "obst_gemuese", "Kasse" to ""), upsertedAisles.map { it.aisleName to it.categoryKey })
        assertTrue(upsertedAisles.all { it.dirty == 1 })
    }

    @Test
    fun `addAisle erkennt Standardnamen ohne Gross-Kleinschreibung`() = runTest {
        repository.addAisle("s", " fisch ")
        repository.addAisle("s", "Kasse")
        repository.addAisle("s", "Kasse 2", "haushalt")

        assertEquals("fisch", upsertedAisles[0].categoryKey)
        assertEquals("fisch", upsertedAisles[0].aisleName) // Name bleibt wie eingegeben (getrimmt)
        assertEquals("", upsertedAisles[1].categoryKey)
        assertEquals("haushalt", upsertedAisles[2].categoryKey)
        assertTrue(upsertedAisles.all { it.dirty == 1 })
    }

    @Test
    fun `setAisleCategory setzt Schluessel und dirty`() = runTest {
        val original = aisle("Kasse", 0)

        repository.setAisleCategory(original, "getraenke")

        val saved = upsertedAisles.single()
        assertEquals("getraenke", saved.categoryKey)
        assertEquals(1, saved.dirty)
        assertTrue(saved.updatedAt > 0L)
    }

    @Test
    fun `addMissingDefaultAisles ergaenzt bei den alten Standardgaengen genau fuenf`() = runTest {
        val old = listOf(
            "Obst & Gemüse" to "obst_gemuese", "Brot & Backwaren" to "brot_backwaren",
            "Milchprodukte & Eier" to "milch_eier", "Fleisch & Wurst" to "fleisch_wurst", "Fisch" to "fisch",
            "Tiefkühl" to "tiefkuehl", "Konserven & Trockenware" to "trockenware", "Getränke" to "getraenke",
            "Süßes & Snacks" to "suesses_snacks", "Drogerie & Haushalt" to "drogerie",
        )
        old.forEachIndexed { i, (name, key) -> existing += aisle(name, i, key) }

        val added = repository.addMissingDefaultAisles("s")

        assertEquals(5, added)
        assertEquals(
            listOf("wuerzen_backen", "fruehstueck", "alkohol", "feinkost", "haushalt"),
            upsertedAisles.map { it.categoryKey },
        )
        assertEquals(listOf(10, 11, 12, 13, 14), upsertedAisles.map { it.sortOrder })
        assertTrue(upsertedAisles.all { it.dirty == 1 })

        // Zweiter Aufruf: alles vorhanden
        existing += upsertedAisles
        upsertedAisles.clear()
        assertEquals(0, repository.addMissingDefaultAisles("s"))
        assertTrue(upsertedAisles.isEmpty())
    }
}
