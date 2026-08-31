package com.helga.android.data.sync

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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.helga.android.MainActivity
import com.helga.android.R
import com.helga.android.data.local.dao.ShoppingDao
import com.helga.android.data.local.dao.WeekplanDao
import com.helga.android.data.preferences.AppPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "helga_reminders"
private const val NOTIFY_ID_SHOPPING = 1001
private const val NOTIFY_ID_COOK = 1002

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val preferences: AppPreferences,
    private val weekplanDao: WeekplanDao,
    private val shoppingDao: ShoppingDao,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        ensureChannel()
        val today = LocalDate.now()
        val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val now = LocalTime.now()

        // Shopping-Day Reminder (morgens 8-9 Uhr)
        if (preferences.notifyShoppingDay.first()) {
            val shoppingDayIndex = preferences.shoppingDay.first() // 0=Mo .. 6=So
            val todayDow = today.dayOfWeek.value - 1 // 0=Mo
            if (shoppingDayIndex == todayDow && now.hour in 7..9) {
                val listId = preferences.defaultShoppingListId.first()
                val itemCount = if (listId.isNotBlank()) {
                    shoppingDao.uncheckedItemCount(listId)
                } else 0
                if (itemCount > 0) {
                    notify(
                        id = NOTIFY_ID_SHOPPING,
                        title = applicationContext.getString(R.string.notify_shopping_title),
                        text = applicationContext.getString(R.string.notify_shopping_text, itemCount),
                    )
                }
            }
        }

        // Cook Reminder (nachmittags 15-17 Uhr)
        if (preferences.notifyCookReminder.first()) {
            if (now.hour in 15..17) {
                val recipeId = weekplanDao.observeTodayRecipeId(todayStr).first()
                if (recipeId != null) {
                    val recipe = weekplanDao.recipeName(recipeId)
                    if (recipe != null) {
                        notify(
                            id = NOTIFY_ID_COOK,
                            title = applicationContext.getString(R.string.notify_cook_title),
                            text = applicationContext.getString(R.string.notify_cook_text, recipe),
                        )
                    }
                }
            }
        }

        return Result.success()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        applicationContext.getString(R.string.notify_channel_name),
                        NotificationManager.IMPORTANCE_DEFAULT,
                    )
                )
            }
        }
    }

    private fun notify(id: Int, title: String, text: String) {
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
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id, notification)
    }
}

object NotificationScheduler {
    private const val WORK_NAME = "helga_reminders"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
