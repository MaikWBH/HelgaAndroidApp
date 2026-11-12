"""Open-Food-Facts-Endpunkte (Phase 4, S4)."""
from fastapi import APIRouter, Depends

from . import off as off_module
from .auth import require_auth
from .models import OffLookupBarcodeRequest, OffSearchRequest

router = APIRouter(dependencies=[Depends(require_auth)])


@router.post("/api/off/lookup-barcode")
async def off_lookup_barcode(req: OffLookupBarcodeRequest):
    return await off_module.lookup_barcode(req.barcode)


@router.post("/api/off/search")
async def off_search(req: OffSearchRequest):
    return await off_module.search_products(req.query, req.limit)
