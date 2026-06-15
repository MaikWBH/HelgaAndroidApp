import json
import os
from datetime import date, timedelta
from typing import AsyncIterator

import httpx
from dotenv import load_dotenv

from .models import (
    AiGenerateRequest, AiRemixRequest, AiClassifyRequest, AiUrlImportRequest,
    AiImportResponse, ImportedIngredient, ImportedInstruction,
    WeekplanGenerateRequest, WeekplanGenerateResponse, WeekplanAssignmentDto,
)

load_dotenv()

AI_PROVIDER = os.getenv("AI_PROVIDER", "openai")
AI_API_KEY = os.getenv("AI_API_KEY", "")
AI_MODEL = os.getenv("AI_MODEL", "")
AI_API_BASE = os.getenv("AI_API_BASE", "")

OPENAI_BASE = AI_API_BASE or "https://api.openai.com/v1"
ANTHROPIC_BASE = "https://api.anthropic.com/v1"

DEFAULT_MODEL = {
    "openai": "gpt-4o-mini",
    "anthropic": "claude-haiku-4-5-20251001",
}

CLASSIFY_VALUES = {
    "protein_type": ["fleisch", "fisch", "vegetarisch", "vegan"],
    "effort": ["schnell", "mittel", "aufwendig"],
    "cuisine": ["deutsch", "italienisch", "asiatisch", "mexikanisch", "französisch",
                "mediterran", "orientalisch", "amerikanisch", "indisch", "sonstige"],
    "meal_slot": ["breakfast", "lunch", "dinner", "snack"],
    "season_fit": ["winter", "sommer", "ganzjährig"],
}

RECIPE_HTML_SYSTEM = """Du bist ein professioneller Koch und Rezept-Autor.
Erstelle ein Rezept als vollständiges HTML-Dokument mit eingebettetem JSON-LD (Schema.org Recipe).

AUSGABEFORMAT — zwingend:
<!DOCTYPE html>
<html lang="de">
<head>
  <meta charset="UTF-8">
  <title>Rezeptname</title>
  <script type="application/ld+json">
  {
    "@context": "https://schema.org",
    "@type": "Recipe",
    "name": "...",
    "description": "...",
    "recipeYield": "4 Portionen",
    "prepTime": "PT15M",
    "cookTime": "PT30M",
    "totalTime": "PT45M",
    "recipeIngredient": ["200g Mehl", "2 Eier"],
    "recipeInstructions": [{"@type":"HowToStep","text":"..."}],
    "recipeCategory": ["Hauptgericht"],
    "keywords": "tag1, tag2",
    "rocks_protein_type": "fleisch|fisch|vegetarisch|vegan",
    "rocks_effort": "schnell|mittel|aufwendig",
    "rocks_cuisine": "deutsch|italienisch|...",
    "rocks_meal_slot": "breakfast|lunch|dinner|snack",
    "rocks_season_fit": "winter|sommer|ganzjährig"
  }
  </script>
</head>
<body><article><h1>...</h1></article></body>
</html>

Zeiten als ISO-8601-Dauer (PT15M, PT1H30M). Sprache: Deutsch.
KRITISCH: Nur rohes HTML ausgeben — kein Markdown, keine Erklärungen."""


def _model() -> str:
    return AI_MODEL or DEFAULT_MODEL.get(AI_PROVIDER, "gpt-4o-mini")


def _openai_headers() -> dict:
    base = AI_API_BASE or "https://api.openai.com/v1"
    return {"Authorization": f"Bearer {AI_API_KEY}", "Content-Type": "application/json"}, base


def _anthropic_headers() -> dict:
    return {
        "x-api-key": AI_API_KEY,
        "anthropic-version": "2023-06-01",
        "content-type": "application/json",
    }, ANTHROPIC_BASE


