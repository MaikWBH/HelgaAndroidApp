package com.helga.android.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "quick_emojis",
    indices = [Index(value = ["updatedAt"]), Index(value = ["deleted"])],
)
data class QuickEmojiEntity(
    @PrimaryKey val id: String,
    val emoji: String = "",
    val food: String = "",
    val quantity: Double = 1.0,
    val unit: String = "",
    val sortOrder: Int = 0,
    val updatedAt: Long = 0L,
    val deleted: Int = 0,
    val dirty: Int = 0,
)
