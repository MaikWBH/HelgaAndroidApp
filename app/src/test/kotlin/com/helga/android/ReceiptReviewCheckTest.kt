package com.helga.android

import com.helga.android.data.local.entity.ReceiptEntity
import com.helga.android.data.local.entity.ReceiptItemEntity
import com.helga.android.data.receipts.ReceiptReviewCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptReviewCheckTest {

    private fun item(
        id: String, price: Double, discount: Double = 0.0, deposit: Int = 0, confidence: Double = 1.0,
    ) = ReceiptItemEntity(id = id, receiptId = "r", totalPrice = price, discount = discount, isDeposit = deposit, confidence = confidence)

    @Test
    fun `low confidence is flagged and high confidence is not`() {
        val ids = ReceiptReviewCheck.lowConfidenceIds(listOf(item("a", 1.0, confidence = 0.69), item("b", 1.0, confidence = 0.7)))
        assertEquals(setOf("a"), ids)
    }

    @Test
    fun `missing price is flagged except for deposit lines`() {
        val ids = ReceiptReviewCheck.lowConfidenceIds(
            listOf(item("zero", 0.0), item("deposit0", 0.0, deposit = 1), item("deposit-", -0.25, deposit = 1), item("ok", 2.0))
        )
        assertEquals(setOf("zero"), ids)
    }

    @Test
    fun `items sum subtracts line discounts and expected total subtracts the receipt discount`() {
        val items = listOf(item("a", 2.0, discount = 0.5), item("b", 3.0))
        assertEquals(4.5, ReceiptReviewCheck.itemsSum(items), 1e-9)
        assertEquals(4.0, ReceiptReviewCheck.expectedTotal(items, 0.5), 1e-9)
    }

    @Test
    fun `deposit with negative amount reduces the sum`() {
        val items = listOf(item("a", 3.0), item("deposit", -0.25, deposit = 1))
        assertEquals(2.75, ReceiptReviewCheck.itemsSum(items), 1e-9)
    }

    @Test
    fun `sum mismatch uses five percent but at least ten cent`() {
        val items = listOf(item("a", 10.0))
        assertFalse(ReceiptReviewCheck.sumMismatch(10.4, items, 0.0)) // 0,40 <= max(0,52, 0,10)
        assertTrue(ReceiptReviewCheck.sumMismatch(11.0, items, 0.0))  // 1,00 > 0,55
        assertFalse(ReceiptReviewCheck.sumMismatch(1.05, listOf(item("a", 1.0)), 0.0)) // 0,05 <= 0,10
        assertTrue(ReceiptReviewCheck.sumMismatch(1.25, listOf(item("a", 1.0)), 0.0))  // 0,25 > 0,10
        assertFalse(ReceiptReviewCheck.sumMismatch(0.0, items, 0.0)) // ohne Gesamtbetrag keine Prüfung
    }

    @Test
    fun `receipt discount is considered in the mismatch check`() {
        assertFalse(ReceiptReviewCheck.sumMismatch(9.0, listOf(item("a", 10.0)), 1.0))
        assertTrue(ReceiptReviewCheck.sumMismatch(10.0, listOf(item("a", 10.0)), 3.0))
    }

    @Test
    fun `needsReview combines the three rules`() {
        val good = ReceiptEntity(id = "r", totalAmount = 5.0, confidence = 0.9)
        assertFalse(ReceiptReviewCheck.needsReview(good, listOf(item("a", 5.0))))
        assertTrue(ReceiptReviewCheck.needsReview(good, listOf(item("a", 5.0, confidence = 0.5))))
        assertTrue(ReceiptReviewCheck.needsReview(good.copy(confidence = 0.6), listOf(item("a", 5.0))))
        assertTrue(ReceiptReviewCheck.needsReview(good.copy(totalAmount = 9.0), listOf(item("a", 5.0))))
    }
}
