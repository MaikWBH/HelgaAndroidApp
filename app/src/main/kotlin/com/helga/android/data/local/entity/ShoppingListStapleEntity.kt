package com.helga.android.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "shopping_list_staples",
    indices = [
        Index(value = ["listId"]),
        Index(value = ["updatedAt"]),
        Index(value = ["deleted"]),
    ],
)
data class ShoppingListStapleEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val name: String = "",
    val quantity: Double = 1.0,
    val sortOrder: Int = 0,
    val updatedAt: Long = 0L,
    val deleted: Int = 0,
    val dirty: Int = 0,
)
