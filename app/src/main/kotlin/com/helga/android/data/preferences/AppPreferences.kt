package com.helga.android.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "helga_prefs")

data class HelgaConnection(
    val serverUrl: String,
    val apiKey: String,
) {
    val isConfigured: Boolean get() = serverUrl.isNotBlank() && apiKey.isNotBlank()
}

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val ds get() = context.dataStore

    val connection: Flow<HelgaConnection> = ds.data.map {
        HelgaConnection(
            serverUrl = it[KEY_SERVER_URL].orEmpty(),
            apiKey = it[KEY_API_KEY].orEmpty(),
        )
    }

    val lastSyncTs: Flow<Long> = ds.data.map { it[KEY_LAST_SYNC_TS] ?: 0L }

    suspend fun currentConnection(): HelgaConnection = connection.first()
    suspend fun currentLastSyncTs(): Long = lastSyncTs.first()

    suspend fun saveConnection(serverUrl: String, apiKey: String) {
        ds.edit {
            it[KEY_SERVER_URL] = serverUrl
            it[KEY_API_KEY] = apiKey
        }
    }

    suspend fun saveLastSyncTs(ts: Long) {
        ds.edit { it[KEY_LAST_SYNC_TS] = ts }
    }

    suspend fun clearConnection() {
        ds.edit {
            it.remove(KEY_SERVER_URL)
            it.remove(KEY_API_KEY)
            it.remove(KEY_LAST_SYNC_TS)
        }
    }

    private companion object {
        val KEY_SERVER_URL = stringPreferencesKey("server_url")
        val KEY_API_KEY = stringPreferencesKey("api_key")
        val KEY_LAST_SYNC_TS = longPreferencesKey("last_sync_ts")
    }
}
