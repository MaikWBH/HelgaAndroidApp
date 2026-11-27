import json
from types import SimpleNamespace as NS

from app import ai

from conftest import run


def _patch(monkeypatch, text):
    seen = {}

    async def fake(system, user_text, image_b64=None, mime_type="image/jpeg", model=None):
        seen["user"] = user_text
        return text

    monkeypatch.setattr(ai, "_receipt_once", fake)
    return seen


CHECKED = [NS(id="s1", name="Milch", quantity=1, unit="l"), NS(id="s2", name="Brot", quantity=1, unit="")]
RECEIPT = [
    NS(id="r1", name="H-Milch", raw_text="G&G H-MILCH", total_price=0.95),
    NS(id="r2", name="Schokolade", raw_text="MILKA", total_price=1.29),
]


def test_valid_matching(monkeypatch):
    _patch(monkeypatch, json.dumps({
        "matches": [{"shopping_item_id": "s1", "receipt_item_id": "r1"}],
        "unexpected": ["r2"], "missing": ["s2"],
    }))
    result = run(ai.reconcile_receipt(CHECKED, RECEIPT))
    assert result == {
        "matches": [{"shopping_item_id": "s1", "receipt_item_id": "r1"}],
        "unexpected": ["r2"], "missing": ["s2"],
    }


def test_invented_ids_are_filtered(monkeypatch):
    _patch(monkeypatch, json.dumps({
        "matches": [{"shopping_item_id": "zzz", "receipt_item_id": "r1"},
                    {"shopping_item_id": "s1", "receipt_item_id": "nope"}],
        "unexpected": ["x"], "missing": ["y"],
    }))
    assert run(ai.reconcile_receipt(CHECKED, RECEIPT)) == {"matches": [], "unexpected": [], "missing": []}


def test_no_json_returns_empty_lists(monkeypatch):
    _patch(monkeypatch, "keine Ahnung")
    assert run(ai.reconcile_receipt(CHECKED, RECEIPT)) == {"matches": [], "unexpected": [], "missing": []}


def test_empty_inputs_use_placeholder(monkeypatch):
    seen = _patch(monkeypatch, "{}")
    run(ai.reconcile_receipt([], []))
    assert seen["user"].count("(keine)") == 2


def test_reconcile_endpoint(client, monkeypatch):
    _patch(monkeypatch, json.dumps({"matches": [], "unexpected": [], "missing": []}))
    r = client.post("/api/receipts/reconcile", json={"checked_items": [], "receipt_items": []})
    assert r.status_code == 200
