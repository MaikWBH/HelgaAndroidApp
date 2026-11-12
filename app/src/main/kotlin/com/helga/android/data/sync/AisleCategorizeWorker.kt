package com.helga.android.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.helga.android.data.shopping.AisleAssigner
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Sortiert Einkaufslisten-Artikel, die lokal keinen Gang bekamen, per Server-KI nach (Phase 3b). Läuft nur mit Netz,
 * schlägt nie fehl (ohne Server bleibt der Artikel ohne Gang) und stößt bei Änderungen einen Sync an.
 */
@HiltWorker
class AisleCategorizeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val assigner: AisleAssigner,
    private val syncScheduler: SyncScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val changed = try {
            assigner.assignRemote()
        } catch (e: Exception) {
            Timber.w(e, "Gang-Kategorisierung fehlgeschlagen")
            0
        }
        if (changed > 0) syncScheduler.triggerOneShot()
        return Result.success()
    }

    companion object {
        const val UNIQUE = "helga.aisle.categorize"
    }
}
