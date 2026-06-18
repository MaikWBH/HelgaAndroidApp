package com.helga.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.helga.android.data.local.entity.ReceiptArticleLinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptArticleLinkDao {

    @Query("SELECT * FROM receipt_article_links WHERE deleted = 0")
    fun observeAll(): Flow<List<ReceiptArticleLinkEntity>>

    @Query("SELECT * FROM receipt_article_links WHERE deleted = 0")
    suspend fun allActive(): List<ReceiptArticleLinkEntity>

    @Query("SELECT * FROM receipt_article_links WHERE normalizedName = :normalizedName AND deleted = 0 LIMIT 1")
    suspend fun findByNormalizedName(normalizedName: String): ReceiptArticleLinkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(link: ReceiptArticleLinkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(links: List<ReceiptArticleLinkEntity>)

    @Query("SELECT * FROM receipt_article_links WHERE dirty = 1")
    suspend fun dirtyLinks(): List<ReceiptArticleLinkEntity>

    @Query("UPDATE receipt_article_links SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearDirty(ids: List<String>)
}
