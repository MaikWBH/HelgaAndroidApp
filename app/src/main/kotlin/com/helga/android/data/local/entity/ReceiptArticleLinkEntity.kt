package com.helga.android.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * "Persönliche DB": verknüpft einen normalisierten Bon-Artikelnamen
 * (`ReceiptItemNormalizer.normalize`) dauerhaft mit einem Open-Food-Facts-Produkt.
 * Einmal vom Nutzer bestätigt, gilt die Verknüpfung für alle künftigen Bons mit
 * demselben Artikelnamen. Wird synchronisiert (echte Nutzerdaten, kein Cache).
 */
@Entity(
    tableName = "receipt_article_links",
    indices = [
        Index(value = ["normalizedName"], unique = true),
        Index(value = ["updatedAt"]),
        Index(value = ["deleted"]),
    ],
)
data class ReceiptArticleLinkEntity(
    @PrimaryKey val id: String,
    val normalizedName: String = "",
    val displayName: String = "",
    val offProductId: String = "",
    val offBarcode: String = "",
    val confirmed: Int = 0,
    val confirmedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deleted: Int = 0,
    val dirty: Int = 0,
)
