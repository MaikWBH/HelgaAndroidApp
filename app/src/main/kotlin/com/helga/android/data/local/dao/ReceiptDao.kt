package com.helga.android.data.local.dao


import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.helga.android.data.local.entity.ReceiptEntity
import com.helga.android.data.local.entity.ReceiptItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDao {

    @Query("SELECT * FROM receipts WHERE deleted = 0 ORDER BY purchaseDate DESC")
    fun observeReceipts(): Flow<List<ReceiptEntity>>

    @Query("SELECT * FROM receipts WHERE id = :id")
    fun observeById(id: String): Flow<ReceiptEntity?>

    @Query("SELECT * FROM receipts WHERE id = :id")
    suspend fun findById(id: String): ReceiptEntity?

    @Query("SELECT * FROM receipt_items WHERE receiptId = :receiptId AND deleted = 0 ORDER BY position ASC")
    fun observeItems(receiptId: String): Flow<List<ReceiptItemEntity>>

    @Query("SELECT * FROM receipt_items WHERE receiptId = :receiptId AND deleted = 0 ORDER BY position ASC")
    suspend fun itemsForReceipt(receiptId: String): List<ReceiptItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReceipt(receipt: ReceiptEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReceipts(receipts: List<ReceiptEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: ReceiptItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<ReceiptItemEntity>)

    // ── Bon-Archiv: Warteschlange und Cache (Phase 3a) ───────────────────────

    /** Bons, deren Foto/Scan noch nicht zum Server hochgeladen ist (lokale Warteschlange). */
    @Query("SELECT * FROM receipts WHERE pendingUpload = 1 AND deleted = 0")
    suspend fun pendingUploads(): List<ReceiptEntity>

    /** Lokale Änderungen, die per `PUT /api/receipts/{id}` an den Server gehen (Warteschlangen-Bons ausgenommen). */
    @Query("SELECT * FROM receipts WHERE dirty = 1 AND pendingUpload = 0")
    suspend fun dirtyReceiptsForPush(): List<ReceiptEntity>

    /** Alle Positionen eines Bons, auch gelöschte (für den Abgleich mit der Server-Positionsliste). */
    @Query("SELECT * FROM receipt_items WHERE receiptId = :receiptId")
    suspend fun allItemsForReceipt(receiptId: String): List<ReceiptItemEntity>

    // Cache-Verdrängung serverbestätigter Zeilen, siehe sync-patterns „Server-Archiv":
    // Der Server behält alles; diese harten Löschungen entfernen nur den lokalen Abzug.
    @Query("DELETE FROM receipts WHERE id = :id")
    suspend fun hardDeleteReceipt(id: String)

    @Query("DELETE FROM receipt_items WHERE receiptId = :receiptId AND id NOT IN (:keepIds)")
    suspend fun hardDeleteItemsExcept(receiptId: String, keepIds: List<String>)

    @Query("DELETE FROM receipt_items WHERE receiptId = :receiptId")
    suspend fun hardDeleteItemsOf(receiptId: String)

    /** Bon und Positionen in einem Schritt schreiben (Repository, Scan). */
    @Transaction
    suspend fun upsertReceiptWithItems(receipt: ReceiptEntity, items: List<ReceiptItemEntity>) {
        upsertReceipt(receipt)
        if (items.isNotEmpty()) upsertItems(items)
    }

    /** Server-Stand übernehmen: Bon, Positionen, lokal überzählige Positionen entfernen — atomar. */
    @Transaction
    suspend fun replaceWithServerState(receipt: ReceiptEntity, items: List<ReceiptItemEntity>) {
        upsertReceipt(receipt)
        upsertItems(items)
        hardDeleteItemsExcept(receipt.id, items.map { it.id })
    }

    /** Bon samt Positionen hart löschen (Cache-Verdrängung, bestätigte Server-Löschung, nie hochgeladene Bons). */
    @Transaction
    suspend fun hardDeleteReceiptWithItems(id: String) {
        hardDeleteItemsOf(id)
        hardDeleteReceipt(id)
    }

    /** Bons, die älter als der Cache-Schnitt sind und sicher verdrängt werden dürfen (alles synchron, kein Upload offen). */
    @Query(
        "SELECT id FROM receipts WHERE purchaseDate < :cutoffMs AND dirty = 0 AND pendingUpload = 0 AND cachedUntil < :nowMs"
    )
    suspend fun evictableIds(cutoffMs: Long, nowMs: Long): List<String>

    @Query("SELECT localImageUri FROM receipts WHERE localImageUri != ''")
    suspend fun allLocalImageUris(): List<String>

    /** Anzahl vorläufiger Bons (KI-Auswertung steht noch aus) für den Hinweis in Bon-Liste und Kostenübersicht. */
    @Query("SELECT COUNT(*) FROM receipts WHERE status = 'provisional' AND deleted = 0")
    fun observeProvisionalCount(): Flow<Int>

    /** Ältester lokal vorhandener Kauftag (`YYYY-MM-DD`) — Grundlage für „ältere Bons vom Server laden". */
    @Query("SELECT MIN(purchaseDay) FROM receipts WHERE purchaseDay != '' AND deleted = 0")
    suspend fun oldestPurchaseDay(): String?

    /**
     * Foto hochgeladen: Server-Dateiname setzen, lokale Kopie freigeben. Bewusst weder `updatedAt` noch `dirty`
     * ändern — der Server hat den Pfad beim Hochladen selbst gesetzt, ein lokal neuerer Zeitstempel würde sonst
     * spätere Server-Änderungen im LWW-Vergleich unterdrücken.
     */
    @Query("UPDATE receipts SET pendingUpload = 0, imagePath = :imagePath, localImageUri = '' WHERE id = :id")
    suspend fun clearPendingUpload(id: String, imagePath: String)

    // ── Scan Reminder (Phase 3) ──────────────────────────────────────────────

    /**
     * Observes whether a receipt was already scanned today for the given list.
     * purchaseDate is stored in Unix-ms. Used by the in-app scan reminder banner.
     */
    @Query("""
        SELECT COUNT(*) FROM receipts
        WHERE shoppingListId = :listId AND deleted = 0
            AND purchaseDate >= :startMs AND purchaseDate < :endMs
    """)
    fun observeReceiptCountForListToday(listId: String, startMs: Long, endMs: Long): Flow<Int>

    // ── Price History (Phase 5) ──────────────────────────────────────────────

    /**
     * Returns all non-deleted receipt items joined with their receipt, ordered by
     * purchase date descending. Used to build the product price history offline.
     */
    @Query("""
        SELECT ri.name AS name, r.storeId AS storeId, r.storeName AS storeName,
               r.purchaseDate AS purchaseDate, ri.unitPrice AS unitPrice,
               ri.totalPrice AS totalPrice, ri.quantity AS quantity
        FROM receipt_items ri
        JOIN receipts r ON ri.receiptId = r.id
        WHERE ri.deleted = 0 AND r.deleted = 0 AND ri.name != ''
        ORDER BY r.purchaseDate DESC
    """)
    suspend fun allPricePoints(): List<PricePoint>
}

// ── Data classes for DAO queries ──────────────────────────────────────────────

data class PricePoint(
    val name: String,
    val storeId: String,
    val storeName: String,
    val purchaseDate: Long,
    val unitPrice: Double,
    val totalPrice: Double,
    val quantity: Double,
)
