package com.helga.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.helga.android.data.local.entity.WeekplanTemplateEntity
import com.helga.android.data.local.entity.WeekplanTemplateEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeekplanTemplateDao {

    @Query("SELECT * FROM weekplan_templates ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<WeekplanTemplateEntity>>

    @Query("SELECT * FROM weekplan_template_entries WHERE templateId = :templateId ORDER BY dayOffset ASC, position ASC")
    suspend fun entriesForTemplate(templateId: String): List<WeekplanTemplateEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: WeekplanTemplateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<WeekplanTemplateEntryEntity>)

    @Query("DELETE FROM weekplan_templates WHERE id = :id")
    suspend fun deleteTemplate(id: String)

    @Query("DELETE FROM weekplan_template_entries WHERE templateId = :templateId")
    suspend fun deleteEntriesForTemplate(templateId: String)
}
