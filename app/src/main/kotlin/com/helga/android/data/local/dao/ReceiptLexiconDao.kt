package com.helga.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.helga.android.data.local.entity.ReceiptLexiconEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptLexiconDao {

    @Query("SELECT * FROM receipt_lexicon ORDER BY storeName, rawText")
    fun observeAll(): Flow<List<ReceiptLexiconEntity>>

    @Query("DELETE FROM receipt_lexicon")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<ReceiptLexiconEntity>)

    /** Ersetzt den lokalen Abzug vollständig durch den Server-Stand. */
    @Transaction
    suspend fun replaceAll(entries: List<ReceiptLexiconEntity>) {
        deleteAll()
        insertAll(entries)
    }

    @Query("DELETE FROM receipt_lexicon WHERE id = :id")
    suspend fun deleteById(id: String)
}
