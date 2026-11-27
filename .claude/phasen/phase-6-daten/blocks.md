# Phase 6 — Review-Blöcke, Kriterien, akzeptierte Abweichungen (Datendatei)

Verbindlich für Phase 6, Block B. Stufe B übernimmt die Angaben hier **wörtlich** in die Prompt-Vorlagen aus
[prompts.md](prompts.md). Nichts ergänzen, nichts weglassen.

Pfade: `$B` = `app/src/main/kotlin/com/helga/android` · `$T` = `app/src/test/kotlin/com/helga/android` ·
`$SA` = `server/app` · `$ST` = `server/tests` · `$P` = `.claude/phasen`

**Review-Bereich (für alle Blöcke gleich):** Fixpunkt `2c148ab` (= `e21e0d0~1`, vor dem Phasenplan) bis `$H`.
`$H` ist der Commit am Ende von Block A der Phase 6 (S9); Stufe B notiert die SHA in S10 und setzt sie überall ein.
Diff-Befehl je Block: `git diff 2c148ab $H -- <Pfade des Blocks>` · Commits: `git log --oneline 2c148ab..$H -- <Pfade>`.

## 1. Tiefe

| Modus | Blöcke | Bedeutung |
|-------|--------|-----------|
| **voll** | R1 B3 · R2 B2 · R3 B1 · R4 B4 · R5 B6 · R6 B5 | Sub-Agenten lesen jeden Hunk des Blocks; Wortgrenze 400 je Achse |
| **Checkliste** | R7 B7 · R8 B8 · R9 B9 · R10 B10 | Sub-Agenten arbeiten nur die Kriterienliste und die grep-Prüfungen des Blocks ab und lesen nur die Treffer bzw. die genannten Dateien; Wortgrenze 250 je Achse |
| **Abgleich** | R11 B11 | nur die Spec-Achse: stimmen Doku und Guidelines mit dem Code-Stand überein (keine Standards-Achse) |

`R1`–`R11` = Reihenfolge der Läufe in Block B (nach Risiko). Die Block-Kennung (`B1`–`B11`) ist die aus dem
Stufe-A-Bericht und bleibt in Bericht und Commit-Nachrichten gleich.

## 2. Schweregrade und Markierungen (für Sub-Agenten und Bericht)

| Grad | Definition |
|------|------------|
| **Blocker** | Datenverlust, Sicherheitslücke mit Zugriff von außen, App-Absturz im Normalablauf, kaputter Build |
| **Hoch** | falsches Verhalten im Normalablauf, Verstoß gegen eine Spec-Anforderung mit Nutzerwirkung, harter Verstoß gegen eine Sync-/Architekturregel |
| **Mittel** | Randfall-Fehler, harter Verstoß gegen eine Guideline ohne direkte Nutzerwirkung, fehlender verlangter Test |
| **Niedrig** | Stil, Doku, Kleinigkeit |

Markierungen (zusätzlich, beliebig kombinierbar): **[ändert Verhalten]** = die Behebung würde beobachtbares Verhalten
(App, API, Daten) ändern; **[Ermessensfrage]** = keine dokumentierte Regel, nur Urteil (jeder Code-Smell der Basis ist
immer Ermessensfrage).

## 3. Nicht melden (für alle Blöcke)

- Generierte Dateien: `app/schemas/**`. Nur deren Existenz zählt.
- Die Spec selbst: `$P/phase-*.md`, `$P/phase-3-daten/*`, `$P/phase-6-daten/*`.
- Was Regel-Tests schon erzwingen: `ArchitectureRulesTest`, `UiConventionsTest`, `ReceiptProtocolRulesTest`,
  `SyncCompletenessTest`, `KotlinQualityRulesTest` (neu in Phase 6).
