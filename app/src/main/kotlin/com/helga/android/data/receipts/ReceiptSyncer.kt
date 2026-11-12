package com.helga.android.data.receipts

import com.helga.android.data.local.dao.ReceiptDao
import com.helga.android.data.local.entity.ReceiptEntity
import com.helga.android.data.preferences.AppPreferences
import com.helga.android.data.remote.ApiProvider
import com.helga.android.data.remote.dto.ReceiptRemoteDto
import kotlinx.coroutines.flow.first
import retrofit2.HttpException
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bon-weiser Abgleich mit dem Server-Archiv (Phase 3a), getrennt vom Voll-Sync.
 * Room bleibt Single Source of Truth: jede Server-Antwort wird zuerst in Room geschrieben.
 * Lokal gehalten wird nur ein Zeitfenster (`receiptRetentionMonths`); der Server behält alles.
 */
@Singleton
class ReceiptSyncer @Inject constructor(
    private val receiptDao: ReceiptDao,
    private val apiProvider: ApiProvider,
    private val preferences: AppPreferences,
    private val imageStore: ReceiptImageStore,
) {

    /** Schickt lokale Änderungen an den Server (PUT bzw. DELETE). Gibt die Anzahl übertragener Bons zurück. */
    suspend fun pushDirty(): Int {
        val api = apiProvider.api()
        var count = 0
        for (receipt in receiptDao.dirtyReceiptsForPush()) {
            try {
                if (receipt.deleted == 1) {
                    api.deleteReceipt(receipt.id, receipt.updatedAt)
                    receiptDao.hardDeleteItemsOf(receipt.id)
                    receiptDao.hardDeleteReceipt(receipt.id)
                } else {
                    val items = receiptDao.itemsForReceipt(receipt.id)
                    val response = api.putReceipt(receipt.id, receipt.toUpsertDto(items))
                    // applied == false: der Server-Stand ist neuer und gewinnt — wird genauso übernommen.
                    writeServerReceipt(response.receipt, receipt)
                }
                count++
            } catch (e: HttpException) {
                if (e.code() !in 400..499) throw e
                Timber.w(e, "Bon %s vom Server abgelehnt (HTTP %d) — bleibt lokal", receipt.id, e.code())
            }
        }
        return count
    }

    /** Holt Änderungen im lokalen Zeitfenster vom Server. Gibt die Anzahl geänderter lokaler Bons zurück. */
    suspend fun pull(today: LocalDate = LocalDate.now()): Int {
        val api = apiProvider.api()
        val months = preferences.receiptRetentionMonths.first()
        val fromDay = ReceiptDates.cutoffDay(today, months)
        val storedFromDay = preferences.receiptSyncFromDay.first()
        // Fenster vergrößert (mehr Monate bzw. „nie löschen"): einmal von vorn, sonst ab dem Cursor.
        val widened = storedFromDay.isNotEmpty() && (fromDay == null || fromDay < storedFromDay)
        val since = if (widened) 0L else preferences.receiptSyncSeq.first()

        val response = api.listReceipts(fromDay, null, since, 1)
        var changed = 0
        for (remote in response.receipts) {
            val local = receiptDao.findById(remote.id)
            val localWins = local != null && local.dirty == 1 && local.updatedAt >= remote.updatedAt
            if (remote.deleted == 1) {
                if (!localWins) {
                    receiptDao.hardDeleteItemsOf(remote.id)
                    receiptDao.hardDeleteReceipt(remote.id)
                    changed++
                }
            } else if (!localWins && local?.pendingUpload != 1) {
                writeServerReceipt(remote, local)
                changed++
            }
        }
        preferences.saveReceiptSyncState(response.serverSeq, fromDay.orEmpty())
        return changed
    }

    /** Verdrängt Bons außerhalb des Zeitfensters aus dem lokalen Cache (nur serverbestätigte Zeilen). */
    suspend fun evict(today: LocalDate = LocalDate.now(), nowMs: Long = System.currentTimeMillis()): Int {
        val months = preferences.receiptRetentionMonths.first()
        val cutoff = ReceiptDates.cutoffDay(today, months) ?: return 0
        val ids = receiptDao.evictableIds(ReceiptDates.dayToEpochMs(cutoff), nowMs)
        ids.forEach { id ->
            receiptDao.hardDeleteItemsOf(id)
            receiptDao.hardDeleteReceipt(id)
        }
        return ids.size
    }

    /** Entfernt Fotos ohne zugehörigen Bon aus der lokalen Ablage. */
    suspend fun cleanupImages() {
        imageStore.cleanupOrphans(receiptDao.allLocalImageUris().toSet())
    }

    /**
     * Lädt einen Zeitraum (inkl. älterer Monate) und hält ihn bis [keepUntilMs] im Cache, damit
     * [evict] ihn nicht sofort wieder entfernt.
     */
    suspend fun loadRange(fromDay: String, toDay: String, keepUntilMs: Long): Int {
        val response = apiProvider.api().listReceipts(fromDay, toDay, 0L, 0)
        var loaded = 0
        for (remote in response.receipts) {
            val local = receiptDao.findById(remote.id)
            val localWins = local != null && local.dirty == 1 && local.updatedAt >= remote.updatedAt
            if (localWins || local?.pendingUpload == 1) continue
            writeServerReceipt(remote, local, cachedUntil = keepUntilMs)
            loaded++
        }
        return loaded
    }

    /** Übernimmt eine einzelne Server-Antwort (z. B. nach Re-Parse) in Room und erhält gerätelokale Felder. */
    suspend fun store(remote: ReceiptRemoteDto) {
        writeServerReceipt(remote, receiptDao.findById(remote.id))
    }

    private suspend fun writeServerReceipt(remote: ReceiptRemoteDto, local: ReceiptEntity?, cachedUntil: Long? = null) {
        val entity = remote.toEntity(local).let { if (cachedUntil != null) it.copy(cachedUntil = cachedUntil) else it }
        receiptDao.upsertReceipt(entity)
        val items = remote.items.map { it.copy(receiptId = remote.id).toEntity() }
        receiptDao.upsertItems(items)
        receiptDao.hardDeleteItemsExcept(remote.id, items.map { it.id })
    }
}
