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
    @Json(name = "meal_slot") val mealSlot: String = "other",
    @Json(name = "season_fit") val seasonFit: String = "",
    @Json(name = "nutrition_kcal") val nutritionKcal: Double = 0.0,
    @Json(name = "nutrition_protein") val nutritionProtein: Double = 0.0,
    @Json(name = "nutrition_fat") val nutritionFat: Double = 0.0,
    @Json(name = "nutrition_carbs") val nutritionCarbs: Double = 0.0,
    @Json(name = "nutrition_nutri_score") val nutritionNutriScore: String = "",
    @Json(name = "nutrition_source") val nutritionSource: String = "",
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
    @Json(name = "off_barcode") val offBarcode: String = "",
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

@JsonClass(generateAdapter = true)
data class ShoppingListDto(
    val id: String,
    @Json(name = "updated_at") val updatedAt: Long,
    val deleted: Int = 0,
    val name: String = "",
    @Json(name = "is_active") val isActive: Int = 0,
    @Json(name = "is_default_weekplan") val isDefaultWeekplan: Int = 0,
    @Json(name = "is_default_recipe") val isDefaultRecipe: Int = 0,
)

@JsonClass(generateAdapter = true)
data class ShoppingItemDto(
    val id: String,
    @Json(name = "updated_at") val updatedAt: Long,
    val deleted: Int = 0,
    @Json(name = "list_id") val listId: String,
    val name: String = "",
    val quantity: Double = 1.0,
    val unit: String = "",
    val aisle: String = "",
    val source: String = "manual",
    @Json(name = "is_checked") val isChecked: Int = 0,
    @Json(name = "sort_order") val sortOrder: Int = 0,
    val origins: String = "[]",
    @Json(name = "off_barcode") val offBarcode: String = "",
    @Json(name = "off_product_id") val offProductId: String = "",
    @Json(name = "price_estimate") val priceEstimate: Double = 0.0,
    @Json(name = "price_last_checked") val priceLastChecked: Long = 0L,
)

@JsonClass(generateAdapter = true)
data class StoreDto(
    val id: String,
    @Json(name = "updated_at") val updatedAt: Long,
    val deleted: Int = 0,
    val name: String = "",
    @Json(name = "is_active") val isActive: Int = 0,
)

@JsonClass(generateAdapter = true)
data class StoreAisleDto(
    val id: String,
    @Json(name = "updated_at") val updatedAt: Long,
    val deleted: Int = 0,
    @Json(name = "store_id") val storeId: String,
    @Json(name = "aisle_name") val aisleName: String = "",
    @Json(name = "sort_order") val sortOrder: Int = 0,
)

@JsonClass(generateAdapter = true)
data class AisleProductDto(
    val id: String,
    @Json(name = "updated_at") val updatedAt: Long,
    val deleted: Int = 0,
    @Json(name = "aisle_name") val aisleName: String = "",
    @Json(name = "product_name") val productName: String = "",
    @Json(name = "store_id") val storeId: String = "",
)

@JsonClass(generateAdapter = true)
data class ShoppingListStapleDto(
    val id: String,
    @Json(name = "updated_at") val updatedAt: Long,
    val deleted: Int = 0,
    @Json(name = "list_id") val listId: String,
    val name: String = "",
    val quantity: Double = 1.0,
    @Json(name = "sort_order") val sortOrder: Int = 0,
)

@JsonClass(generateAdapter = true)
data class QuickEmojiDto(
    val id: String,
    @Json(name = "updated_at") val updatedAt: Long,
    val deleted: Int = 0,
    val emoji: String = "",
    val food: String = "",
    val quantity: Double = 1.0,
    val unit: String = "",
    @Json(name = "sort_order") val sortOrder: Int = 0,
)

@JsonClass(generateAdapter = true)
data class WeekplanDayDto(
    val id: String,
    @Json(name = "updated_at") val updatedAt: Long,
    val deleted: Int = 0,
    @Json(name = "plan_date") val planDate: String = "",
    val note: String = "",
    @Json(name = "is_quick_day") val isQuickDay: Int = 0,
    @Json(name = "is_guest_day") val isGuestDay: Int = 0,
    @Json(name = "is_skipped") val isSkipped: Int = 0,
)