- Altbestand: Zeilen, die nicht im Diff liegen. Fällt ein Sub-Agent darauf, gehört es unter „Beifang" ohne Schweregrad.
- `ui/widget/`, `:wear`.
- **Akzeptierte Abweichungen** (vom Nutzer in den Phasen bestätigt, nie erneut melden):
  - Phase 1: `WeekplanViewModel.kt` mit 555 statt < 500 Zeilen; S1 `@Provides` statt `@Binds`; S6 Bulk-KI zählt
    fehlende Server-Konfiguration pro Rezept; S7 1,5-s-Timeout auch für Wochenplan-Vorschläge; S9 `findRecipe`
    dupliziert `findById`; S18 `UiState` ohne `Idle` (`null` = Idle); S19 `SseClient`-Ausnahme; S19
    `ReceiptCosts`-Verschiebung; `sealed class UiState` (CLAUDE.md, Plan 1 S2).
  - Phase 2: alle Punkte unter „Gemeldete Ausnahmen" im Abschluss (S5, S10/S22-Zählweise, S11, S15 `"%.2f €"` in
    `ShoppingListScreen`, S18 `cost_bon_one/other` statt Plurals, S23 Barcode-Dummy und `ReceiptScanViewModel`-Text,
    `ProteinBar("🥩 Fleisch")`, `" ★"`).
  - Phase 3a: S8 `to_day` lässt Bons ohne `purchase_day` durch; S20 `clearPendingUpload` ohne `updatedAt`; S23
    `findIdsOlderThan` entfernt; S28 `deleteCameraTemp` über FileProvider-URI; Migration 36→37 mit festem `'+2 hours'`.
  - Phase 3b: S14 `internal`-Primärkonstruktor + `@Inject`-Sekundärkonstruktor in `CostOverviewViewModel`; S16 zwei
    Liniendiagramme auf dem Artikel-Screen, `price_single_purchases_title`; S17 `addAisle` lässt den Namen wie
    eingegeben; S22; S23 Rückgabe zählt lokale Treffer, Lernen mit `AisleProductKey.normalize`.
  - Phase 4: S6 Testerwartung `q="oma"`; S8 zusätzlich `TypeError`/`AttributeError`; S9 `_fetch_html` und eigene
    Meldungstexte; S12 zusätzliche Dateien in `server/CLAUDE.md`.
  - Phase 5: S4 Hilfezeile; S5 Compose-Prüfung in Kopie; Container endet ohne `API_KEY` nicht (Plan-Korrektur e8fb60d).
  - Vorgaben eines Plans, die einer Guideline widersprechen, gelten **nicht** als Spec-Verstoß, sondern als
    Standards-Konflikt (Niedrig, [Ermessensfrage]); die zwei bekannten Konflikte (ViewModels nutzen `data/receipts`,
    `sealed class UiState`) werden in S24 in den Guidelines aufgelöst und nicht gemeldet.
- **Vorbefunde aus Stufe A** (stehen schon im Bericht, siehe `review-bericht.md` Abschnitt „Vorbefunde"): nicht
  erneut melden, nur ergänzen, wenn der Sub-Agent eine **weitere** Fundstelle derselben Art findet.

## 4. Kriterien-Bausteine (werden in den Blöcken referenziert)

**K-KQ (kotlin-quality.md):** §1 kein `!!` · §2 `sealed interface`/`data object` für neue Status-Typen · §3
`MutableStateFlow`/`MutableSharedFlow` privat · §4 beobachtbare Daten als `Flow`, `suspend` nur für Einmal-Operationen ·
§5 Schreiben über mehrere Tabellen atomar (`@Transaction`/`withTransaction`) · §6 kein `GlobalScope`, `viewModelScope`
bzw. injizierter Scope; `CancellationException` nie verschlucken · §7 kein `lateinit` außer Hilt-Feldern · §9 Felder
von Sync-/Netz-DTOs mit Standardwert · §10 Routen/Keys als `const val`.

**K-SP (sync-patterns.md):** Pflichtfelder `id/updatedAt/deleted/dirty` und Indizes `updatedAt`, `deleted` an syncbaren
Entities · jede Mutation setzt `dirty = 1` und `updatedAt = now` · nach Mutation im ViewModel
`syncScheduler.triggerOneShot()` · LWW nur in `SyncEngine`/`ReceiptSyncer` · Abschnitt „Server-Archiv": Server-Antwort
zuerst in Room (`dirty = 0`), hartes Löschen nur für `dirty = 0`/`pendingUpload = 0` bzw. nie hochgeladene Bons,
`pendingUpload`-Bons beim Pull nicht überschreiben · Checkliste „Neue syncbare Entity" (Android, Server inkl.
`PRESERVE_IF_UNSET`, Migrationstest über `RoomMigrationHarness`) · keine `fallbackToDestructiveMigration`.

