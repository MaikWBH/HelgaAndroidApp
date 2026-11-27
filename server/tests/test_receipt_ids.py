"""Einheitliche Bon-ID-Prüfung auf allen Routen (Phase 7, F18)."""
import pytest

from app import ai

from test_receipt_scan import parsed_result, patch_parse, scan

BAD_IDS = ["a.b", "x" * 65, "a%0A"]


def _call(client, route, rid):
    url = f"/api/receipts/{rid}"
    if route == "get":
        return client.get(url)
    if route == "put":
        return client.put(url, json={"id": "ok", "store_name": "Lidl", "status": "review", "updated_at": 1, "items": []})
    if route == "delete":
        return client.delete(url)
    if route == "image":
        return client.post(f"{url}/image", files={"file": ("bon.jpg", b"JPEG", "image/jpeg")})
    return client.post(f"{url}/reparse")


@pytest.mark.parametrize("route", ["get", "put", "delete", "image", "reparse"])
def test_invalid_ids_are_rejected(client, route):
    for rid in BAD_IDS:
        r = _call(client, route, rid)
        assert r.status_code == 400, (route, rid, r.status_code)


def test_readable_ids_are_accepted(client, monkeypatch):
    body = {"id": "testbon-lidl-0524", "store_name": "Lidl", "status": "review", "updated_at": 1, "items": []}
    assert client.put("/api/receipts/testbon-lidl-0524", json=body).status_code == 200
    patch_parse(monkeypatch, parsed_result())
    assert scan(client, rid="testbon-lidl-0525").status_code == 200
