> **ARCHIVIERT am 2026-08-22 — inhaltlich abgeschlossen.**
>
> Die Phasen 15–19 dieses Plans sind im Code umgesetzt: Freitextsuche und Favoriten (15),
> Portionsskalierung (16), Zutaten-Merge, Wischgesten und Ein-Tipp-Export (17),
> Wochennavigation und Vorlagen (18), Zutaten-Abhaken und Timer (19). Die technischen Angaben
> sind zudem überholt — der Plan rechnet mit Room-Migration v8/v9, die Datenbank steht auf v30.
>
> Aktuelle Planung: [`.claude/features/README.md`](../features/README.md)

---

# Helga Android – Verbesserungsplan (Phase 15+)

Ziel: Schnellster möglicher Workflow von „Was kochen wir diese Woche?" → Wochenplan → Einkaufsliste.
Inspiriert durch Paprika App (paprikaapp.com). Alle Features priorisiert nach User-Impact.

---

## Analyse: Was Paprika besser macht

| Paprika | Helga aktuell | Gap |
|---------|---------------|-----|
| Textsearch über Rezepte | Nur Tag-Filter + Sort | Kein Freitextsuche |
| Rezept-Skalierung (Portionen) | Fest | Kein Skalieren |
| Zutatenzusammenführung beim Export | Items werden doppelt eingefügt | Kein Konsolidieren |
| Wochenwechsel im Wochenplan | Nur aktuelle Woche | Kein Vor/Zurück |
| Wiederverwendbare Wochen-Templates | Keine | Nicht vorhanden |
| Zutaten in Kochansicht abhaken | Nicht implementiert | Kein Interaktivität |
| Timer-Erkennung in Schritten | Manuell | Nicht vorhanden |
| Favoriten-Filter | Keine | Kein Quick-Filter |
| One-Tap „Zu Einkaufsliste" | Via Menü → Dialog | 2 Schritte |

---

## Phase 15 – Rezept-Suche & Favoriten ⭐⭐⭐ (Höchste Priorität)

### Motivation
Ohne Suchfeld ist es mühsam, ein bestimmtes Rezept zu finden, sobald die Sammlung wächst.
Favoriten ermöglichen schnellen Zugriff auf Lieblingsrezepte.

### 15.1 Freitextsuche in RecipeListScreen + RecipePickerScreen

**Room DAO** – `RecipeDao.kt`:
```kotlin
@Query("SELECT * FROM recipes WHERE deleted=0 AND (name LIKE '%' || :q || '%' OR description LIKE '%' || :q || '%') ORDER BY name ASC")
fun searchByText(q: String): Flow<List<RecipeEntity>>
```

**RecipeListViewModel.kt** – neuer StateFlow:
```kotlin
val searchQuery = MutableStateFlow("")

val recipes: StateFlow<List<RecipeEntity>> = combine(
    recipeDao.observeAll(), searchQuery, selectedTag, sortOrder
) { all, query, tag, sort ->
    all.filter { r ->
        (query.isBlank() || r.name.contains(query, ignoreCase = true))
        && (tag == null || /* tag match */)
    }.sortedBy { ... }
}.stateIn(...)
```

**RecipeListScreen.kt** – SearchBar unter TopAppBar:
```kotlin
// Material3 SearchBar (collapsed → expanded on focus)
// Zeigt "🔍 Rezepte suchen…" placeholder
// onQueryChange → viewModel.searchQuery.value = it
```

Gleiche Änderungen für `WeekplanRecipePickerViewModel` + `WeekplanRecipePickerScreen`.

### 15.2 Favoriten-Toggle + Schnellfilter

`RecipeEntity` hat bereits ein `rating`-Feld (1–5 Sterne). Ein Favorit = Rating ≥ 4.

**Alternative:** separates Boolean-Feld `isFavorite` in `RecipeEntity` (DB-Migration v8 nötig):
```kotlin
@ColumnInfo(name = "is_favorite", defaultValue = "0")
val isFavorite: Int = 0
```