@JsonClass(generateAdapter = true)
data class WeekplanRecipeDto(
    val id: String,
    @Json(name = "updated_at") val updatedAt: Long,
    val deleted: Int = 0,
    @Json(name = "weekplan_day_id") val weekplanDayId: String,
    @Json(name = "recipe_id") val recipeId: String,
    val position: Int = 0,
)

@JsonClass(generateAdapter = true)
data class WeekplanExtraDto(
    val id: String,
    @Json(name = "updated_at") val updatedAt: Long,
    val deleted: Int = 0,
    @Json(name = "weekplan_day_id") val weekplanDayId: String,
    @Json(name = "item_text") val itemText: String = "",
    val position: Int = 0,
)

@JsonClass(generateAdapter = true)
data class WeekplanSettingsDto(
    val id: String = "global",
    @Json(name = "updated_at") val updatedAt: Long,
    val deleted: Int = 0,
    @Json(name = "plan_days") val planDays: Int = 7,
    @Json(name = "shopping_day") val shoppingDay: Int = 0,
)

@JsonClass(generateAdapter = true)
data class WeekplanConstraintsDto(
    val id: String = "global",
    @Json(name = "updated_at") val updatedAt: Long,
    val deleted: Int = 0,
    @Json(name = "max_meat_per_week") val maxMeatPerWeek: Int = 3,
    @Json(name = "max_fish_per_week") val maxFishPerWeek: Int = 2,
    @Json(name = "min_vegetarian_per_week") val minVegetarianPerWeek: Int = 2,
    @Json(name = "max_repeat_days") val maxRepeatDays: Int = 14,
    @Json(name = "max_kcal_per_portion") val maxKcalPerPortion: Int = 700,
    @Json(name = "min_nutri_score") val minNutriScore: String = "c",
    @Json(name = "prefer_organic") val preferOrganic: Int = 0,
    @Json(name = "exclude_allergens") val excludeAllergens: String = "[]",
)

@JsonClass(generateAdapter = true)
data class RecipeHistoryDto(
    val id: String,
    @Json(name = "recipe_id") val recipeId: String,
    @Json(name = "planned_date") val plannedDate: String = "",
    val cooked: Int = 0,
    @Json(name = "updated_at") val updatedAt: Long = 0,
    val deleted: Int = 0,
)

@JsonClass(generateAdapter = true)
data class RecipeFeedbackDto(
    val id: String,
    @Json(name = "recipe_id") val recipeId: String,
    @Json(name = "planned_date") val plannedDate: String = "",
    val liked: Int = 0,
    @Json(name = "updated_at") val updatedAt: Long = 0,
    val deleted: Int = 0,
)

@JsonClass(generateAdapter = true)
data class ReceiptDto(
    val id: String,
    @Json(name = "store_id") val storeId: String = "",
    @Json(name = "store_name") val storeName: String = "",
    @Json(name = "shopping_list_id") val shoppingListId: String = "",
    @Json(name = "purchase_date") val purchaseDate: Long = 0,
    @Json(name = "total_amount") val totalAmount: Double = 0.0,
    val currency: String = "EUR",
    @Json(name = "image_path") val imagePath: String = "",
    @Json(name = "raw_ocr_text") val rawOcrText: String = "",
    val status: String = "scanned",
    @Json(name = "updated_at") val updatedAt: Long,
    val deleted: Int = 0,
)

@JsonClass(generateAdapter = true)
data class ReceiptItemDto(
    val id: String,
    @Json(name = "receipt_id") val receiptId: String,
    val position: Int = 0,
    @Json(name = "raw_text") val rawText: String = "",
    val name: String = "",
    val quantity: Double = 1.0,
    @Json(name = "unit_price") val unitPrice: Double = 0.0,
    @Json(name = "total_price") val totalPrice: Double = 0.0,
    @Json(name = "matched_shopping_item_id") val matchedShoppingItemId: String = "",
    @Json(name = "match_status") val matchStatus: String = "",
    @Json(name = "updated_at") val updatedAt: Long,
    val deleted: Int = 0,
)


