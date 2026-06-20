package com.helga.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.helga.android.data.local.dao.MonthlyBudgetDao
import com.helga.android.data.local.dao.OffProductDao
import com.helga.android.data.local.dao.QuickEmojiDao
import com.helga.android.data.local.dao.ReceiptDao
import com.helga.android.data.local.dao.RecipeFeedbackDao
import com.helga.android.data.local.dao.RecipeDao
import com.helga.android.data.local.dao.RecipeHistoryDao
import com.helga.android.data.local.dao.ShoppingDao
import com.helga.android.data.local.dao.StoreDao
import com.helga.android.data.local.dao.SyncDao
import com.helga.android.data.local.dao.WeekplanConstraintsDao
import com.helga.android.data.local.dao.WeekplanDao
import com.helga.android.data.local.dao.WeekplanSettingsDao
import com.helga.android.data.local.dao.WeekplanTemplateDao
import com.helga.android.data.local.entity.AisleProductEntity
import com.helga.android.data.local.entity.CategoryEntity
import com.helga.android.data.local.entity.IngredientEntity
import com.helga.android.data.local.entity.InstructionEntity
import com.helga.android.data.local.entity.MonthlyBudgetEntity
import com.helga.android.data.local.entity.OffProductEntity
import com.helga.android.data.local.entity.QuickEmojiEntity
import com.helga.android.data.local.entity.ReceiptEntity
import com.helga.android.data.local.entity.ReceiptItemEntity
import com.helga.android.data.local.entity.RecipeFeedbackEntity
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.local.entity.RecipeHistoryEntity
import com.helga.android.data.local.entity.ShoppingItemEntity
import com.helga.android.data.local.entity.ShoppingListEntity
import com.helga.android.data.local.entity.ShoppingListStapleEntity
import com.helga.android.data.local.entity.StoreAisleEntity
import com.helga.android.data.local.entity.StoreEntity
import com.helga.android.data.local.entity.TagEntity
import com.helga.android.data.local.entity.WeekplanDayEntity
import com.helga.android.data.local.entity.WeekplanConstraintsEntity
import com.helga.android.data.local.entity.WeekplanExtraEntity
import com.helga.android.data.local.entity.WeekplanRecipeEntity
import com.helga.android.data.local.entity.WeekplanSettingsEntity
import com.helga.android.data.local.entity.WeekplanTemplateEntity
import com.helga.android.data.local.entity.WeekplanTemplateEntryEntity