**RecipeListScreen** – FilterChip neben Tags:
```kotlin
FilterChip(
    selected = showFavoritesOnly,
    onClick = { viewModel.toggleFavoritesFilter() },
    label = { Text("★ Favoriten") },
    leadingIcon = { Icon(Icons.Filled.Star, null) },
)
```

**RecipeDetailScreen** – Icon in TopAppBar zum Favorit-Toggle:
```kotlin
IconButton(onClick = { viewModel.toggleFavorite() }) {
    Icon(
        if (recipe.isFavorite == 1) Icons.Filled.Star else Icons.Outlined.StarOutline,
        tint = if (recipe.isFavorite == 1) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        contentDescription = "Favorit",
    )
}
```

**Wichtig:** DB-Migration v8 + SyncDto anpassen (`is_favorite` Feld im Push/Pull).

---

## Phase 16 – Rezept-Skalierung ⭐⭐⭐

### Motivation
Portionen anpassen ist Alltagsbedarf (z.B. für 2 statt 4 Personen kochen).
Paprika skaliert alle Zutatenmengen automatisch.

### Implementierung

**Nur UI-State** – kein DB-Schreiben beim Skalieren:

**RecipeDetailViewModel.kt**:
```kotlin
// Basis: recipe.yieldAmount (z.B. 4.0)
private val _servings = MutableStateFlow(0)  // 0 = noch nicht geladen
val servings: StateFlow<Int> = _servings

fun setServings(n: Int) { _servings.value = n.coerceIn(1, 99) }

fun scaledAmount(original: Double, baseServings: Int): Double {
    val factor = if (baseServings > 0) _servings.value.toDouble() / baseServings else 1.0
    return original * factor
}
```

**RecipeDetailScreen** – Portionen-Row:
```kotlin
Row(verticalAlignment = Alignment.CenterVertically) {
    Text("${servings}x Portionen", style = titleMedium)
    Spacer(Modifier.weight(1f))
    IconButton(onClick = { vm.setServings(servings - 1) }) { Icon(Icons.Filled.Remove, null) }
    IconButton(onClick = { vm.setServings(servings + 1) }) { Icon(Icons.Filled.Add, null) }
}
```

Ingredient-Row zeigt `"${scaledAmount(ingredient.amount, baseServings)} ${ingredient.unit}"`.

---

## Phase 17 – Einkaufslisten-Optimierung ⭐⭐⭐

### 17.1 Zutatenzusammenführung beim Wochenplan-Export

**Aktuell:** Jede Zutat wird als separates Item eingefügt → doppelte Einträge bei gleichen Zutaten.

**Ziel:** Beim Export-Dialog „Woche auf Einkaufsliste" werden Zutaten mit gleichem Namen und gleicher Einheit zusammengefasst:
- 200g Mehl + 300g Mehl → 500g Mehl
- 2 Eier + 3 Eier → 5 Eier

**WeekplanRepository.kt** – `exportToShoppingList()` anpassen:
```kotlin
// Statt direktem upsertItem: Zutaten zuerst aggregieren
data class AggIngredient(val name: String, val unit: String, var qty: Double, val aisle: String)

val aggregated = mutableMapOf<String, AggIngredient>()
ingredients.forEach { ing ->
    val key = "${ing.name.lowercase()}|${ing.unit.lowercase()}"
    aggregated.getOrPut(key) { AggIngredient(ing.name, ing.unit, 0.0, ing.aisle) }.qty += ing.quantity
}
aggregated.values.forEach { agg ->
    shoppingRepository.addOrMergeItem(listId, agg.name, agg.qty, agg.unit, agg.aisle)
}
```

