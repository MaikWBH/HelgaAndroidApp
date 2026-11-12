"""Schutznetz für den Router-Umbau (Phase 4, S3).

EXPECTED_ROUTES ist eine Momentaufnahme der Routen VOR dem Umbau. Ändert sich die Routenmenge,
schlägt Test 1 an; Test 2 prüft, dass jede Route außer GET /api/health ohne X-Api-Key mit 401 antwortet.
"""
import re

from fastapi.routing import APIRoute

from app import settings
from app.main import app

EXPECTED_ROUTES = {
    ("DELETE", "/api/receipts/lexicon/{entry_id}"),
    ("DELETE", "/api/receipts/{receipt_id}"),
    ("GET", "/api/health"),
    ("GET", "/api/images/{filename}"),
    ("GET", "/api/receipts"),
    ("GET", "/api/receipts/aggregates"),
    ("GET", "/api/receipts/lexicon"),
    ("GET", "/api/receipts/{receipt_id}"),
    ("GET", "/api/suggestions/aisles"),
    ("GET", "/api/suggestions/items"),
    ("GET", "/api/suggestions/units"),
    ("GET", "/api/sync"),
    ("POST", "/api/ai/categorize-items"),
    ("POST", "/api/ai/classify"),
    ("POST", "/api/ai/generate"),
    ("POST", "/api/ai/import-url"),
    ("POST", "/api/ai/nutrition"),
    ("POST", "/api/ai/parse-receipt"),
    ("POST", "/api/ai/remix"),
    ("POST", "/api/images/upload"),
    ("POST", "/api/off/lookup-barcode"),
    ("POST", "/api/off/search"),
    ("POST", "/api/receipts/reconcile"),
    ("POST", "/api/receipts/scan"),
    ("POST", "/api/receipts/{receipt_id}/image"),
    ("POST", "/api/receipts/{receipt_id}/reparse"),
    ("POST", "/api/sync"),
    ("PUT", "/api/receipts/{receipt_id}"),
}


def _app_routes():
    result = set()
    for route in app.routes:
        if isinstance(route, APIRoute):
            for method in route.methods - {"HEAD", "OPTIONS"}:
                result.add((method, route.path))
    return result


def test_route_set_is_unchanged():
    assert _app_routes() == EXPECTED_ROUTES


def test_all_routes_except_health_require_key(client, monkeypatch):
    monkeypatch.setattr(settings, "API_KEY", "geheim")
    monkeypatch.setattr(settings, "ALLOW_NO_AUTH", False)
    for method, path in sorted(EXPECTED_ROUTES):
        concrete = re.sub(r"\{[^}]+\}", "x", path)
        response = client.request(method, concrete)
        if (method, path) == ("GET", "/api/health"):
            assert response.status_code == 200, path
        else:
            assert response.status_code == 401, f"{method} {path} -> {response.status_code}"
