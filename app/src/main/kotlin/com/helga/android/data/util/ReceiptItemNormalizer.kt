package com.helga.android.data.util

object ReceiptItemNormalizer {
    // Trailing single tax-class letter (A/B/...), "€", or "EUR" after whitespace
    private val TRAILING_SUFFIX = Regex("""\s+(?:[a-z]|€|eur)$""")

    fun normalize(raw: String): String = raw
        .trim()
        .lowercase()
        .replace(Regex("""\s+"""), " ")
        .replace(TRAILING_SUFFIX, "")
}
