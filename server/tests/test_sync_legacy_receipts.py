"""Alte App-Versionen schicken Bons weiter über POST /api/sync (Vertrag Abschnitt 7)."""


def legacy_push(client, receipts=(), items=()):
    r = client.post("/api/sync", json={"receipts": list(receipts), "receipt_items": list(items)})
    assert r.status_code == 200, r.text
    return r.json()


def put(client, body):
    r = client.put(f"/api/receipts/{body['id']}", json=body)
    assert r.status_code == 200, r.text


def test_legacy_push_is_visible_in_the_receipt_archive(client):
    legacy_push(
        client,
        receipts=[{"id": "r1", "store_name": "REWE", "purchase_date": 1794268800000, "total_amount": 3.5,
                   "status": "scanned", "updated_at": 100}],
        items=[{"id": "i1", "receipt_id": "r1", "position": 0, "raw_text": "MILCH", "name": "Milch",
                "total_price": 3.5, "updated_at": 100}],
    )
    got = client.get("/api/receipts/r1").json()
    assert got["store_name"] == "REWE"
    assert got["purchase_day"] == "2026-11-10"
    assert got["source"] == "legacy"
    assert [i["name"] for i in got["items"]] == ["Milch"]
    # der Bon-Cursor sieht auch eine reine Positionsänderung
    cursor = client.get("/api/receipts").json()["server_seq"]
    legacy_push(client, items=[{"id": "i1", "receipt_id": "r1", "position": 0, "raw_text": "MILCH",
                                "name": "Vollmilch", "total_price": 3.5, "updated_at": 200}])
    changed = client.get(f"/api/receipts?since_seq={cursor}").json()["receipts"]
    assert [r["id"] for r in changed] == ["r1"]


def test_legacy_delete_is_ignored(client):
    put(client, {"id": "r1", "store_name": "REWE", "status": "confirmed", "updated_at": 100, "items": []})
    legacy_push(client, receipts=[{"id": "r1", "store_name": "REWE", "status": "confirmed",
                                   "updated_at": 999, "deleted": 1}])
    got = client.get("/api/receipts/r1")
    assert got.status_code == 200
    assert got.json()["deleted"] == 0


def test_legacy_push_does_not_overwrite_new_columns(client):
    put(client, {"id": "r1", "store_name": "REWE", "status": "confirmed", "updated_at": 100,
                 "items": [{"id": "i1", "raw_text": "MILCH", "name": "Milch", "category": "milch_eier",
                            "unit": "kg", "updated_at": 100}]})
    legacy_push(client, items=[{"id": "i1", "receipt_id": "r1", "position": 0, "raw_text": "MILCH",
                                "name": "H-Milch", "total_price": 1.0, "updated_at": 200}])
    item = client.get("/api/receipts/r1").json()["items"][0]
    assert item["name"] == "H-Milch"        # bisherige Spalte aktualisiert
    assert item["category"] == "milch_eier"  # neue Spalte bleibt
    assert item["unit"] == "kg"


def test_pull_no_longer_contains_receipts(client):
    put(client, {"id": "r1", "store_name": "REWE", "status": "confirmed", "updated_at": 100,
                 "items": [{"id": "i1", "raw_text": "MILCH", "name": "Milch"}]})
    body = client.get("/api/sync?since=0").json()
    assert body["receipts"] == [] and body["receipt_items"] == []
    assert client.get("/api/receipts").json()["receipts"][0]["id"] == "r1"
