import sqlite3
import time

from app import ai, receipts

from conftest import run

RID = "11111111-2222-3333-4444-555555555555"


def parsed_ok():
    return {
        "store_name": "REWE", "store_address": "", "purchase_day": "2026-11-10", "purchase_time": "",
        "purchase_date": 1794268800000, "total_amount": 1.0, "discount_total": 0.0, "confidence": 0.9,
        "model": "claude-haiku-5-5",
        "items": [{"raw_text": "MILCH", "name": "Milch", "quantity": 1.0, "unit": "stk", "unit_price": 1.0,
                   "total_price": 1.0, "discount": 0.0, "is_deposit": 0, "category": "milch_eier",
                   "confidence": 0.9}],
    }


class FakeAi:
    def __init__(self, monkeypatch):
        self.fail = True
        self.calls = 0

        async def fake(image_b64, mime_type="image/jpeg", model=None):
            self.calls += 1
            if self.fail:
                raise RuntimeError("KI down")
            return parsed_ok()

        monkeypatch.setattr(ai, "parse_receipt", fake)


def scan(client, rid=RID):
    return client.post("/api/receipts/scan", files={"file": ("bon.jpg", b"JPEG", "image/jpeg")},
                       data={"receipt_id": rid, "captured_at": "1794268800000"})


def sql(db_path, query, params=()):
    con = sqlite3.connect(db_path)
    try:
        cur = con.execute(query, params)
        rows = cur.fetchall()
        con.commit()
        return rows
    finally:
        con.close()


def test_queue_processes_failed_scan(client, db_path, monkeypatch):
    fake = FakeAi(monkeypatch)
    assert scan(client).json()["parsed"] is False
    fake.fail = False
    assert run(receipts.process_parse_queue()) == 1
    receipt = client.get(f"/api/receipts/{RID}").json()
    assert receipt["status"] == "review" and receipt["parse_status"] == "done"
    assert receipt["source"] == "ai" and len(receipt["items"]) == 1
    assert run(receipts.process_parse_queue()) == 0


def test_permanent_failure_ends_in_failed(client, db_path, monkeypatch):
    FakeAi(monkeypatch)
    scan(client)  # Versuch 1
    for _ in range(4):  # Versuche 2 bis 5
        assert run(receipts.process_parse_queue()) == 0
    status, attempts = sql(db_path, "SELECT parse_status, parse_attempts FROM receipts WHERE id = ?", (RID,))[0]
    assert (status, attempts) == ("failed", 5)
    fake_calls = FakeAi(monkeypatch)
    assert run(receipts.process_parse_queue()) == 0
    assert fake_calls.calls == 0


def test_stale_running_is_resumed(client, db_path, monkeypatch):
    fake = FakeAi(monkeypatch)
    scan(client)
    fake.fail = False
    now = int(time.time() * 1000)
    sql(db_path, "UPDATE receipts SET parse_status='running', parse_claimed_at=? WHERE id=?",
        (now - 1 * 60 * 1000, RID))
    assert run(receipts.process_parse_queue()) == 0
    assert sql(db_path, "SELECT parse_status FROM receipts WHERE id=?", (RID,))[0][0] == "running"
    sql(db_path, "UPDATE receipts SET parse_claimed_at=? WHERE id=?", (now - 20 * 60 * 1000, RID))
    assert run(receipts.process_parse_queue()) == 1
    assert sql(db_path, "SELECT parse_status FROM receipts WHERE id=?", (RID,))[0][0] == "done"


def test_reparse_of_confirmed_receipt_replaces_items(client, monkeypatch):
    fake = FakeAi(monkeypatch)
    fake.fail = False
    assert scan(client).json()["parsed"] is True
    client.put(f"/api/receipts/{RID}", json={
        "id": RID, "store_name": "REWE", "status": "confirmed", "updated_at": 9_999_999_999_999,
        "items": [{"id": "old", "raw_text": "ALT", "name": "Alt", "position": 0}]})
    r = client.post(f"/api/receipts/{RID}/reparse")
    assert r.status_code == 200 and r.json()["parsed"] is True
    receipt = client.get(f"/api/receipts/{RID}").json()
    assert receipt["status"] == "review"
    assert [i["name"] for i in receipt["items"]] == ["Milch"]


def test_reparse_without_photo_and_unknown(client):
    client.put("/api/receipts/nophoto", json={"id": "nophoto", "updated_at": 1})
    r = client.post("/api/receipts/nophoto/reparse")
    assert r.status_code == 409 and "Kein Foto" in r.json()["detail"]
    assert client.post("/api/receipts/unknown/reparse").status_code == 404


def test_reparse_failure_keeps_values_and_queues(client, monkeypatch):
    fake = FakeAi(monkeypatch)
    fake.fail = False
    scan(client)
    before = client.get(f"/api/receipts/{RID}").json()
    fake.fail = True
    r = client.post(f"/api/receipts/{RID}/reparse")
    assert r.status_code == 200 and r.json()["parsed"] is False
    after = client.get(f"/api/receipts/{RID}").json()
    assert after["parse_status"] == "queued"
    assert after["store_name"] == before["store_name"]
    assert [i["name"] for i in after["items"]] == [i["name"] for i in before["items"]]
