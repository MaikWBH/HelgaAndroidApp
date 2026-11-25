# Phase 7 — Entwurf (aus der Planungssitzung, noch nicht freigegeben)

> Stand 2026-11-25. Dies ist der **Entwurf** aus dem Plan-Modus. Stufe A (Opus 5.5) macht daraus nach `_PHASE_TEMPLATE.md` die endgültige Phasendatei `phase-7-fixes-und-markdown-import.md` (Schritte S0…Sn mit Akzeptanz und Verifikation) und trägt Phase 7 in die Tabelle der `phasen/README.md` ein.

## Einstieg für die nächste Sitzung (z. B. vom Handy)
1. Diese Datei und `.claude/phasen/testplan.md` (Abschnitt 7, Befunde F1–F18) lesen. Bereichsplan für den Import: `.claude/features/rezepte/plan.md`.
2. Entschieden ist: **eine Phase 7**, Fixes zuerst (Block B), Markdown-Import zuletzt (Block C). Definition **Opus 5.5**, Umsetzung **Sonnet 5.5**. Nutzerliste aus Phase 6 per Fragerunde in Stufe A. Markdown-Import: Vorschau, dann Speichern; mehrere Rezepte je Text, getrennt durch `---`; erweitertes Format, nur der Titel ist Pflicht.
3. Nächster Schritt: Stufe A. Phasendatei schreiben, die Nutzerliste (42 Zeilen in `phase-6-daten/review-bericht.md`) in Bündeln à ≤ 4 Fragen per AskUserQuestion entscheiden lassen (inkl. F5, F13-Seite, F18-Regex, F6-Versionen), Freigabe holen, Plan committen. Danach Stufe B.
4. Umgebung: Planung und Dokumentation gehen von überall. Code, Tests und Gerätetests brauchen Android-SDK/Gradle bzw. den PC (Handy per `adb`, lokaler Testserver mit `server/.venv`). Pushen kann die Umgebung des PCs nicht (keine GitHub-Zugangsdaten): der Nutzer pusht.

---

# Phase 7 — Fixes aus dem Testlauf + Markdown-Rezept-Import

## Context
Der Testlauf vom 25.11.2026 (lokaler Server, Debug-Build, siehe `.claude/phasen/testplan.md`, Abschnitt 7) fand 18 Befunde (F1–F18), darunter einen Datenverlust (F13: vorläufiger Bon verliert nach dem Scan seine OCR-Werte). Dazu kommt ein neues Feature: Rezepte per Markdown einspielen. Ein Tipp auf „Markdown importieren“ im FAB des Rezepte-Tabs legt einen festen KI-Prompt in die Zwischenablage; der Nutzer gibt ihn mit Rezeptwunsch an Gemini, kopiert die Antwort in ein Textfeld der App, sieht eine Vorschau und speichert (ein oder mehrere Rezepte, getrennt durch `---`).

Rahmen (vom Nutzer entschieden): **eine Phase 7, Fixes zuerst, Feature zuletzt**. Definition (Stufe A) **Opus 5.5**, Umsetzung (Stufe B) **Sonnet 5.5**. Die Nutzerliste aus Phase 6 (42 Zeilen, `phase-6-daten/review-bericht.md`) wird in Stufe A per Fragerunde entschieden; freigegebene Einträge werden Schritte.

Vorgehen nach Freigabe dieses Plans (Konvention aus `.claude/phasen/README.md`, `_PHASE_TEMPLATE.md`, Phase 6 als Muster):
1. Stufe A schreibt `.claude/phasen/phase-7-fixes-und-markdown-import.md` (Gerüst: Modelle, Ziel, Entscheidungen, Befunde, Schritte S0…Sn mit Akzeptanz/Verifikation, Nicht-Ziele, Abschluss) und trägt Phase 7 in die Tabelle der `phasen/README.md` ein.
2. Fragerunde zur Nutzerliste (Bündel à ≤ 4 Fragen, per AskUserQuestion), Freigabe, Commit des Plans.
3. Stufe B arbeitet die Schritte ab: ein Commit pro Schritt (`Phase 7 S<n>: …`), pro Block Push (Zugangsdaten fehlen in dieser Umgebung → Nutzer pusht).

## Block A — Baseline
- **S0:** `export ANDROID_HOME=… ; ./gradlew :app:testDebugUnitTest :app:compileDebugKotlin --console=plain` (Stand 287) und `cd server && .venv/bin/python -m pytest -q` (Stand 180) notieren.

