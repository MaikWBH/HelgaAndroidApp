"""Anbieter-Schicht für KI-Aufrufe (Phase 4, S7): Modellwahl, Nutzlasten und HTTP zu OpenAI und Anthropic.

Konfiguration kommt zur Aufrufzeit aus `settings`. Prompts und Auswertung der Antworten stehen in `ai.py`.
"""
import json
from typing import AsyncIterator

import httpx

from . import settings

ANTHROPIC_BASE = "https://api.anthropic.com/v1"

DEFAULT_MODEL = {
    "openai": "gpt-4o-mini",
    "anthropic": "claude-haiku-4-5-20251001",
}

# Referenzmodelle für das Testset-Skript (Vergleich gegen das Bon-Modell). Dichte deutsche
# Bons haben früher kleine Modelle überfordert (Sonnet ~97 % vs. Mini deutlich darunter).
DEFAULT_VISION_MODEL = {
    "openai": "gpt-4o",
    "anthropic": "claude-sonnet-4-6",
}

# Bon-Modell (Entscheidung Phase 3: günstigstes Modell, Qualität per Testset geprüft).
DEFAULT_RECEIPT_MODEL = {
    "openai": "gpt-4o-mini",
    "anthropic": "claude-haiku-5-5",
}

# Modelle, die keine Sampling-Parameter (temperature/top_p/top_k) akzeptieren und
# stattdessen output_config.effort verwenden.
_EFFORT_MODEL_PREFIXES = (
    "claude-haiku-5", "claude-sonnet-5", "claude-opus-5", "claude-opus-4-7",
    "claude-opus-4-8", "claude-fable", "claude-mythos",
)

RECEIPT_MAX_TOKENS = 16000


def _openai_base() -> str:
    return settings.AI_API_BASE or "https://api.openai.com/v1"


# ── Modellwahl ────────────────────────────────────────────────────────────────

def text_model() -> str:
    return settings.AI_MODEL or DEFAULT_MODEL.get(settings.AI_PROVIDER, "gpt-4o-mini")


def vision_model() -> str:
    """Modell für Bild-Eingaben (Kassenbon-Erkennung).

    Reihenfolge:
    1. settings.AI_VISION_MODEL – explizite Wahl (empfohlen, um z. B. Sonnet zu erzwingen).
    2. settings.AI_MODEL – falls bewusst gesetzt, respektieren wir die Nutzerwahl.
    3. Starker Provider-Default (NICHT der günstige Text-Default), weil dichte
       deutsche Bons sonst unzuverlässig erkannt werden.
    """
    return settings.AI_VISION_MODEL or settings.AI_MODEL or DEFAULT_VISION_MODEL.get(settings.AI_PROVIDER, "gpt-4o")


def receipt_model() -> str:
    """Modell für alle Bon-Aufrufe. settings.AI_VISION_MODEL wirkt hier bewusst nicht mehr."""
    return settings.AI_RECEIPT_MODEL or DEFAULT_RECEIPT_MODEL.get(settings.AI_PROVIDER, "gpt-4o-mini")


def uses_effort(model: str) -> bool:
    return model.startswith(_EFFORT_MODEL_PREFIXES)


def receipt_payload(model: str, system: str, content: list) -> dict:
    """Anthropic-Nutzlast für Bon-Aufrufe (nicht gestreamt).

    Neue Modelle (z. B. Haiku 5.5) lehnen Sampling-Parameter mit HTTP 400 ab: dort
    `output_config.effort` statt `temperature`, und kein `thinking`.
    """
    payload: dict = {
        "model": model,
        "system": system,
        "max_tokens": RECEIPT_MAX_TOKENS,
        "messages": [{"role": "user", "content": content}],
    }
    if uses_effort(model):
        payload["output_config"] = {"effort": settings.AI_RECEIPT_EFFORT}
    else:
        payload["temperature"] = 0
    return payload


# ── Header (Basis-URL separat: _openai_base() bzw. ANTHROPIC_BASE) ────────────

def openai_headers() -> dict:
    return {"Authorization": f"Bearer {settings.AI_API_KEY}", "Content-Type": "application/json"}


def anthropic_headers() -> dict:
    return {
        "x-api-key": settings.AI_API_KEY,
        "anthropic-version": "2023-06-01",
        "content-type": "application/json",
    }


# ── Text-Stream (SSE vom Anbieter) ────────────────────────────────────────────

async def stream_text(system: str, user: str) -> AsyncIterator[str]:
    if settings.AI_PROVIDER == "anthropic":
        async for chunk in _stream_anthropic(system, user):
            yield chunk
    else:
        async for chunk in _stream_openai(system, user):
            yield chunk


async def _stream_openai(system: str, user: str) -> AsyncIterator[str]:
    base = _openai_base()
    headers = openai_headers()
    payload = {
        "model": text_model(),
        "messages": [{"role": "system", "content": system}, {"role": "user", "content": user}],
        "temperature": 0.7,
        "stream": True,
    }
    async with httpx.AsyncClient(timeout=120) as client:
        async with client.stream("POST", f"{base}/chat/completions",
                                 headers=headers, json=payload) as resp:
            resp.raise_for_status()
            async for line in resp.aiter_lines():
                if not line.startswith("data:"):
                    continue
                data = line[5:].strip()
                if data == "[DONE]":
                    break
                try:
                    obj = json.loads(data)
                    delta = obj["choices"][0]["delta"].get("content", "")
                    if delta:
                        yield delta
                except (json.JSONDecodeError, KeyError, IndexError):
                    continue