**K-AL (architecture-layers.md):** erlaubte Abhängigkeiten UI → Repository/UseCase → DAO/API · Datenschicht importiert
nie aus `ui/` · Repositories/Dienste injizieren `ApiProvider`, nicht `SyncApiFactory` · Repositories `@Singleton`,
ViewModels `@HiltViewModel` mit Konstruktor-Injektion · ViewModels lesen aus Room, nie aus Retrofit-Antworten.

**K-CP (compose-performance.md):** §1 stabile UI-State-Typen · §2 `key` in Lazy-Listen · §3 `remember` für teure
Berechnungen (Sortieren, Formatieren, Datum) · §6 Item-Composables als benannte Funktionen · §7 kein ViewModel
unterhalb des Screen-Composables.

**K-UX (ux-accessibility.md):** §1 48 dp · §2 `contentDescription` (null nur dekorativ) · §3 Fehler mit Text · §4/§13
`LoadingIndicator` · §5 Leerzustand mit Text und Aktion · §6 destruktive Aktion mit Bestätigung/Undo · §7/§13 Farben nur
über Rollen, Diagrammfarben über `chartSeriesColors()` · §9 Formular-Sheets `skipPartiallyExpanded` · §10 `imeAction`
· §13 Rollen an klickbaren Zeilen, Dialog-/Sheet-Sichtbarkeit und Eingaben mit `rememberSaveable`, `UiText`,
`formatEuro`, Diagramm mit `description` und Werten als Text.

**K-SV (server/CLAUDE.md, Konventionen):** jeder Router mit `Depends(require_auth)` · nur `ALTER TABLE … ADD COLUMN` ·
Soft-Delete, nie physisch löschen · Start nur mit `API_KEY` oder `ALLOW_NO_AUTH=1` · KI-Ausfall → 502 mit
`KI-Dienst hat nicht geantwortet`, Meldungen ohne Schlüssel/Antwortkörper · SSE: Fehler vor erstem Chunk 502, danach
Log ohne `[DONE]` · Eingabe-Limits in `models.py` · SSRF-Regeln in `url_guard.py` · Upload-Limits · Pfad-Traversal
(`Path(x).name`) · neue Schreibpfade nur unter `/app/data` · Tests rufen nie echte KI/OFF auf · kein blockierendes
Datei-I/O im Event-Loop (Muster `asyncio.to_thread` aus `images_api.py`).

**K-SM (Code-Smell-Basis des Skills, immer [Ermessensfrage]):** wörtlich aus
`.claude/skills/code-review/SKILL.md`, Abschnitt 3, die zwölf Punkte von „Mysterious Name" bis „Refused Bequest".

## 5. Blöcke

### B3 — Server-Bon-Archiv (R1, voll)
- **Pfade:** `server/app/receipts.py server/app/receipt_api.py server/app/receipt_text.py server/app/categories.py
  server/app/db.py server/app/sync.py server/scripts/ server/tests/test_receipt_aggregates.py
  server/tests/test_receipt_lexicon.py server/tests/test_receipt_queue.py server/tests/test_receipt_scan.py
  server/tests/test_receipt_testset.py server/tests/test_receipt_text.py server/tests/test_receipts_crud.py
  server/tests/test_schema_migration.py server/tests/test_sync_aisle_category.py server/tests/test_sync_basic.py
  server/tests/test_sync_legacy_receipts.py server/tests/test_categories.py server/tests/test_categorize_items.py
  server/tests/test_restore_purged.py server/tests/test_parse_receipt.py server/tests/test_reconcile_receipt.py`
- **Standards:** K-SV, K-SM.
- **Spec:** `$P/phase-3a-bons-server-scan.md` (Block A–C, S33), `$P/phase-3b-auswertung-gang-kategorien.md` (S1–S3),
  `$P/phase-3-daten/server-vertrag.md` (alle Abschnitte, wörtlich), `$P/phase-3-daten/kategorien.md`.
- **Pflichtfragen Spec:** PUT/DELETE/Scan/Reparse/Warteschlange genau nach Vertrag §3.1 (Reihenfolge, Statuswerte,
  `server_seq` auf Bon **und** Positionen); Lernen nur bei `confirmed`/`reconciled` und nur bei Abweichung (§4);
  Aggregate §6 (Filter, Rundung, Sortierung, Pfand); Alt-Clients §7 (`deleted = 1` ignoriert, nur alte Spalten);
  `PRESERVE_IF_UNSET`; Schema nur additiv; Restore-Skript nur mit `--apply` schreibend.