## Block B — Fix-Pakete (Reihenfolge nach Gewicht)
Pfade relativ zu `app/src/main/kotlin/com/helga/android/` (App) bzw. `server/app/` (Server). Zeilen = Stand 25.11.2026.

**B1 Datenverlust — F13 (+ B1-Anm).** Zwei Ursachen:
- *Ursache B (die im Test beobachtete):* `data/receipts/ReceiptScanFlow.kt:55-61` speichert die OCR-Werte lokal mit `dirty=1` und `updatedAt` vor dem Aufruf. Kann der Server nicht auswerten, legt er eine leere Hülle mit `updated_at = now_ms()` **nach** dem Aufruf an (`receipts.py:436-445`, Erstscan ohne `preliminary`). Der spätere PUT wird abgelehnt (`receipts.py:118-119`, Server neuer), `ReceiptSyncer.pushDirty` übernimmt den Server-Stand und `replaceWithServerState` löscht die lokalen Positionen. Belegt: Server- und Handy-`updated_at` des Test-Bons sind identisch (Differenz 0 ms), Markt/Summe/Datum der Hülle stehen lokal. Fix: lokal `updatedAt = maxOf(now, response.receipt.updatedAt + 1)`; serverseitig Hülle mit `captured_at` bzw. übergebenem `preliminary` anlegen (`receipts.py:444`) — Stufe A entscheidet die Seite (Empfehlung: beides, Server zuerst).
- *Ursache A (latent):* `data/local/dao/ReceiptDao.kt:31-32` `@Insert(REPLACE)` löscht die Bon-Zeile und per `ON DELETE CASCADE` alle Positionen (`ReceiptItemEntity.kt:12-18`). Betroffen: `data/receipts/ReceiptUploader.kt:66,89`; tote Aufrufer `ReceiptRepository.kt:181,303,314` (`updateReceipt`, `linkReceiptToShoppingList`, `updateReceiptStore`) werden entfernt. Fix: `upsertReceipt`/`upsertReceipts` auf `@Upsert` (Room 2.7.1, Vorbild `RecipeFeedbackDao.kt:12,15`). Nur `receipts` hat einen Fremdschlüssel mit CASCADE (Schema 38).
- Tests: Regel in `ReceiptProtocolRulesTest` (`upsertReceipt` nicht `@Insert(REPLACE)`, Muster `transaktionsmethoden_tragen_transaction`), `ReceiptScanFlowTest.kt:61` erweitern (Hülle neuer als lokal ⇒ OCR-Werte bleiben), Repository-Test „Upload mit `parsed=false` behält Positionen“; Server-Test für die Hülle.

**B2 Drehen — F1, F2, F10 (+ B7-S3, B6-S4).** `remember` → `rememberSaveable`: `ui/receipts/CostOverviewScreen.kt:485-486` (`text`, `warnThreshold`), `ui/weekplan/WeekplanScreen.kt:658` (`noteText`; mögliche Zusatzursache: `onDispose` der alten Komposition schreibt vor dem Neuaufbau, dann Überschreiben mit `""`), `ui/recipes/RecipeDetailScreen.kt:703` (`draft`). Gleiches Muster: `SettingsScreen.kt:936-939,983-984`, `ShoppingListScreen.kt:882-885`, `WeekplanScreen.kt:1253-1259`, Gang-`dragOffset` (B6-S4). Regel-Test in `UiConventionsTest`: kein `by remember(...) { mutableStateOf(` für Text-Variablen.

**B3 Bon-Liste/Eingabe — F11, F12 (+ B8-P1/P2).**
- F11: Fußzeile „Ältere Bons laden“ (`ui/receipts/ReceiptListScreen.kt:205-224`, liegt nur im gefüllten Zweig 180-226; Leerzweig 156-179) als eigene Composable auch im Leerzustand; `ReceiptRepository.loadOlderMonth` (`:257-286`) sucht bis zu 12 Monate zurück bis mindestens ein Bon kommt und liefert ein Ergebnis (Anzahl/Monat/Fehler), das `ReceiptListViewModel.kt:36-48` als Snackbar zeigt („N Bons aus <Monat> geladen“ / „Keine älteren Bons“). Tests: Repository- und ViewModel-Test (Muster `ReceiptRepositoryConfirmTest`).
- F12: `parseDecimal` (`ui/receipts/ReceiptReviewComponents.kt:85`) in einen gemeinsamen reinen Helfer (`data/receipts` oder `ui/common`) mit Unit-Test (Stil `ReceiptDatesTest`, inkl. „1.234,56“); Kopf-Dialog `:329-330` und Positionsdialog `:455-464` zeigen `isError` + `supportingText` (neuer String) und sperren „Speichern“ (Vorbild `CostOverviewScreen.kt:487-488,531`).

