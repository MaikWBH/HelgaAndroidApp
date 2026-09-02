package com.helga.android.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    fun schedulePeriodic() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            repeatInterval = 15, repeatIntervalTimeUnit = TimeUnit.MINUTES,
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            SyncWorker.UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun triggerOneShot() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        val uploadRequest = OneTimeWorkRequestBuilder<ImageUploadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        val downloadRequest = OneTimeWorkRequestBuilder<ImageDownloadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        // Erst syncen (Rezept muss auf Server bekannt sein), dann eigene Bilder hochladen,
        // dann Bilder herunterladen, die durch den Sync von anderen Geräten neu dazukamen.
        workManager.beginUniqueWork(
            SyncWorker.UNIQUE_ONESHOT,
            ExistingWorkPolicy.REPLACE,
            syncRequest,
        ).then(uploadRequest).then(downloadRequest).enqueue()
    }

    fun cancelAll() {
        workManager.cancelUniqueWork(SyncWorker.UNIQUE_PERIODIC)
        workManager.cancelUniqueWork(SyncWorker.UNIQUE_ONESHOT)
    }
}
