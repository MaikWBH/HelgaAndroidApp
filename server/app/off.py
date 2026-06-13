import httpx
import json
from .db import get_db, now_ms
from .models import OffProductRecord


async def lookup_barcode(barcode: str) -> OffProductRecord:
    """Barcode-Lookup: lokal prüfen, dann OFF-API abfragen, cachen."""
    # 1. Lokal prüfen
    async with get_db() as db:
        async with db.execute(
            "SELECT * FROM off_products WHERE barcode = ? AND deleted = 0 LIMIT 1",
            (barcode,)
        ) as cursor:
            row = await cursor.fetchone()
            if row:
                return _row_to_product(row)

    # 2. OFF-API abfragen
    off_data = await _fetch_from_off(barcode)
    if not off_data:
        raise ValueError(f"Produkt {barcode} nicht in Open Food Facts gefunden")

    # 3. In Datenbank speichern
    product = OffProductRecord(
        id=barcode,
        barcode=barcode,
        name=off_data.get("product_name", ""),
        brand=off_data.get("brands", ""),
        categories=json.dumps(off_data.get("categories", []) if isinstance(off_data.get("categories"), list) else off_data.get("categories", "").split(",")),
        kcal_per_unit=off_data.get("nutriments", {}).get("energy-kcal_per_100g", 0.0),
        proteins=off_data.get("nutriments", {}).get("proteins_per_100g", 0.0),
        fats=off_data.get("nutriments", {}).get("fat_per_100g", 0.0),
        carbs=off_data.get("nutriments", {}).get("carbohydrates_per_100g", 0.0),
        nutri_score=off_data.get("nutriscore_grade", ""),
        nova=off_data.get("nova_group", 0),
        eco_score=off_data.get("ecoscore_grade", ""),
        allergenes=json.dumps(off_data.get("allergens", [])),
        additives=json.dumps(off_data.get("additives", [])),
        is_organic=1 if "organic" in off_data.get("labels", "").lower() else 0,
        vegan=1 if "vegan" in off_data.get("labels", "").lower() else 0,
        vegetarian=1 if "vegetarian" in off_data.get("labels", "").lower() else 0,
        image_path=off_data.get("image_url", ""),
        updated_at=now_ms(),
        deleted=0,
    )

    async with get_db() as db:
        await db.execute(
            """
            INSERT OR REPLACE INTO off_products (
                id, barcode, name, brand, categories, kcal_per_unit, proteins, fats, carbs,
                nutri_score, nova, eco_score, allergenes, additives, is_organic, vegan, vegetarian,
                image_path, updated_at, deleted
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                product.id, product.barcode, product.name, product.brand, product.categories,
                product.kcal_per_unit, product.proteins, product.fats, product.carbs,
                product.nutri_score, product.nova, product.eco_score, product.allergenes,
                product.additives, product.is_organic, product.vegan, product.vegetarian,
                product.image_path, product.updated_at, product.deleted
            )
        )
        await db.commit()

    return product


async def search_products(query: str, limit: int = 5) -> dict:
    """Text-Suche: lokal erst, dann OFF-API."""
    results = []

    # 1. Lokal suchen
    async with get_db() as db:
        pattern = f"%{query}%"
        async with db.execute(
            "SELECT * FROM off_products WHERE name LIKE ? AND deleted = 0 LIMIT ?",
            (pattern, limit)
        ) as cursor:
            rows = await cursor.fetchall()
            for row in rows:
                results.append(_row_to_product(row))

    # Falls nicht genug gefunden, OFF-API fragen
    if len(results) < limit:
        off_results = await _search_off_api(query, limit - len(results))
        for off_data in off_results:
            product = OffProductRecord(
                id=off_data.get("code"),
                barcode=off_data.get("code", ""),
                name=off_data.get("product_name", ""),
                brand=off_data.get("brands", ""),
                categories=json.dumps(off_data.get("categories", []) if isinstance(off_data.get("categories"), list) else off_data.get("categories", "").split(",")),
                kcal_per_unit=off_data.get("nutriments", {}).get("energy-kcal_per_100g", 0.0),
                proteins=off_data.get("nutriments", {}).get("proteins_per_100g", 0.0),
                fats=off_data.get("nutriments", {}).get("fat_per_100g", 0.0),
                carbs=off_data.get("nutriments", {}).get("carbohydrates_per_100g", 0.0),
                nutri_score=off_data.get("nutriscore_grade", ""),
                nova=off_data.get("nova_group", 0),
                eco_score=off_data.get("ecoscore_grade", ""),
                allergenes=json.dumps(off_data.get("allergens", [])),
                additives=json.dumps(off_data.get("additives", [])),
                is_organic=1 if "organic" in off_data.get("labels", "").lower() else 0,
                vegan=1 if "vegan" in off_data.get("labels", "").lower() else 0,
                vegetarian=1 if "vegetarian" in off_data.get("labels", "").lower() else 0,
                image_path=off_data.get("image_url", ""),
                updated_at=now_ms(),
                deleted=0,
            )
            results.append(product)

            # Cache in DB
            async with get_db() as db:
                await db.execute(
                    """
                    INSERT OR REPLACE INTO off_products (
                        id, barcode, name, brand, categories, kcal_per_unit, proteins, fats, carbs,
                        nutri_score, nova, eco_score, allergenes, additives, is_organic, vegan, vegetarian,
                        image_path, updated_at, deleted
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        product.id, product.barcode, product.name, product.brand, product.categories,
                        product.kcal_per_unit, product.proteins, product.fats, product.carbs,
                        product.nutri_score, product.nova, product.eco_score, product.allergenes,
                        product.additives, product.is_organic, product.vegan, product.vegetarian,
                        product.image_path, product.updated_at, product.deleted
                    )
                )
                await db.commit()

    return {"products": results}


