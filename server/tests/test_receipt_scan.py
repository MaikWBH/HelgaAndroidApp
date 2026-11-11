import json

from app import ai, settings

RID = "11111111-2222-3333-4444-555555555555"
CAPTURED = 1794268800000


def parsed_result(**over):
    base = {
        "store_name": "REWE", "store_address": "Hauptstr. 1", "purchase_day": "2026-11-10",
        "purchase_time": "17:42", "purchase_date": 1794268800000, "total_amount": 1.99,
        "discount_total": 0.0, "confidence": 0.9, "model": "claude-haiku-5-5",
        "items": [{"raw_text": "G&G H-MILCH", "name": "H-Milch", "quantity": 1.0, "unit": "stk",
                   "unit_price": 1.99, "total_price": 1.99, "discount": 0.0, "is_deposit": 0,
                   "category": "", "confidence": 0.9}],
    }
    base.update(over)
    return base


def patch_parse(monkeypatch, result=None, error=None):
    calls = []

    async def fake(image_b64, mime_type="image/jpeg", model=None):
        calls.append(mime_type)
        if error:
            raise error
        return result

    monkeypatch.setattr(ai, "parse_receipt", fake)
    return calls


def scan(client, rid=RID, filename="bon.jpg", content=b"JPEGDATA", **form):
    data = {"receipt_id": rid, "captured_at": str(CAPTURED)}
    data.update(form)
    return client.post("/api/receipts/scan", files={"file": (filename, content, "image/jpeg")}, data=data)


def put(client, body):
    r = client.put(f"/api/receipts/{body['id']}", json=body)
    assert r.status_code == 200, r.text


def test_successful_scan(client, monkeypatch):
    # Lexikon vorbefüllen (S9): gleicher Markt, gleiches Kürzel
    put(client, {"id": "x", "store_name": "REWE", "status": "review", "updated_at": 100,
                 "items": [{"id": "a", "raw_text": "G&G H-MILCH", "name": "H-Milch"}]})
    put(client, {"id": "x", "store_name": "REWE", "status": "confirmed", "updated_at": 200,
                 "items": [{"id": "a", "raw_text": "G&G H-MILCH", "name": "Milch 3,5 %",
                            "category": "milch_eier", "updated_at": 200}]})
    patch_parse(monkeypatch, parsed_result())
    r = scan(client)
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["parsed"] is True
    receipt = body["receipt"]
    assert receipt["status"] == "review" and receipt["parse_status"] == "done"
    assert receipt["source"] == "ai" and receipt["purchase_day"] == "2026-11-10"
    assert receipt["image_path"] == f"receipt_{RID}.jpg"
    assert (settings.IMAGES_DIR / f"receipt_{RID}.jpg").read_bytes() == b"JPEGDATA"
    assert client.get(f"/api/images/receipt_{RID}.jpg").status_code == 200
    item = receipt["items"][0]
    assert item["name"] == "Milch 3,5 %" and item["category"] == "milch_eier"


def test_ai_failure_falls_back_to_preliminary(client, monkeypatch):
    patch_parse(monkeypatch, error=RuntimeError("KI down"))
    prelim = {"id": RID, "store_name": "Lidl", "purchase_date": 1700000000000, "total_amount": 4.5,
              "updated_at": 1, "items": [{"id": "p1", "raw_text": "BROT", "name": "Brot", "position": 0}]}
    r = scan(client, preliminary=json.dumps(prelim))
    assert r.status_code == 200
    body = r.json()
    assert body["parsed"] is False
    receipt = body["receipt"]
    assert receipt["status"] == "provisional" and receipt["parse_status"] == "queued"
    assert receipt["store_name"] == "Lidl" and receipt["source"] == "on_device"
    assert [i["name"] for i in receipt["items"]] == ["Brot"]


def test_ai_failure_without_preliminary_creates_shell(client, monkeypatch):
    patch_parse(monkeypatch, error=RuntimeError("KI down"))
    receipt = scan(client).json()["receipt"]
    assert receipt["status"] == "provisional" and receipt["parse_status"] == "queued"
    assert receipt["purchase_date"] == CAPTURED and receipt["items"] == []


def test_repeat_after_success_does_not_call_ai_again(client, monkeypatch):
    calls = patch_parse(monkeypatch, parsed_result())
    assert scan(client).json()["parsed"] is True
    again = scan(client).json()
    assert again["parsed"] is True and len(calls) == 1


def test_validation(client, monkeypatch):
    patch_parse(monkeypatch, parsed_result())
    assert scan(client, rid="../x").status_code == 400
    assert scan(client, filename="bon.gif").status_code == 400
    assert scan(client, filename="bon.exe").status_code == 400
    assert scan(client, content=b"x" * (9 * 1024 * 1024)).status_code == 413
    assert not (settings.IMAGES_DIR / f"receipt_{RID}.jpg").exists()


def test_image_endpoint_sets_only_image_path(client, monkeypatch):
    put(client, {"id": RID, "store_name": "Aldi", "status": "confirmed", "updated_at": 500,
                 "total_amount": 3.0, "items": []})
    before = client.get(f"/api/receipts/{RID}").json()
    r = client.post(f"/api/receipts/{RID}/image", files={"file": ("a.png", b"PNG", "image/png")})
    assert r.status_code == 200 and r.json() == {"image_path": f"receipt_{RID}.png"}
    after = client.get(f"/api/receipts/{RID}").json()
    assert after["image_path"] == f"receipt_{RID}.png"
    for field in ("store_name", "status", "total_amount", "updated_at"):
        assert after[field] == before[field]
    assert client.post("/api/receipts/22222222-2222-3333-4444-555555555555/image",
                       files={"file": ("a.png", b"PNG", "image/png")}).status_code == 404


def test_empty_ai_result_counts_as_failure(client, monkeypatch):
    patch_parse(monkeypatch, parsed_result(items=[], total_amount=0.0))
    body = scan(client).json()
    assert body["parsed"] is False and body["receipt"]["parse_status"] == "queued"
