package com.helga.android.data.sync

import com.helga.android.data.remote.SyncApiFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Geteilter Erreichbarkeits-Status des Sync-Servers (ki A3) — die KI-Bildschirme (Generieren,
 * Remix, Klassifikation) zeigen einen Hinweis, sobald [reachable] `false` wird, statt den Fehler
 * erst nach einem gescheiterten Generierungsversuch zu melden. `null` = noch nie geprüft; ein
 * bekannter Zustand bleibt bis zur nächsten Prüfung stehen (kein Flackern zwischen Checks).
 * Wird von [NetworkObserver] und [ForegroundSyncObserver] mitgetriggert, damit der Status meist
 * schon aktuell ist, bevor ein KI-Bildschirm überhaupt geöffnet wird.
 */
@Singleton
class ServerReachabilityMonitor @Inject constructor(
    private val apiFactory: SyncApiFactory,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _reachable = MutableStateFlow<Boolean?>(null)
    val reachable: StateFlow<Boolean?> = _reachable.asStateFlow()

    fun checkAsync() {
        scope.launch { check() }
    }

    suspend fun check() {
        _reachable.value = try {
            apiFactory.api().health()
            true
        } catch (_: Exception) {
            false
        }
    }
}
