package com.helga.android.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "weekplan_settings",
    indices = [Index(value = ["updatedAt"]), Index(value = ["deleted"])],
)
data class WeekplanSettingsEntity(
    @PrimaryKey val id: String = "global",
    val planDays: Int = 7,
    val shoppingDay: Int = 0,
    val updatedAt: Long = 0L,
    val deleted: Int = 0,
    val dirty: Int = 0,
)
