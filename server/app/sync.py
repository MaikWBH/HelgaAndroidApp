from typing import Any, Dict, List
import aiosqlite

from .db import get_db, now_ms, SYNC_TABLES
from .models import SyncPayload, SyncPullResponse, SyncPushRequest

# Spalten pro Tabelle (muss mit Schema in db.py übereinstimmen)
TABLE_COLUMNS: Dict[str, List[str]] = {
    "recipes": [
        "id", "slug", "name", "description", "recipe_yield", "prep_time",
        "cook_time", "total_time", "image_path", "source_url", "rating",
        "protein_type", "effort", "cuisine", "meal_type", "season_fit",
        "created_at", "updated_at", "deleted",
    ],
    "recipe_ingredients": [
        "id", "recipe_id", "position", "quantity", "unit", "food", "note",
        "updated_at", "deleted",
    ],
    "recipe_instructions": [
        "id", "recipe_id", "position", "text", "updated_at", "deleted",
    ],
    "recipe_tags": [
        "id", "recipe_id", "name", "updated_at", "deleted",
    ],
    "recipe_categories": [
        "id", "recipe_id", "name", "updated_at", "deleted",
    ],
    "foods": ["id", "name", "updated_at", "deleted"],
    "units": ["id", "name", "updated_at", "deleted"],
    "product_units": [
        "id", "product_name", "unit_name", "sort_order", "updated_at", "deleted",
    ],
    "stores": ["id", "name", "is_active", "updated_at", "deleted"],
    "store_aisles": [
        "id", "store_id", "aisle_name", "sort_order", "updated_at", "deleted",
    ],
    "aisle_products": [
        "id", "aisle_name", "product_name", "store_id", "updated_at", "deleted",
    ],
    "shopping_lists": [
        "id", "name", "is_active", "is_default_weekplan", "is_default_recipe",
        "updated_at", "deleted",
    ],
    "shopping_items": [
        "id", "list_id", "name", "quantity", "unit", "aisle", "source",
        "is_checked", "sort_order", "updated_at", "deleted",
    ],
    "shopping_list_staples": [
        "id", "list_id", "name", "quantity", "sort_order", "updated_at", "deleted",
    ],
    "weekplan_days": ["id", "plan_date", "note", "updated_at", "deleted"],
    "weekplan_recipes": [
        "id", "weekplan_day_id", "recipe_id", "position", "updated_at", "deleted",
    ],
    "weekplan_extras": [
        "id", "weekplan_day_id", "item_text", "position", "updated_at", "deleted",
    ],
    "recipe_history": [
        "id", "recipe_id", "planned_date", "updated_at", "deleted",
    ],
    "quick_emojis": [
        "id", "emoji", "food", "quantity", "unit", "sort_order",
        "updated_at", "deleted",
    ],
    "app_settings": ["id", "value", "updated_at", "deleted"],
    "weekplan_settings": ["id", "plan_days", "shopping_day", "updated_at", "deleted"],
    "weekplan_constraints": [
        "id", "max_meat_per_week", "min_vegetarian_per_week", "max_repeat_days",
        "updated_at", "deleted",
    ],
}

# Mapping Tabellenname → Feldname im SyncPayload
PAYLOAD_FIELD = {
    "recipes": "recipes",
    "recipe_ingredients": "recipe_ingredients",
    "recipe_instructions": "recipe_instructions",
    "recipe_tags": "recipe_tags",
    "recipe_categories": "recipe_categories",
    "foods": "foods",
    "units": "units",
    "product_units": "product_units",
    "stores": "stores",
    "store_aisles": "store_aisles",
    "aisle_products": "aisle_products",
    "shopping_lists": "shopping_lists",
    "shopping_items": "shopping_items",
    "shopping_list_staples": "shopping_list_staples",
    "weekplan_days": "weekplan_days",
    "weekplan_recipes": "weekplan_recipes",
    "weekplan_extras": "weekplan_extras",
    "recipe_history": "recipe_history",
    "quick_emojis": "quick_emojis",
    "app_settings": "app_settings",
    "weekplan_settings": "weekplan_settings",
    "weekplan_constraints": "weekplan_constraints",
}


async def pull_since(since_ts: int) -> SyncPullResponse:
    """Gibt alle Datensätze zurück, die nach since_ts geändert wurden."""
    server_ts = now_ms()
    result: Dict[str, List[Dict]] = {t: [] for t in SYNC_TABLES}

    async with get_db() as db:
        for table in SYNC_TABLES:
            cols = TABLE_COLUMNS[table]
            col_list = ", ".join(cols)
            async with db.execute(
                f"SELECT {col_list} FROM {table} WHERE updated_at > ?",
                (since_ts,),
            ) as cursor:
                rows = await cursor.fetchall()
                result[table] = [
                    {k: v for k, v in zip(cols, row) if v is not None}
                    for row in rows
                ]

    return SyncPullResponse(server_ts=server_ts, **result)


async def push_records(payload: SyncPushRequest) -> SyncPullResponse:
    """
    Upsert aller Client-Datensätze nach LWW.
    Gibt die serverseitig gewonnenen Records zurück (client muss diese übernehmen).
    """
    server_ts = now_ms()
    server_wins: Dict[str, List[Dict]] = {t: [] for t in SYNC_TABLES}

    async with get_db() as db:
        for table in SYNC_TABLES:
            field = PAYLOAD_FIELD[table]
            client_records: List[Any] = getattr(payload, field, [])
            if not client_records:
                continue

            cols = TABLE_COLUMNS[table]

            for record in client_records:
                rec_dict = record.model_dump()

                # Serverseitigen Stand laden
                async with db.execute(
                    f"SELECT updated_at FROM {table} WHERE id = ?",
                    (rec_dict["id"],),
                ) as cursor:
                    existing = await cursor.fetchone()

                if existing and (existing[0] or 0) > rec_dict["updated_at"]:
                    # Server ist neuer → client muss den Server-Stand übernehmen
                    async with db.execute(
                        f"SELECT {', '.join(cols)} FROM {table} WHERE id = ?",
                        (rec_dict["id"],),
                    ) as cursor:
                        row = await cursor.fetchone()
                        if row:
                            server_wins[table].append(dict(zip(cols, row)))
                else:
                    # Client ist neuer oder Record ist neu → upsert
                    placeholders = ", ".join("?" * len(cols))
                    update_set = ", ".join(
                        f"{c} = excluded.{c}" for c in cols if c != "id"
                    )
                    values = [rec_dict.get(c) for c in cols]
                    await db.execute(
                        f"INSERT INTO {table} ({', '.join(cols)}) VALUES ({placeholders}) "
                        f"ON CONFLICT(id) DO UPDATE SET {update_set}",
                        values,
                    )

        await db.commit()

    return SyncPullResponse(server_ts=server_ts, **server_wins)
