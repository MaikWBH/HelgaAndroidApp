package com.helga.android

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architekturregel (Phase 1): ViewModels sprechen nur mit Repositories/UseCases, nie direkt mit
 * DAOs, Retrofit oder der Netzwerk-Fabrik.
 *
 * Ausnahme: `data.remote.SseClient` ist ein Datenschicht-Dienst für SSE-Streaming und darf in
 * `AiGenerateViewModel`/`AiRemixViewModel` verwendet werden (Entscheidung Stufe B, siehe
 * `.claude/guidelines/architecture-layers.md`). Neue Ausnahmen nur nach Entscheidung dort eintragen.
 */
class ArchitectureRulesTest {

    private val forbidden = Regex(
        """com\.helga\.android\.data\.local\.dao\.|""" +
            """com\.helga\.android\.data\.remote\.(SyncApi|SyncApiFactory|ApiProvider)\b|""" +
            """retrofit2\."""
    )

    @Test
    fun viewmodels_nutzen_keine_dao_retrofit_oder_api_fabrik() {
        // Unit-Tests laufen mit dem Arbeitsverzeichnis `app/`.
        val root = File("src/main/kotlin/com/helga/android/ui")
        assertTrue("UI-Verzeichnis nicht gefunden: ${root.absolutePath}", root.isDirectory)

        val viewModels = root.walkTopDown().filter { it.isFile && it.name.endsWith("ViewModel.kt") }.toList()
        assertTrue("Keine ViewModels gefunden", viewModels.isNotEmpty())

        val violations = viewModels.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (forbidden.containsMatchIn(line)) "${file.name}:${index + 1}: ${line.trim()}" else null
            }
        }
        assertTrue("Verbotene Abhängigkeiten in ViewModels:\n" + violations.joinToString("\n"), violations.isEmpty())
    }
}
