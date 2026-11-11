import json

from app import ai, categories
from app import receipt_text

from conftest import run

FULL_RESPONSE = {
    "store_name": "REWE",
    "store_address": "REWE Markt Müller oHG\nHauptstr. 12\n12345 Musterstadt",
    "purchase_date": "2026-11-10",
    "purchase_time": "17:42",
    "total_amount": 2.88,
    "discount_total": 0.0,
    "confidence": 0.92,
    "items": [
        {"name": "G&G H-MILCH 3,5%", "product_name": "H-Milch 3,5 %", "category": "milch_eier",
         "quantity": 1, "unit": "stk", "unit_price": 0.95, "total_price": 0.95, "discount": 0,
         "is_deposit": False, "confidence": 0.95},
        {"name": "BANANE", "product_name": "Bananen", "category": "obst_gemuese",
         "quantity": 2, "unit": "stk", "unit_price": 0.99, "total_price": 1.98, "discount": 0.30,
         "is_deposit": False, "confidence": 0.9},
        {"name": "PFAND", "product_name": "Pfand", "category": "obst_gemuese",
         "quantity": 1, "unit": "stk", "unit_price": 0.25, "total_price": 0.25, "discount": 0,
         "is_deposit": True, "confidence": 0.95},
    ],
}


def _patch_once(monkeypatch, text):
    calls = []

    async def fake(system, user_text, image_b64=None, mime_type="image/jpeg", model=None):
        calls.append({"model": model, "image": image_b64})
        return text

    monkeypatch.setattr(ai, "_receipt_once", fake)
    return calls


def test_full_response(monkeypatch):
    calls = _patch_once(monkeypatch, json.dumps(FULL_RESPONSE))
    result = run(ai.parse_receipt("QUJD"))
    assert calls[0]["model"] == ai._receipt_model()
    assert result["store_name"] == "REWE"
    assert result["store_address"] == "REWE Markt Müller oHG, Hauptstr. 12, 12345 Musterstadt"
    assert result["purchase_day"] == "2026-11-10"
    assert result["purchase_time"] == "17:42"
    assert result["purchase_date"] == receipt_text.day_to_ms("2026-11-10") == 1794268800000
    assert result["total_amount"] == 2.88
    assert result["model"] == ai._receipt_model()
    milk, bananas, deposit = result["items"]
    assert milk["raw_text"] == "G&G H-MILCH 3,5%" and milk["name"] == "H-Milch 3,5 %"
    assert milk["category"] == "milch_eier" and milk["unit"] == "stk"
    assert bananas["discount"] == 0.30 and bananas["quantity"] == 2
    assert deposit["category"] == "pfand" and deposit["is_deposit"] == 1


def test_fenced_json(monkeypatch):
    _patch_once(monkeypatch, "```json\n" + json.dumps(FULL_RESPONSE) + "\n```")
    assert len(run(ai.parse_receipt("QUJD"))["items"]) == 3


def test_comma_prices_as_text(monkeypatch):
    data = {"items": [{"name": "MILCH", "unit_price": "1,99", "total_price": "1,99", "quantity": "1"}]}
    _patch_once(monkeypatch, json.dumps(data))
    item = run(ai.parse_receipt("QUJD"))["items"][0]
    assert item["unit_price"] == 1.99 and item["total_price"] == 1.99


def test_unknown_values_are_sanitized():
    result = ai.normalize_parsed_receipt({
        "purchase_time": "7:5",
        "items": [{"name": "X", "category": "xyz", "unit": "pfund"}],
    })
    assert result["purchase_time"] == ""
    assert result["items"][0]["category"] == ""
    assert result["items"][0]["unit"] == "stk"
    assert ai.normalize_parsed_receipt({"purchase_time": "07:05"})["purchase_time"] == "07:05"
    assert ai.normalize_parsed_receipt({"purchase_time": "7:05"})["purchase_time"] == "07:05"
    assert ai.normalize_parsed_receipt({"purchase_time": "25:00"})["purchase_time"] == ""
    assert ai.normalize_parsed_receipt({"purchase_date": "gestern"})["purchase_date"] == 0


def test_no_json_returns_empty_result(monkeypatch):
    _patch_once(monkeypatch, "Das kann ich nicht lesen.")
    result = run(ai.parse_receipt("QUJD"))
    assert result["items"] == []
    assert result["total_amount"] == 0 and result["confidence"] == 0.0


def test_missing_product_name_falls_back_to_raw_text():
    result = ai.normalize_parsed_receipt({"items": [{"name": "BIO BAN.", "product_name": ""}, {}]})
    assert len(result["items"]) == 1
    assert result["items"][0]["name"] == result["items"][0]["raw_text"] == "BIO BAN."


def test_prompt_contains_all_categories_and_no_placeholder():
    for key in categories.CATEGORY_KEYS:
        assert f"- {key}:" in ai.RECEIPT_PARSE_USER
    assert "{categories}" not in ai.RECEIPT_PARSE_USER
    assert "Uhrzeit und Adresse" not in ai.RECEIPT_PARSE_USER


def test_legacy_endpoint_still_works(client, monkeypatch):
    _patch_once(monkeypatch, json.dumps(FULL_RESPONSE))
    r = client.post("/api/ai/parse-receipt", json={"image_base64": "QUJD"})
    assert r.status_code == 200
    body = r.json()
    assert body["store_name"] == "REWE"
    assert body["items"][0]["name"] == "H-Milch 3,5 %"
    assert body["items"][0]["raw_text"] == "G&G H-MILCH 3,5%"
