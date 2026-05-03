package com.helga.android.data.sync

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Syncing : SyncStatus
    data class Success(val ts: Long) : SyncStatus
    data object Offline : SyncStatus
    data class Error(val message: String) : SyncStatus
}
