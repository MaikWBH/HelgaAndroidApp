package com.helga.android

import com.helga.android.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.sql.Connection

/** Room-Migration 36 → 37 (Phase 3a) gegen echte SQLite auf der JVM. */
class Migration36To37Test {

    private fun Connection.exec(sql: String) = createStatement().use { it.execute(sql) }

    private fun Connection.insertReceipt(
        id: String,
        purchaseDate: Long = 0,
        imagePath: String = "",
        localImageUri: String = "",
        deleted: Int = 0,
        dirty: Int = 0,
    ) = exec(
        "INSERT INTO receipts (id, storeId, storeName, shoppingListId, purchaseDate, totalAmount, currency, " +
            "imagePath, localImageUri, rawOcrText, status, source, updatedAt, deleted, dirty) " +
            "VALUES ('$id', '', 'REWE', '', $purchaseDate, 1.0, 'EUR', '$imagePath', '$localImageUri', '', " +
            "'scanned', 'on_device', 5, $deleted, $dirty)"
    )

    private fun Connection.insertItem(id: String, receiptId: String) = exec(
        "INSERT INTO receipt_items (id, receiptId, position, rawText, name, quantity, unitPrice, totalPrice, " +
            "matchedShoppingItemId, matchStatus, updatedAt, deleted, dirty) " +
            "VALUES ('$id', '$receiptId', 0, 'MILCH', 'Milch', 1.0, 1.0, 1.0, '', '', 5, 0, 0)"
    )

    private fun Connection.scalar(sql: String): Any? =
        createStatement().use { st -> st.executeQuery(sql).use { rs -> if (rs.next()) rs.getObject(1) else null } }

    @Test
    fun `migration produces the exported schema 37 and migrates the data`() {
        RoomMigrationHarness.open().use { conn ->
            RoomMigrationHarness.createFromSchema(conn, 36)
            conn.insertReceipt("A", purchaseDate = 1_794_268_800_000L, localImageUri = "file:///a.jpg")
            conn.insertReceipt("B", deleted = 1, dirty = 1)
            conn.insertItem("ib", "B")
            conn.insertReceipt("C", imagePath = "server.jpg")
            conn.insertItem("ic", "C")

            RoomMigrationHarness.run(conn, AppDatabase.MIGRATION_36_37)
            RoomMigrationHarness.assertMatchesSchema(conn, 37)

            assertEquals("2026-11-10", conn.scalar("SELECT purchaseDay FROM receipts WHERE id = 'A'"))
            assertEquals(1, conn.scalar("SELECT pendingUpload FROM receipts WHERE id = 'A'"))
            assertEquals("none", conn.scalar("SELECT parseStatus FROM receipts WHERE id = 'A'"))
            assertEquals(0, conn.scalar("SELECT pendingUpload FROM receipts WHERE id = 'C'"))
            assertEquals("", conn.scalar("SELECT purchaseDay FROM receipts WHERE id = 'C'"))

            // Annahme 7: lokal gelöschte Bons samt Positionen sind weg
            assertEquals(0, conn.scalar("SELECT COUNT(*) FROM receipts WHERE id = 'B'"))
            assertEquals(0, conn.scalar("SELECT COUNT(*) FROM receipt_items WHERE id = 'ib'"))

            assertEquals("", conn.scalar("SELECT unit FROM receipt_items WHERE id = 'ic'"))
            assertEquals(1.0, conn.scalar("SELECT confidence FROM receipt_items WHERE id = 'ic'"))
            assertFalse(conn.scalar("SELECT COUNT(*) FROM receipt_lexicon") != 0)
        }
    }
}