### B2 — Bon-Protokoll App (R2, voll)
- **Pfade:** `$B/data/receipts/ $B/data/repository/ReceiptRepository.kt $B/data/repository/ReceiptLexiconRepository.kt
  $B/data/sync/SyncWorker.kt $B/data/sync/ReceiptUploadWorker.kt $B/data/sync/SyncScheduler.kt
  $B/data/sync/ImageUploadWorker.kt $B/data/sync/ImageDownloadWorker.kt $B/data/remote/dto/ReceiptDto.kt
  $B/data/remote/dto/SyncDto.kt $B/data/remote/SyncApi.kt $B/di/NetworkModule.kt $B/data/preferences/AppPreferences.kt
  $T/ReceiptSyncerTest.kt $T/ReceiptUploaderTest.kt $T/ReceiptScanFlowTest.kt $T/ReceiptMappingTest.kt
  $T/ReceiptDatesTest.kt $T/ReceiptImageStoreTest.kt $T/ReceiptRepositoryConfirmTest.kt $T/ReceiptProtocolRulesTest.kt
  $T/ReceiptRepositoryOffSuggestionsTest.kt` (ohne `ReceiptStats.kt` und `ReceiptReviewCheck.kt`, die gehören zu B7/B8)
- **Standards:** K-KQ, K-SP, K-AL, K-SM.
- **Spec:** `$P/phase-3a-bons-server-scan.md` S18–S26, S34, Annahmen 1, 7, 8, 9, 12, 13; `server-vertrag.md` §2, §3.
- **Pflichtfragen Spec:** Pull-Cursor und Fenster-Vergrößerung (S22); `localWins`/`pendingUpload` beim Pull; Upload-
  Zweige und Retry-Regeln (S25); Scan-Zweige inkl. `HttpException` = offline (S24); Datei-Löschung nach Upload
  (Annahme 1); `deleteReceipt` hart vs. weich (S26); Reihenfolge Upload → Push → Pull → Aggregate im `SyncWorker`.

### B1 — Room-Migrationen, Schema, DAOs (R3, voll)
- **Pfade:** `$B/data/local/AppDatabase.kt $B/data/local/entity/ $B/data/local/dao/ReceiptDao.kt
  $B/data/local/dao/ReceiptLexiconDao.kt $B/data/local/dao/ReceiptAggregateDao.kt $B/data/local/dao/SyncDao.kt
  $B/data/local/dao/ShoppingDao.kt $B/data/sync/SyncEngine.kt $B/di/DatabaseModule.kt $T/Migration36To37Test.kt
  $T/Migration37To38Test.kt $T/RoomMigrationHarness.kt $T/RoomMigrationHarnessTest.kt $T/SyncCompletenessTest.kt
  $T/SyncEngineTest.kt $T/StoreAisleSyncTest.kt`
- **Standards:** K-SP, K-KQ (§5, §9), K-SM.
- **Spec:** `$P/phase-3a-bons-server-scan.md` S15, S16, S20, S23; `$P/phase-3b-auswertung-gang-kategorien.md` S5, S6;
  `$P/phase-3-daten/kategorien.md` (Zuordnung der alten Standardgänge).
- **Pflichtfragen Spec:** Spalten, Typen, Defaults und Indizes von Migration 36→37 und 37→38 gegen Entities und
  `37.json`/`38.json` (nur Existenz der JSON-Dateien und Gleichheit der Spaltennamen/Typen per Test); Daten-Updates
  der Migrationen wörtlich wie im Plan; `SyncEngine` ohne Bon-Zeilen; `StoreAisleDto.categoryKey` in beide Richtungen.

### B4 — Server-Sicherheit und -Struktur (R4, voll)
- **Pfade:** `server/app/settings.py server/app/auth.py server/app/main.py server/app/ai.py server/app/ai_client.py
  server/app/ai_api.py server/app/images_api.py server/app/off_api.py server/app/suggestions_api.py
  server/app/sync_api.py server/app/url_guard.py server/app/models.py server/tests/conftest.py
  server/tests/test_ai_errors.py server/tests/test_ai_receipt_config.py server/tests/test_health_auth.py
  server/tests/test_images.py server/tests/test_request_limits.py server/tests/test_routes_guard.py
  server/tests/test_startup_auth.py server/tests/test_suggestions.py server/tests/test_url_guard.py`
