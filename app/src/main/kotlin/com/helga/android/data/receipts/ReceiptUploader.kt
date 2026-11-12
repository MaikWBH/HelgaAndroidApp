package com.helga.android.data.receipts

import com.helga.android.data.local.dao.ReceiptDao
import com.helga.android.data.local.entity.ReceiptEntity
import com.helga.android.data.remote.ApiProvider
import com.helga.android.data.remote.dto.ReceiptRemoteDto
import com.helga.android.data.remote.dto.ReceiptUpsertDto
import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Ergebnis eines Durchlaufs: [done] bearbeitete Bons, [retry] = mindestens ein Bon braucht einen neuen Versuch. */
data class UploadResult(val done: Int, val retry: Boolean)

/**
 * Lokale Upload-Warteschlange für Bon-Fotos (`pendingUpload = 1`). Vorläufige Bons gehen über den Scan-Endpunkt
 * (KI-Auswertung nachholen), Bestandsbons nur als Foto. Danach liegt auf dem Handy keine Foto-Datei mehr.
 */
@Singleton
class ReceiptUploader @Inject constructor(
    private val receiptDao: ReceiptDao,
    private val apiProvider: ApiProvider,
    private val imageStore: ReceiptImageStore,
    private val moshi: Moshi,
) {

    suspend fun uploadPending(): UploadResult {
        val pending = receiptDao.pendingUploads()
        if (pending.isEmpty()) return UploadResult(0, false)
        val api = try {
            apiProvider.api()
        } catch (e: IllegalArgumentException) { // Server nicht konfiguriert
            Timber.w(e, "Bon-Upload: Server nicht konfiguriert")
            return UploadResult(0, true)
        }

        var done = 0
        var retry = false
        for (receipt in pending) {
            try {
                if (uploadOne(api, receipt)) done++
            } catch (e: IOException) {
                Timber.w(e, "Bon-Upload offline: %s", receipt.id)
                retry = true
            } catch (e: HttpException) {
                if (e.code() >= 500) retry = true
                Timber.w(e, "Bon-Upload abgelehnt (HTTP %d): %s", e.code(), receipt.id)
            }
        }
        return UploadResult(done, retry)
    }

    /** Gibt true zurück, wenn der Bon abgearbeitet wurde (auch wenn die Datei fehlte). */
    private suspend fun uploadOne(api: com.helga.android.data.remote.SyncApi, receipt: ReceiptEntity): Boolean {
        val file = File(receipt.localImageUri)
        val provisional = receipt.status == "provisional"
        if (receipt.localImageUri.isBlank() || !file.exists()) {
            receiptDao.upsertReceipt(
                receipt.copy(pendingUpload = 0, localImageUri = "", dirty = if (provisional) 1 else receipt.dirty)
            )
            return true
        }

        val part = MultipartBody.Part.createFormData(
            "file", file.name, file.asRequestBody("image/jpeg".toMediaType()),
        )
        if (provisional) {
            val plain = "text/plain".toMediaType()
            val items = receiptDao.itemsForReceipt(receipt.id)
            val preliminary = moshi.adapter(ReceiptUpsertDto::class.java).toJson(receipt.toUpsertDto(items))
            val response = api.scanReceipt(
                file = part,
                receiptId = receipt.id.toRequestBody(plain),
                shoppingListId = receipt.shoppingListId.toRequestBody(plain),
                capturedAt = receipt.updatedAt.toString().toRequestBody(plain),
                preliminary = preliminary.toRequestBody("application/json".toMediaType()),
            )
            if (response.parsed) {
                replaceWithServer(response.receipt, receipt)
            } else {
                receiptDao.upsertReceipt(
                    receipt.copy(
                        imagePath = response.receipt.imagePath, parseStatus = "queued",
                        pendingUpload = 0, localImageUri = "",
                    )
                )
            }
        } else {
            val response = api.uploadReceiptImage(receipt.id, part)
            receiptDao.clearPendingUpload(receipt.id, response.imagePath)
        }
        imageStore.delete(file.absolutePath)
        return true
    }

    private suspend fun replaceWithServer(remote: ReceiptRemoteDto, local: ReceiptEntity) {
        receiptDao.upsertReceipt(remote.toEntity(local).copy(pendingUpload = 0, localImageUri = ""))
        val items = remote.items.map { it.copy(receiptId = remote.id).toEntity() }
        receiptDao.upsertItems(items)
        receiptDao.hardDeleteItemsExcept(remote.id, items.map { it.id })
    }
}
