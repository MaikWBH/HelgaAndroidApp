package com.helga.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests für Last-Write-Wins-Logik, die im SyncEngine verwendet wird.
 * Wird ohne Android-Runtime ausgeführt (reines JVM-Test).
 */
class SyncLwwTest {

    data class RemoteRecord(val id: String, val updatedAt: Long)
    data class LocalTimestamp(val id: String, val updatedAt: Long)

    private fun filterServerWins(
        remote: List<RemoteRecord>,
        local: List<LocalTimestamp>,
    ): List<RemoteRecord> {
        if (remote.isEmpty()) return emptyList()
        val localMap = local.associateBy { it.id }
        return remote.filter { r ->
            val localTs = localMap[r.id]?.updatedAt ?: -1L
            r.updatedAt > localTs
        }
    }

    @Test
    fun `server wins when server record is newer`() {
        val remote = listOf(RemoteRecord("a", 200L))
        val local = listOf(LocalTimestamp("a", 100L))
        val winners = filterServerWins(remote, local)
        assertEquals(1, winners.size)
        assertEquals("a", winners[0].id)
    }

    @Test
    fun `client wins when local record is newer`() {
        val remote = listOf(RemoteRecord("a", 100L))
        val local = listOf(LocalTimestamp("a", 200L))
        val winners = filterServerWins(remote, local)
        assertTrue(winners.isEmpty())
    }

    @Test
    fun `server wins on new record (no local entry)`() {
        val remote = listOf(RemoteRecord("new", 100L))
        val local = emptyList<LocalTimestamp>()
        val winners = filterServerWins(remote, local)
        assertEquals(1, winners.size)
    }

    @Test
    fun `equal timestamps - client wins (no update needed)`() {
        val ts = 100L
        val remote = listOf(RemoteRecord("a", ts))
        val local = listOf(LocalTimestamp("a", ts))
        val winners = filterServerWins(remote, local)
        assertTrue(winners.isEmpty())
    }

    @Test
    fun `multiple records mixed`() {
        val remote = listOf(
            RemoteRecord("a", 200L),  // server newer → server wins
            RemoteRecord("b", 50L),   // server older → client wins
            RemoteRecord("c", 300L),  // new record → server wins
        )
        val local = listOf(
            LocalTimestamp("a", 100L),
            LocalTimestamp("b", 100L),
        )
        val winners = filterServerWins(remote, local)
        assertEquals(2, winners.size)
        assertTrue(winners.any { it.id == "a" })
        assertTrue(winners.any { it.id == "c" })
    }

    @Test
    fun `empty remote returns empty`() {
        val local = listOf(LocalTimestamp("a", 100L))
        val winners = filterServerWins(emptyList(), local)
        assertTrue(winners.isEmpty())
    }
}
