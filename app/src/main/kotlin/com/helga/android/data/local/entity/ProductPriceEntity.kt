package com.helga.android.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "product_prices",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = OffProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["offProductId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("offProductId"),
        Index("storeName"),
        Index("updated_at"),
    ]
)
data class ProductPriceEntity(
    val id: String, // "{offProductId}_{storeId}_{currency}"
    val offProductId: String,
    val storeName: String, // z.B. "Rewe Berlin"
    val currency: String = "EUR",
    val price: Double, // z.B. 1.99
    val unit: String = "", // z.B. "pro Flasche"
    val lastCheckedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deleted: Int = 0,
    val dirty: Int = 0,
)
