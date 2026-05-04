package com.helga.android.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "weekplan_extras",
    indices = [
        Index(value = ["weekplanDayId"]),
        Index(value = ["updatedAt"]),
        Index(value = ["deleted"]),
    ],
)
data class WeekplanExtraEntity(
    @PrimaryKey val id: String,
    val weekplanDayId: String,
    val itemText: String = "",
    val position: Int = 0,
    val updatedAt: Long = 0L,
    val deleted: Int = 0,
    val dirty: Int = 0,
)
