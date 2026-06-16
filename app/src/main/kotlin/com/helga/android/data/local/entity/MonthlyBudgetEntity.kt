package com.helga.android.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Gemeinsames Monatsbudget der Familie. Singleton (id = "global"), wird wie
 * [WeekplanConstraintsEntity] über den Sync zentral auf dem Server gehalten,
 * sodass beide Handys denselben Wert sehen.
 *
 * - [amount]: Budget-Obergrenze pro Kalendermonat in EUR. 0 = kein Budget gesetzt.
 * - [warnThreshold]: Anteil (0..1) ab dem gewarnt wird (Standard 0.8 = 80 %).
 */
@Immutable
@Entity(
    tableName = "monthly_budgets",
    indices = [Index(value = ["updatedAt"]), Index(value = ["deleted"])],
)
data class MonthlyBudgetEntity(
    @PrimaryKey val id: String = "global",
    val amount: Double = 0.0,
    val warnThreshold: Double = 0.8,
    val updatedAt: Long = 0L,
    val deleted: Int = 0,
    val dirty: Int = 0,
)
