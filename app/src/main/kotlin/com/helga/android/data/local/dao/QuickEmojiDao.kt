package com.helga.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.helga.android.data.local.entity.QuickEmojiEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuickEmojiDao {

    @Query("SELECT * FROM quick_emojis WHERE deleted = 0 ORDER BY sortOrder ASC")
    fun observeEmojis(): Flow<List<QuickEmojiEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEmoji(emoji: QuickEmojiEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEmojis(emojis: List<QuickEmojiEntity>)

    @Query("SELECT * FROM quick_emojis WHERE dirty = 1")
    suspend fun dirtyEmojis(): List<QuickEmojiEntity>

    @Query("UPDATE quick_emojis SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearEmojiDirty(ids: List<String>)
}
