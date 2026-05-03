package com.helga.android.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "recipe_instructions",
    indices = [
        Index(value = ["recipeId"]),
        Index(value = ["updatedAt"]),
        Index(value = ["dirty"]),
    ],
)
data class InstructionEntity(
    @PrimaryKey val id: String,
    val recipeId: String,
    val position: Int = 0,
    val text: String = "",
    val updatedAt: Long = 0L,
    val deleted: Int = 0,
    val dirty: Int = 0,
)
