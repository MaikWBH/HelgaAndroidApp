import json

from app import ai


def _item(item_id, position, name="Milch", **extra):
    base = {"id": item_id, "position": position, "raw_text": name.upper(), "name": name,
            "quantity": 1, "unit": "stk", "unit_price": 1.0, "total_price": 1.0, "updated_at": 100}
    base.update(extra)
    return base


def _receipt(receipt_id="r1", items=None, updated_at=100, **extra):
    base = {"id": receipt_id, "store_name": "REWE", "purchase_day": "2026-11-10",
            "total_amount": 3.0, "status": "review", "updated_at": updated_at,
            "items": items if items is not None else [_item("i1", 1), _item("i2", 0, "Brot")]}
    base.update(extra)
    return base


def put(client, body):
    r = client.put(f"/api/receipts/{body['id']}", json=body)
    assert r.status_code == 200, r.text
    return r.json()


def test_put_creates_and_get_returns_items_in_order(client):
    result = put(client, _receipt())
    assert result["applied"] is True
    got = client.get("/api/receipts/r1").json()
    assert [i["id"] for i in got["items"]] == ["i2", "i1"]
    assert got["store_name"] == "REWE"
    assert got["server_seq"] > 0


def test_older_put_is_not_applied(client):
    put(client, _receipt(updated_at=200, total_amount=5.0))
    result = put(client, _receipt(updated_at=100, total_amount=1.0))
    assert result["applied"] is False
    assert result["receipt"]["total_amount"] == 5.0
    assert client.get("/api/receipts/r1").json()["total_amount"] == 5.0


def test_missing_item_is_soft_deleted(client):
    put(client, _receipt())
    put(client, _receipt(updated_at=200, items=[_item("i2", 0, "Brot", updated_at=200)]))
    got = client.get("/api/receipts/r1").json()
    assert [i["id"] for i in got["items"]] == ["i2"]


def test_deposit_total_and_purchase_date(client):
    items = [_item("i1", 0, total_price=2.0),
             _item("i2", 1, "Pfand", total_price=0.25, is_deposit=1),
             _item("i3", 2, "Leergut", total_price=-0.10, is_deposit=1)]
    got = put(client, _receipt(items=items))["receipt"]
    assert got["deposit_total"] == 0.15
    assert got["purchase_date"] == 1794268800000


def test_delete_hides_receipt_and_lists_with_flag(client):
    put(client, _receipt())
    assert client.delete("/api/receipts/r1?updated_at=500").json() == {"ok": True}
    assert client.get("/api/receipts/r1").status_code == 404
    assert client.get("/api/receipts").json()["receipts"] == []
    deleted = client.get("/api/receipts?include_deleted=1").json()["receipts"]
    assert len(deleted) == 1 and deleted[0]["deleted"] == 1 and deleted[0]["items"] == []
    assert deleted[0]["updated_at"] == 500


def test_delete_unknown_is_ok(client):
    assert client.delete("/api/receipts/nope?updated_at=1").status_code == 200


def test_since_seq_returns_only_changes(client):
    put(client, _receipt("a"))
    cursor = client.get("/api/receipts").json()["server_seq"]
    assert client.get(f"/api/receipts?since_seq={cursor}").json()["receipts"] == []
    put(client, _receipt("a", updated_at=200, items=[_item("i1", 0, updated_at=200),
                                                       _item("i9", 1, "Neu", updated_at=200)]))
    changed = client.get(f"/api/receipts?since_seq={cursor}").json()["receipts"]
    assert [r["id"] for r in changed] == ["a"]
    assert len(changed[0]["items"]) == 2


def test_day_filters_are_inclusive_and_keep_undated(client):
    put(client, _receipt("old", purchase_day="2026-09-01"))
    put(client, _receipt("mid", purchase_day="2026-10-15"))
    put(client, _receipt("new", purchase_day="2026-11-10"))
    put(client, _receipt("undated", purchase_day=""))

    def ids(query):
        return sorted(r["id"] for r in client.get(f"/api/receipts?{query}").json()["receipts"])

    assert ids("from_day=2026-10-15") == ["mid", "new", "undated"]
    # Vertrag: `purchase_day <= to_day` gilt wörtlich, ein leerer Tag ('') besteht diesen Filter.
    assert ids("to_day=2026-10-15") == ["mid", "old", "undated"]
    assert ids("from_day=2026-10-15&to_day=2026-10-15") == ["mid", "undated"]


def test_invalid_category_is_cleared(client):
    got = put(client, _receipt(items=[_item("i1", 0, category="xyz"),
                                      _item("i2", 1, "Milch", category="milch_eier")]))["receipt"]
    cats = {i["id"]: i["category"] for i in got["items"]}
    assert cats == {"i1": "", "i2": "milch_eier"}


def test_reconcile_route_still_works(client, monkeypatch):
    async def fake(system, user_text, image_b64=None, mime_type="image/jpeg", model=None):
        return json.dumps({"matches": [], "unexpected": [], "missing": []})

    monkeypatch.setattr(ai, "_receipt_once", fake)
    r = client.post("/api/receipts/reconcile", json={"checked_items": [], "receipt_items": []})
    assert r.status_code == 200
