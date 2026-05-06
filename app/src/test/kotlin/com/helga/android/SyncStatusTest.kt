package com.helga.android

import com.helga.android.data.sync.SyncStatus
import com.helga.android.data.sync.SyncStatusHolder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncStatusTest {

    @Test
    fun `initial status is Idle`() = runTest {
        val holder = SyncStatusHolder()
        assertTrue(holder.status.first() is SyncStatus.Idle)
    }

    @Test
    fun `update to Syncing reflects in flow`() = runTest {
        val holder = SyncStatusHolder()
        holder.update(SyncStatus.Syncing)
        assertTrue(holder.status.first() is SyncStatus.Syncing)
    }

    @Test
    fun `update to Error exposes message`() = runTest {
        val holder = SyncStatusHolder()
        holder.update(SyncStatus.Error("Verbindung abgelehnt"))
        val status = holder.status.first()
        assertTrue(status is SyncStatus.Error)
        assertEquals("Verbindung abgelehnt", (status as SyncStatus.Error).message)
    }

    @Test
    fun `update to Success stores timestamp`() = runTest {
        val holder = SyncStatusHolder()
        val ts = 1_700_000_000_000L
        holder.update(SyncStatus.Success(ts))
        val status = holder.status.first()
        assertTrue(status is SyncStatus.Success)
        assertEquals(ts, (status as SyncStatus.Success).ts)
    }

    @Test
    fun `error message is null when status is not Error`() = runTest {
        val holder = SyncStatusHolder()
        holder.update(SyncStatus.Success(1L))
        val errorMsg = holder.status.first().let {
            if (it is SyncStatus.Error) it.message else null
        }
        assertNull(errorMsg)
    }
}
