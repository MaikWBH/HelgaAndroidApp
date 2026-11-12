"""Bild-Upload (Phase 4, S11): Größenlimit, Erweiterungsprüfung und Antwortformat."""
from app import settings


def test_small_jpg_is_stored(client):
    r = client.post("/api/images/upload", files={"file": ("bild.jpg", b"JPEGDATA", "image/jpeg")})
    assert r.status_code == 200
    body = r.json()
    assert body["filename"] == f"{body['uuid']}.jpg"
    assert (settings.IMAGES_DIR / body["filename"]).read_bytes() == b"JPEGDATA"


def test_exactly_at_limit_is_accepted(client, monkeypatch):
    monkeypatch.setattr(settings, "MAX_IMAGE_BYTES", 1024)
    r = client.post("/api/images/upload", files={"file": ("bild.png", b"x" * 1024, "image/png")})
    assert r.status_code == 200


def test_over_limit_is_413_and_nothing_stored(client, monkeypatch):
    monkeypatch.setattr(settings, "MAX_IMAGE_BYTES", 1024)
    r = client.post("/api/images/upload", files={"file": ("bild.jpg", b"x" * 1025, "image/jpeg")})
    assert r.status_code == 413
    assert r.json()["detail"] == "Bild ist zu groß"
    assert list(settings.IMAGES_DIR.iterdir()) == []


def test_wrong_extension_is_400(client):
    r = client.post("/api/images/upload", files={"file": ("virus.exe", b"MZ", "application/octet-stream")})
    assert r.status_code == 400
    assert r.json()["detail"] == "Dateitype nicht erlaubt: .exe"
    assert list(settings.IMAGES_DIR.iterdir()) == []


def test_receipt_limit_follows_image_limit():
    assert settings.MAX_RECEIPT_IMAGE_BYTES == settings.MAX_IMAGE_BYTES == 8 * 1024 * 1024
