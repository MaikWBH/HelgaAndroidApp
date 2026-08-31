package com.helga.android.data.sync

import com.helga.android.data.local.AppDatabase
import com.helga.android.data.local.dao.MonthlyBudgetDao
import com.helga.android.data.local.dao.OffProductDao
import com.helga.android.data.local.dao.QuickEmojiDao
import com.helga.android.data.local.dao.ReceiptDao
import com.helga.android.data.local.dao.RecipeDao
import com.helga.android.data.local.dao.RecipeFeedbackDao
import com.helga.android.data.local.dao.RecipeHistoryDao
import com.helga.android.data.local.dao.ShoppingDao
import com.helga.android.data.local.dao.StoreDao
import com.helga.android.data.local.dao.SyncDao
import com.helga.android.data.local.dao.WeekplanConstraintsDao
import com.helga.android.data.local.dao.WeekplanDao
import com.helga.android.data.local.dao.WeekplanSettingsDao
import com.helga.android.data.local.entity.AisleProductEntity
import com.helga.android.data.local.entity.CategoryEntity
import com.helga.android.data.local.entity.IngredientEntity
import com.helga.android.data.local.entity.InstructionEntity
import com.helga.android.data.local.entity.MonthlyBudgetEntity
import com.helga.android.data.local.entity.OffProductEntity
import com.helga.android.data.local.entity.QuickEmojiEntity
import com.helga.android.data.local.entity.ReceiptEntity
import com.helga.android.data.local.entity.ReceiptItemEntity
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.local.entity.ShoppingItemEntity
import com.helga.android.data.local.entity.ShoppingListEntity
import com.helga.android.data.local.entity.ShoppingListStapleEntity
import com.helga.android.data.local.entity.StoreAisleEntity
import com.helga.android.data.local.entity.StoreEntity
import com.helga.android.data.local.entity.TagEntity
import com.helga.android.data.local.entity.RecipeFeedbackEntity
import com.helga.android.data.local.entity.RecipeHistoryEntity
import com.helga.android.data.local.entity.WeekplanDayEntity
import com.helga.android.data.local.entity.WeekplanConstraintsEntity
import com.helga.android.data.local.entity.WeekplanExtraEntity
import com.helga.android.data.local.entity.WeekplanRecipeEntity
import com.helga.android.data.local.entity.WeekplanSettingsEntity
import com.helga.android.data.preferences.AppPreferences
import com.helga.android.data.remote.SyncApiFactory
import com.helga.android.data.remote.dto.AisleProductDto
import com.helga.android.data.remote.dto.CategoryDto
import com.helga.android.data.remote.dto.IngredientDto
import com.helga.android.data.remote.dto.InstructionDto
import com.helga.android.data.remote.dto.MonthlyBudgetDto
import com.helga.android.data.remote.dto.OffProductDto
import com.helga.android.data.remote.dto.QuickEmojiDto
import com.helga.android.data.remote.dto.ReceiptDto
import com.helga.android.data.remote.dto.ReceiptItemDto
import com.helga.android.data.remote.dto.RecipeDto
import com.helga.android.data.remote.dto.ShoppingItemDto
import com.helga.android.data.remote.dto.ShoppingListDto
import com.helga.android.data.remote.dto.ShoppingListStapleDto
import com.helga.android.data.remote.dto.StoreAisleDto
import com.helga.android.data.remote.dto.StoreDto
import com.helga.android.data.remote.dto.SyncPullResponse
import com.helga.android.data.remote.dto.SyncPushRequest
import com.helga.android.data.remote.dto.TagDto
import com.helga.android.data.remote.dto.RecipeFeedbackDto
import com.helga.android.data.remote.dto.RecipeHistoryDto
import com.helga.android.data.remote.dto.WeekplanConstraintsDto
import com.helga.android.data.remote.dto.WeekplanDayDto
import com.helga.android.data.remote.dto.WeekplanExtraDto
import com.helga.android.data.remote.dto.WeekplanRecipeDto
import com.helga.android.data.remote.dto.WeekplanSettingsDto
import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncEngine @Inject constructor(
    private val database: AppDatabase,
    private val recipeDao: RecipeDao,
    private val syncDao: SyncDao,
    private val shoppingDao: ShoppingDao,
    private val storeDao: StoreDao,
    private val quickEmojiDao: QuickEmojiDao,
    private val weekplanDao: WeekplanDao,
    private val weekplanSettingsDao: WeekplanSettingsDao,
    private val weekplanConstraintsDao: WeekplanConstraintsDao,
    private val recipeHistoryDao: RecipeHistoryDao,
    private val recipeFeedbackDao: RecipeFeedbackDao,
    private val receiptDao: ReceiptDao,
    private val monthlyBudgetDao: MonthlyBudgetDao,
    private val offProductDao: OffProductDao,
    private val apiFactory: SyncApiFactory,
    private val preferences: AppPreferences,
) {

    suspend fun runFullSync(): SyncOutcome {
        val api = apiFactory.api()

        preferences.ensureSyncProtocol()

        val lastSyncTs = preferences.currentLastSyncTs()
        val pull = api.pull(since = lastSyncTs)
        applyServerChanges(pull)

        val pushBody = buildPushBody()
        val pushResponse = api.push(pushBody)
        applyServerChanges(pushResponse)
        clearDirtyFlagsExcept(pushBody, pushResponse)

        val newTs = maxOf(pull.serverTs, pushResponse.serverTs)
        preferences.saveLastSyncTs(newTs)

        return SyncOutcome(
            pulled = pull.recipes.size,
            pushed = pushBody.recipes.size,
            serverTs = newTs,
        )
    }

    private suspend fun applyServerChanges(response: SyncPullResponse) {
        val recipeWinners = filterServerWins(response.recipes, syncDao.recipeTimestamps()) { it.id to it.updatedAt }
        val ingredientWinners = filterServerWins(response.recipeIngredients, syncDao.ingredientTimestamps()) { it.id to it.updatedAt }
        val instructionWinners = filterServerWins(response.recipeInstructions, syncDao.instructionTimestamps()) { it.id to it.updatedAt }
        val tagWinners = filterServerWins(response.recipeTags, syncDao.tagTimestamps()) { it.id to it.updatedAt }
        val categoryWinners = filterServerWins(response.recipeCategories, syncDao.categoryTimestamps()) { it.id to it.updatedAt }
        val listWinners = filterServerWins(response.shoppingLists, syncDao.shoppingListTimestamps()) { it.id to it.updatedAt }
        val itemWinners = filterServerWins(response.shoppingItems, syncDao.shoppingItemTimestamps()) { it.id to it.updatedAt }
        val storeWinners = filterServerWins(response.stores, syncDao.storeTimestamps()) { it.id to it.updatedAt }
        val aisleWinners = filterServerWins(response.storeAisles, syncDao.storeAisleTimestamps()) { it.id to it.updatedAt }
        val aisleProductWinners = filterServerWins(response.aisleProducts, syncDao.aisleProductTimestamps()) { it.id to it.updatedAt }
        val stapleWinners = filterServerWins(response.shoppingListStaples, syncDao.stapleTimestamps()) { it.id to it.updatedAt }
        val emojiWinners = filterServerWins(response.quickEmojis, syncDao.quickEmojiTimestamps()) { it.id to it.updatedAt }
        val wpDayWinners = filterServerWins(response.weekplanDays, syncDao.weekplanDayTimestamps()) { it.id to it.updatedAt }
        val wpRecipeWinners = filterServerWins(response.weekplanRecipes, syncDao.weekplanRecipeTimestamps()) { it.id to it.updatedAt }
        val wpExtraWinners = filterServerWins(response.weekplanExtras, syncDao.weekplanExtraTimestamps()) { it.id to it.updatedAt }
        val wpSettingsWinners = filterServerWins(response.weekplanSettings, syncDao.weekplanSettingsTimestamps()) { it.id to it.updatedAt }
        val wpConstraintsWinners = filterServerWins(response.weekplanConstraints, syncDao.weekplanConstraintsTimestamps()) { it.id to it.updatedAt }
        val historyWinners = response.recipeHistory.filter { dto ->
            dto.updatedAt > 0L
        }
        val feedbackWinners = response.recipeFeedback.filter { dto ->
            dto.updatedAt > 0L
        }
        val receiptWinners = filterServerWins(response.receipts, syncDao.receiptTimestamps()) { it.id to it.updatedAt }
        val receiptItemWinners = filterServerWins(response.receiptItems, syncDao.receiptItemTimestamps()) { it.id to it.updatedAt }
        val budgetWinners = filterServerWins(response.monthlyBudgets, syncDao.monthlyBudgetTimestamps()) { it.id to it.updatedAt }
        val offProductWinners = filterServerWins(response.offProducts, syncDao.offProductTimestamps()) { it.id to it.updatedAt }

        if (recipeWinners.isEmpty() && ingredientWinners.isEmpty() &&
            instructionWinners.isEmpty() && tagWinners.isEmpty() && categoryWinners.isEmpty() &&
            listWinners.isEmpty() && itemWinners.isEmpty() &&
            storeWinners.isEmpty() && aisleWinners.isEmpty() && aisleProductWinners.isEmpty() &&
            stapleWinners.isEmpty() && emojiWinners.isEmpty() &&
            wpDayWinners.isEmpty() && wpRecipeWinners.isEmpty() && wpExtraWinners.isEmpty() &&
            wpSettingsWinners.isEmpty() && wpConstraintsWinners.isEmpty() && historyWinners.isEmpty() &&
            feedbackWinners.isEmpty() && receiptWinners.isEmpty() && receiptItemWinners.isEmpty() &&
            budgetWinners.isEmpty() && offProductWinners.isEmpty()
        ) return

        database.withTransaction {
            if (recipeWinners.isNotEmpty()) recipeDao.upsertRecipes(recipeWinners.map { it.toEntity() })
            if (ingredientWinners.isNotEmpty()) recipeDao.upsertIngredients(ingredientWinners.map { it.toEntity() })
            if (instructionWinners.isNotEmpty()) recipeDao.upsertInstructions(instructionWinners.map { it.toEntity() })
            if (tagWinners.isNotEmpty()) recipeDao.upsertTags(tagWinners.map { it.toEntity() })
            if (categoryWinners.isNotEmpty()) recipeDao.upsertCategories(categoryWinners.map { it.toEntity() })
            if (listWinners.isNotEmpty()) shoppingDao.upsertLists(listWinners.map { it.toEntity() })
            if (itemWinners.isNotEmpty()) shoppingDao.upsertItems(itemWinners.map { it.toEntity() })
            if (storeWinners.isNotEmpty()) storeDao.upsertStores(storeWinners.map { it.toEntity() })
            if (aisleWinners.isNotEmpty()) storeDao.upsertAisles(aisleWinners.map { it.toEntity() })
            if (aisleProductWinners.isNotEmpty()) storeDao.upsertAisleProducts(aisleProductWinners.map { it.toEntity() })
            if (stapleWinners.isNotEmpty()) storeDao.upsertStaples(stapleWinners.map { it.toEntity() })
            if (emojiWinners.isNotEmpty()) quickEmojiDao.upsertEmojis(emojiWinners.map { it.toEntity() })
            if (wpDayWinners.isNotEmpty()) weekplanDao.upsertDays(wpDayWinners.map { it.toEntity() })
            if (wpRecipeWinners.isNotEmpty()) weekplanDao.upsertWeekplanRecipes(wpRecipeWinners.map { it.toEntity() })
            if (wpExtraWinners.isNotEmpty()) weekplanDao.upsertExtras(wpExtraWinners.map { it.toEntity() })
            if (wpSettingsWinners.isNotEmpty()) weekplanSettingsDao.upsert(wpSettingsWinners.first().toEntity())
            if (wpConstraintsWinners.isNotEmpty()) weekplanConstraintsDao.upsert(wpConstraintsWinners.first().toEntity())
            if (historyWinners.isNotEmpty()) recipeHistoryDao.upsertAll(historyWinners.map { it.toEntity() })
            if (feedbackWinners.isNotEmpty()) recipeFeedbackDao.upsertAll(feedbackWinners.map { it.toEntity() })
            if (receiptWinners.isNotEmpty()) receiptDao.upsertReceipts(receiptWinners.map { it.toEntity() })
            if (receiptItemWinners.isNotEmpty()) receiptDao.upsertItems(receiptItemWinners.map { it.toEntity() })
            if (budgetWinners.isNotEmpty()) monthlyBudgetDao.upsert(budgetWinners.first().toEntity())
            if (offProductWinners.isNotEmpty()) offProductDao.upsertAll(offProductWinners.map { it.toEntity() })
        }
    }

    private suspend fun buildPushBody(): SyncPushRequest = SyncPushRequest(
        clientTs = System.currentTimeMillis(),
        recipes = recipeDao.dirtyRecipes().map { it.toDto() },
        recipeIngredients = recipeDao.dirtyIngredients().map { it.toDto() },
        recipeInstructions = recipeDao.dirtyInstructions().map { it.toDto() },
        recipeTags = recipeDao.dirtyTags().map { it.toDto() },
        recipeCategories = recipeDao.dirtyCategories().map { it.toDto() },
        shoppingLists = shoppingDao.dirtyLists().map { it.toDto() },
        shoppingItems = shoppingDao.dirtyItems().map { it.toDto() },
        stores = storeDao.dirtyStores().map { it.toDto() },
        storeAisles = storeDao.dirtyAisles().map { it.toDto() },
        aisleProducts = storeDao.dirtyAisleProducts().map { it.toDto() },
        shoppingListStaples = storeDao.dirtyStaples().map { it.toDto() },
        quickEmojis = quickEmojiDao.dirtyEmojis().map { it.toDto() },
        weekplanDays = weekplanDao.dirtyDays().map { it.toDto() },
        weekplanRecipes = weekplanDao.dirtyWeekplanRecipes().map { it.toDto() },
        weekplanExtras = weekplanDao.dirtyExtras().map { it.toDto() },
        weekplanSettings = weekplanSettingsDao.dirty().map { it.toDto() },
        weekplanConstraints = weekplanConstraintsDao.dirty().map { it.toDto() },
        recipeHistory = recipeHistoryDao.dirtyHistory().map { it.toDto() },
        recipeFeedback = recipeFeedbackDao.getDirty().map { it.toDto() },
        receipts = receiptDao.dirtyReceipts().map { it.toDto() },
        receiptItems = receiptDao.dirtyItems().map { it.toDto() },
        monthlyBudgets = monthlyBudgetDao.dirty().map { it.toDto() },
        offProducts = offProductDao.dirtyProducts().map { it.toDto() },
    )

    private suspend fun clearDirtyFlagsExcept(
        pushed: SyncPushRequest,
        serverWins: SyncPullResponse,
    ) {
        val recipeIds = pushed.recipes.map { it.id } - serverWins.recipes.map { it.id }.toSet()
        val ingIds = pushed.recipeIngredients.map { it.id } - serverWins.recipeIngredients.map { it.id }.toSet()
        val insIds = pushed.recipeInstructions.map { it.id } - serverWins.recipeInstructions.map { it.id }.toSet()
        val tagIds = pushed.recipeTags.map { it.id } - serverWins.recipeTags.map { it.id }.toSet()
        val catIds = pushed.recipeCategories.map { it.id } - serverWins.recipeCategories.map { it.id }.toSet()
        val listIds = pushed.shoppingLists.map { it.id } - serverWins.shoppingLists.map { it.id }.toSet()
        val itemIds = pushed.shoppingItems.map { it.id } - serverWins.shoppingItems.map { it.id }.toSet()
        val storeIds = pushed.stores.map { it.id } - serverWins.stores.map { it.id }.toSet()
        val aisleIds = pushed.storeAisles.map { it.id } - serverWins.storeAisles.map { it.id }.toSet()
        val aisleProductIds = pushed.aisleProducts.map { it.id } - serverWins.aisleProducts.map { it.id }.toSet()
        val stapleIds = pushed.shoppingListStaples.map { it.id } - serverWins.shoppingListStaples.map { it.id }.toSet()
        val emojiIds = pushed.quickEmojis.map { it.id } - serverWins.quickEmojis.map { it.id }.toSet()
        val wpDayIds = pushed.weekplanDays.map { it.id } - serverWins.weekplanDays.map { it.id }.toSet()
        val wpRecipeIds = pushed.weekplanRecipes.map { it.id } - serverWins.weekplanRecipes.map { it.id }.toSet()
        val wpExtraIds = pushed.weekplanExtras.map { it.id } - serverWins.weekplanExtras.map { it.id }.toSet()
        val wpSettingsIds = pushed.weekplanSettings.map { it.id } - serverWins.weekplanSettings.map { it.id }.toSet()
        val wpConstraintsIds = pushed.weekplanConstraints.map { it.id } - serverWins.weekplanConstraints.map { it.id }.toSet()
        val historyIds = pushed.recipeHistory.map { it.id } - serverWins.recipeHistory.map { it.id }.toSet()
        val feedbackIds = pushed.recipeFeedback.map { it.id } - serverWins.recipeFeedback.map { it.id }.toSet()
        val receiptIds = pushed.receipts.map { it.id } - serverWins.receipts.map { it.id }.toSet()
        val receiptItemIds = pushed.receiptItems.map { it.id } - serverWins.receiptItems.map { it.id }.toSet()
        val budgetIds = pushed.monthlyBudgets.map { it.id } - serverWins.monthlyBudgets.map { it.id }.toSet()
        val offProductIds = pushed.offProducts.map { it.id } - serverWins.offProducts.map { it.id }.toSet()

        database.withTransaction {
            if (recipeIds.isNotEmpty()) recipeDao.clearRecipeDirty(recipeIds)
            if (ingIds.isNotEmpty()) recipeDao.clearIngredientDirty(ingIds)
            if (insIds.isNotEmpty()) recipeDao.clearInstructionDirty(insIds)
            if (tagIds.isNotEmpty()) recipeDao.clearTagDirty(tagIds)
            if (catIds.isNotEmpty()) recipeDao.clearCategoryDirty(catIds)
            if (listIds.isNotEmpty()) shoppingDao.clearListDirty(listIds)
            if (itemIds.isNotEmpty()) shoppingDao.clearItemDirty(itemIds)
            if (storeIds.isNotEmpty()) storeDao.clearStoreDirty(storeIds)
            if (aisleIds.isNotEmpty()) storeDao.clearAisleDirty(aisleIds)
            if (aisleProductIds.isNotEmpty()) storeDao.clearAisleProductDirty(aisleProductIds)
            if (stapleIds.isNotEmpty()) storeDao.clearStapleDirty(stapleIds)
            if (emojiIds.isNotEmpty()) quickEmojiDao.clearEmojiDirty(emojiIds)
            if (wpDayIds.isNotEmpty()) weekplanDao.clearDayDirty(wpDayIds)
            if (wpRecipeIds.isNotEmpty()) weekplanDao.clearWeekplanRecipeDirty(wpRecipeIds)
            if (wpExtraIds.isNotEmpty()) weekplanDao.clearExtraDirty(wpExtraIds)
            if (wpSettingsIds.isNotEmpty()) weekplanSettingsDao.clearDirty(wpSettingsIds.toList())
            if (wpConstraintsIds.isNotEmpty()) weekplanConstraintsDao.clearDirty(wpConstraintsIds.toList())
            if (historyIds.isNotEmpty()) recipeHistoryDao.clearDirty(historyIds.toList())
            if (feedbackIds.isNotEmpty()) recipeFeedbackDao.clearDirty(feedbackIds.toList())
            if (receiptIds.isNotEmpty()) receiptDao.clearReceiptDirty(receiptIds)
            if (receiptItemIds.isNotEmpty()) receiptDao.clearItemDirty(receiptItemIds)
            if (budgetIds.isNotEmpty()) monthlyBudgetDao.clearDirty(budgetIds.toList())
            if (offProductIds.isNotEmpty()) offProductDao.clearDirty(offProductIds.toList())
        }
    }

    private inline fun <T> filterServerWins(
        remote: List<T>,
        local: List<com.helga.android.data.local.dao.TimestampRow>,
        crossinline keyOf: (T) -> Pair<String, Long>,
    ): List<T> {
        if (remote.isEmpty()) return emptyList()
        val localMap = local.associate { it.id to it.updatedAt }
        return remote.filter { item ->
            val (id, remoteTs) = keyOf(item)
            remoteTs > (localMap[id] ?: 0L)
        }
    }
}