@JsonClass(generateAdapter = true)
data class MonthlyBudgetDto(
    val id: String = "global",
    @Json(name = "updated_at") val updatedAt: Long,
    val deleted: Int = 0,
    val amount: Double = 0.0,
    @Json(name = "warn_threshold") val warnThreshold: Double = 0.8,
)

@JsonClass(generateAdapter = true)
data class SuggestionsResponse(val suggestions: List<String> = emptyList())

@JsonClass(generateAdapter = true)
data class OffLookupBarcodeRequest(
    val barcode: String,
)

@JsonClass(generateAdapter = true)
data class OffLookupBarcodeResponse(
    val id: String = "",
    @Json(name = "updated_at") val updatedAt: Long = 0L,
    val deleted: Int = 0,
    val barcode: String = "",
    val name: String = "",
    val brand: String = "",
    val categories: String = "[]",
    @Json(name = "kcal_per_unit") val kcalPerUnit: Double = 0.0,
    val proteins: Double = 0.0,
    val fats: Double = 0.0,
    val carbs: Double = 0.0,
    @Json(name = "nutri_score") val nutriScore: String = "",
    val nova: Int = 0,
    @Json(name = "eco_score") val ecoScore: String = "",
    val allergenes: String = "[]",
    val additives: String = "[]",
    @Json(name = "is_organic") val isOrganic: Int = 0,
    val vegan: Int = 0,
    val vegetarian: Int = 0,
    @Json(name = "image_path") val imagePath: String = "",
    @Json(name = "is_favorite") val isFavorite: Int = 0,
    @Json(name = "package_grams") val packageGrams: Double = 0.0,
    @Json(name = "package_grams_manual") val packageGramsManual: Int = 0,
)

// ── Receipt Reconciliation (Phase 4) ─────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class ReconcileShoppingItemDto(
    val id: String,
    val name: String = "",
    val quantity: Double = 1.0,
    val unit: String = "",
)

@JsonClass(generateAdapter = true)
data class ReconcileReceiptItemDto(
    val id: String,
    val name: String = "",
    @Json(name = "raw_text") val rawText: String = "",
    @Json(name = "total_price") val totalPrice: Double = 0.0,
)

@JsonClass(generateAdapter = true)
data class ReceiptReconcileRequest(
    @Json(name = "checked_items") val checkedItems: List<ReconcileShoppingItemDto> = emptyList(),
    @Json(name = "receipt_items") val receiptItems: List<ReconcileReceiptItemDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class ReconcileMatchDto(
    @Json(name = "shopping_item_id") val shoppingItemId: String = "",
    @Json(name = "receipt_item_id") val receiptItemId: String = "",
)

@JsonClass(generateAdapter = true)
data class ReceiptReconcileResponse(
    val matches: List<ReconcileMatchDto> = emptyList(),
    val unexpected: List<String> = emptyList(),
    val missing: List<String> = emptyList(),
)

// ── Receipt Parsing (KI-Vision) ──────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class ReceiptParseRequest(
    @Json(name = "image_base64") val imageBase64: String,
    @Json(name = "mime_type") val mimeType: String = "image/jpeg",
)

@JsonClass(generateAdapter = true)
data class ReceiptParseItemDto(
    val name: String = "",
    val quantity: Double = 1.0,
    @Json(name = "unit_price") val unitPrice: Double = 0.0,
    @Json(name = "total_price") val totalPrice: Double = 0.0,
    val confidence: Double = 1.0,
)

