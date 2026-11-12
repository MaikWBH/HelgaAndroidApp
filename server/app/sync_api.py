"""Sync-Endpunkte (Phase 4, S4): GET und POST /api/sync."""
from fastapi import APIRouter, Depends, Query

from .auth import require_auth
from .models import SyncPullResponse, SyncPushRequest
from .sync import pull_since, push_records

router = APIRouter(dependencies=[Depends(require_auth)])


@router.get("/api/sync", response_model=SyncPullResponse)
async def sync_pull(since: int = Query(default=0, alias="since")):
    return await pull_since(since)


@router.post("/api/sync", response_model=SyncPullResponse)
async def sync_push(payload: SyncPushRequest):
    return await push_records(payload)