data class SyncOutcome(val pulled: Int, val pushed: Int, val serverTs: Long)

// ── Mapper: Entity ↔ DTO ────────────────────────────────────────────────────

private fun RecipeDto.toEntity(): RecipeEntity = RecipeEntity(
    id = id, slug = slug, name = name, description = description,
    recipeYield = recipeYield, prepTime = prepTime, cookTime = cookTime,
    totalTime = totalTime, imagePath = imagePath, sourceUrl = sourceUrl,
    rating = rating, proteinType = proteinType, effort = effort,
    cuisine = cuisine, mealSlot = mealSlot, seasonFit = seasonFit,
    nutritionKcal = nutritionKcal, nutritionProtein = nutritionProtein,
    nutritionFat = nutritionFat, nutritionCarbs = nutritionCarbs,
    nutritionNutriScore = nutritionNutriScore, nutritionSource = nutritionSource,
    createdAt = createdAt, updatedAt = updatedAt, deleted = deleted, dirty = 0,
    lastServings = lastServings,
)

private fun IngredientDto.toEntity(): IngredientEntity = IngredientEntity(
    id = id, recipeId = recipeId, position = position, quantity = quantity,
    unit = unit, food = food, note = note, updatedAt = updatedAt, deleted = deleted, dirty = 0,
)

