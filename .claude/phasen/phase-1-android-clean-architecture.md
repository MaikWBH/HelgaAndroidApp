# Phase 1 — android-clean-architecture

Basispfad `$B` = `app/src/main/kotlin/com/helga/android/` · Testpfad `$T` = `app/src/test/kotlin/com/helga/android/`

## Modelle
- Definition: Sonnet 5.5 · Umsetzung: Haiku 5.5 · Datum: 2026-11-11

## Ziel und Bereich
Schichtgrenzen der Datenschicht durchsetzen (Bereiche laut Feature-Index: Sync, Rezepte, Wochenplan, Einkaufsliste,
Bons & Kosten, Einstellungen — querschnittlich). ViewModels sprechen nur noch mit Repositories/UseCases, Netzwerkzugriff
liegt ausschließlich in der Datenschicht. Skill-Referenz: Anti-Patterns „Business-Logik im ViewModel", „fette
Repositories" (angepasst an Single-Modul MVVM, siehe Entscheidungen).

## Entscheidungen (aus grilling, 2026-11-11)
- **Zielbild pragmatisch:** Single-Modul, kein Domain-Modul, kein Interface pro Repository. Neue Interfaces nur, wo
  Testbarkeit sie erzwingt (hier: `ApiProvider`). Repository-übergreifende Logik wandert in einzelne UseCases.
- **Schema/Sync unberührt:** keine Room-Änderung, keine Migration, keine Änderung an `SyncApi`, Sync-DTOs oder `server/`.
- **Tests:** Bei jedem Schritt, der Logik verschiebt, vorher einen Charakterisierungstest schreiben (vorher und nachher
  grün). Reine Konstruktor-Umverdrahtung braucht keinen.
- **Entities bleiben in der UI** (29 Dateien). Eigene UI-Modelle nur, wo ein ViewModel Daten ohnehin aufbereitet.
- **UiState:** `sealed class UiState<T>` anlegen, nur in ViewModels anwenden, die in dieser Phase ohnehin umgebaut werden.
- **Umfang:** alle vier Blöcke — Retrofit raus (A), DAOs raus (B), UseCase Wochenplan→Einkauf (C), `WeekplanViewModel` aufteilen (D).

## Befunde (Stand 2026-11-11)
| # | Fundstelle | Befund | Regel |
|---|------------|--------|-------|
| 1 | `ui/**/*ViewModel.kt` | 7 VMs injizieren `SyncApiFactory`: Shopping (387, 399), RecipeDetail (178, 344), RecipeList (142), UrlImport (61, 143), Weekplan (1014), Settings (173, 395), Onboarding (42) | VM nie direkt an Retrofit (CLAUDE.md: Single Source of Truth) |
| 2 | `SettingsViewModel` 173 / `OnboardingViewModel` 42 | Identischer Verbindungstest-Ablauf (health → saveConnection → schedulePeriodic → triggerOneShot) doppelt | Duplikat |
| 3 | 8 VMs | Direkte DAO-Injektion: Shopping (4 DAOs), Stats, RecipeDetail, RecipeCook, RecipeList, CostOverview, Settings, Weekplan (35 Zugriffe) | VM → Repository |
| 4 | `ShoppingListViewModel.kt:225-245` | `exportWeekToShoppingList` verknüpft `weekplanDao`, `recipeDao`, `repository` — repository-übergreifende Logik im VM; parallel existieren `WeekplanRepository.collectExportItems/applyExportItems` | Logik im VM |
| 5 | `WeekplanViewModel.kt` | 1019 Zeilen; `generateWeekplan` (623–795), `regenerateProposalDay` (816), `regenerateDay` (885), `repeatLastWeek` (953) enthalten Planungslogik | Business-Logik im VM |
| 6 | `ui/**` | `sealed class UiState<T>` (CLAUDE.md) existiert nirgends | Konvention |
| 7 | `$T` | Keine Tests für ViewModels/Repositories (13 Testklassen) | – |

## Schritte (Stufe B arbeitet nur diese Liste ab)

