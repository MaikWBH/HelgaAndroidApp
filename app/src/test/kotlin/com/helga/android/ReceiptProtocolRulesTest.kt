package com.helga.android

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regeln für das Bon-Archiv (Phase 3a): Bons laufen über ein eigenes bon-weises Protokoll (`ReceiptSyncer`),
 * nicht über den Voll-Sync, und gelöscht wird hart nur bei der Cache-Verdrängung serverbestätigter Zeilen.
 * Siehe `.claude/guidelines/sync-patterns.md` („Server-Archiv mit lokalem Cache").
 */
class ReceiptProtocolRulesTest {

    // Unit-Tests laufen mit dem Arbeitsverzeichnis `app/`.
    private val root = File("src/main/kotlin/com/helga/android")

    private fun sources(): List<File> {
        assertTrue("Quellverzeichnis nicht gefunden: ${root.absolutePath}", root.isDirectory)
        return root.walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.toList()
    }

    @Test
    fun syncEngine_kennt_keine_bons() {
        val source = File(root, "data/sync/SyncEngine.kt").readText()
        assertTrue("SyncEngine darf ReceiptEntity nicht enthalten (Bons: ReceiptSyncer)", !source.contains("ReceiptEntity"))
        assertTrue("SyncEngine darf receiptDao nicht enthalten (Bons: ReceiptSyncer)", !source.contains("receiptDao"))
    }

    @Test
    fun hartes_loeschen_von_bons_nur_in_receipts_dao_und_repository() {
        val hardDelete = Regex("""\bhardDelete(Receipt|Items)""")
        val violations = sources()
            .filter {
                val path = it.invariantSeparatorsPath
                !path.contains("/data/receipts/") && !path.contains("/data/local/dao/") && it.name != "ReceiptRepository.kt"
            }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (hardDelete.containsMatchIn(line)) "${file.name}:${index + 1}: ${line.trim()}" else null
                }
            }
        assertTrue(
            "Hartes Löschen von Bons nur in data/receipts/, data/local/dao/ und ReceiptRepository:\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun transaktionsmethoden_tragen_transaction() {
        val lines = File(root, "data/local/dao/ReceiptDao.kt").readLines()
        val names = listOf("upsertReceiptWithItems", "replaceWithServerState", "hardDeleteReceiptWithItems")
        names.forEach { name ->
            val index = lines.indexOfFirst { it.contains("suspend fun $name(") }
            assertTrue("ReceiptDao.$name fehlt", index >= 0)
            val previous = lines.subList(0, index).asReversed().firstOrNull {
                val t = it.trim()
                t.isNotEmpty() && !t.startsWith("//") && !t.startsWith("/*") && !t.startsWith("*")
            }
            assertTrue("ReceiptDao.$name braucht @Transaction (Bon und Positionen atomar)", previous?.trim() == "@Transaction")
        }
    }
}