private fun InstructionDto.toEntity(): InstructionEntity = InstructionEntity(
    id = id, recipeId = recipeId, position = position, text = text,
    updatedAt = updatedAt, deleted = deleted, dirty = 0,
)

private fun TagDto.toEntity(): TagEntity = TagEntity(
    id = id, recipeId = recipeId, name = name, updatedAt = updatedAt, deleted = deleted, dirty = 0,
)

private fun CategoryDto.toEntity(): CategoryEntity = CategoryEntity(
    id = id, recipeId = recipeId, name = name, updatedAt = updatedAt, deleted = deleted, dirty = 0,
)

private fun ShoppingListDto.toEntity(): ShoppingListEntity = ShoppingListEntity(
    id = id, name = name, isActive = isActive,
    isDefaultWeekplan = isDefaultWeekplan, isDefaultRecipe = isDefaultRecipe,
    updatedAt = updatedAt, deleted = deleted, dirty = 0,
)

private fun ShoppingItemDto.toEntity(): ShoppingItemEntity = ShoppingItemEntity(
    id = id, listId = listId, name = name, quantity = quantity, unit = unit,
    aisle = aisle, source = source, isChecked = isChecked, sortOrder = sortOrder,
    origins = origins.ifBlank { "[]" },
    updatedAt = updatedAt, deleted = deleted, dirty = 0,
)

