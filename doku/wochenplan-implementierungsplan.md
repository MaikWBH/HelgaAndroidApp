# Wochenplan – Implementierungsplan (Android)

Abgleich von `doku/wochenplan_ideen.md` (Flask-Planung) mit dem aktuellen Stand der Android-App.
Ziel: Alle sinnvollen Features der Flask-Planung in die Android-App übertragen.

---

## Stand der Dinge (DB-Version 10)

### Bereits vollständig implementiert ✅

| Feature (Flask-Plan) | Android-Status |
|---|---|
| `protein_type`, `effort`, `cuisine`, `meal_type`, `season_fit` auf Rezept | ✅ `RecipeEntity`-Felder vorhanden, DB v7→8 |
| `is_favorite` | ✅ `RecipeEntity.isFavorite`, DB v7→8 |
| KI-Klassifizierung (einzeln + Bulk) | ✅ `AiClassifyScreen`, Server `POST /api/ai/classify` |
| Auto-Tag-Button auf Detailseite (nur wenn leer) | ✅ in `RecipeDetailScreen` |
| KI-Klassifikation in Generate-Prompt eingebaut | ✅ Server-seitig via JSON-LD `rocks_*`-Felder |
| Smart-Generator mit Constraints (Fleisch/Veg/Wiederholung) | ✅ `POST /api/weekplan/generate` + `WeekplanConstraintsEntity` |
| Constraints-Editor (3 Slider) | ✅ `ConstraintsEditorSheet` in WeekplanScreen |
| `recipe_history`-Tabelle + Sync | ✅ `RecipeHistoryEntity`, DB v9→10, vollständig gesynct |
| Rotations-Tracking: Generator meidet zuletzt geplante Rezepte | ✅ via `max_repeat_days`-Constraint, Server wertet History aus |
| Wochenwechsel (vor/zurück, „Heute"-Button) | ✅ `weekOffset` in `WeekplanViewModel`, Pfeile in TopAppBar |
| Wochenplan-Templates (DB-Tabellen) | ✅ `WeekplanTemplateEntity` + `WeekplanTemplateEntryEntity`, DB v8→9 |
| KI-Vorschlag-Sheet (Annehmen/Verwerfen) | ✅ Proposal-Flow im `WeekplanViewModel` |
| Rezeptbilder in Tageskarten | ✅ 48×48 px `AsyncImage` in `RecipeItemRow` |
| Tagesnotizen | ✅ `OutlinedTextField` in DayCard |
| Export Woche/Tag → Einkaufsliste | ✅ `ShoppingCart`-Buttons in TopAppBar und DayCard-Header |

---

### Teilweise implementiert / Lücken ⚠️

| Feature | Was fehlt |
|---|---|
| `recordHistory()` beim Hinzufügen eines Rezepts zum Plan | Aufruf im ViewModel vorhanden, Implementierung fehlt noch |
| `RecipeHistoryDao`: Query nach `recipeId` | Nur `dirty`/`upsert`-Methoden; kein `findByRecipeId()` → kein Lesen der History |
| Templates: kein Sync mit Server | Template-Entities haben kein `dirty`/`deleted`-Flag → werden nicht über SyncEngine übertragen |
| Rezeptbilder zu klein (48×48 px) | User-Feedback: größer gewünscht (mind. 80 dp) |
| „Vorlage"-Feature soll entfernt werden | Laut `doku/todo.md` — UI-Einstiegspunkt entfernen |
| „KI-Wochenplan erstellen"-Button soll entfernt werden | Laut `doku/todo.md` — TopAppBar-Button entfernen |

---

### Nicht implementiert / Offen ❌

| Feature (Flask-Plan) | Priorität |
|---|---|
| **#5 Anker-Auswahl** „Worauf hast du Lust?" | Mittel |
| **#8 Tages-Kontext-Flags** (schnell/Gäste) | Niedrig |
| **#9 Mini-Feedback** pro Tag (👍/👎 → Score) | Niedrig |
| **Constraint-Profile** (benannte Sätze wie „Familienwoche") | Niedrig |
| `RecipePickerScreen` als Side-Panel/Bottom-Sheet im Wochenplan | Mittel |

---

## Implementierungsplan

### Phase A – Bugfixes & Bereinigung (DB-Version bleibt 10)

#### A1 – `recordHistory()` im ViewModel vervollständigen

**Problem:** Beim Hinzufügen eines Rezepts zu einem Wochenplantag wird `recordHistory(recipeId, planDate)` aufgerufen, die Implementierung fehlt aber noch.

**Umsetzung in `WeekplanViewModel.kt`:**
```kotlin
private fun recordHistory(recipeId: String, planDate: String) {
    viewModelScope.launch(Dispatchers.IO) {
        val entry = RecipeHistoryEntity(
            id = UUID.randomUUID().toString(),
            recipeId = recipeId,
            plannedDate = planDate,
            updatedAt = System.currentTimeMillis(),
            deleted = 0,
            dirty = 1,
        )
        recipeHistoryDao.upsertAll(listOf(entry))
    }
}
```

#### A2 – `RecipeHistoryDao`: Query zum Lesen nach Zeitraum ergänzen

Damit die App (und der Smart-Generator) weiß, was in den letzten N Tagen geplant wurde:

```kotlin
@Query("SELECT * FROM recipe_history WHERE deleted=0 AND plannedDate >= :since")
fun observeSince(since: String): Flow<List<RecipeHistoryEntity>>

@Query("SELECT DISTINCT recipeId FROM recipe_history WHERE deleted=0 AND plannedDate >= :since")
suspend fun getRecentRecipeIds(since: String): List<String>
```

#### A3 – Vorlage-Feature aus WeekplanScreen entfernen

Laut `doku/todo.md`: Der `Bookmark`-Button in der TopAppBar und das Vorlagen-Sheet entfernen. DB-Tabellen (`weekplan_templates`, `weekplan_template_entries`) bleiben erhalten (kein Breaking Change, kein Migrations-Aufwand).

**Dateien:** `WeekplanScreen.kt` – `Bookmark`-IconButton und `WeekplanTemplateSheet` entfernen.

#### A4 – KI-Wochenplan-Button aus WeekplanScreen entfernen

Laut `doku/todo.md`: Den `AutoAwesome`-Button und den zugehörigen Proposal-Flow aus der UI entfernen.

**Dateien:** `WeekplanScreen.kt` – `AutoAwesome`-IconButton, `generateWeekplan()` und `ProposalSheet` entfernen. Server-Endpoint und ViewModel-Logik können bleiben.

---

### Phase B – Qualitätsverbesserungen (DB-Version bleibt 10)

#### B1 – Rezeptbilder in Tageskarten vergrößern

**Aktuell:** 48×48 dp in `RecipeItemRow`
**Ziel:** 80×80 dp (oder Thumbnail-Format: breite Karte mit Bild links)

```kotlin
// RecipeItemRow
AsyncImage(
    model = imageUrl,
    modifier = Modifier
        .size(80.dp)          // war: 48.dp
        .clip(RoundedCornerShape(8.dp)),
    ...
)
```

#### B2 – Extrazeile mit Autocomplete

Laut `doku/todo.md`: Das `OutlinedTextField` „Extrazeile hinzufügen" soll während der Eingabe Vorschläge anzeigen (Artikel + Mengenangaben).

**Umsetzung:**
- Bestehende `SuggestionsRepository`/`SuggestionsApi` wiederverwenden
- `QuantityParser` nutzen: `"500 g Mehl"` → `{qty: 500, unit: "g", name: "Mehl"}`
- Dropdown-Overlay (analog zur `QuickAddBar` in `ShoppingListScreen`) unter dem Textfeld einblenden

---

### Phase C – Anker-Auswahl „Worauf hast du Lust?" (DB-Version bleibt 10)

**Ursprung:** Flask-Plan #5 – Backend bereits vorbereitet (`anchor_ids`-Parameter in `generate_smart_plan`), UI fehlt.

**Ziel:** Beim Auslösen der KI-Generierung erscheint ein Screen/Sheet mit 6–8 zufälligen Rezeptkarten (Bild + Name). Der User wählt 1–3 als Anker; der Generator füllt die restliche Woche balanciert um diese herum.

**Umsetzung:**

1. **`WeekplanViewModel`** – neuer State + Methoden:
```kotlin
// Schritt 1: Anker-Auswahl anzeigen
val anchorCandidates: StateFlow<List<RecipeEntity>>
val selectedAnchors = MutableStateFlow<Set<String>>(emptySet())

fun loadAnchorCandidates() {
    // 8 zufällige Rezepte aus Room, die nicht in recent history sind
    viewModelScope.launch {
        val recentIds = recipeHistoryDao.getRecentRecipeIds(since = weeksAgo(4))
        _anchorCandidates.value = recipeDao.getRandomExcluding(recentIds, limit = 8)
    }
}

fun toggleAnchor(recipeId: String) {
    _selectedAnchors.update {
        if (recipeId in it) it - recipeId else if (it.size < 3) it + recipeId else it
    }
}

fun generateWithAnchors() {
    // ruft generateWeekplan(anchorIds = selectedAnchors.value) auf
}
```

2. **`RecipeDao`** – neue Query:
```kotlin
@Query("SELECT * FROM recipes WHERE deleted=0 AND id NOT IN (:excludeIds) ORDER BY RANDOM() LIMIT :limit")
suspend fun getRandomExcluding(excludeIds: List<String>, limit: Int): List<RecipeEntity>
```

3. **`WeekplanGenerateRequest`** – `anchor_ids`-Feld ergänzen (bereits serverseitig unterstützt):
```kotlin
data class WeekplanGenerateRequest(
    val start_date: String,
    val plan_days: Int,
    val max_meat_per_week: Int,
    val min_vegetarian_per_week: Int,
    val max_repeat_days: Int,
    val anchor_ids: List<String> = emptyList(),  // NEU
)
```

4. **UI – `AnchorPickerSheet`** (neues `ModalBottomSheet`):
```
┌─────────────────────────────────────────┐
│  Worauf hast du diese Woche Lust?       │
│  Wähle 1–3 Rezepte als Anker            │
│                                         │
│  [Bild] Spaghetti Bolognese   ☑         │
│  [Bild] Linsensuppe           ☐         │
│  [Bild] Hähnchen-Curry        ☑         │
│  ...                                    │
│                                         │
│  [Überspringen]    [Plan erstellen]     │
└─────────────────────────────────────────┘
```

---

### Phase D – Tages-Kontext-Flags (DB-Version 11) – Optional/Niedrige Priorität

**Ursprung:** Flask-Plan #8

**Neue Felder in `WeekplanDayEntity`:**
```kotlin
@ColumnInfo(name = "is_quick_day", defaultValue = "0")
val isQuickDay: Int = 0   // "Sport heute" → nur schnelle Rezepte

@ColumnInfo(name = "is_guest_day", defaultValue = "0")
val isGuestDay: Int = 0   // "Gäste" → aufwendigere Rezepte erlaubt
```

**DB-Migration 10→11:**
```kotlin
database.execSQL("ALTER TABLE weekplan_days ADD COLUMN is_quick_day INTEGER NOT NULL DEFAULT 0")
database.execSQL("ALTER TABLE weekplan_days ADD COLUMN is_guest_day INTEGER NOT NULL DEFAULT 0")
```

**UI:** Kleine Icon-Toggles in der DayCard-Kopfzeile (⚡ für schnell, 👥 für Gäste).

**Generator:** Server-Endpunkt nimmt `day_flags` im Request entgegen und berücksichtigt `effort`-Feld beim Zuweisen.

---

### Phase E – Mini-Feedback (DB-Version 12) – Niedrige Priorität

**Ursprung:** Flask-Plan #9

**Neue Tabelle `recipe_feedback`:**
```kotlin
@Entity(tableName = "recipe_feedback")
data class RecipeFeedbackEntity(
    @PrimaryKey val id: String,
    val recipeId: String,
    val plannedDate: String,
    val liked: Int,        // 1 = 👍, -1 = 👎, 0 = keine Meinung
    val updatedAt: Long,
    val dirty: Int = 1,
    val deleted: Int = 0,
)
```

**UI:** Am Ende einer Planwoche erscheint pro Tag eine kleine Feedback-Zeile (Daumen-Buttons). Score fließt serverseitig in die Gewichtung ein.

---

## Empfohlene Reihenfolge

| Priorität | Task | Aufwand | Wirkung |
|-----------|------|---------|---------|
| 1 | A3 – Vorlage-Button entfernen | trivial | Bereinigung |
| 2 | A4 – KI-Wochenplan-Button entfernen | trivial | Bereinigung |
| 3 | B1 – Bilder vergrößern (80 dp) | trivial | Sichtbar |
| 4 | A1 – `recordHistory()` vervollständigen | klein | Korrektheit |
| 5 | A2 – `RecipeHistoryDao` Queries ergänzen | klein | Korrektheit |
| 6 | B2 – Extrazeile mit Autocomplete | mittel | Komfort |
| 7 | C – Anker-Auswahl Sheet | mittel | Hoher Komfort |
| 8 | D – Tages-Kontext-Flags | mittel | Optional |
| 9 | E – Mini-Feedback | groß | Optional |

---

## Nicht in die Android-App zu portieren

| Feature | Begründung |
|---|---|
| **#6 Side-Panel Rezeptbibliothek** (Drag auf Datum) | Touch-UI: `ModalBottomSheet` + RecipePicker reicht; Drag&Drop komplex |
| **#7 „Vorherige Woche kopieren"** | Templates-Feature wurde bewusst entfernt (todo.md) |
| **#10 AI-Wochenplan-Assistent** (teurer LLM-Call) | Greedy-Generator deckt 80/20-Fall ab; zu kostspielig |
| **Constraint-Profile (benannte Sätze)** | Globale Constraints reichen; ein Profil-System erhöht Komplexität unnötig |
