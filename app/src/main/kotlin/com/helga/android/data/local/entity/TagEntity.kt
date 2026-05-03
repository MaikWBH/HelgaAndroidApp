package com.helga.android.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "recipe_tags",
    indices = [
        Index(value = ["recipeId"]),
        Index(value = ["name"]),
        Index(value = ["updatedAt"]),
        Index(value = ["dirty"]),
    ],
)
data class TagEntity(
    @PrimaryKey val id: String,
    val recipeId: String,
    val name: String = "",
    val updatedAt: Long = 0L,
    val deleted: Int = 0,
    val dirty: Int = 0,
)
