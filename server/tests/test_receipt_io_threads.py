"""Datei-I/O der Bon-Fotos läuft im Thread, nicht im Event-Loop (Phase 6, S6)."""
import asyncio

from app import ai, receipts

from test_receipt_scan import parsed_result, scan


def test_scan_schreibt_und_liest_das_foto_ueber_to_thread(client, monkeypatch):
    async def fake_parse(image_b64, mime_type="image/jpeg", model=None):
        return parsed_result()

    monkeypatch.setattr(ai, "parse_receipt", fake_parse)

    original = asyncio.to_thread
    calls = []

    async def recorder(func, *args, **kwargs):
        calls.append(func.__name__)
        return await original(func, *args, **kwargs)

    monkeypatch.setattr(receipts.asyncio, "to_thread", recorder)

    r = scan(client)
    assert r.status_code == 200, r.text
    assert r.json()["parsed"] is True
    assert "write" in calls
    assert "read_bytes" in calls
