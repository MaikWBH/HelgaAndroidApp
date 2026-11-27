package com.helga.android.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "receipt_items",
    foreignKeys = [
        ForeignKey(
            entity = ReceiptEntity::class,
            parentColumns = ["id"],
            childColumns = ["receiptId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["receiptId"]),
        Index(value = ["updatedAt"]),
        Index(value = ["deleted"]),
    ],
)
data class ReceiptItemEntity(
    @PrimaryKey val id: String,
    val receiptId: String,
    val position: Int = 0,
    val rawText: String = "",
    val name: String = "",
    val quantity: Double = 1.0,
    val unitPrice: Double = 0.0,
    val totalPrice: Double = 0.0,
    // Phase 3a: Einheit, Rabatt, Pfand, Kategorie, bestätigtes OFF-Produkt, KI-Sicherheit
    val unit: String = "",
    val discount: Double = 0.0,
    val isDeposit: Int = 0,
    val category: String = "",
    val offBarcode: String = "",
    val confidence: Double = 1.0,
    val matchedShoppingItemId: String = "",
    val matchStatus: String = "",
    val updatedAt: Long = 0L,
    val deleted: Int = 0,
    val dirty: Int = 0,
)