- **Standards:** K-SV, K-SM.
- **Spec:** `$P/phase-4-fastapi-templates.md` (S1–S11, Entscheidungen, Nicht-Ziele);
  `$P/phase-3a-bons-server-scan.md` S5–S7; `server-vertrag.md` §5.
- **Pflichtfragen Spec:** jede Route außer Health mit Auth (inkl. neuer Router); keine Pfad-/Feld-/Formatänderung an
  bestehenden Endpunkten; Fehlerabbildung 502 ohne Geheimnisse; SSRF: jeder Hop geprüft, alle aufgelösten Adressen,
  5 MiB; Limits genau wie S10; `settings` zur Aufrufzeit gelesen; Bon-Nutzlast ohne Sampling-Parameter.

### B6 — Auto-Zuordnung zum Gang (R5, voll)
- **Pfade:** `$B/data/util/AisleKeywords.kt $B/data/util/AisleKeywordMatcher.kt $B/data/shopping/
  $B/data/sync/AisleCategorizeWorker.kt $B/data/repository/ShoppingRepository.kt $B/data/repository/StoreRepository.kt
  $B/data/model/ProductCategory.kt $B/ui/stores/ $B/ui/shopping/ShoppingListViewModel.kt $T/AisleAssignerTest.kt
  $T/AisleKeywordMatcherTest.kt $T/AisleKeywordsTest.kt $T/ShoppingAisleAssignmentTest.kt
  $T/StoreRepositoryAisleTest.kt $T/ProductCategoryTest.kt`
- **Standards:** K-KQ, K-SP, K-AL, K-UX (nur `ui/stores/`), K-SM.
- **Spec:** `$P/phase-3b-auswertung-gang-kategorien.md` S17–S23, Annahmen 5–8; `$P/phase-3-daten/aisle-keywords.md`
  (Algorithmus Schritte 1–6 wörtlich); `aisle-testfaelle.md` Liste C; `kategorien.md` (Ausweichkette).
- **Pflichtfragen Spec:** Reihenfolge Lexikon → Stichwort → Server-KI; Ausweichkette genau eine Stufe; „sonstiges"
  und „pfand" nie als Gang; KI nur mit aktivem Markt und nur für Artikel ohne Gang; Hinzufügen wartet nie auf das Netz;
  `RAW` zeichengenau (Prüfzahlen-Test vorhanden → nur Abweichungen im Algorithmus melden).

### B5 — Container (R6, voll)
- **Pfade:** `server/Dockerfile server/.dockerignore server/docker-compose.prod.yml server/Makefile
  server/requirements.txt server/requirements-dev.txt server/.env.example doku/casaos-deployment.md`
- **Standards:** K-SV (Schreibpfade, `API_KEY`), Skill `multi-stage-dockerfile` nur soweit in Phase 5 zitiert, K-SM.
- **Spec:** `$P/phase-5-multi-stage-dockerfile.md` (Dockerfile in S3 **wörtlich**, `.dockerignore` in S1 wörtlich, S5).
- **Pflichtfragen Spec:** Dockerfile und `.dockerignore` zeichengleich mit dem Plan; Prod-Compose ohne Healthcheck,
  mit `read_only`, `tmpfs /tmp`, `cap_drop ALL`, `no-new-privileges`; schreibt der Server-Code irgendwo außerhalb von
  `/app/data` und `/tmp`? (`grep -rn "open(\|write_bytes\|mkdir\|NamedTemporaryFile\|tempfile" server/app server/scripts`).

### B7 — Auswertung und Diagramme (R7, Checkliste)
- **Pfade:** `$B/data/receipts/ReceiptStats.kt $B/data/repository/ReceiptStatsRepository.kt
  $B/data/model/ReceiptStatsModels.kt $B/ui/receipts/charts/ $B/ui/theme/ChartColors.kt
  $B/ui/receipts/CostOverviewScreen.kt $B/ui/receipts/CostOverviewViewModel.kt $B/ui/receipts/ProductPrice*
  $T/ReceiptStatsTest.kt $T/ChartMathTest.kt $T/CostOverviewViewModelTest.kt`