private fun StoreDto.toEntity(): StoreEntity = StoreEntity(
    id = id, name = name, isActive = isActive, updatedAt = updatedAt, deleted = deleted, dirty = 0,
)

private fun StoreAisleDto.toEntity(): StoreAisleEntity = StoreAisleEntity(
    id = id, storeId = storeId, aisleName = aisleName, sortOrder = sortOrder,
    updatedAt = updatedAt, deleted = deleted, dirty = 0,
)

private fun AisleProductDto.toEntity(): AisleProductEntity = AisleProductEntity(
    id = id, aisleName = aisleName, productName = productName, storeId = storeId,
    updatedAt = updatedAt, deleted = deleted, dirty = 0,
)

private fun ShoppingListStapleDto.toEntity(): ShoppingListStapleEntity = ShoppingListStapleEntity(
    id = id, listId = listId, name = name, quantity = quantity, sortOrder = sortOrder,
    updatedAt = updatedAt, deleted = deleted, dirty = 0,
)

private fun QuickEmojiDto.toEntity(): QuickEmojiEntity = QuickEmojiEntity(
    id = id, emoji = emoji, food = food, quantity = quantity, unit = unit,
    sortOrder = sortOrder, updatedAt = updatedAt, deleted = deleted, dirty = 0,
)

