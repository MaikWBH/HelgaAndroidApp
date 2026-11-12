"""Bild-Endpunkte (Phase 4, S4): Upload und Abruf unter /api/images."""
import uuid
from pathlib import Path

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile
from fastapi.responses import FileResponse

from . import settings
from .auth import require_auth

ALLOWED_IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".webp", ".gif"}

router = APIRouter(dependencies=[Depends(require_auth)])


@router.post("/api/images/upload")
async def upload_image(file: UploadFile = File(...)):
    ext = Path(file.filename or "").suffix.lower()
    if ext not in ALLOWED_IMAGE_EXTS:
        raise HTTPException(status_code=400, detail=f"Dateitype nicht erlaubt: {ext}")

    image_uuid = str(uuid.uuid4())
    dest = settings.IMAGES_DIR / f"{image_uuid}{ext}"
    content = await file.read()
    dest.write_bytes(content)

    return {"uuid": image_uuid, "filename": dest.name}


@router.get("/api/images/{filename}")
async def get_image(filename: str):
    # Pfad-Traversal verhindern
    safe_name = Path(filename).name
    path = settings.IMAGES_DIR / safe_name
    if not path.exists() or not path.is_file():
        raise HTTPException(status_code=404, detail="Bild nicht gefunden")
    return FileResponse(path)
