package com.helga.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.helga.android.data.local.dao.QuickEmojiDao
import com.helga.android.data.local.dao.RecipeDao
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
import com.helga.android.data.local.entity.QuickEmojiEntity
import com.helga.android.data.local.entity.RecipeEntity
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

@Database(
    version = 8,
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

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .build()
    }
}
