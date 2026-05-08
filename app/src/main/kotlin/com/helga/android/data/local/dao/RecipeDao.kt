package com.helga.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.helga.android.data.local.entity.CategoryEntity
import com.helga.android.data.local.entity.IngredientEntity
import com.helga.android.data.local.entity.InstructionEntity
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    @Query("SELECT * FROM recipes WHERE deleted = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): RecipeEntity?

    @Query("SELECT * FROM recipes WHERE id = :id AND deleted = 0 LIMIT 1")
    fun observeById(id: String): Flow<RecipeEntity?>

    @Query("SELECT DISTINCT name FROM recipe_tags WHERE deleted = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeAllTagNames(): Flow<List<String>>

    @Query("SELECT DISTINCT recipeId FROM recipe_tags WHERE name = :tag AND deleted = 0")
    fun observeRecipeIdsByTag(tag: String): Flow<List<String>>

    @Query("SELECT DISTINCT recipeId FROM recipe_tags WHERE name IN (:tags) AND deleted = 0")
    fun observeRecipeIdsByTags(tags: List<String>): Flow<List<String>>

    @Query("UPDATE recipes SET rating = :rating, updatedAt = :updatedAt, dirty = 1 WHERE id = :id")
    suspend fun updateRating(id: String, rating: Int, updatedAt: Long)

    @Query("UPDATE recipes SET is_favorite = :isFavorite, updatedAt = :updatedAt, dirty = 1 WHERE id = :id")
    suspend fun updateFavorite(id: String, isFavorite: Int, updatedAt: Long)

    @Query("SELECT * FROM recipes WHERE dirty = 1")
    suspend fun dirtyRecipes(): List<RecipeEntity>

    @Query("SELECT * FROM recipe_ingredients WHERE recipeId = :recipeId AND deleted = 0 ORDER BY position ASC")
    fun observeIngredients(recipeId: String): Flow<List<IngredientEntity>>

    @Query("SELECT * FROM recipe_instructions WHERE recipeId = :recipeId AND deleted = 0 ORDER BY position ASC")
    fun observeInstructions(recipeId: String): Flow<List<InstructionEntity>>

    @Query("SELECT * FROM recipe_tags WHERE recipeId = :recipeId AND deleted = 0")
    fun observeTags(recipeId: String): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecipe(recipe: RecipeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecipes(recipes: List<RecipeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIngredients(items: List<IngredientEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInstructions(items: List<InstructionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTags(items: List<TagEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(items: List<CategoryEntity>)

    @Query("UPDATE recipes SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearRecipeDirty(ids: List<String>)

    @Query("UPDATE recipe_ingredients SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearIngredientDirty(ids: List<String>)

    @Query("UPDATE recipe_instructions SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearInstructionDirty(ids: List<String>)

    @Query("UPDATE recipe_tags SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearTagDirty(ids: List<String>)

    @Query("UPDATE recipe_categories SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearCategoryDirty(ids: List<String>)

    @Query("SELECT * FROM recipe_ingredients WHERE dirty = 1")
    suspend fun dirtyIngredients(): List<IngredientEntity>

    @Query("SELECT * FROM recipe_instructions WHERE dirty = 1")
    suspend fun dirtyInstructions(): List<InstructionEntity>

    @Query("SELECT * FROM recipe_tags WHERE dirty = 1")
    suspend fun dirtyTags(): List<TagEntity>

    @Query("SELECT * FROM recipe_categories WHERE dirty = 1")
    suspend fun dirtyCategories(): List<CategoryEntity>

    @Query("SELECT * FROM recipe_ingredients WHERE recipeId = :recipeId")
    suspend fun ingredientsByRecipeId(recipeId: String): List<IngredientEntity>

    @Query("SELECT * FROM recipe_instructions WHERE recipeId = :recipeId")
    suspend fun instructionsByRecipeId(recipeId: String): List<InstructionEntity>

    @Query("SELECT * FROM recipe_tags WHERE recipeId = :recipeId")
    suspend fun tagsByRecipeId(recipeId: String): List<TagEntity>

    @Query("SELECT * FROM recipes WHERE localImageUri != '' AND deleted = 0")
    suspend fun recipesWithLocalImage(): List<RecipeEntity>

    @Query("SELECT * FROM recipes WHERE deleted = 0 AND id NOT IN (:excludeIds) ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomExcluding(excludeIds: List<String>, limit: Int): List<RecipeEntity>

    @Query("UPDATE recipes SET imagePath = :imagePath, localImageUri = '', updatedAt = :updatedAt, dirty = 1 WHERE id = :id")
    suspend fun setImagePathAndClearLocal(id: String, imagePath: String, updatedAt: Long)

    @Transaction
    suspend fun upsertRecipeBundle(
        recipe: RecipeEntity,
        ingredients: List<IngredientEntity>,
        instructions: List<InstructionEntity>,
        tags: List<TagEntity>,
        categories: List<CategoryEntity>,
    ) {
        upsertRecipe(recipe)
        if (ingredients.isNotEmpty()) upsertIngredients(ingredients)
        if (instructions.isNotEmpty()) upsertInstructions(instructions)
        if (tags.isNotEmpty()) upsertTags(tags)
        if (categories.isNotEmpty()) upsertCategories(categories)
    }
}
