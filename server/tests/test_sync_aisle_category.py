import sqlite3

from app import db as app_db

from conftest import run


def aisle(**extra):
    base = {"id": "a1", "store_id": "s1", "aisle_name": "Obst", "sort_order": 0, "updated_at": 100}
    base.update(extra)
    return base


def push(client, record):
    r = client.post("/api/sync", json={"store_aisles": [record]})
    assert r.status_code == 200, r.text
    return r.json()


def pulled(client):
    return client.get("/api/sync?since=0").json()["store_aisles"]


def test_category_key_round_trips(client):
    push(client, aisle(category_key="obst_gemuese"))
    assert pulled(client)[0]["category_key"] == "obst_gemuese"


def test_push_without_the_field_keeps_the_key(client):
    push(client, aisle(category_key="obst_gemuese"))
    push(client, aisle(aisle_name="Obst und Gemüse", updated_at=200))  # alter Client: Feld fehlt
    row = pulled(client)[0]
    assert row["aisle_name"] == "Obst und Gemüse"  # Umbenennung greift
    assert row["category_key"] == "obst_gemuese"    # Schlüssel bleibt


def test_explicit_empty_key_clears_it(client):
    push(client, aisle(category_key="obst_gemuese"))
    push(client, aisle(category_key="", updated_at=200))
    assert pulled(client)[0]["category_key"] == ""


def test_invalid_key_becomes_empty(client):
    push(client, aisle(category_key="Quatsch"))
    assert pulled(client)[0]["category_key"] == ""


def test_new_aisle_without_the_field_gets_the_default(client):
    push(client, aisle())
    assert pulled(client)[0]["category_key"] == ""


def test_backfill_for_old_default_aisles(db_path):
    con = sqlite3.connect(db_path)
    con.executescript(
        "CREATE TABLE store_aisles (id TEXT PRIMARY KEY, store_id TEXT NOT NULL, aisle_name TEXT NOT NULL DEFAULT '', "
        "sort_order INTEGER DEFAULT 0, updated_at INTEGER NOT NULL DEFAULT 0, deleted INTEGER NOT NULL DEFAULT 0);"
        "INSERT INTO store_aisles (id, store_id, aisle_name) VALUES ('1', 's', 'Obst & Gemüse'), "
        "('2', 's', 'Kasse'), ('3', 's', 'Drogerie & Haushalt');"
    )
    con.commit()
    con.close()
    run(app_db.init_db())
    con = sqlite3.connect(db_path)
    keys = dict(con.execute("SELECT id, category_key FROM store_aisles"))
    con.close()
    assert keys == {"1": "obst_gemuese", "2": "", "3": "drogerie"}
