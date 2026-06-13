package com.helga.android.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "product_purchases",
    indices = [
        Index(value = ["shoppingItemId"]),
        Index(value = ["offProductId"]),
        Index(value = ["offProductId", "purchaseDate"]),
        Index(value = ["updatedAt"]),
        Index(value = ["deleted"]),
    ],
)
data class ProductPurchaseEntity(
    @PrimaryKey val id: String,
    val shoppingItemId: String = "",
    val offProductId: String,
    val quantityPurchased: Double = 1.0,
    val pricePaid: Double = 0.0,
    val storeName: String = "",
    val purchaseDate: Long = 0L,
    val updatedAt: Long = 0L,
    val deleted: Int = 0,
    val dirty: Int = 0,
) {
    init {
        require(id.isNotBlank()) { "id cannot be blank" }
        require(offProductId.isNotBlank()) { "offProductId cannot be blank" }
    }
}
