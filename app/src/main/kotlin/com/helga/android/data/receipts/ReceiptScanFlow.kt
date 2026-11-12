package com.helga.android.data.receipts

import com.helga.android.data.local.entity.ReceiptEntity
import com.helga.android.data.local.entity.ReceiptItemEntity
import com.helga.android.data.remote.dto.ReceiptScanResponse
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.time.ZoneOffset

/** Parameter des Server-Scans (`POST /api/receipts/scan`). */
class ScanRequest(
    val receiptId: String,
    val file: File,
    val shoppingListId: String,
    val capturedAt: Long,
)

/**
 * Entscheidungslogik des Bon-Scans ohne Android-Typen (die Bitmap ist nur ein Typparameter), damit sie ohne
 * Emulator testbar ist:
 *  1. Server liest aus (`parsed`) → Bon aus der Antwort in Room, Foto löschen → [ReceiptScanOutcome.Parsed].
 *  2. Server erreichbar, KI fehlgeschlagen → lokale ML-Kit-Werte als „vorläufig", Foto liegt auf dem Server
 *     (Warteschlange dort), lokale Datei löschen.
 *  3. Server nicht erreichbar (IOException, HTTP-Fehler, nicht konfiguriert) → ML-Kit-Werte als „vorläufig",
 *     Foto bleibt lokal (`pendingUpload = 1`) bis zum Upload.
 */
class ReceiptScanFlow<B>(
    private val scanOnServer: suspend (ScanRequest) -> ReceiptScanResponse,
    private val scanLocally: suspend (B) -> Pair<ReceiptEntity, List<ReceiptItemEntity>>,
    private val saveLocal: suspend (ReceiptEntity, List<ReceiptItemEntity>) -> Unit,
    private val deleteFile: (File) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun run(receiptId: String, file: File, bitmap: B, shoppingListId: String): ReceiptScanOutcome {
        val now = clock()
        val response = try {
            scanOnServer(ScanRequest(receiptId, file, shoppingListId, now))
        } catch (_: IOException) {
            return offline(receiptId, file, bitmap, shoppingListId, now)
        } catch (_: HttpException) {
            return offline(receiptId, file, bitmap, shoppingListId, now)
        } catch (_: IllegalArgumentException) { // Server nicht konfiguriert (ApiProvider.api())
            return offline(receiptId, file, bitmap, shoppingListId, now)
        }

        if (response.parsed) {
            val remote = response.receipt
            saveLocal(remote.toEntity(null), remote.items.map { it.copy(receiptId = remote.id).toEntity() })
            deleteFile(file)
            return ReceiptScanOutcome.Parsed(receiptId)
        }

        val (receipt, items) = provisional(receiptId, bitmap, shoppingListId, now)
        saveLocal(
            receipt.copy(imagePath = response.receipt.imagePath, parseStatus = "queued", dirty = 1),
            items.map { it.copy(dirty = 1) },
        )
        deleteFile(file)
        return ReceiptScanOutcome.Provisional(receiptId, queuedOnServer = true)
    }

    private suspend fun offline(
        receiptId: String, file: File, bitmap: B, shoppingListId: String, now: Long,
    ): ReceiptScanOutcome {
        val (receipt, items) = provisional(receiptId, bitmap, shoppingListId, now)
        saveLocal(
            receipt.copy(localImageUri = file.absolutePath, pendingUpload = 1, parseStatus = "none", dirty = 0),
            items,
        )
        return ReceiptScanOutcome.Provisional(receiptId, queuedOnServer = false)
    }

    private suspend fun provisional(
        receiptId: String, bitmap: B, shoppingListId: String, now: Long,
    ): Pair<ReceiptEntity, List<ReceiptItemEntity>> {
        val (scanned, scannedItems) = scanLocally(bitmap)
        val receipt = scanned.copy(
            id = receiptId,
            shoppingListId = shoppingListId,
            purchaseDay = ReceiptDates.epochMsToDay(scanned.purchaseDate, ZoneOffset.UTC),
            status = "provisional",
            source = "on_device",
            updatedAt = now,
            deleted = 0,
        )
        return receipt to scannedItems.map { it.copy(receiptId = receiptId, updatedAt = now) }
    }
}
