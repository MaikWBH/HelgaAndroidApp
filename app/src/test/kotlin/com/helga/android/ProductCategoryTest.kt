package com.helga.android

import com.helga.android.data.model.ProductCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProductCategoryTest {

    // Identisch zu server/tests/test_categories.py (EXPECTED_KEYS)
    private val expectedKeys = listOf(
        "obst_gemuese", "brot_backwaren", "milch_eier", "fleisch_wurst", "fisch", "tiefkuehl",
        "trockenware", "wuerzen_backen", "fruehstueck", "getraenke", "alkohol", "suesses_snacks",
        "feinkost", "drogerie", "haushalt", "sonstiges", "pfand",
    )

    @Test
    fun `keys are exact and in catalog order`() {
        assertEquals(expectedKeys, ProductCategory.entries.map { it.key })
    }

    @Test
    fun `fromKey trims lowercases and rejects unknown values`() {
        assertEquals(ProductCategory.MILCH_EIER, ProductCategory.fromKey(" Milch_Eier "))
        assertNull(ProductCategory.fromKey("x"))
        assertNull(ProductCategory.fromKey(""))
        assertNull(ProductCategory.fromKey(null))
    }

    @Test
    fun `there are 15 aisle categories and sonstiges and pfand are not among them`() {
        assertEquals(15, ProductCategory.aisleCategories.size)
        assertEquals(expectedKeys.take(15), ProductCategory.aisleCategories.map { it.key })
    }

    @Test
    fun `fallback chain matches the catalog table`() {
        val expected = mapOf(
            ProductCategory.WUERZEN_BACKEN to ProductCategory.TROCKENWARE,
            ProductCategory.FRUEHSTUECK to ProductCategory.TROCKENWARE,
            ProductCategory.FEINKOST to ProductCategory.MILCH_EIER,
            ProductCategory.ALKOHOL to ProductCategory.GETRAENKE,
            ProductCategory.FISCH to ProductCategory.FLEISCH_WURST,
            ProductCategory.HAUSHALT to ProductCategory.DROGERIE,
            ProductCategory.DROGERIE to ProductCategory.HAUSHALT,
        )
        ProductCategory.entries.forEach { category ->
            assertEquals(category.key, expected[category], ProductCategory.fallbackFor(category))
        }
    }
}
