package com.helga.android.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Nutzerdefinierter, wiederverwendbarer Tagesmarker (wochenplan A11) — z. B. "Resteessen" oder
 * "Kinder bei Oma", frei anlegbar/löschbar unter Einstellungen. Getrennt von der Zuordnung zu
 * einem konkreten Tag ([WeekplanDayMarkerAssignmentEntity]), damit Name und Farbe über die ganze
 * Woche konsistent bleiben statt bei jeder Verwendung neu eingetippt zu werden.
 */
@Immutable
@Entity(
    tableName = "weekplan_day_markers",
    indices = [Index(value = ["updatedAt"]), Index(value = ["deleted"])],
)
data class WeekplanDayMarkerEntity(
    @PrimaryKey val id: String,
    val name: String = "",
    /** Hex-Farbcode, z. B. "#4CAF50" — dezente Tonfarbe, nie alleiniger Träger der Information. */
    val color: String = "",
    val updatedAt: Long = 0L,
    val deleted: Int = 0,
    val dirty: Int = 0,
)
