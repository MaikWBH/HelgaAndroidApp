package com.helga.android.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Lokaler Abzug der Server-Auswertung (`GET /api/receipts/aggregates`), damit die Diagramme offline für die
 * gesamte Zeit funktionieren. Reiner Cache: wird nach jedem Bon-Abgleich vollständig ersetzt, kein Sync-Mapper.
 *
 * `dimension`: `total` · `week` · `store` · `category` · `product`; `period`: `YYYY-MM` bzw. `YYYY-Www`.
 */
@Immutable
@Entity(
    tableName = "receipt_aggregates",
    indices = [Index(value = ["dimension"]), Index(value = ["period"])],
)
data class ReceiptAggregateEntity(
    /** `"$dimension|$period|$key"` */
    @PrimaryKey val id: String,
    val period: String,
    val dimension: String,
    val key: String = "",
    val label: String = "",
    val amount: Double = 0.0,
    val quantity: Double = 0.0,
    val avgUnitPrice: Double = 0.0,
    val discount: Double = 0.0,
    val count: Int = 0,
)
