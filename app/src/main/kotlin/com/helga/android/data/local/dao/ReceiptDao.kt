package com.helga.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("SELECT * FROM receipts WHERE dirty = 1")
    suspend fun dirtyReceipts(): List<ReceiptEntity>

    @Query("SELECT * FROM receipt_items WHERE dirty = 1")
    suspend fun dirtyItems(): List<ReceiptItemEntity>

    @Query("UPDATE receipts SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearReceiptDirty(ids: List<String>)

    @Query("UPDATE receipt_items SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearItemDirty(ids: List<String>)

    // ── Cost Overview Queries (Phase 2) ──────────────────────────────────────

    /**
     * Aggregates total spending by store (storeId).
     * Returns (storeId, storeName, totalAmount) sorted by amount descending.
     */
    @Query("""
        SELECT
            storeId,
            storeName,
            SUM(totalAmount) as totalAmount,
            COUNT(*) as receiptCount
        FROM receipts
        WHERE deleted = 0
        GROUP BY storeId, storeName
        ORDER BY totalAmount DESC
    """)
    suspend fun costByStore(): List<CostByStore>

    /**
     * Aggregates total spending by date (purchaseDate).
     * purchaseDate is stored as epoch day * 86400 * 1000, convert to date string.
     */
    @Query("""
        SELECT
            datetime(purchaseDate / 1000, 'unixepoch') as date,
            SUM(totalAmount) as totalAmount,
            COUNT(*) as receiptCount
        FROM receipts
        WHERE deleted = 0
        GROUP BY date(purchaseDate / 1000, 'unixepoch')
        ORDER BY date DESC
    """)
    suspend fun costByDate(): List<CostByDate>

    /**
     * Aggregates total spending by date within a date range.
     */
    @Query("""
        SELECT
            datetime(purchaseDate / 1000, 'unixepoch') as date,
            SUM(totalAmount) as totalAmount,
            COUNT(*) as receiptCount
        FROM receipts
        WHERE deleted = 0
            AND purchaseDate / 1000 >= :startEpochSec
            AND purchaseDate / 1000 < :endEpochSec
        GROUP BY date(purchaseDate / 1000, 'unixepoch')
        ORDER BY date DESC
    """)
    suspend fun costByDateRange(startEpochSec: Long, endEpochSec: Long): List<CostByDate>

    /**
     * Total spending in a given date range.
     */
    @Query("""
        SELECT
            SUM(totalAmount) as totalAmount,
            COUNT(*) as receiptCount
        FROM receipts
        WHERE deleted = 0
            AND purchaseDate / 1000 >= :startEpochSec
            AND purchaseDate / 1000 < :endEpochSec
    """)
    suspend fun totalCostForRange(startEpochSec: Long, endEpochSec: Long): CostSummary

    /**
     * Receipts for a given store, ordered by date descending.
     */
    @Query("""
        SELECT * FROM receipts
        WHERE storeId = :storeId AND deleted = 0
        ORDER BY purchaseDate DESC
    """)
    fun observeReceiptsForStore(storeId: String): Flow<List<ReceiptEntity>>

    /**
     * All receipts within a date range, ordered by date descending.
     */
    @Query("""
        SELECT * FROM receipts
        WHERE deleted = 0
            AND purchaseDate / 1000 >= :startEpochSec
            AND purchaseDate / 1000 < :endEpochSec
        ORDER BY purchaseDate DESC
    """)
    suspend fun receiptsInRange(startEpochSec: Long, endEpochSec: Long): List<ReceiptEntity>
}

// ── Data classes for aggregation queries ──────────────────────────────────────

data class CostByStore(
    val storeId: String,
    val storeName: String,
    val totalAmount: Double,
    val receiptCount: Int,
)

data class CostByDate(
    val date: String, // "2026-06-15 HH:MM:SS"
    val totalAmount: Double,
    val receiptCount: Int,
)

data class CostSummary(
    val totalAmount: Double,
    val receiptCount: Int,
)
