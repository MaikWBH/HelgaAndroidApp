import hmac

from fastapi import HTTPException, Request, status

from . import settings


def require_auth(request: Request):
    api_key = settings.API_KEY
    if not api_key:
        return  # Kein Key konfiguriert → offen (nur für lokale Entwicklung)
    key = request.headers.get("X-Api-Key", "")
    if not hmac.compare_digest(key.encode(), api_key.encode()):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Ungültiger API-Key")
