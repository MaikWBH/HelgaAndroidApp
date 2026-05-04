package com.helga.android.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "aisle_products",
    indices = [
        Index(value = ["storeId"]),
        Index(value = ["productName"]),
        Index(value = ["updatedAt"]),
        Index(value = ["deleted"]),
    ],
)
data class AisleProductEntity(
    @PrimaryKey val id: String,
    val aisleName: String = "",
    val productName: String = "",
    val storeId: String = "",
    val updatedAt: Long = 0L,
    val deleted: Int = 0,
    val dirty: Int = 0,
)
