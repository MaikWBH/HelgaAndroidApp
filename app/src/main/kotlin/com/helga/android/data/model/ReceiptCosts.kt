package com.helga.android.data.model

// Ergebnistypen der Kassenbon-Auswertungen (von ReceiptDao-Abfragen zurückgegeben).

data class CostByStore(
    val storeId: String,
    val storeName: String,
    val totalAmount: Double,
    val receiptCount: Int,
)

data class CostByDate(
    val date: String, // "2026-06-15 HH:MM:SS"
    val totalAmount: Double,
    val receiptCount: Int,
)

data class CostSummary(
    val totalAmount: Double,
    val receiptCount: Int,
)
