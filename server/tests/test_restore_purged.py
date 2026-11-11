import importlib.util
import sqlite3
from pathlib import Path

from app import db as app_db

from conftest import run

SCRIPT = Path(__file__).resolve().parent.parent / "scripts" / "restore_purged_receipts.py"
spec = importlib.util.spec_from_file_location("restore_purged_receipts", SCRIPT)
restore_script = importlib.util.module_from_spec(spec)
spec.loader.exec_module(restore_script)

DAY = 86_400_000
PURCHASE = 1_794_268_800_000


def _seed(db_path):
    run(app_db.init_db())
    con = sqlite3.connect(db_path)
    # automatisch gelöscht: 40 Tage nach dem Kauf
    con.execute("INSERT INTO receipts (id, store_name, total_amount, purchase_date, updated_at, deleted) "
                "VALUES ('auto', 'REWE', 9.99, ?, ?, 1)", (PURCHASE, PURCHASE + 40 * DAY))
    con.execute("INSERT INTO receipt_items (id, receipt_id, name, updated_at, deleted) "
                "VALUES ('i1', 'auto', 'Milch', ?, 1)", (PURCHASE + 40 * DAY,))
    # am Kauftag von Hand gelöscht
    con.execute("INSERT INTO receipts (id, store_name, purchase_date, updated_at, deleted) "
                "VALUES ('hand', 'Aldi', ?, ?, 1)", (PURCHASE, PURCHASE + DAY // 2))
    con.commit()
    con.close()


def _state(db_path, table, row_id):
    con = sqlite3.connect(db_path)
    try:
        return con.execute(f"SELECT deleted, server_seq FROM {table} WHERE id = ?", (row_id,)).fetchone()
    finally:
        con.close()


def test_lists_without_changing(db_path, capsys):
    _seed(db_path)
    assert restore_script.main(["--db", db_path]) == 0
    out = capsys.readouterr().out
    assert "REWE" in out and "[auto]" in out and "Aldi" not in out
    assert _state(db_path, "receipts", "auto")[0] == 1


def test_apply_restores_receipt_and_item(db_path):
    _seed(db_path)
    before_seq = sqlite3.connect(db_path).execute("SELECT seq FROM sync_state").fetchone()[0]
    assert restore_script.main(["--db", db_path, "--apply"]) == 0
    deleted, seq = _state(db_path, "receipts", "auto")
    assert deleted == 0 and seq == before_seq + 1
    assert _state(db_path, "receipt_items", "i1") == (0, seq)
    assert _state(db_path, "receipts", "hand")[0] == 1  # am Kauftag gelöscht bleibt gelöscht
