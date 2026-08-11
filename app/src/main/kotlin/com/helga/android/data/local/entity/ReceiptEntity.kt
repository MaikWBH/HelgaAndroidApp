package com.helga.android.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "receipts",
    indices = [
        Index(value = ["storeId"]),
        Index(value = ["shoppingListId"]),
        Index(value = ["purchaseDate"]),
        Index(value = ["updatedAt"]),
        Index(value = ["deleted"]),
    ],
)
data class ReceiptEntity(
    @PrimaryKey val id: String,
    val storeId: String = "",
    val storeName: String = "",
    val shoppingListId: String = "",
    val purchaseDate: Long = 0L,
    val totalAmount: Double = 0.0,
    val currency: String = "EUR",
    val imagePath: String = "",
    val localImageUri: String = "",
    val rawOcrText: String = "",
    val status: String = "scanned",
    // Woher der Bon gelesen wurde ("ai" / "on_device") – wie localImageUri rein
    // gerätelokal, wird bewusst nicht synchronisiert (siehe SyncEngine.toDto()).
    val source: String = "on_device",
    val updatedAt: Long = 0L,
    val deleted: Int = 0,
    val dirty: Int = 0,
)
