package com.helga.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.helga.android.data.local.entity.ReceiptAggregateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptAggregateDao {

    @Query("SELECT * FROM receipt_aggregates")
    fun observeAll(): Flow<List<ReceiptAggregateEntity>>

    @Query("SELECT COUNT(*) FROM receipt_aggregates")
    suspend fun count(): Int

    @Query("DELETE FROM receipt_aggregates")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<ReceiptAggregateEntity>)

    /** Ersetzt den lokalen Abzug vollständig durch den Server-Stand. */
    @Transaction
    suspend fun replaceAll(rows: List<ReceiptAggregateEntity>) {
        deleteAll()
        insertAll(rows)
    }
}
