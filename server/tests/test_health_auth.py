from app import settings


def test_health_ok(client):
    r = client.get("/api/health")
    assert r.status_code == 200
    assert r.json()["ok"] is True


def test_auth_required_when_key_set(client, monkeypatch):
    monkeypatch.setattr(settings, "API_KEY", "x")
    assert client.get("/api/sync?since=0").status_code == 401
    assert client.get("/api/sync?since=0", headers={"X-Api-Key": "falsch"}).status_code == 401
    assert client.get("/api/sync?since=0", headers={"X-Api-Key": "x"}).status_code == 200


def test_fresh_db_returns_empty_lists(client):
    r = client.get("/api/sync?since=0")
    assert r.status_code == 200
    body = r.json()
    assert body["recipes"] == []
    assert body["stores"] == []
