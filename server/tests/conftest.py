import asyncio
import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app import db as app_db  # noqa: E402
from app import settings  # noqa: E402
from app.main import app  # noqa: E402


def run(coro):
    """Führt eine Coroutine synchron aus (für Tests ohne pytest-asyncio)."""
    return asyncio.run(coro)


@pytest.fixture
def db_path(tmp_path, monkeypatch):
    path = str(tmp_path / "t.db")
    monkeypatch.setattr(app_db, "DB_PATH", path)
    return path


@pytest.fixture
def client(tmp_path, db_path, monkeypatch):
    img = tmp_path / "img"
    img.mkdir()
    monkeypatch.setattr(settings, "IMAGES_DIR", img)
    monkeypatch.setattr(settings, "API_KEY", "")
    monkeypatch.setattr(settings, "ALLOW_NO_AUTH", True)
    monkeypatch.setattr(settings, "RECEIPT_QUEUE_INTERVAL_SEC", 0)
    with TestClient(app) as c:
        yield c
