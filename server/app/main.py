import asyncio
import logging
from contextlib import asynccontextmanager
from typing import AsyncIterator

from fastapi import Depends, FastAPI, Query
from fastapi.responses import StreamingResponse

from .auth import require_auth
from .db import init_db, get_db, now_ms
from .models import (
    AiClassifyRequest, AiGenerateRequest, AiNutritionRequest, AiRemixRequest,
    AiUrlImportRequest,
    ReceiptParseRequest,
    ReceiptParseResponse, ReceiptReconcileRequest,
    CategorizeRequest, CategorizeResponse,
)
from . import ai as ai_module
from . import images_api, off_api, receipt_api, receipts, settings, sync_api

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


# ── Health ────────────────────────────────────────────────────────────────────

@app.get("/api/health")
async def health():
    return {"ok": True, "ts": now_ms()}


# ── KI ────────────────────────────────────────────────────────────────────────

def _sse(generator: AsyncIterator[str]) -> StreamingResponse:
    async def event_stream():
        async for chunk in generator:
            # Zeilenumbrüche im Chunk escapen damit SSE-Format stimmt
            safe = chunk.replace("\n", "\\n")
            yield f"data: {safe}\n\n"
        yield "data: [DONE]\n\n"

    return StreamingResponse(event_stream(), media_type="text/event-stream")


@app.post("/api/ai/generate", dependencies=[Depends(require_auth)])
async def ai_generate(req: AiGenerateRequest):
    return _sse(ai_module.stream_generate(req))


@app.post("/api/ai/remix", dependencies=[Depends(require_auth)])
async def ai_remix(req: AiRemixRequest):
    return _sse(ai_module.stream_remix(req))


@app.post("/api/ai/classify", dependencies=[Depends(require_auth)])
async def ai_classify(req: AiClassifyRequest):
    result = await ai_module.classify(req)
    return result


@app.post("/api/ai/nutrition", dependencies=[Depends(require_auth)])
async def ai_nutrition(req: AiNutritionRequest):
    result = await ai_module.estimate_nutrition(req)
    return result


@app.post("/api/ai/import-url", dependencies=[Depends(require_auth)])
async def ai_import_url(req: AiUrlImportRequest):
    return await ai_module.import_url(req)


@app.post("/api/receipts/reconcile", dependencies=[Depends(require_auth)])
async def receipts_reconcile(req: ReceiptReconcileRequest):
    """Gleicht die abgehakte Einkaufsliste mit den Bon-Positionen per KI ab."""
    return await ai_module.reconcile_receipt(req.checked_items, req.receipt_items)


@app.post("/api/ai/parse-receipt", response_model=ReceiptParseResponse,
          dependencies=[Depends(require_auth)])
async def ai_parse_receipt(req: ReceiptParseRequest):
    """Liest einen fotografierten Kassenbon per Vision-Modell aus."""
    data = await ai_module.parse_receipt(req.image_base64, req.mime_type)
    return ReceiptParseResponse(**data)


@app.post("/api/ai/categorize-items", response_model=CategorizeResponse, dependencies=[Depends(require_auth)])
async def ai_categorize_items(req: CategorizeRequest):
    """Ordnet Artikelnamen einer Kategorie aus dem gemeinsamen Katalog zu (Lernspeicher, sonst Bon-Modell)."""
    async with get_db() as db:
        results = await receipts.categorize(db, req.names)
    return CategorizeResponse(results=results)


# ── Vorschläge (Autocomplete) ─────────────────────────────────────────────────

@app.get("/api/suggestions/items", dependencies=[Depends(require_auth)])
async def suggest_items(q: str = Query(default="")):
    if not q:
        return {"suggestions": []}
    pattern = f"%{q}%"
    results = []
    async with get_db() as db:
        async with db.execute(
            "SELECT DISTINCT food FROM recipe_ingredients WHERE food LIKE ? AND deleted = 0 "
            "UNION "
            "SELECT DISTINCT name FROM shopping_items WHERE name LIKE ? AND deleted = 0 "
            "LIMIT 20",
            (pattern, pattern),
        ) as cursor:
            rows = await cursor.fetchall()
            results = [row[0] for row in rows if row[0]]
    return {"suggestions": results}


@app.get("/api/suggestions/aisles", dependencies=[Depends(require_auth)])
async def suggest_aisle(item: str = Query(default="")):
    if not item:
        return {"aisle": ""}
    async with get_db() as db:
        async with db.execute(
            "SELECT aisle_name FROM aisle_products WHERE product_name = ? AND deleted = 0 LIMIT 1",
            (item,),
        ) as cursor:
            row = await cursor.fetchone()
    return {"aisle": row[0] if row else ""}


@app.get("/api/suggestions/units", dependencies=[Depends(require_auth)])
async def suggest_units(item: str = Query(default="")):
    if not item:
        return {"units": []}
    async with get_db() as db:
        async with db.execute(
            "SELECT unit_name FROM product_units WHERE product_name = ? AND deleted = 0 "
            "ORDER BY sort_order LIMIT 5",
            (item,),
        ) as cursor:
            rows = await cursor.fetchall()
    return {"units": [r[0] for r in rows]}
