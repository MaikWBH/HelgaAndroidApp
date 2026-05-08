package com.helga.android.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "pantry_items",
    indices = [
        Index(value = ["updatedAt"]),
        Index(value = ["deleted"]),
        Index(value = ["dirty"]),
    ],
)
data class PantryItemEntity(
    @PrimaryKey val id: String,
    val name: String = "",
    val quantity: Double = 0.0,
    val unit: String = "",
    val category: String = "",
    val expiresAt: String = "",
    val updatedAt: Long = 0L,
    val deleted: Int = 0,
    val dirty: Int = 0,
)
