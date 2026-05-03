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

## Phase 1 – Android Foundation + lokale Rezeptliste (erste APK)

**Ziel:** App startet offline, zeigt lokale Rezepte (initial leer), Room ist eingerichtet.

**Android-Tasks**
1. Gradle-Setup: Kotlin, Compose, Hilt, Room, Retrofit, Moshi Codegen, Coil, WorkManager, DataStore
2. Room-Schema: `RecipeEntity`, `IngredientEntity`, `InstructionEntity`, `TagEntity` mit `id`, `updatedAt`, `deleted`, `dirty`
3. `AppDatabase` mit WAL-Modus, `RecipeDao` mit `Flow<List<RecipeEntity>>`
4. `RecipeRepository`: liest aus Room, schreibt in Room (setzt `dirty = true`, `updatedAt = now()`)
5. `RecipeListScreen`: `LazyColumn` mit Paging, Bild + Name + Kochzeit
6. **Onboarding-Screen:** Server-URL + API-Key eingeben → `DataStore`, Healthcheck

**Deliverable:** APK läuft offline. Liste zeigt „Noch keine Rezepte" (Room leer). Onboarding speichert Server-URL.

---

## Phase 2 – Sync-Engine

**Ziel:** Bidirektionaler Sync. Daten aus migriertem Server erscheinen in der App.

**Android-Tasks**
1. `SyncApi` (Retrofit): `GET /api/sync?since=`, `POST /api/sync`
2. `SyncEngine`: LWW-Merge – vergleicht `updatedAt` Server vs. Room, schreibt Winner in Room
3. `SyncWorker` (CoroutineWorker):
   - Push: alle `dirty = true` Records an Server
   - Pull: alle Server-Änderungen seit `lastSyncTs` in Room mergen
   - Speichert neuen `lastSyncTs` in `DataStore`
4. `SyncScheduler`: WorkManager `PeriodicWorkRequest` (15 min) + `NetworkCallback` für Sofort-Trigger bei Reconnect
5. Sync-Status sichtbar: Icon in Top-Bar (syncing / ok / offline / fehler)
6. Manueller Sync: Pull-to-Refresh im Einstellungs-Screen

**Deliverable:** App zeigt alle migrierten Rezepte nach erstem Sync. Änderungen on- und offline werden korrekt zusammengeführt.

---

## Phase 3 – Rezept-Detail + Tag-Filter + Bewertung

**Android-Tasks**
- `RecipeDetailScreen`: Bild, Zutaten, Schritte, Metadaten, Sterne-Bewertung
- Bewertung schreibt in Room → `dirty = true` → sync bei nächster Gelegenheit
- Tag-Chips in der Rezeptliste, Sortierung (Name / Bewertung / Zuletzt geändert)
- `SharedTransitionLayout` für Bild-Übergang Liste→Detail (Compose 1.7+)
- Coil: `placeholderMemoryCacheKey` damit Bild beim Übergang sofort sichtbar

**Baseline Profile** einrichten (Macrobenchmark-Modul).

---

## Phase 4 – Manuelle Rezeptverwaltung (CRUD)

**Android-Tasks**
- `RecipeFormScreen` (Erstellen + Bearbeiten, gemeinsamer Flow)
- UUID clientseitig generieren beim Anlegen – kein Server-Roundtrip nötig
- Dynamische Listen für Zutaten und Schritte (Add/Remove/Reorder)
- Bild-Auswahl via `ActivityResultContracts.PickVisualMedia` → lokal in App-Cache speichern
- Bild-Upload-Queue: WorkManager-Task der Bilder nach Sync hochlädt
- Lösch-Bestätigung → `deleted = true`, `dirty = true` (Soft-Delete)

---

## Phase 5 – URL-Import + Kochansicht

**Server-Tasks (`server/`)**
```
POST /api/recipes/import-url       # body: { url } → scraped RecipeDto
```

**Android-Tasks**
- URL-Eingabe → Vorschau-Screen → bei Bestätigung in Room speichern + Sync
- Android-Share-Target: URLs aus Browser-App direkt in Helga importieren
- **Kochansicht:** Schritt-für-Schritt, abhakbare Schritte (nur im Arbeitsspeicher – kein DB-Write), Wakelock

---

## Phase 6 – KI-Rezepterstellung + Klassifikation (SSE)

**Android-Tasks**
- `AiGenerateScreen`: Prompt → SSE-Stream als Typewriter-Effekt anzeigen
- `AiRemixScreen`: Original-Rezept + Änderungsprompt
- Ergebnis als Preview → bei Bestätigung in Room speichern (UUID clientseitig)
- KI-Klassifikation: einzeln (Detail-Screen) + Bulk (Einstellungen, WorkManager-Task)
- SSE via OkHttp `EventSource`; Stream-State: `MutableStateFlow<String>` mit `.update { it + chunk }`

