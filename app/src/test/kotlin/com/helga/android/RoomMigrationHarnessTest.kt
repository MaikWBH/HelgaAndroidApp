package com.helga.android

import org.junit.Assert.assertThrows
import org.junit.Test

class RoomMigrationHarnessTest {

    @Test
    fun `schema 36 can be created and matches itself`() {
        RoomMigrationHarness.open().use { conn ->
            RoomMigrationHarness.createFromSchema(conn, 36)
            RoomMigrationHarness.assertMatchesSchema(conn, 36)
        }
    }

    @Test
    fun `an extra column makes the comparison fail`() {
        RoomMigrationHarness.open().use { conn ->
            RoomMigrationHarness.createFromSchema(conn, 36)
            conn.createStatement().use { it.execute("ALTER TABLE stores ADD COLUMN x TEXT") }
            assertThrows(AssertionError::class.java) {
                RoomMigrationHarness.assertMatchesSchema(conn, 36)
            }
        }
    }
}
