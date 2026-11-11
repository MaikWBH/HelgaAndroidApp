"""Bon-Endpunkte (Phase 3a), siehe .claude/phasen/phase-3-daten/server-vertrag.md Abschnitt 3."""
import json
from typing import Optional

from fastapi import APIRouter, Depends, File, Form, HTTPException, Query, UploadFile

from . import receipts
from .auth import require_auth
from .db import get_db
from .models import (
    LexiconListResponse, ReceiptIn, ReceiptListResponse, ReceiptOut, ReceiptPutResponse,
    ReceiptScanResponse,
)

router = APIRouter(prefix="/api/receipts", dependencies=[Depends(require_auth)])


@router.get("", response_model=ReceiptListResponse)
async def list_receipts(
    from_day: Optional[str] = Query(default=None),
    to_day: Optional[str] = Query(default=None),
    since_seq: int = Query(default=0),
    include_deleted: int = Query(default=0),
):
    async with get_db() as db:
        server_seq, rows = await receipts.list_receipts(
            db, from_day, to_day, since_seq, bool(include_deleted)
        )
    return ReceiptListResponse(server_seq=server_seq, receipts=rows)


# ── feste Pfade (/scan, /lexicon, /aggregates) stehen VOR /{receipt_id} ─────────

@router.get("/lexicon", response_model=LexiconListResponse)
async def get_lexicon(store_key: Optional[str] = Query(default=None)):
    async with get_db() as db:
        entries = await receipts.list_lexicon(db, store_key)
    return LexiconListResponse(entries=entries)


@router.delete("/lexicon/{entry_id}")
async def delete_lexicon_entry(entry_id: str):
    async with get_db() as db:
        await receipts.delete_lexicon_entry(db, entry_id)
        await db.commit()
    return {"ok": True}


@router.post("/scan", response_model=ReceiptScanResponse)
async def scan_receipt(
    file: UploadFile = File(...),
    receipt_id: str = Form(...),
    shopping_list_id: str = Form(default=""),
    captured_at: int = Form(default=0),
    preliminary: Optional[str] = Form(default=None),
):
    image_name = await receipts.save_receipt_image(receipt_id, file)
    prelim: Optional[ReceiptIn] = None
    if preliminary:
        try:
            prelim = ReceiptIn.model_validate(json.loads(preliminary))
        except (ValueError, TypeError):
            prelim = None  # unlesbare Vorab-Werte werden ignoriert
    async with get_db() as db:
        async with db.execute(
            "SELECT parse_status FROM receipts WHERE id = ? AND deleted = 0", (receipt_id,)
        ) as cur:
            row = await cur.fetchone()
        if row and row[0] == "done":  # idempotente Wiederholung: nicht erneut auslesen
            await receipts.set_image_path(db, receipt_id, image_name)
            receipt = await receipts.get_receipt(db, receipt_id)
            await db.commit()
            return ReceiptScanResponse(parsed=True, receipt=receipt)
        parsed, receipt = await receipts.parse_and_store(
            db, receipt_id, image_name, shopping_list_id, captured_at, prelim
        )
        await db.commit()
    return ReceiptScanResponse(parsed=parsed, receipt=receipt)

# (weitere feste Pfade folgen in den nächsten Schritten, jeweils vor /{receipt_id})


@router.get("/{receipt_id}", response_model=ReceiptOut)
async def get_receipt(receipt_id: str):
    async with get_db() as db:
        receipt = await receipts.get_receipt(db, receipt_id)
    if receipt is None:
        raise HTTPException(status_code=404, detail="Bon nicht gefunden")
    return receipt


@router.put("/{receipt_id}", response_model=ReceiptPutResponse)
async def put_receipt(receipt_id: str, body: ReceiptIn):
    payload = body.model_dump()
    payload["id"] = receipt_id
    async with get_db() as db:
        applied, receipt = await receipts.put_receipt(db, payload)
        await db.commit()
    return ReceiptPutResponse(applied=applied, receipt=receipt)


@router.delete("/{receipt_id}")
async def delete_receipt(receipt_id: str, updated_at: int = Query(default=0)):
    async with get_db() as db:
        await receipts.delete_receipt(db, receipt_id, updated_at)
        await db.commit()
    return {"ok": True}


@router.post("/{receipt_id}/image")
async def upload_receipt_image(receipt_id: str, file: UploadFile = File(...)):
    async with get_db() as db:
        async with db.execute("SELECT 1 FROM receipts WHERE id = ?", (receipt_id,)) as cur:
            if await cur.fetchone() is None:
                raise HTTPException(status_code=404, detail="Bon nicht gefunden")
        image_name = await receipts.save_receipt_image(receipt_id, file)
        await receipts.set_image_path(db, receipt_id, image_name)
        await db.commit()
    return {"image_path": image_name}


@router.post("/{receipt_id}/reparse", response_model=ReceiptScanResponse)
async def reparse_receipt(receipt_id: str):
    parsed, receipt = await receipts.reparse(get_db, receipt_id)
    return ReceiptScanResponse(parsed=parsed, receipt=receipt)
