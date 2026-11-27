def put(client, body):
    r = client.put(f"/api/receipts/{body['id']}", json=body)
    assert r.status_code == 200, r.text


def item(item_id, name, total, category="", qty=1, unit_price=None, discount=0.0, deposit=0, position=0):
    return {"id": item_id, "position": position, "raw_text": name.upper(), "name": name, "category": category,
            "quantity": qty, "unit_price": total / qty if unit_price is None else unit_price, "total_price": total,
            "discount": discount, "is_deposit": deposit, "updated_at": 100}


def seed(client):
    put(client, {"id": "r1", "store_name": "REWE", "purchase_day": "2026-11-10", "status": "confirmed",
                 "total_amount": 10.0, "discount_total": 1.0, "updated_at": 100, "items": [
                     item("a", "Milch", 2.0, "milch_eier"),
                     item("b", "Bananen", 1.5, "obst_gemuese", qty=0.5, unit_price=3.0, discount=0.5, position=1),
                     item("c", "Pfand", 0.25, "obst_gemuese", deposit=1, position=2)]})
    put(client, {"id": "r2", "store_name": "Aldi", "purchase_day": "2026-10-30", "status": "reviewed",
                 "total_amount": 5.0, "updated_at": 100, "items": [
                     item("d", "Milch", 2.0, "", unit_price=2.2),
                     item("e", "Brot", 0.126, "brot_backwaren", position=1)]})
    put(client, {"id": "r3", "store_name": "Lidl", "purchase_day": "2026-11-11", "status": "provisional",
                 "total_amount": 99.0, "updated_at": 100, "items": [item("f", "Wein", 99.0, "alkohol")]})
    put(client, {"id": "r4", "store_name": "Netto", "purchase_day": "2026-11-12", "status": "confirmed",
                 "total_amount": 77.0, "updated_at": 100, "items": [item("g", "Käse", 77.0, "milch_eier")]})
    client.delete("/api/receipts/r4?updated_at=200")
    put(client, {"id": "r5", "store_name": "Penny", "purchase_day": "", "status": "confirmed",
                 "total_amount": 55.0, "updated_at": 100, "items": [item("h", "Tee", 55.0, "fruehstueck")]})


def rows(client, dimension):
    body = client.get("/api/receipts/aggregates").json()
    return [r for r in body["rows"] if r["dimension"] == dimension]


def test_empty_database(client):
    body = client.get("/api/receipts/aggregates").json()
    assert body["rows"] == [] and body["generated_at"] > 0


def test_total_per_month_with_discounts(client):
    seed(client)
    total = {r["period"]: r for r in rows(client, "total")}
    assert set(total) == {"2026-10", "2026-11"}   # provisional, gelöscht und ohne Tag fehlen
    assert total["2026-11"]["amount"] == 10.0 and total["2026-11"]["count"] == 1
    assert total["2026-11"]["discount"] == 1.5     # 1,00 Bon-Rabatt + 0,50 Positionsrabatt
    assert total["2026-10"]["amount"] == 5.0 and total["2026-10"]["discount"] == 0.0


def test_week_uses_iso_weeks(client):
    seed(client)
    weeks = {r["period"]: r for r in rows(client, "week")}
    assert "2026-W46" in weeks and weeks["2026-W46"]["amount"] == 10.0
    assert len(weeks) == 2


def test_store_key_and_most_common_name(client):
    seed(client)
    stores = {(r["period"], r["key"]): r for r in rows(client, "store")}
    assert stores[("2026-11", "name:rewe")]["label"] == "REWE"
    assert stores[("2026-11", "name:rewe")]["amount"] == 10.0
    assert stores[("2026-10", "name:aldi")]["count"] == 1
    assert not any(k[1] in ("name:lidl", "name:netto", "name:penny") for k in stores)


def test_category_includes_sonstiges_and_pfand(client):
    seed(client)
    cats = {(r["period"], r["key"]): r for r in rows(client, "category")}
    assert cats[("2026-11", "milch_eier")]["amount"] == 2.0
    assert cats[("2026-11", "obst_gemuese")]["amount"] == 1.0   # 1,50 − 0,50
    assert cats[("2026-11", "pfand")]["amount"] == 0.25
    assert cats[("2026-10", "sonstiges")]["amount"] == 2.0       # leere Kategorie
    assert cats[("2026-10", "brot_backwaren")]["amount"] == 0.13  # gerundet auf 2 Stellen
    assert not any(k[1] in ("alkohol", "fruehstueck") for k in cats)


def test_products_without_deposit(client):
    seed(client)
    products = {(r["period"], r["key"]): r for r in rows(client, "product")}
    banana = products[("2026-11", "bananen")]
    assert banana["label"] == "Bananen" and banana["amount"] == 1.0
    assert banana["quantity"] == 0.5 and banana["avg_unit_price"] == 3.0 and banana["count"] == 1
    assert products[("2026-10", "milch")]["avg_unit_price"] == 2.2
    assert not any(k[1] == "pfand" for k in products)


def test_rows_are_sorted(client):
    seed(client)
    keys = [(r["dimension"], r["period"], r["key"]) for r in client.get("/api/receipts/aggregates").json()["rows"]]
    assert keys == sorted(keys)
