package com.helga.android.di

import android.content.Context
import com.helga.android.data.local.AppDatabase
import com.helga.android.data.local.dao.QuickEmojiDao
import com.helga.android.data.local.dao.RecipeDao
import com.helga.android.data.local.dao.ShoppingDao
import com.helga.android.data.local.dao.StoreDao
import com.helga.android.data.local.dao.SyncDao
import com.helga.android.data.local.dao.RecipeFeedbackDao
import com.helga.android.data.local.dao.RecipeHistoryDao
import com.helga.android.data.local.dao.OffProductDao
import com.helga.android.data.local.dao.PantryDao
import com.helga.android.data.local.dao.ProductPriceDao
import com.helga.android.data.local.dao.IngredientMappingDao
import com.helga.android.data.local.dao.WeekplanConstraintsDao
import com.helga.android.data.local.dao.WeekplanDao
import com.helga.android.data.local.dao.WeekplanSettingsDao
import com.helga.android.data.local.dao.WeekplanTemplateDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.build(context)

    @Provides
    fun provideRecipeDao(db: AppDatabase): RecipeDao = db.recipeDao()

    @Provides
    fun provideSyncDao(db: AppDatabase): SyncDao = db.syncDao()

    @Provides
    fun provideShoppingDao(db: AppDatabase): ShoppingDao = db.shoppingDao()

    @Provides
    fun provideStoreDao(db: AppDatabase): StoreDao = db.storeDao()

    @Provides
    fun provideQuickEmojiDao(db: AppDatabase): QuickEmojiDao = db.quickEmojiDao()

    @Provides
    fun provideWeekplanDao(db: AppDatabase): WeekplanDao = db.weekplanDao()

    @Provides
    fun provideWeekplanSettingsDao(db: AppDatabase): WeekplanSettingsDao = db.weekplanSettingsDao()

    @Provides
    fun provideWeekplanConstraintsDao(db: AppDatabase): WeekplanConstraintsDao = db.weekplanConstraintsDao()

    @Provides
    fun provideWeekplanTemplateDao(db: AppDatabase): WeekplanTemplateDao = db.weekplanTemplateDao()

    @Provides
    fun provideRecipeHistoryDao(db: AppDatabase): RecipeHistoryDao = db.recipeHistoryDao()

    @Provides
    fun provideRecipeFeedbackDao(db: AppDatabase): RecipeFeedbackDao = db.recipeFeedbackDao()

    @Provides
    fun providePantryDao(db: AppDatabase): PantryDao = db.pantryDao()

    @Provides
    fun provideOffProductDao(db: AppDatabase): OffProductDao = db.offProductDao()

    @Provides
    fun provideProductPriceDao(db: AppDatabase): ProductPriceDao = db.productPriceDao()

    @Provides
    fun provideIngredientMappingDao(db: AppDatabase): IngredientMappingDao = db.ingredientMappingDao()
}
