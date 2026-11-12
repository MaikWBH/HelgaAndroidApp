package com.helga.android.ui.receipts

import com.helga.android.data.local.entity.ReceiptEntity
import com.helga.android.data.receipts.ReceiptDates
import java.time.ZoneId

/** Auswertungsstatus eines Bons für die Anzeige (ein Chip pro Zeile). */
enum class ReceiptStatusKind { PROVISIONAL, REVIEW, FAILED }

/** `failed` hat Vorrang vor `provisional`, dieser vor `review`; fertige Bons haben keinen Chip. */
fun receiptStatusKind(receipt: ReceiptEntity): ReceiptStatusKind? = when {
    receipt.parseStatus == "failed" -> ReceiptStatusKind.FAILED
    receipt.status == "provisional" -> ReceiptStatusKind.PROVISIONAL
    receipt.status == "review" -> ReceiptStatusKind.REVIEW
    else -> null
}

/** `YYYY-MM` aus dem gedruckten Tag, sonst aus dem Kaufzeitpunkt; unbekannt → leer. */
fun receiptMonthKey(receipt: ReceiptEntity, zone: ZoneId = ZoneId.systemDefault()): String {
    val day = receipt.purchaseDay.takeIf { ReceiptDates.isValidDay(it) }
        ?: ReceiptDates.epochMsToDay(receipt.purchaseDate, zone)
    return day.take(7)
}

/** Gruppiert die (bereits nach Datum absteigend sortierte) Liste nach Monat, Reihenfolge bleibt erhalten. */
fun groupReceiptsByMonth(
    receipts: List<ReceiptEntity>,
    zone: ZoneId = ZoneId.systemDefault(),
): List<Pair<String, List<ReceiptEntity>>> {
    val groups = LinkedHashMap<String, MutableList<ReceiptEntity>>()
    receipts.forEach { groups.getOrPut(receiptMonthKey(it, zone)) { mutableListOf() }.add(it) }
    return groups.map { it.key to it.value }
}
