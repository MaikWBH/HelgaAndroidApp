package com.helga.android.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Lokaler Abzug des lernenden Produkt-Lexikons vom Server (Kürzel je Markt → Artikel).
 * Reiner Cache: Quelle der Wahrheit ist der Server (`/api/receipts/lexicon`), kein Sync-Mapper.
 */
@Immutable
@Entity(
    tableName = "receipt_lexicon",
    indices = [Index(value = ["storeKey"])],
)
data class ReceiptLexiconEntity(
    @PrimaryKey val id: String,
    val storeKey: String = "",
    val storeName: String = "",
    val rawText: String = "",
    val name: String = "",
    val category: String = "",
    val offBarcode: String = "",
    val hits: Int = 0,
    val updatedAt: Long = 0L,
)
