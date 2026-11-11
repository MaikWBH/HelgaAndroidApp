package com.helga.android

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * UI-Konventionen (Phase 2): keine `collectAsState(`, keine nicht gespiegelten ArrowBack-Icons und
 * keine hartkodierten Wort-Literale in Compose-Texten. `ui/widget/` (Glance) ist ausgenommen.
 */
class UiConventionsTest {

    private val allowedLiterals = setOf("sync-rotate", "angle")

    private val textStart =
        Regex("""(Text\(\s*|(text|label|title|placeholder|contentDescription|message) = )"""")
    private val interpolationBlock = Regex("""\$\{[^}]*\}""")
    private val interpolationName = Regex("""\$[A-Za-z_][A-Za-z0-9_]*""")
    private val wordChars = Regex("""[A-Za-zÄÖÜäöüß]{2,}""")

    private fun sources(): List<File> {
        // Unit-Tests laufen mit dem Arbeitsverzeichnis `app/`.
        val root = File("src/main/kotlin/com/helga/android")
        assertTrue("Quellverzeichnis nicht gefunden: ${root.absolutePath}", root.isDirectory)
        return root.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .filter { !it.invariantSeparatorsPath.contains("/ui/widget/") }
            .toList()
    }

    private fun violations(predicate: (String) -> Boolean): List<String> =
        sources().flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (predicate(line)) "${file.name}:${index + 1}: ${line.trim()}" else null
            }
        }

    @Test
    fun keine_collectAsState_ohne_lifecycle() {
        val found = violations { it.contains("collectAsState(") }
        assertTrue("collectAsStateWithLifecycle verwenden:\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test
    fun keine_nicht_gespiegelten_arrowback_icons() {
        val found = violations { it.contains("Icons.Filled.ArrowBack") || it.contains("Icons.Default.ArrowBack") }
        assertTrue("Icons.AutoMirrored.Filled.ArrowBack verwenden:\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test
    fun keine_hartkodierten_wort_literale_in_texten() {
        val found = violations { line ->
            val match = textStart.find(line) ?: return@violations false
            val rest = line.substring(match.range.last + 1)
            // erstes String-Literal: bis zum nächsten nicht maskierten Anführungszeichen
            var end = 0
            while (end < rest.length && !(rest[end] == '"' && (end == 0 || rest[end - 1] != '\\'))) end++
            val literal = rest.substring(0, end)
            if (literal in allowedLiterals) return@violations false
            val stripped = literal.replace(interpolationBlock, "").replace(interpolationName, "")
            wordChars.containsMatchIn(stripped)
        }
        assertTrue(
            "Texte nach strings.xml auslagern (oder Ausdruck in lokale val ziehen):\n" + found.joinToString("\n"),
            found.isEmpty(),
        )
    }
}
