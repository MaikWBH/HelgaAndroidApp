package com.helga.android.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "store_aisles",
    indices = [
        Index(value = ["storeId"]),
        Index(value = ["updatedAt"]),
        Index(value = ["deleted"]),
    ],
)
data class StoreAisleEntity(
    @PrimaryKey val id: String,
    val storeId: String,
    val aisleName: String = "",
    val sortOrder: Int = 0,
    val updatedAt: Long = 0L,
    val deleted: Int = 0,
    val dirty: Int = 0,
)
