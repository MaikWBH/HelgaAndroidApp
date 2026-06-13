package com.helga.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.helga.android.data.local.entity.IngredientProductMappingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IngredientMappingDao {

    @Query("SELECT * FROM ingredient_product_mappings WHERE deleted = 0 ORDER BY ingredientName")
    fun observeAll(): Flow<List<IngredientProductMappingEntity>>

    @Query("SELECT * FROM ingredient_product_mappings WHERE deleted = 0")
    suspend fun allActive(): List<IngredientProductMappingEntity>

    @Query("SELECT * FROM ingredient_product_mappings WHERE ingredientName = :name AND deleted = 0 LIMIT 1")
    suspend fun getByIngredientName(name: String): IngredientProductMappingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mapping: IngredientProductMappingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(mappings: List<IngredientProductMappingEntity>)

    @Query("SELECT * FROM ingredient_product_mappings WHERE dirty = 1")
    suspend fun dirty(): List<IngredientProductMappingEntity>

    @Query("UPDATE ingredient_product_mappings SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearDirty(ids: List<String>)
}
