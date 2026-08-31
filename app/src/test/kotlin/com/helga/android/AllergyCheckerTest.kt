package com.helga.android

import com.helga.android.data.local.entity.OffProductEntity
import com.helga.android.data.util.AllergyChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests für AllergyChecker – sicherheitsrelevanteste Logik der App (naehrwerte A1).
 * Reines JVM-Test wie SyncLwwTest, keine Android-Runtime nötig.
 */
class AllergyCheckerTest {

    private fun product(allergenes: String) = OffProductEntity(id = "p1", allergenes = allergenes)

    @Test
    fun `empty user profile finds nothing`() {
        val result = AllergyChecker.hasAllergens(product("""["Gluten","Milch"]"""), emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `product without allergens finds nothing`() {
        val result = AllergyChecker.hasAllergens(product(""), listOf("Gluten"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `exact match is found`() {
        val result = AllergyChecker.hasAllergens(product("""["Gluten","Milch"]"""), listOf("Milch"))
        assertEquals(listOf("Milch"), result)
    }

    @Test
    fun `match is case-insensitive`() {
        val result = AllergyChecker.hasAllergens(product("""["GLUTEN"]"""), listOf("gluten"))
        assertEquals(listOf("GLUTEN"), result)
    }

    @Test
    fun `partial match when user allergy is substring of product allergen`() {
        // Produkt listet "Weizengluten", Nutzerprofil nur "Gluten"
        val result = AllergyChecker.hasAllergens(product("""["Weizengluten"]"""), listOf("Gluten"))
        assertEquals(listOf("Weizengluten"), result)
    }

    @Test
    fun `partial match when product allergen is substring of user allergy`() {
        // Produkt listet nur "Nuss", Nutzerprofil hat spezifischer "Erdnuss"
        val result = AllergyChecker.hasAllergens(product("""["Nuss"]"""), listOf("Erdnuss"))
        assertEquals(listOf("Nuss"), result)
    }

    @Test
    fun `only matching allergens are returned, not the full product list`() {
        val result = AllergyChecker.hasAllergens(
            product("""["Gluten","Soja","Sellerie"]"""),
            listOf("Soja"),
        )
        assertEquals(listOf("Soja"), result)
    }

    @Test
    fun `multiple user allergies can each match different product allergens`() {
        val result = AllergyChecker.hasAllergens(
            product("""["Gluten","Soja","Sellerie"]"""),
            listOf("Soja", "Gluten"),
        )
        assertEquals(setOf("Gluten", "Soja"), result.toSet())
    }

    @Test
    fun `no match when allergens are unrelated`() {
        val result = AllergyChecker.hasAllergens(product("""["Fisch"]"""), listOf("Laktose"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `empty array string finds nothing`() {
        val result = AllergyChecker.hasAllergens(product("[]"), listOf("Gluten"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `whitespace around entries is trimmed`() {
        val result = AllergyChecker.hasAllergens(product("""[ "Gluten" , "Milch" ]"""), listOf("Milch"))
        assertEquals(listOf("Milch"), result)
    }

    @Test
    fun `hasAnyAllergen is true when a match exists`() {
        assertTrue(AllergyChecker.hasAnyAllergen(product("""["Gluten"]"""), listOf("Gluten")))
    }

    @Test
    fun `hasAnyAllergen is false when no match exists`() {
        assertFalse(AllergyChecker.hasAnyAllergen(product("""["Fisch"]"""), listOf("Laktose")))
    }

    @Test
    fun `hasAnyAllergen is false for empty user profile`() {
        assertFalse(AllergyChecker.hasAnyAllergen(product("""["Gluten"]"""), emptyList()))
    }
}
