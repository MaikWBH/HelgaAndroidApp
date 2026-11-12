# Phase 4 — fastapi-templates (Sync-Backend `server/app/`)

Skill: fastapi-templates. Bereich laut Feature-Index: Server (Sync, KI-Proxy, Bilder, OFF, Vorschläge); die Bon-Teile
(`receipts.py`, `receipt_api.py`) wurden in Phase 3a/3b bereits überarbeitet und werden hier nur berührt, wo es sein muss.
Feature-Pläne mit Server-Bezug: `.claude/features/sync/plan.md`, `.claude/features/ki/plan.md`.

Pfade: `$S` = `server` · `$SA` = `server/app` · `$ST` = `server/tests`

**Verifikation `V`:** `cd server && python3 -m pytest -q` (Abhängigkeiten siehe `server/CLAUDE.md` → „Tests").
Kein Docker, keine echten KI-Aufrufe. Jeder Schritt endet mit grünem `V`; die Testzahl darf nur steigen.

## Modelle
- Definition: Sonnet 5.5 · Umsetzung: Haiku 5.5 · Datum: 2026-11-12 (Plan vom Nutzer freigegeben)

## Ziel und Bereich
`server/app/main.py` bündelt Sync, KI, Bilder, Open Food Facts und Vorschläge in einer Datei; `ai.py` (849 Zeilen)
mischt Anbieter-Aufrufe, Prompts und Bon-Logik. Fehler der KI-Anbieter, riesige Uploads und beliebige URLs im
Rezept-Import sind unbehandelt, und ohne `API_KEY` ist die öffentlich erreichbare API offen. Phase 4 teilt `main.py`
in Router, trennt die Anbieter-Schicht aus `ai.py` und härtet die Randfälle. **Das Verhalten für die Android-App bleibt
kompatibel**: keine Pfad-, Feld- oder Formatänderung an bestehenden Endpunkten (neu sind nur zusätzliche Fehlerantworten
bei Missbrauch/Ausfall).

## Entscheidungen (aus grilling, 2026-11-12)
- Umfang → Struktur **und** Härtung.
- SSRF bei `/api/ai/import-url` → Nur `http`/`https`; Loopback, Privatnetz, Link-Local, Reserviert, Multicast und
  nicht spezifizierte Adressen werden mit 422 abgelehnt, auch nach Weiterleitungen (jeder Hop wird geprüft).
- Start ohne `API_KEY` → Server startet nicht, außer `ALLOW_NO_AUTH=1` (lokale Entwicklung, Tests).
- `/api/suggestions/aisles` und `/units` (von der App ungenutzt) → bleiben unverändert erhalten, ziehen in den
  Vorschläge-Router um.
- Konfiguration → zentral in `settings.py`, **ohne** `pydantic-settings`; alle Werte werden zur Aufrufzeit über das
  Modul gelesen (`settings.X`), Tests stellen sie per `monkeypatch` um.
- `ai.py` → Anbieter-Schicht wird als `ai_client.py` abgetrennt; `ai.py` behält Prompts und Aufgaben und stellt für Tests
  und Skripte dünne Aliase bereit.
- Eingabe-Limits → ja, moderate Limits (Werte in S10/S11).
- Von Sonnet 5.5 ohne Rückfrage festgelegt (Folge der Entscheidungen): KI-Ausfall bei JSON-Endpunkten → HTTP 502
  mit deutscher Meldung; bei SSE wird der erste Chunk vorab geholt, schlägt er fehl → 502, bricht es danach ab → Log und
  Stream endet ohne `[DONE]` (wie bisher, nur geloggt). Die Testsuite bleibt synchron (`TestClient`), kein
  `pytest-asyncio`. Uvicorn-Worker, Dockerfile und Compose bleiben unangetastet (Phase 5).

## Befunde
| # | Datei:Zeile | Befund | Regel (Skill/Guideline) |
|---|-------------|--------|-------------------------|
| 1 | `$SA/main.py` (211 Zeilen) | Sync, KI, Bilder, OFF und Vorschläge in einer Datei, rohes SQL in Routen | Skill „Project Structure": Router je Bereich (`api/…`); `receipt_api.py` zeigt das Zielmuster |
| 2 | `$SA/ai.py:312` | `import_url` lädt jede URL, folgt Weiterleitungen, liest die ganze Antwort → Zugriff auf Heimnetz-Adressen (SSRF) | Sicherheit; `server/CLAUDE.md` „API hängt öffentlich im Internet" |
| 3 | `$SA/auth.py:11` | Ohne `API_KEY` ist `require_auth` offen, Produktion bricht nur über das Homelab-Skript ab | `server/CLAUDE.md` „Auth immer…" |
| 4 | `$SA/ai.py:392,425,471,497,520,546` | `raise_for_status()` ohne Abfangen → HTTP 500 ohne Log; bei SSE bricht die Verbindung ab | Skill „Error Handling" |
| 5 | `$SA/main.py` `upload_image` | liest die ganze Datei in den Speicher, kein Größenlimit, blockierendes `write_bytes` im Event-Loop | Skill „Async Patterns" |
| 6 | `$SA/models.py:268-352` | Request-Modelle der KI/OFF-Endpunkte ohne Längen-/Mengenlimits | Skill „Schemas" |
| 7 | `$SA/ai.py:21-31`, `$SA/db.py:9` | Umgebungsvariablen an drei Stellen gelesen (`ai.py`, `db.py`, `settings.py`), `load_dotenv()` doppelt | Skill „Core configuration" |
| 8 | `$SA/ai.py:154-164` | `_openai_headers`/`_anthropic_headers` geben ein Tupel zurück, behaupten aber `-> dict`; Header werden in `_receipt_once` nochmals von Hand gebaut | Duplikat |
| 9 | `$SA/main.py` `suggest_items` | `LIKE '%'||q||'%'` ohne Escaping von `%`/`_` | Eingabe-Hygiene |
| 10 | – | Keine Logs in `ai.py`, keine zentrale Logging-Konfiguration | Betrieb |

Geprüft und **in Ordnung**: Warteschlange mit zwei Uvicorn-Workern (atomarer Claim in `process_parse_queue`), Pfad-Traversal-Schutz
beim Bildabruf, `hmac.compare_digest` in `require_auth`, Soft-Delete/LWW im Sync, alle Routen außer Health mit Auth.

## Schritte (Stufe B arbeitet nur diese Liste ab)

### Block A — Fundament (Befunde 3, 7)
- [x] **S0 Baseline** — `V` ausführen, Testzahl unter „Abschluss" notieren (erwartet 110). Akzeptanz: grün.
- [x] **S1 Konfiguration zentral** — Dateien: `$SA/settings.py`, `$SA/ai.py`, `$SA/db.py`,
  `$S/scripts/receipt_testset.py`, betroffene Tests in `$ST`, `$S/.env.example`. In `settings.py` ergänzen (alle mit
  `os.getenv`, Defaults unverändert): `AI_PROVIDER` („openai"), `AI_API_KEY`, `AI_MODEL`, `AI_API_BASE`,
  `AI_VISION_MODEL`, `AI_RECEIPT_MODEL`, `AI_RECEIPT_EFFORT` („medium"), `DB_PATH` („data/recipes.db"),
  `ALLOW_NO_AUTH` (`bool`, wahr bei „1"/„true"/„yes", Standard falsch), `LOG_LEVEL` („INFO"). In `ai.py`: alle
  `os.getenv`-Konstanten und `load_dotenv()` löschen; jede Nutzung durch `settings.AI_PROVIDER` usw. ersetzen, **zur
  Aufrufzeit** gelesen (auch `OPENAI_BASE` → Hilfsfunktion `_openai_base()` mit `settings.AI_API_BASE or "https://api.openai.com/v1"`).
  In `db.py`: `DB_PATH = settings.DB_PATH` (Modulattribut bleibt, weil `conftest.py` es patcht). Tests, die
  `ai.AI_PROVIDER`/`AI_RECEIPT_MODEL`/`AI_RECEIPT_EFFORT`/`AI_VISION_MODEL` patchen, patchen jetzt `settings.…`;
  `scripts/receipt_testset.py:152` liest `settings.AI_VISION_MODEL`. `.env.example`: Zeilen `ALLOW_NO_AUTH=` (leer, mit
  Kommentar „nur lokal") und `LOG_LEVEL=INFO` ergänzen. Akzeptanz: `V` grün; `grep -n "os.getenv" $SA/ai.py $SA/db.py`
  leer; `grep -n "load_dotenv" $SA/*.py` nur in `settings.py`.
- [ ] **S2 Start ohne API_KEY verweigern** — Dateien: `$SA/main.py` (Lifespan), `$ST/conftest.py`,
  neu `$ST/test_startup_auth.py`. Am Anfang des Lifespans (vor `init_db()`): ist `settings.API_KEY` leer **und**
  `settings.ALLOW_NO_AUTH` falsch → `raise RuntimeError("API_KEY fehlt. Für lokale Entwicklung ALLOW_NO_AUTH=1 setzen.")`.
  Ist `API_KEY` leer und `ALLOW_NO_AUTH` wahr → `logger.warning("API läuft ohne Authentifizierung (ALLOW_NO_AUTH)")`.
  `require_auth` bleibt unverändert. `conftest.py`: Fixture `client` setzt zusätzlich `settings.ALLOW_NO_AUTH = True`
  (`monkeypatch`). Tests (je mit `with TestClient(app)`): ohne Key und ohne Flag → `RuntimeError`; mit Flag → `GET
  /api/health` 200; mit `API_KEY="x"` und ohne Flag → 200. Akzeptanz: `V` grün. Danach Block A pushen.

### Block B — Struktur (Befunde 1, 8, 9)
- [ ] **S3 Schutznetz für den Umbau** — Datei: neu `$ST/test_routes_guard.py`. Vor dem Umbau eine Momentaufnahme
  festhalten: `python3 -c "from app.main import app; from fastapi.routing import APIRoute; print(sorted((m, r.path) for r in app.routes if isinstance(r, APIRoute) for m in sorted(r.methods - {'HEAD','OPTIONS'})))"`
  (in `$S`) und die Ausgabe als `EXPECTED_ROUTES` (Menge von Tupeln) in den Test kopieren. Test 1: die Routenmenge
  des Apps ist gleich `EXPECTED_ROUTES`. Test 2: mit `settings.API_KEY = "geheim"` und `ALLOW_NO_AUTH = False` antwortet
  jede Route außer `GET /api/health` auf einen Aufruf **ohne** `X-Api-Key` (Pfadparameter `{…}` durch `x` ersetzen,
  kein Body) mit 401; `GET /api/health` mit 200. Akzeptanz: `V` grün. Der Test bleibt in S4–S11 **unverändert** grün; eine
  neue Route (z. B. keine) wird nur mit ausdrücklichem Hinweis in „Abweichungen" ergänzt.
- [ ] **S4 Router: Sync, Bilder, OFF** — Dateien: neu `$SA/sync_api.py`, `$SA/images_api.py`, `$SA/off_api.py`,
  `$SA/main.py`. Je Datei `router = APIRouter(dependencies=[Depends(require_auth)])` (Muster `receipt_api.py`), Routen
  1:1 verschoben (`GET/POST /api/sync`; `POST /api/images/upload`, `GET /api/images/{filename}`;
  `POST /api/off/lookup-barcode`, `/api/off/search` mit dem Import von `off` oben in der Datei statt in der Funktion).
  `main.py` bindet sie per `include_router` ein. Reihenfolge der `include_router`-Aufrufe in `main.py` am Ende:
  `receipt_api`, `sync_api`, `images_api`, `off_api` (danach S5/S6 ergänzen). Akzeptanz: `V` grün (insbesondere
  `test_routes_guard.py`); `grep -c "app\.\(get\|post\)" $SA/main.py` sinkt entsprechend.
- [ ] **S5 Router: KI** — Dateien: neu `$SA/ai_api.py`, `$SA/main.py`. Verschieben: `_sse`, `POST /api/ai/generate`,
  `/remix`, `/classify`, `/nutrition`, `/import-url`, `/parse-receipt`, `/categorize-items` **und**
  `POST /api/receipts/reconcile` (Pfad bleibt, Handler ruft `ai`). `router = APIRouter(dependencies=[Depends(require_auth)])`
  ohne Prefix (Pfade vollständig angeben). `include_router(ai_api.router)` **nach** `receipt_api` einbinden (wie bisher,
  damit `/api/receipts/reconcile` nicht von `/api/receipts/{id}` geschluckt wird). Akzeptanz: `V` grün.
- [ ] **S6 Router: Vorschläge** — Dateien: neu `$SA/suggestions_api.py`, `$SA/main.py`,
  neu `$ST/test_suggestions.py`. Verschieben: `GET /api/suggestions/items`, `/aisles`, `/units` unverändert im
  Verhalten. Einzige Änderung in `items`: `%`, `_` und `\` im Suchwort mit `\` escapen und `LIKE ? ESCAPE '\'`
  verwenden. Tests (Daten über `POST /api/sync` oder direkt per `app_db`): `q="oma"` findet „Tomate" nicht
  fälschlich über Wildcards; `q="%"` liefert nur Namen, die ein echtes `%` enthalten (hier: leer); `aisles` und `units`
  liefern wie vorher (Treffer / leer). Danach hat `main.py` nur noch: Lifespan, App, Health, `include_router`-Zeilen,
  Exception-Handler (folgt S8). Akzeptanz: `V` grün; `wc -l $SA/main.py` < 80.
- [ ] **S7 `ai_client.py` abtrennen** — Dateien: neu `$SA/ai_client.py`, `$SA/ai.py`, `$ST/test_ai_receipt_config.py`.
  Nach `ai_client.py` verschieben (mit Konfiguration über `settings`): `DEFAULT_MODEL`, `DEFAULT_VISION_MODEL`,
  `DEFAULT_RECEIPT_MODEL`, `_EFFORT_MODEL_PREFIXES`, `RECEIPT_MAX_TOKENS`, `ANTHROPIC_BASE`, `_openai_base()`, die
  Modellwahl, die HTTP-Aufrufe für Text-Stream, Einmalaufruf, Vision und Bon. Öffentliche Namen dort:
  `text_model()`, `vision_model()`, `receipt_model()`, `uses_effort()`, `receipt_payload()`, `openai_headers()` und
  `anthropic_headers()` (beide geben **nur** das Header-Dict zurück; Basis-URL separat), `stream_text()`,
  `complete_text()`, `vision_text()`, `receipt_text()`. Der doppelt von Hand gebaute Header in `_receipt_once` entfällt
  (nutzt `openai_headers()`/`anthropic_headers()`). `ai.py` importiert diese Namen und definiert **Aliase**, die Tests und
  `scripts/receipt_testset.py` nutzen: `_receipt_model`, `_receipt_payload`, `_receipt_once`, `_stream`, `_call_once`,
  `_vision_once`, `_model`, `_vision_model`; die Aufgaben in `ai.py` rufen weiterhin die Aliase (damit
  `monkeypatch.setattr(ai, "_receipt_once", …)` wirkt). `test_ai_receipt_config.py`: `ai.httpx` → `ai_client.httpx`.
  Akzeptanz: `V` grün; `wc -l $SA/ai.py` sinkt um mindestens 250; `grep -n "httpx.AsyncClient" $SA/ai.py` nur noch in
  `import_url`. Danach Block B pushen.

### Block C — Härtung (Befunde 2, 4, 5, 6, 10)
- [ ] **S8 Logging und KI-Fehlerabbildung** — Dateien: `$SA/ai_client.py`, `$SA/ai_api.py`, `$SA/main.py`,
  neu `$ST/test_ai_errors.py`. In `ai_client.py`: `class AiUpstreamError(Exception)` mit `status: int | None`; alle
  Anbieter-Aufrufe fangen `httpx.HTTPStatusError` (→ `AiUpstreamError("KI-Anbieter antwortete mit Status N", N)`),
  `httpx.RequestError` inkl. Timeout (→ „KI-Anbieter nicht erreichbar") und fehlerhafte Antwortformen
  (`KeyError`, `IndexError`, `ValueError` beim Auslesen → „unerwartete Antwort des KI-Anbieters"); Meldungen enthalten
  **nie** Schlüssel oder Antwortkörper; `logger.warning` mit Status. `main.py`: `logging.basicConfig(level=settings.LOG_LEVEL,
  format="%(asctime)s %(levelname)s %(name)s: %(message)s")` und `@app.exception_handler(AiUpstreamError)` →
  `JSONResponse(status_code=502, content={"detail": "KI-Dienst hat nicht geantwortet"})`. `_sse` in `ai_api.py` holt
  den ersten Chunk vorab (`anext`); `AiUpstreamError` dabei → normaler 502; `AiUpstreamError` später → `logger.warning`,
  Stream endet ohne `[DONE]`. `receipts.py`-Fänge (`except Exception`) bleiben. Tests (Fake-Client mit
  Status 500 bzw. `httpx.ConnectError`): `POST /api/ai/classify` → 502 mit der Meldung; `POST /api/ai/generate` mit
  Fehler im ersten Chunk → 502; mit Fehler nach dem ersten Chunk → 200, Body enthält den ersten Chunk, **kein**
  `[DONE]`; `parse_receipt` über `POST /api/ai/parse-receipt` → 502; `categorize` in `receipts.py` schluckt den Fehler
  weiter (bestehender Test grün). Akzeptanz: `V` grün.
- [ ] **S9 SSRF-Schutz im Rezept-Import** — Dateien: neu `$SA/url_guard.py`, `$SA/ai.py` (`import_url`),
  neu `$ST/test_url_guard.py`. `url_guard.py`: `async def ensure_public_http_url(url: str) -> None` (wirft `ValueError` mit
  deutscher Meldung): Schema nur `http`/`https`, Host vorhanden; Host per `asyncio.to_thread(socket.getaddrinfo, …)`
  auflösen (Auflösung in `_resolve(host)` kapseln, damit Tests sie patchen); jede aufgelöste Adresse darf **nicht**
  `is_private`, `is_loopback`, `is_link_local`, `is_reserved`, `is_multicast` oder `is_unspecified` sein (`ipaddress`),
  sonst `ValueError("Diese Adresse ist nicht erlaubt")`. `import_url`: `follow_redirects=False`; Schleife mit höchstens
  5 Weiterleitungen (`urljoin` mit dem `Location`-Header), **jeder** Hop vor dem Abruf mit `ensure_public_http_url`
  prüfen; Antwort als Stream lesen und bei mehr als 5 MiB (`settings.MAX_IMPORT_PAGE_BYTES`, neu, Standard 5 242 880)
  abbrechen. `ValueError`/zu groß/zu viele Weiterleitungen → `HTTPException(422, …)` (Texte: „Diese Adresse ist nicht
  erlaubt", „Seite ist zu groß", „Zu viele Weiterleitungen"); die bestehenden 422-Fehlertexte bleiben. Bekannte
  Grenze (im Code-Kommentar vermerken): DNS-Rebinding zwischen Prüfung und Abruf ist nicht ausgeschlossen. Tests
  (`_resolve` gepatcht, kein Netz): `http://127.0.0.1/`, `http://[::1]/`, `http://192.168.178.1/`, `http://10.0.0.5/`,
  `http://169.254.169.254/`, `http://localhost/` (löst zu 127.0.0.1 auf), `ftp://example.com/` werden abgelehnt;
  `http://93.184.216.34/` ist erlaubt; ein Hostname, der **eine** öffentliche und **eine** private Adresse liefert, wird
  abgelehnt; Weiterleitung von öffentlich auf `http://192.168.1.1/` → 422; mehr als 5 Weiterleitungen → 422; zu große
  Seite → 422. Akzeptanz: `V` grün.
- [ ] **S10 Eingabe-Limits der Modelle** — Dateien: `$SA/models.py`, neu `$ST/test_request_limits.py`. Oben in
  `models.py`: `ShortText = Annotated[str, StringConstraints(max_length=200)]`,
  `LongText = Annotated[str, StringConstraints(max_length=4000)]`, `TagList = Annotated[List[ShortText], Field(max_length=300)]`.
  Anwenden (Pydantic 2): `AiGenerateRequest`: `prompt` LongText, `available_tags` TagList, `custom_instructions`
  Optional[LongText], `exclude_allergens` Liste ≤ 50 ShortText. `AiRemixRequest`: `recipe_name` ShortText,
  `recipe_description`/`remix_prompt` LongText, `recipe_ingredients` Liste ≤ 200 ShortText, `recipe_instructions` Liste
  ≤ 100 LongText, `available_tags` TagList, `exclude_allergens` wie oben. `AiClassifyRequest`: `name` ShortText,
  `description` LongText, `tags` Liste ≤ 100 ShortText, `ingredients` Liste ≤ 200 ShortText. `AiNutritionRequest`:
  gleiche Felder, `portions` `Field(ge=1, le=100)`. `AiUrlImportRequest.url` max 2048. `OffLookupBarcodeRequest.barcode`
  max 32. `OffSearchRequest`: `query` max 200, `limit` `Field(ge=1, le=20)`. `ReceiptParseRequest.image_base64` max
  11 200 000 Zeichen (≈ 8 MiB Binärdaten). `ReceiptReconcileRequest`: beide Listen ≤ 300 Einträge. `CategorizeRequest.names`:
  Elemente ShortText (Liste bleibt ≤ 50). Tests: je Modell ein Aufruf knapp am Limit → nicht 422; knapp darüber → 422;
  `portions=0` → 422. Akzeptanz: `V` grün (alle bestehenden Tests bleiben unverändert grün; müsste einer am Limit
  scheitern, nicht das Limit anheben, sondern melden).
- [ ] **S11 Upload-Limit für Bilder** — Dateien: `$SA/images_api.py`, `$SA/settings.py`, `$ST/test_images.py`
  (neu oder bestehende Datei erweitern). `settings.MAX_IMAGE_BYTES = 8 * 1024 * 1024`
  (`MAX_RECEIPT_IMAGE_BYTES` bleibt, wird intern `= MAX_IMAGE_BYTES`). `upload_image`: Datei in Blöcken zu 1 MiB lesen,
  bei Überschreitung `HTTPException(413, "Bild ist zu groß")` und keine Datei hinterlassen; Schreiben im Thread
  (`asyncio.to_thread(dest.write_bytes, content)`). Erweiterungsprüfung und Antwortformat (`uuid`, `filename`)
  unverändert. Tests: kleines `.jpg` → 200 und Datei existiert; Datei knapp über dem Limit (Limit im Test per
  `monkeypatch` auf 1 KiB senken) → 413 und `IMAGES_DIR` leer; `.exe` → 400 wie bisher. Akzeptanz: `V` grün. Danach Block C
  pushen.

### Block D — Dokumentation und Abschluss
- [ ] **S12 Doku und Index** — Dateien: `$S/CLAUDE.md` (Struktur-Abschnitt auf die neuen Dateien `sync_api.py`,
  `images_api.py`, `off_api.py`, `ai_api.py`, `suggestions_api.py`, `ai_client.py`, `url_guard.py` umstellen; Abschnitt
  „Konventionen" um: Start nur mit `API_KEY` oder `ALLOW_NO_AUTH=1`; KI-Ausfall → 502; Eingabe-Limits; SSRF-Schutz;
  neue Umgebungsvariablen), `doku/casaos-deployment.md` (Hinweis: `API_KEY` ist Pflicht, sonst startet der Container
  nicht), `.claude/features/sync/plan.md` und `.claude/features/ki/plan.md` (Abschnitt „Phase 4"),
  `.claude/phasen/README.md` (Phase 4 → „umgesetzt (Stufe B)", Umsetzungsmodell eintragen). Akzeptanz: Dateien
  geändert; `V` grün. Danach pushen.

## Nicht-Ziele
- `pydantic-settings`, SQLAlchemy/Repository-Schicht, `pytest-asyncio`, API-Versionierung (`/api/v1`), CORS (nur mobile
  Clients), JWT/Benutzerverwaltung.
- `receipts.py`, `receipt_api.py`, `sync.py`-Logik und `db.py`-Schema (in Phase 3 überarbeitet; nur Importe/Konfiguration
  dürfen sich ändern).
- Dockerfile, Compose, Uvicorn-Worker, Healthcheck (Phase 5); Homelab-Repo `server_home_lab`.
- Änderungen an der Android-App.
- Entfernen der alten Endpunkte `/api/ai/parse-receipt` und `/api/receipts/reconcile` (alte App-Versionen).

## Abschluss
- [ ] Baseline aus S0 notiert; Endstand `V`: … Tests (Anzahl), grün
- [ ] `wc -l`-Werte vorher/nachher: `main.py` 211 → …, `ai.py` 849 → …
- [ ] Gemeldete Abweichungen und Ausnahmen (je Schritt eine Zeile)
- [ ] Bereichs-`plan.md` (sync, ki), Phasen-README, `server/CLAUDE.md` aktualisiert (S12)
- [ ] **Hinweis für den Nutzer (Rollout):** Vor dem nächsten Deploy im Homelab prüfen, dass `API_KEY` in der Produktions-`.env`
      gesetzt ist — der Container startet sonst nicht mehr. Der Server ist nach dem Tag `vX.Y.Z` per `scripts/app.sh bump helga`
      im Homelab-Repo auszurollen (liegt außerhalb dieses Repos).
- [ ] **Prüfliste (Gerät/Server)** für den Nutzer, von Stufe B nicht abgehakt: Rezept per URL importieren (öffentliche
      Seite klappt, `http://192.168.x.x` wird abgelehnt); Rezept per KI erzeugen mit absichtlich falschem `AI_API_KEY` → App
      zeigt einen Fehler statt Hängen; Foto-Upload eines normalen Bildes klappt; Sync und Bon-Scan wie vorher.
