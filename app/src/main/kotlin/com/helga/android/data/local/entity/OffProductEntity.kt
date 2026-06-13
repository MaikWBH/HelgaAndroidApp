package com.helga.android.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "off_products",
    indices = [
        Index(value = ["barcode"], unique = true),
        Index(value = ["updatedAt"]),
        Index(value = ["deleted"]),
    ],
)
data class OffProductEntity(
    @PrimaryKey val id: String, // barcode/EAN
    val barcode: String,
    val name: String = "",
    val brand: String = "",
    val categories: String = "[]", // JSON array
    val kcalPerUnit: Double = 0.0, // pro 100g
    val proteins: Double = 0.0, // g/100g
    val fats: Double = 0.0, // g/100g
    val carbs: Double = 0.0, // g/100g
    val nutriScore: String = "", // "a"-"e"
    val nova: Int = 0, // 1-4
    val ecoScore: String = "", // "a"-"e"
    val allergenes: String = "[]", // JSON array
    val additives: String = "[]", // JSON array of E-numbers
    val isOrganic: Int = 0, // 0 or 1
    val vegan: Int = 0,
    val vegetarian: Int = 0,
    val imagePath: String = "",
    val isFavorite: Int = 0, // 1 = Teil des persönlichen Katalogs "Meine Produkte"
    val updatedAt: Long = 0L,
    val deleted: Int = 0,
    val dirty: Int = 0,
)
