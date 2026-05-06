package com.helga.android.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "weekplan_template_entries",
    indices = [Index(value = ["templateId"])],
)
data class WeekplanTemplateEntryEntity(
    @PrimaryKey val id: String,
    val templateId: String,
    val dayOffset: Int,   // 0 = Montag, 1 = Dienstag, …, 6 = Sonntag
    val recipeId: String,
    val position: Int = 0,
)