**B4 Sync-Anzeige — F3.** Neuer DataStore-Schlüssel `last_sync_at` (`data/preferences/AppPreferences.kt`, `saveLastSyncAt`), in `data/sync/SyncEngine.kt` nach Zeile 104 mit `System.currentTimeMillis()` setzen, in `SettingsViewModel.kt:56-57` und `SettingsScreen.kt:105,606` anzeigen (`formatTimestamp` mappt ≤ 0 auf „Noch nie“), in `clearConnection` (`AppPreferences.kt:197`) löschen. Cursor (`KEY_LAST_SYNC_TS`) und `KEY_RECEIPT_SYNC_SEQ` bleiben unberührt; keine Uhr als Konstruktor-Parameter (Hilt). Tests: `SyncEngineTest.kt:174` um `coVerify(exactly = 0){ saveLastSyncAt }` (Abbruch) und Erfolgsfall ergänzen.

**B5 Optik — F4, F8, F9, F14, F15, F17, F19 (neu).**
- F4: `WeekplanScreen.kt:574,581,588,596,947,956` bedienbare inaktive Zustände ohne `alpha(0.4)` (≥ 3:1); Zeile 611 (disabled) bleibt. Die Symbole sind Emoji in `Text`; Unterscheidung aktiv/inaktiv über `Modifier.alpha(1f/0.6f)` oder echte `Icon`s.
- F8: kürzeres Formularlabel (neuer String `recipe_form_prep_time`, bisher `recipe_detail_prep_time` nur in `RecipeFormScreen.kt:140`) oder zwei Spalten statt drei (`:136-156`).
- F9/F17: `RecipeDetailScreen.kt:351-354` `contentPadding = PaddingValues(bottom = 88.dp)`; `ReceiptListScreen.kt:186,207` ebenso und Fußzeilen-Padding entfernen; gemeinsame Konstante (Vorbild `StoreListScreen.kt:155`; Geschwister `RecipeListScreen.kt:253`). F17 vor dem Fix am Screenshot gegenprüfen.
- F14: `CostOverviewScreen.kt:237-258` `Row` → `FlowRow` (`@OptIn(ExperimentalLayoutApi::class)`, Vorbild `WeekplanScreen.kt:562`).
- F15: `ui/receipts/charts/SpendingBarChart.kt:94-106` Labels dürfen ihren Slot überlaufen (`wrapContentWidth(unbounded = true)`, `softWrap = false`) oder jedes 3. Label; ggf. `labelStep` in `ChartMath` mit Test.
- F19 (neu, Testlauf-Screenshot): Dauer wird roh angezeigt („PT30M“, `RecipeListScreen.kt:407-410`, `RecipeDetailScreen.kt:520-522`). Formatter für ISO **und** Freitext („30 Min“, „1 Std 30 Min“) in `data/util/`, mit Test; wird auch vom Markdown-Import genutzt.
- F5 (Lila als Tertiärfarbe bei Grün/Blau/Petrol, `ui/theme/Theme.kt:39-44`; Orange hell weiß auf `#E65100` = 3,8:1, `Color.kt:23`) ist eine Gestaltungsfrage → Fragerunde, kein Standardschritt.

