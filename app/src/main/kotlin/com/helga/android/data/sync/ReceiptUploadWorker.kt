package com.helga.android.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.helga.android.data.receipts.ReceiptUploader
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Arbeitet die lokale Bon-Upload-Warteschlange ab (Offline-Scan, Phase 3a). Wird beim Scannen ohne Server
 * eingereiht und läuft, sobald Netz da ist; der periodische Sync ruft denselben Uploader zusätzlich auf.
 */
@HiltWorker
class ReceiptUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val uploader: ReceiptUploader,
    private val syncScheduler: SyncScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = try {
            uploader.uploadPending()
        } catch (e: Exception) {
            Timber.w(e, "Bon-Upload fehlgeschlagen")
            return Result.retry()
        }
        if (result.done > 0) syncScheduler.triggerOneShot()
        return if (result.retry) Result.retry() else Result.success()
    }

    companion object {
        const val UNIQUE = "helga.receipt.upload"
    }
}
