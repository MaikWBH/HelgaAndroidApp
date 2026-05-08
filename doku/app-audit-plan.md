# App-Audit-Plan: Bugs, UI-Konsistenz & Performance

## Übersicht

Umfassende Prüfung der Helga Android App ergab:
- **1 kritischer Kompilierungsfehler** (SettingsViewModel)
- **3 funktionale Bugs** (Loading-Overlay, noteText-Verlust, fehlende Annotations)
- **5 UI-Konsistenzprobleme** (Touch-Targets, Button-Sichtbarkeit, Generate-Button)
- **2 Performance-Verbesserungen** (allRecipes Map, flatten-Memoization)

---

## Phase 1: Kritische Bugs

### 1.1 – SettingsViewModel: Doppelter Code-Block (KRITISCH)

**Datei:** `app/src/main/kotlin/com/helga/android/ui/settings/SettingsViewModel.kt` (Zeile ~110)

**Problem:**
Nach der `setCheckMode()`-Methode befindet sich ein verwaister Code-Block:
```kotlin
viewModelScope.launch { preferences.saveAccentColor(index) }
}
```
Dieser ist Überbleibsel vom Einfügen der `setCheckMode()`-Methode und führt zu Kompilierungsfehlern.

**Fix:** Die zwei verwaisten Zeilen (Zeile ~110-111) löschen.

**Impact:** Kritisch – App kompiliert nicht

---

### 1.2 – WeekplanScreen: Loading-Overlay blockiert nicht die Interaktion

**Datei:** `app/src/main/kotlin/com/helga/android/ui/weekplan/WeekplanScreen.kt` (Zeile ~196)

**Problem:**
Die Loading-Box und die LazyColumn sind Geschwister-Composables. Während KI-Generierung:
- Der Ladeindikator wird angezeigt
- Die LazyColumn dahinter bleibt sichtbar und interaktiv
- User kann weiterhin Items verschieben/löschen während Generation läuft

**Fix:**
Option A: LazyColumn während Loading ausblenden via `if (!isLoading) { LazyColumn(...) }`  
Option B: Loading-Box mit halbtransparentem Scrim + `Modifier.clickable { }` zum Blockieren

**Impact:** Hoch – Nutzer können während laufender Operation Daten ändern → Konsistenzprobleme

---

### 1.3 – RecipeFeedbackEntity / RecipeHistoryEntity: Fehlende @Immutable Annotation

**Dateien:**
- `app/src/main/kotlin/com/helga/android/data/local/entity/RecipeFeedbackEntity.kt`
- `app/src/main/kotlin/com/helga/android/data/local/entity/RecipeHistoryEntity.kt`

**Problem:**
Alle anderen Entity-Klassen tragen `@Immutable` Annotation, diese beiden nicht. Führt zu unnötigen Recompositions in Compose-UI, da Compose nicht erkennen kann, dass sich die Klassen nicht ändern.

**Fix:** `@Immutable` Annotation + erforderlichen Import hinzufügen

**Impact:** Mittel – Performance-Degradation bei vielen Recompositions

---

### 1.4 – DayCard: noteText wird nicht gespeichert bei Fokusverlust

**Datei:** `app/src/main/kotlin/com/helga/android/ui/weekplan/WeekplanScreen.kt` (DayCard Composable, ~Zeile 414)

**Problem:**
`noteText` wird nur bei IME-Aktion `Done` (Keyboard-Return) gespeichert via `KeyboardActions(onDone = { ... })`. Wenn der User:
- Einfach einen anderen Tag auswählt
- Zu einem anderen Tab wechselt
- Die App zurück ins Hintergrund geht

...geht die Änderung verloren, da kein Speicher-Event auf Fokusverlust existiert.

**Fix:** 
```kotlin
DisposableEffect(noteState) {
    onDispose {
        // noteState.value in Day speichern
        viewModel.updateDayNote(day.id, noteState.value)
    }
}
```

**Impact:** Hoch – Datenverlust aus Nutzer-Perspektive

---

## Phase 2: UI-Konsistenz

### 2.1 – WeekplanScreen: Feedback-Buttons (👍/👎) mit zu kleinen Touch-Targets

**Datei:** `app/src/main/kotlin/com/helga/android/ui/weekplan/WeekplanScreen.kt` (RecipeItemRow, ~Zeile 552)

