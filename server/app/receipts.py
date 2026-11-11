"""Speicherschicht für Bons (Phase 3a). Reine DB-Logik: jede Funktion bekommt eine offene
`db`-Verbindung (aiosqlite, row_factory=Row) und committet nicht selbst."""
from typing import Optional

from . import categories, receipt_text
from .db import now_ms

RECEIPT_COLS = [
    "id", "store_id", "store_name", "store_address", "shopping_list_id", "purchase_date",
    "purchase_day", "purchase_time", "total_amount", "discount_total", "deposit_total",
    "currency", "image_path", "status", "source", "confidence", "parse_status",
    "updated_at", "deleted", "server_seq",
]

ITEM_COLS = [
    "id", "receipt_id", "position", "raw_text", "name", "quantity", "unit", "unit_price",
    "total_price", "discount", "is_deposit", "category", "off_barcode", "confidence",
    "matched_shopping_item_id", "match_status", "updated_at", "deleted",
]


async def next_seq(db) -> int:
    """Reserviert die nächste Commit-Sequenz (wie sync.push_records)."""
    await db.execute("UPDATE sync_state SET seq = seq + 1 WHERE id = 0")
    async with db.execute("SELECT seq FROM sync_state WHERE id = 0") as cur:
        return (await cur.fetchone())[0]


async def current_seq(db) -> int:
    async with db.execute("SELECT seq FROM sync_state WHERE id = 0") as cur:
        row = await cur.fetchone()
        return row[0] if row else 0


async def _items_for(db, receipt_id: str) -> list[dict]:
    cols = ", ".join(ITEM_COLS)
    async with db.execute(
        f"SELECT {cols} FROM receipt_items WHERE receipt_id = ? AND deleted = 0 "
        f"ORDER BY position, id",
        (receipt_id,),
    ) as cur:
        return [dict(zip(ITEM_COLS, row)) for row in await cur.fetchall()]


async def _receipt_row(db, receipt_id: str) -> Optional[dict]:
    cols = ", ".join(RECEIPT_COLS)
    async with db.execute(f"SELECT {cols} FROM receipts WHERE id = ?", (receipt_id,)) as cur:
        row = await cur.fetchone()
    return dict(zip(RECEIPT_COLS, row)) if row else None


async def get_receipt(db, receipt_id: str, include_deleted: bool = False) -> Optional[dict]:
    receipt = await _receipt_row(db, receipt_id)
    if receipt is None:
        return None
    if receipt["deleted"]:
        if not include_deleted:
            return None
        receipt["items"] = []  # gelöschte Bons kommen ohne Positionen
        return receipt
    receipt["items"] = await _items_for(db, receipt_id)
    return receipt


async def list_receipts(db, from_day: Optional[str], to_day: Optional[str],
                        since_seq: int, include_deleted: bool) -> tuple[int, list[dict]]:
    server_seq = await current_seq(db)  # vor dem Lesen der Zeilen (wie sync.pull_since)
    where = ["server_seq > ?"]
    params: list = [since_seq]
    if from_day:
        where.append("(purchase_day = '' OR purchase_day >= ?)")
        params.append(from_day)
    if to_day:
        where.append("purchase_day <= ?")
        params.append(to_day)
    if not include_deleted:
        where.append("deleted = 0")
    cols = ", ".join(RECEIPT_COLS)
    async with db.execute(
        f"SELECT {cols} FROM receipts WHERE {' AND '.join(where)} "
        f"ORDER BY purchase_date DESC, id",
        params,
    ) as cur:
        receipts = [dict(zip(RECEIPT_COLS, row)) for row in await cur.fetchall()]
    for receipt in receipts:
        receipt["items"] = [] if receipt["deleted"] else await _items_for(db, receipt["id"])
    return server_seq, receipts


async def _known_stores(db) -> list[dict]:
    async with db.execute("SELECT id, name FROM stores WHERE deleted = 0") as cur:
        return [{"id": row[0], "name": row[1]} for row in await cur.fetchall()]


