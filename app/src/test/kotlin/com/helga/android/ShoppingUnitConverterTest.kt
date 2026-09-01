package com.helga.android

import com.helga.android.data.util.ShoppingUnitConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests für ShoppingUnitConverter (einkaufsliste A3) — Mengeneinheiten-Familien g/kg, ml/l/cl. */
class ShoppingUnitConverterTest {

    @Test
    fun `identical units are always compatible`() {
        assertTrue(ShoppingUnitConverter.isCompatible("Stück", "Stück"))
    }

    @Test
    fun `mass units within the same family are compatible`() {
        assertTrue(ShoppingUnitConverter.isCompatible("g", "kg"))
        assertTrue(ShoppingUnitConverter.isCompatible("gramm", "gr"))
    }

    @Test
    fun `volume units within the same family are compatible`() {
        assertTrue(ShoppingUnitConverter.isCompatible("ml", "l"))
        assertTrue(ShoppingUnitConverter.isCompatible("cl", "l"))
    }

    @Test
    fun `mass and volume units are not compatible with each other`() {
        assertFalse(ShoppingUnitConverter.isCompatible("g", "ml"))
        assertFalse(ShoppingUnitConverter.isCompatible("kg", "l"))
    }

    @Test
    fun `unknown units are only compatible when exactly equal`() {
        assertTrue(ShoppingUnitConverter.isCompatible("Bund", "Bund"))
        assertFalse(ShoppingUnitConverter.isCompatible("Bund", "Stück"))
    }

    @Test
    fun `comparison is case-insensitive and trims whitespace`() {
        assertTrue(ShoppingUnitConverter.isCompatible(" KG ", "g"))
    }

    @Test
    fun `converts kg to g`() {
        assertEquals(1200.0, ShoppingUnitConverter.convert(1.2, "kg", "g"), 0.0001)
    }

    @Test
    fun `converts g to kg`() {
        assertEquals(0.2, ShoppingUnitConverter.convert(200.0, "g", "kg"), 0.0001)
    }

    @Test
    fun `converts l to ml`() {
        assertEquals(1500.0, ShoppingUnitConverter.convert(1.5, "l", "ml"), 0.0001)
    }

    @Test
    fun `converts cl to ml`() {
        assertEquals(50.0, ShoppingUnitConverter.convert(5.0, "cl", "ml"), 0.0001)
    }

    @Test
    fun `same unit conversion returns the quantity unchanged`() {
        assertEquals(42.0, ShoppingUnitConverter.convert(42.0, "g", "g"), 0.0001)
    }

    @Test
    fun `incompatible units return the quantity unchanged instead of a wrong result`() {
        assertEquals(3.0, ShoppingUnitConverter.convert(3.0, "Stück", "g"), 0.0001)
    }

    @Test
    fun `unknown units on either side return the quantity unchanged`() {
        assertEquals(3.0, ShoppingUnitConverter.convert(3.0, "Bund", "Prise"), 0.0001)
    }
}
