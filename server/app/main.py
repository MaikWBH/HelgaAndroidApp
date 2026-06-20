import os
import uuid
from contextlib import asynccontextmanager
from pathlib import Path
from typing import AsyncIterator

from fastapi import Depends, FastAPI, File, HTTPException, Query, Request, UploadFile, status
from fastapi.responses import FileResponse, StreamingResponse
from dotenv import load_dotenv

from .db import init_db, get_db, now_ms
from .models import (
    AiClassifyRequest, AiGenerateRequest, AiNutritionRequest, AiRemixRequest,
    AiUrlImportRequest,
    OffLookupBarcodeRequest, OffSearchRequest,
    ReceiptParseRequest,
    ReceiptParseResponse, ReceiptReconcileRequest,
    SyncPullResponse, SyncPushRequest, WeekplanGenerateRequest,
)
from .sync import pull_since, push_records
from . import ai as ai_module

load_dotenv()

API_KEY = os.getenv("API_KEY", "")
IMAGES_DIR = Path(os.getenv("IMAGES_DIR", "data/images"))
IMAGES_DIR.mkdir(parents=True, exist_ok=True)

ALLOWED_IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".webp", ".gif"}


@asynccontextmanager
async def lifespan(app: FastAPI):
    await init_db()
    yield


app = FastAPI(title="HelgaSyncServer", version="1.0.0", lifespan=lifespan)


# ── Auth ─────────────────────────────────────────────────────────────────────

def require_auth(request: Request):
    if not API_KEY:
        return  # Kein Key konfiguriert → offen (nur für lokale Entwicklung)
    key = request.headers.get("X-Api-Key", "")
    if key != API_KEY:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Ungültiger API-Key")


# ── Health ────────────────────────────────────────────────────────────────────

@app.get("/api/health")
async def health():
    return {"ok": True, "ts": now_ms()}


# ── Sync ──────────────────────────────────────────────────────────────────────

@app.get("/api/sync", response_model=SyncPullResponse, dependencies=[Depends(require_auth)])
async def sync_pull(since: int = Query(default=0, alias="since")):
    return await pull_since(since)


@app.post("/api/sync", response_model=SyncPullResponse, dependencies=[Depends(require_auth)])
async def sync_push(payload: SyncPushRequest):
    return await push_records(payload)


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


@app.post("/api/weekplan/generate", dependencies=[Depends(require_auth)])
async def weekplan_generate(req: WeekplanGenerateRequest):
    return await ai_module.generate_weekplan(req)


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


# ── Bilder ────────────────────────────────────────────────────────────────────

@app.post("/api/images/upload", dependencies=[Depends(require_auth)])
async def upload_image(file: UploadFile = File(...)):
    ext = Path(file.filename or "").suffix.lower()
    if ext not in ALLOWED_IMAGE_EXTS:
        raise HTTPException(status_code=400, detail=f"Dateitype nicht erlaubt: {ext}")

    image_uuid = str(uuid.uuid4())
    dest = IMAGES_DIR / f"{image_uuid}{ext}"
    content = await file.read()
    dest.write_bytes(content)

    return {"uuid": image_uuid, "filename": dest.name}


@app.get("/api/images/{filename}", dependencies=[Depends(require_auth)])
async def get_image(filename: str):
    # Pfad-Traversal verhindern
    safe_name = Path(filename).name
    path = IMAGES_DIR / safe_name
    if not path.exists() or not path.is_file():
        raise HTTPException(status_code=404, detail="Bild nicht gefunden")
    return FileResponse(path)


# ── Open Food Facts Lookups ──────────────────────────────────────────────────

@app.post("/api/off/lookup-barcode", dependencies=[Depends(require_auth)])
async def off_lookup_barcode(req: OffLookupBarcodeRequest):
    from . import off as off_module
    return await off_module.lookup_barcode(req.barcode)


@app.post("/api/off/search", dependencies=[Depends(require_auth)])
async def off_search(req: OffSearchRequest):
    from . import off as off_module
    return await off_module.search_products(req.query, req.limit)


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
