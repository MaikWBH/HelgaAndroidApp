"""Der Voll-Sync (Rezepte, Listen usw.) funktioniert nach der Umschaltung der Bons unverändert weiter."""


def test_push_then_pull_recipe(client):
    r = client.post("/api/sync", json={"recipes": [{"id": "r1", "updated_at": 100, "name": "Pfannkuchen"}]})
    assert r.status_code == 200
    assert r.json()["recipes"] == []  # keine server_wins
    pulled = client.get("/api/sync?since=0").json()
    assert [x["name"] for x in pulled["recipes"]] == ["Pfannkuchen"]
    cursor = pulled["server_ts"]
    assert client.get(f"/api/sync?since={cursor}").json()["recipes"] == []


def test_older_push_loses_against_newer_server_state(client):
    client.post("/api/sync", json={"recipes": [{"id": "r1", "updated_at": 200, "name": "Neu"}]})
    r = client.post("/api/sync", json={"recipes": [{"id": "r1", "updated_at": 50, "name": "Alt"}]})
    assert [x["name"] for x in r.json()["recipes"]] == ["Neu"]  # Server gewinnt und meldet es zurück
    assert [x["name"] for x in client.get("/api/sync?since=0").json()["recipes"]] == ["Neu"]


def test_newer_push_wins(client):
    client.post("/api/sync", json={"recipes": [{"id": "r1", "updated_at": 100, "name": "Alt"}]})
    r = client.post("/api/sync", json={"recipes": [{"id": "r1", "updated_at": 300, "name": "Neu"}]})
    assert r.json()["recipes"] == []
    assert [x["name"] for x in client.get("/api/sync?since=0").json()["recipes"]] == ["Neu"]
