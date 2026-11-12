# Phase 3a — Bons: Server-Archiv, Datenmodell, Scan und Review

Skills: fastapi-templates (Server), android-clean-architecture (Datenschicht), mobile-android-design (Scan-/Review-UI).
Fortsetzung: [Phase 3b](phase-3b-auswertung-gang-kategorien.md) (Auswertung, Diagramme, Gang-Kategorien).
Grundlage: [Entscheidungen](phase-3-bons-kategorien-entscheidungen.md).

Pfade: `$S` = `server` · `$SA` = `server/app` · `$ST` = `server/tests` ·
`$B` = `app/src/main/kotlin/com/helga/android` · `$U` = `$B/ui` · `$T` = `app/src/test/kotlin/com/helga/android` ·
`$R` = `app/src/main/res` · `$D` = `.claude/phasen/phase-3-daten`

Datendateien (verbindlich, werden nicht nacherfunden):
[server-vertrag.md](phase-3-daten/server-vertrag.md) (Schema, Endpunkte, Prompt, KI-Parameter) ·
[kategorien.md](phase-3-daten/kategorien.md) (Katalog).

## Modelle
- Definition: Fable 5.2 · Umsetzung: vom Nutzer manuell gewählt (offen) · Datum: 2026-11-11

## Ziel und Bereich
Bons liegen künftig vollständig auf dem Server (Daten und Foto); das Handy hält nur eine Warteschlange, die Bons der
letzten Monate und (ab 3b) Monatsaggregate. Der Scan lädt das verkleinerte Foto hoch, der Server liest es mit Haiku 5.5
aus (Uhrzeit, Filiale/Adresse, Rabatte, Pfand, Einheit, Kategorie), reichert es über ein lernendes Lexikon je Markt an
und speichert den Bon. Ohne Server zeigt die App sofort vorläufige ML-Kit-Werte und holt die Auswertung per WorkManager
nach. Bereiche laut Feature-Index: Bons & Kosten ([plan](../features/bons-kosten/plan.md)), Sync
([plan](../features/sync/plan.md)), Nährwerte/OFF ([plan](../features/naehrwerte/plan.md)), Server.

