# Phase 8 — Server-Härtung und App-Robustheit (Rest der Nutzerliste aus Phase 6)

> Status: **freigegeben, Stufe B läuft (2026-11-27).** Gate 0 erledigt; Zeilennummern sind Stand `99735ec` und vor jeder
> Änderung per `grep -n` neu zu suchen (Phase 7 verschiebt viele davon).

Basispfad `$B` = `app/src/main/kotlin/com/helga/android` · Testpfad `$T` = `app/src/test/kotlin/com/helga/android` ·
`$R` = `app/src/main/res` · `$SA` = `server/app` · `$ST` = `server/tests`

Quellen: Nutzerliste in [phase-6-daten/review-bericht.md](phase-6-daten/review-bericht.md) („An den Nutzer zur
Entscheidung"); Entscheidungen der Fragerunden aus Phase 7 Stufe A (2026-11-26).

## Modelle
- Definition: Opus 5.5 · Umsetzung: Sonnet 5.5 (Gate 0, 2026-11-27) · Datum: 2026-11-26

## Ziel und Bereich
Die in Phase 7 zurückgestellten, vom Nutzer freigegebenen Einträge der Phase-6-Nutzerliste umsetzen: Härtung des
Sync-Servers (Eingabe-Limits, Weiterleitungen, Stream-Fehler, Prüfung des API-Schlüssels vor dem Lesen des Bodys) und
Robustheit der App (Abbrüche, Lost Update bei der Gang-Zuordnung, Bon-UI, kleinere Korrekturen). Dazu die
„Doku statt Code"-Fälle: Spec und Guidelines werden an den Code angepasst, der Code bleibt. Bereiche: Server
(`server/app`), Sync/Bons/Einkaufsliste/Wochenplan in der App, Guidelines.

## Entscheidungen (Nutzer, 2026-11-26)
- **Server (Runde 3):** V5 `Location` per `.get`, fehlt sie → 422 · B4-S12 `max_length` für `q`/`item` · B3-S1
  großzügige Limits für `ReceiptIn`/`ReceiptItemIn` (Texte ≤ 500 Zeichen, IDs ≤ 64, höchstens 500 Positionen) ·
  B4-S2/B4-P2 Anthropic-`error`-Event und kaputte Stream-Frames → `AiUpstreamError` · B4-P3 ASGI-Middleware prüft
  `X-Api-Key` und `Content-Length` vor dem Lesen des Bodys.
- **App (Runde 4):** B6-S1/B9-S6 `CancellationException` überall weiterwerfen, mit Regeltest · B9-P1 verständliche
  Fehlertexte statt `e.message` · V6 vor dem Schreiben neu laden, nur schreiben wenn `aisle` leer, nicht abgehakt und
  Name gleich · B6-P1 `ExistingWorkPolicy.APPEND_OR_REPLACE` statt `KEEP` für die Gang-Zuordnung · B8-S1 Undo beim
  Löschen eines Lexikon-Eintrags · B8-S7/B8-P4 „Erneut versuchen" liest wirklich neu aus · B8-S2 `imeAction` in den
  Bon-Dialogen · B7-S7/B7-P2 `loaded` auswerten · B7-P4 `Locale.ROOT` · B9-S3 Wochenbilanz als Flow · B2-P3
  Bestandsfotos vor dem Upload verkleinern.
- **Doku statt Code:** B3-S4/B4-S3 (KI-Ausfall ergibt 200 mit `parsed:false`/leer, kein 502), V10 (Pflichtobjekte in
  Antwort-DTOs ohne Standardwert), B1-S1 (Migration 36→37 löscht lokal gelöschte Bons; Plan-Annahme 7), B8-P3
  („Positionen" zeigt `expectedTotal`, gewollt), B8-S4 (Lexikon-Leerzustand nur Text), B7-P1 (Budget-Karte vor dem
  Leerzustand), B1-P2 (drei `@Transaction`-Methoden bleiben).
- **Nachträglich entschieden (2026-11-27):** B5-S6 `pytest` auf feste Version und Basis-Image auf Patchversion pinnen ·
  B10-S4 Timer-Chip in der `toggleable`-Zeile semantisch trennen (Prüfung mit TalkBack bleibt Gerätetest).
  **Hinweis für Gate 0 / Stufe-A-Nachtrag:** Für diese beiden Punkte gibt es noch keine Schritte in diesem Plan; sie
  werden vor dem Start von Stufe B als eigene Schritte ergänzt (Server-Block bzw. App-Block).
- **Festgelegt in Stufe A:** Limits so großzügig, dass bisherige App-Daten durchgehen (Phase 7 S6 setzt abgelehnte Bons
  auf `rejected`, eine Ablehnung ist also nicht mehr endlos). Middleware lässt `/api/health` ohne Schlüssel durch und
  antwortet mit 401 bzw. 413 **bevor** der Body gelesen wird; Grenzwert = `settings.MAX_REQUEST_BYTES` (neu, Standard
  10 MiB, größer als das Bild-Limit 8 MiB). Undo per Snackbar „Eintrag gelöscht" mit Aktion „Rückgängig" (8 s);
  gelöscht wird erst nach Ablauf (lokal ausgeblendet bis dahin).

## Befunde
| # | Datei:Zeile (Stand `99735ec`) | Befund |
|---|-------------------------------|--------|
| V5 | `$SA/ai.py:246` | `resp.headers["location"]` → `KeyError` → 500 |
| B4-S12 | `$SA/suggestions_api.py:16,35,48` | `q`/`item` ohne `max_length` |
| B3-S1 | `$SA/models.py:429-470` | `ReceiptIn`/`ReceiptItemIn` ohne Limits |
| B4-S2/P2 | `$SA/ai_client.py` (`_stream_anthropic`, `_stream_openai`) | `error`-Event übersprungen; `data: null` → `TypeError` |
| B4-P3 | `$SA/auth.py`, `$SA/main.py` | Body wird vor `require_auth` gelesen (Vermutung) |
| B6-S1/B9-S6 | 25 Dateien mit `catch (e: Exception)`/`catch (_: Exception)` in `$B` (Liste per `grep`) | `CancellationException` verschluckt |
| B9-P1 | `$B/data/repository/AiRepository.kt:27-31`, `RecipeDetailViewModel`, `RecipeListViewModel` | `e.message` („HTTP 500 …") in der UI |
| V6 | `$B/data/shopping/AisleAssigner.kt:60-89` | Lost Update |
| B6-P1 | `$B/data/sync/SyncScheduler.kt:89` | `KEEP` verliert Artikel während des KI-Laufs |
| B8-S1 | Lexikon-Screen (`$B/ui/receipts/`, `grep -rn deleteLexicon`) | Löschen ohne Undo |
| B8-S7/P4 | `$B/ui/receipts/ReceiptDetailScreen.kt:172,242-245` | „Erneut versuchen" schließt nur |
| B8-S2 | `$B/ui/receipts/ReceiptReviewComponents.kt` (Kopf- und Positionsdialog) | keine `imeAction` |
| B7-S7/P2 | `$B/ui/receipts/CostOverviewViewModel.kt:26,75`, `CostOverviewScreen.kt` | Leerzustand blitzt auf |
| B7-P4 | `$B/data/receipts/ReceiptStats.kt:34` | `"%d-W%02d".format` mit Standard-Locale |
| B9-S3 | `$B/ui/weekplan/WeekplanViewModel.kt:191-194` | `recipesForDay` als Snapshot in `combine` |
| B2-P3 | `$B/data/receipts/ReceiptUploader.kt` (Zweig `uploadReceiptImage`) | Bestandsfotos unverkleinert, 413 |

## Schritte (Stufe B arbeitet nur diese Liste ab)

Vorgehen, Verifikation `V`/`TC`/`VS`, Regeln und Push je Block: wie in
[Phase 7](phase-7-fixes-und-markdown-import.md) („Vorgehen je Schritt", „Verifikation", „Allgemeine Regeln"), mit
Commit-Präfix „Phase 8 S<n>:". Testzahlen: Baseline aus S0, jeder Schritt nennt den Zuwachs.

### Block A — Baseline
- [x] **S0 Baseline** — `V`, `TC`, `VS` notieren (erwartet: Endstand Phase 7, App 351, Server 192).

### Block B — Server-Härtung
- [x] **S1 Weiterleitung ohne `Location` (V5)** — `$SA/ai.py`, `$ST/test_url_guard.py`. `location = resp.headers.get("location")`;
  fehlt sie → `raise ValueError("Ungültige Weiterleitung")` (der bestehende Pfad macht daraus 422). Test (+1)
  `test_redirect_without_location_is_rejected` (Attrappe wie in `test_redirect_to_private_address_is_rejected`, 302 ohne
  Header → 422). Akzeptanz: `VS` grün.
- [x] **S2 Längen für Vorschläge (B4-S12)** — `$SA/suggestions_api.py`, `$ST/test_suggestions.py`. `Query(default="", max_length=100)`
  an allen drei Endpunkten. Test (+1) `test_too_long_query_is_422` (101 Zeichen → 422 je Endpunkt, ein Test mit drei
  Aufrufen). Akzeptanz: `VS` grün.
- [x] **S3 Limits für Bons (B3-S1)** — `$SA/models.py`, `$ST/test_request_limits.py`. `ReceiptItemIn`/`ReceiptIn`:
  alle `str`-Felder `Field(default=…, max_length=500)`, `id`/`receipt_id`/`store_id`/`shopping_list_id` `max_length=64`,
  `items: list[ReceiptItemIn] = Field(default_factory=list, max_length=500)`. Bestehende Tests müssen grün bleiben (sonst
  stoppen: ein Limit ist zu knapp). Test (+2) `test_receipt_text_limit` (501 Zeichen in `store_name` → 422),
  `test_receipt_item_count_limit` (501 Positionen → 422). Akzeptanz: `VS` grün.
- [x] **S4 Stream-Fehler (B4-S2, B4-P2)** — `$SA/ai_client.py`, `$ST/test_ai_errors.py`. Anthropic-Stream: Event mit
  `type == "error"` → `raise AiUpstreamError("Anbieter meldet Fehler im Stream")`; beide Streams: `json.loads` liefert kein
  `dict` oder fehlende Schlüssel → `AiUpstreamError("Unerwartete Stream-Antwort")` statt `TypeError`/`AttributeError`
  (Log ohne Body). Tests (+2) `test_anthropic_error_event_is_502` (Fehler vor dem ersten Chunk → 502),
  `test_null_frame_ends_stream_without_done` (nach erstem Chunk → Stream ohne `[DONE]`). Akzeptanz: `VS` grün.
- [x] **S5 Schlüssel und Größe vor dem Body (B4-P3)** — `$SA/settings.py` (`MAX_REQUEST_BYTES = int(os.getenv("MAX_REQUEST_BYTES", str(10 * 1024 * 1024)))`),
  neu `$SA/guard_middleware.py` (reine ASGI-Middleware: für `scope["type"] == "http"` und Pfad mit Präfix `/api/` außer
  `/api/health`: ist `settings.API_KEY` gesetzt und der Header `x-api-key` nicht gleich (`hmac.compare_digest`) → 401
  JSON `{"detail": "Nicht autorisiert"}`; `content-length` > `MAX_REQUEST_BYTES` → 413; sonst weiterreichen — liest den
  Body nie), `$SA/main.py` (`app.add_middleware(…)`), `require_auth` bleibt zusätzlich bestehen, `server/CLAUDE.md`
  (Struktur-Liste und `.env.example`-Hinweis), neu `$ST/test_guard_middleware.py`. Tests (+3): 401 ohne Schlüssel auf
  `POST /api/receipts/scan` **ohne** dass `save_receipt_image` aufgerufen wird (per `monkeypatch` Zähler), 413 bei
  `Content-Length` über der Grenze, `/api/health` ohne Schlüssel 200. Akzeptanz: `VS` grün; `test_health_auth.py` grün.
- [x] **S14 Pinning (B5-S6, Nachtrag 2026-11-27)** — `server/requirements-dev.txt` (`pytest==9.2.0`, die in der Cloud
  verwendete Version), `server/Dockerfile` (`ARG PYTHON_IMAGE=python:3.12.16-slim-bookworm`, Tag auf Docker Hub
  geprüft), `server/CLAUDE.md` (Satz: Updates von Basis-Image und `pytest` geschehen bewusst). Kein Docker-Build in der
  Cloud. Akzeptanz: `VS` grün. **Danach Block B pushen.** (Rollout-Hinweis für den Nutzer: Server vor App, `make backup`.)

### Block C — App-Robustheit
- [x] **S6 Abbrüche weiterwerfen (B6-S1, B9-S6)** — alle Dateien aus
  `grep -rln "catch (e: Exception)\|catch (_: Exception)" $B`, außer den nicht-suspendierenden Fundstellen
  `data/preferences/AppPreferences.kt`, `data/remote/ApiErrorMapping.kt`, `data/util/ReceiptImagePreprocessor.kt`,
  `ui/ai/RecipeJsonLdParser.kt`, `ui/components/BarcodeScanner.kt` (in Stufe B per Blick auf die Stelle bestätigen; ist eine
  davon doch in einer Coroutine, gehört sie dazu). Vor jedem betroffenen `catch` `catch (e: CancellationException) { throw e }`
  mit `import kotlinx.coroutines.CancellationException`. Regeltest (+1) `abbrueche_werden_weitergeworfen` in
  `KotlinQualityRulesTest`: für jede Datei außerhalb der Ausnahmeliste gilt Anzahl
  `catch (e: CancellationException) { throw e }` ≥ Anzahl `catch (\w+: Exception)`. Akzeptanz: `V` grün; `TC` +1.
- [ ] **S7 Verständliche KI-Fehler (B9-P1)** — `$B/data/repository/AiRepository.kt`, betroffene ViewModels
  (`grep -rn "e.message" $B/ui`), `$R/values/strings.xml`, `$T/AiRepositoryTest.kt`. `classifyRecipe`/`estimateNutrition`
  fangen `HttpException`/`IOException` und werfen `AiUnavailableException(kind)` (neu in `data/repository/`,
  `kind` = `OFFLINE`, `SERVER`, `AI_DOWN` für 502); ViewModels zeigen `UiText.Res` je Art (Strings `ai_error_offline`
  „Keine Verbindung zum Server.", `ai_error_server` „Der Server hat einen Fehler gemeldet.", `ai_error_ai_down`
  „Die KI ist gerade nicht erreichbar."). Tests (+3, je Art). Akzeptanz: `V` grün; `grep -n "e.message" $B/ui -r` nur noch
  in Log-Aufrufen.
- [ ] **S8 Gang-Zuordnung ohne Lost Update (V6, B6-P1)** — `$B/data/shopping/AisleAssigner.kt`, `$B/data/sync/SyncScheduler.kt`,
  `$T/AisleAssignerTest.kt`. Nach dem KI-Aufruf je Artikel frisch laden (`findById` des DAO über das Repository-Muster
  in `AisleAssigner`); schreiben nur, wenn `aisle.isBlank() && isChecked == 0 && name == <Name beim Aufruf>`.
  `enqueueUniqueWork(AisleCategorizeWorker.UNIQUE, ExistingWorkPolicy.APPEND_OR_REPLACE, request)`. Tests (+2): abgehakter
  bzw. umbenannter Artikel wird nicht überschrieben. Akzeptanz: `V` grün.
- [ ] **S9 Bon-UI (B8-S1, B8-S7/P4, B8-S2)** — Lexikon-Screen + ViewModel, `$B/ui/receipts/ReceiptDetailScreen.kt`,
  `$B/ui/receipts/ReceiptReviewComponents.kt`, `$R/values/strings.xml`. Lexikon: Löschen blendet den Eintrag aus und zeigt
  Snackbar `lexicon_deleted` „Eintrag gelöscht" mit Aktion `action_undo` „Rückgängig" (`SnackbarDuration.Long`); erst
  wenn die Snackbar ohne Aktion endet, ruft das ViewModel das Löschen auf. Detail: der Knopf „Erneut versuchen" ruft
  `viewModel.reparse()` (vorher `dismissReparseError()`). Dialoge: einzeilige Felder `ImeAction.Next`, letztes Feld
  `ImeAction.Done` mit `KeyboardActions(onDone = { focusManager.clearFocus() })`. Test (+1) im Lexikon-ViewModel-Test:
  Löschen erst nach `confirmDelete`, nicht nach `undo`. Akzeptanz: `V` grün.
- [ ] **S10 Kleinkram (B7-S7/P2, B7-P4, B9-S3)** — `$B/ui/receipts/CostOverviewScreen.kt` (solange `!state.loaded`
  `LoadingIndicator` statt Leerzustand), `$B/data/receipts/ReceiptStats.kt` (`String.format(Locale.ROOT, "%d-W%02d", …)`),
  `$B/ui/weekplan/WeekplanViewModel.kt` (`weekBalance` aus `combine(days, allRecipes, <Flow aus observeRecipesForDays(dayIds)>)`
  per `flatMapLatest` statt `recipesForDay`-Snapshot). Tests (+2): `ReceiptStatsTest` mit `Locale.setDefault(Locale("ar"))`
  liefert `2026-W46`; Wochenbilanz aktualisiert sich nach neuem Rezept (Flow-Emission). Akzeptanz: `V` grün.
- [ ] **S11 Bestandsfotos verkleinern (B2-P3)** — `$B/data/receipts/ReceiptUploader.kt`, `$B/data/receipts/ReceiptImageStore.kt`,
  `$T/ReceiptUploaderTest.kt`. Vor `uploadReceiptImage` `imageStore.downscaleInPlace(file)` (gleiche Grenzen wie `prepare`:
  längste Seite 2000 px, JPEG 85; Datei unter 2000 px bleibt unverändert). 413 trotzdem → `pendingUpload = 0` und
  `parseStatus = "rejected"` (Muster Phase 7 S6). Test (+1): 413 beendet die Wiederholung. Akzeptanz: `V` grün.
- [ ] **S15 Timer-Chip erreichbar (B10-S4, Nachtrag 2026-11-27)** — Kochansicht (`$B/ui/recipes/RecipeCookScreen.kt`,
  `toggleable`-Zeile mit `SuggestionChip`): Zeile und Chip semantisch trennen (Chip außerhalb der `toggleable`-Fläche,
  z. B. `Row` mit eigener `toggleable`-Fläche für Text/Checkbox und danach der Chip), Verhalten und Optik unverändert.
  Kein Unit-Test; TalkBack-Prüfung steht in der Nutzer-Liste (S13). Akzeptanz: `V` grün; `UiConventionsTest` grün.
  **Danach Block C pushen.**

### Block D — Doku statt Code
- [ ] **S12 Spec und Guidelines anpassen** — `.claude/phasen/phase-3-daten/server-vertrag.md` (B3-S4/B4-S3: KI-Ausfall bei
  `_ai_parse`, `categorize`, `reparse` ergibt 200 mit `parsed:false` bzw. leer — gewollt; B8-P3: „Positionen" zeigt die
  erwartete Summe), `.claude/guidelines/kotlin-quality.md` §9 (V10: Pflichtobjekte von Antwort-DTOs ohne Standardwert
  sind erlaubt, weil ein Standardobjekt leere Datensätze in Room schreiben würde), `.claude/phasen/phase-3a-bons-server-scan.md`
  (B1-S1: Migration 36→37 löscht bewusst; B8-S4 und B7-P1 als akzeptierte Abweichung), `phase-6-daten/review-bericht.md`
  (Spalte „Vorschlag" der Nutzerliste: je Eintrag „entschieden 2026-11-26: Phase 7 S<n> / Phase 8 S<n> / Doku / offen").
  Akzeptanz: Dateien geändert; `V`, `VS` grün. **Danach pushen.**

### Block E — Abschluss
- [ ] **S13 Abschluss** — `.claude/phasen/README.md`, Feature-Index, Bereichspläne `sync`, `bons-kosten`, `einkaufsliste`,
  `wochenplan`; Abschnitt „Abschluss" ausfüllen; Nutzer-Liste für Gerät/Homelab: Upload großer Bestandsfotos, Lexikon-Undo,
  „Erneut versuchen", Gang-Zuordnung während des KI-Laufs, KI-Fehlertexte, Server ohne Schlüssel (401 vor Upload).
  **Danach pushen.**

## Nicht-Ziele
- Neue Endpunkte, Room-Schema-Änderungen, Docker-Builds und echte KI-Aufrufe.

## Abschluss
- [x] Baseline S0: App `TC` 351, Server 192 passed (wie erwartet)
- [ ] Endstand: App `TC` …, Server …
- [ ] Gemeldete Abweichungen: …
