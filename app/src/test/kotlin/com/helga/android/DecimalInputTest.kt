package com.helga.android

import com.helga.android.ui.common.DecimalInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DecimalInputTest {

    @Test
    fun komma_und_punkt_als_dezimalzeichen() {
        assertEquals(12.5, DecimalInput.parse("12,5")!!, 1e-9)
        assertEquals(12.5, DecimalInput.parse("12.5")!!, 1e-9)
    }

    @Test
    fun tausenderpunkt_mit_dezimalkomma() {
        assertEquals(1234.56, DecimalInput.parse("1.234,56")!!, 1e-9)
        assertEquals(1234.56, DecimalInput.parse("1,234.56")!!, 1e-9)
    }

    @Test
    fun euro_und_leerzeichen() {
        assertEquals(3.99, DecimalInput.parse(" 3,99 €")!!, 1e-9)
    }

    @Test
    fun mehrere_trenner_ungueltig() {
        assertNull(DecimalInput.parse("1,2,3"))
        assertNull(DecimalInput.parse("1.2.3"))
    }

    @Test
    fun buchstaben_ungueltig() {
        assertNull(DecimalInput.parse("abc"))
        assertNull(DecimalInput.parse("12a"))
    }

    @Test
    fun leer_und_minus() {
        assertNull(DecimalInput.parse(""))
        assertEquals(-0.5, DecimalInput.parse("-0,50")!!, 1e-9)
        assertEquals(0.5, DecimalInput.parse(",5")!!, 1e-9)
    }
}
