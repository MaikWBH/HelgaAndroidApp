"""Speicherschicht für Bons (Phase 3a). Reine DB-Logik: jede Funktion bekommt eine offene
`db`-Verbindung (aiosqlite, row_factory=Row) und committet nicht selbst."""
import base64
import logging
import re
import uuid
from pathlib import Path
from typing import Optional

from fastapi import HTTPException, UploadFile

from . import ai, categories, receipt_text, settings
from .db import now_ms
from .models import ReceiptIn, ReceiptItemIn

logger = logging.getLogger(__name__)

RECEIPT_ID_RE = re.compile(r"^[0-9a-fA-F-]{36}$")
IMAGE_EXTS = {".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".png": "image/png", ".webp": "image/webp"}

LEARNING_STATUSES = ("confirmed", "reconciled")

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
    before_items = {it["id"]: it for it in await _items_for(db, receipt_id)} if existing else {}

    # Positionen bereinigen
    clean_items = []
    for index, item in enumerate(items):
        item = ReceiptItemIn(**{"position": index, **dict(item)}).model_dump()  # fehlende Felder → Standardwerte
        item["receipt_id"] = receipt_id
        item["category"] = categories.coerce(item.get("category"))
        item["updated_at"] = item.get("updated_at") or updated_at
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

    if payload.get("status") in LEARNING_STATUSES and not payload.get("deleted"):
        await learn(db, before_items, {**payload, **fields}, clean_items)

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


# ── Anreichern und Lernen (Vertrag Abschnitt 4) ────────────────────────────────────────

async def enrich(db, receipt: dict, items: list[dict]) -> None:
    """Reichert frisch ausgelesene Positionen an (Markt, Lexikon, Kategorie). Verändert die Argumente."""
    if not receipt.get("store_id") and receipt.get("store_name"):
        match = receipt_text.match_store(receipt["store_name"], await _known_stores(db))
        if match:
            receipt["store_id"] = match["id"]
    key_store = receipt_text.store_key(receipt.get("store_id", ""), receipt.get("store_name", ""))

    for item in items:
        if item.get("is_deposit"):
            item["category"] = "pfand"
            continue
        raw_key = receipt_text.normalize_item_key(item.get("raw_text"))
        if key_store and raw_key:
            async with db.execute(
                "SELECT id, name, category, off_barcode FROM receipt_lexicon "
                "WHERE store_key = ? AND raw_key = ? AND deleted = 0",
                (key_store, raw_key),
            ) as cur:
                entry = await cur.fetchone()
            if entry:
                if entry[1]:
                    item["name"] = entry[1]
                if entry[2]:
                    item["category"] = entry[2]
                if entry[3]:
                    item["off_barcode"] = entry[3]
                await db.execute(
                    "UPDATE receipt_lexicon SET hits = hits + 1 WHERE id = ?", (entry[0],)
                )
        if not item.get("category"):
            product_key = receipt_text.normalize_item_key(item.get("name"))
            if product_key:
                async with db.execute(
                    "SELECT category FROM product_categories WHERE product_key = ?",
                    (product_key,),
                ) as cur:
                    row = await cur.fetchone()
                if row and row[0]:
                    item["category"] = row[0]


async def learn(db, before_items: dict, receipt: dict, items: list[dict]) -> None:
    """Lernt aus Korrekturen eines bestätigten Bons: Lexikon je Markt und Kategorie je Produkt."""
    key_store = receipt_text.store_key(receipt.get("store_id", ""), receipt.get("store_name", ""))
    now = now_ms()
    for item in items:
        before = before_items.get(item["id"])
        if before is None or item.get("is_deposit") or item.get("deleted"):
            continue
        raw_text = (item.get("raw_text") or "").strip()
        if not raw_text:
            continue
        changed = any(
            (item.get(field) or "") != (before.get(field) or "")
            for field in ("name", "category", "off_barcode")
        )
        if not changed:
            continue
        if key_store:
            await db.execute(
                "INSERT INTO receipt_lexicon (id, store_key, store_name, raw_key, raw_text, name, "
                "category, off_barcode, hits, updated_at, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, 0) "
                "ON CONFLICT(store_key, raw_key) DO UPDATE SET store_name = excluded.store_name, "
                "raw_text = excluded.raw_text, name = excluded.name, category = excluded.category, "
                "off_barcode = excluded.off_barcode, hits = receipt_lexicon.hits + 1, "
                "updated_at = excluded.updated_at, deleted = 0",
                (str(uuid.uuid4()), key_store, receipt.get("store_name", ""),
                 receipt_text.normalize_item_key(raw_text), raw_text, item.get("name", ""),
                 item.get("category", ""), item.get("off_barcode", ""), now),
            )
        product_key = receipt_text.normalize_item_key(item.get("name"))
        if item.get("category") and product_key:
            await db.execute(
                "INSERT INTO product_categories (product_key, product_name, category, source, updated_at) "
                "VALUES (?, ?, ?, 'user', ?) ON CONFLICT(product_key) DO UPDATE SET "
                "product_name = excluded.product_name, category = excluded.category, "
                "source = 'user', updated_at = excluded.updated_at",
                (product_key, item.get("name", ""), item["category"], now),
            )


async def list_lexicon(db, store_key: Optional[str] = None) -> list[dict]:
    where = "deleted = 0"
    params: list = []
    if store_key:
        where += " AND store_key = ?"
        params.append(store_key)
    cols = ["id", "store_key", "store_name", "raw_text", "name", "category", "off_barcode", "hits", "updated_at"]
    async with db.execute(
        f"SELECT {', '.join(cols)} FROM receipt_lexicon WHERE {where} ORDER BY store_name, raw_text",
        params,
    ) as cur:
        return [dict(zip(cols, row)) for row in await cur.fetchall()]


async def delete_lexicon_entry(db, entry_id: str) -> None:
    await db.execute(
        "UPDATE receipt_lexicon SET deleted = 1, updated_at = ? WHERE id = ?", (now_ms(), entry_id)
    )


# ── Foto, Scan, Auslesen (Vertrag Abschnitt 3.1) ───────────────────────────────────────

async def save_receipt_image(receipt_id: str, upload: UploadFile) -> str:
    """Speichert das Foto als `receipt_<id><ext>` in IMAGES_DIR und gibt den Dateinamen zurück."""
    if not RECEIPT_ID_RE.match(receipt_id or ""):
        raise HTTPException(status_code=400, detail="Ungültige Bon-ID")
    ext = Path(upload.filename or "").suffix.lower()
    if ext not in IMAGE_EXTS:
        raise HTTPException(status_code=400, detail=f"Dateityp nicht erlaubt: {ext}")
    dest = settings.IMAGES_DIR / f"receipt_{receipt_id}{ext}"
    total = 0
    try:
        with dest.open("wb") as out:
            while True:
                chunk = await upload.read(64 * 1024)
                if not chunk:
                    break
                total += len(chunk)
                if total > settings.MAX_RECEIPT_IMAGE_BYTES:
                    raise HTTPException(status_code=413, detail="Bild zu groß")
                out.write(chunk)
    except HTTPException:
        dest.unlink(missing_ok=True)
        raise
    return dest.name


def _has_content(parsed: dict) -> bool:
    return bool(parsed.get("items")) or (parsed.get("total_amount") or 0) > 0


def _item_payloads(parsed_items: list[dict]) -> list[dict]:
    return [
        {
            "id": str(uuid.uuid4()),
            "position": index,
            "raw_text": it["raw_text"],
            "name": it["name"],
            "quantity": it["quantity"],
            "unit": it["unit"],
            "unit_price": it["unit_price"],
            "total_price": it["total_price"],
            "discount": it["discount"],
            "is_deposit": it["is_deposit"],
            "category": it["category"],
            "off_barcode": "",
            "confidence": it["confidence"],
        }
        for index, it in enumerate(parsed_items)
    ]


async def _set_parse_state(db, receipt_id: str, **columns) -> None:
    names = ", ".join(f"{name} = ?" for name in columns)
    await db.execute(f"UPDATE receipts SET {names} WHERE id = ?", [*columns.values(), receipt_id])


async def _attempts(db, receipt_id: str) -> int:
    async with db.execute("SELECT parse_attempts FROM receipts WHERE id = ?", (receipt_id,)) as cur:
        row = await cur.fetchone()
    return row[0] if row else 0


async def apply_parse(db, receipt_id: str, parsed: dict, shopping_list_id: str,
                      captured_at: int, image_name: str = "") -> dict:
    """Schreibt ein erfolgreiches KI-Ergebnis (Status review, Positionen ersetzt, angereichert)."""
    existing = await _receipt_row(db, receipt_id)
    updated_at = max(now_ms(), (existing["updated_at"] if existing else 0) + 1)
    items = _item_payloads(parsed["items"])
    receipt = {
        "id": receipt_id,
        "store_id": existing["store_id"] if existing else "",
        "store_name": parsed["store_name"],
        "store_address": parsed["store_address"],
        "shopping_list_id": shopping_list_id or (existing["shopping_list_id"] if existing else ""),
        "purchase_date": (parsed["purchase_date"] or captured_at
                          or (existing["purchase_date"] if existing else 0) or now_ms()),
        "purchase_day": parsed["purchase_day"],
        "purchase_time": parsed["purchase_time"],
        "total_amount": parsed["total_amount"],
        "discount_total": parsed["discount_total"],
        "currency": "EUR",
        "status": "review",
        "source": "ai",
        "updated_at": updated_at,
        "deleted": 0,
    }
    await enrich(db, receipt, items)
    receipt["items"] = items
    await put_receipt(db, receipt)
    await _set_parse_state(
        db, receipt_id, confidence=parsed["confidence"], parse_status="done",
        parse_attempts=await _attempts(db, receipt_id) + 1,
        parse_model=parsed.get("model", ""), parse_claimed_at=0,
        **({"image_path": image_name} if image_name else {}),
    )
    return await get_receipt(db, receipt_id, include_deleted=True)


async def mark_failed_attempt(db, receipt_id: str, image_name: str, shopping_list_id: str,
                              captured_at: int, preliminary: Optional[ReceiptIn]) -> dict:
    """KI hat nicht geliefert: Bon vorläufig anlegen bzw. nur die Warteschlange vermerken."""
    existing = await _receipt_row(db, receipt_id)
    if existing is None:
        now = now_ms()
        if preliminary is not None:
            payload = preliminary.model_dump()
            payload.update({"id": receipt_id, "status": "provisional", "source": "on_device",
                            "updated_at": now, "deleted": 0})
            if shopping_list_id:
                payload["shopping_list_id"] = shopping_list_id
        else:
            payload = {"id": receipt_id, "shopping_list_id": shopping_list_id,
                       "purchase_date": captured_at or now, "status": "provisional",
                       "source": "", "updated_at": now, "items": []}
        await put_receipt(db, payload)
    attempts = await _attempts(db, receipt_id)
    await _set_parse_state(db, receipt_id, image_path=image_name, parse_status="queued",
                           parse_attempts=attempts + 1, parse_claimed_at=0)
    return await get_receipt(db, receipt_id, include_deleted=True)


async def _ai_parse(image_name: str, receipt_id: str) -> Optional[dict]:
    """Ruft die KI auf dem gespeicherten Foto auf; None bei Fehler oder leerem Ergebnis."""
    try:
        data = (settings.IMAGES_DIR / image_name).read_bytes()
        parsed = await ai.parse_receipt(
            base64.b64encode(data).decode("ascii"),
            IMAGE_EXTS.get(Path(image_name).suffix.lower(), "image/jpeg"),
        )
    except Exception as exc:  # KI-/Netz-/Dateifehler: Bon bleibt in der Warteschlange
        logger.warning("Bon-Auslesen fehlgeschlagen (%s): %s", receipt_id, exc)
        return None
    return parsed if _has_content(parsed) else None


async def parse_and_store(db, receipt_id: str, image_name: str, shopping_list_id: str,
                          captured_at: int, preliminary: Optional[ReceiptIn]) -> tuple[bool, dict]:
    """Liest das gespeicherte Foto mit der KI aus (Scan Schritt 4–5)."""
    parsed = await _ai_parse(image_name, receipt_id)
    if parsed is not None:
        return True, await apply_parse(db, receipt_id, parsed, shopping_list_id, captured_at,
                                       image_name)
    return False, await mark_failed_attempt(db, receipt_id, image_name, shopping_list_id,
                                            captured_at, preliminary)


async def set_image_path(db, receipt_id: str, image_name: str) -> bool:
    """Setzt nur `image_path` (Bild-Endpunkt); bumpt `server_seq`, damit andere Geräte es sehen."""
    if await _receipt_row(db, receipt_id) is None:
        return False
    seq = await next_seq(db)
    await db.execute("UPDATE receipts SET image_path = ?, server_seq = ? WHERE id = ?",
                     (image_name, seq, receipt_id))
    return True


# ── Re-Parse und Warteschlange ───────────────────────────────────────────────────────────

MAX_PARSE_ATTEMPTS = 5
STALE_RUNNING_MS = 15 * 60 * 1000


def _photo_exists(image_path: str) -> bool:
    return bool(image_path) and (settings.IMAGES_DIR / Path(image_path).name).is_file()


async def reparse(db_factory, receipt_id: str) -> tuple[bool, dict]:
    """Liest den Bon aus dem gespeicherten Foto neu aus. 404 unbekannt, 409 ohne Foto.

    Bei Misserfolg bleiben die bisherigen Werte stehen, nur `parse_status = 'queued'`.
    """
    async with db_factory() as db:
        row = await _receipt_row(db, receipt_id)
        if row is None or row["deleted"]:
            raise HTTPException(status_code=404, detail="Bon nicht gefunden")
        if not _photo_exists(row["image_path"]):
            raise HTTPException(status_code=409, detail="Kein Foto vorhanden")
        image_name = Path(row["image_path"]).name
        parsed = await _ai_parse(image_name, receipt_id)
        if parsed is not None:
            receipt = await apply_parse(db, receipt_id, parsed, "", 0, image_name)
            await db.commit()
            return True, receipt
        await _set_parse_state(db, receipt_id, parse_status="queued", parse_claimed_at=0,
                               parse_attempts=await _attempts(db, receipt_id) + 1)
        receipt = await get_receipt(db, receipt_id, include_deleted=True)
        await db.commit()
        return False, receipt


async def process_parse_queue(limit: int = 5, db_factory=None) -> int:
    """Arbeitet wartende Bons ab. Gibt die Anzahl erfolgreich ausgelesener Bons zurück."""
    from .db import get_db

    factory = db_factory or get_db
    async with factory() as db:
        await db.execute(
            "UPDATE receipts SET parse_status = 'queued' "
            "WHERE parse_status = 'running' AND parse_claimed_at < ?",
            (now_ms() - STALE_RUNNING_MS,),
        )
        await db.commit()
        async with db.execute(
            "SELECT id FROM receipts WHERE parse_status = 'queued' AND parse_attempts < ? "
            "AND deleted = 0 ORDER BY updated_at ASC LIMIT ?",
            (MAX_PARSE_ATTEMPTS, limit),
        ) as cur:
            candidate_ids = [row[0] for row in await cur.fetchall()]

    done = 0
    for receipt_id in candidate_ids:
        async with factory() as db:
            cursor = await db.execute(
                "UPDATE receipts SET parse_status = 'running', parse_claimed_at = ? "
                "WHERE id = ? AND parse_status = 'queued'",
                (now_ms(), receipt_id),
            )
            await db.commit()
            if cursor.rowcount != 1:
                continue  # von einem anderen Worker übernommen
            row = await _receipt_row(db, receipt_id)
            image_name = Path(row["image_path"]).name if row and row["image_path"] else ""
            parsed = await _ai_parse(image_name, receipt_id) if image_name else None
            if parsed is not None:
                await apply_parse(db, receipt_id, parsed, "", 0, image_name)
                done += 1
            else:
                attempts = await _attempts(db, receipt_id) + 1
                await _set_parse_state(
                    db, receipt_id, parse_attempts=attempts, parse_claimed_at=0,
                    parse_status="failed" if attempts >= MAX_PARSE_ATTEMPTS else "queued",
                )
            await db.commit()
    return done


async def queue_loop(interval_sec: int) -> None:
    """Hintergrund-Task: arbeitet die Warteschlange alle `interval_sec` Sekunden ab."""
    import asyncio

    while True:
        await asyncio.sleep(interval_sec)
        try:
            await process_parse_queue()
        except asyncio.CancelledError:
            raise
        except Exception:
            logger.exception("Bon-Warteschlange fehlgeschlagen")


# ── Auswertung (Phase 3b, Vertrag Abschnitt 6) ───────────────────────────────────────────

def _most_common(values: list[str]) -> str:
    counts: dict[str, int] = {}
    for value in values:
        counts[value] = counts.get(value, 0) + 1
    if not counts:
        return ""
    best = max(counts.values())
    return sorted(v for v, c in counts.items() if c == best)[0]  # bei Gleichstand stabil alphabetisch


async def aggregate_rows(db) -> list[dict]:
    """Berechnet die Auswertung bei jedem Aufruf aus den Bons (keine gespeicherte Tabelle).

    Grundlage: Bons mit `deleted = 0`, Status nicht `provisional`, gesetztem `purchase_day`; Positionen mit `deleted = 0`.
    """
    from datetime import date

    async with db.execute(
        "SELECT id, store_id, store_name, purchase_day, total_amount, discount_total FROM receipts "
        "WHERE deleted = 0 AND status != 'provisional' AND purchase_day != ''"
    ) as cur:
        receipts_rows = await cur.fetchall()
    async with db.execute(
        "SELECT r.purchase_day, ri.category, ri.is_deposit, ri.name, ri.quantity, ri.unit_price, "
        "ri.total_price, ri.discount FROM receipt_items ri JOIN receipts r ON ri.receipt_id = r.id "
        "WHERE ri.deleted = 0 AND r.deleted = 0 AND r.status != 'provisional' AND r.purchase_day != ''"
    ) as cur:
        item_rows = await cur.fetchall()

    total: dict[str, dict] = {}
    week: dict[str, dict] = {}
    store: dict[tuple, dict] = {}
    category: dict[tuple, dict] = {}
    product: dict[tuple, dict] = {}

    for _id, store_id, store_name, day, amount, discount_total in receipts_rows:
        month = day[:7]
        t = total.setdefault(month, {"amount": 0.0, "count": 0, "discount": 0.0})
        t["amount"] += amount or 0.0
        t["count"] += 1
        t["discount"] += discount_total or 0.0
        try:
            iso = date.fromisoformat(day).isocalendar()
        except ValueError:
            continue
        w = week.setdefault(f"{iso[0]}-W{iso[1]:02d}", {"amount": 0.0, "count": 0})
        w["amount"] += amount or 0.0
        w["count"] += 1
        key = receipt_text.store_key(store_id, store_name)
        st = store.setdefault((month, key), {"amount": 0.0, "count": 0, "names": []})
        st["amount"] += amount or 0.0
        st["count"] += 1
        st["names"].append(store_name or "")

    for day, cat, is_deposit, name, quantity, unit_price, total_price, discount in item_rows:
        month = day[:7]
        net = (total_price or 0.0) - (discount or 0.0)
        total.setdefault(month, {"amount": 0.0, "count": 0, "discount": 0.0})["discount"] += discount or 0.0
        cat_key = "pfand" if is_deposit else (cat or "sonstiges")
        c = category.setdefault((month, cat_key), {"amount": 0.0, "count": 0})
        c["amount"] += net
        c["count"] += 1
        if is_deposit:
            continue
        product_key = receipt_text.normalize_item_key(name)
        if not product_key:
            continue
        p = product.setdefault((month, product_key), {"amount": 0.0, "count": 0, "quantity": 0.0, "prices": [], "names": []})
        p["amount"] += net
        p["count"] += 1
        p["quantity"] += quantity or 0.0
        p["prices"].append(unit_price or 0.0)
        p["names"].append(name or "")

    rows: list[dict] = []
    for month, v in total.items():
        rows.append({"period": month, "dimension": "total", "key": "", "label": "", "amount": round(v["amount"], 2),
                     "quantity": 0.0, "count": v["count"], "avg_unit_price": 0.0, "discount": round(v["discount"], 2)})
    for period, v in week.items():
        rows.append({"period": period, "dimension": "week", "key": "", "label": "", "amount": round(v["amount"], 2),
                     "quantity": 0.0, "count": v["count"], "avg_unit_price": 0.0, "discount": 0.0})
    for (month, key), v in store.items():
        rows.append({"period": month, "dimension": "store", "key": key, "label": _most_common(v["names"]),
                     "amount": round(v["amount"], 2), "quantity": 0.0, "count": v["count"],
                     "avg_unit_price": 0.0, "discount": 0.0})
    for (month, key), v in category.items():
        rows.append({"period": month, "dimension": "category", "key": key, "label": "", "amount": round(v["amount"], 2),
                     "quantity": 0.0, "count": v["count"], "avg_unit_price": 0.0, "discount": 0.0})
    for (month, key), v in product.items():
        rows.append({"period": month, "dimension": "product", "key": key, "label": _most_common(v["names"]),
                     "amount": round(v["amount"], 2), "quantity": round(v["quantity"], 3), "count": v["count"],
                     "avg_unit_price": round(sum(v["prices"]) / len(v["prices"]), 2), "discount": 0.0})
    rows.sort(key=lambda r: (r["dimension"], r["period"], r["key"]))
    return rows
