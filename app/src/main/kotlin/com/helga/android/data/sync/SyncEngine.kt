package com.helga.android.data.sync

import com.helga.android.data.local.AppDatabase
import com.helga.android.data.local.dao.RecipeDao
import com.helga.android.data.local.dao.SyncDao
import com.helga.android.data.local.entity.CategoryEntity
import com.helga.android.data.local.entity.IngredientEntity
import com.helga.android.data.local.entity.InstructionEntity
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.local.entity.TagEntity
import com.helga.android.data.preferences.AppPreferences
import com.helga.android.data.remote.SyncApiFactory
import com.helga.android.data.remote.dto.CategoryDto
import com.helga.android.data.remote.dto.IngredientDto
import com.helga.android.data.remote.dto.InstructionDto
import com.helga.android.data.remote.dto.RecipeDto
import com.helga.android.data.remote.dto.SyncPullResponse
import com.helga.android.data.remote.dto.SyncPushRequest
import com.helga.android.data.remote.dto.TagDto
import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bidirektionaler LWW-Sync.
 *
 * Pull → vergleicht Server-`updated_at` gegen lokale Werte; nur Server-Wins
 * werden in Room geschrieben (in einer Transaktion).
 *
 * Push → sendet alle `dirty = 1` Records. Server liefert Konflikte zurück
 * (Server-Wins), die ebenfalls in Room geschrieben werden. Dirty-Flags werden
 * nur für Records gelöscht, die nicht durch Server-Wins überschrieben wurden.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val database: AppDatabase,
    private val recipeDao: RecipeDao,
    private val syncDao: SyncDao,
    private val apiFactory: SyncApiFactory,
    private val preferences: AppPreferences,
) {

    suspend fun runFullSync(): SyncOutcome {
        val api = apiFactory.api()

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

        if (recipeWinners.isEmpty() && ingredientWinners.isEmpty() &&
            instructionWinners.isEmpty() && tagWinners.isEmpty() && categoryWinners.isEmpty()
        ) return

        database.withTransaction {
            if (recipeWinners.isNotEmpty()) recipeDao.upsertRecipes(recipeWinners.map { it.toEntity() })
            if (ingredientWinners.isNotEmpty()) recipeDao.upsertIngredients(ingredientWinners.map { it.toEntity() })
            if (instructionWinners.isNotEmpty()) recipeDao.upsertInstructions(instructionWinners.map { it.toEntity() })
            if (tagWinners.isNotEmpty()) recipeDao.upsertTags(tagWinners.map { it.toEntity() })
            if (categoryWinners.isNotEmpty()) recipeDao.upsertCategories(categoryWinners.map { it.toEntity() })
        }
    }

    private suspend fun buildPushBody(): SyncPushRequest = SyncPushRequest(
        clientTs = System.currentTimeMillis(),
        recipes = recipeDao.dirtyRecipes().map { it.toDto() },
        recipeIngredients = recipeDao.dirtyIngredients().map { it.toDto() },
        recipeInstructions = recipeDao.dirtyInstructions().map { it.toDto() },
        recipeTags = recipeDao.dirtyTags().map { it.toDto() },
        recipeCategories = recipeDao.dirtyCategories().map { it.toDto() },
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

        if (recipeIds.isEmpty() && ingIds.isEmpty() && insIds.isEmpty() &&
            tagIds.isEmpty() && catIds.isEmpty()
        ) return

        database.withTransaction {
            if (recipeIds.isNotEmpty()) recipeDao.clearRecipeDirty(recipeIds)
            if (ingIds.isNotEmpty()) recipeDao.clearIngredientDirty(ingIds)
            if (insIds.isNotEmpty()) recipeDao.clearInstructionDirty(insIds)
            if (tagIds.isNotEmpty()) recipeDao.clearTagDirty(tagIds)
            if (catIds.isNotEmpty()) recipeDao.clearCategoryDirty(catIds)
        }
    }

    /**
     * Behält nur Records, deren Server-`updated_at` echt neuer ist als der lokale.
     * Nicht-existierende lokale Records gewinnen automatisch (lokal = 0).
     */
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
    id = id,
    slug = slug,
    name = name,
    description = description,
    recipeYield = recipeYield,
    prepTime = prepTime,
    cookTime = cookTime,
    totalTime = totalTime,
    imagePath = imagePath,
    sourceUrl = sourceUrl,
    rating = rating,
    proteinType = proteinType,
    effort = effort,
    cuisine = cuisine,
    mealType = mealType,
    seasonFit = seasonFit,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deleted = deleted,
    dirty = 0,
)

private fun IngredientDto.toEntity(): IngredientEntity = IngredientEntity(
    id = id,
    recipeId = recipeId,
    position = position,
    quantity = quantity,
    unit = unit,
    food = food,
    note = note,
    updatedAt = updatedAt,
    deleted = deleted,
    dirty = 0,
)

private fun InstructionDto.toEntity(): InstructionEntity = InstructionEntity(
    id = id,
    recipeId = recipeId,
    position = position,
    text = text,
    updatedAt = updatedAt,
    deleted = deleted,
    dirty = 0,
)

private fun TagDto.toEntity(): TagEntity = TagEntity(
    id = id,
    recipeId = recipeId,
    name = name,
    updatedAt = updatedAt,
    deleted = deleted,
    dirty = 0,
)

private fun CategoryDto.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    recipeId = recipeId,
    name = name,
    updatedAt = updatedAt,
    deleted = deleted,
    dirty = 0,
)

private fun RecipeEntity.toDto(): RecipeDto = RecipeDto(
    id = id,
    updatedAt = updatedAt,
    deleted = deleted,
    slug = slug,
    name = name,
    description = description,
    recipeYield = recipeYield,
    prepTime = prepTime,
    cookTime = cookTime,
    totalTime = totalTime,
    imagePath = imagePath,
    sourceUrl = sourceUrl,
    rating = rating,
    proteinType = proteinType,
    effort = effort,
    cuisine = cuisine,
    mealType = mealType,
    seasonFit = seasonFit,
    createdAt = createdAt,
)

private fun IngredientEntity.toDto(): IngredientDto = IngredientDto(
    id = id,
    updatedAt = updatedAt,
    deleted = deleted,
    recipeId = recipeId,
    position = position,
    quantity = quantity,
    unit = unit,
    food = food,
    note = note,
)

private fun InstructionEntity.toDto(): InstructionDto = InstructionDto(
    id = id,
    updatedAt = updatedAt,
    deleted = deleted,
    recipeId = recipeId,
    position = position,
    text = text,
)

private fun TagEntity.toDto(): TagDto = TagDto(
    id = id,
    updatedAt = updatedAt,
    deleted = deleted,
    recipeId = recipeId,
    name = name,
)

private fun CategoryEntity.toDto(): CategoryDto = CategoryDto(
    id = id,
    updatedAt = updatedAt,
    deleted = deleted,
    recipeId = recipeId,
    name = name,
)
