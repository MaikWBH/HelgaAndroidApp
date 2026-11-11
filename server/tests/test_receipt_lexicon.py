import sqlite3

from app import db as app_db
from app import receipts

from conftest import run


def _item(item_id, raw, name, category="", **extra):
    base = {"id": item_id, "position": 0, "raw_text": raw, "name": name, "category": category,
            "quantity": 1, "unit": "stk", "unit_price": 1.0, "total_price": 1.0, "updated_at": 100}
    base.update(extra)
    return base


def _receipt(items, status, updated_at, store_name="REWE", **extra):
    base = {"id": "r1", "store_name": store_name, "purchase_day": "2026-11-10", "total_amount": 1.0,
            "status": status, "updated_at": updated_at, "items": items}
    base.update(extra)
    return base


def put(client, body):
    r = client.put(f"/api/receipts/{body['id']}", json=body)
    assert r.status_code == 200, r.text
    return r.json()


def learn_milk(client, store_name="REWE"):
    """Server-Stand 'H-Milch', dann bestätigt mit Korrektur -> lernt."""
    put(client, _receipt([_item("i1", "G&G H-MILCH", "H-Milch")], "review", 100, store_name))
    put(client, _receipt([_item("i1", "G&G H-MILCH", "Milch 3,5 %", "milch_eier", updated_at=200)],
                         "confirmed", 200, store_name))


def lexicon(client, query=""):
    return client.get(f"/api/receipts/lexicon{query}").json()["entries"]


def categories_rows(db_path):
    con = sqlite3.connect(db_path)
    try:
        return con.execute("SELECT product_key, category, source FROM product_categories").fetchall()
    finally:
        con.close()


def test_correction_is_learned(client, db_path):
    learn_milk(client)
    entries = lexicon(client)
    assert len(entries) == 1
    assert entries[0]["name"] == "Milch 3,5 %" and entries[0]["category"] == "milch_eier"
    assert entries[0]["hits"] == 1 and entries[0]["raw_text"] == "G&G H-MILCH"
    assert categories_rows(db_path) == [("milch 3,5 %", "milch_eier", "user")]


def test_unchanged_put_does_not_learn_again(client):
    learn_milk(client)
    put(client, _receipt([_item("i1", "G&G H-MILCH", "Milch 3,5 %", "milch_eier", updated_at=300)],
                         "confirmed", 300))
    assert lexicon(client)[0]["hits"] == 1


def test_confirm_without_previous_server_state_learns_nothing(client, db_path):
    put(client, _receipt([_item("i1", "G&G H-MILCH", "Milch", "milch_eier")], "confirmed", 100))
    assert lexicon(client) == []
    assert categories_rows(db_path) == []


def test_enrich_uses_lexicon_of_same_store_only(client, db_path):
    learn_milk(client, "REWE")

    async def run_enrich(store_name):
        receipt = {"store_id": "", "store_name": store_name}
        items = [_item("n1", "G&G H-MILCH", "H-Milch")]
        async with app_db.get_db() as db:
            await receipts.enrich(db, receipt, items)
            await db.commit()
        return items[0]

    same = run(run_enrich("REWE"))
    assert same["name"] == "Milch 3,5 %" and same["category"] == "milch_eier"
    other = run(run_enrich("Aldi"))
    assert other["name"] == "H-Milch" and other["category"] == ""
    assert lexicon(client)[0]["hits"] == 2


def test_enrich_fills_empty_category_from_product_categories(client, db_path):
    learn_milk(client)

    async def run_enrich():
        items = [_item("n1", "ANDERES KUERZEL", "Milch 3,5 %")]
        async with app_db.get_db() as db:
            await receipts.enrich(db, {"store_id": "", "store_name": ""}, items)
        return items[0]

    assert run(run_enrich())["category"] == "milch_eier"


def test_deposit_is_not_learned_or_enriched(client):
    put(client, _receipt([_item("p1", "PFAND", "Pfand", is_deposit=1)], "review", 100))
    put(client, _receipt([_item("p1", "PFAND", "Leergut", "obst_gemuese", is_deposit=1, updated_at=200)],
                         "confirmed", 200))
    assert lexicon(client) == []

    async def run_enrich():
        items = [_item("p2", "EINWEG", "Pfand", is_deposit=1)]
        async with app_db.get_db() as db:
            await receipts.enrich(db, {"store_id": "", "store_name": "REWE"}, items)
        return items[0]

    assert run(run_enrich())["category"] == "pfand"


def test_store_is_matched_on_put(client, db_path):
    run(app_db.init_db())
    con = sqlite3.connect(db_path)
    con.execute("INSERT INTO stores (id, name, updated_at) VALUES ('s1', 'REWE', 1)")
    con.commit()
    con.close()
    got = put(client, _receipt([], "review", 100, "REWE Markt"))["receipt"]
    assert got["store_id"] == "s1"


def test_lexicon_list_delete_and_relearn(client):
    learn_milk(client, "REWE")
    put(client, {"id": "r2", "store_name": "Aldi", "status": "review", "updated_at": 100,
                 "items": [_item("j1", "BROT", "Brot")]})
    put(client, {"id": "r2", "store_name": "Aldi", "status": "confirmed", "updated_at": 200,
                 "items": [_item("j1", "BROT", "Bauernbrot", "brot_backwaren", updated_at=200)]})
    entries = lexicon(client)
    assert [e["store_name"] for e in entries] == ["Aldi", "REWE"]
    assert [e["id"] for e in lexicon(client, f"?store_key={entries[1]['store_key']}")] == [entries[1]["id"]]

    assert client.delete(f"/api/receipts/lexicon/{entries[1]['id']}").json() == {"ok": True}
    assert [e["store_name"] for e in lexicon(client)] == ["Aldi"]

    # erneutes Lernen reaktiviert den gelöschten Eintrag
    put(client, _receipt([_item("i1", "G&G H-MILCH", "Vollmilch", "milch_eier", updated_at=300)],
                         "confirmed", 300))
    assert sorted(e["store_name"] for e in lexicon(client)) == ["Aldi", "REWE"]


def test_lexicon_path_is_not_a_receipt_id(client):
    r = client.get("/api/receipts/lexicon")
    assert r.status_code == 200 and r.json() == {"entries": []}