Vorgehen je Schritt: Charakterisierungstest zuerst (falls „Test: ja"), Änderung, dann Verifikation, dann Commit
(Deutsch, ein Satz, Attribution laut Session-Anweisung). Verifikation = `./gradlew :app:testDebugUnitTest :app:compileDebugKotlin`.
Läuft Gradle in der Umgebung nicht, gilt der Schritt als **nicht verifiziert**: stoppen und per `grilling` melden.

### Block 0 — Basis
- [x] **S0 Baseline** — `./gradlew :app:testDebugUnitTest` ausführen, Ergebnis (Anzahl Tests, grün/rot) oben im Abschnitt „Abschluss" notieren. Test: nein.
- [x] **S1 ApiProvider-Interface** — Dateien: `$B/data/remote/SyncApiFactory.kt`, `$B/di/NetworkModule.kt`. `interface ApiProvider { suspend fun api(): SyncApi; fun apiForOnboarding(serverUrl: String, apiKey: String): SyncApi }` anlegen (gleiche Datei wie die Factory), `SyncApiFactory` implementiert es, Hilt `@Binds` in `NetworkModule`. Nur Repositories/Services injizieren künftig `ApiProvider`. Akzeptanz: App kompiliert, bestehende Tests grün. Test: nein.
- [x] **S2 UiState anlegen** — Datei neu: `$B/ui/common/UiState.kt`: `sealed class UiState<out T> { data object Loading; data class Success<T>(val data: T); data class Error(val message: String) }`. Akzeptanz: kompiliert, noch nirgends genutzt. Test: nein.

### Block A — Retrofit raus aus ViewModels (Befund 1, 2)
Gemeinsame Fehlerabbildung: `HttpException` → Meldung aus `errorBody` (vorhandene Funktion `extractErrorDetail` aus `UrlImportViewModel` in die Datenschicht verschieben), `IOException` → „Server nicht erreichbar", sonst `e.message`.
- [x] **S3 ServerConnectionChecker** — neu: `$B/data/remote/ServerConnectionChecker.kt` (`@Singleton`, injiziert `ApiProvider`, `AppPreferences`, `SyncScheduler`). `suspend fun test(url, key): ConnectionResult` führt `apiForOnboarding(url,key).health()` aus und gibt `Success`/`HttpError(code, detail)`/`Unreachable`/`Other(msg)` zurück; bei `Success` speichert es `preferences.saveConnection`, `schedulePeriodic()`, `triggerOneShot()`. `SettingsViewModel` (≈ Z.173) und `OnboardingViewModel` (≈ Z.42) rufen nur noch `test` auf und mappen auf ihren State (`SettingsValidation` / `Validation`). Test: ja — `$T/ServerConnectionCheckerTest.kt` mit Fake-`ApiProvider` (Erfolg, HttpException 401, IOException).
- [x] **S4 AiRepository (Klassifikation + Nährwerte)** — neu: `$B/data/repository/AiRepository.kt` mit `classifyRecipe(...)` und `estimateNutrition(...)` (nehmen/liefern DTOs wie `SyncApi`). `RecipeDetailViewModel.classify()` (≈ Z.172–190) und `calcNutrition` (≈ Z.340–360), `RecipeListViewModel` (≈ Z.142, `classifyRecipeAsync`) rufen `AiRepository`; `apiFactory` aus deren Konstruktoren entfernen. Test: ja — `$T/AiRepositoryTest.kt` (Fake-`ApiProvider`, Klassifikation und Fehlerpfad).
- [x] **S5 URL-Import in AiRepository** — `importFromUrl(url): ImportOutcome` ergänzen (Fehlerabbildung inkl. `extractErrorDetail` hierher verschieben). `UrlImportViewModel.import()` (≈ Z.55–75) und `classifyRecipeAsync` (≈ Z.140) nutzen es; `apiFactory` aus Konstruktor entfernen. Test: ja — `$T/AiRepositoryUrlImportTest.kt` (Erfolg, HttpException mit errorBody-Detail, IOException).
- [x] **S6 Bulk-KI in AiRepository** — `SettingsViewModel.runBulkAi` (≈ Z.390–440) nutzt direkt `apiFactory.api()`; die API-Aufrufe (Klassifikation/Nährwerte pro Rezept) über `AiRepository`, Schleife und Fortschritts-State bleiben im VM. `apiFactory` aus `SettingsViewModel` entfernen. Test: nein (nur Umverdrahtung).
- [x] **S7 Vorschläge und Barcode** — in `ShoppingRepository` ergänzen: `suspend fun suggestItems(query: String): List<String>` (mit `withTimeoutOrNull(1_500)`, bei Timeout/Fehler leere Liste — Verhalten wie `ShoppingListViewModel` ≈ Z.387) und `suspend fun lookupBarcode(...)` (Verhalten wie ≈ Z.399). `ShoppingListViewModel` und `WeekplanViewModel.suggestItems` (≈ Z.1011) nutzen sie; `apiFactory` aus beiden Konstruktoren entfernen. Test: ja — `$T/ShoppingSuggestionsTest.kt` (Timeout → leer, Fehler → leer, Erfolg → Liste).
- [x] **S8 Netzwerkgrenze prüfen** — `grep -rn "SyncApiFactory\|ApiProvider\|data.remote" $B/ui` darf nur noch DTO-Typen liefern, keine Factory/Provider. Fund-Liste in „Abschluss" notieren. Test: nein.

### Block B — DAOs raus aus ViewModels (Befund 3)
Pro Schritt: DAO-Zugriffe des VM als Repository-Methoden mit gleichem Rückgabetyp (`Flow<…>`/`suspend`) anlegen, VM umstellen, DAO aus VM-Konstruktor entfernen.
- [x] **S9 StatsViewModel** — `historyDao`/`recipeDao` (Z.83–111) → `RecipeRepository.observeHistorySince(date)`, `recipeIdsBefore(date)`, `findRecipe(id)`. Test: ja — vorhandener `StatsAggregationTest` bleibt grün; neuen Test nur wenn Aggregationslogik verschoben wird.
- [x] **S10 RecipeCook + RecipeDetail + RecipeList** — `RecipeCookViewModel` (Z.118–144: `markCooked`, `upsertAll`, `findByRecipeAndDate`, `upsert`) → `RecipeRepository.markCooked(recipeId, date, now)` und `saveFeedback(...)`; `RecipeDetailViewModel` (Z.276–278: `recipesForDay`, `findById`) → `WeekplanRepository`/`RecipeRepository`; `RecipeListViewModel` (Z.95 `observeTodayRecipeId`) → `WeekplanRepository.observeTodayRecipeId`. Test: ja — `$T/RecipeRepositoryCookTest.kt` für `markCooked` (neuer Eintrag vs. vorhandener Eintrag, dirty=1 und updatedAt gesetzt laut `sync-patterns.md`), DAOs per `mockk` mocken (`mockk` und `turbine` sind bereits Test-Abhängigkeiten; `room-testing` nicht — keine neuen Abhängigkeiten hinzufügen).
- [x] **S11 CostOverviewViewModel** — `receiptDao`/`monthlyBudgetDao` (10 Stellen) → `ReceiptRepository` (`observeBudget`, `saveBudget`, `totalCost(range)`, `costByStore(range?)`, `costByDate(range?)`). Test: nein.
- [x] **S12 SettingsViewModel** — `weekplanSettingsDao` → `WeekplanRepository.saveSettings`, `quickEmojiDao` → `ShoppingRepository.observeQuickEmojis/upsertQuickEmoji`. Test: nein.
- [x] **S13 ShoppingListViewModel (ohne Export)** — `quickEmojiDao` → `ShoppingRepository`, `weekplanDao.observeDaysBetween/observeRecipeCount` → `WeekplanRepository.observeHasRecipesBetween(start, end): Flow<Boolean>`, `receiptDao.observeReceiptCountForListToday` → `ReceiptRepository`, `recipeDao.searchIngredientNames` (Z.381) → `RecipeRepository`. `recipeDao`/`weekplanDao`/`receiptDao`/`quickEmojiDao` entfernen, bis auf die Nutzung in `exportWeekToShoppingList` (Schritt S14). Test: ja — `$T/WeekplanHasRecipesTest.kt` (Flow liefert true/false je nach Rezeptanzahl) mit Fake-DAO.

### Block C — UseCase Wochenplan → Einkaufsliste (Befund 4)
- [x] **S14 exportWeekToShoppingList** — Aufrufer prüfen (`grep -rn "exportWeekToShoppingList" $B`). **Regel:** Ist die Methode in der UI nicht aufgerufen, wird sie samt `weekplanDao`/`recipeDao`/`receiptDao`-Resten aus `ShoppingListViewModel` gelöscht. Wird sie aufgerufen, entsteht `$B/data/usecase/ExportWeekToShoppingListUseCase.kt` (`operator fun invoke(listId, weekStart, weekEnd)`), das `WeekplanRepository`, `RecipeRepository` und `ShoppingRepository.addOrMergeItem` nutzt (gleiche Logik wie Z.228–244: gelöschte Zutaten überspringen, `source = "weekplan"`, `recipeName`), das VM ruft den UseCase und danach `syncScheduler.triggerOneShot()`. Test: ja (nur im Aufrufer-Fall) — `$T/ExportWeekToShoppingListUseCaseTest.kt` mit Fake-Repositories: zwei Rezepte, eine gelöschte Zutat → nur aktive Zutaten werden gemerged.

### Block D — WeekplanViewModel aufteilen (Befund 5)
Ziel: `WeekplanViewModel.kt` unter 500 Zeilen, Verhalten unverändert. `computeWeekBalance` und `filterCandidateRecipes` (Top-Level, durch `WeekplanFiltersTest` abgedeckt) bleiben unverändert in ihrer Datei.
- [x] **S15 Charakterisierung der Generierung** — vor dem Umbau Test `$T/WeekplanGenerationCharacterizationTest.kt`: pure Teile aus `generateWeekplan` (Anker-Tage für gesperrte Tage, Wiederholungssperre `maxRepeatDays`, Feedback-Scores `sumOf { liked }`, Fallback auf alle Rezepte, wenn alle kürzlich geplant) als Top-Level-Funktionen in `$B/ui/weekplan/WeekplanPlanning.kt` extrahieren (reines Verschieben, gleiche Signaturen und Semantik) und mit Tabellentests absichern. Test: ja.
- [x] **S16 WeekplanGenerator auslagern** — neu: `$B/data/weekplan/WeekplanGenerator.kt` (`@Singleton`, injiziert die in `WeekplanViewModel` für Generierung genutzten DAOs/Repositories). Verschieben aus dem VM: `generateWeekplan`, `applyRecipeFilters`, `applyProposal`, `discardProposal`, `regenerateProposalDay`, `regenerateDay`, `repeatLastWeek`, `recordHistory`. Das VM behält `_generateStatus` und ruft nur noch `generator.…`; Funktionen, die `viewModelScope` brauchen, bleiben im VM und delegieren. Test: S15-Test bleibt grün.
- [ ] **S17 Weekplan-DAOs aus dem VM** — verbleibende DAO-Zugriffe (`recipeHistoryDao`, `recipeFeedbackDao`, `shoppingDao`, `weekplanDao`, `weekplanSettingsDao`, `weekplanConstraintsDao`, `recipeDao.observeAll` Z.268) hinter `WeekplanRepository`/`RecipeRepository`/`ShoppingRepository`. VM-Zeilen zählen: `wc -l` < 500 (sonst Rest in `WeekplanExportController`-ähnliche Datei verschieben und melden, statt weiterzukürzen). Test: nein.

### Block E — UiState und Regeln absichern
- [ ] **S18 UiState anwenden** — in `UrlImportViewModel` `ImportStatus` durch `UiState<ImportedRecipeDto>` ersetzen (und Screen `UrlImportScreen.kt` anpassen); in `RecipeDetailViewModel` die Zustände `_classifyState`/`_nutritionCalcState` bleiben unverändert. Nur dieses eine VM, da nur angefasste Screens umgestellt werden. Test: nein.
- [ ] **S19 Architekturregel als Test** — neu: `$T/ArchitectureRulesTest.kt`: liest alle `*ViewModel.kt` unter `src/main/kotlin/com/helga/android/ui` (Arbeitsverzeichnis der Unit-Tests ist `app/`) und schlägt fehl, wenn eine Datei `data.local.dao`-Import oder `SyncApi`/`SyncApiFactory`/`ApiProvider`/`OkHttp`/`Retrofit` enthält. Test: ja (er ist der Test).
- [ ] **S20 Guideline und Index** — neu: `.claude/guidelines/architecture-layers.md` (max. 40 Zeilen: erlaubte Abhängigkeiten UI → Repository/UseCase → DAO/API; Hilt-Regel; Verweis auf `ArchitectureRulesTest`); in `.claude/features/README.md` unter „Bereichsübergreifende Befunde" eine Zeile ergänzen; in den betroffenen Bereichs-`plan.md` (rezepte, wochenplan, einkaufsliste, bons-kosten, einstellungen) je einen Vermerk „Phase 1 umgesetzt". Test: nein.

## Entscheidungen Stufe B (2026-11-11, nach Stopp bei S16)
- **S16 Vorbedingung (neu, vor dem Auslagern):** `filterCandidateRecipes`, `computeWeekBalance` (nur falls vom Generator gebraucht), `WeekplanGenerateStatus` und `parseAllergenJson` werden **unverändert** (gleiche Signaturen/Semantik) in die Datenschicht verschoben, z. B. nach `$B/data/weekplan/WeekplanFilters.kt` bzw. `$B/data/weekplan/WeekplanGenerateStatus.kt`. `ui/weekplan/` (ViewModel, Screen, Tests) importiert von dort. Danach `WeekplanGenerator` in `$B/data/weekplan/` wie in S16 beschrieben. Eine Datenschicht darf nie aus `ui/` importieren.
- **Akzeptierte Abweichungen:** S6 (Bulk-KI zählt fehlende Server-Konfiguration pro Rezept als Fehler, behebt hängendes `isRunning`) und S7 (1,5-s-Timeout auch für Wochenplan-Artikelvorschläge) bleiben so.
- **S19-Ausnahme:** `ArchitectureRulesTest` verbietet in `*ViewModel.kt` unter `ui/` nur `data.local.dao`, `SyncApi`, `SyncApiFactory`, `ApiProvider`, `retrofit2`. `data.remote.SseClient` (in `AiGenerateViewModel`, `AiRemixViewModel`) ist als Datenschicht-Dienst **erlaubt**; im Test als Kommentar dokumentieren und in `architecture-layers.md` (S20) erwähnen.

## Nicht-Ziele
- Kein Multi-Modul-Umbau, keine Domain-Interfaces, kein Koin/KMP.
- Keine Room-Schema-, Migration-, DTO- oder Server-Änderung.
- Keine UI-Modelle für alle Screens, kein Umbau auf `UiState` über S18 hinaus.
- Keine visuellen Änderungen (Phase 2).
- Kein Refactoring von `SyncEngine` und `SyncWorker`.

## Abschluss
- [ ] Baseline S0 notiert: `<Anzahl Tests / Ergebnis>`
- [ ] `./gradlew :app:testDebugUnitTest :app:compileDebugKotlin` grün nach S20 (Ausgabe festgehalten)
- [ ] `ArchitectureRulesTest` grün (Beweis: kein DAO-/Retrofit-Import in ViewModels)
- [ ] Bereichs-`plan.md` und Feature-Index aktualisiert (S20)

### Notizen Stufe B (Block A–E)
- S8 Fund-Liste (`grep -rn "SyncApiFactory\|ApiProvider\|data.remote" ui`, ohne dto): keine Factory/Provider mehr in `ui/`. Verbleibend: `ui/ai/AiGenerateViewModel.kt` und `ui/ai/AiRemixViewModel.kt` importieren `data.remote.SseClient` (SSE-Streaming, kein Factory/Provider; nicht in der S19-Verbotsliste, daher nicht Teil dieser Phase). `ConnectionResult`/`ServerConnectionChecker` in `OnboardingViewModel`/`SettingsViewModel` sind gewollte Ergebnis-/Service-Typen aus S3.

### Abschluss Stufe B (Stand: Stopp bei S16)
- Baseline S0: 115 Tests, 0 Fehler, BUILD SUCCESSFUL (vor Stufe B, bereits ausgeführt).
- Verifikation nach jedem abgeschlossenen Schritt grün; zuletzt nach S15: 142 Tests, 0 Fehler, BUILD SUCCESSFUL (`./gradlew :app:testDebugUnitTest :app:compileDebugKotlin`).
- Erledigt: S0–S15 (Block 0, A, B, C und S15 aus D). Gepusht nach Block 0, A, B, C (Branch `ccr-c53a7201-5hd57f`), S15 liegt committed, Stand zum Push siehe Git-Log.
- **STOPP bei S16 (nicht umgesetzt):** `data/weekplan/WeekplanGenerator.kt` braucht `filterCandidateRecipes`, `WeekplanGenerateStatus` und `parseAllergenJson` (letztere in `ui/weekplan/WeekplanScreen.kt:1181`). Eine Datenschicht, die aus `ui/` importiert, widerspricht der Schichtung in CLAUDE.md. Der Plan legt nicht fest, wie das aufzulösen ist (Helfer verschieben oder Ergebnistyp neu definieren). Entscheidung nötig, bevor S16 weitergeht.
- Nicht umgesetzt wegen des Stopps: S16, S17, S18, S19 (ArchitectureRulesTest), S20 (Guideline, Feature-Index, Bereichs-`plan.md`).
- `WeekplanViewModel.kt` aktuell: 1008 Zeilen (Ziel < 500, noch nicht erreicht).

Abweichungen und Auffälligkeiten (Stufe B):
- S1: Plan verlangt `@Binds` in `NetworkModule`. `NetworkModule` ist ein `object`, und `@Binds` braucht ein abstraktes Modul. Umgesetzt als `@Provides fun provideApiProvider(factory: SyncApiFactory): ApiProvider = factory` in `di/NetworkModule.kt`.
- S3: `HttpError(code, detail)` braucht `extractErrorDetail`; dafür neu `data/remote/ApiErrorMapping.kt` (Top-Level), in S5 von der privaten Kopie in `UrlImportViewModel` übernommen.
- S4: Plan-Namen teils abweichend. `calcNutrition` heißt im Code `calculateNutritionWithAi`. `classifyRecipeAsync` steht in `UrlImportViewModel` (S5), nicht in `RecipeListViewModel`; dort heißt die Funktion `classifyBatch`.
- S5: `ImportOutcome` (Success/Error) ist Plan-Name ohne Form; Form analog zum bestehenden `ImportStatus`. `classifyRecipeAsync` nutzt `AiRepository.classifyRecipe`.
- S6: Verhaltensänderung: fehlende Server-Konfiguration wirft nicht mehr vor der Schleife, sondern zählt pro Rezept als fehlgeschlagen (vorher blieb `isRunning` bei dieser Ausnahme stehen).
- S7: Verhaltensänderung: `WeekplanViewModel.suggestItems` bekommt den 1,5-s-Timeout aus `ShoppingRepository.suggestItems` (vorher ohne Timeout), weil der Plan beide Aufrufer auf dieselbe Methode legt.
- S8 (Fund-Liste): `ui/ai/AiGenerateViewModel.kt` und `ui/ai/AiRemixViewModel.kt` importieren `data.remote.SseClient`. Nicht Teil der S19-Regel; zur Entscheidung offen.
- S9: `RecipeRepository.findRecipe` ist ein Duplikat von `findById` (Plan-Name übernommen).
- S13: Export-Abhängigkeiten (`weekplanDao`, `recipeDao`) blieben bis S14 in der VM, wie geplant.
- S14: `ExportWeekToShoppingListUseCase` umgesetzt, Aufrufer in `ShoppingListScreen.kt:362`.
- Lint: ungenutzte Imports in den geänderten Dateien entfernt (Stichprobe mit Namensprüfung, Build grün).