**Problem:**
Die Feedback-Buttons nutzen:
```kotlin
Text("👍", Modifier.clickable { ... }.padding(4.dp))
```
Das ergibt Touch-Targets von ~20dp. Material 3 Richtlinie: mindestens 48dp × 48dp.

**Fix:**
```kotlin
IconButton(onClick = { ... }, modifier = Modifier.size(40.dp)) {
    Text("👍", fontSize = 20.sp)
}
```
Oder direktes `Box` mit 48dp:
```kotlin
Box(Modifier.size(48.dp).clickable { ... }, contentAlignment = Alignment.Center) {
    Text("👍", fontSize = 20.sp)
}
```

**Impact:** Mittel – Accessibility Issue, schwer zu treffen auf Touch-Geräten

---

### 2.2 – RecipeDetailScreen: TopAppBar mit zu vielen sichtbaren Buttons

**Datei:** `app/src/main/kotlin/com/helga/android/ui/recipes/RecipeDetailScreen.kt` (TopAppBar, ~Zeile 163)

**Problem:**
Aktuell sichtbare Actions in der TopAppBar:
1. Star (Favorit)
2. MenuBook (Kochen)
3. Edit (Bearbeiten)
4. Delete (Löschen)
5. MoreVert (Overflow)

Auf kleineren Bildschirmen wird der Titel der Recipe dadurch stark eingeengt oder ganz verdrängt.

**Fix:**
- Sichtbar bleiben: Star, Cook, MoreVert
- Ins Overflow-Menü verschieben: Edit, Delete

```kotlin
TopAppBar(
    title = { ... },
    actions = {
        IconButton(...) { /* Star */ }
        IconButton(...) { /* Cook */ }
        DropdownMenu {
            DropdownMenuItem(text = "Bearbeiten", ...)
            DropdownMenuItem(text = "Löschen", ...)
        }
    }
)
```

**Impact:** Mittel – UI-Ästhetik, Lesbarkeit

---

### 2.3 – WeekplanScreen: Generate-Button fehlt im TopAppBar

**Datei:** `app/src/main/kotlin/com/helga/android/ui/weekplan/WeekplanScreen.kt` (TopAppBar, ~Zeile 180)

**Problem:**
Der `viewModel.openAnchorPicker()`-Aufruf existiert im ViewModel, aber **es gibt keinen UI-Button** der diese Methode aufruft. Der User kann KI-Generierung nur via Proposal-Sheet oder Anchor-Picker öffnen, aber nicht direkt von der TopAppBar aus.

**Fix:** IconButton in der TopAppBar hinzufügen:
```kotlin
TopAppBar(
    actions = {
        IconButton(onClick = { viewModel.openAnchorPicker() }) {
            Icon(Icons.Filled.AutoAwesome, "Generieren")
        }
        IconButton(onClick = { /* ... Tune */ }) { ... }
        IconButton(onClick = { /* ... Shopping */ }) { ... }
    }
)
```

**Impact:** Hoch – Feature ist nicht intuitiv auffindbar

---

## Phase 3: Performance

### 3.1 – WeekplanViewModel: `allRecipes` als komplette Liste (O(n) Lookup)

**Datei:** `app/src/main/kotlin/com/helga/android/ui/weekplan/WeekplanViewModel.kt`

**Problem:**
```kotlin
val allRecipes: StateFlow<List<RecipeEntity>> = recipeDao.observeAll()
```

Für jeden Rezept-ID Lookup in `recipeById(id)` wird linear die gesamte Liste durchsucht:
```kotlin
fun recipeById(id: String) = allRecipes.value.find { it.id == id }  // O(n)
```

Bei 500+ Rezepten und 30+ Items pro Wochenplan sind das 1500+ lineare Durchläufe.

**Fix:** Map statt List cachen
```kotlin
val allRecipesMap: StateFlow<Map<String, RecipeEntity>> = 
    recipeDao.observeAll().map { recipes -> recipes.associateBy { it.id } }

fun recipeById(id: String) = allRecipesMap.value[id]  // O(1)
```

**Impact:** Mittel – Performance bei großen Rezept-Beständen

---

### 3.2 – ShoppingListScreen: `flatten()` wird bei jedem Recompose neu berechnet

