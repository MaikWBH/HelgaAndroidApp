package com.helga.android

import com.helga.android.data.local.entity.ReceiptEntity
import com.helga.android.data.local.entity.ReceiptItemEntity
import com.helga.android.data.receipts.ReceiptScanFlow
import com.helga.android.data.receipts.ReceiptScanOutcome
import com.helga.android.data.remote.dto.ReceiptRemoteDto
import com.helga.android.data.remote.dto.ReceiptRemoteItemDto
import com.helga.android.data.remote.dto.ReceiptScanResponse
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.File
import java.io.IOException

class ReceiptScanFlowTest {

    private val file = File("/tmp/foto.jpg")
    private val now = 1_794_300_000_000L

    private var savedReceipt: ReceiptEntity? = null
    private var savedItems: List<ReceiptItemEntity> = emptyList()
    private val deleted = mutableListOf<File>()

    private fun flow(serverCall: suspend () -> ReceiptScanResponse) = ReceiptScanFlow<Unit>(
        scanOnServer = { serverCall() },
        scanLocally = {
            ReceiptEntity(id = "ml", storeName = "Lidl", purchaseDate = 1_794_268_800_000L, totalAmount = 4.5) to
                listOf(ReceiptItemEntity(id = "ml1", receiptId = "ml", name = "Brot"))
        },
        saveLocal = { receipt, items -> savedReceipt = receipt; savedItems = items },
        deleteFile = { deleted += it },
        clock = { now },
    )

    private fun response(parsed: Boolean, imagePath: String = "receipt_r1.jpg") = ReceiptScanResponse(
        parsed = parsed,
        receipt = ReceiptRemoteDto(
            id = "r1", storeName = "REWE", status = if (parsed) "review" else "provisional",
            parseStatus = if (parsed) "done" else "queued", imagePath = imagePath, updatedAt = 50,
            items = listOf(ReceiptRemoteItemDto(id = "s1", name = "Milch")),
        ),
    )

    @Test
    fun `server success stores the server receipt and deletes the file`() = runTest {
        val outcome = flow { response(true) }.run("r1", file, Unit, "list1")
        assertEquals(ReceiptScanOutcome.Parsed("r1"), outcome)
        assertEquals("REWE", savedReceipt?.storeName)
        assertEquals(0, savedReceipt?.dirty)
        assertEquals(0, savedReceipt?.pendingUpload)
        assertEquals(listOf("s1"), savedItems.map { it.id })
        assertEquals(listOf(file), deleted)
    }

    @Test
    fun `server side ai failure keeps provisional values queued on the server`() = runTest {
        val outcome = flow { response(false) }.run("r1", file, Unit, "list1")
        assertEquals(ReceiptScanOutcome.Provisional("r1", queuedOnServer = true), outcome)
        val receipt = savedReceipt!!
        assertEquals("r1", receipt.id)
        assertEquals("provisional", receipt.status)
        assertEquals("on_device", receipt.source)
        assertEquals("queued", receipt.parseStatus)
        assertEquals("receipt_r1.jpg", receipt.imagePath)
        assertEquals("", receipt.localImageUri)
        assertEquals(1, receipt.dirty)
        assertEquals(0, receipt.pendingUpload)
        assertEquals("list1", receipt.shoppingListId)
        assertEquals("2026-11-10", receipt.purchaseDay)
        assertEquals(now, receipt.updatedAt)
        assertEquals(listOf("r1"), savedItems.map { it.receiptId })
        assertEquals(listOf(file), deleted)
    }

    @Test
    fun `io exception means offline and keeps the photo for the upload queue`() = runTest {
        val outcome = flow { throw IOException("kein Netz") }.run("r1", file, Unit, "list1")
        assertEquals(ReceiptScanOutcome.Provisional("r1", queuedOnServer = false), outcome)
        val receipt = savedReceipt!!
        assertEquals(file.absolutePath, receipt.localImageUri)
        assertEquals(1, receipt.pendingUpload)
        assertEquals(0, receipt.dirty)
        assertEquals("none", receipt.parseStatus)
        assertEquals("", receipt.imagePath)
        assertTrue(deleted.isEmpty())
    }

    @Test
    fun `http error counts like offline`() = runTest {
        val notFound = HttpException(Response.error<Any>(404, "".toResponseBody(null)))
        val outcome = flow { throw notFound }.run("r1", file, Unit, "")
        assertEquals(ReceiptScanOutcome.Provisional("r1", queuedOnServer = false), outcome)
        assertEquals(1, savedReceipt?.pendingUpload)
    }

    @Test
    fun `missing server configuration counts like offline`() = runTest {
        val outcome = flow { throw IllegalArgumentException("Helga-Server nicht konfiguriert") }.run("r1", file, Unit, "")
        assertEquals(ReceiptScanOutcome.Provisional("r1", queuedOnServer = false), outcome)
    }
}
