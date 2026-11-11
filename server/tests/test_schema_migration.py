import sqlite3

from app import db as app_db

from conftest import run

# receipts/receipt_items im Stand vor Phase 3a (Altschema).
LEGACY_SCHEMA = """
CREATE TABLE sync_state (id INTEGER PRIMARY KEY, seq INTEGER NOT NULL DEFAULT 0);
INSERT INTO sync_state (id, seq) VALUES (0, 0);
CREATE TABLE receipts (
    id TEXT PRIMARY KEY,
    store_id TEXT NOT NULL DEFAULT '',
    store_name TEXT NOT NULL DEFAULT '',
    shopping_list_id TEXT NOT NULL DEFAULT '',
    purchase_date INTEGER NOT NULL DEFAULT 0,
    total_amount REAL NOT NULL DEFAULT 0.0,
    currency TEXT NOT NULL DEFAULT 'EUR',
    image_path TEXT NOT NULL DEFAULT '',
    local_image_uri TEXT NOT NULL DEFAULT '',
    raw_ocr_text TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'scanned',
    updated_at INTEGER NOT NULL DEFAULT 0,
    deleted INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE receipt_items (
    id TEXT PRIMARY KEY,
    receipt_id TEXT NOT NULL,
    position INTEGER NOT NULL DEFAULT 0,
    raw_text TEXT NOT NULL DEFAULT '',
    name TEXT NOT NULL DEFAULT '',
    quantity REAL NOT NULL DEFAULT 1.0,
    unit_price REAL NOT NULL DEFAULT 0.0,
    total_price REAL NOT NULL DEFAULT 0.0,
    matched_shopping_item_id TEXT NOT NULL DEFAULT '',
    match_status TEXT NOT NULL DEFAULT '',
    updated_at INTEGER NOT NULL DEFAULT 0,
    deleted INTEGER NOT NULL DEFAULT 0
);
"""


def _columns(path, table):
    con = sqlite3.connect(path)
    try:
        return {row[1] for row in con.execute(f"PRAGMA table_info({table})")}
    finally:
        con.close()


def _tables(path):
    con = sqlite3.connect(path)
    try:
        return {r[0] for r in con.execute("SELECT name FROM sqlite_master WHERE type='table'")}
    finally:
        con.close()


def test_fresh_db_has_new_columns_and_tables(db_path):
    run(app_db.init_db())
    receipt_cols = _columns(db_path, "receipts")
    for col in ("store_address", "purchase_day", "purchase_time", "discount_total", "deposit_total",
                "confidence", "source", "parse_status", "parse_attempts", "parse_claimed_at", "parse_model"):
        assert col in receipt_cols
    item_cols = _columns(db_path, "receipt_items")
    for col in ("unit", "discount", "is_deposit", "category", "off_barcode", "confidence"):
        assert col in item_cols
    tables = _tables(db_path)
    assert {"receipt_lexicon", "product_categories"} <= tables
    for removed in ("ingredient_product_mappings", "product_prices", "product_purchases"):
        assert removed not in tables


def test_existing_receipts_are_migrated(db_path):
    con = sqlite3.connect(db_path)
    con.executescript(LEGACY_SCHEMA)
    con.execute("INSERT INTO receipts (id, purchase_date, updated_at) VALUES ('a', 1794268800000, 5)")
    con.execute("INSERT INTO receipts (id, purchase_date, updated_at) VALUES ('b', 0, 5)")
    con.execute("INSERT INTO receipt_items (id, receipt_id, name) VALUES ('ia', 'a', 'Milch')")
    con.execute("INSERT INTO receipt_items (id, receipt_id, name) VALUES ('ib', 'b', 'Brot')")
    con.commit()
    con.close()

    run(app_db.init_db())

    con = sqlite3.connect(db_path)
    rows = dict(con.execute("SELECT id, purchase_day FROM receipts"))
    assert rows == {"a": "2026-11-10", "b": ""}
    assert {r[0] for r in con.execute("SELECT source FROM receipts")} == {"legacy"}
    assert {r[0] for r in con.execute("SELECT parse_status FROM receipts")} == {"none"}
    item = con.execute("SELECT unit, discount, name FROM receipt_items WHERE id = 'ia'").fetchone()
    assert item == ("", 0.0, "Milch")
    con.close()


def test_init_db_is_idempotent(db_path):
    run(app_db.init_db())
    con = sqlite3.connect(db_path)
    con.execute("INSERT INTO receipts (id, purchase_date, updated_at) VALUES ('x', 1794268800000, 1)")
    con.commit()
    con.close()
    run(app_db.init_db())
    con = sqlite3.connect(db_path)
    assert con.execute("SELECT COUNT(*) FROM receipts").fetchone()[0] == 1
    con.close()
