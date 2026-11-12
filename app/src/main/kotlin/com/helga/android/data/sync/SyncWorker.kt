package com.helga.android.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.helga.android.data.preferences.AppPreferences
import com.helga.android.data.receipts.ReceiptSyncer
import com.helga.android.data.receipts.ReceiptUploader
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
    private val receiptSyncer: ReceiptSyncer,
    private val receiptUploader: ReceiptUploader,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        evictOldReceipts()

        if (!preferences.currentConnection().isConfigured) {
            // Nichts zu tun – Onboarding noch nicht abgeschlossen.
            return Result.success()
        }

        statusHolder.update(SyncStatus.Syncing)
        return try {
            val outcome = syncEngine.runFullSync()
            statusHolder.update(SyncStatus.Success(outcome.serverTs))
            Timber.i("Sync ok: pulled=${outcome.pulled} pushed=${outcome.pushed}")
            syncReceipts()
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
     * Worker-Lauf (periodisch, Connectivity-Change, App-Vordergrund), auch offline. Entfernt nur
     * serverbestätigte Bons außerhalb des Zeitfensters aus dem lokalen Cache (der Server behält alles)
     * und verwaiste Fotos.
     */
    private suspend fun evictOldReceipts() {
        try {
            val evicted = receiptSyncer.evict()
            if (evicted > 0) Timber.i("Bon-Cache: $evicted Bon(s) außerhalb des Zeitfensters verdrängt")
            receiptSyncer.cleanupImages()
        } catch (e: Exception) {
            Timber.w(e, "Bon-Cache-Bereinigung fehlgeschlagen")
        }
    }

    /** Bon-Archiv (eigenes Protokoll): erst lokale Änderungen senden, dann Server-Stand holen. Fehler zählen nicht als Sync-Fehler. */
    private suspend fun syncReceipts() {
        try {
            // Zuerst die Upload-Warteschlange (Offline-Scans), dann lokale Änderungen senden, dann pullen.
            receiptUploader.uploadPending()
            receiptSyncer.pushDirty()
            receiptSyncer.pull()
            receiptSyncer.refreshAggregates()
        } catch (e: Exception) {
            Timber.w(e, "Bon-Abgleich fehlgeschlagen")
        }
    }

    companion object {
        const val UNIQUE_PERIODIC = "helga.sync.periodic"
        const val UNIQUE_ONESHOT = "helga.sync.oneshot"
    }
}