**ShoppingRepository.kt** – neue Methode `addOrMergeItem()`:
```kotlin
suspend fun addOrMergeItem(listId: String, name: String, qty: Double, unit: String, aisle: String) {
    val existing = shoppingDao.findActiveItem(listId, name, unit)  // neuer DAO-Query
    if (existing != null) {
        shoppingDao.upsertItem(existing.copy(quantity = existing.quantity + qty, updatedAt = now, dirty = 1))
    } else {
        addItem(listId, name, qty, unit, aisle)
    }
}
```

### 17.2 Swipe-to-Check in Einkaufsliste

Material3 `SwipeToDismissBox` – Swipe rechts = abhaken, Swipe links = löschen:

```kotlin
SwipeToDismissBox(
    state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> { vm.toggleChecked(item); false }  // false = kein Entfernen
                SwipeToDismissBoxValue.EndToStart -> { vm.deleteItem(item); true }
                else -> false
            }
        }
    ),
    backgroundContent = { SwipeBg(dismissState) },
    content = { ShoppingItemRow(item) },
)
```

### 17.3 One-Tap „Zu Standardliste hinzufügen" in RecipeDetail

**RecipeDetailScreen** – zweiter FAB (Extended) in der Bottom-Action-Bar:
```kotlin
ExtendedFloatingActionButton(
    onClick = { vm.addToDefaultShoppingList() },
    icon = { Icon(Icons.Filled.ShoppingCart, null) },
    text = { Text("Einkaufsliste") },
)
```

**RecipeDetailViewModel** – neues `addToDefaultShoppingList()`:
```kotlin
fun addToDefaultShoppingList() {
    viewModelScope.launch {
        val listId = preferences.defaultShoppingListId.first()
            ?: shoppingDao.lists().firstOrNull()?.id ?: return@launch
        recipeRepository.exportToShoppingList(recipe.value?.id ?: return@launch, listId)
        syncScheduler.triggerOneShot()
        _snackbarMessage.value = "Zur Einkaufsliste hinzugefügt"
    }
}
```

---

## Phase 18 – Wochenplan Power-Features ⭐⭐

### 18.1 Wochenwechsel (Vor/Zurück navigieren)

**Aktuell:** Immer aktuelle Woche. Kein Navigieren in Zukunft/Vergangenheit.

**WeekplanViewModel.kt**:
```kotlin
private val _weekOffset = MutableStateFlow(0)  // 0 = aktuelle Woche, +1 = nächste, -1 = letzte
val weekOffset: StateFlow<Int> = _weekOffset

fun nextWeek() { _weekOffset.value++ }
fun prevWeek() { _weekOffset.value-- }
fun goToCurrentWeek() { _weekOffset.value = 0 }

fun ensureWeek() {
    val monday = LocalDate.now()
        .with(DayOfWeek.MONDAY)
        .plusWeeks(_weekOffset.value.toLong())
    // ... bestehende Logik mit neuem Startdatum
}
```

**WeekplanScreen** – TopAppBar mit Pfeil-Navigation:
```kotlin
TopAppBar(
    title = {
        Text("KW ${weekNumber} · ${mondayDate.format("dd.MM.")}–${sundayDate.format("dd.MM.")}")
    },
    navigationIcon = {
        IconButton(onClick = vm::prevWeek) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
    },
    actions = {
        IconButton(onClick = vm::nextWeek) { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) }
        if (weekOffset != 0) {
            TextButton(onClick = vm::goToCurrentWeek) { Text("Heute") }
        }
        // ... bestehende KI + Constraint Icons
    }
)
```

### 18.2 Wochenplan-Vorlagen (Templates)

**Ziel:** Aktuelle Woche als Vorlage speichern → nächste Woche schnell befüllen.