async def stream_generate(req: AiGenerateRequest) -> AsyncIterator[str]:
    tag_hint = (
        f"\n\nTAGS — PFLICHT: Verwende nur Tags aus dieser Liste: {', '.join(req.available_tags[:40])}"
        if req.available_tags else "\n\nVergib 2–4 deutsche Tags im 'keywords'-Feld."
    )
    custom = f"\n\nZUSÄTZLICHE ANWEISUNGEN:\n{req.custom_instructions}" if req.custom_instructions else ""
    system = RECIPE_HTML_SYSTEM + tag_hint + custom

    async for chunk in _stream(system, req.prompt):
        yield chunk


async def stream_remix(req: AiRemixRequest) -> AsyncIterator[str]:
    ingredients_block = "\n".join(f"- {i}" for i in req.recipe_ingredients)
    instructions_block = "\n".join(f"{i+1}. {s}" for i, s in enumerate(req.recipe_instructions))
    tag_hint = (
        f"\n\nTAGS — PFLICHT: Verwende nur Tags aus dieser Liste: {', '.join(req.available_tags[:40])}"
        if req.available_tags else "\n\nVergib 2–4 deutsche Tags im 'keywords'-Feld."
    )
    system = (
        f"Du bist ein kreativer Profi-Koch. Wandle das folgende Rezept gemäß dem Kundenwunsch ab "
        f"und gib das Ergebnis als vollständiges HTML mit JSON-LD aus (gleiches Format wie bei neuen Rezepten)."
        f"{tag_hint}\nKRITISCH: Nur rohes HTML ausgeben."
    )
    user = (
        f"ORIGINAL:\nName: {req.recipe_name}\nBeschreibung: {req.recipe_description}\n"
        f"Zutaten:\n{ingredients_block}\nAnweisungen:\n{instructions_block}\n\n"
        f"KUNDENWUNSCH: {req.remix_prompt}"
    )
    async for chunk in _stream(system, user):
        yield chunk


async def classify(req: AiClassifyRequest) -> dict:
    def _line(label, key):
        return f"- {label}: einer von [{', '.join(CLASSIFY_VALUES[key])}]"

    allowed = "\n".join([
        _line("protein_type", "protein_type"),
        _line("effort", "effort"),
        _line("cuisine", "cuisine"),
        _line("meal_slot", "meal_slot"),
        _line("season_fit", "season_fit"),
    ])
    ingredients_block = "\n".join(f"- {i}" for i in req.ingredients[:40]) or "(keine)"

    system = ("Du klassifizierst Rezepte. "
              "Antworte NUR mit einem validen JSON-Objekt — kein Markdown, keine Erklärungen.")
    user = (
        f"NAME: {req.name}\nBESCHREIBUNG: {req.description or ''}\n"
        f"TAGS: {', '.join(req.tags) or 'keine'}\nZUTATEN:\n{ingredients_block}\n\n"
        f"FELDER:\n{allowed}\n\n"
        "Antworte mit: {\"protein_type\":\"...\",\"effort\":\"...\","
        "\"cuisine\":\"...\",\"meal_slot\":\"...\",\"season_fit\":\"...\"}"
    )

    text = await _call_once(system, user)
    text = text.strip()
    if text.startswith("```"):
        text = text.split("```")[1].lstrip("json").strip()
    start, end = text.find("{"), text.rfind("}") + 1
    if start == -1 or end == 0:
        return {}
    data = json.loads(text[start:end])
    return {
        k: (v if v in CLASSIFY_VALUES.get(k, []) else "")
        for k, v in data.items()
        if k in CLASSIFY_VALUES
    }


def _mins_to_iso(mins) -> str:
    try:
        m = int(mins)
    except (TypeError, ValueError):
        return ""
    if m <= 0:
        return ""
    h, rem = divmod(m, 60)
    if h and rem:
        return f"PT{h}H{rem}M"
    elif h:
        return f"PT{h}H"
    return f"PT{rem}M"


def _safe(scraper, method: str, default=""):
    try:
        return getattr(scraper, method)() or default
    except Exception:
        return default