async def _fetch_from_off(barcode: str) -> dict | None:
    """Fetch single product from Open Food Facts API."""
    async with httpx.AsyncClient(timeout=10) as client:
        try:
            url = f"https://world.openfoodfacts.org/api/v0/product/{barcode}.json"
            resp = await client.get(url)
            if resp.status_code == 200:
                data = resp.json()
                if data.get("status") == 1:  # Product found
                    return data.get("product", {})
        except Exception:
            pass
    return None


async def _search_off_api(query: str, limit: int = 5) -> list:
    """Search Open Food Facts API."""
    results = []
    async with httpx.AsyncClient(timeout=10) as client:
        try:
            url = "https://world.openfoodfacts.org/cgi/search.pl"
            params = {
                "search_terms": query,
                "search_simple": 1,
                "action": "process",
                "json": 1,
                "page_size": limit,
            }
            resp = await client.get(url, params=params)
            if resp.status_code == 200:
                data = resp.json()
                results = data.get("products", [])
        except Exception:
            pass
    return results


def _row_to_product(row) -> OffProductRecord:
    """Convert DB row to OffProductRecord."""
    return OffProductRecord(
        id=row["id"],
        barcode=row["barcode"],
        name=row["name"],
        brand=row["brand"],
        categories=row["categories"],
        kcal_per_unit=row["kcal_per_unit"],
        proteins=row["proteins"],
        fats=row["fats"],
        carbs=row["carbs"],
        nutri_score=row["nutri_score"],
        nova=row["nova"],
        eco_score=row["eco_score"],
        allergenes=row["allergenes"],
        additives=row["additives"],
        is_organic=row["is_organic"],
        vegan=row["vegan"],
        vegetarian=row["vegetarian"],
        image_path=row["image_path"],
        is_favorite=row["is_favorite"] if "is_favorite" in row.keys() else 0,
        updated_at=row["updated_at"],
        deleted=row["deleted"],
    )
