import aiosqlite

from app import db as app_db

from conftest import run


def _seed(sql_rows):
    async def go():
        async with aiosqlite.connect(app_db.DB_PATH) as db:
            for sql, params in sql_rows:
                await db.execute(sql, params)
            await db.commit()
    run(go())


def _seed_items():
    _seed([
        ("INSERT INTO recipe_ingredients (id, recipe_id, food, updated_at, deleted) VALUES (?, ?, ?, 1, 0)",
         ("r1", "rec1", "Tomate")),
        ("INSERT INTO recipe_ingredients (id, recipe_id, food, updated_at, deleted) VALUES (?, ?, ?, 1, 0)",
         ("r2", "rec1", "Oma-Ei")),
        ("INSERT INTO recipe_ingredients (id, recipe_id, food, updated_at, deleted) VALUES (?, ?, ?, 1, 0)",
         ("r3", "rec1", "100% Saft")),
        ("INSERT INTO shopping_items (id, list_id, name, updated_at, deleted) VALUES (?, ?, ?, 1, 0)",
         ("s1", "list1", "Tomatenmark")),
    ])


def test_items_wildcards_are_literal(client):
    _seed_items()
    # "oma" ist eine echte Teilzeichenkette von "Tomate" (T-oma-te): das ist korrekt, kein Wildcard-Effekt.
    found = client.get("/api/suggestions/items", params={"q": "oma"}).json()["suggestions"]
    assert set(found) == {"Oma-Ei", "Tomate", "Tomatenmark"}
    # Unterstrich ist ein LIKE-Platzhalter; ohne Escaping würde "t_mate" auf "Tomate" treffen.
    assert client.get("/api/suggestions/items", params={"q": "t_mate"}).json()["suggestions"] == []
    assert client.get("/api/suggestions/items", params={"q": "tom"}).json()["suggestions"] != []


def test_items_percent_matches_only_literal_percent(client):
    _seed_items()
    # Ohne Escaping würde "%" alles treffen; mit Escaping nur Namen mit echtem "%".
    assert client.get("/api/suggestions/items", params={"q": "%"}).json()["suggestions"] == ["100% Saft"]
    _seed([("INSERT INTO shopping_items (id, list_id, name, updated_at, deleted) VALUES (?, ?, ?, 1, 0)",
            ("s2", "list1", "Apfel"))])
    assert "Apfel" not in client.get("/api/suggestions/items", params={"q": "%"}).json()["suggestions"]


def test_items_empty_query(client):
    assert client.get("/api/suggestions/items").json() == {"suggestions": []}


def test_aisles_and_units_unchanged(client):
    _seed([
        ("INSERT INTO aisle_products (id, aisle_name, product_name, updated_at, deleted) VALUES (?, ?, ?, 1, 0)",
         ("a1", "Obst", "Tomate")),
        ("INSERT INTO product_units (id, product_name, unit_name, sort_order, updated_at, deleted) "
         "VALUES (?, ?, ?, 0, 1, 0)", ("u1", "Tomate", "Stück")),
    ])
    assert client.get("/api/suggestions/aisles", params={"item": "Tomate"}).json() == {"aisle": "Obst"}
    assert client.get("/api/suggestions/aisles", params={"item": "Unbekannt"}).json() == {"aisle": ""}
    assert client.get("/api/suggestions/aisles").json() == {"aisle": ""}
    assert client.get("/api/suggestions/units", params={"item": "Tomate"}).json() == {"units": ["Stück"]}
    assert client.get("/api/suggestions/units", params={"item": "Unbekannt"}).json() == {"units": []}
    assert client.get("/api/suggestions/units").json() == {"units": []}
