import json
import os
import re
from datetime import date
from typing import AsyncIterator

import httpx
from dotenv import load_dotenv
from fastapi import HTTPException

from . import categories, receipt_text
from .ingredient_parser import is_header_line, parse_ingredient_line
from .models import (
    AiGenerateRequest, AiRemixRequest, AiClassifyRequest, AiNutritionRequest,
    AiUrlImportRequest,
    AiImportResponse, ImportedIngredient, ImportedInstruction,
)

load_dotenv()

AI_PROVIDER = os.getenv("AI_PROVIDER", "openai")
AI_API_KEY = os.getenv("AI_API_KEY", "")
AI_MODEL = os.getenv("AI_MODEL", "")
AI_API_BASE = os.getenv("AI_API_BASE", "")
# Referenzmodell (stärker, bild-fähig) – seit Phase 3a nur noch vom Testset-Skript
# scripts/receipt_testset.py genutzt. Siehe _vision_model().
AI_VISION_MODEL = os.getenv("AI_VISION_MODEL", "")
# Modell für ALLE Bon-Aufrufe (Auslesen, Abgleich, Kategorisieren). Leer = DEFAULT_RECEIPT_MODEL.
AI_RECEIPT_MODEL = os.getenv("AI_RECEIPT_MODEL", "")
# output_config.effort für Modelle, die es kennen: low | medium | high
AI_RECEIPT_EFFORT = os.getenv("AI_RECEIPT_EFFORT", "medium")

OPENAI_BASE = AI_API_BASE or "https://api.openai.com/v1"
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


def _vision_model() -> str:
    """Modell für Bild-Eingaben (Kassenbon-Erkennung).

    Reihenfolge:
    1. AI_VISION_MODEL – explizite Wahl (empfohlen, um z. B. Sonnet zu erzwingen).
    2. AI_MODEL – falls bewusst gesetzt, respektieren wir die Nutzerwahl.
    3. Starker Provider-Default (NICHT der günstige Text-Default), weil dichte
       deutsche Bons sonst unzuverlässig erkannt werden.
    """
    return AI_VISION_MODEL or AI_MODEL or DEFAULT_VISION_MODEL.get(AI_PROVIDER, "gpt-4o")


def _receipt_model() -> str:
    """Modell für alle Bon-Aufrufe. AI_VISION_MODEL wirkt hier bewusst nicht mehr."""
    return AI_RECEIPT_MODEL or DEFAULT_RECEIPT_MODEL.get(AI_PROVIDER, "gpt-4o-mini")


def _uses_effort(model: str) -> bool:
    return model.startswith(_EFFORT_MODEL_PREFIXES)


def _receipt_payload(model: str, system: str, content: list) -> dict:
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
    if _uses_effort(model):
        payload["output_config"] = {"effort": AI_RECEIPT_EFFORT}
    else:
        payload["temperature"] = 0
    return payload


def _openai_headers() -> dict:
    base = AI_API_BASE or "https://api.openai.com/v1"
    return {"Authorization": f"Bearer {AI_API_KEY}", "Content-Type": "application/json"}, base


def _anthropic_headers() -> dict:
    return {
        "x-api-key": AI_API_KEY,
        "anthropic-version": "2023-06-01",
        "content-type": "application/json",
    }, ANTHROPIC_BASE


def _allergen_hint(exclude_allergens: list[str]) -> str:
    if not exclude_allergens:
        return ""
    return (
        f"\n\nALLERGENE — PFLICHT: Das Rezept darf KEINE der folgenden Allergene/Zutaten "
        f"enthalten, auch nicht in Spurenform: {', '.join(exclude_allergens)}."
    )


async def stream_generate(req: AiGenerateRequest) -> AsyncIterator[str]:
    tag_hint = (
        f"\n\nTAGS — PFLICHT: Verwende nur Tags aus dieser Liste: {', '.join(req.available_tags[:40])}"
        if req.available_tags else "\n\nVergib 2–4 deutsche Tags im 'keywords'-Feld."
    )
    custom = f"\n\nZUSÄTZLICHE ANWEISUNGEN:\n{req.custom_instructions}" if req.custom_instructions else ""
    system = RECIPE_HTML_SYSTEM + tag_hint + custom + _allergen_hint(req.exclude_allergens)

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
        f"{_allergen_hint(req.exclude_allergens)}"
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