async def _stream_anthropic(system: str, user: str) -> AsyncIterator[str]:
    headers = anthropic_headers()
    payload = {
        "model": text_model(),
        "system": system,
        "messages": [{"role": "user", "content": user}],
        "max_tokens": 4096,
        "temperature": 0.7,
        "stream": True,
    }
    async with httpx.AsyncClient(timeout=120) as client:
        async with client.stream("POST", f"{ANTHROPIC_BASE}/messages",
                                 headers=headers, json=payload) as resp:
            resp.raise_for_status()
            async for line in resp.aiter_lines():
                if not line.startswith("data:"):
                    continue
                data = line[5:].strip()
                try:
                    obj = json.loads(data)
                    if obj.get("type") == "content_block_delta":
                        delta = obj.get("delta", {}).get("text", "")
                        if delta:
                            yield delta
                except (json.JSONDecodeError, KeyError):
                    continue


async def complete_text(system: str, user: str) -> str:
    """Sammelt den Text-Stream komplett zu einer Antwort."""
    chunks = []
    async for chunk in stream_text(system, user):
        chunks.append(chunk)
    return "".join(chunks)


# ── Vision (Bild-Eingabe, einmaliger Aufruf) ──────────────────────────────────

async def vision_text(system: str, user_text: str, image_b64: str, mime_type: str) -> str:
    if settings.AI_PROVIDER == "anthropic":
        return await _vision_anthropic(system, user_text, image_b64, mime_type)
    return await _vision_openai(system, user_text, image_b64, mime_type)


async def _vision_openai(system: str, user_text: str, image_b64: str, mime_type: str) -> str:
    base = _openai_base()
    headers = openai_headers()
    payload = {
        "model": vision_model(),
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": [
                {"type": "text", "text": user_text},
                {"type": "image_url",
                 # "high" erzwingt die hochauflösende Bildanalyse – wichtig, damit
                 # kleiner Bon-Druck (Artikelnamen/Preise) nicht verschluckt wird.
                 "image_url": {"url": f"data:{mime_type};base64,{image_b64}",
                               "detail": "high"}},
            ]},
        ],
        "temperature": 0,
    }
    async with httpx.AsyncClient(timeout=120) as client:
        resp = await client.post(f"{base}/chat/completions", headers=headers, json=payload)
        resp.raise_for_status()
        obj = resp.json()
        return obj["choices"][0]["message"].get("content") or ""


async def _vision_anthropic(system: str, user_text: str, image_b64: str, mime_type: str) -> str:
    headers = anthropic_headers()
    payload = {
        "model": vision_model(),
        "system": system,
        "max_tokens": 8192,
        "temperature": 0,
        "messages": [
            {"role": "user", "content": [
                {"type": "image", "source": {
                    "type": "base64", "media_type": mime_type, "data": image_b64}},
                {"type": "text", "text": user_text},
            ]},
        ],
    }
    async with httpx.AsyncClient(timeout=120) as client:
        resp = await client.post(f"{ANTHROPIC_BASE}/messages", headers=headers, json=payload)
        resp.raise_for_status()
        obj = resp.json()
        return "".join(p.get("text", "") for p in obj.get("content", []) if p.get("type") == "text")


# ── Bon-Aufruf (einmalig, mit oder ohne Bild, Bon-Modell) ─────────────────────

async def receipt_text(system: str, user_text: str, image_b64: str | None = None,
                       mime_type: str = "image/jpeg", model: str | None = None) -> str:
    """Einmaliger Bon-Aufruf mit dem Bon-Modell, mit oder ohne Bild."""
    used_model = model or receipt_model()
    if settings.AI_PROVIDER == "anthropic":
        content: list = []
        if image_b64:
            content.append({"type": "image", "source": {
                "type": "base64", "media_type": mime_type, "data": image_b64}})
        content.append({"type": "text", "text": user_text})
        payload = receipt_payload(used_model, system, content)
        async with httpx.AsyncClient(timeout=120) as client:
            resp = await client.post(f"{ANTHROPIC_BASE}/messages", headers=anthropic_headers(), json=payload)
            resp.raise_for_status()
            obj = resp.json()
            return "".join(
                p.get("text", "") for p in obj.get("content", []) if p.get("type") == "text"
            )

    base = _openai_base()
    headers = openai_headers()
    if image_b64:
        user_content: object = [
            {"type": "text", "text": user_text},
            {"type": "image_url",
             "image_url": {"url": f"data:{mime_type};base64,{image_b64}", "detail": "high"}},
        ]
    else:
        user_content = user_text
    payload = {
        "model": used_model,
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": user_content},
        ],
        "temperature": 0,
    }
    async with httpx.AsyncClient(timeout=120) as client:
        resp = await client.post(f"{base}/chat/completions", headers=headers, json=payload)
        resp.raise_for_status()
        obj = resp.json()
        return obj["choices"][0]["message"].get("content") or ""