private fun RecipeEntity.toDto(): RecipeDto = RecipeDto(
    id = id, updatedAt = updatedAt, deleted = deleted, slug = slug, name = name,
    description = description, recipeYield = recipeYield, prepTime = prepTime,
    cookTime = cookTime, totalTime = totalTime, imagePath = imagePath,
    sourceUrl = sourceUrl, rating = rating, proteinType = proteinType,
    effort = effort, cuisine = cuisine, mealSlot = mealSlot, seasonFit = seasonFit,
    nutritionKcal = nutritionKcal, nutritionProtein = nutritionProtein,
    nutritionFat = nutritionFat, nutritionCarbs = nutritionCarbs,
    nutritionNutriScore = nutritionNutriScore, nutritionSource = nutritionSource,
    createdAt = createdAt,
    lastServings = lastServings,
)

private fun IngredientEntity.toDto(): IngredientDto = IngredientDto(
    id = id, updatedAt = updatedAt, deleted = deleted, recipeId = recipeId,
    position = position, quantity = quantity, unit = unit, food = food, note = note,
)

private fun InstructionEntity.toDto(): InstructionDto = InstructionDto(
    id = id, updatedAt = updatedAt, deleted = deleted, recipeId = recipeId,
    position = position, text = text,
)

private fun TagEntity.toDto(): TagDto = TagDto(
    id = id, updatedAt = updatedAt, deleted = deleted, recipeId = recipeId, name = name,
)

