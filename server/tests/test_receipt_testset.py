import importlib.util
from pathlib import Path

SCRIPT = Path(__file__).resolve().parent.parent / "scripts" / "receipt_testset.py"
spec = importlib.util.spec_from_file_location("receipt_testset", SCRIPT)
testset = importlib.util.module_from_spec(spec)
spec.loader.exec_module(testset)

RESULT = {"store_name": "REWE Markt GmbH", "purchase_day": "2026-11-10", "total_amount": 12.34,
          "discount_total": 0.0, "items": [{"total_price": 10.0, "discount": 0.0},
                                           {"total_price": 2.34, "discount": 0.0}]}


def test_compare_hits():
    truth = {"store_name": "REWE", "purchase_date": "2026-11-10", "total_amount": 12.34, "item_count": 2}
    assert testset.compare(RESULT, truth) == {"store": True, "date": True, "total": True, "items": True}


def test_compare_misses_and_tolerances():
    truth = {"store_name": "Lidl", "purchase_date": "2026-11-11", "total_amount": 12.40, "item_count": 4}
    assert testset.compare(RESULT, truth) == {"store": False, "date": False, "total": False, "items": False}
    near = {"total_amount": 12.345, "item_count": 3}
    got = testset.compare(RESULT, near)
    assert got["total"] is True and got["items"] is True  # Summe ±0,01 und Positionen ±1
    assert testset.compare(RESULT, {})["store"] is None


def test_compare_accepts_epoch_ms_date():
    assert testset.compare(RESULT, {"purchase_date": 1794268800000})["date"] is True


def test_render_report_contains_rates():
    rows = [
        {"photo": "a.jpg", "model": "m1", "result": RESULT, "seconds": 1.2,
         "cmp": {"store": True, "date": True, "total": True, "items": True}},
        {"photo": "b.jpg", "model": "m1", "error": "Timeout", "seconds": 0.5},
    ]
    report = testset.render_report(rows)
    assert "| a.jpg | m1 | REWE Markt GmbH | 2026-11-10 | 12.34 | 2 | 12.34 | +0.00 | 1.2 |" in report
    assert "Fehler: Timeout" in report
    assert "1/2 (50 %)" in report  # Fehler zählt als Fehlschlag


def test_dry_run_lists_photos(tmp_path, capsys):
    (tmp_path / "b.jpg").write_bytes(b"")
    (tmp_path / "a.png").write_bytes(b"")
    (tmp_path / "notes.txt").write_text("x")
    assert testset.main(["--dir", str(tmp_path), "--dry-run"]) == 0
    assert capsys.readouterr().out.split() == ["a.png", "b.jpg"]


def test_missing_directory_exits_with_2(tmp_path, monkeypatch):
    monkeypatch.delenv("RECEIPT_TESTSET_DIR", raising=False)
    assert testset.main(["--dry-run"]) == 2
    assert testset.main(["--dir", str(tmp_path / "gibt-es-nicht"), "--dry-run"]) == 2
