package com.helga.android.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Lokaler Cache eines Open-Food-Facts-Produkts. Die Tabelle wird bereits seit
 * MIGRATION_15_16 per Roh-SQL geführt; ab Version 27 ist sie eine echte
 * Room-Entity, damit Nährwerte offline verfügbar sind.
 *
 * `packageGrams` ist die aus den OFF-Rohdaten geparste Packungsgröße in Gramm
 * (0.0 = unbekannt). `packageGramsManual = 1` markiert eine manuelle Korrektur,
 * damit ein späterer Cache-Refresh sie nicht überschreibt.
 */
@Entity(
    tableName = "off_products",
    indices = [
        Index(value = ["barcode"], unique = true),
        Index(value = ["updatedAt"]),
        Index(value = ["deleted"]),
    ],
)
data class OffProductEntity(
    @PrimaryKey val id: String,
    val barcode: String = "",
    val name: String = "",
    val brand: String = "",
    val categories: String = "[]",
    val kcalPerUnit: Double = 0.0,
    val proteins: Double = 0.0,
    val fats: Double = 0.0,
    val carbs: Double = 0.0,
    val nutriScore: String = "",
    val nova: Int = 0,
    val ecoScore: String = "",
    val allergenes: String = "[]",
    val additives: String = "[]",
    val isOrganic: Int = 0,
    val vegan: Int = 0,
    val vegetarian: Int = 0,
    val imagePath: String = "",
    val isFavorite: Int = 0,
    val packageGrams: Double = 0.0,
    val packageGramsManual: Int = 0,
    val updatedAt: Long = 0L,
    val deleted: Int = 0,
    val dirty: Int = 0,
)
