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
├── main.py         # Routen (Sync, KI, Bilder, OFF), Lifespan (Bon-Warteschlange)
├── settings.py     # API_KEY, IMAGES_DIR, Warteschlangen-Takt, Foto-Limit (zur Aufrufzeit lesen)
├── auth.py         # require_auth
├── db.py           # Schema, init_db(), get_db(), SYNC_TABLES, RECEIPT_TABLES, now_ms()
├── models.py       # Pydantic-Modelle (SyncPayload, DTOs für KI, Bon-Archiv)
├── sync.py         # pull_since(), push_records() – LWW-Merge
├── ai.py           # stream_generate(), stream_remix(), classify(), parse_receipt(), reconcile_receipt()
├── categories.py   # gemeinsamer Kategorien-Katalog (Bons, Auswertung, Gänge)
├── receipt_text.py # Schlüssel- und Datumshilfen für Bons (reine Funktionen)
├── receipts.py     # Bon-Speicherschicht, Scan, Anreichern, Lernen, Warteschlange
└── receipt_api.py  # Router /api/receipts (Bon-Archiv, Lexikon)
scripts/
├── migrate_from_helga.py         # Einmalige Migration
├── receipt_testset.py            # Qualitätstest des Bon-Modells auf eigenen Fotos
└── restore_purged_receipts.py    # automatisch gelöschte Bons wiederherstellen
tests/                            # pytest (siehe Abschnitt Tests)
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
| `POST /api/ai/parse-receipt` | JSON: Kassenbon-Foto (base64) auslesen → Kopf (Markt, Adresse, Datum, Uhrzeit, Summe, Rabatt) und Positionen. Bleibt für alte App-Versionen |
| `POST /api/receipts/scan` | Multipart: Bon-Foto speichern, auslesen (KI), anreichern; bei KI-Ausfall vorläufig anlegen und in die Warteschlange stellen |
| `GET/PUT/DELETE /api/receipts…` | Bon-Archiv: bon-weises Protokoll (LWW, Cursor `server_seq`), Lexikon unter `/api/receipts/lexicon` |

Vollständiger Vertrag (Schema, Endpunkte, Prompt, KI-Parameter): `.claude/phasen/phase-3-daten/server-vertrag.md`.

**Bon-Modell:** Alle Bon-Aufrufe (Auslesen, Abgleich) nutzen `AI_RECEIPT_MODEL` (Standard
`claude-haiku-5-5` bei `AI_PROVIDER=anthropic`, `gpt-4o-mini` bei `openai`). Neue Modelle bekommen
**keine** Sampling-Parameter (`temperature` führt bei Haiku 5.5 zu HTTP 400), sondern
`output_config.effort` (`AI_RECEIPT_EFFORT`, Standard `medium`). Es gibt keinen automatischen Rückfall
auf ein größeres Modell; die Qualität wird mit `scripts/receipt_testset.py` auf eigenen Bon-Fotos
(außerhalb des Repos) gegen ein Referenzmodell (`AI_VISION_MODEL`) geprüft. Das Modell muss bild-fähig
sein, sonst greift in der App der On-Device-OCR-Fallback.

SSE-Format: `data: <chunk>\n\n`, Abschluss: `data: [DONE]\n\n`. Zeilenumbrüche im Chunk als `\n` escaped.

---

## Tests

```bash
cd server
pip install fastapi==0.115.5 aiosqlite==0.20.0 httpx==0.28.1 python-dotenv==1.0.1 python-multipart==0.0.20 pytest
python3 -m pytest -q
```

`pip install -r requirements.txt` scheitert in der Cloud-Umgebung am Build von `recipe-scrapers`
(`jstyleson`); die Tests brauchen das Paket nicht. Tests nutzen `TestClient` mit temporärer DB
(`tests/conftest.py`) und rufen **nie** echte KI- oder OFF-Dienste auf (per `monkeypatch` ersetzen).

---

## Deployment

Produktiv läuft der Server **nicht** aus diesem Clone, sondern als Git-Submodule im Homelab-Repo `server_home_lab` (`apps/helga`, Service `helga-sync`, Port 8000, öffentlich über `food.rockshomeassistant.casa` per Cloudflare Tunnel). `docker-compose.prod.yml` bleibt die maßgebliche Compose-Datei. Das Homelab legt nur ein Override darüber (Datenpfad, Containername).

```bash
# Lokal entwickeln (unabhängig vom Homelab)
cp .env.example .env   # API_KEY + AI_API_KEY befüllen
make dev

# Neue Version ausrollen
git tag vX.Y.Z && git push --tags
# dann in server_home_lab: scripts/app.sh bump helga vX.Y.Z → commit → Release → scripts/deploy.sh

# Helga-Daten importieren (einmalig)
make migrate           # Pfad zur Helga-DB im Makefile-Rezept anpassen
```

Da die API öffentlich erreichbar ist, muss `API_KEY` in Produktion gesetzt sein. Ohne ihn ist `require_auth` offen, und `validate-env.sh` im Homelab bricht deshalb ab.

Alle weiteren Befehle: `make help`

---

## Konventionen

- Auth immer über `Depends(require_auth)` – nie weglassen (API hängt öffentlich im Internet)
- Pfad-Traversal bei Bildabruf abwehren: `Path(filename).name`
- Schema-Änderungen sind **nicht destruktiv** – nur `ALTER TABLE ... ADD COLUMN`
- `deleted = 1` ist Soft-Delete – nie physisch löschen (würde Sync brechen)
