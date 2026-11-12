import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

from .db import init_db, now_ms
from . import (
    ai_api, images_api, off_api, receipt_api, receipts, settings, suggestions_api, sync_api,
)

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    if not settings.API_KEY:
        if not settings.ALLOW_NO_AUTH:
            raise RuntimeError("API_KEY fehlt. Für lokale Entwicklung ALLOW_NO_AUTH=1 setzen.")
        logger.warning("API läuft ohne Authentifizierung (ALLOW_NO_AUTH)")
    await init_db()
    queue_task = None
    if settings.RECEIPT_QUEUE_INTERVAL_SEC > 0:
        queue_task = asyncio.create_task(receipts.queue_loop(settings.RECEIPT_QUEUE_INTERVAL_SEC))
    try:
        yield
    finally:
        if queue_task is not None:
            queue_task.cancel()


app = FastAPI(title="HelgaSyncServer", version="1.0.0", lifespan=lifespan)
app.include_router(receipt_api.router)
app.include_router(sync_api.router)
app.include_router(images_api.router)
app.include_router(off_api.router)
app.include_router(ai_api.router)
app.include_router(suggestions_api.router)


# ── Health ────────────────────────────────────────────────────────────────────

@app.get("/api/health")
async def health():
    return {"ok": True, "ts": now_ms()}
