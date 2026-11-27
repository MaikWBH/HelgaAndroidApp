package com.helga.android

import com.helga.android.data.receipts.ORPHAN_MIN_AGE_MS
import com.helga.android.data.receipts.orphanFiles
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptImageStoreTest {

    private val now = ORPHAN_MIN_AGE_MS

    @Test
    fun `referenced files stay and foreign files are listed`() {
        val existing = listOf("/a/1.jpg" to 0L, "/a/2.jpg" to 0L, "/a/3.jpg" to 0L)
        assertEquals(listOf("/a/2.jpg", "/a/3.jpg"), orphanFiles(existing, setOf("/a/1.jpg"), now))
    }

    @Test
    fun `nothing referenced lists everything and nothing existing lists nothing`() {
        assertEquals(listOf("/a/1.jpg"), orphanFiles(listOf("/a/1.jpg" to 0L), emptySet(), now))
        assertEquals(emptyList<String>(), orphanFiles(emptyList(), setOf("/a/1.jpg"), now))
        assertEquals(emptyList<String>(), orphanFiles(emptyList(), emptySet(), now))
    }

    @Test
    fun `young orphan files stay`() {
        assertEquals(emptyList<String>(), orphanFiles(listOf("/a/1.jpg" to now - 1_000L), emptySet(), now))
    }
}
