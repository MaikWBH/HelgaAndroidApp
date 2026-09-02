import json
import os
from datetime import date
from typing import AsyncIterator

import httpx
from dotenv import load_dotenv
from fastapi import HTTPException

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
# Eigenes (starkes) Vision-Modell für die Bon-Erkennung. Siehe _vision_model().
AI_VISION_MODEL = os.getenv("AI_VISION_MODEL", "")

OPENAI_BASE = AI_API_BASE or "https://api.openai.com/v1"
ANTHROPIC_BASE = "https://api.anthropic.com/v1"

DEFAULT_MODEL = {
    "openai": "gpt-4o-mini",
    "anthropic": "claude-haiku-4-5-20251001",
}

# Vision-Tasks (Kassenbon-Erkennung) brauchen ein deutlich stärkeres, bild-fähiges
# Modell als die günstigen Text-Defaults: dichte deutsche Bons mit kleinem Druck
# überfordern Mini-/Haiku-Modelle (Belege: Sonnet ~97 % vs. Mini deutlich darunter).
DEFAULT_VISION_MODEL = {
    "openai": "gpt-4o",
    "anthropic": "claude-sonnet-4-6",
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


def _vision_model() -> str:
    """Modell für Bild-Eingaben (Kassenbon-Erkennung).

    Reihenfolge:
    1. AI_VISION_MODEL – explizite Wahl (empfohlen, um z. B. Sonnet zu erzwingen).
    2. AI_MODEL – falls bewusst gesetzt, respektieren wir die Nutzerwahl.
    3. Starker Provider-Default (NICHT der günstige Text-Default), weil dichte
       deutsche Bons sonst unzuverlässig erkannt werden.
    """
    return AI_VISION_MODEL or AI_MODEL or DEFAULT_VISION_MODEL.get(AI_PROVIDER, "gpt-4o")


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


# ── Receipt Parsing (KI-Vision) ──────────────────────────────────────────────
# Liest einen fotografierten Kassenbon direkt aus dem Bild. Deutlich robuster als
# die On-Device-OCR, weil das Vision-Modell Spalten-Layout, Mengen und Preise
# im Zusammenhang versteht (z. B. Gewichtsabrechnung "0,512 kg x 2,99").

RECEIPT_PARSE_SYSTEM = (
    "Du bist ein Experte für das Auslesen deutscher Kassenbons (Supermarkt-Quittungen). "
    "Du erhältst ein Foto eines Kassenbons und extrahierst die Daten strukturiert. "
    "Antworte AUSSCHLIESSLICH mit einem validen JSON-Objekt – kein Markdown, keine Erklärungen."
)

RECEIPT_PARSE_USER = (
    "Lies diesen Kassenbon vollständig aus und gib die Daten als JSON zurück.\n\n"
    "VORGEHEN (wichtig für Vollständigkeit):\n"
    "- Gehe den Bon Zeile für Zeile von OBEN nach UNTEN durch.\n"
    "- Erfasse JEDE Artikelzeile zwischen Kopf (Markt/Adresse) und Summenblock. "
    "Überspringe oder kürze nichts, auch wenn es viele Positionen sind.\n"
    "- Ein Artikelname kann über ZWEI Zeilen gehen (z. B. Marke in Zeile 1, "
    "Produkt in Zeile 2) – fasse ihn zu EINEM Eintrag zusammen.\n"
    "- Wenn ein Name oder Preis teils unleserlich ist: trage trotzdem deine BESTE "
    "Schätzung ein und setze eine niedrige confidence. Lasse die Position NICHT weg.\n\n"
    "FELDER:\n"
    "- store_name: Name des Markts/Geschäfts (z. B. 'REWE', 'ALDI', 'EDEKA', 'Lidl'). "
    "Leer lassen, wenn nicht erkennbar.\n"
    "- purchase_date: Kaufdatum im Format 'YYYY-MM-DD'. Leer lassen, wenn nicht erkennbar.\n"
    "- total_amount: Gesamtbetrag (Summe/zu zahlen) als Zahl mit Punkt als Dezimaltrennzeichen.\n"
    "- items: Liste ALLER gekauften Artikel. Pro Artikel:\n"
    "    - name: Produktname wie auf dem Bon, ohne nachgestelltes Steuerkennzeichen "
    "(z. B. einzelnes 'A'/'B' am Zeilenende) und ohne den Preis.\n"
    "    - quantity: Menge/Anzahl (Standard 1; bei Gewicht das Gewicht, z. B. 0.512).\n"
    "    - unit_price: Einzel-/Grundpreis pro Stück bzw. pro Einheit. "
    "Falls nur ein Preis erkennbar ist, setze unit_price = total_price.\n"
    "    - total_price: Gesamtpreis dieser Position.\n"
    "    - confidence: Wie sicher du dir bei dieser Position bist (0.0–1.0). "
    "Niedrig wählen, wenn Name oder Preis verschwommen/unleserlich sind.\n"
    "- confidence: Gesamt-Sicherheit über den ganzen Bon (0.0–1.0).\n\n"
    "WICHTIG:\n"
    "- Deutsche Preise nutzen Komma (1,99) – wandle sie in Punkt-Notation um (1.99).\n"
    "- Pfand ('PFAND', 'LEERGUT') als eigene Position aufnehmen (Leergut-Rückgabe negativ).\n"
    "- Mengenzeilen wie '2 x 1,99' oder '3 Stk x 0,89': quantity=Anzahl, "
    "unit_price=Stückpreis, total_price=Summe.\n"
    "- Bei Gewichtsabrechnung ('0,512 kg x 2,99 €/kg'): quantity=Gewicht, "
    "unit_price=Preis pro Einheit, total_price=berechneter Betrag.\n"
    "- Ignoriere Zwischensummen, MwSt-/Steuer-Zeilen, Zahlart (EC/BAR/Kartenzahlung), "
    "Rückgeld, Kundenkarten-/Payback-Punkte, Bon-Nummern, Uhrzeit und Adresse.\n"
    "- Plausibilitätscheck: Die Summe der total_price aller items sollte ungefähr "
    "total_amount ergeben (abzüglich evtl. Rabatte). Weicht sie stark ab, hast du "
    "vermutlich Positionen übersehen – lies noch einmal genau.\n\n"
    "BEISPIEL (nur Format, nicht den Inhalt übernehmen):\n"
    'Bon-Zeilen "G&G H-MILCH 3,5% 0,95 A" und "2 x BANANE 1,98 B" werden zu '
    '[{"name":"G&G H-Milch 3,5%","quantity":1,"unit_price":0.95,"total_price":0.95,'
    '"confidence":0.95},{"name":"Banane","quantity":2,"unit_price":0.99,'
    '"total_price":1.98,"confidence":0.9}]\n\n'
    'Antworte mit EXAKT diesem Format (nur JSON, kein Markdown): '
    '{"store_name":"...","purchase_date":"YYYY-MM-DD",'
    '"total_amount":0.00,"confidence":0.0,"items":[{"name":"...","quantity":1,'
    '"unit_price":0.00,"total_price":0.00,"confidence":0.0}]}'
)


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


def _iso_to_ms(s) -> int:
    if not s:
        return 0
    try:
        from datetime import datetime
        d = date.fromisoformat(str(s)[:10])
        return int(datetime(d.year, d.month, d.day).timestamp() * 1000)
    except (ValueError, TypeError):
        return 0


async def parse_receipt(image_b64: str, mime_type: str = "image/jpeg") -> dict:
    """Extrahiert Markt, Datum, Gesamtbetrag und Positionen aus einem Bon-Foto.

    Gibt einen Dict im Format der ReceiptParseResponse zurück.
    """
    empty = {"store_name": "", "purchase_date": 0, "total_amount": 0.0,
             "confidence": 0.0, "items": []}

    raw = (await _vision_once(RECEIPT_PARSE_SYSTEM, RECEIPT_PARSE_USER, image_b64, mime_type)).strip()
    if raw.startswith("```"):
        raw = raw.split("```")[1].lstrip("json").strip()
    start, end = raw.find("{"), raw.rfind("}") + 1
    if start == -1 or end == 0:
        return empty
    try:
        data = json.loads(raw[start:end])
    except json.JSONDecodeError:
        return empty

    items = []
    for it in data.get("items", []):
        if not isinstance(it, dict):
            continue
        name = str(it.get("name", "")).strip()
        if not name:
            continue
        qty = _to_float(it.get("quantity"), 1.0) or 1.0
        unit = _to_float(it.get("unit_price"), 0.0)
        total = _to_float(it.get("total_price"), 0.0)
        if total == 0.0 and unit != 0.0:
            total = round(unit * qty, 2)
        if unit == 0.0 and total != 0.0:
            unit = round(total / qty, 2) if qty else total
        conf = _to_float(it.get("confidence"), 1.0)
        conf = min(max(conf, 0.0), 1.0)
        items.append({
            "name": name,
            "quantity": qty,
            "unit_price": unit,
            "total_price": total,
            "confidence": conf,
        })

    overall = min(max(_to_float(data.get("confidence"), 1.0), 0.0), 1.0)

    return {
        "store_name": str(data.get("store_name", "")).strip(),
        "purchase_date": _iso_to_ms(data.get("purchase_date", "")),
        "total_amount": _to_float(data.get("total_amount"), 0.0),
        "confidence": overall,
        "items": items,
    }


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

    raw = (await _call_once(system, user)).strip()
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