- **Checkliste Standards:** (1) `grep -rn 'Color(0x' $B/ui | grep -v theme/` leer; (2) jedes Diagramm-Composable hat
  einen `description`-Parameter und setzt `semantics { contentDescription = … }`; (3) Rechnungen (Skalierung, Winkel,
  Perioden) nur in `ChartMath`/`ReceiptStats`, nicht im Canvas-Block; (4) K-CP §3: `remember` um Sortier-/
  Formatierlogik in `CostOverviewScreen`; (5) K-KQ §3 in den zwei ViewModels; (6) `rememberSaveable` für
  `showBudgetDialog` und die Eingaben im `BudgetEditDialog` (`grep -n 'remember { mutableStateOf'`).
- **Checkliste Spec:** `$P/phase-3b-…md` S8–S16: Reihenfolge der Karten in S15 (8 Punkte); Zeiträume nach Annahme 4;
  Wochen-Budget = Budget × 12 / 52; vorläufige Bons fehlen + Hinweis (Annahme 1); Fußnote Bon-Rabatte (Annahme 2);
  Diagramme lesen nur aus `receipt_aggregates`; `grep -rn 'costByStore\|CostSummary' $B` leer.

### B8 — Bon-UI (R8, Checkliste)
- **Pfade:** `$B/ui/receipts/ReceiptScanScreen.kt $B/ui/receipts/ReceiptScanViewModel.kt
  $B/ui/receipts/ReceiptReviewComponents.kt $B/ui/receipts/ReceiptListScreen.kt $B/ui/receipts/ReceiptListViewModel.kt
  $B/ui/receipts/ReceiptDetailScreen.kt $B/ui/receipts/ReceiptDetailViewModel.kt $B/ui/receipts/ReceiptLexiconScreen.kt
  $B/ui/receipts/ReceiptLexiconViewModel.kt $B/ui/receipts/ReceiptGrouping.kt $B/ui/receipts/CategoryLabels.kt
  $B/data/receipts/ReceiptReviewCheck.kt $T/ReceiptGroupingTest.kt $T/ReceiptReviewCheckTest.kt`
- **Checkliste Standards:** (1) `grep -n 'remember { mutableStateOf' <Pfade>` → nur Kategorien aus Phase 2 Block G
  „Nicht umstellen"; (2) `grep -n '\.clickable' <Pfade>` → jede Stelle mit `role =` (mehrzeilig ansehen);
  (3) `items(` mit `key =`; (4) jede Mutation im ViewModel gefolgt von `syncScheduler.triggerOneShot()`
  (`confirm`, `delete`, `reparse`, Lexikon löschen); (5) K-KQ §3; (6) Löschen/Neu auslesen mit Bestätigungsdialog;
  (7) Leerzustände (Liste, Lexikon) mit Text und Aktion; (8) Fehler beim Nachladen/Lexikon als Snackbar mit Text.
- **Checkliste Spec:** `$P/phase-3a-…md` S27–S32 und Annahme 9 (vorläufige Bons schreibgeschützt: „Bearbeiten"
  ausgeblendet bei `provisional`); Status-Chips und Farben in S30; Texte in S29–S32 wörtlich (`grep` in
  `res/values/strings.xml`).

### B9 — Architektur aus Phase 1 (R9, Checkliste)
- **Pfade:** `$B/data/repository/AiRepository.kt $B/data/repository/RecipeRepository.kt
  $B/data/repository/WeekplanRepository.kt $B/data/weekplan/ $B/data/usecase/ $B/data/remote/ServerConnectionChecker.kt
  $B/data/remote/ApiErrorMapping.kt $B/data/remote/SyncApiFactory.kt ':(glob)$B/ui/**/*ViewModel.kt'
  ':!$B/ui/receipts/' $T/ArchitectureRulesTest.kt $T/AiRepository*Test.kt $T/ServerConnectionCheckerTest.kt $T/ShoppingSuggestionsTest.kt
  $T/RecipeRepositoryCookTest.kt $T/ExportWeekToShoppingListUseCaseTest.kt $T/WeekplanGenerationCharacterizationTest.kt
  $T/WeekplanHasRecipesTest.kt $T/WeekplanFiltersTest.kt`