private fun CategoryEntity.toDto(): CategoryDto = CategoryDto(
    id = id, updatedAt = updatedAt, deleted = deleted, recipeId = recipeId, name = name,
)

private fun ShoppingListEntity.toDto(): ShoppingListDto = ShoppingListDto(
    id = id, updatedAt = updatedAt, deleted = deleted, name = name,
    isActive = isActive, isDefaultWeekplan = isDefaultWeekplan, isDefaultRecipe = isDefaultRecipe,
)

private fun ShoppingItemEntity.toDto(): ShoppingItemDto = ShoppingItemDto(
    id = id, updatedAt = updatedAt, deleted = deleted, listId = listId, name = name,
    quantity = quantity, unit = unit, aisle = aisle, source = source,
    isChecked = isChecked, sortOrder = sortOrder, origins = origins,
)

private fun StoreEntity.toDto(): StoreDto = StoreDto(
    id = id, updatedAt = updatedAt, deleted = deleted, name = name, isActive = isActive,
)

private fun StoreAisleEntity.toDto(): StoreAisleDto = StoreAisleDto(
    id = id, updatedAt = updatedAt, deleted = deleted, storeId = storeId,
    aisleName = aisleName, sortOrder = sortOrder,
)

private fun AisleProductEntity.toDto(): AisleProductDto = AisleProductDto(
    id = id, updatedAt = updatedAt, deleted = deleted, aisleName = aisleName,
    productName = productName, storeId = storeId,
)

