package com.helga.android.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Ordnet einen [WeekplanDayMarkerEntity] einem Tag zu (wochenplan A11) — mehrere Marker pro Tag
 * möglich, ein Marker kann in mehreren Wochen wiederverwendet werden.
 */
@Immutable
@Entity(
    tableName = "weekplan_day_marker_assignments",
    indices = [
        Index(value = ["weekplanDayId"]),
        Index(value = ["markerId"]),
        Index(value = ["updatedAt"]),
        Index(value = ["deleted"]),
    ],
)
data class WeekplanDayMarkerAssignmentEntity(
    @PrimaryKey val id: String,
    val weekplanDayId: String,
    val markerId: String,
    val updatedAt: Long = 0L,
    val deleted: Int = 0,
    val dirty: Int = 0,
)
