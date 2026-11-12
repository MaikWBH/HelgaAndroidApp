package com.helga.android.data.receipts

import com.helga.android.data.local.entity.ReceiptEntity
import com.helga.android.data.local.entity.ReceiptItemEntity
import kotlin.math.abs

/**
 * Deterministische Prüfregeln für gescannte Bons (Review): unsichere Positionen und Abweichung zwischen der
 * Summe der Positionen und dem Bon-Gesamtbetrag. Rein, ohne Android, voll testbar.
 */
object ReceiptReviewCheck {

    /** Unter diesem Konfidenz-Wert wird eine Position bzw. der ganze Bon zur Prüfung markiert. */
    const val CONFIDENCE_THRESHOLD = 0.7

    /** Positionen mit geringer Sicherheit oder ohne erkannten Preis (Pfand darf negativ oder 0 sein). */
    fun lowConfidenceIds(items: List<ReceiptItemEntity>): Set<String> =
        items.filter { it.confidence < CONFIDENCE_THRESHOLD || (it.isDeposit == 0 && it.totalPrice == 0.0) }
            .map { it.id }
            .toSet()

    /** Σ (Zeilenbetrag − Positionsrabatt). */
    fun itemsSum(items: List<ReceiptItemEntity>): Double = items.sumOf { it.totalPrice - it.discount }

    /** Erwarteter Bon-Gesamtbetrag: Positionen abzüglich Bon-Rabatt. */
    fun expectedTotal(items: List<ReceiptItemEntity>, discountTotal: Double): Double =
        itemsSum(items) - discountTotal

    /** Abweichung größer als 5 % (mindestens 0,10 €) vom gedruckten Gesamtbetrag. */
    fun sumMismatch(total: Double, items: List<ReceiptItemEntity>, discountTotal: Double): Boolean =
        total > 0.0 && abs(expectedTotal(items, discountTotal) - total) > maxOf(total * 0.05, 0.10)

    /** Soll der Nutzer den Bon vor dem Speichern genauer prüfen? */
    fun needsReview(receipt: ReceiptEntity, items: List<ReceiptItemEntity>): Boolean =
        lowConfidenceIds(items).isNotEmpty() ||
            receipt.confidence < CONFIDENCE_THRESHOLD ||
            sumMismatch(receipt.totalAmount, items, receipt.discountTotal)
}
