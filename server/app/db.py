import os
import time
from contextlib import asynccontextmanager
import aiosqlite
from dotenv import load_dotenv

load_dotenv()

DB_PATH = os.getenv("DB_PATH", "data/recipes.db")


def now_ms() -> int:
    return int(time.time() * 1000)


# Alle sync-fähigen Tabellen in Reihenfolge (Fremdschlüssel-Abhängigkeiten beachten)
SYNC_TABLES = [
    "recipes",
    "recipe_ingredients",
    "recipe_instructions",
    "recipe_tags",
    "recipe_categories",
    "foods",
    "units",
    "product_units",
    "stores",
    "store_aisles",
    "aisle_products",
    "shopping_lists",
    "shopping_items",
    "shopping_list_staples",
    "weekplan_days",
    "weekplan_recipes",
    "weekplan_extras",
    "recipe_history",
    "quick_emojis",
    "app_settings",
    "weekplan_settings",
    "weekplan_constraints",
    "off_products",
]

SCHEMA = """
PRAGMA journal_mode=WAL;
PRAGMA foreign_keys=ON;

CREATE TABLE IF NOT EXISTS recipes (
    id          TEXT PRIMARY KEY,
    slug        TEXT NOT NULL DEFAULT '',
    name        TEXT NOT NULL DEFAULT '',
    description TEXT DEFAULT '',
    recipe_yield TEXT DEFAULT '',
    prep_time   TEXT DEFAULT '',
    cook_time   TEXT DEFAULT '',
    total_time  TEXT DEFAULT '',
    image_path  TEXT DEFAULT '',
    source_url  TEXT DEFAULT '',
    rating      INTEGER DEFAULT 0,
    protein_type TEXT DEFAULT '',
    effort      TEXT DEFAULT '',
    cuisine     TEXT DEFAULT '',
    meal_type   TEXT DEFAULT '',
    season_fit  TEXT DEFAULT '',
    created_at  INTEGER NOT NULL DEFAULT 0,
    updated_at  INTEGER NOT NULL DEFAULT 0,
    deleted     INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS recipe_ingredients (
    id          TEXT PRIMARY KEY,
    recipe_id   TEXT NOT NULL,
    position    INTEGER DEFAULT 0,
    quantity    REAL DEFAULT 0,
    unit        TEXT DEFAULT '',
    food        TEXT DEFAULT '',
    note        TEXT DEFAULT '',
    off_barcode TEXT NOT NULL DEFAULT '',
    updated_at  INTEGER NOT NULL DEFAULT 0,
    deleted     INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS recipe_instructions (
    id          TEXT PRIMARY KEY,
    recipe_id   TEXT NOT NULL,
    position    INTEGER DEFAULT 0,
    text        TEXT NOT NULL DEFAULT '',
    updated_at  INTEGER NOT NULL DEFAULT 0,
    deleted     INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS recipe_tags (
    id          TEXT PRIMARY KEY,
    recipe_id   TEXT NOT NULL,
    name        TEXT NOT NULL DEFAULT '',
    updated_at  INTEGER NOT NULL DEFAULT 0,
    deleted     INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS recipe_categories (
    id          TEXT PRIMARY KEY,
    recipe_id   TEXT NOT NULL,
    name        TEXT NOT NULL DEFAULT '',
    updated_at  INTEGER NOT NULL DEFAULT 0,
    deleted     INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS foods (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL DEFAULT '',
    updated_at  INTEGER NOT NULL DEFAULT 0,
    deleted     INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS units (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL DEFAULT '',
    updated_at  INTEGER NOT NULL DEFAULT 0,
    deleted     INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS product_units (
    id           TEXT PRIMARY KEY,
    product_name TEXT NOT NULL DEFAULT '',
    unit_name    TEXT NOT NULL DEFAULT '',
    sort_order   INTEGER DEFAULT 0,
    updated_at   INTEGER NOT NULL DEFAULT 0,
    deleted      INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS stores (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL DEFAULT '',
    is_active   INTEGER DEFAULT 0,
    updated_at  INTEGER NOT NULL DEFAULT 0,
    deleted     INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS store_aisles (
    id          TEXT PRIMARY KEY,
    store_id    TEXT NOT NULL,
    aisle_name  TEXT NOT NULL DEFAULT '',
    sort_order  INTEGER DEFAULT 0,
    updated_at  INTEGER NOT NULL DEFAULT 0,
    deleted     INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS aisle_products (
    id           TEXT PRIMARY KEY,
    aisle_name   TEXT NOT NULL DEFAULT '',
    product_name TEXT NOT NULL DEFAULT '',
    store_id     TEXT DEFAULT '',
    updated_at   INTEGER NOT NULL DEFAULT 0,
    deleted      INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS shopping_lists (
    id                   TEXT PRIMARY KEY,
    name                 TEXT NOT NULL DEFAULT '',
    is_active            INTEGER DEFAULT 0,
    is_default_weekplan  INTEGER DEFAULT 0,
    is_default_recipe    INTEGER DEFAULT 0,
    updated_at           INTEGER NOT NULL DEFAULT 0,
    deleted              INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS shopping_items (
    id                  TEXT PRIMARY KEY,
    list_id             TEXT NOT NULL,
    name                TEXT NOT NULL DEFAULT '',
    quantity            REAL DEFAULT 1,
    unit                TEXT DEFAULT '',
    aisle               TEXT DEFAULT '',
    source              TEXT DEFAULT 'manual',
    is_checked          INTEGER DEFAULT 0,
    sort_order          INTEGER DEFAULT 0,
    origins             TEXT NOT NULL DEFAULT '[]',
    off_barcode         TEXT NOT NULL DEFAULT '',
    off_product_id      TEXT NOT NULL DEFAULT '',
    price_estimate      REAL NOT NULL DEFAULT 0.0,
    price_last_checked  INTEGER NOT NULL DEFAULT 0,
    updated_at          INTEGER NOT NULL DEFAULT 0,
    deleted             INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS shopping_list_staples (
    id          TEXT PRIMARY KEY,
    list_id     TEXT NOT NULL,
    name        TEXT NOT NULL DEFAULT '',
    quantity    REAL DEFAULT 1,
    sort_order  INTEGER DEFAULT 0,
    updated_at  INTEGER NOT NULL DEFAULT 0,
    deleted     INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS weekplan_days (
    id          TEXT PRIMARY KEY,
    plan_date   TEXT NOT NULL DEFAULT '',
    note        TEXT DEFAULT '',
    updated_at  INTEGER NOT NULL DEFAULT 0,
    deleted     INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS weekplan_recipes (
    id              TEXT PRIMARY KEY,
    weekplan_day_id TEXT NOT NULL,
    recipe_id       TEXT NOT NULL,
    position        INTEGER DEFAULT 0,
    updated_at      INTEGER NOT NULL DEFAULT 0,
    deleted         INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS weekplan_extras (
    id              TEXT PRIMARY KEY,
    weekplan_day_id TEXT NOT NULL,
    item_text       TEXT NOT NULL DEFAULT '',
    position        INTEGER DEFAULT 0,
    updated_at      INTEGER NOT NULL DEFAULT 0,
    deleted         INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS recipe_history (
    id           TEXT PRIMARY KEY,
    recipe_id    TEXT NOT NULL,
    planned_date TEXT NOT NULL DEFAULT '',
    updated_at   INTEGER NOT NULL DEFAULT 0,
    deleted      INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS quick_emojis (
    id          TEXT PRIMARY KEY,
    emoji       TEXT NOT NULL DEFAULT '',
    food        TEXT NOT NULL DEFAULT '',
    quantity    REAL DEFAULT 1,
    unit        TEXT DEFAULT '',
    sort_order  INTEGER DEFAULT 0,
    updated_at  INTEGER NOT NULL DEFAULT 0,
    deleted     INTEGER NOT NULL DEFAULT 0
);

-- Key-Value Einstellungen (id = key)
CREATE TABLE IF NOT EXISTS app_settings (
    id          TEXT PRIMARY KEY,
    value       TEXT NOT NULL DEFAULT '',
    updated_at  INTEGER NOT NULL DEFAULT 0,
    deleted     INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS weekplan_settings (
    id           TEXT PRIMARY KEY,
    plan_days    INTEGER NOT NULL DEFAULT 7,
    shopping_day INTEGER NOT NULL DEFAULT 0,
    updated_at   INTEGER NOT NULL DEFAULT 0,
    deleted      INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS weekplan_constraints (
    id                       TEXT PRIMARY KEY,
    max_meat_per_week        INTEGER NOT NULL DEFAULT 3,
    min_vegetarian_per_week  INTEGER NOT NULL DEFAULT 2,
    max_repeat_days          INTEGER NOT NULL DEFAULT 14,
    updated_at               INTEGER NOT NULL DEFAULT 0,
    deleted                  INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS off_products (
    id                  TEXT PRIMARY KEY,
    barcode             TEXT NOT NULL UNIQUE,
    name                TEXT NOT NULL DEFAULT '',
    brand               TEXT NOT NULL DEFAULT '',
    categories          TEXT NOT NULL DEFAULT '[]',
    kcal_per_unit       REAL NOT NULL DEFAULT 0.0,
    proteins            REAL NOT NULL DEFAULT 0.0,
    fats                REAL NOT NULL DEFAULT 0.0,
    carbs               REAL NOT NULL DEFAULT 0.0,
    nutri_score         TEXT NOT NULL DEFAULT '',
    nova                INTEGER NOT NULL DEFAULT 0,
    eco_score           TEXT NOT NULL DEFAULT '',
    allergenes          TEXT NOT NULL DEFAULT '[]',
    additives           TEXT NOT NULL DEFAULT '[]',
    is_organic          INTEGER NOT NULL DEFAULT 0,
    vegan               INTEGER NOT NULL DEFAULT 0,
    vegetarian          INTEGER NOT NULL DEFAULT 0,
    image_path          TEXT NOT NULL DEFAULT '',
    updated_at          INTEGER NOT NULL DEFAULT 0,
    deleted             INTEGER NOT NULL DEFAULT 0
);
"""