private fun ShoppingListStapleEntity.toDto(): ShoppingListStapleDto = ShoppingListStapleDto(
    id = id, updatedAt = updatedAt, deleted = deleted, listId = listId,
    name = name, quantity = quantity, sortOrder = sortOrder,
)

private fun QuickEmojiEntity.toDto(): QuickEmojiDto = QuickEmojiDto(
    id = id, updatedAt = updatedAt, deleted = deleted, emoji = emoji,
    food = food, quantity = quantity, unit = unit, sortOrder = sortOrder,
)

private fun WeekplanDayDto.toEntity(): WeekplanDayEntity = WeekplanDayEntity(
    id = id, planDate = planDate, note = note, isQuickDay = isQuickDay, isGuestDay = isGuestDay,
    isSkipped = isSkipped, updatedAt = updatedAt, deleted = deleted, dirty = 0,
)

private fun WeekplanRecipeDto.toEntity(): WeekplanRecipeEntity = WeekplanRecipeEntity(
    id = id, weekplanDayId = weekplanDayId, recipeId = recipeId, position = position,
    updatedAt = updatedAt, deleted = deleted, dirty = 0,
)

private fun WeekplanExtraDto.toEntity(): WeekplanExtraEntity = WeekplanExtraEntity(
    id = id, weekplanDayId = weekplanDayId, itemText = itemText, position = position,
    updatedAt = updatedAt, deleted = deleted, dirty = 0,
)

private fun WeekplanDayEntity.toDto(): WeekplanDayDto = WeekplanDayDto(
    id = id, updatedAt = updatedAt, deleted = deleted, planDate = planDate, note = note,
    isQuickDay = isQuickDay, isGuestDay = isGuestDay, isSkipped = isSkipped,
)

private fun WeekplanRecipeEntity.toDto(): WeekplanRecipeDto = WeekplanRecipeDto(
    id = id, updatedAt = updatedAt, deleted = deleted, weekplanDayId = weekplanDayId,
    recipeId = recipeId, position = position,
)

private fun WeekplanExtraEntity.toDto(): WeekplanExtraDto = WeekplanExtraDto(
    id = id, updatedAt = updatedAt, deleted = deleted, weekplanDayId = weekplanDayId,
    itemText = itemText, position = position,
)

private fun WeekplanSettingsDto.toEntity(): WeekplanSettingsEntity = WeekplanSettingsEntity(
    id = id,
    planDays = planDays,
    shoppingDay = shoppingDay,
    updatedAt = updatedAt,
    deleted = deleted,
    dirty = 0,
)

private fun WeekplanSettingsEntity.toDto(): WeekplanSettingsDto = WeekplanSettingsDto(
    id = id,
    updatedAt = updatedAt,
    deleted = deleted,
    planDays = planDays,
    shoppingDay = shoppingDay,
)

private fun WeekplanConstraintsDto.toEntity(): WeekplanConstraintsEntity = WeekplanConstraintsEntity(
    id = id,
    maxMeatPerWeek = maxMeatPerWeek,
    maxFishPerWeek = maxFishPerWeek,
    minVegetarianPerWeek = minVegetarianPerWeek,
    maxRepeatDays = maxRepeatDays,
    updatedAt = updatedAt,
    deleted = deleted,
    dirty = 0,
)

private fun WeekplanConstraintsEntity.toDto(): WeekplanConstraintsDto = WeekplanConstraintsDto(
    id = id,
    maxMeatPerWeek = maxMeatPerWeek,
    maxFishPerWeek = maxFishPerWeek,
    minVegetarianPerWeek = minVegetarianPerWeek,
    maxRepeatDays = maxRepeatDays,
    updatedAt = updatedAt,
    deleted = deleted,
)

private fun RecipeHistoryDto.toEntity(): RecipeHistoryEntity = RecipeHistoryEntity(
    id = id, recipeId = recipeId, plannedDate = plannedDate, cooked = cooked,
    updatedAt = updatedAt, deleted = deleted, dirty = 0,
)

