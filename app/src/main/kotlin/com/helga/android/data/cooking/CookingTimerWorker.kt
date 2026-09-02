package com.helga.android.data.cooking

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.helga.android.MainActivity
import com.helga.android.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

private const val TIMER_CHANNEL_ID = "helga_timers"

/**
 * Feuert die "Timer abgelaufen"-Benachrichtigung (rezepte A8). Eigener Kanal statt
 * `helga_reminders` (siehe [com.helga.android.data.sync.NotificationScheduler]) — Android lässt
 * die Priorität eines Kanals nach dem Anlegen nicht mehr ändern, `helga_reminders` läuft aber
 * bereits mit `IMPORTANCE_DEFAULT`; ein Timer braucht `IMPORTANCE_HIGH` (Heads-up), damit er beim
 * Kochen tatsächlich auffällt.
 */
@HiltWorker
class CookingTimerWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val timerManager: CookingTimerManager,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val label = inputData.getString(KEY_LABEL) ?: applicationContext.getString(R.string.cook_timer_done)
        timerManager.markFinished(id)
        ensureChannel()
        notify(id.hashCode(), label)
        return Result.success()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(TIMER_CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        TIMER_CHANNEL_ID,
                        applicationContext.getString(R.string.notify_timer_channel_name),
                        NotificationManager.IMPORTANCE_HIGH,
                    )
                )
            }
        }
    }

    private fun notify(id: Int, label: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            applicationContext, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, TIMER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(applicationContext.getString(R.string.notify_timer_title))
            .setContentText(label)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id, notification)
    }

    companion object {
        const val KEY_ID = "timer_id"
        const val KEY_LABEL = "timer_label"
    }
}
