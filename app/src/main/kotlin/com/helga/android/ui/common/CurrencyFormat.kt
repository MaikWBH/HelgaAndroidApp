package com.helga.android.ui.common

import java.text.NumberFormat
import java.util.Locale

fun formatEuro(amount: Double): String =
    NumberFormat.getCurrencyInstance(Locale.GERMANY).format(amount)
