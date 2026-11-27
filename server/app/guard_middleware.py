"""Prüft Schlüssel und Größe, bevor ein Request-Body gelesen wird (Phase 8, S5).

Reine ASGI-Middleware: sie liest den Body nie, sondern entscheidet nur anhand der Header. `require_auth`
bleibt als zweite Absicherung an den Routern bestehen.
"""
import hmac
import json

from . import settings

_PUBLIC_PATHS = {"/api/health"}


class GuardMiddleware:
    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http" or not scope["path"].startswith("/api/") or scope["path"] in _PUBLIC_PATHS:
            await self.app(scope, receive, send)
            return
        headers = {k.lower(): v for k, v in scope["headers"]}
        api_key = settings.API_KEY
        if api_key:
            sent = headers.get(b"x-api-key", b"")
            if not hmac.compare_digest(sent, api_key.encode()):
                await _reject(send, 401, "Nicht autorisiert")
                return
        length = headers.get(b"content-length", b"")
        if length.isdigit() and int(length) > settings.MAX_REQUEST_BYTES:
            await _reject(send, 413, "Anfrage zu groß")
            return
        await self.app(scope, receive, send)


async def _reject(send, status: int, detail: str):
    body = json.dumps({"detail": detail}).encode()
    await send({
        "type": "http.response.start",
        "status": status,
        "headers": [(b"content-type", b"application/json"), (b"content-length", str(len(body)).encode())],
    })
    await send({"type": "http.response.body", "body": body})
