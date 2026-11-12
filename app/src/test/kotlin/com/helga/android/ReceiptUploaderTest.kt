package com.helga.android

import com.helga.android.data.local.dao.ReceiptDao
import com.helga.android.data.local.entity.ReceiptEntity
import com.helga.android.data.receipts.ReceiptImageStore
import com.helga.android.data.receipts.ReceiptUploader
import com.helga.android.data.remote.ApiProvider
import com.helga.android.data.remote.SyncApi
import com.helga.android.data.remote.dto.ReceiptImageResponse
import com.helga.android.data.remote.dto.ReceiptRemoteDto
import com.helga.android.data.remote.dto.ReceiptScanResponse
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class ReceiptUploaderTest {

    @get:Rule val tmp = TemporaryFolder()

    private val dao: ReceiptDao = mockk(relaxed = true)
    private val api: SyncApi = mockk()
    private val provider: ApiProvider = mockk()
    private val store: ReceiptImageStore = mockk(relaxed = true)
    private lateinit var uploader: ReceiptUploader

    @Before
    fun setUp() {
        coEvery { provider.api() } returns api
        uploader = ReceiptUploader(dao, provider, store, Moshi.Builder().build())
    }

    private fun receipt(id: String, status: String, file: java.io.File?, dirty: Int = 0) = ReceiptEntity(
        id = id, status = status, localImageUri = file?.absolutePath.orEmpty(), pendingUpload = 1, dirty = dirty,
    )

    private fun scanResponse(parsed: Boolean) = ReceiptScanResponse(
        parsed = parsed,
        receipt = ReceiptRemoteDto(id = "p", storeName = "REWE", imagePath = "receipt_p.jpg", status = if (parsed) "review" else "provisional"),
    )

    @Test
    fun `provisional receipt with parsed answer is replaced by the server receipt`() = runTest {
        val file = tmp.newFile("a.jpg")
        coEvery { dao.pendingUploads() } returns listOf(receipt("p", "provisional", file))
        coEvery { dao.itemsForReceipt("p") } returns emptyList()
        coEvery { api.scanReceipt(any(), any(), any(), any(), any()) } returns scanResponse(true)
        val result = uploader.uploadPending()
        assertEquals(1, result.done); assertEquals(false, result.retry)
        coVerify { dao.upsertReceipt(match { it.id == "p" && it.status == "review" && it.pendingUpload == 0 && it.localImageUri == "" }) }
        coVerify { store.delete(file.absolutePath) }
    }

    @Test
    fun `provisional receipt stays local when the server could not parse`() = runTest {
        val file = tmp.newFile("b.jpg")
        coEvery { dao.pendingUploads() } returns listOf(receipt("p", "provisional", file))
        coEvery { dao.itemsForReceipt("p") } returns emptyList()
        coEvery { api.scanReceipt(any(), any(), any(), any(), any()) } returns scanResponse(false)
        assertEquals(1, uploader.uploadPending().done)
        coVerify {
            dao.upsertReceipt(match {
                it.status == "provisional" && it.imagePath == "receipt_p.jpg" && it.parseStatus == "queued" &&
                    it.pendingUpload == 0 && it.localImageUri == ""
            })
        }
    }

    @Test
    fun `existing receipt only uploads the photo and keeps dirty`() = runTest {
        val file = tmp.newFile("c.jpg")
        coEvery { dao.pendingUploads() } returns listOf(receipt("c", "confirmed", file, dirty = 1))
        coEvery { api.uploadReceiptImage("c", any()) } returns ReceiptImageResponse("receipt_c.jpg")
        assertEquals(1, uploader.uploadPending().done)
        coVerify { dao.clearPendingUpload("c", "receipt_c.jpg") }
        coVerify(exactly = 0) { dao.upsertReceipt(any()) }
    }

    @Test
    fun `missing file clears the queue entry and marks provisional receipts dirty`() = runTest {
        coEvery { dao.pendingUploads() } returns listOf(
            receipt("m1", "provisional", null),
            receipt("m2", "confirmed", java.io.File(tmp.root, "weg.jpg")),
        )
        assertEquals(2, uploader.uploadPending().done)
        coVerify { dao.upsertReceipt(match { it.id == "m1" && it.pendingUpload == 0 && it.dirty == 1 }) }
        coVerify { dao.upsertReceipt(match { it.id == "m2" && it.pendingUpload == 0 && it.dirty == 0 }) }
    }

    @Test
    fun `io exception keeps the row and asks for a retry`() = runTest {
        val file = tmp.newFile("d.jpg")
        coEvery { dao.pendingUploads() } returns listOf(receipt("p", "provisional", file))
        coEvery { dao.itemsForReceipt("p") } returns emptyList()
        coEvery { api.scanReceipt(any(), any(), any(), any(), any()) } throws IOException("offline")
        val result = uploader.uploadPending()
        assertEquals(0, result.done); assertEquals(true, result.retry)
        coVerify(exactly = 0) { store.delete(any()) }
    }

    @Test
    fun `client errors do not force a retry but server errors do`() = runTest {
        val f1 = tmp.newFile("e.jpg"); val f2 = tmp.newFile("f.jpg")
        coEvery { dao.pendingUploads() } returns listOf(receipt("a", "confirmed", f1))
        coEvery { api.uploadReceiptImage("a", any()) } throws HttpException(Response.error<Any>(413, "".toResponseBody(null)))
        assertEquals(false, uploader.uploadPending().retry)

        coEvery { dao.pendingUploads() } returns listOf(receipt("b", "confirmed", f2))
        coEvery { api.uploadReceiptImage("b", any()) } throws HttpException(Response.error<Any>(503, "".toResponseBody(null)))
        assertEquals(true, uploader.uploadPending().retry)
    }
}
