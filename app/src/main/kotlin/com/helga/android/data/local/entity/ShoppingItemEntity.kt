package com.helga.android.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "shopping_items",
    indices = [
        Index(value = ["listId"]),
        Index(value = ["updatedAt"]),
        Index(value = ["deleted"]),
    ],
)
data class ShoppingItemEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val name: String = "",
    val quantity: Double = 1.0,
    val unit: String = "",
    val aisle: String = "",
    val source: String = "manual",
    val isChecked: Int = 0,
    val sortOrder: Int = 0,
    val origins: String = "[]", // JSON-Liste von ItemOrigin: Rezept-Herkünfte
    val offBarcode: String = "", // OFF barcode/EAN
    val offProductId: String = "", // link to OffProductEntity
    val priceEstimate: Double = 0.0, // cached price
    val priceLastChecked: Long = 0L,
    val updatedAt: Long = 0L,
    val deleted: Int = 0,
    val dirty: Int = 0,
)