private fun RecipeHistoryEntity.toDto(): RecipeHistoryDto = RecipeHistoryDto(
    id = id, recipeId = recipeId, plannedDate = plannedDate, cooked = cooked,
    updatedAt = updatedAt, deleted = deleted,
)

private fun RecipeFeedbackDto.toEntity(): RecipeFeedbackEntity = RecipeFeedbackEntity(
    id = id, recipeId = recipeId, plannedDate = plannedDate,
    liked = liked, updatedAt = updatedAt, deleted = deleted, dirty = 0,
)

private fun RecipeFeedbackEntity.toDto(): RecipeFeedbackDto = RecipeFeedbackDto(
    id = id, recipeId = recipeId, plannedDate = plannedDate,
    liked = liked, updatedAt = updatedAt, deleted = deleted,
)

// localImageUri ist gerätelokal (absoluter Dateipfad) und wird – wie bei Rezepten –
// bewusst NICHT synchronisiert. Nur imagePath (Server-Dateiname) wandert über den Sync.
private fun ReceiptDto.toEntity(): ReceiptEntity = ReceiptEntity(
    id = id, storeId = storeId, storeName = storeName, shoppingListId = shoppingListId,
    purchaseDate = purchaseDate, totalAmount = totalAmount, currency = currency,
    imagePath = imagePath, rawOcrText = rawOcrText,
    status = status, updatedAt = updatedAt, deleted = deleted, dirty = 0,
)

private fun ReceiptEntity.toDto(): ReceiptDto = ReceiptDto(
    id = id, storeId = storeId, storeName = storeName, shoppingListId = shoppingListId,
    purchaseDate = purchaseDate, totalAmount = totalAmount, currency = currency,
    imagePath = imagePath, rawOcrText = rawOcrText,
    status = status, updatedAt = updatedAt, deleted = deleted,
)

private fun ReceiptItemDto.toEntity(): ReceiptItemEntity = ReceiptItemEntity(
    id = id, receiptId = receiptId, position = position, rawText = rawText, name = name,
    quantity = quantity, unitPrice = unitPrice, totalPrice = totalPrice,
    matchedShoppingItemId = matchedShoppingItemId, matchStatus = matchStatus,
    updatedAt = updatedAt, deleted = deleted, dirty = 0,
)

private fun ReceiptItemEntity.toDto(): ReceiptItemDto = ReceiptItemDto(
    id = id, receiptId = receiptId, position = position, rawText = rawText, name = name,
    quantity = quantity, unitPrice = unitPrice, totalPrice = totalPrice,
    matchedShoppingItemId = matchedShoppingItemId, matchStatus = matchStatus,
    updatedAt = updatedAt, deleted = deleted,
)

private fun MonthlyBudgetDto.toEntity(): MonthlyBudgetEntity = MonthlyBudgetEntity(
    id = id, amount = amount, warnThreshold = warnThreshold,
    updatedAt = updatedAt, deleted = deleted, dirty = 0,
)

private fun MonthlyBudgetEntity.toDto(): MonthlyBudgetDto = MonthlyBudgetDto(
    id = id, updatedAt = updatedAt, deleted = deleted,
    amount = amount, warnThreshold = warnThreshold,
)

private fun OffProductDto.toEntity(): OffProductEntity = OffProductEntity(
    id = id, barcode = barcode, name = name, brand = brand, categories = categories,
    kcalPerUnit = kcalPerUnit, proteins = proteins, fats = fats, carbs = carbs,
    nutriScore = nutriScore, nova = nova, ecoScore = ecoScore, allergenes = allergenes,
    additives = additives, isOrganic = isOrganic, vegan = vegan, vegetarian = vegetarian,
    imagePath = imagePath, isFavorite = isFavorite, packageGrams = packageGrams,
    packageGramsManual = packageGramsManual, updatedAt = updatedAt, deleted = deleted, dirty = 0,
)

private fun OffProductEntity.toDto(): OffProductDto = OffProductDto(
    id = id, updatedAt = updatedAt, deleted = deleted, barcode = barcode, name = name,
    brand = brand, categories = categories, kcalPerUnit = kcalPerUnit, proteins = proteins,
    fats = fats, carbs = carbs, nutriScore = nutriScore, nova = nova, ecoScore = ecoScore,
    allergenes = allergenes, additives = additives, isOrganic = isOrganic, vegan = vegan,
    vegetarian = vegetarian, imagePath = imagePath, isFavorite = isFavorite,
    packageGrams = packageGrams, packageGramsManual = packageGramsManual,
)