INDICES = [
    "CREATE INDEX IF NOT EXISTS idx_recipes_updated ON recipes(updated_at)",
    "CREATE INDEX IF NOT EXISTS idx_recipe_ingredients_recipe ON recipe_ingredients(recipe_id)",
    "CREATE INDEX IF NOT EXISTS idx_recipe_ingredients_updated ON recipe_ingredients(updated_at)",
    "CREATE INDEX IF NOT EXISTS idx_recipe_instructions_recipe ON recipe_instructions(recipe_id)",
    "CREATE INDEX IF NOT EXISTS idx_recipe_tags_recipe ON recipe_tags(recipe_id)",
    "CREATE INDEX IF NOT EXISTS idx_recipe_categories_recipe ON recipe_categories(recipe_id)",
    "CREATE INDEX IF NOT EXISTS idx_shopping_items_list ON shopping_items(list_id)",
    "CREATE INDEX IF NOT EXISTS idx_shopping_items_updated ON shopping_items(updated_at)",
    "CREATE INDEX IF NOT EXISTS idx_store_aisles_store ON store_aisles(store_id)",
    "CREATE INDEX IF NOT EXISTS idx_aisle_products_name ON aisle_products(product_name)",
    "CREATE INDEX IF NOT EXISTS idx_weekplan_days_date ON weekplan_days(plan_date)",
    "CREATE INDEX IF NOT EXISTS idx_weekplan_recipes_day ON weekplan_recipes(weekplan_day_id)",
    "CREATE INDEX IF NOT EXISTS idx_weekplan_extras_day ON weekplan_extras(weekplan_day_id)",
    "CREATE INDEX IF NOT EXISTS idx_recipe_history_recipe ON recipe_history(recipe_id)",
    "CREATE INDEX IF NOT EXISTS idx_weekplan_settings_updated ON weekplan_settings(updated_at)",
    "CREATE INDEX IF NOT EXISTS idx_weekplan_constraints_updated ON weekplan_constraints(updated_at)",
    "CREATE INDEX IF NOT EXISTS idx_off_products_barcode ON off_products(barcode)",
    "CREATE INDEX IF NOT EXISTS idx_off_products_updated ON off_products(updated_at)",
]


