package com.helga.android

import com.helga.android.data.receipts.orphanFiles
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptImageStoreTest {

    @Test
    fun `referenced files stay and foreign files are listed`() {
        val existing = listOf("/a/1.jpg", "/a/2.jpg", "/a/3.jpg")
        assertEquals(listOf("/a/2.jpg", "/a/3.jpg"), orphanFiles(existing, setOf("/a/1.jpg")))
    }

    @Test
    fun `nothing referenced lists everything and nothing existing lists nothing`() {
        assertEquals(listOf("/a/1.jpg"), orphanFiles(listOf("/a/1.jpg"), emptySet()))
        assertEquals(emptyList<String>(), orphanFiles(emptyList(), setOf("/a/1.jpg")))
        assertEquals(emptyList<String>(), orphanFiles(emptyList(), emptySet()))
    }
}
