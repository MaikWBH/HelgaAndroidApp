package com.helga.android

import com.helga.android.data.local.dao.ShoppingDao
import com.helga.android.data.local.dao.StoreDao
import com.helga.android.data.local.entity.StoreAisleEntity
import com.helga.android.data.remote.ApiProvider
import com.helga.android.data.shopping.AisleAssigner
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
        assigner = AisleAssigner(storeDao, shoppingDao, apiProvider)
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
