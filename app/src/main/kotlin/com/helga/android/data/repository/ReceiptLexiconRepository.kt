package com.helga.android.data.repository

import com.helga.android.data.local.dao.ReceiptLexiconDao
import com.helga.android.data.local.entity.ReceiptLexiconEntity
import com.helga.android.data.remote.ApiProvider
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Das lernende Produkt-Lexikon (Kürzel je Markt → Artikel) liegt auf dem Server; Room hält nur einen Abzug
 * für die Anzeige. Aktualisieren und Löschen brauchen den Server.
 */
@Singleton
class ReceiptLexiconRepository @Inject constructor(
    private val lexiconDao: ReceiptLexiconDao,
    private val apiProvider: ApiProvider,
) {

    fun observe(): Flow<List<ReceiptLexiconEntity>> = lexiconDao.observeAll()

    /** Holt den Server-Stand und ersetzt den lokalen Abzug. `false` bei Fehler. */
    suspend fun refresh(): Boolean = serverCall("Lexikon laden") {
        val entries = apiProvider.api().receiptLexicon(null).entries.map {
            ReceiptLexiconEntity(
                id = it.id, storeKey = it.storeKey, storeName = it.storeName, rawText = it.rawText,
                name = it.name, category = it.category, offBarcode = it.offBarcode, hits = it.hits,
                updatedAt = it.updatedAt,
            )
        }
        lexiconDao.replaceAll(entries)
    }

    /** Löscht einen Eintrag auf dem Server, danach lokal. `false` bei Fehler (Eintrag bleibt). */
    suspend fun delete(id: String): Boolean = serverCall("Lexikon-Eintrag löschen") {
        apiProvider.api().deleteLexiconEntry(id)
        lexiconDao.deleteById(id)
    }

    private suspend fun serverCall(what: String, block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: IOException) {
        Timber.w(e, "%s fehlgeschlagen", what)
        false
    } catch (e: HttpException) {
        Timber.w(e, "%s fehlgeschlagen (HTTP %d)", what, e.code())
        false
    } catch (e: IllegalArgumentException) {
        Timber.w(e, "%s: Server nicht konfiguriert", what)
        false
    }
}