---

## Phase 7 – Einkaufsliste

**Room-Schema:** `ShoppingListEntity`, `ShoppingItemEntity` (mit `aisle`, `done`, `dirty`, `updatedAt`)

**Android-Tasks**
- `ShoppingListScreen`: Items gruppiert nach Gang, Checkboxes, Swipe-to-Delete
- `QuickAddBar` mit Autocomplete (debounced 300 ms, ruft `/api/suggestions/items` auf wenn online)
- Listenwechsel via Top-Bar-Dropdown
- Optimistic UI: Toggle / Delete sofort in Room, Sync läuft im Hintergrund
- `SnapshotStateList` für Item-Mutationen (kein komplettes List-Replace)

---

## Phase 8 – Märkte + Gänge + Vorratsstapel + Emoji-Schnellbuttons

**Room-Schema:** `StoreEntity`, `AisleMappingEntity`, `StapleEntity`, `QuickEmojiEntity`

**Android-Tasks**
- `StoreSettingsScreen`: Märkte anlegen, Gangreihenfolge per Drag-Reorder
- Dialog „Gang zuweisen" für Items ohne Zuordnung (schreibt in `AisleMappingEntity`)
- Vorratsstapel-Editor pro Liste
- Emoji-Schnellbutton-Bar in Einkaufsliste

---

## Phase 9 – Wochenplan (manuell)

**Room-Schema:** `WeekplanDayEntity`, `WeekplanRecipeEntity`, `WeekplanExtraEntity`

**Android-Tasks**
- `WeekplanScreen`: Tageskarten als `LazyColumn`, Rezept per Bottom-Sheet hinzufügen
- Drag-and-Drop zwischen Tagen (initial: Tap-Menü „Verschieben nach …")
- Tagesnotizen, Extra-Items
- Shopping-Preview → Commit schreibt direkt in `ShoppingItemEntity` (Room)

---

## Phase 10 – KI-Wochenplanung

**Server-Tasks**
```
POST /api/weekplan/generate-smart   # SSE-Progress (liest Constraint-Settings)
POST /api/weekplan/generate-classic
```

**Room-Schema:** `WeekplanConstraintsEntity` (max Fleisch, min Fisch, …)

**Android-Tasks**
- Constraint-Editor (Slider-UI)
- „Plan generieren" mit SSE-Progress-Anzeige
- Vorher/Nachher-Vergleich, Annehmen oder Verwerfen

---

## Phase 11 – Mealie-Import

**Server-Tasks**
```
GET  /api/mealie/recipes            # Liste mit imported-Flag
POST /api/mealie/import             # SSE-Progress pro Rezept, schreibt in Server-DB
```

**Android-Tasks**
- Import-Screen mit Auswahl + Live-Progress (SSE)
- Nach Import sofort Sync triggern → Rezepte landen in Room

---

## Phase 12 – Einstellungen-Vollausbau + Polish

**Android-Tasks**
- Vollständiger Settings-Screen: Server-URL, API-Key, KI-Modell, Wochenstart, Standard-Einkaufsliste
- Sync-History: letzter Sync-Zeitpunkt, Fehler-Log
- Dark Mode, dynamische Farben (Material You)
- Adaptive Layouts für Tablet / Foldable

**Performance-Audit**
- Macrobenchmark: Startup, Listen-Scroll, Detail-Öffnung
- Baseline Profile aktualisieren
- Strict-Mode in Debug + LeakCanary
- R8-Output prüfen: keine unnötigen Klassen im APK

---

## Helga Flask-App – Abschalt-Plan

| Milestone | Aktion |
|-----------|--------|
| Phase 0 abgeschlossen | Migration durchgeführt, Flask-App read-only lassen |
| Phase 9 abgeschlossen | Alle Features in Android verfügbar, Flask-App abschalten |

---

## Cross-Cutting Concerns (kontinuierlich)

- **Fehlerbehandlung:** Netzwerkfehler sind nicht-fatal (App funktioniert offline), Room-Fehler sind fatal
- **Logging:** Timber in Debug, deaktiviert in Release
- **Versionierung:** `versionCode` aus Git-Commit-Count, `versionName` aus Tag
- **Room-Migrationen:** Jede Schema-Änderung braucht `@Migration(from, to)` – kein `fallbackToDestructiveMigration` in Produktion
- **CI:** Lint + Unit-Tests + APK-Build pro Push
