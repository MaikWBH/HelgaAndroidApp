package com.helga.android.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Sync-DTOs gespiegelt zu `server/app/models.py`.
 * Snake_case-Mapping ist explizit – auf der Wire bleiben die Server-Feldnamen.
 */

@JsonClass(generateAdapter = true)
data class RecipeDto(
    val id: String,
    @Json(name = "updated_at") val updatedAt: Long,
    val deleted: Int = 0,
    val slug: String = "",
    val name: String = "",
    val description: String = "",
    @Json(name = "recipe_yield") val recipeYield: String = "",
    @Json(name = "prep_time") val prepTime: String = "",
    @Json(name = "cook_time") val cookTime: String = "",
    @Json(name = "total_time") val totalTime: String = "",
    @Json(name = "image_path") val imagePath: String = "",
    @Json(name = "source_url") val sourceUrl: String = "",
    val rating: Int = 0,
    @Json(name = "protein_type") val proteinType: String = "",
    val effort: String = "",
    val cuisine: String = "",
    @Json(name = "meal_type") val mealType: String = "",
    @Json(name = "season_fit") val seasonFit: String = "",
    @Json(name = "created_at") val createdAt: Long = 0,
)

@JsonClass(generateAdapter = true)
data class IngredientDto(
    val id: String,
    @Json(name = "updated_at") val updatedAt: Long,
    val deleted: Int = 0,
    @Json(name = "recipe_id") val recipeId: String,
    val position: Int = 0,
    val quantity: Double = 0.0,
    val unit: String = "",
    val food: String = "",
    val note: String = "",
)

@JsonClass(generateAdapter = true)
data class InstructionDto(
    val id: String,
    @Json(name = "updated_at") val updatedAt: Long,
    val deleted: Int = 0,
    @Json(name = "recipe_id") val recipeId: String,
    val position: Int = 0,
    val text: String = "",
)

@JsonClass(generateAdapter = true)
data class TagDto(
    val id: String,
    @Json(name = "updated_at") val updatedAt: Long,
    val deleted: Int = 0,
    @Json(name = "recipe_id") val recipeId: String,
    val name: String = "",
)

@JsonClass(generateAdapter = true)
data class CategoryDto(
    val id: String,
    @Json(name = "updated_at") val updatedAt: Long,
    val deleted: Int = 0,
    @Json(name = "recipe_id") val recipeId: String,
    val name: String = "",
)

/**
 * Pull-Antwort. Andere Tabellen (foods, units, stores, …) sind in späteren Phasen
 * relevant; bis dahin werden ihre Listen einfach ignoriert (Felder defaulten auf
 * `emptyList()`).
 */
@JsonClass(generateAdapter = true)
data class SyncPullResponse(
    @Json(name = "server_ts") val serverTs: Long,
    val recipes: List<RecipeDto> = emptyList(),
    @Json(name = "recipe_ingredients") val recipeIngredients: List<IngredientDto> = emptyList(),
    @Json(name = "recipe_instructions") val recipeInstructions: List<InstructionDto> = emptyList(),
    @Json(name = "recipe_tags") val recipeTags: List<TagDto> = emptyList(),
    @Json(name = "recipe_categories") val recipeCategories: List<CategoryDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class SyncPushRequest(
    @Json(name = "client_ts") val clientTs: Long,
    val recipes: List<RecipeDto> = emptyList(),
    @Json(name = "recipe_ingredients") val recipeIngredients: List<IngredientDto> = emptyList(),
    @Json(name = "recipe_instructions") val recipeInstructions: List<InstructionDto> = emptyList(),
    @Json(name = "recipe_tags") val recipeTags: List<TagDto> = emptyList(),
    @Json(name = "recipe_categories") val recipeCategories: List<CategoryDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class HealthResponse(
    val status: String = "",
)

@JsonClass(generateAdapter = true)
data class ImageUploadResponse(
    val uuid: String = "",
    val filename: String = "",
)