async def estimate_nutrition(req: AiNutritionRequest) -> dict:
    """Schätzt Nährwerte für ein ganzes Rezept (alle Zutaten zusammen, skaliert
    auf `req.portions` Portionen). Antwortformat analog zu [classify]."""
    ingredients_block = "\n".join(f"- {i}" for i in req.ingredients[:60]) or "(keine)"

    system = ("Du bist Ernährungsexperte. Schätze die Nährwerte für ein GANZES Rezept "
               "(Summe über alle Zutaten, nicht pro 100g). "
               "Antworte NUR mit einem validen JSON-Objekt — kein Markdown, keine Erklärungen.")
    user = (
        f"REZEPT: {req.name}\nBESCHREIBUNG: {req.description or ''}\n"
        f"PORTIONEN: {req.portions}\n"
        f"ZUTATEN (bereits für {req.portions} Portionen bemessen):\n{ingredients_block}\n\n"
        "Schätze die GESAMT-Nährwerte für das komplette Rezept (alle Zutaten zusammen):\n"
        "- kcal: Gesamt-Kalorien\n- protein: Gesamt-Eiweiß in Gramm\n"
        "- fat: Gesamt-Fett in Gramm\n- carbs: Gesamt-Kohlenhydrate in Gramm\n\n"
        'Antworte mit: {"kcal":0,"protein":0,"fat":0,"carbs":0}'
    )

    text = (await _call_once(system, user)).strip()
    if text.startswith("```"):
        text = text.split("```")[1].lstrip("json").strip()
    start, end = text.find("{"), text.rfind("}") + 1
    if start == -1 or end == 0:
        return {}
    try:
        data = json.loads(text[start:end])
    except json.JSONDecodeError:
        return {}

    return {
        "kcal": _to_float(data.get("kcal"), 0.0),
        "protein": _to_float(data.get("protein"), 0.0),
        "fat": _to_float(data.get("fat"), 0.0),
        "carbs": _to_float(data.get("carbs"), 0.0),
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
    from recipe_scrapers._exceptions import NoSchemaFoundInWildMode, WebsiteNotImplementedError

    try:
        async with httpx.AsyncClient(timeout=20, follow_redirects=True) as client:
            resp = await client.get(req.url, headers={"User-Agent": "Mozilla/5.0"})
            resp.raise_for_status()
    except httpx.HTTPStatusError as e:
        raise HTTPException(
            status_code=422, detail=f"Seite antwortet mit Fehler {e.response.status_code}"
        ) from e
    except httpx.RequestError as e:
        raise HTTPException(status_code=422, detail="Seite nicht erreichbar") from e

    try:
        # supported_only=False: auch bei Seiten ohne dediziertem Scraper per generischem
        # schema.org-Parser versuchen, statt nur die feste Domain-Liste zu erlauben.
        scraper = scrape_html(resp.text, org_url=req.url, supported_only=False)
    except WebsiteNotImplementedError as e:
        raise HTTPException(status_code=422, detail="Diese Seite wird nicht unterstützt") from e
    except NoSchemaFoundInWildMode as e:
        raise HTTPException(status_code=422, detail="Kein Rezept auf dieser Seite gefunden") from e
    except Exception as e:
        raise HTTPException(status_code=422, detail="Rezept konnte nicht gelesen werden") from e

    raw_ingredients = _safe(scraper, "ingredients", [])
    ingredients = [
        ImportedIngredient(**parse_ingredient_line(s))
        for s in raw_ingredients
        if s.strip() and not is_header_line(s)
    ]

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


# ── Vision (Bild-Eingabe, einmaliger Aufruf) ─────────────────────────────────

async def _vision_once(system: str, user_text: str, image_b64: str, mime_type: str) -> str:
    if AI_PROVIDER == "anthropic":
        return await _vision_anthropic(system, user_text, image_b64, mime_type)
    return await _vision_openai(system, user_text, image_b64, mime_type)


async def _vision_openai(system: str, user_text: str, image_b64: str, mime_type: str) -> str:
    base = AI_API_BASE or "https://api.openai.com/v1"
    headers = {"Authorization": f"Bearer {AI_API_KEY}", "Content-Type": "application/json"}
    payload = {
        "model": _vision_model(),
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
    headers = {
        "x-api-key": AI_API_KEY,
        "anthropic-version": "2023-06-01",
        "content-type": "application/json",
    }
    payload = {
        "model": _vision_model(),
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


async def _receipt_once(system: str, user_text: str, image_b64: str | None = None,
                        mime_type: str = "image/jpeg", model: str | None = None) -> str:
    """Einmaliger Bon-Aufruf mit dem Bon-Modell, mit oder ohne Bild."""
    used_model = model or _receipt_model()
    if AI_PROVIDER == "anthropic":
        content: list = []
        if image_b64:
            content.append({"type": "image", "source": {
                "type": "base64", "media_type": mime_type, "data": image_b64}})
        content.append({"type": "text", "text": user_text})
        headers = {
            "x-api-key": AI_API_KEY,
            "anthropic-version": "2023-06-01",
            "content-type": "application/json",
        }
        payload = _receipt_payload(used_model, system, content)
        async with httpx.AsyncClient(timeout=120) as client:
            resp = await client.post(f"{ANTHROPIC_BASE}/messages", headers=headers, json=payload)
            resp.raise_for_status()
            obj = resp.json()
            return "".join(
                p.get("text", "") for p in obj.get("content", []) if p.get("type") == "text"
            )

    base = AI_API_BASE or "https://api.openai.com/v1"
    headers = {"Authorization": f"Bearer {AI_API_KEY}", "Content-Type": "application/json"}
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


# ── Receipt Parsing (KI-Vision) ──────────────────────────────────────────────
# Liest einen fotografierten Kassenbon direkt aus dem Bild. Deutlich robuster als
# die On-Device-OCR, weil das Vision-Modell Spalten-Layout, Mengen und Preise
# im Zusammenhang versteht (z. B. Gewichtsabrechnung "0,512 kg x 2,99").

RECEIPT_PARSE_SYSTEM = (
    "Du bist ein Experte für das Auslesen deutscher Kassenbons (Supermarkt-Quittungen). "
    "Du erhältst ein Foto eines Kassenbons und extrahierst die Daten strukturiert. "
    "Antworte AUSSCHLIESSLICH mit einem validen JSON-Objekt – kein Markdown, keine Erklärungen."
)

RECEIPT_PARSE_TEMPLATE = r'''Lies diesen Kassenbon vollständig aus und gib die Daten als JSON zurück.

VORGEHEN:
- Gehe den Bon Zeile für Zeile von OBEN nach UNTEN durch.
- Erfasse JEDE Artikelzeile zwischen Kopf und Summenblock. Überspringe oder kürze nichts.
- Ein Artikelname kann über ZWEI Zeilen gehen (Marke in Zeile 1, Produkt in Zeile 2): fasse ihn zu EINEM Eintrag zusammen.
- Ist ein Name oder Preis teils unleserlich: trage deine beste Schätzung ein und setze eine niedrige confidence. Lasse die Position nicht weg.

KOPF:
- store_name: Name der Kette oder des Geschäfts (z. B. "REWE", "ALDI SÜD", "EDEKA", "Lidl", "dm"). Leer, wenn nicht erkennbar.
- store_address: Filiale und Adresse in einer Zeile, so weit gedruckt (z. B. "REWE Markt Müller oHG, Hauptstr. 12, 12345 Musterstadt"). Leer, wenn nicht gedruckt. Keine Telefonnummer, keine Steuernummer.
- purchase_date: Kaufdatum als "YYYY-MM-DD". Leer, wenn nicht erkennbar.
- purchase_time: Uhrzeit des Kaufs als "HH:MM" (24 Stunden). Leer, wenn nicht gedruckt.
- total_amount: zu zahlender Gesamtbetrag (Summe / zu zahlen) als Zahl.
- discount_total: Summe aller Rabatte, die für den GANZEN Bon gelten (Coupon, "5% auf alles", Mitarbeiterrabatt, Treuerabatt), als positive Zahl. 0, wenn keine.
- confidence: deine Gesamt-Sicherheit für den ganzen Bon (0.0 bis 1.0).

POSITIONEN (items), je Artikel:
- name: Text der Artikelzeile GENAU wie auf dem Bon, ohne Preis und ohne nachgestelltes Steuerkennzeichen (einzelnes A/B am Zeilenende).
- product_name: gut lesbarer deutscher Artikelname ohne Kürzel (aus "G&G H-MILCH 3,5%" wird "H-Milch 3,5 %", aus "BIO BAN." wird "Bio-Bananen"). Marke nur, wenn sie zum Erkennen nötig ist.
- category: genau EIN Schlüssel aus der Liste KATEGORIEN. Passt nichts: "sonstiges".
- quantity: Anzahl oder Gewicht/Volumen als Zahl (Standard 1; bei "0,512 kg" also 0.512).
- unit: "stk" für Stück, "kg", "g", "l" oder "ml" bei Abrechnung nach Gewicht oder Volumen. Unklar: "stk".
- unit_price: Preis je Einheit. Ist nur ein Preis erkennbar: gleich total_price.
- total_price: Betrag der Zeile, wie er rechts steht, VOR einem Rabatt dieser Position.
- discount: Rabatt, der nur für DIESE Position gilt (Zeile direkt darunter wie "Rabatt -0,50", "Aktionsnachlass", "Preisvorteil", "-30%"), als positive Zahl. 0, wenn keiner. Eine solche Rabattzeile ist KEINE eigene Position.
- is_deposit: true bei Pfand und Leergut ("PFAND", "EINWEG", "MEHRWEG", "LEERGUT"), sonst false. Pfand immer als eigene Position mit category "pfand"; Leergut-Rückgabe mit negativem total_price.
- confidence: deine Sicherheit für diese Position (0.0 bis 1.0).

KATEGORIEN:
{categories}

REGELN:
- Deutsche Preise nutzen Komma (1,99): gib Zahlen mit Punkt aus (1.99).
- "2 x 1,99" oder "3 Stk x 0,89": quantity = Anzahl, unit = "stk", unit_price = Stückpreis, total_price = Summe.
- "0,512 kg x 2,99 EUR/kg": quantity = 0.512, unit = "kg", unit_price = 2.99, total_price = gedruckter Betrag.
- Keine Positionen sind: Zwischensummen, MwSt-Zeilen, Zahlart, Rückgeld, Kundenkarten- und Punktezeilen, Bon-Nummern.
- Prüfe am Ende: Summe aus (total_price minus discount) aller Positionen, minus discount_total, ergibt ungefähr total_amount. Weicht es stark ab, hast du Positionen oder Rabatte übersehen: lies noch einmal.

BEISPIEL (nur Format):
Die Zeilen "G&G H-MILCH 3,5% 0,95 A", "2 x BANANE 1,98 B", "Rabatt -0,30" und "PFAND 0,25 A" werden zu
[{"name":"G&G H-MILCH 3,5%","product_name":"H-Milch 3,5 %","category":"milch_eier","quantity":1,"unit":"stk","unit_price":0.95,"total_price":0.95,"discount":0,"is_deposit":false,"confidence":0.95},
 {"name":"BANANE","product_name":"Bananen","category":"obst_gemuese","quantity":2,"unit":"stk","unit_price":0.99,"total_price":1.98,"discount":0.30,"is_deposit":false,"confidence":0.9},
 {"name":"PFAND","product_name":"Pfand","category":"pfand","quantity":1,"unit":"stk","unit_price":0.25,"total_price":0.25,"discount":0,"is_deposit":true,"confidence":0.95}]

Antworte mit genau diesem Format, nur JSON, kein Markdown:
{"store_name":"","store_address":"","purchase_date":"YYYY-MM-DD","purchase_time":"HH:MM","total_amount":0.00,"discount_total":0.00,"confidence":0.0,"items":[{"name":"","product_name":"","category":"","quantity":1,"unit":"stk","unit_price":0.00,"total_price":0.00,"discount":0.00,"is_deposit":false,"confidence":0.0}]}'''

RECEIPT_PARSE_USER = RECEIPT_PARSE_TEMPLATE.replace("{categories}", categories.prompt_lines())


def _to_float(v, default: float = 0.0) -> float:
    if v is None:
        return default
    if isinstance(v, bool):
        return default
    if isinstance(v, (int, float)):
        return float(v)
    s = str(v).strip().replace("€", "").replace(" ", "")
    # Deutsches Komma → Punkt (nur wenn kein Punkt als Dezimaltrenner vorhanden)
    if "," in s and "." not in s:
        s = s.replace(",", ".")
    else:
        s = s.replace(",", "")
    try:
        return float(s)
    except ValueError:
        return default


_ALLOWED_UNITS = {"stk", "kg", "g", "l", "ml"}
_TIME_RE = re.compile(r"^(\d{1,2}):(\d{2})")


def _clamp01(value) -> float:
    return min(max(_to_float(value, 1.0), 0.0), 1.0)


def _truthy(value) -> int:
    if isinstance(value, bool):
        return 1 if value else 0
    if isinstance(value, (int, float)):
        return 1 if value else 0
    return 1 if str(value).strip().lower() in ("true", "1", "ja") else 0


def _normalize_day(value) -> str:
    try:
        return date.fromisoformat(str(value or "").strip()[:10]).isoformat()
    except ValueError:
        return ""


def _normalize_time(value) -> str:
    match = _TIME_RE.match(str(value or "").strip())
    if not match:
        return ""
    hours, minutes = int(match.group(1)), int(match.group(2))
    if hours > 23 or minutes > 59:
        return ""
    return f"{hours:02d}:{minutes:02d}"


def normalize_parsed_receipt(data: dict) -> dict:
    """Bringt die KI-Antwort in die feste Form (rein, ohne Netz). Siehe server-vertrag.md 5.3."""
    day = _normalize_day(data.get("purchase_date"))
    address = ", ".join(
        part.strip() for part in str(data.get("store_address") or "").splitlines() if part.strip()
    )[:200].strip()

    items = []
    for it in data.get("items") or []:
        if not isinstance(it, dict):
            continue
        raw_text = str(it.get("name") or "").strip()
        product_name = str(it.get("product_name") or "").strip()
        name = product_name or raw_text
        if not raw_text and not name:
            continue
        qty = _to_float(it.get("quantity"), 1.0) or 1.0
        unit_price = _to_float(it.get("unit_price"), 0.0)
        total = _to_float(it.get("total_price"), 0.0)
        if total == 0.0 and unit_price != 0.0:
            total = round(unit_price * qty, 2)
        if unit_price == 0.0 and total != 0.0:
            unit_price = round(total / qty, 2) if qty else total
        unit = str(it.get("unit") or "").strip().lower()
        if unit not in _ALLOWED_UNITS:
            unit = "stk"
        is_deposit = _truthy(it.get("is_deposit"))
        items.append({
            "raw_text": raw_text,
            "name": name,
            "quantity": qty,
            "unit": unit,
            "unit_price": unit_price,
            "total_price": total,
            "discount": abs(_to_float(it.get("discount"), 0.0)),
            "is_deposit": is_deposit,
            "category": "pfand" if is_deposit else categories.coerce(it.get("category")),
            "confidence": _clamp01(it.get("confidence")),
        })

    return {
        "store_name": str(data.get("store_name") or "").strip(),
        "store_address": address,
        "purchase_day": day,
        "purchase_time": _normalize_time(data.get("purchase_time")),
        "purchase_date": receipt_text.day_to_ms(day),
        "total_amount": _to_float(data.get("total_amount"), 0.0),
        "discount_total": abs(_to_float(data.get("discount_total"), 0.0)),
        "confidence": _clamp01(data.get("confidence")),
        "items": items,
    }


async def parse_receipt(image_b64: str, mime_type: str = "image/jpeg", model: str | None = None) -> dict:
    """Extrahiert Kopfdaten und Positionen aus einem Bon-Foto (Bon-Modell, siehe _receipt_model)."""
    used_model = model or _receipt_model()
    empty = normalize_parsed_receipt({})
    empty["confidence"] = 0.0
    empty["model"] = used_model

    raw = (await _receipt_once(
        RECEIPT_PARSE_SYSTEM, RECEIPT_PARSE_USER, image_b64, mime_type, used_model
    )).strip()
    if raw.startswith("```"):
        raw = raw.split("```")[1].lstrip("json").strip()
    start, end = raw.find("{"), raw.rfind("}") + 1
    if start == -1 or end == 0:
        return empty
    try:
        data = json.loads(raw[start:end])
    except json.JSONDecodeError:
        return empty
    if not isinstance(data, dict):
        return empty

    result = normalize_parsed_receipt(data)
    result["model"] = used_model
    return result


# ── Receipt Reconciliation (Phase 4) ─────────────────────────────────────────
# OCR läuft on-device (Google ML Kit). Der KI-Abgleich vergleicht die abgehakte
# Einkaufsliste mit den erkannten Bon-Positionen – robust gegen kryptische
# Marken-Abkürzungen (z. B. "G&G H-MILCH 3,5%" = "Milch").

async def reconcile_receipt(checked_items: list, receipt_items: list) -> dict:
    """Ordnet Bon-Positionen den abgehakten Listen-Items zu.

    Gibt JSON zurück: {matches:[{shopping_item_id, receipt_item_id}],
    unexpected:[receipt_item_id...], missing:[shopping_item_id...]}
    """
    checked_block = "\n".join(
        f"- id={i.id}: {i.name} ({i.quantity} {i.unit})".strip()
        for i in checked_items
    ) or "(keine)"
    receipt_block = "\n".join(
        f"- id={i.id}: {i.name or i.raw_text} ({i.total_price:.2f})"
        for i in receipt_items
    ) or "(keine)"

    system = (
        "Du gleichst eine abgehakte Einkaufsliste mit den Positionen eines "
        "Kassenbons ab. Bon-Namen sind oft kryptische Marken-Abkürzungen "
        "(z. B. 'G&G H-MILCH 3,5%' = 'Milch'). Antworte NUR mit validem JSON — "
        "kein Markdown, keine Erklärungen."
    )
    user = (
        f"ABGEHAKTE EINKAUFSLISTE:\n{checked_block}\n\n"
        f"KASSENBON-POSITIONEN:\n{receipt_block}\n\n"
        "Ordne jede Bon-Position höchstens einem Listen-Item zu. Gib zurück:\n"
        '{"matches":[{"shopping_item_id":"<id>","receipt_item_id":"<id>"}],'
        '"unexpected":["<receipt_item_id ohne Listen-Entsprechung>"],'
        '"missing":["<shopping_item_id, das nicht auf dem Bon ist>"]}'
    )

    raw = (await _receipt_once(system, user)).strip()
    if raw.startswith("```"):
        raw = raw.split("```")[1].lstrip("json").strip()
    start, end = raw.find("{"), raw.rfind("}") + 1
    if start == -1 or end == 0:
        return {"matches": [], "unexpected": [], "missing": []}
    try:
        data = json.loads(raw[start:end])
    except json.JSONDecodeError:
        return {"matches": [], "unexpected": [], "missing": []}

    # Auf gültige IDs filtern, damit der Client keine Geister-IDs zurückbekommt
    checked_ids = {i.id for i in checked_items}
    receipt_ids = {i.id for i in receipt_items}
    matches = [
        {"shopping_item_id": m.get("shopping_item_id", ""),
         "receipt_item_id": m.get("receipt_item_id", "")}
        for m in data.get("matches", [])
        if m.get("shopping_item_id") in checked_ids and m.get("receipt_item_id") in receipt_ids
    ]
    unexpected = [rid for rid in data.get("unexpected", []) if rid in receipt_ids]
    missing = [sid for sid in data.get("missing", []) if sid in checked_ids]
    return {"matches": matches, "unexpected": unexpected, "missing": missing}
