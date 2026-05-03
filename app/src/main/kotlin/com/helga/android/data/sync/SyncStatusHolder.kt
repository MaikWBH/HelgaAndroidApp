package com.helga.android.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-weiter Inhaber des aktuellen Sync-Status. Vom [SyncWorker] aktualisiert,
 * von der UI (TopBar-Icon) beobachtet.
 */
@Singleton
class SyncStatusHolder @Inject constructor() {
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    fun update(status: SyncStatus) {
        _status.value = status
    }
}
