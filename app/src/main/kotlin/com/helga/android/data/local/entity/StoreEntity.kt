package com.helga.android.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "stores",
    indices = [Index(value = ["updatedAt"]), Index(value = ["deleted"])],
)
data class StoreEntity(
    @PrimaryKey val id: String,
    val name: String = "",
    val isActive: Int = 0,
    val updatedAt: Long = 0L,
    val deleted: Int = 0,
    val dirty: Int = 0,
)
