package com.helga.android

import com.helga.android.data.util.ReceiptItemNormalizer
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests für ReceiptItemNormalizer (bons-kosten A1) — Rohtext von OCR-Kassenbons bereinigen. */
class ReceiptItemNormalizerTest {

    @Test
    fun `lowercases the text`() {
        assertEquals("milch", ReceiptItemNormalizer.normalize("MILCH"))
    }

    @Test
    fun `trims leading and trailing whitespace`() {
        assertEquals("brot", ReceiptItemNormalizer.normalize("  Brot  "))
    }

    @Test
    fun `collapses multiple internal spaces into one`() {
        assertEquals("käse 2,50", ReceiptItemNormalizer.normalize("Käse    2,50"))
    }

    @Test
    fun `strips a trailing single-letter tax class marker`() {
        assertEquals("milch 1,29", ReceiptItemNormalizer.normalize("Milch 1,29 A"))
    }

    @Test
    fun `strips a trailing EUR marker`() {
        assertEquals("käse 2,50", ReceiptItemNormalizer.normalize("Käse   2,50  EUR"))
    }

    @Test
    fun `strips a trailing euro sign`() {
        assertEquals("brot 3,00", ReceiptItemNormalizer.normalize("Brot 3,00 €"))
    }

    @Test
    fun `does not strip a multi-letter unit suffix without a preceding space`() {
        assertEquals("bananen 1kg", ReceiptItemNormalizer.normalize("Bananen 1kg"))
    }

    @Test
    fun `does not touch a line without any trailing suffix`() {
        assertEquals("bananen 1,50", ReceiptItemNormalizer.normalize("Bananen 1,50"))
    }

    @Test
    fun `empty string stays empty`() {
        assertEquals("", ReceiptItemNormalizer.normalize(""))
    }
}
