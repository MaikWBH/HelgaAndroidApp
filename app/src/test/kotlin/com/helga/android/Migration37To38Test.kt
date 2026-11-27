package com.helga.android

import com.helga.android.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Test
import java.sql.Connection

/** Room-Migration 37 → 38 (Phase 3b) gegen echte SQLite auf der JVM. */
class Migration37To38Test {

    private fun Connection.exec(sql: String) = createStatement().use { it.execute(sql) }

    private fun Connection.categoryOf(id: String): String =
        createStatement().use { st ->
            st.executeQuery("SELECT categoryKey FROM store_aisles WHERE id = '$id'").use { rs -> rs.next(); rs.getString(1) }
        }

    @Test
    fun `migration adds the category key, fills the old default aisles and creates the aggregate table`() {
        RoomMigrationHarness.open().use { conn ->
            RoomMigrationHarness.createFromSchema(conn, 37)
            listOf("a1" to "Obst & Gemüse", "a2" to "Drogerie & Haushalt", "a3" to "Kasse").forEach { (id, name) ->
                conn.exec(
                    "INSERT INTO store_aisles (id, storeId, aisleName, sortOrder, updatedAt, deleted, dirty) " +
                        "VALUES ('$id', 's1', '$name', 0, 5, 0, 0)"
                )
            }

            RoomMigrationHarness.run(conn, AppDatabase.MIGRATION_37_38)
            RoomMigrationHarness.assertMatchesSchema(conn, 38)

            assertEquals("obst_gemuese", conn.categoryOf("a1"))
            assertEquals("drogerie", conn.categoryOf("a2"))
            assertEquals("", conn.categoryOf("a3"))
            // dirty bleibt unverändert: der Server füllt dieselben Werte per Backfill
            conn.createStatement().use { st ->
                st.executeQuery("SELECT COUNT(*) FROM store_aisles WHERE dirty = 1").use { rs ->
                    rs.next(); assertEquals(0, rs.getInt(1))
                }
            }
        }
    }
}
