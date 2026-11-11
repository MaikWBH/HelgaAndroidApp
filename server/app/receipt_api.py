"""Bon-Endpunkte (Phase 3a), siehe .claude/phasen/phase-3-daten/server-vertrag.md Abschnitt 3."""
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, Query

from . import receipts
from .auth import require_auth
from .db import get_db
from .models import (
    ReceiptIn, ReceiptListResponse, ReceiptOut, ReceiptPutResponse,
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
# (werden in den folgenden Schritten ergänzt)


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
