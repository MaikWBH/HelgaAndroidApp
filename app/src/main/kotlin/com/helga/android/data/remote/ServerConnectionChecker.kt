package com.helga.android.data.remote

import com.helga.android.data.preferences.AppPreferences
import com.helga.android.data.sync.SyncScheduler
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Ergebnis eines Verbindungstests gegen den Sync-Server. */
sealed interface ConnectionResult {
    data object Success : ConnectionResult
    data class HttpError(val code: Int, val detail: String?) : ConnectionResult
    data object Unreachable : ConnectionResult
    data class Other(val message: String) : ConnectionResult
}

/**
 * Prüft Server-URL und API-Key per Healthcheck. Bei Erfolg werden Verbindung gespeichert,
 * der periodische Sync geplant und ein Sofort-Sync gestartet.
 */
@Singleton
class ServerConnectionChecker @Inject constructor(
    private val apiProvider: ApiProvider,
    private val preferences: AppPreferences,
    private val syncScheduler: SyncScheduler,
) {
    suspend fun test(url: String, key: String): ConnectionResult = try {
        apiProvider.apiForOnboarding(url, key).health()
        preferences.saveConnection(url, key)
        syncScheduler.schedulePeriodic()
        syncScheduler.triggerOneShot()
        ConnectionResult.Success
    } catch (e: HttpException) {
        Timber.w(e, "Healthcheck HTTP-Fehler")
        ConnectionResult.HttpError(e.code(), e.response()?.errorBody()?.string()?.let(::extractErrorDetail))
    } catch (e: IOException) {
        Timber.w(e, "Healthcheck offline")
        ConnectionResult.Unreachable
    } catch (e: Exception) {
        Timber.e(e, "Healthcheck unbekannter Fehler")
        ConnectionResult.Other(e.message ?: "Unbekannter Fehler")
    }
}
