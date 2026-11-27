import logging

import pytest
from fastapi.testclient import TestClient

from app import settings
from app.main import app


@pytest.fixture
def startup(db_path, monkeypatch):
    monkeypatch.setattr(settings, "RECEIPT_QUEUE_INTERVAL_SEC", 0)
    monkeypatch.setattr(settings, "API_KEY", "")
    monkeypatch.setattr(settings, "ALLOW_NO_AUTH", False)
    return monkeypatch


def test_refuses_start_without_key_and_flag(startup):
    with pytest.raises(RuntimeError, match="API_KEY fehlt"):
        with TestClient(app):
            pass


def test_starts_without_key_with_flag_and_warns(startup, caplog):
    startup.setattr(settings, "ALLOW_NO_AUTH", True)
    with caplog.at_level(logging.WARNING):
        with TestClient(app) as c:
            assert c.get("/api/health").status_code == 200
    assert "ohne Authentifizierung" in caplog.text


def test_starts_with_key_without_flag(startup):
    startup.setattr(settings, "API_KEY", "x")
    with TestClient(app) as c:
        assert c.get("/api/health").status_code == 200
