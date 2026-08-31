package com.helga.android.data.sync

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Triggert einen Sofort-Sync sobald die App in den Vordergrund kommt.
 * Wird im [com.helga.android.HelgaApp] genau einmal beim Start registriert.
 */
@Singleton
class ForegroundSyncObserver @Inject constructor(
    private val syncScheduler: SyncScheduler,
) : DefaultLifecycleObserver {
    private var registered = false

    fun start() {
        if (registered) return
        registered = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        syncScheduler.triggerOneShot()
    }
}
