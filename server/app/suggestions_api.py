"""Vorschläge für Autocomplete (Phase 4, S6): /api/suggestions/items, /aisles, /units."""
from fastapi import APIRouter, Depends, Query

from .auth import require_auth
from .db import get_db

router = APIRouter(dependencies=[Depends(require_auth)])


def _escape_like(text: str) -> str:
    """Escaped LIKE-Platzhalter, damit die Suche Wildcards im Suchwort wörtlich nimmt."""
    return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")


@router.get("/api/suggestions/items")
async def suggest_items(q: str = Query(default="")):
    if not q:
        return {"suggestions": []}
    pattern = f"%{_escape_like(q)}%"
    results = []
    async with get_db() as db:
        async with db.execute(
            "SELECT DISTINCT food FROM recipe_ingredients WHERE food LIKE ? ESCAPE '\\' AND deleted = 0 "
            "UNION "
            "SELECT DISTINCT name FROM shopping_items WHERE name LIKE ? ESCAPE '\\' AND deleted = 0 "
            "LIMIT 20",
            (pattern, pattern),
        ) as cursor:
            rows = await cursor.fetchall()
            results = [row[0] for row in rows if row[0]]
    return {"suggestions": results}


@router.get("/api/suggestions/aisles")
async def suggest_aisle(item: str = Query(default="")):
    if not item:
        return {"aisle": ""}
    async with get_db() as db:
        async with db.execute(
            "SELECT aisle_name FROM aisle_products WHERE product_name = ? AND deleted = 0 LIMIT 1",
            (item,),
        ) as cursor:
            row = await cursor.fetchone()
    return {"aisle": row[0] if row else ""}


@router.get("/api/suggestions/units")
async def suggest_units(item: str = Query(default="")):
    if not item:
        return {"units": []}
    async with get_db() as db:
        async with db.execute(
            "SELECT unit_name FROM product_units WHERE product_name = ? AND deleted = 0 "
            "ORDER BY sort_order LIMIT 5",
            (item,),
        ) as cursor:
            rows = await cursor.fetchall()
    return {"units": [r[0] for r in rows]}