**B6 Server/Build — F7, F18, F6.**
- F7a: `server/app/main.py:36` `FastAPI(..., docs_url=None, redoc_url=None, openapi_url=None)` (oder Umgebungsschalter, Standard aus); Test: 404 für `/docs`, `/redoc`, `/openapi.json`.
- F7b: `server/app/ai_client.py:160-167` `_require_ai_key()` in `openai_headers`/`anthropic_headers` → `AiUpstreamError("KI-Schlüssel fehlt")` (502 ohne Anbieter-Aufruf); `test_ai_receipt_config.py` (~Z. 75) braucht `monkeypatch.setattr(settings,"AI_API_KEY","x")`; neuer Test „leerer Schlüssel ⇒ 502, Fake-Anbieter nie aufgerufen“; optional Warnung im Lifespan.
- F18: ID-Regex vereinheitlichen: `receipts.py:19` auf `^[A-Za-z0-9_-]{1,64}$` und auf **alle** `/{receipt_id}`-Routen anwenden (`receipt_api.py:98-125`); Empfehlung gegenüber „strikt UUID“ (würde `test_receipts_crud.py`, `test_receipt_lexicon.py`, `test_receipt_aggregates.py`, `test_sync_legacy_receipts.py` umschreiben); `test_receipt_scan.py:98` bleibt gültig.
- F6: `gradle/libs.versions.toml`: ML Kit Text Recognition 16.0.0 → ≥ 16.0.1 und CameraX 1.3.4 → ≥ 1.4.x (Version aus Erinnerung, in Stufe A gegen Release-Notes prüfen). ELF-Prüfung des Debug-APK: nur `libmlkit_google_ocr_pipeline.so` und `libimage_processing_util_jni.so` sind 4-KB-ausgerichtet; DataStore/graphics-path sind es dort nicht → danach APK neu bauen und die Geräte-Meldung erneut prüfen. 32-Bit-ABI-Verzicht (`libbarhopper_v3` armeabi-v7a/x86) ist eine Produktentscheidung.

**Geteilte Ursachen** (Rule-Tests lohnen sich): `remember` statt `rememberSaveable` (F1/F2/F10); fehlende FAB-Freiräume (F9/F17); feste Breiten bei schmalen Layouts (F8/F14/F15: einmal bei 360 dp und 1,3× Schrift prüfen); stummes Fallback auf 0 bei ungültigen Zahlen (F12, `CostOverviewScreen.kt:487` parst gleich); Server-Härtung (F7/F18) getrennt vom App-Teil.

## Block C — Markdown-Import (neu)
Entschieden: Vorschau, dann Speichern · mehrere Rezepte je Text (`---`) · erweitertes Format, alles außer Titel optional.

**Ablauf**
1. `ui/components/CreateFab.kt` bekommt `onMarkdownImport`; Reihenfolge: Remix, KI erstellen, URL importieren, **Markdown importieren**, Neues Rezept. Beide Aufrufstellen anpassen: `ui/recipes/RecipeListScreen.kt:175-181` und `ui/recipes/RecipeDetailScreen.kt:332-338` (+ `HelgaNavGraph.kt:223-232` und `:356-364`).
2. Tipp auf die Option: Prompt in die Zwischenablage (`ClipboardManager`, Label „Helga Rezept-Vorlage“; bisher keine Clipboard-Nutzung in der App) und Navigation zu neuer Route `ROUTE_RECIPE_MD_IMPORT = "recipe/md-import"` (`HelgaNavGraph.kt`, neben `ROUTE_RECIPE_URL_IMPORT`, Zeile 86).
3. Neuer Screen `MarkdownImportScreen`/`MarkdownImportContent` (Muster: `ui/recipes/UrlImportScreen.kt`, Screen/Content-Trennung, `@ThemePreviews`): Hinweiskarte „Prompt liegt in der Zwischenablage“ mit „Erneut kopieren“, kurze 2-Schritt-Anleitung, mehrzeiliges Textfeld mit „Einfügen“-Knopf, „Auswerten“. Eingabetext `rememberSaveable` (Lehre aus F1/F2/F10).
4. Vorschau-Liste: je Rezept eine Karte (Name, Zutaten-/Schrittzahl, Zeiten, Tags, Warnungen; Häkchen zum Abwählen; Hinweis bei gleichem Namen). Knopf „N Rezepte speichern“. Fehlerhafte Blöcke erscheinen mit Grund und werden nicht gespeichert (gültige werden gespeichert, kein Alles-oder-nichts).
5. Speichern: je Rezept `RecipeRepository.saveRecipe(entity, ingredients, instructions, tags)` (`data/repository/RecipeRepository.kt:149-183`, transaktional über `RecipeDao.upsertRecipeBundle`, setzt `updatedAt`/`dirty`), danach einmal `syncScheduler.triggerOneShot()`. Ein Rezept → Detail öffnen (`recipeDetailRoute`, `popUpTo(ROUTE_RECIPES)`), mehrere → zurück zur Liste. Server-Klassifizierung (Muster `classifyRecipeAsync` in `UrlImportViewModel.kt:116-145`) nur für Rezepte ohne eigene Klassifizierung; Fehler still.

