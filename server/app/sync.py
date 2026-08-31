from typing import Any, Dict, List
import aiosqlite

from .db import get_db, SYNC_TABLES
from .models import SyncPayload, SyncPullResponse, SyncPushRequest

# Spalten pro Tabelle (muss mit Schema in db.py übereinstimmen)
TABLE_COLUMNS: Dict[str, List[str]] = {
    "recipes": [
        "id", "slug", "name", "description", "recipe_yield", "prep_time",
        "cook_time", "total_time", "image_path", "source_url", "rating",
        "protein_type", "effort", "cuisine", "meal_type", "meal_slot", "season_fit",
        "nutrition_kcal", "nutrition_protein", "nutrition_fat", "nutrition_carbs",
        "nutrition_nutri_score", "nutrition_source", "last_servings",
        "created_at", "updated_at", "deleted",
    ],
    "recipe_ingredients": [
        "id", "recipe_id", "position", "quantity", "unit", "food", "note",
        "off_barcode", "updated_at", "deleted",
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
        "is_checked", "sort_order", "origins", "off_barcode", "off_product_id",
        "price_estimate", "price_last_checked", "updated_at", "deleted",
    ],
    "shopping_list_staples": [
        "id", "list_id", "name", "quantity", "sort_order", "updated_at", "deleted",
    ],
    "weekplan_days": [
        "id", "plan_date", "note", "is_quick_day", "is_guest_day", "is_skipped", "updated_at", "deleted",
    ],
    "weekplan_recipes": [
        "id", "weekplan_day_id", "recipe_id", "position", "updated_at", "deleted",
    ],
    "weekplan_extras": [
        "id", "weekplan_day_id", "item_text", "position", "updated_at", "deleted",
    ],
    "recipe_history": [
        "id", "recipe_id", "planned_date", "cooked", "updated_at", "deleted",
    ],
    "recipe_feedback": [
        "id", "recipe_id", "planned_date", "liked", "updated_at", "deleted",
    ],
    "quick_emojis": [
        "id", "emoji", "food", "quantity", "unit", "sort_order",
        "updated_at", "deleted",
    ],
    "weekplan_settings": ["id", "plan_days", "shopping_day", "updated_at", "deleted"],
    "weekplan_constraints": [
        "id", "max_meat_per_week", "min_vegetarian_per_week", "max_repeat_days",
        "updated_at", "deleted",
    ],
    "off_products": [
        "id", "barcode", "name", "brand", "categories", "kcal_per_unit",
        "proteins", "fats", "carbs", "nutri_score", "nova", "eco_score",
        "allergenes", "additives", "is_organic", "vegan", "vegetarian",
        "image_path", "is_favorite", "package_grams", "package_grams_manual",
        "updated_at", "deleted",
    ],
    # local_image_uri ist gerätelokal (absoluter Dateipfad) und wird bewusst NICHT
    # synchronisiert – sonst zirkuliert ein gerätespezifischer Pfad zwischen Clients.
    "receipts": [
        "id", "store_id", "store_name", "shopping_list_id", "purchase_date", "total_amount",
        "currency", "image_path", "raw_ocr_text", "status", "updated_at", "deleted",
    ],
    "receipt_items": [
        "id", "receipt_id", "position", "raw_text", "name", "quantity", "unit_price", "total_price",
        "matched_shopping_item_id", "match_status", "updated_at", "deleted",
    ],
    "monthly_budgets": [
        "id", "amount", "warn_threshold", "updated_at", "deleted",
    ],
}

# Mapping Tabellenname → Feldname im SyncPayload
PAYLOAD_FIELD = {
    "recipes": "recipes",
    "recipe_ingredients": "recipe_ingredients",
    "recipe_instructions": "recipe_instructions",
    "recipe_tags": "recipe_tags",
    "recipe_categories": "recipe_categories",
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
    "recipe_feedback": "recipe_feedback",
    "quick_emojis": "quick_emojis",
    "weekplan_settings": "weekplan_settings",
    "weekplan_constraints": "weekplan_constraints",
    "off_products": "off_products",
    "receipts": "receipts",
    "receipt_items": "receipt_items",
    "monthly_budgets": "monthly_budgets",
}


async def pull_since(since_seq: int) -> SyncPullResponse:
    """Gibt alle Datensätze zurück, deren server_seq > since_seq ist.

    Der Cursor (server_ts im Response) ist die globale Commit-Sequenz, NICHT die
    Wanduhr. Dadurch kann kein Datensatz mehr unsichtbar werden, nur weil sein
    updated_at (Client-Bearbeitungszeit) unter dem Cursor eines anderen Geräts
    liegt. Der Zähler wird zuerst gelesen, damit später committete Zeilen
    garantiert beim nächsten Pull (server_seq > Cursor) ausgeliefert werden.
    """
    result: Dict[str, List[Dict]] = {t: [] for t in SYNC_TABLES}

    async with get_db() as db:
        async with db.execute("SELECT seq FROM sync_state WHERE id = 0") as cursor:
            row = await cursor.fetchone()
            server_seq = row[0] if row else 0

        for table in SYNC_TABLES:
            cols = TABLE_COLUMNS[table]
            col_list = ", ".join(cols)
            async with db.execute(
                f"SELECT {col_list} FROM {table} WHERE server_seq > ?",
                (since_seq,),
            ) as cursor:
                rows = await cursor.fetchall()
                result[table] = [
                    {k: v for k, v in zip(cols, row) if v is not None}
                    for row in rows
                ]

    return SyncPullResponse(server_ts=server_seq, **result)


async def push_records(payload: SyncPushRequest) -> SyncPullResponse:
    """
    Upsert aller Client-Datensätze nach LWW (Vergleich über updated_at).
    Jeder akzeptierte Schreibvorgang erhält die neue Commit-Sequenz als
    server_seq, sodass andere Geräte ihn beim nächsten Pull garantiert erhalten.
    Gibt die serverseitig gewonnenen Records zurück (client muss diese übernehmen).
    """
    server_wins: Dict[str, List[Dict]] = {t: [] for t in SYNC_TABLES}

    async with get_db() as db:
        # Neue Commit-Sequenz reservieren (SQLite serialisiert Writer → eindeutig)
        await db.execute("UPDATE sync_state SET seq = seq + 1 WHERE id = 0")
        async with db.execute("SELECT seq FROM sync_state WHERE id = 0") as cursor:
            commit_seq = (await cursor.fetchone())[0]

        for table in SYNC_TABLES:
            field = PAYLOAD_FIELD[table]
            client_records: List[Any] = getattr(payload, field, [])
            if not client_records:
                continue

            cols = TABLE_COLUMNS[table]
            insert_cols = cols + ["server_seq"]

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
                    # Client ist neuer oder Record ist neu → upsert + server_seq stempeln
                    placeholders = ", ".join("?" * len(insert_cols))
                    update_set = ", ".join(
                        f"{c} = excluded.{c}" for c in insert_cols if c != "id"
                    )
                    values = [rec_dict.get(c) for c in cols] + [commit_seq]
                    await db.execute(
                        f"INSERT INTO {table} ({', '.join(insert_cols)}) VALUES ({placeholders}) "
                        f"ON CONFLICT(id) DO UPDATE SET {update_set}",
                        values,
                    )

        await db.commit()

    return SyncPullResponse(server_ts=commit_seq, **server_wins)