- **Checkliste Standards:** (1) `grep -rn 'import com.helga.android.ui' $B/data` leer (KDoc-Verweise ausgenommen);
  (2) `grep -rn 'SyncApiFactory' $B/data/repository $B/data/receipts $B/data/shopping` leer; (3) jede neue
  Repository-Mutation setzt `dirty = 1` und `updatedAt` (`markCooked`, `saveFeedback`, `toggleFeedback`, Wochenplan-
  Schreibmethoden); (4) jede Mutation im ViewModel gefolgt von `triggerOneShot()`; (5) neue Repository-Methoden für
  beobachtbare Daten geben `Flow` zurück (K-KQ §4); (6) `WeekplanGenerator` schreibt mehrere Tabellen atomar (K-KQ §5).
- **Checkliste Spec:** `$P/phase-1-…md` S3–S20 und „Entscheidungen Stufe B": Verhalten unverändert verschoben
  (Charakterisierungstests vorhanden), `ExportWeekToShoppingListUseCase` überspringt gelöschte Zutaten, Fehlerabbildung
  `HttpException`/`IOException` wie in Block A beschrieben.

### B10 — UI-Querschnitt aus Phase 2 (R10, Checkliste)
- **Pfade:** `$B/ui/theme/ $B/ui/components/ $B/ui/common/ $B/HelgaNavGraph.kt $B/MainActivity.kt
  app/src/main/AndroidManifest.xml app/src/main/res/ $B/ui/onboarding/ $B/ui/recipes/ $B/ui/weekplan/ $B/ui/shopping/
  $B/ui/settings/ $B/ui/stats/ $B/ui/ai/ gradle/libs.versions.toml app/build.gradle.kts $T/UiConventionsTest.kt
  $T/CurrencyFormatTest.kt $T/UiTextTest.kt` (nur `*Screen.kt`, Components, Theme; ViewModels gehören zu B9)
- **Checkliste Standards:** (1) `grep -rn 'Color(0x' $B/ui | grep -v theme/` leer; (2) `grep -rn 'RoundedCornerShape(' $B/ui
  | grep -v theme/` → nur Stellen außerhalb von Composables; (3) `grep -rn 'bottomPadding' $B` leer; (4)
  `grep -rn 'imePadding' $B` nur `HelgaNavGraph.kt`; (5) `grep -rn '\.clickable' $B/ui | grep -v 'role =\|receipts/\|widget/'`
  → nur die zwei Ausnahmen aus Phase 2 S24; (6) `grep -rn 'CircularProgressIndicator(' $B/ui` nur in
  `LoadingIndicator.kt`; (7) `items(` ohne `key =` in `$B/ui`; (8) neue Strings nur in `res/values/strings.xml`
  (deutsch), Plurals wie S22.
- **Checkliste Spec:** `$P/phase-2-…md` S1–S32: Tabellen T1–T3 (Stichprobe: Grün hell/dunkel und Orange hell in
  `Color.kt`), `ACCENT_SYSTEM`-Logik (S2), Startziel ohne `!!` (S8), NavigationRail/840 dp (S11), Nicht-Ziele
  eingehalten (keine Umbauten an Groß-Screens, kein Rezept-Grid).

### B11 — Doku-Abgleich (R11, Abgleich; nur Spec-Achse)
- **Pfade:** `CLAUDE.md server/CLAUDE.md .claude/guidelines/ .claude/features/ $P/README.md doku/casaos-deployment.md`
- **Checkliste:** (1) `server/CLAUDE.md` Struktur-Abschnitt nennt jede Datei unter `server/app/*.py` (`ls server/app`);
  (2) KI-Endpunkt-Tabelle in `server/CLAUDE.md` gegen `EXPECTED_ROUTES` in `server/tests/test_routes_guard.py`;
  (3) `architecture-layers.md` nennt alle Pakete unter `$B/data/` (`ls $B/data`); (4) `features/README.md` Stand-Zeile
  (DB-Version = `version =` in `AppDatabase.kt`, Entity-Zahl = Länge der `entities = [...]`-Liste); (5) jede Phase 1–5
  in `$P/README.md` hat den Status der Phasen-Datei; (6) Widersprüche zwischen Guidelines und CLAUDE.md.