## Entscheidungen
Aus dem grilling (2026-11-11, Nummern wie in der Entscheidungsdatei): 2 Datenhaltung · 3 Zusatzfelder · 4 Offline-Scan ·
5 Artikelzuordnung mit Lexikon · 6 Haiku 5.5 + Testset · 8 Kategorienliste. Daraus und aus dem Code abgeleitet
(Stufe A, zur Freigabe unter „Annahmen"):

- **Protokoll:** Bons bekommen ein eigenes, bon-weises Protokoll (`GET/PUT/DELETE /api/receipts…`, LWW über
  `updated_at`, Cursor `server_seq`). Room bleibt Single Source of Truth: jede Server-Antwort wird zuerst in Room
  geschrieben, die UI beobachtet Room. Lokale Änderungen setzen weiter `dirty = 1` + `updatedAt`.
- **Scan synchron:** `POST /api/receipts/scan` speichert das Foto und liest sofort aus. Schlägt die KI fehl, landet der
  Bon in der Re-Parse-Warteschlange des Servers; die App behält ihre ML-Kit-Werte als „vorläufig".
- **Felder:** `raw_text` = Bon-Text, `name` = lesbarer Artikelname. Keine zusätzliche Namensspalte.
- **OFF-Vorschläge auf Abruf:** Der Server ruft Open Food Facts beim Scan nicht auf (Suche ist auf ca. 10 Anfragen je
  Minute begrenzt, ein Bon hat 20–40 Positionen). Im Review holt „Produkt zuordnen" je Position Treffer über das
  vorhandene `/api/off/search`; die Bestätigung lernt das Lexikon.
- **Lexikon lernt nur aus Korrekturen** (Name, Kategorie, OFF-Produkt weicht vom Server-Vorschlag ab).
- **Datum:** `purchase_day`/`purchase_time` als Text wie gedruckt; `purchase_date` bleibt Sortierschlüssel
  (Mitternacht UTC des Tages).
- **Room-Migrationstest ohne Emulator:** Test-Abhängigkeit `org.xerial:sqlite-jdbc`, Migration läuft gegen eine echte
  SQLite-Datei auf der JVM und wird mit dem exportierten Schema verglichen.

## Annahmen zur Freigabe

Aus der Entscheidungsdatei („Standardannahmen"):
1. Das Bonfoto wird vor dem Upload verkleinert (längste Kante 2000 px, JPEG 85) und die Kopie in `filesDir/receipts/`
   nach erfolgreichem Upload gelöscht; die Kamera-Temp-Datei in `cacheDir/receipts/` wird nach dem Scan gelöscht.
2. Bons verlassen `SYNC_TABLES` und bekommen eigene Endpunkte; Bestandsbons bleiben erhalten und werden migriert.
3. Neue Endpunkte und Parser bekommen Tests (auch `parse_receipt`, `reconcile_receipt`, Upload).
4. Die ungenutzten Server-Tabellen `ingredient_product_mappings`, `product_prices`, `product_purchases` werden
   **entfernt** (aus `SCHEMA`/`INDICES`; vorhandene Tabellen in der Produktions-DB bleiben unangetastet, kein `DROP`).
   Begründung: kein Code liest oder schreibt sie, die Auswertung rechnet direkt aus `receipt_items`.
5. Phase-1/2-Regeln bleiben; `ArchitectureRulesTest` und `UiConventionsTest` bleiben grün.

Eigene Annahmen der Stufe A:
6. **Bereits automatisch gelöschte Bons** (heutige 3-Monats-Löschung hat `deleted = 1` zum Server gesynct) werden nicht
   automatisch zurückgeholt. Ein Skript listet sie (Löschzeitpunkt mindestens 25 Tage nach Kaufdatum) und stellt sie
   nur mit `--apply` wieder her.
7. **Noch nicht gesyncte lokale Löschungen** werden bei der Room-Migration verworfen (lokal entfernt, nicht an den
   Server gemeldet), weil Auto-Löschung und Handlöschung nicht unterscheidbar sind. Ein von Hand gelöschter, noch nicht
   gesyncter Bon kann dadurch wieder auftauchen.
8. **Einstellung „Bons aufbewahren"** bleibt, bedeutet aber künftig „so lange offline auf dem Handy" (1/3/6/12 Monate,
   Standard 3; „nie" = alles laden und behalten). Auf dem Server wird nichts mehr automatisch gelöscht.
9. **Vorläufige Bons sind schreibgeschützt** (nur Ansehen und Löschen), bis die KI-Auswertung da ist; danach stehen sie
   mit „Bitte prüfen" in der Liste. Keine Push-Benachrichtigung.
10. **Alte App-Versionen** dürfen nach der Umschaltung weiter scannen (Server nimmt ihre Bons an), sehen aber keine Bons
    anderer Geräte mehr und können keine Bons mehr löschen. Beide Handys bekommen die neue App.
11. **KI-Provider:** Bon-Aufrufe nutzen den konfigurierten `AI_PROVIDER`. Haiku 5.5 setzt `AI_PROVIDER=anthropic`
    voraus; mit `openai` ist der Standard `gpt-4o-mini`. Ein gesetztes `AI_VISION_MODEL` wirkt nicht mehr auf Bons.
12. **Netz-Timeout:** Lese-/Schreib-Timeout des OkHttp-Clients steigt für alle Aufrufe von 60 s auf 120 s (der Scan
    wartet auf die KI).
13. **Bon-Fotos werden nicht mehr vorab aufs Handy geladen**; die Detailansicht lädt das Foto bei Bedarf vom Server.
14. **Lexikon-Ansicht** liegt unter Einstellungen → Bons, zeigt einen lokalen Abzug (Room) und braucht zum Aktualisieren
    und Löschen den Server.
15. **Server-Struktur:** neue Dateien `settings.py`, `auth.py`, `categories.py`, `receipt_text.py`, `receipts.py`,
    `receipt_api.py`; der Rest von `main.py` bleibt für Phase 4.

## Befunde (Stand 2026-11-11)
| # | Datei:Zeile | Befund | Regel (Skill/Guideline) |
|---|-------------|--------|-------------------------|
| 1 | `$SA/db.py:42-43`, `$SA/sync.py:83-90` | Bons hängen am Voll-Sync; jedes Gerät zieht alle Bons | Entscheidung 2 |
| 2 | `$B/data/sync/SyncWorker.kt:56-67`, `$B/data/repository/ReceiptRepository.kt:273-294` | 3-Monats-Löschung setzt `deleted = 1`/`dirty = 1` und löscht damit auch auf dem Server | Entscheidung 2 (Server = Archiv) |
| 3 | `$SA/ai.py:466-512` | Prompt ignoriert Uhrzeit und Adresse (Z.499), kein Feld für Rabatt, Pfand, Einheit, Kategorie | Entscheidung 3, 8 |
| 4 | `$SA/ai.py:37-40, 93-102` | Bon-Modell `claude-sonnet-4-6` über `AI_VISION_MODEL` | Entscheidung 6 |
| 5 | `$SA/ai.py:439`, `:367` | `temperature` wird immer gesendet; `claude-haiku-5-5` lehnt Sampling-Parameter mit HTTP 400 ab | Skill claude-api (Haiku 5.5) |
| 6 | `$SA/ai.py:534-542` | `_iso_to_ms` rechnet in Server-Ortszeit; Monatsgrenzen hängen an der Container-Zeitzone | Auswertung je Monat |
| 7 | `$ST/` | 1 Testdatei (11 Tests, nur `parse_package_grams`); keine Tests für `parse_receipt`, `reconcile_receipt`, Sync, Upload | Standardannahme 3; Skill fastapi-templates „Testing" |
| 8 | Umgebung | `python3 -m pytest` fehlt zunächst (`No module named pytest`); `pip install -r requirements.txt` scheitert an `recipe-scrapers` (Build von `jstyleson`). Mit `pip install fastapi==0.115.5 aiosqlite==0.20.0 httpx==0.28.1 python-dotenv==1.0.1 python-multipart==0.0.20 pytest` laufen die Tests: **11 passed**; `TestClient` mit temporärem `DB_PATH` funktioniert | Verifikation |
| 9 | `$SA/main.py:26-28`, `$SA/db.py:9` | `API_KEY`, `IMAGES_DIR`, `DB_PATH` werden beim Import gelesen; Tests brauchen eine umstellbare Quelle | Skill fastapi-templates „Dependency Injection" |
| 10 | `$SA/main.py:127-138` | Upload ohne Größenlimit, Dateiname zufällig; kein Bezug zum Bon | Server-Konvention |
| 11 | `$SA/db.py:322-354, 446-454` | Drei Tabellen ohne Leser/Schreiber | Standardannahme 4 |
| 12 | `$SA/off.py:126-192` | `/api/off/search` existiert; `INSERT OR REPLACE` ohne `server_seq` und überschreibt `is_favorite` — **nur dokumentiert**, Phase 4 | – |
| 13 | `$B/data/repository/ReceiptRepository.kt:82, 160, 409` | injiziert `SyncApiFactory` statt `ApiProvider` | architecture-layers „Hilt" |
| 14 | `$B/data/repository/ReceiptRepository.kt:136-145` | jede Ausnahme führt still zu ML Kit; kein Hinweis „vorläufig", kein Nachholen | Entscheidung 4 |
| 15 | `$B/data/local/ReceiptScanner.kt:82, 325` | ML Kit: `storeId` leer, Menge fest 1.0 | bleibt (nur vorläufige Werte) |
| 16 | `$B/data/sync/ImageUploadWorker.kt:71-94`, `$U/receipts/ReceiptScanViewModel.kt:105-118`, `$U/receipts/ReceiptScanScreen.kt:108` | Foto wird unverkleinert kopiert, nach Upload nicht gelöscht; Kamera-Temp-Datei bleibt | Standardannahme 1 |
| 17 | `$B/data/sync/ImageDownloadWorker.kt:41` | lädt alle Bon-Fotos vorab in den Coil-Cache | Ziel „Handy nicht zumüllen" |
| 18 | `$B/data/remote/SyncApi.kt` | kein `/api/off/search` in der App | Entscheidung 5 |
| 19 | `$B/di/NetworkModule.kt:35-36` | Lese-/Schreib-Timeout 60 s | Scan wartet auf KI |
| 20 | `$B/data/local/AppDatabase.kt:49, 830`, `app/schemas/` | Room v36, Migrationen 1→36 lückenlos, alle `private`; Schema `36.json` vorhanden; keine Migrationstests | Auftrag: Migration + Schema + Test |
| 21 | `$T/SyncCompletenessTest.kt:19` | verlangt für jede Entity Mapper in `SyncEngine` oder einen Eintrag in `intentionallyLocalOnly` | muss angepasst werden |
| 22 | `$U/receipts/*.kt` | 0× `rememberSaveable`, `.clickable` ohne Rolle (Scan Z.401/413, Detail Z.293), kein `Screen`/`Content`, keine Previews | Phase 2 Nicht-Ziel → jetzt |
| 23 | `$U/receipts/ReceiptListViewModel.kt` | nur `receipts`; kein Status, kein Nachladen | bons-kosten „Code-Qualität" |
| 24 | `.claude/features/bons-kosten/plan.md` Frage 8 | „kein Offline-Weg nötig" widerspricht Entscheidung 4 | Plan nachziehen (S36) |

## Schritte (Stufe B arbeitet nur diese Liste ab)

**Vorgehen je Schritt:** Änderung → Verifikation → Checkbox abhaken → Commit (Deutsch, ein Satz, Attribution laut
Session-Anweisung). Ein Commit pro Schritt. Push nach jedem Block.

**Verifikation `VS`** (Server-Schritte):
```bash
cd server && python3 -m pytest -q
```
**Verifikation `V`** (App-Schritte):
```bash
export ANDROID_HOME=/root/android-sdk ANDROID_SDK_ROOT=/root/android-sdk
./gradlew :app:testDebugUnitTest :app:compileDebugKotlin --console=plain --no-parallel --max-workers=1 --no-configuration-cache
```
Bei HTTP 429 von Maven Central ca. 45 s warten und wiederholen (höchstens 3 Versuche). Läuft `VS` oder `V` danach
nicht, gilt der Schritt als **nicht verifiziert**: stoppen und per `grilling` melden.

**Allgemeine Regeln für Stufe B**
- Zeilennummern sind Stand 2026-11-11; immer per `grep -n` neu suchen.
- Was eine Datendatei festlegt (Feldnamen, SQL, Prompt, Regeln), wird wörtlich übernommen. Widerspricht der Code einer
  Datendatei oder fehlt eine Angabe: stoppen und per `grilling` fragen, nicht selbst entscheiden.
- Server: jede Route mit `Depends(require_auth)`; nur `ALTER TABLE … ADD COLUMN`; kein physisches Löschen von Bons.
  Tests rufen nie echte KI- oder OFF-Dienste auf (immer per `monkeypatch` ersetzen).
- App: UI-Texte nur über `strings.xml` (String-Regel R-S und Prüf-grep `G-S` aus
  [Phase 2](phase-2-mobile-android-design.md), Abschnitt „Allgemeine Regeln für Stufe B"; Präfixe `receipt_`, `category_`, `settings_`,
  `common_`); ViewModels reden nur mit Repositories/Diensten der Datenschicht; `collectAsStateWithLifecycle`;
  neue klickbare Zeilen mit `role`; neue Dialog-/Eingabezustände mit `rememberSaveable` (Regel aus Phase 2 Block G).
- Stufe B läuft ohne Emulator. „Prüfliste (Gerät)" wird nicht abgehakt, sondern unverändert im Abschnitt „Abschluss"
  gesammelt.

### Block 0 — Basis
- [x] **S0 Baseline** — Server: `pip install fastapi==0.115.5 aiosqlite==0.20.0 httpx==0.28.1 python-dotenv==1.0.1
  python-multipart==0.0.20 pytest`, dann `VS` (erwartet 11 passed). App: `V` (erwartet 154 Tests, grün). Beide Ergebnisse
  und `grep -c 'version = 36' $B/data/local/AppDatabase.kt` (erwartet 1) unter „Abschluss" notieren. Weicht eine Zahl ab:
  notieren, weitermachen.

### Block A — Server-Grundlagen (abwärtskompatibel; Befunde 6–11)
- [x] **S1 Einstellungen, Auth, Test-Gerüst** — Dateien: neu `$SA/settings.py`, neu `$SA/auth.py`, `$SA/main.py`,
  neu `$S/requirements-dev.txt`, neu `$ST/conftest.py`, neu `$ST/test_health_auth.py`. `settings.py`: nach
  `load_dotenv()` die Modulvariablen `API_KEY`, `IMAGES_DIR` (`Path`, wird angelegt), `RECEIPT_QUEUE_INTERVAL_SEC`
  (`int`, Standard 600), `MAX_RECEIPT_IMAGE_BYTES = 8 * 1024 * 1024`. `auth.py`: `require_auth` unverändert aus
  `main.py:44-49`, liest aber `settings.API_KEY` **zur Aufrufzeit**. `main.py`: importiert `require_auth` aus `auth`,
  nutzt überall `settings.IMAGES_DIR` zur Aufrufzeit; die Zeilen 26–28 entfallen. `requirements-dev.txt`:
  `-r requirements.txt` und `pytest>=8`. `conftest.py`: legt `server/` in `sys.path`; Fixture `client(tmp_path,
  monkeypatch)` setzt `app.db.DB_PATH` auf `tmp_path/"t.db"`, `app.settings.IMAGES_DIR` auf `tmp_path/"img"`
  (anlegen), `app.settings.API_KEY = ""`, `app.settings.RECEIPT_QUEUE_INTERVAL_SEC = 0` und liefert
  `with TestClient(app) as c: yield c`; Fixture `db_path` gibt den Pfad zurück; Hilfsfunktion `run(coro)` =
  `asyncio.run(coro)`. Test: `/api/health` 200; mit `API_KEY = "x"` liefert `/api/sync?since=0` ohne Header 401, mit
  Header 200; frische DB liefert leere Listen. Akzeptanz: `VS` ≥ 14 passed; `grep -n 'os.getenv("API_KEY"' $SA/main.py`
  leer.
- [x] **S2 Kategorien-Katalog** — Dateien: neu `$SA/categories.py`, neu `$ST/test_categories.py`. `CATEGORIES:
  list[tuple[str, str]]` mit den 17 Einträgen aus `$D/kategorien.md` in Tabellenreihenfolge; `CATEGORY_KEYS`
  (Menge); `AISLE_CATEGORY_KEYS` (Einträge 1–15); `coerce(key) -> str` (getrimmt, kleingeschrieben, unbekannt → `""`);
  `prompt_lines(include_deposit=True) -> str` (`- <schluessel>: <Name>` je Zeile). Test: exakte Schlüsselliste in
  Reihenfolge (im Test ausgeschrieben), `coerce(" Obst_Gemuese ") == "obst_gemuese"`, `coerce("xyz") == ""`,
  `prompt_lines(False)` enthält kein `pfand`. Akzeptanz: `VS` grün.
- [x] **S3 Schema** — Dateien: `$SA/db.py`, neu `$ST/test_schema_migration.py`. (a) `ADDED_COLUMNS` um `receipts` und
  `receipt_items` nach Vertrag 1.1 ergänzen, `COLUMN_BACKFILL` um die zwei Einträge. (b) Die zwei Tabellen und drei
  Indizes aus Vertrag 1.2 in `SCHEMA`/`INDICES`. (c) Annahme 4: die `CREATE TABLE` für `ingredient_product_mappings`,
  `product_prices`, `product_purchases` (Z.322–354) und ihre 9 Indexzeilen (Z.446–454) löschen; den Kommentar Z.19–20
  auf „werden seit Phase 3a nicht mehr angelegt" kürzen. (d) Neue Konstante `RECEIPT_TABLES = ["receipts",
  "receipt_items"]` (noch **zusätzlich** zu `SYNC_TABLES`, beide Tabellen bleiben in dieser Liste bis S33).
  Test: (1) frische DB: `PRAGMA table_info` enthält alle neuen Spalten, beide neuen Tabellen existieren, die drei
  entfernten nicht; (2) **Bestandsmigration:** mit `sqlite3` eine DB im Altschema anlegen (die `CREATE TABLE`-Texte für
  `receipts`/`receipt_items` im Stand vor diesem Schritt als Konstante im Test, plus `sync_state`), zwei Bons einfügen
  (`purchase_date` = 1794268800000 und 0) mit je einer Position, `init_db()` laufen lassen → Zeilen noch da,
  `purchase_day` = `2026-11-10` bzw. `''`, `source = 'legacy'`, `parse_status = 'none'`, Position hat `unit = ''`,
  `discount = 0`; (3) zweiter `init_db()`-Lauf ändert nichts und wirft nicht. Akzeptanz: `VS` grün;
  `grep -c 'product_prices' $SA/db.py` = 0.
- [x] **S4 Schlüssel-Funktionen** — Dateien: neu `$SA/receipt_text.py`, neu `$ST/test_receipt_text.py`.
  `normalize_item_key`, `store_key`, `match_store`, `day_to_ms(day: str) -> int` (Mitternacht UTC per
  `calendar.timegm`, ungültig → 0) nach Vertrag 1.1/1.3. Test: alle Fälle aus `$T/ReceiptItemNormalizerTest.kt`
  übernommen (gleiche Eingaben, gleiche Erwartungen); `store_key("s1", "REWE") == "s1"`,
  `store_key("", "REWE Markt GmbH") == "name:rewe markt gmbh"`, `store_key("", "") == ""`; `match_store` mit Märkten
  `REWE`, `REWE City`, `Aldi`: „REWE Markt GmbH" → `REWE`, „rewe city" → `REWE City`, „Lidl" → kein Treffer,
  „Aldi" → `Aldi`; `day_to_ms("2026-11-10") == 1794268800000`, `day_to_ms("x") == 0`. Akzeptanz: `VS` grün.

### Block B — KI für Bons (Befunde 3–5)
- [x] **S5 Modell- und Nutzlastwahl** — Dateien: `$SA/ai.py`, `$S/.env.example`, neu `$ST/test_ai_receipt_config.py`.
  Neu nach Vertrag 5.1: `AI_RECEIPT_MODEL`, `AI_RECEIPT_EFFORT`, `DEFAULT_RECEIPT_MODEL`, `_receipt_model()`,
  `_uses_effort(model)`, `_receipt_payload(model, system, content)`, `async _receipt_once(system, user_text,
  image_b64=None, mime_type="image/jpeg", model=None) -> str` (Anthropic: `POST {ANTHROPIC_BASE}/messages` mit
  `_receipt_payload`, `content` = optional Bildblock + Textblock wie Z.441–445; OpenAI: wie `_vision_openai`, ohne Bild
  reiner Text, Modell `model or _receipt_model()`; Timeout 120 s). `_vision_once`/`_vision_model`/`DEFAULT_VISION_MODEL`
  bleiben bestehen (nur noch vom Testset-Skript genutzt), der Kommentar Z.34–36 wird auf diesen Zweck angepasst.
  `.env.example`: die drei Variablen aus Vertrag 5.1 mit je einer Kommentarzeile; Kommentar zu `AI_VISION_MODEL` auf
  „nur Referenzmodell für scripts/receipt_testset.py". Test (per `monkeypatch` auf die Modulvariablen):
  `_receipt_model()` für anthropic → `claude-haiku-5-5`, für openai → `gpt-4o-mini`, mit gesetztem
  `AI_RECEIPT_MODEL` → dieser Wert, `AI_VISION_MODEL` wirkt nicht; `_receipt_payload("claude-haiku-5-5", …)` hat
  `output_config == {"effort": "medium"}` und **keinen** Schlüssel `temperature`/`thinking`;
  `_receipt_payload("claude-sonnet-4-6", …)` hat `temperature == 0` und kein `output_config`; `max_tokens == 16000`.
  Akzeptanz: `VS` grün.
- [x] **S6 Prompt und Normalisierung** — Dateien: `$SA/ai.py`, `$SA/models.py`, `$SA/main.py`, neu
  `$ST/test_parse_receipt.py`. `RECEIPT_PARSE_USER` durch den Text aus Vertrag 5.2 ersetzen (`{categories}` per
  `.replace` mit `categories.prompt_lines()`; kein `str.format`, der Text enthält geschweifte Klammern).
  `normalize_parsed_receipt(data)` nach Vertrag 5.3. `parse_receipt(image_b64, mime_type="image/jpeg", model=None)`:
  ruft `_receipt_once`, schneidet JSON wie bisher (Z.554–562), gibt `normalize_parsed_receipt(...)` plus
  `"model"` zurück; unlesbare Antwort → leeres Ergebnis (`items = []`, Beträge 0). `_iso_to_ms` wird durch
  `receipt_text.day_to_ms` ersetzt und gelöscht. `ReceiptParseItem`/`ReceiptParseResponse` (`models.py:394-407`)
  bekommen die neuen Felder mit Standardwerten (`raw_text`, `unit`, `discount`, `is_deposit`, `category`; Kopf:
  `store_address`, `purchase_day`, `purchase_time`, `discount_total`); Route `ai_parse_receipt` unverändert im
  Verhalten. Test (`_receipt_once` per `monkeypatch` durch eine `async`-Attrappe mit festem Text): (1) vollständige
  Antwort mit 3 Positionen (Milch, Bananen mit Rabatt, Pfand) → alle Felder wie Vertrag, `purchase_date` =
  `day_to_ms`, Pfand hat `category == "pfand"`, `is_deposit == 1`; (2) Antwort in ```` ```json ````-Zaun; (3) Komma-Preise
  als Text (`"1,99"`); (4) unbekannte Kategorie → `""`, unbekannte Einheit → `stk`, `purchase_time "7:5"` → `''`,
  `"07:05"` bleibt; (5) kein JSON → leeres Ergebnis; (6) `product_name` leer → `name == raw_text`; (7) Prompt enthält
  alle 17 Schlüssel und weder `{categories}` noch „Uhrzeit und Adresse". Akzeptanz: `VS` grün;
  `grep -n '_iso_to_ms' $SA/*.py` leer.
- [x] **S7 Abgleich auf Bon-Modell, Tests** — Dateien: `$SA/ai.py`, neu `$ST/test_reconcile_receipt.py`.
  `reconcile_receipt` (Z.604–656) ruft `_receipt_once(system, user)` statt `_call_once`; sonst unverändert. Test
  (Attrappe): gültige Zuordnung; erfundene IDs werden gefiltert; kein JSON → drei leere Listen; leere Eingaben →
  Platzhalter „(keine)" im Nutzertext. Akzeptanz: `VS` grün; `grep -n '_call_once' $SA/ai.py` zeigt `reconcile_receipt`
  nicht mehr als Aufrufer.

### Block C — Server: Bon-Endpunkte (Befunde 1, 10)
- [x] **S8 Speicherschicht und Lesen/Schreiben** — Dateien: neu `$SA/receipts.py`, neu `$SA/receipt_api.py`,
  `$SA/models.py`, `$SA/main.py`, neu `$ST/test_receipts_crud.py`. `models.py`: `ReceiptItemIn/Out`, `ReceiptIn`,
  `ReceiptOut`, `ReceiptListResponse`, `ReceiptPutResponse`, `ReceiptScanResponse` nach Vertrag 2/3. `receipts.py`
  (nur DB-Logik, jede Funktion bekommt eine offene `db`-Verbindung): `next_seq(db)`, `get_receipt(db, id,
  include_deleted=False)`, `list_receipts(db, from_day, to_day, since_seq, include_deleted)`, `put_receipt(db,
  payload) -> (applied, receipt)`, `delete_receipt(db, id, updated_at)`. `receipt_api.py`:
  `router = APIRouter(prefix="/api/receipts", dependencies=[Depends(require_auth)])` mit `GET ""`, `GET
  "/{receipt_id}"`, `PUT "/{receipt_id}"`, `DELETE "/{receipt_id}"` nach Vertrag 3.1; `main.py`:
  `app.include_router(receipt_api.router)`. In diesem Schritt noch **ohne** Lernen und ohne `match_store` (folgen in
  S9). Test: PUT legt an, GET liefert mit Positionen in `position`-Reihenfolge; zweiter PUT mit älterem `updated_at` →
  `applied == false`, Daten unverändert; PUT ohne eine vorherige Position → diese `deleted = 1` und fehlt im GET;
  `deposit_total` wird berechnet; `purchase_day` setzt `purchase_date`; DELETE → GET 404, Liste ohne/mit
  `include_deleted`; `since_seq` liefert nur Änderungen und nach einer Positionsänderung den Bon erneut; `from_day`/
  `to_day` filtern inklusiv und lassen Bons ohne `purchase_day` bei `from_day` durch; DELETE unbekannter ID → 200;
  ungültige Kategorie wird `""`; `POST /api/receipts/reconcile` (Attrappe) antwortet weiter 200. Akzeptanz: `VS` grün.
- [x] **S9 Markt-Erkennung, Lexikon, Lernen** — Dateien: `$SA/receipts.py`, `$SA/receipt_api.py`, `$SA/models.py`,
  neu `$ST/test_receipt_lexicon.py`. `enrich(db, receipt, items)` und `learn(db, before_items, receipt, items)` nach
  Vertrag 4; `put_receipt` ruft `match_store` (Vertrag 3.1) und nach dem Schreiben `learn`. Endpunkte `GET /lexicon`,
  `DELETE /lexicon/{entry_id}` — **vor** `/{receipt_id}` deklarieren. Test: (1) PUT `review` mit Position
  `raw_text "G&G H-MILCH"`/`name "H-Milch"`, dann PUT `confirmed` mit `name "Milch 3,5 %"`, `category "milch_eier"` →
  ein Lexikon-Eintrag (`hits == 1`) und eine `product_categories`-Zeile (`source == "user"`); (2) erneuter PUT ohne
  Änderung → `hits` bleibt 1; (3) PUT `confirmed` ohne vorherigen Server-Stand lernt nichts; (4) `enrich` übernimmt
  Namen/Kategorie aus dem Lexikon desselben Markts und nicht aus einem anderen Markt; (5) `enrich` füllt leere Kategorie
  aus `product_categories`; (6) Pfand-Position wird weder gelernt noch angereichert, bekommt `pfand`; (7) `store_name
  "REWE Markt"` bei vorhandenem Markt `REWE` → `store_id` gesetzt; (8) Lexikon-Liste sortiert, Löschen blendet aus,
  erneutes Lernen reaktiviert (`deleted = 0`); (9) `GET /api/receipts/lexicon` wird nicht als `receipt_id` behandelt.
  Akzeptanz: `VS` grün.
- [x] **S10 Scan- und Bild-Endpunkt** — Dateien: `$SA/receipts.py`, `$SA/receipt_api.py`, neu
  `$ST/test_receipt_scan.py`. `write_parsed`, `save_receipt_image(receipt_id, upload) -> str`, Routen `POST /scan`
  und `POST /{receipt_id}/image` nach Vertrag 3.1 (Scan-Schritte 1–5, Bild). Die Datei wird in 64-KiB-Stücken
  gelesen und bei Überschreiten von `settings.MAX_RECEIPT_IMAGE_BYTES` mit 413 abgebrochen (Teil-Datei löschen). Test
  (`ai.parse_receipt` per `monkeypatch`): (1) Erfolg → `parsed == true`, Status `review`, `parse_status == "done"`,
  Datei `receipt_<id>.jpg` liegt in `IMAGES_DIR`, `GET /api/images/receipt_<id>.jpg` 200, Positionen angereichert
  (Lexikon aus S9 vorbefüllt); (2) Attrappe wirft → `parsed == false`, Status `provisional`, `parse_status ==
  "queued"`, Werte aus `preliminary` übernommen; (3) wie 2 ohne `preliminary` → leere Hülle mit `purchase_date ==
  captured_at`; (4) Wiederholung nach Erfolg ruft die KI nicht erneut (Zähler in der Attrappe); (5) `receipt_id
  "../x"` → 400, Endung `.gif`/`.exe` → 400, 9 MB → 413; (6) Bild-Endpunkt setzt nur `image_path`, unbekannter Bon →
  404; (7) leeres KI-Ergebnis zählt als Misserfolg. Akzeptanz: `VS` grün.
- [x] **S11 Re-Parse und Warteschlange** — Dateien: `$SA/receipts.py`, `$SA/receipt_api.py`, `$SA/main.py`, neu
  `$ST/test_receipt_queue.py`. `reparse(db_factory, receipt_id)`, `process_parse_queue(limit=5) -> int`, Route `POST
  /{receipt_id}/reparse`, `lifespan` startet/stoppt den Task nach Vertrag 3.1 „Warteschlange" (nur wenn
  `settings.RECEIPT_QUEUE_INTERVAL_SEC > 0`; beim Herunterfahren `task.cancel()`). Test: (1) nach fehlgeschlagenem Scan
  macht `run(process_parse_queue())` mit funktionierender Attrappe den Bon `review`/`done` und gibt 1 zurück; (2)
  dauerhafter Fehler → nach dem 5. Versuch `failed`, weiterer Lauf gibt 0 zurück; (3) `running` mit `parse_claimed_at`
  vor 20 Minuten wird wieder aufgenommen, eines vor 1 Minute nicht; (4) Reparse eines bestätigten Bons setzt `review`
  und ersetzt die Positionen (alte `deleted = 1`); (5) Reparse ohne Foto → 409, unbekannt → 404; (6) Reparse-Misserfolg
  lässt Werte stehen, `parse_status == "queued"`. Akzeptanz: `VS` grün.
- [x] **S12 Testset-Skript** — Dateien: neu `$S/scripts/receipt_testset.py`, neu `$ST/test_receipt_testset.py`,
  `$S/Makefile`. Aufruf: `python scripts/receipt_testset.py --dir <pfad> [--models a,b] [--out bericht.md]
  [--dry-run]`; `--dir` fällt auf `RECEIPT_TESTSET_DIR` zurück, fehlt beides → Fehlermeldung, Exit 2. Standard
  `--models`: `<_receipt_model()>,<AI_VISION_MODEL oder claude-sonnet-4-6>` (erstes = Kandidat, letztes = Referenz).
  Liest `*.jpg|*.jpeg|*.png|*.webp` (sortiert), optional je Foto `<name>.expected.json` mit `store_name`,
  `purchase_date`, `total_amount`, `item_count`. Je Foto und Modell `ai.parse_receipt(b64, mime, model=…)`
  nacheinander; Fehler eines Aufrufs werden als Zeile „Fehler: …" festgehalten. Reine Funktionen: `compare(result,
  truth) -> dict` (Markt: bereinigter Name gleich oder einer beginnt mit dem anderen; Datum gleich; Summe ±0,01;
  Positionsanzahl ±1) und `render_report(rows) -> str` (Markdown: Tabelle je Foto und Modell mit Markt, Datum, Summe,
  Positionen, Σ Positionen, Abweichung, Dauer in s; darunter Trefferquoten je Modell für Datum/Markt/Summe/Positionen).
  Maßstab: `expected.json`, sonst das Referenzmodell. `--dry-run` listet nur die Dateien und ruft keine KI. Fotos und
  Berichte werden nie ins Repo geschrieben (`--out` Standard: Standardausgabe). `Makefile`: Ziel `testset` mit Hinweis
  auf `DIR=`. Test: `compare` (Treffer, Summe knapp daneben, Positionen ±1, Markt-Präfix), `render_report` enthält die
  Quoten, `--dry-run` auf einem `tmp_path` mit zwei leeren `.jpg` listet beide, fehlendes Verzeichnis → Exit 2.
  Akzeptanz: `VS` grün. Prüfliste (Nutzer, mit echtem Schlüssel): 10–20 Fotos außerhalb des Repos ablegen, Skript
  laufen lassen, Bericht ansehen; bei schwacher Quote `AI_RECEIPT_EFFORT=high` testen.
- [x] **S13 Skript für automatisch gelöschte Bons** — Dateien: neu `$S/scripts/restore_purged_receipts.py`, neu
  `$ST/test_restore_purged.py`. `--db <pfad>` (Pflicht), `--min-days 25`, `--apply`. Kandidaten: `deleted = 1 AND
  purchase_date > 0 AND updated_at - purchase_date >= min_days * 86400000`. Ohne `--apply` nur Liste (Datum, Markt,
  Summe, Tage). Mit `--apply`: `sync_state.seq + 1`, Bon und seine Positionen mit **gleichem** `updated_at` wie der
  Bon auf `deleted = 0`, `updated_at = now`, `server_seq = seq`. Test auf temporärer DB: auto-gelöschter Bon (40 Tage)
  wird gelistet und mit `--apply` wiederhergestellt samt Position; am Kauftag gelöschter Bon bleibt gelöscht; ohne
  `--apply` keine Änderung. Akzeptanz: `VS` grün.
- [x] **S14 Server-Doku** — Datei: `$S/CLAUDE.md`. Struktur-Abschnitt um die neuen Module; KI-Tabelle um die
  Bon-Endpunkte (Verweis auf `$D/server-vertrag.md`); der Absatz „Hinweis Vision" wird ersetzt durch: Bon-Aufrufe
  nutzen `AI_RECEIPT_MODEL` (Standard `claude-haiku-5-5`), keine Sampling-Parameter, Qualität über
  `scripts/receipt_testset.py`; Abschnitt „Tests" mit dem `pip`-Befehl aus S0 und dem Hinweis auf `recipe-scrapers`.
  Akzeptanz: `grep -c 'claude-haiku-5-5' $S/CLAUDE.md` ≥ 1; `VS` grün. Danach Block A–C pushen.

### Block D — App: Datenmodell und Protokoll (Befunde 13, 18–21)
- [x] **S15 Migrationstest-Gerüst** — Dateien: `gradle/libs.versions.toml`, `app/build.gradle.kts`, neu
  `$T/RoomMigrationHarness.kt`, neu `$T/RoomMigrationHarnessTest.kt`. Katalog: `sqliteJdbc = "3.46.1.3"`,
  `sqlite-jdbc = { group = "org.xerial", name = "sqlite-jdbc", version.ref = "sqliteJdbc" }`;
  `testImplementation(libs.sqlite.jdbc)`. `RoomMigrationHarness` (nur Test): `open(): java.sql.Connection`
  (`jdbc:sqlite::memory:`); `createFromSchema(conn, version)` liest `schemas/com.helga.android.data.local.AppDatabase/
  <version>.json` bzw. den tatsächlichen Pfad unter `app/schemas` (per `File("schemas").walk()` die Datei
  `<version>.json` suchen), führt je Entity `createSql` (mit `${TABLE_NAME}` ersetzt) und alle Index-`createSql` aus;
  `run(conn, migration)` führt `migration.migrate(db)` mit einem `mockk<SupportSQLiteDatabase>` aus, dessen
  `execSQL(String)` an `conn.createStatement().execute(...)` weiterleitet; `assertMatchesSchema(conn, version)`
  vergleicht je Entity Spaltenname, Affinität (`TEXT`/`INTEGER`/`REAL`), `notNull` und Primärschlüssel aus `PRAGMA
  table_info` sowie die Indexnamen aus `PRAGMA index_list` (ohne `sqlite_autoindex…`) mit der JSON-Datei. Test:
  `createFromSchema(conn, 36)` + `assertMatchesSchema(conn, 36)` grün; nach `ALTER TABLE stores ADD COLUMN x TEXT`
  schlägt `assertMatchesSchema` fehl (`assertThrows`). Akzeptanz: `V` grün.
- [x] **S16 Room v37** — Dateien: `$B/data/local/entity/ReceiptEntity.kt`, `ReceiptItemEntity.kt`, neu
  `ReceiptLexiconEntity.kt`, `$B/data/local/AppDatabase.kt`, `app/schemas/…/37.json` (entsteht beim Build),
  `$T/SyncCompletenessTest.kt`, neu `$T/Migration36To37Test.kt`. `ReceiptEntity` neu: `storeAddress: String = ""`,
  `purchaseDay: String = ""`, `purchaseTime: String = ""`, `discountTotal: Double = 0.0`, `depositTotal: Double =
  0.0`, `confidence: Double = 1.0`, `parseStatus: String = "none"`, `pendingUpload: Int = 0`, `cachedUntil: Long =
  0L`; neuer Index `pendingUpload`. `ReceiptItemEntity` neu: `unit: String = ""`, `discount: Double = 0.0`,
  `isDeposit: Int = 0`, `category: String = ""`, `offBarcode: String = ""`, `confidence: Double = 1.0`.
  `ReceiptLexiconEntity` (`receipt_lexicon`): `id` (PK), `storeKey`, `storeName`, `rawText`, `name`, `category`,
  `offBarcode` (alle `String = ""`), `hits: Int = 0`, `updatedAt: Long = 0L`; Index `storeKey`. `AppDatabase`:
  `version = 37`, Entity eintragen, `MIGRATION_36_37` als **`internal val`** und in `addMigrations`:
  je neue Spalte `ALTER TABLE <t> ADD COLUMN <name> <TEXT|REAL|INTEGER> NOT NULL DEFAULT <''|0.0|1.0|0|'none'>`;
  `CREATE INDEX IF NOT EXISTS index_receipts_pendingUpload ON receipts(pendingUpload)`; `CREATE TABLE IF NOT EXISTS
  receipt_lexicon (…)` + `index_receipt_lexicon_storeKey`; Daten: `UPDATE receipts SET purchaseDay =
  strftime('%Y-%m-%d', purchaseDate / 1000, 'unixepoch', '+2 hours') WHERE purchaseDate > 0`; `UPDATE receipts SET
  pendingUpload = 1 WHERE localImageUri != '' AND imagePath = ''`; Annahme 7: `DELETE FROM receipt_items WHERE
  receiptId IN (SELECT id FROM receipts WHERE deleted = 1)` und `DELETE FROM receipts WHERE deleted = 1`.
  `SyncCompletenessTest`: `ReceiptLexicon` in `intentionallyLocalOnly` mit Kommentar „reiner Abzug des Server-Lexikons".
  Test: Schema 36 anlegen, einfügen: Bon A (`purchaseDate` 1794268800000, `localImageUri` gesetzt, `imagePath` leer),
  Bon B (`deleted = 1`, `dirty = 1`) mit Position, Bon C mit Position → `MIGRATION_36_37` → `assertMatchesSchema(conn,
  37)`; A: `purchaseDay == "2026-11-10"`, `pendingUpload == 1`, `parseStatus == "none"`; B und seine Position fehlen;
  C-Position hat `unit == ""`, `confidence == 1.0`. Akzeptanz: `V` grün; `app/schemas/**/37.json` existiert und wird
  mitcommittet.
- [x] **S17 Kategorien in der App** — Dateien: neu `$B/data/model/ProductCategory.kt`, `$R/values/strings.xml`, neu
  `$U/receipts/CategoryLabels.kt`, neu `$T/ProductCategoryTest.kt`. `enum class ProductCategory(val key: String, val
  isAisle: Boolean)` mit den 17 Einträgen in Katalogreihenfolge (`OBST_GEMUESE("obst_gemuese", true)` … `SONSTIGES(…,
  false)`, `PFAND(…, false)`); `companion`: `fromKey(key: String?): ProductCategory?` (getrimmt, kleingeschrieben),
  `aisleCategories: List<ProductCategory>`, `fallbackFor(category): ProductCategory?` nach der Ausweichkette.
  `CategoryLabels.kt`: `@StringRes fun ProductCategory?.labelRes(): Int` (`null` → `R.string.category_none`). Strings
  nach `$D/kategorien.md` „String-Ressourcen". Test: Schlüsselliste exakt und in Reihenfolge (im Test ausgeschrieben,
  identisch zu `test_categories.py`), `fromKey(" Milch_Eier ")`, `fromKey("x") == null`, 15 Gang-Kategorien, jede Zeile
  der Ausweichkette. Akzeptanz: `V` grün.
- [x] **S18 DTOs und API** — Dateien: `$B/data/remote/dto/SyncDto.kt` (oder neu `$B/data/remote/dto/ReceiptDto.kt`
  für die neuen Klassen), `$B/data/remote/SyncApi.kt`, `$B/di/NetworkModule.kt`. Neue DTOs (`@JsonClass`, alle Felder
  mit Standardwerten, `@Json(name = "snake_case")`): `ReceiptRemoteItemDto`, `ReceiptRemoteDto` (Felder `ReceiptOut`),
  `ReceiptUpsertDto` (`ReceiptIn`), `ReceiptListResponse`, `ReceiptPutResponse`, `ReceiptScanResponse`,
  `ReceiptImageResponse`, `LexiconEntryDto`, `LexiconListResponse`, `OkResponse`, `OffSearchRequest(query, limit =
  5)`, `OffSearchResponse(products: List<OffProductDto>)`. `SyncApi`: `listReceipts(@Query("from_day") String?,
  @Query("to_day") String?, @Query("since_seq") Long, @Query("include_deleted") Int)`, `getReceipt(@Path)`,
  `putReceipt(@Path, @Body)`, `deleteReceipt(@Path, @Query("updated_at") Long)`, `@Multipart scanReceipt(@Part file,
  @Part("receipt_id") RequestBody, @Part("shopping_list_id") RequestBody, @Part("captured_at") RequestBody,
  @Part("preliminary") RequestBody?)`, `@Multipart uploadReceiptImage(@Path, @Part file)`, `reparseReceipt(@Path)`,
  `receiptLexicon(@Query("store_key") String?)`, `deleteLexiconEntry(@Path)`, `searchOff(@Body OffSearchRequest)`
  (`POST api/off/search`). `parseReceipt` bleibt vorerst. `NetworkModule`: `readTimeout`/`writeTimeout` 120 s
  (Annahme 12). Alle Test-Attrappen von `SyncApi` in `$T` um die neuen Methoden ergänzen (`TODO()`-Rümpfe mit
  `error("nicht genutzt")`). Akzeptanz: `V` grün.
- [x] **S19 Abbildung und Datumshilfen** — Dateien: neu `$B/data/receipts/ReceiptMapping.kt`, neu
  `$B/data/receipts/ReceiptDates.kt`, neu `$T/ReceiptMappingTest.kt`, neu `$T/ReceiptDatesTest.kt`.
  `ReceiptRemoteDto.toEntity(local: ReceiptEntity?): ReceiptEntity` (übernimmt alle Server-Felder, `dirty = 0`,
  behält `localImageUri`, `pendingUpload`, `cachedUntil` von `local`; `source` vom Server, leer → `local?.source ?:
  "ai"`), `ReceiptRemoteItemDto.toEntity()`, `ReceiptEntity.toUpsertDto(items)`, `ReceiptItemEntity.toRemoteDto()`.
  `ReceiptDates` (reines Kotlin): `dayToEpochMs(day: String): Long` (Mitternacht UTC, ungültig → 0), `epochMsToDay(ms:
  Long, zone: ZoneId): String`, `isValidDay`, `isValidTime`, `displayDate(day: String, epochMs: Long, zone): String`
  (`TT.MM.JJJJ` aus `day`, sonst aus `epochMs`), `cutoffDay(today: LocalDate, months: Int): String?` (`months <= 0` →
  `null`, sonst `today.minusMonths(months)` als `YYYY-MM-DD`). Tests: Hin- und Rückabbildung aller Felder,
  Erhalt der lokalen Felder, `dayToEpochMs("2026-11-10") == 1794268800000L`, ungültige Eingaben, `cutoffDay` für 0/3.
  Akzeptanz: `V` grün.
- [x] **S20 DAO für Cache und Warteschlange** — Dateien: `$B/data/local/dao/ReceiptDao.kt`, neu
  `$B/data/local/dao/ReceiptLexiconDao.kt`, `$B/data/local/AppDatabase.kt`, `$B/di/DatabaseModule.kt`. `ReceiptDao`
  neu: `pendingUploads()` (`pendingUpload = 1 AND deleted = 0`), `dirtyReceiptsForPush()` (`dirty = 1 AND
  pendingUpload = 0`), `allItemsForReceipt(receiptId)` (inkl. gelöschter), `@Query("DELETE FROM receipts WHERE id =
  :id") hardDeleteReceipt`, `@Query("DELETE FROM receipt_items WHERE receiptId = :receiptId AND id NOT IN (:keepIds)")
  hardDeleteItemsExcept`, `hardDeleteItemsOf(receiptId)`, `evictableIds(cutoffMs: Long, nowMs: Long)` (`purchaseDate <
  :cutoffMs AND dirty = 0 AND pendingUpload = 0 AND cachedUntil < :nowMs`), `allLocalImageUris()`,
  `observeProvisionalCount(): Flow<Int>` (`status = 'provisional' AND deleted = 0`), `oldestPurchaseDay(): String?`,
  `clearPendingUpload(id, imagePath, updatedAt)`. Über den harten Löschungen steht der Kommentar „Cache-Verdrängung
  serverbestätigter Zeilen, siehe sync-patterns ‚Server-Archiv'". `ReceiptLexiconDao`: `observeAll()` (sortiert
  `storeName`, `rawText`), `replaceAll(entries)` (`@Transaction`: alles löschen, einfügen), `deleteById(id)`.
  Akzeptanz: `V` grün (Room prüft die Queries beim Build).

### Block E — App: Bon-Abgleich, Cache, Fotos (Befunde 2, 13, 14, 16, 17)
- [x] **S21 Foto-Ablage** — Dateien: neu `$B/data/receipts/ReceiptImageStore.kt`, neu `$T/ReceiptImageStoreTest.kt`.
  `@Singleton class ReceiptImageStore(@ApplicationContext context)`: `suspend fun prepare(uri: Uri, receiptId:
  String): PreparedImage?` (`ReceiptImagePreprocessor.loadUprightBitmap(context, uri, maxDim = 2000)`, als JPEG
  Qualität 85 nach `filesDir/receipts/<receiptId>.jpg`; gibt `PreparedImage(file, bitmap)` zurück), `fun
  delete(path: String)`, `fun deleteCameraTemp(uri: Uri?)` (löscht nur Dateien unter `cacheDir/receipts/`), `fun
  cleanupOrphans(referenced: Set<String>)`. Reine Top-Level-Funktion `orphanFiles(existing: List<String>, referenced:
  Set<String>): List<String>`. Test nur für `orphanFiles` (referenziert bleibt, fremd wird gelistet, leere Mengen).
  Akzeptanz: `V` grün.
- [x] **S22 `ReceiptSyncer`** — Dateien: neu `$B/data/receipts/ReceiptSyncer.kt`,
  `$B/data/preferences/AppPreferences.kt`, neu `$T/ReceiptSyncerTest.kt`. `AppPreferences`: `receiptSyncSeq: Long`
  (Key `receipt_sync_seq`, Standard 0) und `receiptSyncFromDay: String` (Key `receipt_sync_from_day`, Standard `""`)
  mit `saveReceiptSyncState(seq, fromDay)`. `@Singleton class ReceiptSyncer(receiptDao, apiProvider, preferences,
  imageStore)`:
  - `suspend fun pushDirty(): Int` — je Zeile aus `dirtyReceiptsForPush()`: `deleted == 1` → `deleteReceipt(id,
    updatedAt)`, danach `hardDeleteItemsOf` + `hardDeleteReceipt`; sonst `putReceipt(id, toUpsertDto(
    itemsForReceipt))` und die Antwort wie beim Pull in Room schreiben.
  - `suspend fun pull(today: LocalDate = LocalDate.now()): Int` — `months = receiptRetentionMonths`, `fromDay =
    cutoffDay(today, months)`; ist der gespeicherte `receiptSyncFromDay` nicht leer und (`fromDay == null` oder
    `fromDay < gespeichert`), dann `since = 0`, sonst `since = receiptSyncSeq`; `listReceipts(fromDay, null, since,
    1)`; je Bon: `deleted == 1` → lokal hart löschen, außer lokal `dirty == 1 && updatedAt >= server.updatedAt`;
    sonst überspringen, wenn lokal `dirty == 1 && updatedAt >= server.updatedAt` oder lokal `pendingUpload == 1`,
    andernfalls `upsertReceipt(toEntity(local))`, `upsertItems`, `hardDeleteItemsExcept(id, itemIds)`. Danach
    `saveReceiptSyncState(response.serverSeq, fromDay ?: "")` (leerer Text bei „nie").
  - `suspend fun evict(today: LocalDate = LocalDate.now(), nowMs: Long = System.currentTimeMillis()): Int` — bei
    `months <= 0` nichts; sonst `evictableIds(dayToEpochMs(cutoffDay), nowMs)` hart löschen (Positionen zuerst).
  - `suspend fun cleanupImages()` — `imageStore.cleanupOrphans(allLocalImageUris().toSet())`.
  - `suspend fun loadRange(fromDay: String, toDay: String, keepUntilMs: Long): Int` — `listReceipts(fromDay, toDay,
    0, 0)`, wie Pull schreiben, aber `cachedUntil = keepUntilMs`.
  Test mit `mockk`-DAO und Attrappen-API: Push PUT/DELETE inkl. hartem Löschen; Pull legt an, ersetzt, entfernt
  fehlende Positionen, respektiert lokales `dirty` und `pendingUpload`, löscht serverseitig gelöschte; Fenster
  vergrößert → `since = 0`; `evict` ruft für `months = 0` nichts auf; Cursor wird gespeichert. Akzeptanz: `V` grün.
- [x] **S23 Umschalten: Bons raus aus dem Voll-Sync** — Dateien: `$B/data/sync/SyncEngine.kt`,
  `$B/data/sync/SyncWorker.kt`, `$B/data/sync/ImageUploadWorker.kt`, `$B/data/sync/ImageDownloadWorker.kt`,
  `$B/data/repository/ReceiptRepository.kt`, `$B/data/local/dao/SyncDao.kt`, `$B/data/local/dao/ReceiptDao.kt`,
  `$T/SyncEngineTest.kt`, `$T/SyncCompletenessTest.kt`. `SyncEngine`: alle Bon-Zeilen entfernen (Z.139–140, 153,
  178–179, 208–209, 239–240, 266–267, Mapper Z.535–561, `receiptDao` im Konstruktor); `SyncPullResponse`/
  `SyncPushRequest` behalten die Felder `receipts`/`receiptItems` (werden nicht mehr befüllt/gelesen).
  `SyncDao.receiptTimestamps`/`receiptItemTimestamps`, `ReceiptDao.dirtyReceipts/dirtyItems/clearReceiptDirty/
  clearItemDirty/receiptsNeedingImageDownload/setImagePathAndClearLocal/receiptsWithLocalImage` löschen, sofern
  danach unbenutzt (`grep`). `SyncWorker`: injiziert `ReceiptSyncer` statt `ReceiptRepository`; `purgeOldReceipts()`
  wird zu `receiptSyncer.evict()` + `cleanupImages()` (weiter als erster Schritt, in `try/catch`); nach
  erfolgreichem `runFullSync()` in einem eigenen `try/catch` (Fehler nur loggen, Ergebnis des Workers bleibt)
  `pushDirty()`, dann `pull()`. `ImageUploadWorker`/`ImageDownloadWorker`: Bon-Teil und `receiptDao` entfernen.
  `ReceiptRepository`: `ApiProvider` statt `SyncApiFactory`; `purgeReceiptsOlderThan` löschen. `SyncCompletenessTest`:
  `Receipt`, `ReceiptItem` in `intentionallyLocalOnly` („eigenes Bon-Protokoll, siehe ReceiptSyncer").
  `SyncEngineTest`: Konstruktor anpassen. Akzeptanz: `V` grün; `grep -n -i 'receipt' $B/data/sync/SyncEngine.kt
  $B/data/sync/ImageUploadWorker.kt $B/data/sync/ImageDownloadWorker.kt` leer; `grep -rn 'SyncApiFactory'
  $B/data/repository` leer. Prüfliste (Gerät, Server mit Block A–C): Bon auf Gerät 1 ändern → nach Sync auf Gerät 2
  geändert; Bon löschen → auf Gerät 2 weg; Rezept-/Listen-Sync unverändert.
- [x] **S24 Scan-Ablauf im Repository** — Dateien: `$B/data/repository/ReceiptRepository.kt`, neu
  `$B/data/receipts/ReceiptScanOutcome.kt`, neu `$T/ReceiptScanFlowTest.kt`. `sealed interface ReceiptScanOutcome {
  data class Parsed(val receiptId: String); data class Provisional(val receiptId: String, val queuedOnServer:
  Boolean) }`. Neu `suspend fun scanFromUri(uri: Uri, shoppingListId: String): ReceiptScanOutcome`: (1) `receiptId` =
  neue UUID, `imageStore.prepare(uri, receiptId)`, `null` → `IllegalStateException`; (2) `scanReceipt`-API mit
  Datei-Part (`image/jpeg`), `captured_at = now`, ohne `preliminary`; (3) Antwort `parsed == true` → Bon + Positionen
  in Room (`dirty = 0`), Datei löschen, `Parsed`; (4) Antwort `parsed == false` → `receiptScanner.scanReceiptImage(
  bitmap)`, Bon lokal mit dessen Werten, `id = receiptId`, `status = "provisional"`, `source = "on_device"`,
  `parseStatus = "queued"`, `imagePath` aus der Antwort, `dirty = 1`, `updatedAt = now`, Datei löschen,
  `Provisional(queuedOnServer = true)`; (5) `IOException`, `HttpException` oder fehlende Server-Konfiguration
  (`IllegalArgumentException` aus `api()`) → ML Kit wie in 4, aber `localImageUri = Datei`, `pendingUpload = 1`,
  `dirty = 0`, `parseStatus = "none"`, `Provisional(queuedOnServer = false)`. In allen Fällen
  `imageStore.deleteCameraTemp(uri)`. Die Entscheidungslogik liegt in einer Klasse ohne Android-Typen
  (`ReceiptScanFlow(api-Aufruf, ML-Kit-Aufruf, Speichern, Löschen)` als Funktionsparameter), damit der Test ohne
  `Bitmap` auskommt. Alt: `scanReceipt(bitmap)`, `scanAndSaveReceipt`, `parseReceiptWithAi`, `encodeJpegBase64`,
  `downscale` und die Konstanten `MAX_AI_IMAGE_DIM`/`AI_JPEG_QUALITY` löschen; `CONFIDENCE_THRESHOLD = 0.7` zieht nach
  `$B/data/receipts/ReceiptReviewCheck.kt` (S27). `SyncApi.parseReceipt` und die DTOs `ReceiptParse*` löschen.
  Test: die drei Zweige (Erfolg; KI-Fehler serverseitig; `IOException`) mit erwarteten Feldern und Datei-Löschung;
  `HttpException(404)` zählt wie offline. Akzeptanz: `V` grün (die UI wird in S28 umgestellt — bis dahin hält
  `ReceiptScanViewModel.scanImage` einen kompilierenden Übergang: es ruft `scanFromUri` und behandelt beide Ergebnisse
  vorläufig wie `Saved`).
- [x] **S25 Upload-Warteschlange** — Dateien: neu `$B/data/receipts/ReceiptUploader.kt`, neu
  `$B/data/sync/ReceiptUploadWorker.kt`, `$B/data/sync/SyncScheduler.kt`, `$B/data/sync/SyncWorker.kt`, neu
  `$T/ReceiptUploaderTest.kt`. `ReceiptUploader.uploadPending(): UploadResult(done: Int, retry: Boolean)` — je Zeile
  aus `pendingUploads()`: Datei fehlt → `pendingUpload = 0`, `localImageUri = ""`, bei `status == "provisional"`
  zusätzlich `dirty = 1`; `status == "provisional"` → `scanReceipt` mit `preliminary` (JSON des `ReceiptUpsertDto`
  per Moshi): `parsed == true` → Server-Bon ersetzt lokal (Positionen ersetzen, `pendingUpload = 0`, `localImageUri =
  ""`), `parsed == false` → lokal bleibt, `imagePath` setzen, `parseStatus = "queued"`, `pendingUpload = 0`; sonst
  (Bestand mit Foto) → `uploadReceiptImage`, `imagePath` setzen, `pendingUpload = 0`, `dirty` unverändert. Nach
  Erfolg Datei löschen. `IOException`/`HttpException` ≥ 500 → Zeile bleibt, `retry = true`; `HttpException` 4xx →
  loggen, Zeile bleibt, kein Retry-Zwang. `ReceiptUploadWorker` (`@HiltWorker`): `Result.retry()` bei `retry`, sonst
  `success`; danach `syncScheduler.triggerOneShot()` nur wenn `done > 0`. `SyncScheduler.enqueueReceiptUpload()`:
  `OneTimeWorkRequest` mit `NetworkType.CONNECTED`, Backoff exponentiell 30 s, `enqueueUniqueWork(
  "helga.receipt.upload", ExistingWorkPolicy.KEEP, …)`; `cancelAll()` storniert auch diese Arbeit. `SyncWorker` ruft
  vor `pushDirty()` `receiptUploader.uploadPending()` auf (so holen periodischer Sync und Connectivity-Wechsel die
  Warteschlange nach). `ReceiptRepository.scanFromUri` ruft im Offline-Zweig `enqueueReceiptUpload()`. Test: alle
  vier Zweige plus Retry bei `IOException`. Akzeptanz: `V` grün. Prüfliste (Gerät): Flugmodus → Bon scannen →
  „vorläufig" erscheint sofort; Flugmodus aus → innerhalb weniger Minuten ersetzt die KI-Auswertung die Werte, Status
  „Bitte prüfen"; unter `Android/data/…/files/receipts/` liegt danach keine Datei mehr.
- [x] **S26 Repository-Schnitt für die UI** — Dateien: `$B/data/repository/ReceiptRepository.kt`, neu
  `$B/data/repository/ReceiptLexiconRepository.kt`, neu `$T/ReceiptRepositoryConfirmTest.kt`. `ReceiptRepository`
  neu/geändert: `confirmReceipt(receipt, items)` (schreibt Kopf und **vollständige** Positionsliste, fehlende lokale
  Positionen `deleted = 1`, alles `status = "confirmed"`, `dirty = 1`, `updatedAt = now`; `purchaseDate` aus
  `purchaseDay`, `depositTotal` aus den Pfand-Positionen); `deleteReceipt` löscht bei `pendingUpload == 1` oder nie
  gesyncten vorläufigen Bons (`imagePath == ""`, `status == "provisional"`) hart samt Foto, sonst wie bisher weich;
  `reparse(receiptId)` (API, Antwort in Room); `loadOlderMonth(): Boolean` (Monat vor `oldestPurchaseDay()`, bzw. vor
  dem aktuellen Monat; `loadRange(…, keepUntilMs = now + 24 h)`; `false` bei Fehler); `observeProvisionalCount()`;
  `suggestOffProducts(query): List<OffProductDto>` (Timeout 8 s, Fehler → leere Liste, wie
  `ShoppingRepository.suggestItems`). `reconcile` bleibt, setzt weiter `status = "reconciled"`.
  `ReceiptLexiconRepository`: `observe()`, `refresh(): Boolean` (API → `replaceAll`), `delete(id): Boolean` (API,
  dann lokal). Test: `confirmReceipt` setzt Felder/Flags und markiert entfernte Positionen; `deleteReceipt` hart vs.
  weich. Akzeptanz: `V` grün. Danach Block D–E pushen.

### Block F — App: Scan, Review, Liste, Detail (Befunde 14, 22, 23)
- [x] **S27 Prüf-Logik** — Dateien: neu `$B/data/receipts/ReceiptReviewCheck.kt`, neu `$T/ReceiptReviewCheckTest.kt`.
  `object ReceiptReviewCheck { const val CONFIDENCE_THRESHOLD = 0.7; fun lowConfidenceIds(items): Set<String>`
  (`confidence < Schwelle` oder `totalPrice == 0.0` bei nicht-Pfand); `fun itemsSum(items): Double` (Σ `totalPrice −
  discount`); `fun expectedTotal(items, discountTotal) = itemsSum − discountTotal`; `fun sumMismatch(total, items,
  discountTotal): Boolean` (`total > 0 && |expected − total| > max(total * 0.05, 0.10)`); `fun needsReview(receipt,
  items)` }`. Test: je Regel ein Treffer und ein Nichttreffer, Pfand mit negativem Betrag, Rabatte. Akzeptanz: `V` grün.
- [x] **S28 Scan-ViewModel** — Dateien: `$U/receipts/ReceiptScanViewModel.kt`, `$B/HelgaNavGraph.kt`. Route:
  `ROUTE_RECEIPT_SCAN = "receipts/scan?listId={listId}&reviewId={reviewId}"` (beide optional, Standard `null`).
  `ReceiptScanUiState.Preview` wird erweitert um `receiptId`, `storeAddress`, `purchaseDay`, `purchaseTime`,
  `discountTotal` (die Felder `rawOcrText`, `lowConfidenceItemIds`, `needsReview`, `source` bleiben); neuer Zustand
  `data class Provisional(val receiptId: String, val queuedOnServer: Boolean)`. `scanImage(uri)`:
  `repository.scanFromUri(uri, shoppingListId)` → `Parsed` → `loadForReview(id)`; `Provisional` → Zustand
  `Provisional`. `loadForReview(receiptId)` (auch aus `reviewId` in `init` über `SavedStateHandle`): Bon + Positionen
  aus dem Repository, Zustand `Preview` mit `ReceiptReviewCheck`. Neu: `updateHeader(storeName, storeAddress,
  purchaseDay, purchaseTime, total, discountTotal)` (ungültiges Datum/Uhrzeit → Wert unverändert lassen),
  `suggestProducts(item)`/`clearSuggestions()` mit `StateFlow<List<OffProductDto>>`, `applyProduct(itemId, product)`
  (setzt `offBarcode`, und `name`, falls der Nutzer ihn nicht selbst geändert hat). `save()` →
  `repository.confirmReceipt(...)`, `syncScheduler.triggerOneShot()`, `Saved`. `persistImage`, `sourceImageUri` und der
  `@ApplicationContext` entfallen. Akzeptanz: `V` grün; `ArchitectureRulesTest` grün;
  `grep -n 'ApplicationContext\|persistImage' $U/receipts/ReceiptScanViewModel.kt` leer.
- [ ] **S29 Scan-/Review-Screen** — Dateien: `$U/receipts/ReceiptScanScreen.kt`, neu
  `$U/receipts/ReceiptReviewComponents.kt`, `$R/values/strings.xml`. `ReceiptScanScreen` sammelt State und reicht an
  `internal fun ReceiptScanContent(state, stores, suggestions, <Callbacks>, modifier)` weiter. Neu im Review: Kopfkarte
  mit Markt, Adresse, Datum, Uhrzeit, Summe, Bon-Rabatt und Stift-Button → `HeaderEditDialog` (Textfelder; Datum
  `TT.MM.JJJJ`, Uhrzeit `HH:MM`, Fehlertext unter dem Feld bei ungültiger Eingabe, „Speichern" nur bei gültigen
  Werten); Summenzeile „Positionen %1$s · Bon %2$s" mit Warnfarbe `error` bei `sumMismatch`. Positionszeile:
  Name, darunter `bodySmall` Bon-Text (nur wenn abweichend), Menge + Einheit, Rabatt als „− %1$s", Pfand als
  `AssistChip`, Kategorie als `AssistChip` mit Namen. `EditItemDialog` zusätzlich: Einheit (`ExposedDropdownMenuBox`:
  Stück, kg, g, l, ml), Rabatt, Schalter „Pfand" (setzt Kategorie `pfand`, sperrt die Kategorieauswahl), Kategorie
  (`ExposedDropdownMenuBox` über `ProductCategory.entries` ohne `PFAND`, plus „Ohne Kategorie"), Button „Produkt
  zuordnen" → `ModalBottomSheet` mit Ladeindikator (`LoadingIndicator`), Trefferliste (Name, Marke) und Leertext
  „Keine Treffer – Name anpassen und erneut suchen". Zustand `Provisional`: Karte mit Icon `CloudOff` bzw.
  `HourglassTop`, Text „Vorläufig gespeichert. Die genaue Auswertung folgt, sobald der Server erreichbar ist." bzw.
  „… Die Auswertung läuft auf dem Server nach.", Button „Bon ansehen" → `onOpenReceipt(receiptId)`. Alle
  `remember { mutableStateOf` für `Boolean`/`String` in dieser Datei → `rememberSaveable`; die zwei `.clickable`
  (Z.401/413) bekommen `role = Role.Button`. Zwei `@ThemePreviews` (Review mit Beispieldaten, Vorläufig).
  `HelgaNavGraph`: `onOpenReceipt` navigiert zu `ROUTE_RECEIPT_DETAIL` und entfernt den Scan vom Backstack.
  Akzeptanz: `V` grün; `G-S $U/receipts/ReceiptScanScreen.kt` und `…/ReceiptReviewComponents.kt` regelkonform;
  `grep -c 'remember { mutableStateOf(false)' $U/receipts/ReceiptScanScreen.kt` = 0. Prüfliste (Gerät): Bon mit
  Rabatt und Pfand scannen → Uhrzeit, Adresse, Rabatt je Position, Pfand-Chip und Kategorien stehen im Review;
  Kategorie ändern, speichern, denselben Artikel im selben Markt erneut scannen → Korrektur ist übernommen; „Produkt
  zuordnen" zeigt Treffer; Drehen im geöffneten Dialog behält Eingaben; TalkBack liest Chips und Buttons.
- [ ] **S30 Bon-Liste** — Dateien: `$U/receipts/ReceiptListViewModel.kt`, `$U/receipts/ReceiptListScreen.kt`,
  `$R/values/strings.xml`. ViewModel: `receipts`, `loadingOlder: StateFlow<Boolean>`, `olderMessage:
  SharedFlow<UiText>`, `fun loadOlder()` (`repository.loadOlderMonth()`; `false` → Meldung „Ältere Bons konnten nicht
  geladen werden. Server erreichbar?"). Screen (`Screen`/`Content`-Teilung): Zeilen gruppiert nach Monat (Sticky
  Header „MMMM yyyy" aus `purchaseDay`, sonst `purchaseDate`); je Zeile Status-Chip: `provisional` → „Vorläufig"
  (`tertiaryContainer`), `review` → „Bitte prüfen" (`primaryContainer`), `parseStatus == "failed"` → „Auswertung
  fehlgeschlagen" (`errorContainer`); Datum über `ReceiptDates.displayDate`. Leerzustand mit Icon, Text „Noch keine
  Bons. Fotografiere deinen ersten Kassenbon." und Button „Bon scannen". Am Listenende `TextButton` „Ältere Bons
  laden" (deaktiviert mit Ladeindikator während `loadingOlder`), darunter `bodySmall` „Auf dem Handy liegen nur die
  Bons der letzten Monate. Ältere werden für einen Tag nachgeladen." `SnackbarHost` für `olderMessage`. `items(…, key
  = { it.id })`. Preview hell/dunkel. Akzeptanz: `V` grün; `G-S` regelkonform. Prüfliste (Gerät): Chips sichtbar;
  „Ältere Bons laden" holt den Vormonat, offline erscheint die Meldung; leere Liste zeigt den Leerzustand.
- [ ] **S31 Bon-Detail** — Dateien: `$U/receipts/ReceiptDetailViewModel.kt`, `$U/receipts/ReceiptDetailScreen.kt`,
  `$B/HelgaNavGraph.kt`, `$R/values/strings.xml`. ViewModel: `reparseState` (`Idle/Running/Error(UiText)`), `fun
  reparse()`; `delete` unverändert. Screen: Banner je Status (`provisional`: „Vorläufige Werte vom Handy. Die genaue
  Auswertung folgt automatisch."; `review`: „Bitte prüfen" mit Button „Jetzt prüfen" → `onReview(receiptId)` =
  Navigation zum Scan mit `reviewId`); Kopf zeigt Adresse, Datum + Uhrzeit, Bon-Rabatt, Pfand-Summe; Positionen mit
  Einheit, Rabatt, Pfand- und Kategorie-Chip (gemeinsame Zeile aus `ReceiptReviewComponents.kt`). Foto: lokale Datei,
  wenn `localImageUri` gesetzt, sonst `ImageUrls.serverImageUrl(serverUrl, imagePath)`; ohne beides kein Platzhalter.
  Overflow-Menü: „Bearbeiten" (→ `onReview`, ausgeblendet bei `provisional`), „Neu auslesen" (nur wenn `imagePath`
  gesetzt; Bestätigungsdialog „Die bisherigen Werte werden durch die neue Auswertung ersetzt."), „Löschen". Abgleich-
  Abschnitt nur bei Status `confirmed`/`scanned`/`reconciled`. `.clickable` Z.293 mit `role`; `showDeleteDialog` →
  `rememberSaveable`. Akzeptanz: `V` grün; `G-S` regelkonform. Prüfliste (Gerät): vorläufiger Bon zeigt das lokale
  Foto und lässt sich nicht bearbeiten; „Neu auslesen" ersetzt die Werte; Foto eines alten Bons lädt vom Server.
- [ ] **S32 Einstellungen: Aufbewahrung und Lexikon** — Dateien: `$U/settings/SettingsScreen.kt`,
  `$U/settings/SettingsViewModel.kt`, neu `$U/receipts/ReceiptLexiconScreen.kt`, neu
  `$U/receipts/ReceiptLexiconViewModel.kt`, `$B/HelgaNavGraph.kt`, `$R/values/strings.xml`. Strings ändern (Namen
  bleiben): `settings_receipt_retention` → „Bons offline behalten", `settings_receipt_retention_never` → „Alle
  (unbegrenzt)"; neuer Hinweistext darunter „Ältere Bons bleiben auf dem Server und werden bei Bedarf geladen."
  Neue Zeile „Gelerntes Bon-Lexikon" → Route `ROUTE_RECEIPT_LEXICON = "receipts/lexicon"`. Lexikon-Screen
  (`Scaffold` mit Zurück): beim Öffnen `refresh()`; Liste gruppiert nach `storeName` (leer → „Ohne Markt"), Zeile
  „<rawText> → <name>", darunter Kategorie-Name und „%1$d× verwendet", Papierkorb-Button mit
  `contentDescription`; Leertext „Noch nichts gelernt. Das Lexikon füllt sich, wenn du Bons im Review korrigierst.";
  Fehler beim Aktualisieren/Löschen als Snackbar („Server nicht erreichbar"); `PullToRefreshBox` ruft `refresh()`.
  Akzeptanz: `V` grün; `G-S` regelkonform; `ArchitectureRulesTest` grün. Prüfliste (Gerät): Eintrag erscheint nach
  einer Review-Korrektur, Löschen entfernt ihn und die Korrektur greift beim nächsten Scan nicht mehr.

### Block G — Umschaltung der Alt-Clients, Absicherung, Doku
- [ ] **S33 Server: Bons raus aus dem Voll-Sync** — Dateien: `$SA/db.py`, `$SA/sync.py`, neu
  `$ST/test_sync_legacy_receipts.py`, neu `$ST/test_sync_basic.py`. `SYNC_TABLES` ohne `receipts`/`receipt_items`;
  `_ensure_server_seq` iteriert über `SYNC_TABLES + RECEIPT_TABLES`; `sync.py` nach Vertrag 7 (`LEGACY_PUSH_TABLES`,
  `pull_since` liefert die beiden Tabellen nicht mehr; `SyncPullResponse(**result)` bekommt für sie leere Listen).
  Test: (1) Alt-Push eines neuen Bons mit Position → über `GET /api/receipts` sichtbar, `purchase_day` gefüllt,
  `source == "legacy"`; (2) Alt-Push mit `deleted = 1` auf einen vorhandenen Bon → Bon bleibt `deleted = 0`;
  (3) Alt-Push überschreibt neue Spalten nicht (`category` einer Position bleibt); (4) `GET /api/sync?since=0`
  enthält `receipts == []`, obwohl Bons existieren; (5) `test_sync_basic`: Push/Pull eines Rezepts und LWW
  (älterer Push verliert) funktionieren weiter. Akzeptanz: `VS` grün. Hinweis für den Nutzer unter „Abschluss":
  Reihenfolge beim Ausrollen = Server-Version mit dieser Phase deployen, dann die neue App auf **beiden** Handys
  installieren.
- [ ] **S34 Regel-Test für das Bon-Protokoll** — Datei: neu `$T/ReceiptProtocolRulesTest.kt` (Aufbau wie
  `ArchitectureRulesTest`). Zwei Tests: (1) `SyncEngine.kt` enthält weder `ReceiptEntity` noch `receiptDao`;
  (2) außerhalb von `data/receipts/` und `data/local/dao/` ruft keine Datei `hardDeleteReceipt`/`hardDeleteItems`
  auf, mit Ausnahme von `ReceiptRepository.kt`. Akzeptanz: `V` grün.
- [ ] **S35 Guidelines** — Dateien: `.claude/guidelines/sync-patterns.md`, `.claude/guidelines/architecture-layers.md`,
  `CLAUDE.md` (Wurzel). `sync-patterns.md`: neuer Abschnitt „Server-Archiv mit lokalem Cache (Bons)" (höchstens 30
  Zeilen): Bons nicht in `SyncEngine`; Schreiben weiter mit `dirty`/`updatedAt`; Push/Pull über `ReceiptSyncer`;
  hartes Löschen nur für serverbestätigte Zeilen (`dirty = 0`, `pendingUpload = 0`) und nur in `data/receipts/`;
  Verweis auf `ReceiptProtocolRulesTest` und `$D/server-vertrag.md`; die Checkliste „Neue syncbare Entity" bekommt die
  Zeile „Room-Migration mit Test über `RoomMigrationHarness`". `architecture-layers.md`: `data/receipts/` als
  Dienst-Paket nennen. `CLAUDE.md`: im Abschnitt „Sync-Protokoll" ein Absatz „Ausnahme Bons" (3 Zeilen) und in
  „Konventionen" bei Soft-Delete der Zusatz „(Ausnahme: Cache-Verdrängung der Bons)". Akzeptanz:
  `grep -c 'Server-Archiv' .claude/guidelines/sync-patterns.md` ≥ 1.
- [ ] **S36 Pläne und Index** — Dateien: `.claude/features/bons-kosten/plan.md`, `.claude/features/sync/plan.md`,
  `.claude/features/naehrwerte/plan.md`, `.claude/features/README.md`, `.claude/phasen/README.md`.
  `bons-kosten/plan.md`: Abschnitt „Phase 3a" (umgesetzte Punkte in 8–10 Zeilen), Frage 8 mit Vermerk „überholt durch
  Phase 3, Entscheidung 4", Entscheidungszeilen für Server-Archiv, Haiku 5.5, Lexikon; Umfang-Tabelle um
  `data/receipts/` und die Server-Module. `sync/plan.md`: Vermerk „Bons seit Phase 3a außerhalb des Voll-Syncs".
  `naehrwerte/plan.md`: Vermerk „OFF-Textsuche wird im Bon-Review genutzt". `features/README.md`: Stand-Zeile
  (DB v37, Entity-Zahl), Zeile unter „Bereichsübergreifende Befunde". `phasen/README.md`: Status der Phase-3-Zeile auf
  „3a umgesetzt, 3b offen". Akzeptanz: Dateien geändert, `V` und `VS` grün. Danach pushen.

## Nicht-Ziele
- Diagramme, Monatsaggregate, Kostenübersicht, Top-Artikel, Preisentwicklung, Gang-Kategorien, Stichwortliste,
  `categorize-items` → Phase 3b.
- Zahlart und MwSt-Klasse (Entscheidung 3); mehrere Währungen (bleibt `EUR`).
- Automatischer Wechsel auf ein größeres Modell; Structured Outputs/Tool-Use für das Auslesen.
- Automatische OFF-Zuordnung aller Positionen beim Scan; Nährwerte aus Bons.
- Push-Benachrichtigung nach der Auswertung; Bearbeiten vorläufiger Bons.
- Verbesserung der ML-Kit-Erkennung (`ReceiptScanner`), Kamera-/Zuschneide-UI.
- Aufräumen des restlichen Servers (`main.py`-Routen, `off.py` Befund 12, `/api/suggestions/aisles`) → Phase 4;
  Dockerfile → Phase 5.
- Physisches Löschen von Bons oder Fotos auf dem Server; Papierkorb-/Wiederherstellen-UI.
- `:wear`-Modul und `ui/widget/`.

## Abschluss
- [ ] Baseline aus S0 notiert (Server-Tests, App-Tests)
- [ ] `VS` grün (Anzahl Tests notieren), `V` grün (Anzahl Tests notieren)
- [ ] `app/schemas/**/37.json` committet, `Migration36To37Test` grün
- [ ] Bereichs-`plan.md`, Feature-Index, Guidelines aktualisiert (S35, S36)
- [ ] Gemeldete Abweichungen und Ausnahmen (je Schritt eine Zeile)
- [ ] Gesammelte „Prüfliste (Gerät)" aus S12, S23, S25, S29–S32 und der Ausroll-Hinweis aus S33 für den Nutzer
      (von Stufe B nicht abgehakt)

## Abschluss

### Baseline S0 (2026-11-11)
- Server: `pytest` 11 passed · App: `V` 154 Tests, grün · `version = 36` in `AppDatabase.kt`: 1 Treffer.

### Notizen Umsetzung 3a
- S8: `GET /api/receipts?to_day=…` lässt Bons ohne `purchase_day` durch (Vertrag: `purchase_day <= to_day` wörtlich, `'' <= '…'` ist wahr). Das gilt wie bei `from_day` bewusst; der Test hält es fest.

### Prüflisten (Nutzer, nicht abgehakt)
- **S12 Testset (mit echtem Schlüssel):** 10–20 Bon-Fotos außerhalb des Repos ablegen (optional je Foto `<name>.expected.json`),
  `cd server && make testset DIR=/pfad/zu/fotos` laufen lassen, Bericht ansehen. Bei schwacher Quote `AI_RECEIPT_EFFORT=high` testen.
  Voraussetzung: `AI_PROVIDER=anthropic`, `AI_API_KEY` gesetzt.
- **S13 Wiederherstellung (vor der Umschaltung, auf dem Server):** `make backup`, dann
  `python scripts/restore_purged_receipts.py --db data/recipes.db` (nur Liste), Ergebnis prüfen, erst dann `--apply`.
- S20: `ReceiptDao.clearPendingUpload(id, imagePath)` hat bewusst **keinen** `updatedAt`-Parameter (Plan nannte ihn): ein lokal neuerer Zeitstempel ohne `dirty` würde spätere Server-Änderungen im LWW-Vergleich unterdrücken. Aufrufer in S22/S25 entsprechend.
- S23: Zusätzlich zur Liste entfernt: `ReceiptDao.findIdsOlderThan` (nur von `purgeReceiptsOlderThan` genutzt). Die Prüfliste (zwei Geräte, Server mit Block A–C) steht unter „Prüflisten (Nutzer)".
- **S23 Umschaltung (Gerät, Server mit Block A–C, beide Handys):** Bon auf Gerät 1 ändern → nach Sync auf Gerät 2 geändert; Bon löschen → auf Gerät 2 weg; Rezept- und Listen-Sync unverändert.
- S24: `CONFIDENCE_THRESHOLD` wurde aus `ReceiptRepository` entfernt; die neue Konstante entsteht in S27 (`ReceiptReviewCheck`). `ReceiptScanViewModel.scanImage` ist bis S28 ein Übergang (ruft `scanFromUri`, danach `Saved`).
- **S25 Offline-Scan (Gerät, Server mit Block A–C):** Flugmodus → Bon scannen → „vorläufig" erscheint sofort; Flugmodus aus → innerhalb weniger Minuten ersetzt die KI-Auswertung die Werte, Status „Bitte prüfen"; unter `Android/data/…/files/receipts/` liegt danach keine Datei mehr.
- S28: `ReceiptImageStore.deleteCameraTemp` (S21) kannte nur Dateipfade; die Kamera liefert aber eine FileProvider-`content://`-URI. Nachgezogen: die URI wird über Autorität und `receipt_captures`-Pfadsegment auf `cacheDir/receipts/<datei>` abgebildet. Der Screen hat bis S29 einen schlichten Übergangszweig für `Provisional`.