**Parser** `data/util/RecipeMarkdownParser.kt` (reines Kotlin-`object`, kein Room/Netz, wie `IngredientLineParser`): `parse(text) -> MarkdownParseResult(recipes, errors)`.
- Vorverarbeitung: BOM, `\r\n`, umschließende Code-Zäune (```` ```markdown ````) entfernen; Blöcke an Trennlinien `---`/`***` teilen.
- Je Block: `# Titel` (sonst erste Überschrift), Beschreibung = Fließtext/`>`-Zitat vor den Abschnitten, Schlüssel-Zeilen tolerant (`- Portionen: 4`, `**Portionen:** 4`, `Portionen: 4`): Portionen→`recipeYield` (Freitext, mind. eine Zahl; „4“ → „4 Portionen“, Servings werden per `\d+` gelesen), Vorbereitung/Kochzeit/Gesamtzeit→ISO-Dauer, Küche→`cuisine` (Vokabular `server/app/ai.py:30-37`, sonst „sonstige“), Mahlzeit→`mealSlot` (breakfast/lunch/dinner/snack/other; deutsche Wörter mappen, `MealSlots` in `ui/components/MealSlot.kt:8-16`), Aufwand (schnell/mittel/aufwendig), Eiweiß→`proteinType` (fleisch/fisch/vegetarisch/vegan), Saison (winter/sommer/ganzjährig), Tags (Komma/Semikolon), Nährwerte, Notizen→`personalNotes`.
- Abschnitte `##` (Synonyme: Zutaten; Zubereitung/Schritte/Anleitung): Zutatenzeilen (`-`, `*`, `+`, Checkbox `[ ]`, Fließzeilen) über **`IngredientLineParser.parse`** + `isHeaderLine` (`data/util/IngredientLineParser.kt`, Muster `AiGenerateViewModel.kt:157-168`); Schritte (`1.`, `1)`, `Schritt 1:`, Spiegelstriche, Folgezeilen anhängen). Zwischenüberschriften `###` werden verworfen (kein Gruppenfeld im Modell).
- Dauer-Parser (`15 Min`, `1 Std 30 Min`, `1,5 h`, `PT15M`) → ISO, gleiche Form wie `import_url`/`_mins_to_iso` (`server/app/ai.py`); derselbe Formatter aus F19 für die Anzeige.
- Grenzen: Text ≤ 100 000 Zeichen, ≤ 50 Rezepte, ≤ 100 Zutaten/Schritte je Rezept (Abbruch mit Meldung statt Hängen).
- Mapper Parsed → `RecipeEntity`/`IngredientEntity` (Position ab 0)/`InstructionEntity` (Position ab 1)/`TagEntity` in `data/util/` (UUID für `id` und `slug`, `createdAt = now`, wie `UrlImportViewModel.kt:60-110`; `mealSlot` nur aus gültigem Vokabular).
- **Nährwerte (geklärt):** Room hält Gesamtwerte für 4 Portionen (`NUTRITION_BASELINE_PORTIONS = 4`, `data/model/RecipeNutrition.kt`), die Anzeige teilt durch 4. Das Format verlangt „Nährwerte **pro Portion**“ (kcal, Eiweiß, Fett, Kohlenhydrate in g); der Mapper speichert Wert × 4 in `nutritionKcal/Protein/Fat/Carbs` und setzt `nutritionSource = "ai"`. Fehlt die Zeile oder ist sie unvollständig, bleiben alle Nährwertfelder leer.

**Prompt** `app/src/main/res/raw/recipe_md_prompt.txt` (deutsch, eine Quelle der Wahrheit): Rolle, exakte Formatvorgabe mit vollständigem Beispiel (zwei Rezepte), erlaubte Werte je Schlüssel, Regeln („nur Markdown, kein Codeblock, metrische Einheiten, ein Rezept pro Block, Blöcke mit `---` trennen“) und letzte Zeile „Mein Wunsch:“. Laden über kleinen Helfer in `ui/recipes/` (Context → Raw-Resource). Kein deutscher Literaltext in Kotlin (`UiConventionsTest`).

**Strings** (`strings.xml`, deutsch): `fab_markdown_import` = „Markdown importieren“ (neben `fab_url_import`, Zeile ~165), Screen-Titel, Anleitung, Knöpfe, Fehler-/Warntexte, „Prompt in Zwischenablage kopiert“.

**Tests** (neu, JVM)
- `RecipeMarkdownParserTest` (~25): Idealfall, zwei Rezepte, Code-Zaun, Aufzählungs-/Nummerierungsvarianten, Schlüssel-Synonyme, Dauerformate, Nährwerte × 4, fehlender Titel, leeres Rezept, Zwischenüberschriften, Umlaute, Obergrenzen.
- `RecipePromptSyncTest`: liest `app/src/main/res/raw/recipe_md_prompt.txt`, extrahiert das eingebettete Beispiel und parst es fehlerfrei (hält Prompt und Parser synchron).
- `MarkdownImportViewModelTest` (mockk/Turbine nach `CostOverviewViewModelTest`): Auswerten → Vorschau, Abwählen, Speichern ruft `saveRecipe` je Auswahl und `triggerOneShot` einmal, Fehlerpfad.
- Bestehende Regeltests bleiben grün (`ArchitectureRulesTest`, `UiConventionsTest`, `KotlinQualityRulesTest`: kein `!!`, kein DAO im ViewModel).

**Dokumentation:** Formatspezifikation `.claude/features/rezepte/markdown-format.md`; Backlog-Einträge in `.claude/features/rezepte/plan.md` und Index `.claude/features/README.md`.

## Block D — Nutzerliste (Stufe A)
42 Zeilen (nicht 44; in 5 Zeilen stehen je zwei IDs, 47 IDs) aus `review-bericht.md`. Fragerunde in Bündeln; freigegebene Einträge werden Schritte in Block B, Rest bleibt „offen“ dokumentiert. Zusammenfassen mit den F-Paketen: B1-Anm → B1, B7-S3/B6-S4 → B2, B8-P1/P2 → B3, B4-P1 → B6. Die Zählung „44“ in `phasen/README.md` und Phase 6 korrigieren.

## Block E — Abschluss
- Nachtest am Handy für die geänderten Punkte (Testplan 3.13, 4.2, 4.5, 4.7, 4.9, 5.1) und Eintrag in `testplan.md`.
- `phasen/README.md`, Feature-Index, Phasendatei „Abschluss“ mit Baseline/Endstand der Testzahlen und Abweichungen.
- Hinweis zum Rollout (Server zuerst, dann App, `make backup`) bleibt beim Nutzer.

## Verifikation
- App: `export ANDROID_HOME=/root/android-sdk ANDROID_SDK_ROOT=/root/android-sdk; ./gradlew :app:testDebugUnitTest :app:compileDebugKotlin --console=plain --no-parallel --max-workers=1 --no-configuration-cache` — alle grün, Testzahl 287 + neue. (Auf dem lokalen PC genügt `./gradlew …`, `local.properties` zeigt auf `/home/maikg/Android/Sdk`.)
- Server: `cd server && .venv/bin/python -m pytest -q` (venv Python 3.12, `server/.venv`) — 180 + neue.
- Gerät (Debug-Build `com.helga.android.debug`, lokaler Testserver wie im Testlauf; `adb reverse tcp:8000 tcp:8000`, Server mit `API_KEY=<Testwert> AI_API_KEY=` starten): FAB → „Markdown importieren“ → Zwischenablage enthält den Prompt → Beispieltext mit zwei Rezepten einfügen → Vorschau → Speichern → beide Rezepte in der Liste, Zeiten als „30 Min“ statt „PT30M“, Sync-Push am Server prüfbar über `GET /api/sync?since=0`. Fehlerfälle: Text ohne Titel, leere Eingabe, 51 Rezepte.
- Fixes am Gerät: Drehen mit Budget-Dialog/Wochenplan-Notiz/Rezept-Notiz (Text bleibt), Galerie-Scan ohne KI → Bon behält OCR-Werte, leere Bon-Liste zeigt „Ältere Bons laden“, „Letzter Sync“ mit echter Zeit, Chip „Gesamt“ einzeilig.

## Offene Punkte (Stufe A klärt)
- **F13 Ursache B:** Fix auf Client, Server oder beiden Seiten (Empfehlung: beide).
- **F18:** einheitliche sichere ID-Regex (Empfehlung) oder strikt UUID.
- **F6:** konkrete Zielversionen gegen Release-Notes prüfen; 32-Bit-ABIs.
- **F5:** Gestaltungsentscheidung des Nutzers.
- Kein Docker auf dem Entwicklungs-PC: `make docker-test` und Container-Prüfungen bleiben Nutzer-Aufgabe (Homelab).
