# CLAUDE.md — HelgaSyncServer

## Zweck

Schlankes FastAPI-Backend das ausschließlich als Datenhub für die Helga Android App dient. Kein HTML, keine Templates – nur JSON-API, KI-Proxy und Bild-Storage. Langfristig ersetzt dieses Backend die Flask-Helga-App vollständig.

---

## Stack

| Bereich | Entscheidung |
|---------|--------------|
| Framework | FastAPI + uvicorn |
| Datenbank | SQLite via aiosqlite (async) |
| KI | httpx-Streaming zu OpenAI / Anthropic |
| Auth | `X-Api-Key`-Header (Shared Secret) |
| Deployment | Docker + Makefile |

---

## Struktur

```
app/
├── main.py       # Alle Routen, Auth-Middleware
├── db.py         # Schema, init_db(), get_db(), SYNC_TABLES, now_ms()
├── models.py     # Pydantic-Modelle (SyncPayload, DTOs für KI)
├── sync.py       # pull_since(), push_records() – LWW-Merge
└── ai.py         # stream_generate(), stream_remix(), classify()
scripts/
└── migrate_from_helga.py  # Einmalige Migration
data/             # Volume-Mount (gitignored): recipes.db, images/
```

---

## Sync-Protokoll

Jede Tabelle hat `updated_at` (Unix-ms) und `deleted` (0/1). Last-Write-Wins: höherer `updated_at` gewinnt.

```
GET  /api/sync?since=<ts>   → SyncPullResponse (alle Änderungen nach ts)
POST /api/sync              → SyncPushRequest  → SyncPullResponse (server-wins zurück)
```

Neue Tabellen im Schema erfordern:
1. `CREATE TABLE` in `db.py → SCHEMA`
2. Spalten in `db.py → TABLE_COLUMNS`
3. Pydantic-Model in `models.py`
4. Felder in `SyncPayload` und `PAYLOAD_FIELD` in `sync.py`

---

## KI-Endpunkte

| Endpunkt | Verhalten |
|----------|-----------|
| `POST /api/ai/generate` | SSE-Stream: Rezept aus Prompt generieren |
| `POST /api/ai/remix` | SSE-Stream: Rezept abwandeln |
| `POST /api/ai/classify` | JSON: 5 Felder (protein_type, effort, cuisine, meal_slot, season_fit) |
| `POST /api/ai/parse-receipt` | JSON: Kassenbon-Foto (base64) per Vision-Modell auslesen → store_name, purchase_date, total_amount, items[] |

**Hinweis Vision:** `parse-receipt` sendet das Bild an ein vision-fähiges Modell
(Default `gpt-4o-mini` bzw. `claude-haiku-4-5-20251001`). Bei eigenem `AI_MODEL`
muss dieses Bilder unterstützen, sonst greift in der App der On-Device-OCR-Fallback.

SSE-Format: `data: <chunk>\n\n`, Abschluss: `data: [DONE]\n\n`. Zeilenumbrüche im Chunk als `\n` escaped.

---

## Deployment

```bash
# Ersteinrichtung (einmalig auf dem Server)
git clone <repo-url> /opt/helga-sync
cp .env.example .env   # API_KEY + AI_API_KEY befüllen
docker compose -f docker-compose.prod.yml up -d --build

# Update vom Entwicklungsrechner (SERVER_HOST in Makefile anpassen)
make deploy

# Helga-Daten importieren (einmalig)
make migrate           # Pfad zur Helga-DB im Makefile-Rezept anpassen
```

Alle weiteren Befehle: `make help`

---

## Konventionen

- Auth immer über `Depends(require_auth)` – nie weglassen
- Pfad-Traversal bei Bildabruf abwehren: `Path(filename).name`
- Schema-Änderungen sind **nicht destruktiv** – nur `ALTER TABLE ... ADD COLUMN`
- `deleted = 1` ist Soft-Delete – nie physisch löschen (würde Sync brechen)
