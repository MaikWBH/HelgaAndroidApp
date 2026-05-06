# Helga Android – Entwicklungsplan

Offline-First, bidirektionaler Sync (Last-Write-Wins). Jede Phase liefert eine **lauffähige APK**. Room ist immer Source of Truth – Netzwerk ist optional.

```
HelgaAndroidApp/
├── app/       # Android-App (Kotlin, Compose) – ab Phase 1
└── server/    # FastAPI-Sync-Backend – Phase 0 ✅ fertig
```

---

## Performance-Leitlinien (alle Phasen)

**Compose**
- Datenklassen als `@Immutable` – verhindert unnötige Recompositions
- `LazyColumn`/`LazyVerticalGrid` mit stabilem `key = { it.id }`
- `remember` + `derivedStateOf` für abgeleitete State-Werte
- `@Composable`-Lambdas in `items()` nicht inline definieren – separaten `@Composable` extrahieren

**Room / Lokale DB**
- `Flow<List<T>>` aus DAOs – Room triggert Recomposition automatisch bei Änderungen
- `@Transaction` für Operationen über mehrere Tabellen
- Indices auf häufig gefilterte Spalten (`tag`, `deleted`, `updatedAt`)
- WAL-Modus aktivieren (`enableWriteAheadLogging()`)

**Netzwerk (nur Sync)**
- Moshi mit **Codegen** (`moshi-kotlin-codegen`) – keine Reflection
- OkHttp mit `ConnectionPool` + GZIP
- Sync-Payload komprimieren: nur `dirty`-Records senden, nicht die gesamte DB

**Build**
- R8 + Resource Shrinking in Release
- `nonTransitiveRClass=true`, `enableR8FullMode=true`
- Baseline Profile ab Phase 3

**Threading**
- Room-DAOs immer auf `Dispatchers.IO`
- `viewModelScope` mit `SupervisorJob` (Default)
- WorkManager-Worker: `CoroutineWorker`

---

## Phase 0 – Sync-Backend ✅ FERTIG

Code liegt in `server/`. Siehe `server/CLAUDE.md` für Details.

**Endpunkte:**
```
GET  /api/health
GET  /api/sync?since=<ts>         # Pull: alle Änderungen nach Timestamp
POST /api/sync                    # Push: LWW-Upsert, gibt Server-Wins zurück
POST /api/ai/generate             # SSE-Stream
POST /api/ai/remix                # SSE-Stream
POST /api/ai/classify             # JSON
POST /api/images/upload
GET  /api/images/<filename>
GET  /api/suggestions/items|aisles|units
```

**Deployment:**
```bash
cd server && docker compose up -d          # lokal
make deploy                                # Homeserver (SERVER_HOST in Makefile setzen)
make migrate                               # Helga-Daten einmalig importieren
```

---

## Phase 1 – Android Foundation + lokale Rezeptliste ✅ FERTIG

**Ziel:** App startet offline, zeigt lokale Rezepte (initial leer), Room ist eingerichtet.

**Implementiert:**
- Gradle-Setup: Kotlin, Compose, Hilt, Room, Retrofit, Moshi Codegen, Coil, WorkManager, DataStore
- Room-Schema: `RecipeEntity`, `IngredientEntity`, `InstructionEntity`, `TagEntity`
- `AppDatabase` mit WAL-Modus
- `RecipeListScreen`, Onboarding-Screen

---

## Phase 2 – Sync-Engine ✅ FERTIG

**Ziel:** Bidirektionaler Sync (LWW).

**Implementiert:**
- `SyncApi`, `SyncEngine`, `SyncWorker`
- `SyncScheduler` mit periodischem Sync + NetworkCallback
- Sync-Status-Icon in Top-Bar
- Manueller Sync via Settings

---

## Phase 3 – Rezept-Detail + Tag-Filter + Bewertung ✅ FERTIG

**Implementiert:**
- `RecipeDetailScreen` mit Bild, Zutaten, Schritte, Metadaten, Sterne-Bewertung
- Tag-Filterung, Sortierung (Name / Bewertung / Zuletzt geändert)
- `SharedTransitionLayout` für Bild-Übergang
- Baseline Profile (Macrobenchmark-Modul)

---

## Phase 4 – Manuelle Rezeptverwaltung (CRUD) ✅ FERTIG

**Implementiert:**
- `RecipeFormScreen` (Erstellen + Bearbeiten)
- UUID clientseitig generieren
- Dynamische Listen für Zutaten und Schritte (Add/Remove/Reorder)
- Bild-Auswahl via `PickVisualMedia`
- Soft-Delete (`deleted = true`)

