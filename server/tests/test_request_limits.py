"""Eingabe-Limits der Request-Modelle (Phase 4, S10).

Jeder Fall schickt einen Wert knapp am Limit (kein 422, sondern 200 über die Attrappe) und einen
knapp darüber (422). Die KI- und OFF-Funktionen sind per monkeypatch ersetzt; es gibt keine Netzaufrufe.
"""
import pytest

from app import ai, off, receipts


BASE = {
    "/api/ai/generate": {"prompt": "p"},
    "/api/ai/remix": {"recipe_name": "n", "remix_prompt": "r"},
    "/api/ai/classify": {"name": "n"},
    "/api/ai/nutrition": {"name": "n"},
    "/api/ai/import-url": {"url": "https://example.com/"},
    "/api/ai/parse-receipt": {"image_base64": "QUJD"},
    "/api/ai/categorize-items": {"names": []},
    "/api/receipts/reconcile": {},
    "/api/off/lookup-barcode": {"barcode": "1"},
    "/api/off/search": {"query": "q"},
}

CASES = [
    # KI-Generierung
    ("/api/ai/generate", "prompt", "x" * 4000, "x" * 4001),
    ("/api/ai/generate", "custom_instructions", "x" * 4000, "x" * 4001),
    ("/api/ai/generate", "available_tags", ["t" * 200], ["t" * 201]),
    ("/api/ai/generate", "available_tags", ["t"] * 300, ["t"] * 301),
    ("/api/ai/generate", "exclude_allergens", ["a"] * 50, ["a"] * 51),
    # Remix
    ("/api/ai/remix", "recipe_name", "n" * 200, "n" * 201),
    ("/api/ai/remix", "recipe_description", "d" * 4000, "d" * 4001),
    ("/api/ai/remix", "remix_prompt", "r" * 4000, "r" * 4001),
    ("/api/ai/remix", "recipe_ingredients", ["i"] * 200, ["i"] * 201),
    ("/api/ai/remix", "recipe_instructions", ["s"] * 100, ["s"] * 101),
    ("/api/ai/remix", "recipe_instructions", ["s" * 4000], ["s" * 4001]),
    ("/api/ai/remix", "exclude_allergens", ["a"] * 50, ["a"] * 51),
    # Klassifizieren
    ("/api/ai/classify", "name", "n" * 200, "n" * 201),
    ("/api/ai/classify", "description", "d" * 4000, "d" * 4001),
    ("/api/ai/classify", "tags", ["t"] * 100, ["t"] * 101),
    ("/api/ai/classify", "ingredients", ["i"] * 200, ["i"] * 201),
    # Nährwerte
    ("/api/ai/nutrition", "name", "n" * 200, "n" * 201),
    ("/api/ai/nutrition", "ingredients", ["i"] * 200, ["i"] * 201),
    ("/api/ai/nutrition", "portions", 100, 101),
    ("/api/ai/nutrition", "portions", 1, 0),
    # Rezept-Import per URL
    ("/api/ai/import-url", "url", "h" * 2048, "h" * 2049),
    # Bon-Foto
    ("/api/ai/parse-receipt", "image_base64", "A" * 11_200_000, "A" * 11_200_001),
    # Bon-Abgleich
    ("/api/receipts/reconcile", "checked_items", [{"id": "a"}] * 300, [{"id": "a"}] * 301),
    ("/api/receipts/reconcile", "receipt_items", [{"id": "a"}] * 300, [{"id": "a"}] * 301),
    # Kategorisieren
    ("/api/ai/categorize-items", "names", ["n"] * 50, ["n"] * 51),
    ("/api/ai/categorize-items", "names", ["n" * 200], ["n" * 201]),
    # Open Food Facts
    ("/api/off/lookup-barcode", "barcode", "1" * 32, "1" * 33),
    ("/api/off/search", "query", "q" * 200, "q" * 201),
    ("/api/off/search", "limit", 20, 21),
    ("/api/off/search", "limit", 1, 0),
]


@pytest.fixture
def fakes(monkeypatch):
    async def gen(req):
        yield "x"

    async def empty_dict(*args, **kwargs):
        return {}

    async def empty_list(*args, **kwargs):
        return []

    monkeypatch.setattr(ai, "stream_generate", gen)
    monkeypatch.setattr(ai, "stream_remix", gen)
    monkeypatch.setattr(ai, "classify", empty_dict)
    monkeypatch.setattr(ai, "estimate_nutrition", empty_dict)
    monkeypatch.setattr(ai, "import_url", empty_dict)
    monkeypatch.setattr(ai, "parse_receipt", empty_dict)
    monkeypatch.setattr(ai, "reconcile_receipt", empty_dict)
    monkeypatch.setattr(receipts, "categorize", empty_list)
    monkeypatch.setattr(off, "lookup_barcode", empty_dict)
    monkeypatch.setattr(off, "search_products", empty_list)


@pytest.mark.parametrize("path, field, ok, bad", CASES)
def test_limit_boundary(client, fakes, path, field, ok, bad):
    body = dict(BASE[path])
    body[field] = ok
    assert client.post(path, json=body).status_code == 200
    body[field] = bad
    assert client.post(path, json=body).status_code == 422