@Database(
    version = 29,
    exportSchema = true,
    entities = [
        RecipeEntity::class,
        IngredientEntity::class,
        InstructionEntity::class,
        TagEntity::class,
        CategoryEntity::class,
        ShoppingListEntity::class,
        ShoppingItemEntity::class,
        StoreEntity::class,
        StoreAisleEntity::class,
        AisleProductEntity::class,
        ShoppingListStapleEntity::class,
        QuickEmojiEntity::class,
        WeekplanDayEntity::class,
        WeekplanRecipeEntity::class,
        WeekplanExtraEntity::class,
        WeekplanSettingsEntity::class,
        WeekplanConstraintsEntity::class,
        WeekplanTemplateEntity::class,
        WeekplanTemplateEntryEntity::class,
        RecipeHistoryEntity::class,
        RecipeFeedbackEntity::class,
        ReceiptEntity::class,
        ReceiptItemEntity::class,
        MonthlyBudgetEntity::class,
        OffProductEntity::class,
    ],
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun recipeDao(): RecipeDao
    abstract fun syncDao(): SyncDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun storeDao(): StoreDao
    abstract fun quickEmojiDao(): QuickEmojiDao
    abstract fun weekplanDao(): WeekplanDao
    abstract fun weekplanSettingsDao(): WeekplanSettingsDao
    abstract fun weekplanConstraintsDao(): WeekplanConstraintsDao
    abstract fun weekplanTemplateDao(): WeekplanTemplateDao
    abstract fun recipeHistoryDao(): RecipeHistoryDao
    abstract fun recipeFeedbackDao(): RecipeFeedbackDao
    abstract fun receiptDao(): ReceiptDao
    abstract fun monthlyBudgetDao(): MonthlyBudgetDao
    abstract fun offProductDao(): OffProductDao

    companion object {
        const val NAME = "helga.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipes ADD COLUMN localImageUri TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS shopping_lists (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL DEFAULT '',
                        isActive INTEGER NOT NULL DEFAULT 0,
                        isDefaultWeekplan INTEGER NOT NULL DEFAULT 0,
                        isDefaultRecipe INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        deleted INTEGER NOT NULL DEFAULT 0,
                        dirty INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS shopping_items (
                        id TEXT NOT NULL PRIMARY KEY,
                        listId TEXT NOT NULL,
                        name TEXT NOT NULL DEFAULT '',
                        quantity REAL NOT NULL DEFAULT 1.0,
                        unit TEXT NOT NULL DEFAULT '',
                        aisle TEXT NOT NULL DEFAULT '',
                        source TEXT NOT NULL DEFAULT 'manual',
                        isChecked INTEGER NOT NULL DEFAULT 0,
                        sortOrder INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        deleted INTEGER NOT NULL DEFAULT 0,
                        dirty INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_shopping_lists_updatedAt ON shopping_lists(updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_shopping_lists_deleted ON shopping_lists(deleted)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_shopping_items_listId ON shopping_items(listId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_shopping_items_updatedAt ON shopping_items(updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_shopping_items_deleted ON shopping_items(deleted)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS stores (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL DEFAULT '',
                        isActive INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        deleted INTEGER NOT NULL DEFAULT 0,
                        dirty INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS store_aisles (
                        id TEXT NOT NULL PRIMARY KEY,
                        storeId TEXT NOT NULL,
                        aisleName TEXT NOT NULL DEFAULT '',
                        sortOrder INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        deleted INTEGER NOT NULL DEFAULT 0,
                        dirty INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS aisle_products (
                        id TEXT NOT NULL PRIMARY KEY,
                        aisleName TEXT NOT NULL DEFAULT '',
                        productName TEXT NOT NULL DEFAULT '',
                        storeId TEXT NOT NULL DEFAULT '',
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        deleted INTEGER NOT NULL DEFAULT 0,
                        dirty INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS shopping_list_staples (
                        id TEXT NOT NULL PRIMARY KEY,
                        listId TEXT NOT NULL,
                        name TEXT NOT NULL DEFAULT '',
                        quantity REAL NOT NULL DEFAULT 1.0,
                        sortOrder INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        deleted INTEGER NOT NULL DEFAULT 0,
                        dirty INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS quick_emojis (
                        id TEXT NOT NULL PRIMARY KEY,
                        emoji TEXT NOT NULL DEFAULT '',
                        food TEXT NOT NULL DEFAULT '',
                        quantity REAL NOT NULL DEFAULT 1.0,
                        unit TEXT NOT NULL DEFAULT '',
                        sortOrder INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        deleted INTEGER NOT NULL DEFAULT 0,
                        dirty INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_stores_updatedAt ON stores(updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_stores_deleted ON stores(deleted)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_store_aisles_storeId ON store_aisles(storeId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_store_aisles_updatedAt ON store_aisles(updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_store_aisles_deleted ON store_aisles(deleted)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_aisle_products_storeId ON aisle_products(storeId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_aisle_products_productName ON aisle_products(productName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_aisle_products_updatedAt ON aisle_products(updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_aisle_products_deleted ON aisle_products(deleted)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_shopping_list_staples_listId ON shopping_list_staples(listId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_shopping_list_staples_updatedAt ON shopping_list_staples(updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_shopping_list_staples_deleted ON shopping_list_staples(deleted)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_quick_emojis_updatedAt ON quick_emojis(updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_quick_emojis_deleted ON quick_emojis(deleted)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS weekplan_days (
                        id TEXT NOT NULL PRIMARY KEY,
                        planDate TEXT NOT NULL DEFAULT '',
                        note TEXT NOT NULL DEFAULT '',
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        deleted INTEGER NOT NULL DEFAULT 0,
                        dirty INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS weekplan_recipes (
                        id TEXT NOT NULL PRIMARY KEY,
                        weekplanDayId TEXT NOT NULL,
                        recipeId TEXT NOT NULL,
                        position INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        deleted INTEGER NOT NULL DEFAULT 0,
                        dirty INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS weekplan_extras (
                        id TEXT NOT NULL PRIMARY KEY,
                        weekplanDayId TEXT NOT NULL,
                        itemText TEXT NOT NULL DEFAULT '',
                        position INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        deleted INTEGER NOT NULL DEFAULT 0,
                        dirty INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_weekplan_days_planDate ON weekplan_days(planDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_weekplan_days_updatedAt ON weekplan_days(updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_weekplan_days_deleted ON weekplan_days(deleted)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_weekplan_recipes_weekplanDayId ON weekplan_recipes(weekplanDayId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_weekplan_recipes_recipeId ON weekplan_recipes(recipeId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_weekplan_recipes_updatedAt ON weekplan_recipes(updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_weekplan_recipes_deleted ON weekplan_recipes(deleted)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_weekplan_extras_weekplanDayId ON weekplan_extras(weekplanDayId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_weekplan_extras_updatedAt ON weekplan_extras(updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_weekplan_extras_deleted ON weekplan_extras(deleted)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS weekplan_settings (
                        id TEXT NOT NULL PRIMARY KEY,
                        planDays INTEGER NOT NULL DEFAULT 7,
                        shoppingDay INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        deleted INTEGER NOT NULL DEFAULT 0,
                        dirty INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_weekplan_settings_updatedAt ON weekplan_settings(updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_weekplan_settings_deleted ON weekplan_settings(deleted)")
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO weekplan_settings (id, planDays, shoppingDay, updatedAt, deleted, dirty)
                    VALUES ('global', 7, 0, 0, 0, 0)
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS weekplan_constraints (
                        id TEXT NOT NULL PRIMARY KEY,
                        maxMeatPerWeek INTEGER NOT NULL DEFAULT 3,
                        minVegetarianPerWeek INTEGER NOT NULL DEFAULT 2,
                        maxRepeatDays INTEGER NOT NULL DEFAULT 14,
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        deleted INTEGER NOT NULL DEFAULT 0,
                        dirty INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_weekplan_constraints_updatedAt ON weekplan_constraints(updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_weekplan_constraints_deleted ON weekplan_constraints(deleted)")
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO weekplan_constraints (id, maxMeatPerWeek, minVegetarianPerWeek, maxRepeatDays, updatedAt, deleted, dirty)
                    VALUES ('global', 3, 2, 14, 0, 0, 0)
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipes ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS weekplan_templates (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS weekplan_template_entries (
                        id TEXT NOT NULL PRIMARY KEY,
                        templateId TEXT NOT NULL DEFAULT '',
                        dayOffset INTEGER NOT NULL DEFAULT 0,
                        recipeId TEXT NOT NULL DEFAULT '',
                        position INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_weekplan_template_entries_templateId ON weekplan_template_entries(templateId)")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recipe_history (
                        id TEXT NOT NULL PRIMARY KEY,
                        recipeId TEXT NOT NULL,
                        plannedDate TEXT NOT NULL DEFAULT '',
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        deleted INTEGER NOT NULL DEFAULT 0,
                        dirty INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recipe_history_recipeId ON recipe_history(recipeId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recipe_history_plannedDate ON recipe_history(plannedDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recipe_history_updatedAt ON recipe_history(updatedAt)")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE weekplan_days ADD COLUMN isQuickDay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE weekplan_days ADD COLUMN isGuestDay INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recipe_feedback (
                        id TEXT NOT NULL PRIMARY KEY,
                        recipeId TEXT NOT NULL,
                        plannedDate TEXT NOT NULL DEFAULT '',
                        liked INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        deleted INTEGER NOT NULL DEFAULT 0,
                        dirty INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recipe_feedback_recipeId ON recipe_feedback(recipeId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recipe_feedback_plannedDate ON recipe_feedback(plannedDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recipe_feedback_updatedAt ON recipe_feedback(updatedAt)")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE weekplan_constraints ADD COLUMN maxFishPerWeek INTEGER NOT NULL DEFAULT 2")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipes ADD COLUMN personalNotes TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pantry_items (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL DEFAULT '',
                        quantity REAL NOT NULL DEFAULT 0.0,
                        unit TEXT NOT NULL DEFAULT '',
                        category TEXT NOT NULL DEFAULT '',
                        expiresAt TEXT NOT NULL DEFAULT '',
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        deleted INTEGER NOT NULL DEFAULT 0,
                        dirty INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pantry_items_updatedAt ON pantry_items(updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pantry_items_deleted ON pantry_items(deleted)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pantry_items_dirty ON pantry_items(dirty)")
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create off_products table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS off_products (
                        id TEXT NOT NULL PRIMARY KEY,
                        barcode TEXT NOT NULL,
                        name TEXT NOT NULL,
                        brand TEXT NOT NULL,
                        categories TEXT NOT NULL,
                        kcalPerUnit REAL NOT NULL,
                        proteins REAL NOT NULL,
                        fats REAL NOT NULL,
                        carbs REAL NOT NULL,
                        nutriScore TEXT NOT NULL,
                        nova INTEGER NOT NULL,
                        ecoScore TEXT NOT NULL,
                        allergenes TEXT NOT NULL,
                        additives TEXT NOT NULL,
                        isOrganic INTEGER NOT NULL,
                        vegan INTEGER NOT NULL,
                        vegetarian INTEGER NOT NULL,
                        imagePath TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        deleted INTEGER NOT NULL,
                        dirty INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_off_products_barcode ON off_products(barcode)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_off_products_updatedAt ON off_products(updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_off_products_deleted ON off_products(deleted)")

                // Add columns to shopping_items
                db.execSQL("ALTER TABLE shopping_items ADD COLUMN offBarcode TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE shopping_items ADD COLUMN offProductId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE shopping_items ADD COLUMN priceEstimate REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE shopping_items ADD COLUMN priceLastChecked INTEGER NOT NULL DEFAULT 0")

                // Add columns to pantry_items
                db.execSQL("ALTER TABLE pantry_items ADD COLUMN offBarcode TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE pantry_items ADD COLUMN offProductId TEXT NOT NULL DEFAULT ''")

                // Add column to recipe_ingredients
                db.execSQL("ALTER TABLE recipe_ingredients ADD COLUMN offBarcode TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add nutrition budget columns to weekplan_constraints
                db.execSQL("ALTER TABLE weekplan_constraints ADD COLUMN maxKcalPerPortion INTEGER NOT NULL DEFAULT 700")
                db.execSQL("ALTER TABLE weekplan_constraints ADD COLUMN minNutriScore TEXT NOT NULL DEFAULT 'c'")
                db.execSQL("ALTER TABLE weekplan_constraints ADD COLUMN preferOrganic INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE weekplan_constraints ADD COLUMN excludeAllergens TEXT NOT NULL DEFAULT '[]'")
            }
        }

        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Ensure off_products barcode index is unique (fix for devices with old schema)
                db.execSQL("DROP INDEX IF EXISTS index_off_products_barcode")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_off_products_barcode ON off_products(barcode)")
            }
        }

        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Rezept-Herkünfte je Einkaufsartikel (JSON-Liste von ItemOrigin)
                db.execSQL("ALTER TABLE shopping_items ADD COLUMN origins TEXT NOT NULL DEFAULT '[]'")
            }
        }

        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add mealSlot column to recipes table
                db.execSQL("ALTER TABLE recipes ADD COLUMN mealSlot TEXT NOT NULL DEFAULT 'other'")

                // Migrate existing mealType values to mealSlot
                db.execSQL("""
                    UPDATE recipes SET mealSlot = CASE
                        WHEN LOWER(mealType) IN ('frühstück', 'breakfast') THEN 'breakfast'
                        WHEN LOWER(mealType) IN ('mittag', 'lunch', 'mittagessen') THEN 'lunch'
                        WHEN LOWER(mealType) IN ('abendessen', 'dinner', 'hauptgericht', 'hauptspeise') THEN 'dinner'
                        WHEN LOWER(mealType) IN ('dessert', 'snack', 'beilage', 'side') THEN 'snack'
                        WHEN mealType = '' THEN 'other'
                        ELSE 'other'
                    END
                """.trimIndent())
            }
        }

        private val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Persönlicher Katalog: Favoriten-Flag auf OFF-Produkten
                db.execSQL("ALTER TABLE off_products ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")

                // Globales Zutat→Produkt-Mapping
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS ingredient_product_mappings (
                        id TEXT NOT NULL PRIMARY KEY,
                        ingredientName TEXT NOT NULL DEFAULT '',
                        offProductId TEXT NOT NULL DEFAULT '',
                        offBarcode TEXT NOT NULL DEFAULT '',
                        displayName TEXT NOT NULL DEFAULT '',
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        deleted INTEGER NOT NULL DEFAULT 0,
                        dirty INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_ingredient_product_mappings_ingredientName ON ingredient_product_mappings(ingredientName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ingredient_product_mappings_updatedAt ON ingredient_product_mappings(updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ingredient_product_mappings_deleted ON ingredient_product_mappings(deleted)")
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create product_prices table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS product_prices (
                        id TEXT NOT NULL PRIMARY KEY,
                        offProductId TEXT NOT NULL,
                        storeName TEXT NOT NULL,
                        currency TEXT NOT NULL,
                        price REAL NOT NULL,
                        unit TEXT NOT NULL,
                        lastCheckedAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        deleted INTEGER NOT NULL,
                        dirty INTEGER NOT NULL,
                        FOREIGN KEY (offProductId) REFERENCES off_products(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_product_prices_offProductId ON product_prices(offProductId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_product_prices_storeName ON product_prices(storeName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_product_prices_updatedAt ON product_prices(updatedAt)")
            }
        }

        private val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS product_purchases (
                        id TEXT NOT NULL PRIMARY KEY,
                        shopping_item_id TEXT NOT NULL DEFAULT '',
                        off_product_id TEXT NOT NULL,
                        quantity_purchased REAL NOT NULL DEFAULT 1.0,
                        price_paid REAL NOT NULL DEFAULT 0.0,
                        store_name TEXT NOT NULL DEFAULT '',
                        purchase_date INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL DEFAULT 0,
                        deleted INTEGER NOT NULL DEFAULT 0,
                        dirty INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_pp_shopping_item ON product_purchases(shopping_item_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_pp_off_product ON product_purchases(off_product_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_pp_off_product_date ON product_purchases(off_product_id, purchase_date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_pp_updated ON product_purchases(updated_at)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_pp_deleted ON product_purchases(deleted)")
            }
        }

        private val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS receipts (
                        id TEXT NOT NULL PRIMARY KEY,
                        storeId TEXT NOT NULL DEFAULT '',
                        storeName TEXT NOT NULL DEFAULT '',
                        shoppingListId TEXT NOT NULL DEFAULT '',
                        purchaseDate INTEGER NOT NULL DEFAULT 0,
                        totalAmount REAL NOT NULL DEFAULT 0.0,
                        currency TEXT NOT NULL DEFAULT 'EUR',
                        imagePath TEXT NOT NULL DEFAULT '',
                        localImageUri TEXT NOT NULL DEFAULT '',
                        rawOcrText TEXT NOT NULL DEFAULT '',
                        status TEXT NOT NULL DEFAULT 'scanned',
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        deleted INTEGER NOT NULL DEFAULT 0,
                        dirty INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS receipt_items (
                        id TEXT NOT NULL PRIMARY KEY,
                        receiptId TEXT NOT NULL,
                        position INTEGER NOT NULL DEFAULT 0,
                        rawText TEXT NOT NULL DEFAULT '',
                        name TEXT NOT NULL DEFAULT '',
                        quantity REAL NOT NULL DEFAULT 1.0,
                        unitPrice REAL NOT NULL DEFAULT 0.0,
                        totalPrice REAL NOT NULL DEFAULT 0.0,
                        matchedShoppingItemId TEXT NOT NULL DEFAULT '',
                        matchStatus TEXT NOT NULL DEFAULT '',
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        deleted INTEGER NOT NULL DEFAULT 0,
                        dirty INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (receiptId) REFERENCES receipts(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                // Index-Namen müssen Rooms Konvention (index_<tabelle>_<spalte>) folgen,
                // sonst schlägt die Schema-Validierung beim Öffnen fehl.
                db.execSQL("CREATE INDEX IF NOT EXISTS index_receipts_storeId ON receipts(storeId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_receipts_shoppingListId ON receipts(shoppingListId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_receipts_purchaseDate ON receipts(purchaseDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_receipts_updatedAt ON receipts(updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_receipts_deleted ON receipts(deleted)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_receipt_items_receiptId ON receipt_items(receiptId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_receipt_items_updatedAt ON receipt_items(updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_receipt_items_deleted ON receipt_items(deleted)")
            }
        }

        private val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS monthly_budgets (
                        id TEXT NOT NULL PRIMARY KEY,
                        amount REAL NOT NULL DEFAULT 0.0,
                        warnThreshold REAL NOT NULL DEFAULT 0.8,
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        deleted INTEGER NOT NULL DEFAULT 0,
                        dirty INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_monthly_budgets_updatedAt ON monthly_budgets(updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_monthly_budgets_deleted ON monthly_budgets(deleted)")
                // Lokaler Default mit dirty = 0 → wird erst beim ersten Bearbeiten gepusht.
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO monthly_budgets (id, amount, warnThreshold, updatedAt, deleted, dirty)
                    VALUES ('global', 0.0, 0.8, 0, 0, 0)
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Woher der Bon gelesen wurde (KI-Vision vs. On-Device-OCR) – für die
                // Anzeige im Bon-Detail. Rein lokal, daher kein DTO-/Sync-Feld.
                db.execSQL("ALTER TABLE receipts ADD COLUMN source TEXT NOT NULL DEFAULT 'on_device'")
            }
        }

        private val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Packungsgröße (aus OFF-Rohdaten geparst) + Flag für manuelle Korrektur.
                db.execSQL("ALTER TABLE off_products ADD COLUMN packageGrams REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE off_products ADD COLUMN packageGramsManual INTEGER NOT NULL DEFAULT 0")

                // Persönliche DB: Bon-Artikelname → OFF-Produkt (synchronisiert).
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS receipt_article_links (
                        id TEXT NOT NULL PRIMARY KEY,
                        normalizedName TEXT NOT NULL DEFAULT '',
                        displayName TEXT NOT NULL DEFAULT '',
                        offProductId TEXT NOT NULL DEFAULT '',
                        offBarcode TEXT NOT NULL DEFAULT '',
                        confirmed INTEGER NOT NULL DEFAULT 0,
                        confirmedAt INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        deleted INTEGER NOT NULL DEFAULT 0,
                        dirty INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_receipt_article_links_normalizedName ON receipt_article_links(normalizedName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_receipt_article_links_updatedAt ON receipt_article_links(updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_receipt_article_links_deleted ON receipt_article_links(deleted)")
            }
        }

        private val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Pro-Artikel-Nährwertzuordnung (Phase 1/2) wurde vollständig entfernt:
                // OFF-Produktsuche fand zwar Treffer, lieferte aber keine brauchbaren
                // Nährwerte. Ersetzt durch Nährwerte direkt am Rezept (manuell/KI).
                db.execSQL("DROP TABLE IF EXISTS receipt_article_links")

                // Rezept-Nährwerte, immer für eine feste Basis von 4 Portionen berechnet
                // (unabhängig von der Portionen-Anpassung im UI). source = "manual"/"ai"/"".
                db.execSQL("ALTER TABLE recipes ADD COLUMN nutritionKcal REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE recipes ADD COLUMN nutritionProtein REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE recipes ADD COLUMN nutritionFat REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE recipes ADD COLUMN nutritionCarbs REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE recipes ADD COLUMN nutritionNutriScore TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE recipes ADD COLUMN nutritionSource TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Unterscheidet "eingeplant" von "im Cooking-Mode tatsächlich gekocht"
                // (Fertig-Button), damit Stats und Wiederholungssperre die Realität abbilden.
                db.execSQL("ALTER TABLE recipe_history ADD COLUMN cooked INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29)
                .build()
    }
}
