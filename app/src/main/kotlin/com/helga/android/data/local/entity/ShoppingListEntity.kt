package com.helga.android.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "shopping_lists",
    indices = [Index(value = ["updatedAt"]), Index(value = ["deleted"])],
)
data class ShoppingListEntity(
    @PrimaryKey val id: String,
    val name: String = "",
    val isActive: Int = 0,
    val isDefaultWeekplan: Int = 0,
    val isDefaultRecipe: Int = 0,
    val updatedAt: Long = 0L,
    val deleted: Int = 0,
    val dirty: Int = 0,
)
