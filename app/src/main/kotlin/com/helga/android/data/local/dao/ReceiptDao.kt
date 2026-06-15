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
}
