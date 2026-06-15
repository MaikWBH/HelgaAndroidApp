package com.helga.android.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

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
    val weekplanDays: Flow<Int> = ds.data.map { prefs ->
        val raw = prefs[KEY_WEEKPLAN_DAYS] ?: 7
        if (raw in setOf(7, 10, 14)) raw else 7
    }
    val shoppingDay: Flow<Int> = ds.data.map { prefs ->
        val raw = prefs[KEY_SHOPPING_DAY] ?: 0
        raw.coerceIn(0, 6)
    }
    val defaultShoppingListId: Flow<String> = ds.data.map { it[KEY_DEFAULT_SHOPPING_LIST_ID].orEmpty() }
    val themeMode: Flow<String> = ds.data.map { it[KEY_THEME_MODE] ?: "system" }
    val accentColor: Flow<Int> = ds.data.map { it[KEY_ACCENT_COLOR] ?: 0 }
    val checkMode: Flow<String> = ds.data.map { it[KEY_CHECK_MODE] ?: "keep" }
    val notifyShoppingDay: Flow<Boolean> = ds.data.map { it[KEY_NOTIFY_SHOPPING] ?: false }
    val notifyCookReminder: Flow<Boolean> = ds.data.map { it[KEY_NOTIFY_COOK] ?: false }

    /** Anteil abgehakter Items (0.0–1.0), ab dem der Kassenzettel-Scan-Hinweis erscheint. */
    val scanReminderThreshold: Flow<Float> = ds.data.map { prefs ->
        (prefs[KEY_SCAN_REMINDER_THRESHOLD] ?: 0.6f).coerceIn(0f, 1f)
    }
    val allergies: Flow<List<String>> = ds.data.map { prefs ->
        val json = prefs[KEY_ALLERGIES].orEmpty()
        if (json.isBlank()) emptyList()
        else {
            val moshi = Moshi.Builder().build()
            val type = Types.newParameterizedType(List::class.java, String::class.java)
            try {
                moshi.adapter<List<String>>(type).fromJson(json) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

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

    /**
     * Einmalige Migration auf das server_seq-basierte Sync-Protokoll.
     * Setzt den Cursor einmalig auf 0, damit ein voller Re-Pull alle Einträge
     * nachzieht, die zuvor durch den wanduhr-basierten Cursor unsichtbar waren.
     * Idempotent – läuft nur, solange die gespeicherte Protokollversion < 1 ist.
     */
    suspend fun ensureSyncProtocol() {
        ds.edit { prefs ->
            val version = prefs[KEY_SYNC_PROTOCOL] ?: 0
            if (version < SYNC_PROTOCOL_VERSION) {
                prefs[KEY_LAST_SYNC_TS] = 0L
                prefs[KEY_SYNC_PROTOCOL] = SYNC_PROTOCOL_VERSION
            }
        }
    }

    suspend fun saveWeekplanDays(days: Int) {
        val valid = if (days in setOf(7, 10, 14)) days else 7
        ds.edit { it[KEY_WEEKPLAN_DAYS] = valid }
    }

    suspend fun saveShoppingDay(day: Int) {
        ds.edit { it[KEY_SHOPPING_DAY] = day.coerceIn(0, 6) }
    }

    suspend fun saveDefaultShoppingListId(listId: String) {
        ds.edit {
            if (listId.isBlank()) it.remove(KEY_DEFAULT_SHOPPING_LIST_ID)
            else it[KEY_DEFAULT_SHOPPING_LIST_ID] = listId
        }
    }

    suspend fun saveThemeMode(mode: String) {
        ds.edit { it[KEY_THEME_MODE] = mode }
    }

    suspend fun saveAccentColor(index: Int) {
        ds.edit { it[KEY_ACCENT_COLOR] = index.coerceIn(0, 5) }
    }

    suspend fun saveCheckMode(mode: String) {
        ds.edit { it[KEY_CHECK_MODE] = mode }
    }

    suspend fun saveNotifyShoppingDay(enabled: Boolean) {
        ds.edit { it[KEY_NOTIFY_SHOPPING] = enabled }
    }

    suspend fun saveNotifyCookReminder(enabled: Boolean) {
        ds.edit { it[KEY_NOTIFY_COOK] = enabled }
    }

    suspend fun saveScanReminderThreshold(threshold: Float) {
        ds.edit { it[KEY_SCAN_REMINDER_THRESHOLD] = threshold.coerceIn(0f, 1f) }
    }

    suspend fun saveAllergies(allergies: List<String>) {
        ds.edit {
            if (allergies.isEmpty()) it.remove(KEY_ALLERGIES)
            else {
                val moshi = Moshi.Builder().build()
                val type = Types.newParameterizedType(List::class.java, String::class.java)
                val json = moshi.adapter<List<String>>(type).toJson(allergies)
                it[KEY_ALLERGIES] = json
            }
        }
    }

    suspend fun clearConnection() {
        ds.edit {
            it.remove(KEY_SERVER_URL)
            it.remove(KEY_API_KEY)
            it.remove(KEY_LAST_SYNC_TS)
            it.remove(KEY_DEFAULT_SHOPPING_LIST_ID)
        }
    }

    private companion object {
        // Aktuelle Sync-Protokollversion. Erhöhen erzwingt einen einmaligen Voll-Resync.
        const val SYNC_PROTOCOL_VERSION = 1

        val KEY_SERVER_URL = stringPreferencesKey("server_url")
        val KEY_API_KEY = stringPreferencesKey("api_key")
        val KEY_LAST_SYNC_TS = longPreferencesKey("last_sync_ts")
        val KEY_SYNC_PROTOCOL = intPreferencesKey("sync_protocol_version")
        val KEY_WEEKPLAN_DAYS = intPreferencesKey("weekplan_days")
        val KEY_SHOPPING_DAY = intPreferencesKey("shopping_day")
        val KEY_DEFAULT_SHOPPING_LIST_ID = stringPreferencesKey("default_shopping_list_id")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_ACCENT_COLOR = intPreferencesKey("accent_color")
        val KEY_CHECK_MODE = stringPreferencesKey("check_mode")
        val KEY_NOTIFY_SHOPPING = booleanPreferencesKey("notify_shopping_day")
        val KEY_NOTIFY_COOK = booleanPreferencesKey("notify_cook_reminder")
        val KEY_ALLERGIES = stringPreferencesKey("allergies")
        val KEY_SCAN_REMINDER_THRESHOLD = floatPreferencesKey("scan_reminder_threshold")
    }
}
