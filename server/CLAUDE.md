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
├── main.py            # App, Lifespan (Start-Prüfung, Bon-Warteschlange), Logging, 502-Handler, Health
├── settings.py        # Alle Umgebungsvariablen zentral; zur Aufrufzeit über settings.X gelesen
├── auth.py            # require_auth
├── db.py              # Schema, init_db(), get_db(), SYNC_TABLES, RECEIPT_TABLES, now_ms()
├── models.py          # Pydantic-Modelle (Sync, KI, Bon-Archiv, OFF) mit Eingabe-Limits
├── sync.py            # pull_since(), push_records() – LWW-Merge
├── sync_api.py        # Router /api/sync
├── images_api.py      # Router /api/images (Upload mit Größenlimit, Abruf)
├── off.py             # Open-Food-Facts-Abfragen; off_api.py = Router /api/off
├── suggestions_api.py # Router /api/suggestions (Autocomplete)
├── ai.py              # Prompts und Aufgaben: stream_generate(), stream_remix(), classify(), import_url(), parse_receipt(), reconcile_receipt()
├── ai_client.py       # Anbieter-Schicht: Modellwahl, HTTP zu OpenAI/Anthropic, AiUpstreamError
├── ai_api.py          # Router KI-Endpunkte (SSE, JSON) und /api/receipts/reconcile
├── url_guard.py       # SSRF-Schutz für den Rezept-Import
├── ingredient_parser.py # Zutatenzeilen aus Rezept-Import
├── categories.py      # gemeinsamer Kategorien-Katalog (Bons, Auswertung, Gänge)
├── receipt_text.py    # Schlüssel- und Datumshilfen für Bons (reine Funktionen)
├── receipts.py        # Bon-Speicherschicht, Scan, Anreichern, Lernen, Warteschlange
└── receipt_api.py     # Router /api/receipts (Bon-Archiv, Lexikon)
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

**Alt-Clients schützen:** Neue Spalten an Sync-Tabellen in `sync.py → PRESERVE_IF_UNSET` eintragen, solange alte
App-Versionen pushen. Der Upsert überschreibt sie dann nur, wenn der Client das Feld mitschickt (z. B.
`store_aisles.category_key`); sonst behalten sie ihren Server-Wert.

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
| `GET /api/receipts/aggregates` | Auswertung (Monate, Wochen, Märkte, Kategorien, Artikel), bei jedem Aufruf aus den Bons berechnet; die App hält davon einen Abzug für Offline-Diagramme |
| `POST /api/ai/categorize-items` | JSON: bis zu 50 Artikelnamen → Kategorie aus dem gemeinsamen Katalog (erst Lernspeicher `product_categories`, Unbekanntes in einem Aufruf mit dem Bon-Modell) |

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

Da die API öffentlich erreichbar ist, muss `API_KEY` in Produktion gesetzt sein. Ohne ihn startet der Server nicht (Ausnahme nur lokal: `ALLOW_NO_AUTH=1`), und `validate-env.sh` im Homelab bricht ohne `API_KEY` ab.

Alle weiteren Befehle: `make help`

---

## Konventionen

- Auth immer über `Depends(require_auth)` – nie weglassen (API hängt öffentlich im Internet)
- Pfad-Traversal bei Bildabruf abwehren: `Path(filename).name`
- Schema-Änderungen sind **nicht destruktiv** – nur `ALTER TABLE ... ADD COLUMN`
- `deleted = 1` ist Soft-Delete – nie physisch löschen (würde Sync brechen)
- Router je Bereich: `APIRouter(dependencies=[Depends(require_auth)])`, eingebunden in `main.py` per `include_router`
- Start nur mit `API_KEY` oder mit `ALLOW_NO_AUTH=1` (nur lokale Entwicklung und Tests); sonst bricht der Lifespan mit `RuntimeError` ab
- KI-Ausfall (Anbieter-Status, nicht erreichbar, unbrauchbare Antwort) → HTTP 502 mit `KI-Dienst hat nicht geantwortet`; Meldungen enthalten nie Schlüssel oder Antwortkörper, Details stehen im Log
- SSE-Streams: Fehler vor dem ersten Chunk → 502; Abbruch danach → Log, Stream endet ohne `[DONE]`
- Eingabe-Limits in `models.py`: Kurztexte 200, Langtexte 4000 Zeichen, Tag-Liste 300, Zutaten-/Schrittlisten gedeckelt, Bild-Base64 ca. 8 MiB, `portions` 1–100, OFF-`limit` 1–20
- Rezept-Import per URL (SSRF-Schutz in `url_guard.py`): nur http/https; Loopback, Privatnetz, Link-Local, Reserviert, Multicast und unspezifizierte Adressen werden abgelehnt, auch nach Weiterleitungen (jeder Hop geprüft, max. 5); Seite max. 5 MiB. Bekannte Grenze: DNS-Rebinding zwischen Prüfung und Abruf
- Bild-Upload: max. 8 MiB (`settings.MAX_IMAGE_BYTES`), darüber 413 ohne Datei
- Neue Umgebungsvariablen: `ALLOW_NO_AUTH` (nur lokal, Standard aus), `LOG_LEVEL` (Standard `INFO`). Alle übrigen Werte stehen in `settings.py`
