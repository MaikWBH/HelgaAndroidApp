package com.helga.android.data.cooking

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class ActiveCookingTimer(
    val id: String,
    val label: String,
    val totalSeconds: Int,
    val endAtMillis: Long,
)

/**
 * Mehrere parallele Kochtimer (rezepte A8). Läuft im Hintergrund weiter, weil das eigentliche
 * "Klingeln" über einen WorkManager-Einmaljob passiert — dessen Zeitplan lebt in WorkManagers
 * eigener DB, nicht im App-Prozess, und überlebt damit Navigation weg vom Kochbildschirm und
 * Backgrounding der App. [activeTimers] dient nur der UI-Anzeige, solange der Prozess lebt;
 * stirbt der Prozess während ein Timer läuft, feuert die Benachrichtigung trotzdem pünktlich,
 * die Liste hier ist beim nächsten App-Start dann aber leer (kein persistenter Datensatz nötig —
 * die Benachrichtigung selbst hat den Nutzer bereits erreicht).
 */
@Singleton
class CookingTimerManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _activeTimers = MutableStateFlow<List<ActiveCookingTimer>>(emptyList())
    val activeTimers: StateFlow<List<ActiveCookingTimer>> = _activeTimers.asStateFlow()

    fun start(label: String, totalSeconds: Int): String {
        val id = UUID.randomUUID().toString()
        schedule(id, label, totalSeconds)
        return id
    }

    fun reset(id: String, label: String, totalSeconds: Int) {
        schedule(id, label, totalSeconds)
    }

    fun cancel(id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
        _activeTimers.update { list -> list.filterNot { it.id == id } }
    }

    /** Vom [CookingTimerWorker] aufgerufen, sobald ein Timer abgelaufen ist. */
    fun markFinished(id: String) {
        _activeTimers.update { list -> list.filterNot { it.id == id } }
    }

    private fun schedule(id: String, label: String, totalSeconds: Int) {
        val endAt = System.currentTimeMillis() + totalSeconds * 1000L
        _activeTimers.update { list ->
            val entry = ActiveCookingTimer(id, label, totalSeconds, endAt)
            if (list.any { it.id == id }) list.map { if (it.id == id) entry else it } else list + entry
        }
        val request = OneTimeWorkRequestBuilder<CookingTimerWorker>()
            .setInitialDelay(totalSeconds.toLong(), TimeUnit.SECONDS)
            .setInputData(
                workDataOf(
                    CookingTimerWorker.KEY_ID to id,
                    CookingTimerWorker.KEY_LABEL to label,
                )
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, request)
    }

    private fun workName(id: String) = "cooking_timer_$id"
}