async def import_url(req: AiUrlImportRequest) -> AiImportResponse:
    from recipe_scrapers import scrape_html

    async with httpx.AsyncClient(timeout=20, follow_redirects=True) as client:
        resp = await client.get(req.url, headers={"User-Agent": "Mozilla/5.0"})
        resp.raise_for_status()

    scraper = scrape_html(resp.text, org_url=req.url)

    raw_ingredients = _safe(scraper, "ingredients", [])
    ingredients = [ImportedIngredient(food=s) for s in raw_ingredients if s.strip()]

    raw_instructions = _safe(scraper, "instructions_list", [])
    if not raw_instructions:
        full = _safe(scraper, "instructions", "")
        raw_instructions = [s.strip() for s in full.split("\n") if s.strip()] if full else []
    instructions = [ImportedInstruction(text=s) for s in raw_instructions if s.strip()]

    raw_tags = _safe(scraper, "keywords", [])
    if isinstance(raw_tags, str):
        raw_tags = [t.strip() for t in raw_tags.split(",") if t.strip()]

    return AiImportResponse(
        name=_safe(scraper, "title"),
        description=_safe(scraper, "description"),
        recipe_yield=_safe(scraper, "yields"),
        prep_time=_mins_to_iso(_safe(scraper, "prep_time", 0)),
        cook_time=_mins_to_iso(_safe(scraper, "cook_time", 0)),
        total_time=_mins_to_iso(_safe(scraper, "total_time", 0)),
        cuisine=_safe(scraper, "cuisine"),
        source_url=req.url,
        image_url=_safe(scraper, "image"),
        ingredients=ingredients,
        instructions=instructions,
        tags=raw_tags[:20],
    )


async def generate_weekplan(req: WeekplanGenerateRequest) -> WeekplanGenerateResponse:
    from .db import get_db

    async with get_db() as db:
        async with db.execute(
            "SELECT id, name, protein_type FROM recipes WHERE deleted = 0 AND name != ''",
        ) as cursor:
            all_recipes = [dict(r) for r in await cursor.fetchall()]

        cutoff = (date.fromisoformat(req.start_date) - timedelta(days=req.max_repeat_days)).isoformat()
        async with db.execute(
            """SELECT DISTINCT wr.recipe_id
               FROM weekplan_recipes wr
               JOIN weekplan_days wd ON wr.weekplan_day_id = wd.id
               WHERE wd.plan_date >= ? AND wr.deleted = 0""",
            (cutoff,),
        ) as cursor:
            recent_ids = {row[0] for row in await cursor.fetchall()}

    available = [r for r in all_recipes if r["id"] not in recent_ids]
    if not available:
        available = all_recipes
    if not available:
        return WeekplanGenerateResponse(assignments=[])

    start = date.fromisoformat(req.start_date)
    dates = [(start + timedelta(days=i)).isoformat() for i in range(req.plan_days)]
    recipe_list = "\n".join(
        f"- {r['id']}: {r['name']} ({r.get('protein_type') or 'unbekannt'})"
        for r in available[:80]
    )

    system = "Du bist ein Ernährungsplaner. Wähle Rezepte für einen Wochenplan. Antworte NUR mit validem JSON."
    user = (
        f"Erstelle einen Wochenplan für {req.plan_days} Tage ab {req.start_date}.\n"
        f"Constraints: max {req.max_meat_per_week} Fleisch-Tage, "
        f"min {req.min_vegetarian_per_week} vegetarische Tage.\n"
        f"Tage: {', '.join(dates)}\n\n"
        f"Verfügbare Rezepte:\n{recipe_list}\n\n"
        f'Antworte mit: {{"assignments": [{{"date": "2026-05-05", "recipe_id": "uuid"}}]}}\n'
        f"Wähle für jeden Tag genau ein Rezept. Variiere Protein-Typen. Nur exakte IDs verwenden."
    )

    try:
        raw = await _call_once(system, user)
        raw = raw.strip()
        start_idx, end_idx = raw.find("{"), raw.rfind("}") + 1
        if start_idx == -1:
            return WeekplanGenerateResponse(assignments=[])
        data = json.loads(raw[start_idx:end_idx])
    except Exception:
        return WeekplanGenerateResponse(assignments=[])

    recipe_map = {r["id"]: r["name"] for r in all_recipes}
    assignments = [
        WeekplanAssignmentDto(
            date=a.get("date", ""),
            recipe_id=a.get("recipe_id", ""),
            recipe_name=recipe_map.get(a.get("recipe_id", ""), ""),
        )
        for a in data.get("assignments", [])
        if a.get("recipe_id") in recipe_map and a.get("date")
    ]
    return WeekplanGenerateResponse(assignments=assignments)


