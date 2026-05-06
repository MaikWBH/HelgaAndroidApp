package com.helga.android.data.repository

import com.helga.android.data.local.dao.WeekplanTemplateDao
import com.helga.android.data.local.entity.WeekplanTemplateEntity
import com.helga.android.data.local.entity.WeekplanTemplateEntryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeekplanTemplateRepository @Inject constructor(
    private val dao: WeekplanTemplateDao,
) {
    fun observeAll(): Flow<List<WeekplanTemplateEntity>> = dao.observeAll()

    suspend fun save(id: String, name: String, entries: List<WeekplanTemplateEntryEntity>) {
        dao.insertTemplate(WeekplanTemplateEntity(id = id, name = name.trim()))
        if (entries.isNotEmpty()) dao.insertEntries(entries)
    }

    suspend fun entriesForTemplate(templateId: String): List<WeekplanTemplateEntryEntity> =
        dao.entriesForTemplate(templateId)

    suspend fun delete(templateId: String) {
        dao.deleteEntriesForTemplate(templateId)
        dao.deleteTemplate(templateId)
    }
}
