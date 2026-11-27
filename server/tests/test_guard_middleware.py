"""Schlüssel- und Größenprüfung vor dem Lesen des Bodys (Phase 8, S5)."""
from app import receipts, settings


def test_missing_key_is_401_before_upload_is_read(client, monkeypatch):
    monkeypatch.setattr(settings, "API_KEY", "geheim")
    calls = []

    async def spy(*args, **kwargs):
        calls.append(1)
        return "x.jpg"

    monkeypatch.setattr(receipts, "save_receipt_image", spy)
    files = {"file": ("bon.jpg", b"JPEG", "image/jpeg")}
    r = client.post("/api/receipts/scan", files=files, data={"receipt_id": "abc"})
    assert r.status_code == 401
    assert r.json() == {"detail": "Nicht autorisiert"}
    assert calls == []
    ok = client.post("/api/receipts/scan", files=files, data={"receipt_id": "abc"}, headers={"X-Api-Key": "geheim"})
    assert ok.status_code != 401
    assert calls == [1]


def test_oversized_content_length_is_413(client, monkeypatch):
    monkeypatch.setattr(settings, "API_KEY", "geheim")
    monkeypatch.setattr(settings, "MAX_REQUEST_BYTES", 100)
    r = client.post("/api/sync", content=b"x" * 200, headers={"X-Api-Key": "geheim", "Content-Type": "application/json"})
    assert r.status_code == 413
    assert r.json() == {"detail": "Anfrage zu groß"}


def test_health_needs_no_key(client, monkeypatch):
    monkeypatch.setattr(settings, "API_KEY", "geheim")
    assert client.get("/api/health").status_code == 200
