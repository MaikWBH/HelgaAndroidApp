package com.helga.android

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regeln aus `.claude/guidelines/kotlin-quality.md`, die sich per Quelltext-Scan absichern lassen (Phase 6).
 */
class KotlinQualityRulesTest {

    // Unit-Tests laufen mit dem Arbeitsverzeichnis `app/`.
    private val root = File("src/main/kotlin/com/helga/android")

    private fun sources(): List<File> {
        assertTrue("Quellverzeichnis nicht gefunden: ${root.absolutePath}", root.isDirectory)
        return root.walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.toList()
    }

    @Test
    fun kein_not_null_assertion_operator() {
        val stringLiteral = Regex("\"(\\\\.|[^\"\\\\])*\"")
        val violations = sources().flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val code = line.substringBefore("//").replace(stringLiteral, "\"\"")
                if (code.contains("!!")) "${file.name}:${index + 1}: ${line.trim()}" else null
            }
        }
        assertTrue(
            "`!!` ist verboten (kotlin-quality §1):\n" + violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    /** Nicht-suspendierende Fundstellen: dort ist ein Coroutine-Abbruch nicht möglich. */
    private val keineCoroutine = setOf(
        "AppPreferences.kt", "ApiErrorMapping.kt", "ReceiptImagePreprocessor.kt", "RecipeJsonLdParser.kt", "BarcodeScanner.kt",
    )

    @Test
    fun abbrueche_werden_weitergeworfen() {
        val rethrow = Regex("""catch \(e: CancellationException\) \{\s*throw e\s*\}""")
        val generic = Regex("""catch \(\w+: Exception\)""")
        val violations = sources().filter { it.name !in keineCoroutine }.mapNotNull { file ->
            val text = file.readText()
            val caught = generic.findAll(text).count()
            val rethrown = rethrow.findAll(text).count()
            if (rethrown < caught) "${file.name}: $caught x catch (Exception), $rethrown x Rethrow" else null
        }
        assertTrue(
            "`catch (e: Exception)` ohne `catch (e: CancellationException) { throw e }` davor (kotlin-quality):\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }
}
