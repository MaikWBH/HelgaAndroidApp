"""KI-Endpunkte (Phase 4, S5): SSE-Streams, JSON-Aufgaben und der Bon-Abgleich unter /api/receipts/reconcile."""
import logging
from typing import AsyncIterator

from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse

from . import ai as ai_module
from . import receipts
from .ai_client import AiUpstreamError
from .auth import require_auth
from .db import get_db
from .models import (
    AiClassifyRequest, AiGenerateRequest, AiNutritionRequest, AiRemixRequest,
    AiUrlImportRequest,
    ReceiptParseRequest,
    ReceiptParseResponse, ReceiptReconcileRequest,
    CategorizeRequest, CategorizeResponse,
)

logger = logging.getLogger(__name__)

router = APIRouter(dependencies=[Depends(require_auth)])


async def _sse(generator: AsyncIterator[str]) -> StreamingResponse:
    """SSE-Stream. Der erste Chunk wird vorab geholt: scheitert der KI-Anbieter schon dort, antwortet
    der Endpunkt mit 502 (AiUpstreamError, siehe main.py). Bricht er später ab, endet der Stream ohne [DONE]."""
    first = await anext(generator, None)

    async def event_stream():
        try:
            if first is not None:
                yield _frame(first)
            async for chunk in generator:
                yield _frame(chunk)
        except AiUpstreamError as exc:
            logger.warning("KI-Stream abgebrochen: %s", exc)
            return  # ohne [DONE]
        yield "data: [DONE]\n\n"

    return StreamingResponse(event_stream(), media_type="text/event-stream")


def _frame(chunk: str) -> str:
    # Zeilenumbrüche im Chunk escapen damit SSE-Format stimmt
    return "data: " + chunk.replace("\n", "\\n") + "\n\n"


@router.post("/api/ai/generate")
async def ai_generate(req: AiGenerateRequest):
    return await _sse(ai_module.stream_generate(req))


@router.post("/api/ai/remix")
async def ai_remix(req: AiRemixRequest):
    return await _sse(ai_module.stream_remix(req))


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