@JsonClass(generateAdapter = true)
data class ReceiptParseResponse(
    @Json(name = "store_name") val storeName: String = "",
    @Json(name = "purchase_date") val purchaseDate: Long = 0L,
    @Json(name = "total_amount") val totalAmount: Double = 0.0,
    val confidence: Double = 1.0,
    val items: List<ReceiptParseItemDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class SyncPullResponse(
    @Json(name = "server_ts") val serverTs: Long,
    val recipes: List<RecipeDto> = emptyList(),
    @Json(name = "recipe_ingredients") val recipeIngredients: List<IngredientDto> = emptyList(),
    @Json(name = "recipe_instructions") val recipeInstructions: List<InstructionDto> = emptyList(),
    @Json(name = "recipe_tags") val recipeTags: List<TagDto> = emptyList(),
    @Json(name = "recipe_categories") val recipeCategories: List<CategoryDto> = emptyList(),
    @Json(name = "shopping_lists") val shoppingLists: List<ShoppingListDto> = emptyList(),
    @Json(name = "shopping_items") val shoppingItems: List<ShoppingItemDto> = emptyList(),
    val stores: List<StoreDto> = emptyList(),
    @Json(name = "store_aisles") val storeAisles: List<StoreAisleDto> = emptyList(),
    @Json(name = "aisle_products") val aisleProducts: List<AisleProductDto> = emptyList(),
    @Json(name = "shopping_list_staples") val shoppingListStaples: List<ShoppingListStapleDto> = emptyList(),
    @Json(name = "quick_emojis") val quickEmojis: List<QuickEmojiDto> = emptyList(),
    @Json(name = "weekplan_days") val weekplanDays: List<WeekplanDayDto> = emptyList(),
    @Json(name = "weekplan_recipes") val weekplanRecipes: List<WeekplanRecipeDto> = emptyList(),
    @Json(name = "weekplan_extras") val weekplanExtras: List<WeekplanExtraDto> = emptyList(),
    @Json(name = "weekplan_settings") val weekplanSettings: List<WeekplanSettingsDto> = emptyList(),
    @Json(name = "weekplan_constraints") val weekplanConstraints: List<WeekplanConstraintsDto> = emptyList(),
    @Json(name = "recipe_history") val recipeHistory: List<RecipeHistoryDto> = emptyList(),
    @Json(name = "recipe_feedback") val recipeFeedback: List<RecipeFeedbackDto> = emptyList(),
    val receipts: List<ReceiptDto> = emptyList(),
    @Json(name = "receipt_items") val receiptItems: List<ReceiptItemDto> = emptyList(),
    @Json(name = "monthly_budgets") val monthlyBudgets: List<MonthlyBudgetDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class SyncPushRequest(
    @Json(name = "client_ts") val clientTs: Long,
    val recipes: List<RecipeDto> = emptyList(),
    @Json(name = "recipe_ingredients") val recipeIngredients: List<IngredientDto> = emptyList(),
    @Json(name = "recipe_instructions") val recipeInstructions: List<InstructionDto> = emptyList(),
    @Json(name = "recipe_tags") val recipeTags: List<TagDto> = emptyList(),
    @Json(name = "recipe_categories") val recipeCategories: List<CategoryDto> = emptyList(),
    @Json(name = "shopping_lists") val shoppingLists: List<ShoppingListDto> = emptyList(),
    @Json(name = "shopping_items") val shoppingItems: List<ShoppingItemDto> = emptyList(),
    val stores: List<StoreDto> = emptyList(),
    @Json(name = "store_aisles") val storeAisles: List<StoreAisleDto> = emptyList(),
    @Json(name = "aisle_products") val aisleProducts: List<AisleProductDto> = emptyList(),
    @Json(name = "shopping_list_staples") val shoppingListStaples: List<ShoppingListStapleDto> = emptyList(),
    @Json(name = "quick_emojis") val quickEmojis: List<QuickEmojiDto> = emptyList(),
    @Json(name = "weekplan_days") val weekplanDays: List<WeekplanDayDto> = emptyList(),
    @Json(name = "weekplan_recipes") val weekplanRecipes: List<WeekplanRecipeDto> = emptyList(),
    @Json(name = "weekplan_extras") val weekplanExtras: List<WeekplanExtraDto> = emptyList(),
    @Json(name = "weekplan_settings") val weekplanSettings: List<WeekplanSettingsDto> = emptyList(),
    @Json(name = "weekplan_constraints") val weekplanConstraints: List<WeekplanConstraintsDto> = emptyList(),
    @Json(name = "recipe_history") val recipeHistory: List<RecipeHistoryDto> = emptyList(),
    @Json(name = "recipe_feedback") val recipeFeedback: List<RecipeFeedbackDto> = emptyList(),
    val receipts: List<ReceiptDto> = emptyList(),
    @Json(name = "receipt_items") val receiptItems: List<ReceiptItemDto> = emptyList(),
    @Json(name = "monthly_budgets") val monthlyBudgets: List<MonthlyBudgetDto> = emptyList(),
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

@JsonClass(generateAdapter = true)
data class UrlImportRequest(val url: String)

@JsonClass(generateAdapter = true)
data class AiGenerateRequest(
    val prompt: String,
    @Json(name = "custom_instructions") val customInstructions: String = "",
    @Json(name = "available_tags") val availableTags: List<String> = emptyList(),
    @Json(name = "exclude_allergens") val excludeAllergens: List<String> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class AiRemixRequest(
    @Json(name = "recipe_name") val recipeName: String,
    @Json(name = "recipe_description") val recipeDescription: String = "",
    @Json(name = "recipe_ingredients") val recipeIngredients: List<String> = emptyList(),
    @Json(name = "recipe_instructions") val recipeInstructions: List<String> = emptyList(),
    @Json(name = "remix_prompt") val remixPrompt: String,
    @Json(name = "available_tags") val availableTags: List<String> = emptyList(),
    @Json(name = "exclude_allergens") val excludeAllergens: List<String> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class AiClassifyRequest(
    val name: String,
    val description: String = "",
    val tags: List<String> = emptyList(),
    val ingredients: List<String> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class AiClassifyResponse(
    @Json(name = "protein_type") val proteinType: String = "",
    val effort: String = "",
    val cuisine: String = "",
    @Json(name = "meal_slot") val mealSlot: String = "other",
    @Json(name = "season_fit") val seasonFit: String = "",
)

@JsonClass(generateAdapter = true)
data class AiNutritionRequest(
    val name: String,
    val description: String = "",
    val ingredients: List<String> = emptyList(),
    val portions: Int = 4,
)

@JsonClass(generateAdapter = true)
data class AiNutritionResponse(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
)

@JsonClass(generateAdapter = true)
data class ImportedIngredientDto(
    val food: String = "",
    val quantity: Double = 0.0,
    val unit: String = "",
    val note: String = "",
)

@JsonClass(generateAdapter = true)
data class ImportedInstructionDto(val text: String = "")

@JsonClass(generateAdapter = true)
data class WeekplanAssignmentDto(
    val date: String = "",
    @Json(name = "recipe_id") val recipeId: String = "",
    @Json(name = "recipe_name") val recipeName: String = "",
)

@JsonClass(generateAdapter = true)
data class ImportedRecipeDto(
    val name: String = "",
    val description: String = "",
    @Json(name = "recipe_yield") val recipeYield: String = "",
    @Json(name = "prep_time") val prepTime: String = "",
    @Json(name = "cook_time") val cookTime: String = "",
    @Json(name = "total_time") val totalTime: String = "",
    val cuisine: String = "",
    @Json(name = "meal_slot") val mealSlot: String = "other",
    val effort: String = "",
    @Json(name = "protein_type") val proteinType: String = "",
    @Json(name = "season_fit") val seasonFit: String = "",
    @Json(name = "source_url") val sourceUrl: String = "",
    @Json(name = "image_url") val imageUrl: String = "",
    val ingredients: List<ImportedIngredientDto> = emptyList(),
    val instructions: List<ImportedInstructionDto> = emptyList(),
    val tags: List<String> = emptyList(),
)
