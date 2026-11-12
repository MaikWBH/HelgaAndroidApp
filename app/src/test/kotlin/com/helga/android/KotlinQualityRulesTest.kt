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
}
