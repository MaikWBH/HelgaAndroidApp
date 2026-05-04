package com.helga.android.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "weekplan_days",
    indices = [
        Index(value = ["planDate"]),
        Index(value = ["updatedAt"]),
        Index(value = ["deleted"]),
    ],
)
data class WeekplanDayEntity(
    @PrimaryKey val id: String,
    val planDate: String = "",
    val note: String = "",
    val updatedAt: Long = 0L,
    val deleted: Int = 0,
    val dirty: Int = 0,
)
