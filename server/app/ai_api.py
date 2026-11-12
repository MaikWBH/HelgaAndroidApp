"""KI-Endpunkte (Phase 4, S5): SSE-Streams, JSON-Aufgaben und der Bon-Abgleich unter /api/receipts/reconcile."""
from typing import AsyncIterator

from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse

from . import ai as ai_module
from . import receipts
from .auth import require_auth
from .db import get_db
from .models import (
    AiClassifyRequest, AiGenerateRequest, AiNutritionRequest, AiRemixRequest,
    AiUrlImportRequest,
    ReceiptParseRequest,
    ReceiptParseResponse, ReceiptReconcileRequest,
    CategorizeRequest, CategorizeResponse,
)

router = APIRouter(dependencies=[Depends(require_auth)])


def _sse(generator: AsyncIterator[str]) -> StreamingResponse:
    async def event_stream():
        async for chunk in generator:
            # Zeilenumbrüche im Chunk escapen damit SSE-Format stimmt
            safe = chunk.replace("\n", "\\n")
            yield f"data: {safe}\n\n"
        yield "data: [DONE]\n\n"

    return StreamingResponse(event_stream(), media_type="text/event-stream")


@router.post("/api/ai/generate")
async def ai_generate(req: AiGenerateRequest):
    return _sse(ai_module.stream_generate(req))


@router.post("/api/ai/remix")
async def ai_remix(req: AiRemixRequest):
    return _sse(ai_module.stream_remix(req))


@router.post("/api/ai/classify")
async def ai_classify(req: AiClassifyRequest):
    result = await ai_module.classify(req)
    return result


@router.post("/api/ai/nutrition")
async def ai_nutrition(req: AiNutritionRequest):
    result = await ai_module.estimate_nutrition(req)
    return result


@router.post("/api/ai/import-url")
async def ai_import_url(req: AiUrlImportRequest):
    return await ai_module.import_url(req)


@router.post("/api/receipts/reconcile")
async def receipts_reconcile(req: ReceiptReconcileRequest):
    """Gleicht die abgehakte Einkaufsliste mit den Bon-Positionen per KI ab."""
    return await ai_module.reconcile_receipt(req.checked_items, req.receipt_items)


@router.post("/api/ai/parse-receipt", response_model=ReceiptParseResponse)
async def ai_parse_receipt(req: ReceiptParseRequest):
    """Liest einen fotografierten Kassenbon per Vision-Modell aus."""
    data = await ai_module.parse_receipt(req.image_base64, req.mime_type)
    return ReceiptParseResponse(**data)


@router.post("/api/ai/categorize-items", response_model=CategorizeResponse)
async def ai_categorize_items(req: CategorizeRequest):
    """Ordnet Artikelnamen einer Kategorie aus dem gemeinsamen Katalog zu (Lernspeicher, sonst Bon-Modell)."""
    async with get_db() as db:
        results = await receipts.categorize(db, req.names)
    return CategorizeResponse(results=results)