**Datei:** `app/src/main/kotlin/com/helga/android/ui/shopping/ShoppingListScreen.kt`

**Problem:**
Im MOVE-Modus wird im LazyColumn-Body berechnet:
```kotlin
val allItems = itemsByAisle.values.flatten()  // Recomposition?
```

Diese List-Allokation passiert potentiell bei jeder Recomposition.

**Fix:** Mit `remember` memoizen
```kotlin
val allItems = remember(itemsByAisle) {
    itemsByAisle.values.flatten()
}
```

**Impact:** Niedrig – Nur relevant bei vielen Recompositions

---

## Phase 4: Kleinigkeiten

### 4.1 – AnchorPickerSheet: "Überspringen" Label ist unklar

**Datei:** `app/src/main/kotlin/com/helga/android/ui/weekplan/WeekplanScreen.kt` (AnchorPickerSheet)

**Problem:**
Der Skip-Button trägt das Label "Überspringen", macht aber eigentlich "Ohne Anker generieren". Die Semantik ist nicht offensichtlich.

**Fix:** Button-Label ändern zu:
```kotlin
TextButton(onClick = { onGenerate(emptyList()) }) {
    Text("Ohne Anker generieren")
}
```

**Impact:** Niedrig – UX/Klarheit

---

## Zusammenfassung der Schritte

| Priorität | # | Aktion | Datei |
|-----------|---|--------|-------|
| **KRITISCH** | 1.1 | SettingsViewModel doppelter Code löschen | SettingsViewModel.kt |
| **HOCH** | 1.2 | Loading-Overlay mit Scrim/Block | WeekplanScreen.kt |
| **HOCH** | 1.4 | noteText bei Dekomposition speichern | WeekplanScreen.kt |
| **HOCH** | 2.3 | Generate-Button im TopAppBar | WeekplanScreen.kt |
| **MITTEL** | 1.3 | @Immutable auf 2 Entities | RecipeFeedbackEntity.kt, RecipeHistoryEntity.kt |
| **MITTEL** | 2.1 | Feedback-Buttons Touch-Target | WeekplanScreen.kt |
| **MITTEL** | 2.2 | RecipeDetail TopAppBar verschlanken | RecipeDetailScreen.kt |
| **MITTEL** | 3.1 | allRecipes als Map cachen | WeekplanViewModel.kt |
| **NIEDRIG** | 3.2 | flatten() mit remember wrappen | ShoppingListScreen.kt |
| **NIEDRIG** | 4.1 | AnchorPicker Skip-Label verdeutlichen | WeekplanScreen.kt |

---

## Verifikation nach Fixes

1. **Kompilierung:**
   ```bash
   ./gradlew assembleDebug
   ```
   Muss ohne Fehler durchlaufen (spätestens nach Fix 1.1)

2. **Unit-Tests:**
   ```bash
   ./gradlew test
   ```
   Alle bestehenden Tests müssen grün bleiben

3. **Manuelle Tests:**
   - [ ] Tab-Wechsel ohne Datenverlust in Notizen
   - [ ] KI-Generierung: Während Loading sind keine Interaktionen möglich
   - [ ] Feedback-Buttons: Touch-Ziel gut erreichbar
   - [ ] Generate-Button in Wochenplan-TopAppBar funktioniert
   - [ ] RecipeDetail TopAppBar: Titel gut lesbar
   - [ ] AnchorPicker Skip-Button generiert ohne Anker

---

## Entscheidungen (bewusst nicht geändert)

- **Herkunfts-Tag (Micro-Badge):** Bewusste Designentscheidung mit `background() + RoundedCornerShape()` statt Material 3 Badge → bleibt so
- **forEach in DayCard statt LazyColumn:** Akzeptabel da maximal 7 Items pro Tag + äußere LazyColumn virtualisiert → keine Änderung nötig
- **Kontext-Flags-Styling:** `IconButton` mit Text-Emoji ist konsistent mit Feedback-Buttons → bleibt

---

## Status

**Stand:** Audit-Plan erstellt, Implementation ausstehend  
**Scope:** Nur oben gelistete Punkte, keine neuen Features  
**Geschätzte Dauer:** 2–3 Stunden für alle Fixes + Verifikation
