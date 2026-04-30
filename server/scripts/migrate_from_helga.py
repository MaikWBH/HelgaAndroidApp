"""
Einmalige Migration: Helga-SQLite → HelgaSyncServer-SQLite

Aufruf:
    python scripts/migrate_from_helga.py --src /pfad/zu/helga/data/recipes.db

Das Skript ist idempotent (kann mehrfach ausgeführt werden, überschreibt
nur neuere Einträge via LWW).
"""

import argparse
import sqlite3
import sys
import time
import uuid
from pathlib import Path


def now_ms() -> int:
    return int(time.time() * 1000)


def to_ms(text_dt: str | None) -> int:
    """Konvertiert SQLite-Textdatum ('2024-01-15 10:30:00') in Unix-ms."""
    if not text_dt:
        return 0
    import datetime
    for fmt in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%dT%H:%M:%S", "%Y-%m-%d"):
        try:
            dt = datetime.datetime.strptime(text_dt.split(".")[0], fmt)
            return int(dt.timestamp() * 1000)
        except ValueError:
            continue
    return 0


def ensure_id(val) -> str:
    return str(val) if val else str(uuid.uuid4())


def migrate(src_path: str, dst_path: str):
    if not Path(src_path).exists():
        print(f"FEHLER: Quelldatenbank nicht gefunden: {src_path}")
        sys.exit(1)

    print(f"Migriere: {src_path} → {dst_path}")
    src = sqlite3.connect(src_path)
    src.row_factory = sqlite3.Row
    dst = sqlite3.connect(dst_path)
    dst.execute("PRAGMA journal_mode=WAL")
    dst.execute("PRAGMA foreign_keys=OFF")  # während Migration deaktivieren

    ts = now_ms()
    migrated = {}

    # ── Rezepte ────────────────────────────────────────────────────────────
    recipes = src.execute("SELECT * FROM recipes").fetchall()
    for r in recipes:
        dst.execute(
            """INSERT INTO recipes (id, slug, name, description, recipe_yield,
               prep_time, cook_time, total_time, image_path, source_url,
               rating, created_at, updated_at, deleted)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,0)
               ON CONFLICT(id) DO UPDATE SET
               name=excluded.name, updated_at=excluded.updated_at""",
            (
                ensure_id(r["id"]),
                r["slug"] or "",
                r["name"] or "",
                r["description"] or "",
                r["recipe_yield"] or "",
                r["prep_time"] or "",
                r["cook_time"] or "",
                r["total_time"] or "",
                r["image_path"] or "",
                r["source_url"] or "",
                r["rating"] if "rating" in r.keys() else 0,
                to_ms(r["created_at"]) if "created_at" in r.keys() else ts,
                to_ms(r["updated_at"]) or ts,
            ),
        )
    migrated["recipes"] = len(recipes)

    # ── Klassifikations-Felder nachladen (falls vorhanden) ─────────────────
    try:
        classify_rows = src.execute(
            "SELECT recipe_id, protein_type, effort, cuisine, meal_type, season_fit "
            "FROM recipe_classifications"
        ).fetchall()
        for c in classify_rows:
            dst.execute(
                "UPDATE recipes SET protein_type=?, effort=?, cuisine=?, meal_type=?, season_fit=? "
                "WHERE id=?",
                (c["protein_type"] or "", c["effort"] or "", c["cuisine"] or "",
                 c["meal_type"] or "", c["season_fit"] or "", ensure_id(c["recipe_id"])),
            )
    except sqlite3.OperationalError:
        pass  # Tabelle existiert ggf. nicht in älteren Helga-Versionen

    # Klassifikation direkt aus recipes-Tabelle falls Felder vorhanden
    try:
        rows = src.execute(
            "SELECT id, protein_type, effort, cuisine, meal_type, season_fit FROM recipes"
        ).fetchall()
        for r in rows:
            dst.execute(
                "UPDATE recipes SET protein_type=?, effort=?, cuisine=?, meal_type=?, season_fit=? "
                "WHERE id=?",
                (r["protein_type"] or "", r["effort"] or "", r["cuisine"] or "",
                 r["meal_type"] or "", r["season_fit"] or "", ensure_id(r["id"])),
            )
    except sqlite3.OperationalError:
        pass

    # ── Zutaten ────────────────────────────────────────────────────────────
    ingredients = src.execute("SELECT * FROM recipe_ingredients").fetchall()
    for i in ingredients:
        dst.execute(
            """INSERT INTO recipe_ingredients
               (id, recipe_id, position, quantity, unit, food, note, updated_at, deleted)
               VALUES (?,?,?,?,?,?,?,?,0)
               ON CONFLICT(id) DO NOTHING""",
            (
                ensure_id(i["id"]),
                ensure_id(i["recipe_id"]),
                i["position"] if "position" in i.keys() else 0,
                float(i["quantity"]) if i["quantity"] else 0,
                i["unit"] or "",
                i["food"] or "",
                i["note"] or "",
                ts,
            ),
        )
    migrated["recipe_ingredients"] = len(ingredients)

    # ── Anweisungen ────────────────────────────────────────────────────────
    instructions = src.execute("SELECT * FROM recipe_instructions").fetchall()
    for i in instructions:
        dst.execute(
            """INSERT INTO recipe_instructions (id, recipe_id, position, text, updated_at, deleted)
               VALUES (?,?,?,?,?,0) ON CONFLICT(id) DO NOTHING""",
            (
                ensure_id(i["id"]),
                ensure_id(i["recipe_id"]),
                i["position"] if "position" in i.keys() else 0,
                i["text"] or "",
                ts,
            ),
        )
    migrated["recipe_instructions"] = len(instructions)

    # ── Tags ───────────────────────────────────────────────────────────────
    tags = src.execute("SELECT * FROM recipe_tags").fetchall()
    for t in tags:
        dst.execute(
            """INSERT INTO recipe_tags (id, recipe_id, name, updated_at, deleted)
               VALUES (?,?,?,?,0) ON CONFLICT(id) DO NOTHING""",
            (ensure_id(t["id"]), ensure_id(t["recipe_id"]), t["name"] or "", ts),
        )
    migrated["recipe_tags"] = len(tags)

    # ── Kategorien ─────────────────────────────────────────────────────────
    try:
        cats = src.execute("SELECT * FROM recipe_categories").fetchall()
        for c in cats:
            dst.execute(
                """INSERT INTO recipe_categories (id, recipe_id, name, updated_at, deleted)
                   VALUES (?,?,?,?,0) ON CONFLICT(id) DO NOTHING""",
                (ensure_id(c["id"]), ensure_id(c["recipe_id"]), c["name"] or "", ts),
            )
        migrated["recipe_categories"] = len(cats)
    except sqlite3.OperationalError:
        migrated["recipe_categories"] = 0

    # ── Läden ──────────────────────────────────────────────────────────────
    try:
        stores = src.execute("SELECT * FROM stores").fetchall()
        for s in stores:
            dst.execute(
                """INSERT INTO stores (id, name, is_active, updated_at, deleted)
                   VALUES (?,?,?,?,0) ON CONFLICT(id) DO NOTHING""",
                (ensure_id(s["id"]), s["name"] or "", int(s["is_active"] or 0), ts),
            )
        migrated["stores"] = len(stores)

        aisles = src.execute("SELECT * FROM store_aisles").fetchall()
        for a in aisles:
            dst.execute(
                """INSERT INTO store_aisles (id, store_id, aisle_name, sort_order, updated_at, deleted)
                   VALUES (?,?,?,?,?,0) ON CONFLICT(id) DO NOTHING""",
                (
                    ensure_id(a["id"]),
                    ensure_id(a["store_id"]),
                    a["aisle_name"] or "",
                    a["sort_order"] or 0,
                    ts,
                ),
            )
        migrated["store_aisles"] = len(aisles)
    except sqlite3.OperationalError:
        migrated["stores"] = migrated["store_aisles"] = 0

    # ── Gang-Produkt-Zuordnungen ────────────────────────────────────────────
    try:
        ap = src.execute("SELECT * FROM aisle_products").fetchall()
        for a in ap:
            dst.execute(
                """INSERT INTO aisle_products (id, aisle_name, product_name, updated_at, deleted)
                   VALUES (?,?,?,?,0) ON CONFLICT(id) DO NOTHING""",
                (ensure_id(a["id"]), a["aisle_name"] or "", a["product_name"] or "", ts),
            )
        migrated["aisle_products"] = len(ap)
    except sqlite3.OperationalError:
        migrated["aisle_products"] = 0

    # ── Einkaufslisten ─────────────────────────────────────────────────────
    try:
        lists = src.execute("SELECT * FROM shopping_lists").fetchall()
        for l in lists:
            dst.execute(
                """INSERT INTO shopping_lists
                   (id, name, is_active, is_default_weekplan, is_default_recipe, updated_at, deleted)
                   VALUES (?,?,?,?,?,?,0) ON CONFLICT(id) DO NOTHING""",
                (
                    ensure_id(l["id"]),
                    l["name"] or "",
                    int(l["is_active"] or 0),
                    int(l["is_default_weekplan"] or 0),
                    int(l["is_default_recipe"] or 0),
                    to_ms(l["created_at"]) if "created_at" in l.keys() else ts,
                ),
            )
        migrated["shopping_lists"] = len(lists)

        items = src.execute("SELECT * FROM shopping_items").fetchall()
        for i in items:
            dst.execute(
                """INSERT INTO shopping_items
                   (id, list_id, name, quantity, unit, aisle, source, is_checked, sort_order, updated_at, deleted)
                   VALUES (?,?,?,?,?,?,?,?,?,?,0) ON CONFLICT(id) DO NOTHING""",
                (
                    ensure_id(i["id"]),
                    ensure_id(i["list_id"]),
                    i["name"] or "",
                    float(i["quantity"]) if i["quantity"] else 1,
                    i["unit"] or "",
                    i["aisle"] or "",
                    i["source"] or "manual",
                    int(i["is_checked"] or 0),
                    i["sort_order"] or 0,
                    to_ms(i["created_at"]) if "created_at" in i.keys() else ts,
                ),
            )
        migrated["shopping_items"] = len(items)
    except sqlite3.OperationalError as e:
        print(f"  Einkaufslisten übersprungen: {e}")
        migrated["shopping_lists"] = migrated["shopping_items"] = 0

    # ── Wochenplan ─────────────────────────────────────────────────────────
    try:
        days = src.execute("SELECT * FROM weekplan_days").fetchall()
        for d in days:
            dst.execute(
                """INSERT INTO weekplan_days (id, plan_date, note, updated_at, deleted)
                   VALUES (?,?,?,?,0) ON CONFLICT(id) DO NOTHING""",
                (
                    ensure_id(d["id"]),
                    d["plan_date"] or "",
                    d["note"] or "",
                    to_ms(d["updated_at"]) or ts,
                ),
            )

        wp_recipes = src.execute("SELECT * FROM weekplan_recipes").fetchall()
        for r in wp_recipes:
            dst.execute(
                """INSERT INTO weekplan_recipes (id, weekplan_day_id, recipe_id, position, updated_at, deleted)
                   VALUES (?,?,?,?,?,0) ON CONFLICT(id) DO NOTHING""",
                (
                    ensure_id(r["id"]),
                    ensure_id(r["weekplan_day_id"]),
                    ensure_id(r["recipe_id"]),
                    r["position"] or 0,
                    ts,
                ),
            )

        extras = src.execute("SELECT * FROM weekplan_extras").fetchall()
        for e in extras:
            dst.execute(
                """INSERT INTO weekplan_extras (id, weekplan_day_id, item_text, position, updated_at, deleted)
                   VALUES (?,?,?,?,?,0) ON CONFLICT(id) DO NOTHING""",
                (
                    ensure_id(e["id"]),
                    ensure_id(e["weekplan_day_id"]),
                    e["item_text"] or "",
                    e["position"] or 0,
                    ts,
                ),
            )
        migrated["weekplan"] = len(days)
    except sqlite3.OperationalError as e:
        print(f"  Wochenplan übersprungen: {e}")
        migrated["weekplan"] = 0

    # ── Rezeptverlauf ──────────────────────────────────────────────────────
    try:
        history = src.execute("SELECT * FROM recipe_history").fetchall()
        for h in history:
            dst.execute(
                """INSERT INTO recipe_history (id, recipe_id, planned_date, updated_at, deleted)
                   VALUES (?,?,?,?,0) ON CONFLICT(id) DO NOTHING""",
                (ensure_id(h["id"]), ensure_id(h["recipe_id"]), h["planned_date"] or "", ts),
            )
        migrated["recipe_history"] = len(history)
    except sqlite3.OperationalError:
        migrated["recipe_history"] = 0

    # ── App-Einstellungen ──────────────────────────────────────────────────
    try:
        settings = src.execute("SELECT key, value FROM app_settings").fetchall()
        for s in settings:
            dst.execute(
                """INSERT INTO app_settings (id, value, updated_at, deleted)
                   VALUES (?,?,?,0) ON CONFLICT(id) DO UPDATE SET value=excluded.value""",
                (s["key"], s["value"] or "", ts),
            )
        migrated["app_settings"] = len(settings)
    except sqlite3.OperationalError:
        migrated["app_settings"] = 0

    dst.commit()
    dst.execute("PRAGMA foreign_keys=ON")
    src.close()
    dst.close()

    print("\nMigration abgeschlossen:")
    for k, v in migrated.items():
        print(f"  {k}: {v} Einträge")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Helga → HelgaSyncServer Migration")
    parser.add_argument("--src", required=True, help="Pfad zur Helga recipes.db")
    parser.add_argument(
        "--dst", default="data/recipes.db", help="Ziel-DB (default: data/recipes.db)"
    )
    args = parser.parse_args()
    migrate(args.src, args.dst)
