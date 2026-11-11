package com.helga.android

import com.helga.android.ui.common.formatEuro
import org.junit.Assert.assertEquals
import org.junit.Test

class CurrencyFormatTest {

    private fun normalized(amount: Double) =
        formatEuro(amount).replace(' ', ' ').replace(' ', ' ')

    @Test fun zero() = assertEquals("0,00 €", normalized(0.0))

    @Test fun smallAmount() = assertEquals("12,30 €", normalized(12.3))

    @Test fun thousandsSeparator() = assertEquals("1.234,50 €", normalized(1234.5))

    @Test fun negative() = assertEquals("-5,00 €", normalized(-5.0))
}
