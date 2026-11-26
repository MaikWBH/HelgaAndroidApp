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

    private val keyLine = Regex(
        """^\s*(?:[-*+]\s+)?\**\s*([A-Za-zÄÖÜäöüß][A-Za-zÄÖÜäöüß .]*?)\s*\**\s*:\s*\**\s*(.+?)\s*$""",
    )
    private val number = """(\d+(?:[.,]\d+)?)"""
    private val kcalRegex = Regex("""$number\s*kcal""", RegexOption.IGNORE_CASE)
    private val proteinRegex = Regex("""$number\s*g\s*(?:eiweiß|eiweiss|protein)""", RegexOption.IGNORE_CASE)
    private val fatRegex = Regex("""$number\s*g\s*fett""", RegexOption.IGNORE_CASE)
    private val carbsRegex = Regex("""$number\s*g\s*(?:kohlenhydrate|kh)""", RegexOption.IGNORE_CASE)
    private const val MAX_TAGS = 20

    private val cuisines = setOf(
        "deutsch", "italienisch", "asiatisch", "mexikanisch", "französisch", "mediterran",
        "orientalisch", "amerikanisch", "indisch", "sonstige",
    )
    private val mealSlots = mapOf(
        "frühstück" to MealSlots.BREAKFAST, "fruehstueck" to MealSlots.BREAKFAST, "breakfast" to MealSlots.BREAKFAST,
        "mittag" to MealSlots.LUNCH, "mittagessen" to MealSlots.LUNCH, "lunch" to MealSlots.LUNCH,
        "abend" to MealSlots.DINNER, "abendessen" to MealSlots.DINNER, "abendbrot" to MealSlots.DINNER,
        "dinner" to MealSlots.DINNER,
        "snack" to MealSlots.SNACK, "zwischenmahlzeit" to MealSlots.SNACK,
    )
    private val efforts = mapOf(
        "schnell" to "schnell", "einfach" to "schnell",
        "mittel" to "mittel", "mittelschwer" to "mittel",
        "aufwendig" to "aufwendig", "aufwändig" to "aufwendig", "schwer" to "aufwendig",
    )
    private val proteinTypes = setOf("fleisch", "fisch", "vegetarisch", "vegan")
    private val seasons = mapOf(
        "winter" to "winter", "sommer" to "sommer",
        "ganzjährig" to "ganzjährig", "ganzjaehrig" to "ganzjährig", "ganzes jahr" to "ganzjährig",
    )
    private val servingKeys = setOf("portionen", "portion", "personen")
    private val prepKeys = setOf("vorbereitung", "vorbereitungszeit", "zubereitungszeit")
    private val cookKeys = setOf("kochzeit", "garzeit", "backzeit")
    private val totalKeys = setOf("gesamtzeit", "gesamt", "dauer")
    private val tagKeys = setOf("tags", "schlagwörter", "stichwörter")
    private val nutritionKeys = setOf("nährwerte", "naehrwerte", "nährwerte pro portion", "naehrwerte pro portion")
    private val noteKeys = setOf("notizen", "notiz", "tipp", "tipps")

    private val listMarker = Regex("""^\s*(?:[-*+•]|\d+[.)])\s+""")
    private val checkbox = Regex("""^\[[ xX]\]\s*""")
    private val stepMarker = Regex("""(?i)^\s*(?:\d+[.)]|schritt\s+\d+\s*:?|[-*+•])\s+(.*)$""")

    private enum class Section { HEAD, INGREDIENTS, STEPS, NOTES, NUTRITION, INFO, IGNORED }

    private class Header(val name: String, val raw: String, val level: Int)

    private fun sectionOf(name: String): Section? = when (name) {
        "zutaten", "zutatenliste", "ingredients" -> Section.INGREDIENTS
        "zubereitung", "schritte", "anleitung", "zubereitungsschritte", "instructions" -> Section.STEPS
        "notizen", "notiz", "tipps", "tipp", "hinweise" -> Section.NOTES
        "nährwerte", "naehrwerte" -> Section.NUTRITION
        "infos", "informationen", "details", "eckdaten" -> Section.INFO
        else -> null
    }

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
                val recipe = parseBlock(index, title, lines)
                val code = blockError(recipe)
                if (code == null) recipes += recipe else errors += MarkdownBlockError(index, recipe.name, code)
            }
        }
        return MarkdownParseResult(recipes, errors)
    }

    private fun fatal(code: MarkdownFatal) = MarkdownParseResult(emptyList(), emptyList(), code)

    private fun preprocess(text: String): String =
        text.removePrefix("\uFEFF")
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

    private fun normalizeName(raw: String): String = raw.trim(' ', '\t', ':', '*', '#').lowercase()

    private fun isSectionName(name: String): Boolean = sectionOf(normalizeName(name)) != null

    private fun headerOf(line: String): Header? {
        headingSection.matchEntire(line)?.let {
            val level = line.trimStart().takeWhile { c -> c == '#' }.length
            return Header(normalizeName(it.groupValues[1]), it.groupValues[1].trim(' ', ':', '*', '#'), level)
        }
        boldSection.matchEntire(line.trim())?.let {
            return Header(normalizeName(it.groupValues[1]), it.groupValues[1].trim(' ', ':', '*', '#'), 0)
        }
        return null
    }

    private fun blockError(recipe: ParsedRecipe): MarkdownErrorCode? = when {
        recipe.ingredients.size > MAX_LINES || recipe.steps.size > MAX_LINES -> MarkdownErrorCode.TOO_MANY_LINES
        recipe.ingredients.isEmpty() && recipe.steps.isEmpty() -> MarkdownErrorCode.EMPTY_RECIPE
        else -> null
    }

    private fun parseBlock(index: Int, title: Pair<Int, String>, lines: List<String>): ParsedRecipe {
        val (titleAt, name) = title
        var recipe = ParsedRecipe(blockIndex = index, name = name)
        val description = mutableListOf<String>()
        val ingredientLines = mutableListOf<String>()
        val stepLines = mutableListOf<String>()
        val noteLines = mutableListOf<String>()
        val nutritionLines = mutableListOf<String>()
        var section = Section.HEAD

        for (line in lines.drop(titleAt + 1)) {
            val header = headerOf(line)
            if (header != null) {
                val known = sectionOf(header.name)
                if (known != null) {
                    section = known
                    continue
                }
                if (header.level == 2) {
                    recipe = recipe.warn(MarkdownWarningCode.UNKNOWN_SECTION, header.raw)
                    section = Section.IGNORED
                    continue
                }
                // `###` oder fette Zeile mit unbekanntem Namen: Gruppenüberschrift, im Kopf evtl. Schlüsselzeile
                val keyApplied = if (section == Section.HEAD || section == Section.INFO) applyKeyLine(recipe, line) else null
                if (keyApplied != null) recipe = keyApplied
                continue
            }
            when (section) {
                Section.HEAD, Section.INFO -> {
                    val applied = applyKeyLine(recipe, line)
                    if (applied != null) {
                        recipe = applied
                    } else if (section == Section.HEAD) {
                        val text = line.trim().removePrefix(">").trim()
                        if (text.isNotEmpty()) description += text
                    }
                }
                Section.INGREDIENTS -> ingredientLines += line
                Section.STEPS -> stepLines += line
                Section.NOTES -> noteLines += line
                Section.NUTRITION -> nutritionLines += line
                Section.IGNORED -> Unit
            }
        }

        val ingredients = ingredientLines.mapNotNull { parseIngredient(it) }
        val steps = parseSteps(stepLines)
        var result = recipe.copy(
            description = description.joinToString("\n"),
            ingredients = ingredients,
            steps = steps,
        )
        val joinedNutrition = nutritionLines
            .map { it.replace(listMarker, "").trim() }
            .filter { it.isNotEmpty() }
            .joinToString(", ")
        if (joinedNutrition.isNotEmpty()) result = applyNutrition(result, joinedNutrition)
        val sectionNotes = noteLines.joinToString("\n") { it.trimEnd() }.trim()
        if (sectionNotes.isNotEmpty()) {
            result = result.copy(notes = listOf(result.notes, sectionNotes).filter { it.isNotEmpty() }.joinToString("\n"))
        }
        if (ingredients.isEmpty() && steps.isNotEmpty()) result = result.warn(MarkdownWarningCode.NO_INGREDIENTS)
        if (steps.isEmpty() && ingredients.isNotEmpty()) result = result.warn(MarkdownWarningCode.NO_STEPS)
        return result
    }

    private fun parseIngredient(raw: String): ParsedIngredientLine? {
        val line = raw.replace(listMarker, "").trim().replace(checkbox, "").trim()
        if (line.isEmpty() || IngredientLineParser.isHeaderLine(line)) return null
        return IngredientLineParser.parse(line).let { it.copy(food = it.food.ifBlank { line }) }
    }

    private fun parseSteps(lines: List<String>): List<String> {
        val steps = mutableListOf<String>()
        var afterBlank = false
        for (raw in lines) {
            if (raw.isBlank()) {
                afterBlank = true
                continue
            }
            val marker = stepMarker.matchEntire(raw)
            if (marker != null) {
                val text = marker.groupValues[1].trim()
                if (text.isNotEmpty()) steps += text
            } else if (steps.isNotEmpty() && !afterBlank) {
                steps[steps.lastIndex] = steps.last() + " " + raw.trim()
            } else {
                steps += raw.trim()
            }
            afterBlank = false
        }
        return steps
    }

    /** Wendet eine Schlüsselzeile an; `null`, wenn die Zeile keine Schlüsselzeile mit bekanntem Schlüssel ist. */
    private fun applyKeyLine(recipe: ParsedRecipe, line: String): ParsedRecipe? {
        val match = keyLine.matchEntire(line) ?: return null
        val key = match.groupValues[1].trim().lowercase()
        val value = match.groupValues[2].trim().trim('*').trim()
        if (value.isEmpty()) return null
        return applyKey(recipe, key, value)
    }

    private fun applyKey(recipe: ParsedRecipe, key: String, value: String): ParsedRecipe? {
        val lower = value.lowercase()
        return when (key) {
            in servingKeys ->
                if (value.any { it.isDigit() }) {
                    recipe.copy(recipeYield = value)
                } else {
                    recipe.warn(MarkdownWarningCode.INVALID_SERVINGS, value)
                }
            in prepKeys -> withDuration(recipe, value) { r, iso -> r.copy(prepTime = iso) }
            in cookKeys -> withDuration(recipe, value) { r, iso -> r.copy(cookTime = iso) }
            in totalKeys -> withDuration(recipe, value) { r, iso -> r.copy(totalTime = iso) }
            "küche", "kueche" -> recipe.copy(cuisine = if (lower in cuisines) lower else "sonstige")
            "mahlzeit", "slot" -> mealSlots[lower]?.let { recipe.copy(mealSlot = it) }
                ?: recipe.copy(mealSlot = MealSlots.OTHER).warn(MarkdownWarningCode.UNKNOWN_VALUE, value)
            "aufwand", "schwierigkeit" -> efforts[lower]?.let { recipe.copy(effort = it) }
                ?: recipe.copy(effort = "").warn(MarkdownWarningCode.UNKNOWN_VALUE, value)
            "eiweiß", "eiweiss", "protein", "proteinquelle" ->
                if (lower in proteinTypes) {
                    recipe.copy(proteinType = lower)
                } else {
                    recipe.copy(proteinType = "").warn(MarkdownWarningCode.UNKNOWN_VALUE, value)
                }
            "saison" -> seasons[lower]?.let { recipe.copy(seasonFit = it) }
                ?: recipe.copy(seasonFit = "").warn(MarkdownWarningCode.UNKNOWN_VALUE, value)
            in tagKeys -> recipe.copy(tags = parseTags(value))
            in nutritionKeys -> applyNutrition(recipe, value)
            in noteKeys -> recipe.copy(notes = listOf(recipe.notes, value).filter { it.isNotEmpty() }.joinToString("\n"))
            else -> null
        }
    }

    private fun ParsedRecipe.warn(code: MarkdownWarningCode, detail: String = ""): ParsedRecipe =
        copy(warnings = warnings + MarkdownWarning(code, detail))

    private fun withDuration(recipe: ParsedRecipe, value: String, set: (ParsedRecipe, String) -> ParsedRecipe): ParsedRecipe {
        val minutes = RecipeDuration.parseMinutes(value)
        return if (minutes == null) {
            recipe.warn(MarkdownWarningCode.INVALID_DURATION, value)
        } else {
            set(recipe, RecipeDuration.toIso(minutes))
        }
    }

    private fun parseTags(value: String): List<String> =
        value.split(',', ';')
            .map { it.trim().trimStart('#').trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .take(MAX_TAGS)

    private fun applyNutrition(recipe: ParsedRecipe, value: String): ParsedRecipe {
        fun find(regex: Regex): Double? =
            regex.find(value)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        val kcal = find(kcalRegex)
        val protein = find(proteinRegex)
        val fat = find(fatRegex)
        val carbs = find(carbsRegex)
        return if (kcal != null && protein != null && fat != null && carbs != null) {
            recipe.copy(nutrition = NutritionPerPortion(kcal, protein, fat, carbs))
        } else {
            recipe.copy(nutrition = null).warn(MarkdownWarningCode.NUTRITION_INCOMPLETE)
        }
    }
}