# ── Low-level streaming ──────────────────────────────────────────────────────

async def _stream(system: str, user: str) -> AsyncIterator[str]:
    if AI_PROVIDER == "anthropic":
        async for chunk in _stream_anthropic(system, user):
            yield chunk
    else:
        async for chunk in _stream_openai(system, user):
            yield chunk


async def _stream_openai(system: str, user: str) -> AsyncIterator[str]:
    base = AI_API_BASE or "https://api.openai.com/v1"
    headers = {"Authorization": f"Bearer {AI_API_KEY}", "Content-Type": "application/json"}
    payload = {
        "model": _model(),
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
    headers = {
        "x-api-key": AI_API_KEY,
        "anthropic-version": "2023-06-01",
        "content-type": "application/json",
    }
    payload = {
        "model": _model(),
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


async def _call_once(system: str, user: str) -> str:
    """Blocking-ähnlicher Aufruf (sammelt Stream komplett)."""
    chunks = []
    async for chunk in _stream(system, user):
        chunks.append(chunk)
    return "".join(chunks)


# ── Receipt Analysis (Vision) ───────────────────────────────────────────────

async def _stream_openai_vision(system: str, user: str, image_b64: str, media_type: str) -> AsyncIterator[str]:
    base = AI_API_BASE or "https://api.openai.com/v1"
    headers = {"Authorization": f"Bearer {AI_API_KEY}", "Content-Type": "application/json"}
    payload = {
        "model": _model(),
        "messages": [
            {"role": "system", "content": system},
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": user},
                    {
                        "type": "image_url",
                        "image_url": {"url": f"data:{media_type};base64,{image_b64}"}
                    }
                ]
            }
        ],
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


async def _stream_anthropic_vision(system: str, user: str, image_b64: str, media_type: str) -> AsyncIterator[str]:
    headers = {
        "x-api-key": AI_API_KEY,
        "anthropic-version": "2023-06-01",
        "content-type": "application/json",
    }
    payload = {
        "model": _model(),
        "system": system,
        "messages": [
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": user},
                    {
                        "type": "image",
                        "source": {
                            "type": "base64",
                            "media_type": media_type,
                            "data": image_b64
                        }
                    }
                ]
            }
        ],
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


async def analyze_receipt(image_b64: str, media_type: str = "image/jpeg") -> str:
    """Analyzes a receipt image and returns JSON with extracted data.

    Returns JSON: {store_name, purchase_date, currency, total_amount, items:[...]}
    """
    system = """Du bist ein OCR-Spezialist für Kassenbons. Lies das Bild aus und extrahiere strukturierte Daten.
Antworte NUR mit validem JSON — kein Markdown, keine Erklärungen.

JSON-Format (zwingend):
{
  "store_name": "Name des Marktes",
  "purchase_date": "YYYY-MM-DD",
  "currency": "EUR",
  "total_amount": 0.0,
  "items": [
    {"raw_text": "Originalzeile", "name": "Produktname", "quantity": 1.0, "unit_price": 0.0, "total_price": 0.0}
  ]
}"""

    user = "Lese diesen Kassenbon aus und gib die Daten als JSON zurück."

    chunks = []
    if AI_PROVIDER == "anthropic":
        async for chunk in _stream_anthropic_vision(system, user, image_b64, media_type):
            chunks.append(chunk)
    else:
        async for chunk in _stream_openai_vision(system, user, image_b64, media_type):
            chunks.append(chunk)

    return "".join(chunks)