**DB:** Neue Tabelle `weekplan_templates` + `weekplan_template_entries` (DB-Migration v9):
```kotlin
@Entity(tableName = "weekplan_templates")
data class WeekplanTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val updatedAt: Long,
    val dirty: Int = 1,
    val deleted: Int = 0,
)

@Entity(tableName = "weekplan_template_entries")
data class WeekplanTemplateEntryEntity(
    @PrimaryKey val id: String,
    val templateId: String,
    val dayOffset: Int,        // 0=Mo, 1=Di, ..., 6=So
    val recipeId: String,
    val updatedAt: Long,
    val dirty: Int = 1,
    val deleted: Int = 0,
)
```

**WeekplanViewModel** – neue Methoden:
```kotlin
fun saveAsTemplate(name: String) { /* speichert aktuelle Woche als Template */ }
fun applyTemplate(templateId: String) { /* befüllt aktuelle/ausgewählte Woche */ }
```

**WeekplanScreen** – Menü-Item in TopAppBar-Actions:
```kotlin
DropdownMenuItem(text = { Text("Als Vorlage speichern") }, ...)
DropdownMenuItem(text = { Text("Vorlage laden") }, ...)
```

---

## Phase 19 – Kochansicht verbessern ⭐⭐

### 19.1 Zutaten in Kochansicht abhaken

**Aktuell:** `RecipeCookScreen` zeigt nur Schritte. Zutaten sind nicht sichtbar.

**Neues Tab-Layout in RecipeCookScreen:**
```
[ Zutaten | Schritte ]
```

**Zutaten-Tab:**
- Zeigt alle Zutaten mit Checkbox
- State nur im ViewModel (kein DB-Write)
- Abgehakte Zutaten werden durchgestrichen

```kotlin
// RecipeCookViewModel
val checkedIngredients = MutableStateFlow<Set<String>>(emptySet())  // IDs

fun toggleIngredient(id: String) {
    _checkedIngredients.update { if (id in it) it - id else it + id }
}
```

### 19.2 Timer-Erkennung in Schritten

Regex erkennt Zeitangaben in Schritttext und zeigt einen „Timer starten"-Button:

```kotlin
// Pattern: "15 Minuten", "30 min", "1 Stunde", "2h"
val TIME_REGEX = Regex("""(\d+)\s*(Minuten?|min|Stunden?|h)\b""", RegexOption.IGNORE_CASE)

@Composable
fun StepText(text: String, onStartTimer: (seconds: Int) -> Unit) {
    val matches = TIME_REGEX.findAll(text)
    // AnnotatedString mit klickbaren Timer-Spans
    // Klick → Timer-Dialog → Android CountDownTimer / AlarmManager
}
```

---

## Implementierungsreihenfolge (empfohlen)

| Priorität | Phase | Aufwand | Impact |
|-----------|-------|---------|--------|
| 1 | 15.1 Freitextsuche | klein | hoch |
| 2 | 17.1 Zutatenzusammenführung | mittel | hoch |
| 3 | 17.2 Swipe-to-Check | klein | mittel |
| 4 | 17.3 One-Tap Einkaufsliste | klein | hoch |
| 5 | 16 Rezept-Skalierung | mittel | mittel |
| 6 | 15.2 Favoriten | mittel | mittel |
| 7 | 18.1 Wochenwechsel | mittel | hoch |
| 8 | 18.2 Templates | groß | hoch |
| 9 | 19.1 Zutaten abhaken | klein | mittel |
| 10 | 19.2 Timer | mittel | niedrig |

---

## Kern-Workflow nach allen Phasen

```
1. Rezepte-Tab → Suche "Pasta" → Favoriten-Filter → Detail ansehen
2. RecipeDetail → Skalieren auf 2 Portionen → "Zu Einkaufsliste" (1 Tap)

ODER für Wochenplan:
1. Wochenplan-Tab → Woche navigieren (KW21)
2. Tag antippen → RecipePicker (mit Suche) → Rezept wählen
3. "Woche auf Einkaufsliste" → Zutaten automatisch konsolidiert
4. Einkaufsliste → Items per Swipe abhaken beim Einkauf
5. Nächste Woche: Vorlage laden → sofort fertig
```