# Spalten, die nach dem ersten Release ergänzt wurden. init_db() fügt sie
# non-destruktiv zu bestehenden DBs hinzu (ALTER TABLE ... ADD COLUMN), da
# "CREATE TABLE IF NOT EXISTS" bestehende Tabellen nicht verändert.
ADDED_COLUMNS = {
    "shopping_items": [
        ("origins", "TEXT NOT NULL DEFAULT '[]'"),
        ("off_barcode", "TEXT NOT NULL DEFAULT ''"),
        ("off_product_id", "TEXT NOT NULL DEFAULT ''"),
        ("price_estimate", "REAL NOT NULL DEFAULT 0.0"),
        ("price_last_checked", "INTEGER NOT NULL DEFAULT 0"),
    ],
    "recipe_ingredients": [
        ("off_barcode", "TEXT NOT NULL DEFAULT ''"),
    ],
}


@asynccontextmanager
async def get_db():
    async with aiosqlite.connect(DB_PATH) as db:
        db.row_factory = aiosqlite.Row
        await db.execute("PRAGMA journal_mode=WAL")
        await db.execute("PRAGMA foreign_keys=ON")
        yield db


async def _ensure_columns(db):
    for table, columns in ADDED_COLUMNS.items():
        async with db.execute(f"PRAGMA table_info({table})") as cursor:
            existing = {row[1] for row in await cursor.fetchall()}
        for name, ddl in columns:
            if name not in existing:
                await db.execute(f"ALTER TABLE {table} ADD COLUMN {name} {ddl}")


async def init_db():
    os.makedirs(os.path.dirname(DB_PATH) if os.path.dirname(DB_PATH) else ".", exist_ok=True)
    async with aiosqlite.connect(DB_PATH) as db:
        await db.executescript(SCHEMA)
        await _ensure_columns(db)
        for idx in INDICES:
            await db.execute(idx)
        await db.commit()