---

## Phase 5 – URL-Import + Kochansicht ✅ FERTIG

**Implementiert:**
- `UrlImportScreen`, `UrlImportViewModel`
- `RecipeCookScreen`, `RecipeCookViewModel`
- `SyncApi.importFromUrl()`
- Android-Share-Target für Browser-URLs
- Kochansicht: Schritt-für-Schritt (lokale State, kein DB-Write)

---

## Phase 6 – KI-Rezepterstellung + Klassifikation (SSE) ✅ FERTIG

**Implementiert:**
- `AiGenerateScreen`, `AiGenerateViewModel` (mit `SseClient`)
- `AiRemixScreen`, `AiRemixViewModel`
- `RecipeJsonLdParser` für HTML→Rezept-Extraktion
- KI-Klassifikation (einzeln + Bulk)
- SSE via OkHttp `EventSource`

---

## Phase 7 – Einkaufsliste ✅ FERTIG

**Implementiert:**
- `ShoppingListScreen`, `ShoppingListViewModel`
- `ShoppingListEntity`, `ShoppingItemEntity`
- Items gruppiert nach Gang, Checkboxes, Swipe-to-Delete
- `QuickAddBar` mit Autocomplete
- Listenwechsel via Top-Bar-Dropdown
- Optimistic UI

---

## Phase 8 – Märkte + Gänge + Vorratsstapel + Emoji-Schnellbuttons ✅ FERTIG

**Implementiert:**
- `StoreListScreen`, `StoreListViewModel`
- `StoreEntity`, `StoreAisleEntity`
- `ShoppingListStapleEntity` (Vorratsstapel)
- `QuickEmojiEntity`, `QuickEmojiDao` (Emoji-Schnellbuttons)
- Gangreihenfolge per Drag-Reorder (Reorder-Buttons)
- Dialog „Gang zuweisen" für Items ohne Zuordnung

---

## Phase 9 – Wochenplan (manuell) ✅ FERTIG

**Implementiert:**
- `WeekplanScreen`, `WeekplanViewModel`
- `WeekplanDayEntity`, `WeekplanRecipeEntity`, `WeekplanExtraEntity`
- Tageskarten mit Rezept + Extra-Items
- Tagesnotizen, Rezept-Picker (ModalBottomSheet)
- Export einzelner Tage oder ganzer Woche zu Einkaufsliste
- `daySummaries` StateFlow (multi-day COUNT aggregation)

---

## Phase 10 – KI-Wochenplanung ✅ FERTIG

**Implementiert:**
- Server: `POST /api/weekplan/generate` (JSON, LLM-basiert mit Constraint-Berücksichtigung)
- Server: `weekplan_settings` + `weekplan_constraints` Tabellen + vollständige Sync-Integration
- Room: `WeekplanConstraintsEntity` (DB v7) + `WeekplanConstraintsDao`
- Sync: Constraints bidirektional syncbar (SyncEngine, SyncDto, SyncDao)
- UI: Constraints-Editor (3 Slider: max Fleisch, min Vegetarisch, Wiederholungssperre)
- UI: „KI-Wochenplan"-Button (AutoAwesome-Icon) in WeekplanScreen-TopBar
- UI: KI-Vorschlag-Sheet mit Annehmen/Verwerfen
- UI: Tune-Icon-Button öffnet Constraints-Editor

---

## Phase 13 – Feature-Ergänzung (Flask→Android Migration) ✅ FERTIG

**Ziel:** Restliche Flask-Features, die noch fehlen.  
**Implementiert:** Alle 9 Tasks erledigt (Rezept→Einkaufsliste, Item-Bearbeitung, Wochenplan-Zeitraum konfigurierbar, Einkaufstag, Listen-Verwaltung, Emoji-Schnellbuttons, KI-Preview Verwerfen, WeekplanSettings Sync, Strings).

**Android-Tasks (9 Items)**

### 1. Rezept → Einkaufsliste (2 Subtasks)
- **1.1:** Button „Zu Einkaufsliste hinzufügen" in `RecipeDetailScreen` (MoreVert-Menü)
- **1.2:** Dialog zur Listenauswahl, dann `RecipeRepository.exportToShoppingList(recipeId, listId)`

### 2. Einkaufs-Items inline bearbeiten (3 Subtasks)
- **2.1:** `ShoppingItemRow` → Tap auf Item öffnet Edit-Dialog (Menge, Einheit, Name)
- **2.2:** `ShoppingItemEntity` ändern → `viewModel.updateItem(id, quantity, unit, name)`
- **2.3:** Optimistic UI: Änderung sofort sichtbar, Sync im Hintergrund

