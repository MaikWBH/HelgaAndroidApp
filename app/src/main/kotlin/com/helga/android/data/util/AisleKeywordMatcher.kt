package com.helga.android.data.util

/**
 * Ordnet einen Artikelnamen offline einer Gang-Kategorie zu (Phase 3b). Der Algorithmus ist verbindlich in
 * `.claude/phasen/phase-3-daten/aisle-keywords.md` beschrieben (Schritte 1–6) und dort mit Testfällen belegt.
 */
object AisleKeywordMatcher {

    private val suffixes = listOf("en", "er", "n", "e", "s")

    fun normalize(raw: String): String = AisleKeywords.normalize(raw)

    private data class Candidate(val length: Int, val tokenIndex: Int, val position: Int, val entryIndex: Int)

    private val candidateOrder: Comparator<Candidate> =
        compareBy<Candidate> { it.length }.thenBy { it.tokenIndex }.thenBy { it.position }.thenByDescending { it.entryIndex }

    /** Liefert den Kategorie-Schlüssel oder `""`, wenn kein Stichwort trifft. */
    fun categoryFor(name: String): String {
        val text = normalize(name)
        if (text.isEmpty()) return ""
        val tokens = text.split(" ")
        val forms = tokens.map { formsOf(it) }
        val entries = AisleKeywords.entries

        // 3. Vorrang-Stichwörter in Dateireihenfolge
        for (entry in entries) {
            if (!entry.priority) continue
            val hit = tokens.any { it == entry.word || (entry.word.length >= 4 && it.startsWith(entry.word)) }
            if (hit) return entry.category
        }

        // 4. Stufe 1: genau / Endung / Phrase
        var best: Candidate? = null
        var bestCategory = ""
        val padded = " $text "
        entries.forEachIndexed { index, entry ->
            if (entry.priority) return@forEachIndexed
            val word = entry.word
            val candidate = if (' ' in word) {
                val at = padded.indexOf(" $word ")
                if (at < 0) null
                else Candidate(word.length, padded.substring(0, at + 1 + word.length).count { it == ' ' } - 1, 0, index)
            } else {
                var tokenIndex = -1
                for (i in tokens.indices.reversed()) {
                    if (forms[i].any { it == word || (word.length >= 4 && it.endsWith(word)) }) {
                        tokenIndex = i
                        break
                    }
                }
                if (tokenIndex < 0) null else Candidate(word.length, tokenIndex, 0, index)
            }
            if (candidate != null && (best == null || candidateOrder.compare(candidate, best!!) > 0)) {
                best = candidate
                bestCategory = entry.category
            }
        }
        if (best != null) return bestCategory

        // 5. Stufe 2: Einzelwörter ab 5 Zeichen, die irgendwo in einem Token vorkommen
        entries.forEachIndexed { index, entry ->
            val word = entry.word
            if (entry.priority || ' ' in word || word.length < 5) return@forEachIndexed
            tokens.forEachIndexed { tokenIndex, token ->
                val position = token.indexOf(word)
                if (position < 0) return@forEachIndexed
                val candidate = Candidate(word.length, tokenIndex, position, index)
                if (best == null || candidateOrder.compare(candidate, best!!) > 0) {
                    best = candidate
                    bestCategory = entry.category
                }
            }
        }
        return if (best != null) bestCategory else ""
    }

    /** Das Token selbst plus je Suffix das Token ohne Suffix (Rest mindestens 2 Zeichen). */
    private fun formsOf(token: String): List<String> =
        listOf(token) + suffixes.mapNotNull { suffix ->
            if (token.endsWith(suffix) && token.length - suffix.length >= 2) token.dropLast(suffix.length) else null
        }
}