async def put_receipt(db, payload: dict) -> tuple[bool, dict]:
    """Legt einen Bon an oder übernimmt ihn (LWW über updated_at).

    Gibt (applied, bon) zurück. Bei `applied == False` ist der Server-Stand neuer.
    """
    receipt_id = payload["id"]
    existing = await _receipt_row(db, receipt_id)
    if existing and (existing["updated_at"] or 0) > payload["updated_at"]:
        return False, await get_receipt(db, receipt_id, include_deleted=True)

    seq = await next_seq(db)
    updated_at = payload["updated_at"]
    items = payload.get("items") or []

    # Positionen bereinigen
    clean_items = []
    for index, item in enumerate(items):
        item = dict(item)
        item["receipt_id"] = receipt_id
        item["category"] = categories.coerce(item.get("category"))
        item["updated_at"] = item.get("updated_at") or updated_at
        item.setdefault("position", index)
        clean_items.append(item)

    deposit_total = round(sum(
        it.get("total_price", 0.0) for it in clean_items
        if it.get("is_deposit") and not it.get("deleted")
    ), 2)

    purchase_date = payload.get("purchase_date", 0)
    purchase_day = payload.get("purchase_day", "")
    if purchase_day:
        day_ms = receipt_text.day_to_ms(purchase_day)
        if day_ms:
            purchase_date = day_ms

    store_id = payload.get("store_id", "")
    if not store_id and payload.get("store_name"):
        match = receipt_text.match_store(payload["store_name"], await _known_stores(db))
        if match:
            store_id = match["id"]

    fields = {
        "store_id": store_id,
        "store_name": payload.get("store_name", ""),
        "store_address": payload.get("store_address", ""),
        "shopping_list_id": payload.get("shopping_list_id", ""),
        "purchase_date": purchase_date,
        "purchase_day": purchase_day,
        "purchase_time": payload.get("purchase_time", ""),
        "total_amount": payload.get("total_amount", 0.0),
        "discount_total": payload.get("discount_total", 0.0),
        "deposit_total": deposit_total,
        "currency": payload.get("currency", "EUR"),
        "status": payload.get("status", "scanned"),
        "source": payload.get("source", ""),
        "updated_at": updated_at,
        "deleted": payload.get("deleted", 0),
        "server_seq": seq,
    }
    cols = ["id"] + list(fields)
    values = [receipt_id] + list(fields.values())
    update_set = ", ".join(f"{c} = excluded.{c}" for c in fields)
    await db.execute(
        f"INSERT INTO receipts ({', '.join(cols)}) VALUES ({', '.join('?' * len(cols))}) "
        f"ON CONFLICT(id) DO UPDATE SET {update_set}",
        values,
    )

    # Positionen upserten, fehlende serverseitige auf deleted = 1
    sent_ids = {it["id"] for it in clean_items}
    item_cols = [c for c in ITEM_COLS]
    for item in clean_items:
        row_values = [item.get(c) for c in item_cols] + [seq]
        all_cols = item_cols + ["server_seq"]
        update_set_items = ", ".join(f"{c} = excluded.{c}" for c in all_cols if c != "id")
        await db.execute(
            f"INSERT INTO receipt_items ({', '.join(all_cols)}) "
            f"VALUES ({', '.join('?' * len(all_cols))}) "
            f"ON CONFLICT(id) DO UPDATE SET {update_set_items}",
            row_values,
        )
    async with db.execute(
        "SELECT id FROM receipt_items WHERE receipt_id = ? AND deleted = 0", (receipt_id,)
    ) as cur:
        stale = [row[0] for row in await cur.fetchall() if row[0] not in sent_ids]
    for item_id in stale:
        await db.execute(
            "UPDATE receipt_items SET deleted = 1, updated_at = ?, server_seq = ? WHERE id = ?",
            (updated_at, seq, item_id),
        )

    return True, await get_receipt(db, receipt_id, include_deleted=True)


async def delete_receipt(db, receipt_id: str, updated_at: int) -> None:
    """Soft-Delete von Bon und Positionen; das Foto bleibt liegen (Archiv)."""
    existing = await _receipt_row(db, receipt_id)
    if existing is None:
        return
    stamp = max(updated_at or 0, existing["updated_at"] or 0)
    seq = await next_seq(db)
    await db.execute(
        "UPDATE receipts SET deleted = 1, updated_at = ?, server_seq = ? WHERE id = ?",
        (stamp, seq, receipt_id),
    )
    await db.execute(
        "UPDATE receipt_items SET deleted = 1, updated_at = ?, server_seq = ? WHERE receipt_id = ?",
        (stamp, seq, receipt_id),
    )
