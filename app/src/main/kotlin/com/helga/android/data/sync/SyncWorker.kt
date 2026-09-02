package com.helga.android.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.helga.android.data.preferences.AppPreferences
import com.helga.android.data.repository.ReceiptRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncEngine: SyncEngine,
    private val statusHolder: SyncStatusHolder,
    private val preferences: AppPreferences,
    private val receiptRepository: ReceiptRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        purgeOldReceipts()

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

    /**
     * Rein lokale Bereinigung, unabhängig von Netzwerk/Server-Konfiguration – läuft bei jedem
     * Worker-Lauf (periodisch, Connectivity-Change, App-Vordergrund), auch offline.
     */
    private suspend fun purgeOldReceipts() {
        val months = preferences.receiptRetentionMonths.first()
        if (months <= 0) return
        try {
            val cutoff = LocalDate.now().minusMonths(months.toLong())
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val purged = receiptRepository.purgeReceiptsOlderThan(cutoff)
            if (purged > 0) Timber.i("Automatische Bon-Löschung: $purged Bon(s) älter als $months Monate entfernt")
        } catch (e: Exception) {
            Timber.w(e, "Automatische Bon-Löschung fehlgeschlagen")
        }
    }

    companion object {
        const val UNIQUE_PERIODIC = "helga.sync.periodic"
        const val UNIQUE_ONESHOT = "helga.sync.oneshot"
    }
}
