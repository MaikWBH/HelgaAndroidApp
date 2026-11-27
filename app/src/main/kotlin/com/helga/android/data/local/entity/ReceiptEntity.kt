package com.helga.android.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "receipts",
    indices = [
        Index(value = ["storeId"]),
        Index(value = ["shoppingListId"]),
        Index(value = ["purchaseDate"]),
        Index(value = ["updatedAt"]),
        Index(value = ["deleted"]),
        Index(value = ["pendingUpload"]),
    ],
)
data class ReceiptEntity(
    @PrimaryKey val id: String,
    val storeId: String = "",
    val storeName: String = "",
    val shoppingListId: String = "",
    val purchaseDate: Long = 0L,
    val totalAmount: Double = 0.0,
    val currency: String = "EUR",
    val imagePath: String = "",
    val localImageUri: String = "",
    val rawOcrText: String = "",
    val status: String = "scanned",
    // Woher der Bon gelesen wurde ("ai" / "on_device") – wie localImageUri rein
    // gerätelokal, wird bewusst nicht synchronisiert (siehe SyncEngine.toDto()).
    val source: String = "on_device",
    // Phase 3a: Felder des Server-Archivs (siehe .claude/phasen/phase-3-daten/server-vertrag.md)
    val storeAddress: String = "",
    /** Kaufdatum wie gedruckt, `YYYY-MM-DD`; leer, wenn nicht erkannt. */
    val purchaseDay: String = "",
    /** Uhrzeit wie gedruckt, `HH:MM`. */
    val purchaseTime: String = "",
    val discountTotal: Double = 0.0,
    val depositTotal: Double = 0.0,
    val confidence: Double = 1.0,
    /** `none` · `queued` · `running` · `done` · `failed` — Zustand der KI-Auswertung auf dem Server. */
    val parseStatus: String = "none",
    /** 1 = Foto/Bon wartet noch auf den Upload zum Server (lokale Warteschlange). */
    val pendingUpload: Int = 0,
    /** Bis wann (Unix-ms) dieser Bon trotz Alter im lokalen Cache bleiben soll (z. B. frisch geöffnet). */
    val cachedUntil: Long = 0L,
    val updatedAt: Long = 0L,
    val deleted: Int = 0,
    val dirty: Int = 0,
)
