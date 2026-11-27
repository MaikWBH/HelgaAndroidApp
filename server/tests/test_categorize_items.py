import json
import re
import sqlite3

from app import ai


class FakeModel:
    """Attrappe für `_receipt_once`: antwortet nach einer Tabelle Name → Kategorie und zählt die Aufrufe."""

    def __init__(self, monkeypatch, answers, error=None):
        self.answers = answers
        self.error = error
        self.calls = 0
        self.asked = []

        async def fake(system, user_text, image_b64=None, mime_type="image/jpeg", model=None):
            self.calls += 1
            if self.error:
                raise self.error
            names = re.findall(r"^\d+\. (.+)$", user_text.split("ARTIKEL:")[1], re.M)
            self.asked.append(names)
            results = [{"index": i, "category": self.answers.get(n, "sonstiges")} for i, n in enumerate(names, 1)]
            return json.dumps({"results": results})

        monkeypatch.setattr(ai, "_receipt_once", fake)


def categorize(client, names):
    r = client.post("/api/ai/categorize-items", json={"names": names})
    assert r.status_code == 200, r.text
    return r.json()["results"]


def stored(db_path):
    con = sqlite3.connect(db_path)
    try:
        return {r[0]: (r[1], r[2]) for r in con.execute("SELECT product_key, category, source FROM product_categories")}
    finally:
        con.close()


def test_known_products_need_no_ai_call(client, db_path, monkeypatch):
    con = sqlite3.connect(db_path)
    con.execute("INSERT INTO product_categories (product_key, product_name, category, source) VALUES ('milch', 'Milch', 'milch_eier', 'user')")
    con.commit(); con.close()
    model = FakeModel(monkeypatch, {})
    assert categorize(client, ["Milch"]) == [{"name": "Milch", "category": "milch_eier"}]
    assert model.calls == 0


def test_unknown_products_in_one_call_then_stored(client, db_path, monkeypatch):
    model = FakeModel(monkeypatch, {"Tomaten": "obst_gemuese", "Bier": "alkohol"})
    result = categorize(client, ["Tomaten", "Bier", "Tomaten"])
    assert [r["category"] for r in result] == ["obst_gemuese", "alkohol", "obst_gemuese"]
    assert model.calls == 1 and model.asked == [["Tomaten", "Bier"]]  # ohne Dublette, ein Aufruf
    assert stored(db_path)["tomaten"] == ("obst_gemuese", "ai")
    categorize(client, ["Tomaten", "Bier"])
    assert model.calls == 1  # zweiter Aufruf ohne KI


def test_unknown_category_becomes_sonstiges_and_is_stored(client, db_path, monkeypatch):
    model = FakeModel(monkeypatch, {"Geschenkpapier": "quatsch"})
    assert categorize(client, ["Geschenkpapier"])[0]["category"] == "sonstiges"
    assert stored(db_path)["geschenkpapier"] == ("sonstiges", "ai")
    categorize(client, ["Geschenkpapier"])
    assert model.calls == 1  # wird nicht erneut gefragt


def test_ai_failure_returns_empty_and_stores_nothing(client, db_path, monkeypatch):
    model = FakeModel(monkeypatch, {}, error=RuntimeError("KI down"))
    assert categorize(client, ["Tomaten"]) == [{"name": "Tomaten", "category": ""}]
    assert model.calls == 1 and stored(db_path) == {}


def test_order_is_kept_and_empty_names_are_empty(client, monkeypatch):
    FakeModel(monkeypatch, {"Käse": "milch_eier", "Brot": "brot_backwaren"})
    result = categorize(client, ["Käse", "  ", "Brot"])
    assert [(r["name"], r["category"]) for r in result] == [("Käse", "milch_eier"), ("  ", ""), ("Brot", "brot_backwaren")]


def test_more_than_fifty_names_are_rejected(client):
    r = client.post("/api/ai/categorize-items", json={"names": [f"Artikel {i}" for i in range(51)]})
    assert r.status_code == 422