### 3. Wochenplan-Zeitraum konfigurierbar (3 Subtasks)
- **3.1:** `WeekplanSettingsEntity` oder Preferences (`planDays`: 7, 10, 14)
- **3.2:** UI in `SettingsScreen` (RadioButton/Dropdown: 7/10/14 Tage)
- **3.3:** `WeekplanViewModel.ensureWeek()` nutzt konfigurierte Anzahl statt fest 7

### 4. Einkaufstag konfigurierbar (2 Subtasks)
- **4.1:** Preferences-Feld `shoppingDay` (0=Mo, 1=Di, ..., 6=So)
- **4.2:** UI in `SettingsScreen` (Dropdown oder Spinner)

### 5. Einkaufslisten-Verwaltung in Settings (2 Subtasks)
- **5.1:** Settings-Shopping-Sektion erweitern: „Löschen"-Button pro Liste (mit Bestätigung)
- **5.2:** „Standardliste"-Selector (RadioButton) — diese wird per Default beim Öffnen geladen

### 6. Emoji-Schnellbuttons Management in Settings (2 Subtasks)
- **6.1:** Neue Subsection in Settings: „Schnellbuttons"
- **6.2:** CRUD-UI (Liste, Add, Edit, Delete) — nutzt `QuickEmojiDao`

### 7. KI-Preview „Verwerfen"-Button (2 Subtasks)
- **7.1:** `AiPreviewContent` um Button „Verwerfen" erweitern
- **7.2:** `onDiscard`-Callback → setzt Status auf Idle/Input, User kann neuen Prompt eingeben

### 8. Sync-Integration für neue Features (1 Task)
- **8.1:** `WeekplanSettingsEntity` zu `SyncDto`, `SyncEngine`, `SyncDao`

### 9. Strings (1 Task)
- **9.1:** Alle neuen UI-Strings zu `strings.xml`

---

## Phase 14 – Einstellungen-Vollausbau + Polish ✅ FERTIG

**Implementiert:**
- **StrictMode** im Debug-Build (`HelgaApp.kt`): ThreadPolicy + VmPolicy mit `penaltyLog()`
- **LeakCanary 2.14** als `debugImplementation` Dependency
- **Sync-Fehleranzeige** in Settings: `SyncStatusHolder` in `SettingsViewModel` injiziert, Fehlertext rot unter letztem Sync-Zeitpunkt
- **Proguard-Rules** vollständig ausgebaut: Retrofit, Moshi Codegen, Room, Hilt, Coil, DataStore, WorkManager, Coroutines, OkHttp
- **VersionCode aus Git** (`gitCommitCount()` in `build.gradle.kts` via `git rev-list --count HEAD`)
- **Test-Dependencies** hinzugefügt: JUnit 4, MockK, Coroutines-Test, Turbine
- **Unit-Tests** (3 Klassen, 14 Tests): `SyncLwwTest` (LWW-Logik), `SettingsValidationTest` (URL-Validierung), `SyncStatusTest` (SyncStatusHolder Flow)
- **Bereits vorhanden**: Dark Mode, Material You (dynamicColor), Edge-to-Edge, R8 + Resource Shrinking, ProfileInstaller, Timber

---

## Helga Flask-App – Abschalt-Plan

| Milestone | Aktion |
|-----------|--------|
| Phase 0 abgeschlossen | Migration durchgeführt, Flask-App read-only lassen |
| Phase 9 abgeschlossen | Alle Kern-Features in Android verfügbar |
| Phase 13 abgeschlossen | Flask-Features vollständig portiert, Flask-App kann abgeschaltet werden |
| Phase 14 abgeschlossen | Android-App production-ready, letzte Fehlerbereinigung |

---

## Cross-Cutting Concerns (kontinuierlich)

- **Fehlerbehandlung:** Netzwerkfehler sind nicht-fatal (App funktioniert offline), Room-Fehler sind fatal
- **Logging:** Timber in Debug, deaktiviert in Release
- **Versionierung:** `versionCode` aus Git-Commit-Count, `versionName` aus Tag
- **Room-Migrationen:** Jede Schema-Änderung braucht `@Migration(from, to)` – kein `fallbackToDestructiveMigration` in Produktion
- **CI:** Lint + Unit-Tests + APK-Build pro Push
- **Dokumentation:** README mit Setup, Build, Deployment, Contributing Guidelines
