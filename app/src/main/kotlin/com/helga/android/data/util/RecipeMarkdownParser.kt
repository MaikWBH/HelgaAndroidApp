package com.helga.android.data.util

import com.helga.android.data.model.MealSlots

/** Nährwerte je Portion (kcal, Gramm). */
data class NutritionPerPortion(val kcal: Double, val protein: Double, val fat: Double, val carbs: Double)

enum class MarkdownWarningCode {
    INVALID_SERVINGS,
    INVALID_DURATION,
    UNKNOWN_VALUE,
    NUTRITION_INCOMPLETE,
    NO_INGREDIENTS,
    NO_STEPS,
    UNKNOWN_SECTION,
}

data class MarkdownWarning(val code: MarkdownWarningCode, val detail: String = "")

enum class MarkdownErrorCode { MISSING_TITLE, EMPTY_RECIPE, TOO_MANY_LINES }

data class MarkdownBlockError(val blockIndex: Int, val title: String, val code: MarkdownErrorCode)

enum class MarkdownFatal { EMPTY_INPUT, TOO_LONG, TOO_MANY_RECIPES }

data class ParsedRecipe(
    val blockIndex: Int,
    val name: String,
    val description: String = "",
    val recipeYield: String = "",
    val prepTime: String = "",
    val cookTime: String = "",
    val totalTime: String = "",
    val cuisine: String = "",
    val mealSlot: String = MealSlots.OTHER,
    val effort: String = "",
    val proteinType: String = "",
    val seasonFit: String = "",
    val tags: List<String> = emptyList(),
    val nutrition: NutritionPerPortion? = null,
    val ingredients: List<ParsedIngredientLine> = emptyList(),
    val steps: List<String> = emptyList(),
    val notes: String = "",
    val warnings: List<MarkdownWarning> = emptyList(),
)

data class MarkdownParseResult(
    val recipes: List<ParsedRecipe>,
    val errors: List<MarkdownBlockError>,
    val fatal: MarkdownFatal? = null,
)

/**
 * Liest KI-Antworten im Markdown-Rezeptformat (Format: `.claude/features/rezepte/markdown-format.md`).
 * Reine Funktionen, keine Android-Abhängigkeiten (Phase 7).
 */
object RecipeMarkdownParser {
    const val MAX_CHARS = 100_000
    const val MAX_RECIPES = 50
    const val MAX_LINES = 100
    private const val MAX_TITLE = 200

    private val fence = Regex("""^\s*```[A-Za-z]*\s*$""")
    private val separator = Regex("""^\s*(-{3,}|\*{3,}|_{3,})\s*$""")
    private val h1 = Regex("""^#\s+(.+)$""")
    private val anyHeading = Regex("""^#{1,6}\s+(.+)$""")
    private val headingSection = Regex("""^#{2,3}\s+(.+)$""")
    private val boldSection = Regex("""^\*\*(.+?):?\*\*:?\s*$""")

    private val sectionNames = setOf(
        "zutaten", "zutatenliste", "ingredients",
        "zubereitung", "schritte", "anleitung", "zubereitungsschritte", "instructions",
        "notizen", "notiz", "tipps", "tipp", "hinweise",
        "nährwerte", "naehrwerte",
        "infos", "informationen", "details", "eckdaten",
    )

    fun parse(text: String): MarkdownParseResult {
        val cleaned = preprocess(text)
        if (cleaned.isBlank()) return fatal(MarkdownFatal.EMPTY_INPUT)
        if (cleaned.length > MAX_CHARS) return fatal(MarkdownFatal.TOO_LONG)
        val blocks = splitBlocks(cleaned.lines())
        if (blocks.size > MAX_RECIPES) return fatal(MarkdownFatal.TOO_MANY_RECIPES)

        val recipes = mutableListOf<ParsedRecipe>()
        val errors = mutableListOf<MarkdownBlockError>()
        blocks.forEachIndexed { i, lines ->
            val index = i + 1
            val title = findTitle(lines)
            if (title == null) {
                errors += MarkdownBlockError(index, "", MarkdownErrorCode.MISSING_TITLE)
            } else {
                recipes += parseBlock(index, title, lines)
            }
        }
        return MarkdownParseResult(recipes, errors)
    }

    private fun fatal(code: MarkdownFatal) = MarkdownParseResult(emptyList(), emptyList(), code)

    private fun preprocess(text: String): String =
        text.removePrefix("﻿")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
            .filterNot { fence.matches(it) }
            .joinToString("\n")

    private fun splitBlocks(lines: List<String>): List<List<String>> {
        val blocks = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        var hasH1 = false
        fun close() {
            if (current.any { it.isNotBlank() }) blocks += current
            current = mutableListOf()
            hasH1 = false
        }
        for (line in lines) {
            if (separator.matches(line)) {
                close()
                continue
            }
            if (h1.matches(line)) {
                if (hasH1) close()
                hasH1 = true
            }
            current += line
        }
        close()
        return blocks
    }

    /** Zeilenindex und bereinigter Name der Titelzeile oder `null`. */
    private fun findTitle(lines: List<String>): Pair<Int, String>? {
        lines.forEachIndexed { i, line ->
            h1.matchEntire(line)?.let { return i to cleanHeading(it.groupValues[1]) }
        }
        lines.forEachIndexed { i, line ->
            val name = anyHeading.matchEntire(line)?.let { cleanHeading(it.groupValues[1]) }
            if (name != null && !isSectionName(name)) return i to name
        }
        return null
    }

    private fun cleanHeading(raw: String): String =
        raw.trim(' ', '\t', '#', '*').take(MAX_TITLE).trim()

    private fun isSectionName(name: String): Boolean =
        name.trim().trimEnd(':').trim().lowercase() in sectionNames

    private fun isSectionStart(line: String): Boolean {
        headingSection.matchEntire(line)?.let { return true }
        boldSection.matchEntire(line.trim())?.let { return isSectionName(it.groupValues[1]) }
        return false
    }

    private fun parseBlock(index: Int, title: Pair<Int, String>, lines: List<String>): ParsedRecipe {
        val (titleAt, name) = title
        val head = lines.drop(titleAt + 1).takeWhile { !isSectionStart(it) }
        val description = head
            .map { it.trim().removePrefix(">").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
        return ParsedRecipe(blockIndex = index, name = name, description = description)
    }
}
