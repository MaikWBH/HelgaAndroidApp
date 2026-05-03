package com.helga.android.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.helga.android.data.preferences.AppPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.io.IOException

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncEngine: SyncEngine,
    private val statusHolder: SyncStatusHolder,
    private val preferences: AppPreferences,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!preferences.currentConnection().isConfigured) {
            // Nichts zu tun – Onboarding noch nicht abgeschlossen.
            return Result.success()
        }

        statusHolder.update(SyncStatus.Syncing)
        return try {
            val outcome = syncEngine.runFullSync()
            statusHolder.update(SyncStatus.Success(outcome.serverTs))
            Timber.i("Sync ok: pulled=${outcome.pulled} pushed=${outcome.pushed}")
            Result.success()
        } catch (io: IOException) {
            Timber.w(io, "Sync offline")
            statusHolder.update(SyncStatus.Offline)
            Result.retry()
        } catch (e: Exception) {
            Timber.e(e, "Sync error")
            statusHolder.update(SyncStatus.Error(e.message ?: e.javaClass.simpleName))
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_PERIODIC = "helga.sync.periodic"
        const val UNIQUE_ONESHOT = "helga.sync.oneshot"
    }
}
