package com.helga.android.ui.ai

import org.json.JSONObject

data class ParsedAiRecipe(
    val name: String = "",
    val description: String = "",
    val recipeYield: String = "",
    val prepTime: String = "",
    val cookTime: String = "",
    val totalTime: String = "",
    val cuisine: String = "",
    val proteinType: String = "",
    val effort: String = "",
    val mealType: String = "",
    val seasonFit: String = "",
    val ingredients: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
)

object RecipeJsonLdParser {
    fun parse(html: String): ParsedAiRecipe? {
        val jsonStr = Regex(
            """<script[^>]*type=["']application/ld\+json["'][^>]*>([\s\S]*?)</script>""",
            RegexOption.IGNORE_CASE,
        ).find(html)?.groupValues?.get(1)?.trim() ?: return null

        return try {
            val obj = JSONObject(jsonStr)

            val ingredients = obj.optJSONArray("recipeIngredient")?.let { arr ->
                (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
            } ?: emptyList()

            val instructions = obj.optJSONArray("recipeInstructions")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    when (val item = arr.opt(i)) {
                        is JSONObject -> item.optString("text").takeIf { it.isNotBlank() }
                        is String -> item.takeIf { it.isNotBlank() }
                        else -> null
                    }
                }
            } ?: emptyList()

            val tags = obj.optString("keywords", "")
                .split(",").map { it.trim() }.filter { it.isNotBlank() }

            ParsedAiRecipe(
                name = obj.optString("name", ""),
                description = obj.optString("description", ""),
                recipeYield = obj.optString("recipeYield", ""),
                prepTime = obj.optString("prepTime", ""),
                cookTime = obj.optString("cookTime", ""),
                totalTime = obj.optString("totalTime", ""),
                cuisine = obj.optString("cuisine", obj.optString("rocks_cuisine", "")),
                proteinType = obj.optString("rocks_protein_type", ""),
                effort = obj.optString("rocks_effort", ""),
                mealType = obj.optString("rocks_meal_type", ""),
                seasonFit = obj.optString("rocks_season_fit", ""),
                ingredients = ingredients,
                instructions = instructions,
                tags = tags,
            )
        } catch (e: Exception) {
            null
        }
    }
}
