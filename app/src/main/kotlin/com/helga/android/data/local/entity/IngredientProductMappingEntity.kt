package com.helga.android.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Globale Zuordnung eines (normalisierten) Zutatnamens zu einem konkreten Produkt
 * aus dem persönlichen Katalog ("Meine Produkte"). Gilt für ALLE Rezepte: steht im
 * Rezept generisch "Butter", landet beim Export genau das hier verknüpfte Produkt
 * auf der Einkaufsliste (inkl. Nährwerte + Preis).
 */
@Entity(
    tableName = "ingredient_product_mappings",
    indices = [
        Index(value = ["ingredientName"], unique = true),
        Index(value = ["updatedAt"]),
        Index(value = ["deleted"]),
    ],
)
data class IngredientProductMappingEntity(
    @PrimaryKey val id: String,
    val ingredientName: String, // normalisiert: lowercase().trim()
    val offProductId: String = "",
    val offBarcode: String = "",
    val displayName: String = "", // Produktname für die Anzeige ("Kerrygold Butter")
    val updatedAt: Long = 0L,
    val deleted: Int = 0,
    val dirty: Int = 0,
)
