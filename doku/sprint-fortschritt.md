# Sprint-Fortschritt – 28. Mai 2026

## Zusammenfassung der erledigten Arbeiten

### Sprint 1: Kritische Bugs

#### 1. Tab-Wechsel → schwarzer Bildschirm (BEHOBEN)
**Ursache:** `SharedTransitionLayout` um den gesamten `NavHost` verursachte Konflikte mit `saveState`/`restoreState` der Bottom Navigation. Beim Wechsel vom Rezepte-Tab (der Shared Element Transitions nutzte) auf andere Tabs blieb der Bildschirm schwarz.

**Fix:** `SharedTransitionLayout` komplett entfernt. Die `sharedTransitionScope` und `animatedVisibilityScope` Parameter aus `RecipeListScreen`, `RecipeDetailScreen` und `HelgaNavGraph` entfernt. Shared Element Animations zwischen Rezeptliste und Detail geopfert zugunsten stabiler Navigation.

**Dateien:** `HelgaNavGraph.kt`, `RecipeListScreen.kt`, `RecipeDetailScreen.kt`

#### 2. Rezeptbilder werden nicht angezeigt (BEHOBEN)
**Ursache:** Coil's Standard-`ImageLoader` hat keinen `X-Api-Key` Header. Der Server-Endpunkt `/api/images/{filename}` verlangt Authentifizierung → alle Bildanfragen bekamen HTTP 401.

**Fix:** `HelgaApp` implementiert nun `ImageLoaderFactory` und liefert einen `ImageLoader` mit einem OkHttp-Interceptor, der den API-Key aus `AppPreferences` in jede Anfrage einfügt.

**Dateien:** `HelgaApp.kt`

---

### Sprint 2: Quick UI-Fixes

#### 3. Rezeptname Marquee in Listenansicht
Lange Rezeptnamen in `RecipeRow` werden jetzt mit `maxLines = 1` + `Modifier.basicMarquee()` als Lauftext dargestellt.

#### 4. "Nicht zugewiesen" Standardgang
Einkaufsartikel ohne Gang-Zuweisung bekommen jetzt den Header "Ohne Gang" (aus `strings.xml: shopping_no_aisle`). Vorher wurden sie ohne Überschrift gerendert.

#### 5+6. Vorlage-Feature + KI-Wochenplan-Button entfernt
- `TemplateSheet` und zugehöriger Overflow-Menüpunkt entfernt
- `generateWeekplan()`-IconButton aus der TopAppBar entfernt
- Zugehörige State-Variablen (`templateSheetVisible`, `templates`) entfernt

#### 7. Autocomplete im Vorrat-Dialog
`PantryViewModel` hat jetzt eine `suggestItems()` Methode (nutzt `/api/suggestions/items`). Der `AddPantryDialog` zeigt nach 2 Zeichen Eingabe `SuggestionChip`s in einer `LazyRow` an.

#### 8. Zutaten-Ansicht optisch aufwerten
`IngredientRow` hat jetzt mehr horizontales Padding (24dp), `verticalAlignment = CenterVertically`, feste Mindestbreite (72dp) für die Mengenspalte, und zeigt "•" statt leerem Raum wenn keine Menge vorhanden ist.

#### 9. Rezept-Metadaten als Emoji
War bereits implementiert — `MetadataSection` nutzt `FlowRow` mit `SuggestionChip`s (🍽️ Portionen, ⏱️ Zeit, 🌍 Küche, etc.)

#### 10. Feedback-Buttons Touch-Targets
`Modifier.size(40.dp)` von den 👍/👎 `IconButton`s entfernt. `IconButton` nutzt jetzt sein Default-Minimum von 48dp.

---

### Sprint 3: Komfort-Features

#### 11. Portionszahl beim Export
- `ShoppingListPickerDialog` hat jetzt einen Portionen-Stepper (1–12)
- `WeekplanViewModel.exportToShoppingList()` akzeptiert `servings` Parameter
- `WeekplanRepository.exportToShoppingList()` berechnet `scale = desiredServings / recipeYield` und multipliziert Zutatmengen proportional

#### 14. Unklassifizierte-Rezepte Warnung
In `RecipeRow` wird ein ⚠️ Badge neben dem Rezeptnamen angezeigt, wenn alle 5 Klassifizierungsfelder (`proteinType`, `effort`, `cuisine`, `mealType`, `seasonFit`) leer sind.

---

## Detaillierter Plan für verbleibende Features

### 12. AnchorPicker-Sheet UI

**Ziel:** Beim Generieren eines Wochenplans soll der User 1–3 Rezepte als "Anker" auswählen können. Der Generator füllt die restlichen Tage um diese Anker herum.

**Implementierungsschritte:**

1. **`RecipeDao` erweitern** — Query `getRandomExcluding()` prüfen (existiert bereits)

2. **`WeekplanViewModel` erweitern:**
   - Neuer State `anchorPickerVisible: MutableStateFlow<Boolean>`
   - `anchorCandidates: StateFlow<List<RecipeEntity>>` — 8 zufällige Rezepte, die nicht in den letzten 4 Wochen geplant waren
   - `selectedAnchors: MutableStateFlow<Set<String>>` — max. 3 ausgewählte IDs
   - `fun loadAnchorCandidates()` — befüllt Kandidaten aus Room
   - `fun toggleAnchor(recipeId: String)` — toggelt Auswahl (max. 3)
   - `fun generateWithAnchors()` — ruft `generateWeekplan()` auf, setzt gewählte Anker auf feste Tage

3. **`AnchorPickerSheet` Composable (neues BottomSheet in WeekplanScreen.kt):**
   ```
   ┌─────────────────────────────────────────┐
   │  Worauf hast du diese Woche Lust?       │
   │  Wähle 1–3 Rezepte als Anker            │
   │                                         │
   │  [Bild] Spaghetti Bolognese   ☑         │
   │  [Bild] Linsensuppe           ☐         │
   │  [Bild] Hähnchen-Curry        ☑         │
   │  ... (8 Karten)                         │
   │                                         │
   │  [Überspringen]    [Plan erstellen]     │
   └─────────────────────────────────────────┘
   ```

4. **Trigger:** Neuer Overflow-Menüpunkt "Mit Favoriten generieren" oder Long-Press auf den bestehenden Generieren-Mechanismus

5. **Generator-Logik anpassen:** `generateWeekplan()` erhält `anchorIds: List<String>`. Anker werden auf zufällige Tage verteilt, restliche Tage wie bisher generiert (Constraints, Diversity, etc. bleiben bestehen)

**Aufwand:** ~2–3 Stunden  
**Dateien:** `WeekplanScreen.kt`, `WeekplanViewModel.kt`

---

### 13. Reste-Verwertung im Generator

**Ziel:** Wenn ein Rezept ≥6 Portionen hat, soll der Folgetag als "leicht/schnell" markiert oder übersprungen werden.

**Implementierungsschritte:**

1. **In `WeekplanViewModel.generateWeekplan()`** (lokaler Smart-Generator):
   - Nach Zuweisung eines Rezepts: `recipeYield` parsen
   - Wenn `yield >= 6`: nächsten Tag als "Quick-Day" behandeln
   - Quick-Day: nur Rezepte mit `effort = "schnell"` oder `totalTime < 30min` erlauben
   - Optional: Folgetag ganz leer lassen mit Notiz "Reste von gestern"

2. **Neuer Helper:**
   ```kotlin
   private fun parseYield(recipe: RecipeEntity): Int =
       Regex("""\d+""").find(recipe.recipeYield)?.value?.toIntOrNull() ?: 0
   ```

3. **Integration in die Generator-Schleife:**
   ```kotlin
   for (i in dayIndices) {
       if (previousDayLargePortions) {
           // Nur schnelle Rezepte oder "Reste"-Eintrag
           val quickPool = candidates.filter { it.effort.lowercase() in listOf("schnell", "quick") }
           // ... zuweisen
       } else {
           // Normal generieren
       }
   }
   ```

**Aufwand:** ~1 Stunde  
**Dateien:** `WeekplanViewModel.kt`

---

### 15. Wochen-Statistik Dashboard

**Ziel:** Neuer Screen (erreichbar über Settings) mit Überblick über langfristige Ernährungsmuster.

**Implementierungsschritte:**

1. **Neuer Screen `StatsScreen.kt`** (existiert bereits, ggf. erweitern):
   - Letzte 4 Wochen: Fleisch/Fisch/Veg-Verteilung als horizontales Balkendiagramm
   - Top-5 meistgekochte Rezepte (aus `recipe_history`)
   - "Vergessene Schätze" — Rezepte die >30 Tage nicht gekocht wurden
   - Durchschnittliche Aufwands-Verteilung (schnell/mittel/aufwendig)

2. **`StatsViewModel` erweitern:**
   - `data class WeekStats(val meat: Int, val fish: Int, val veg: Int, val other: Int)`
   - `val last4Weeks: StateFlow<List<WeekStats>>` — aus `recipe_history` + `RecipeEntity.proteinType`
   - `val topRecipes: StateFlow<List<Pair<RecipeEntity, Int>>>` — Häufigkeits-Ranking
   - `val forgotten: StateFlow<List<RecipeEntity>>` — isFavorite=1 aber lange nicht geplant

3. **DAO-Queries:**
   ```kotlin
   // RecipeHistoryDao
   @Query("SELECT recipeId, COUNT(*) as cnt FROM recipe_history WHERE deleted=0 AND plannedDate >= :since GROUP BY recipeId ORDER BY cnt DESC LIMIT 5")
   suspend fun topRecipes(since: String): List<RecipeCount>
   
   // RecipeDao
   @Query("SELECT * FROM recipes WHERE deleted=0 AND is_favorite=1 AND id NOT IN (SELECT recipeId FROM recipe_history WHERE plannedDate >= :since)")
   suspend fun forgottenFavorites(since: String): List<RecipeEntity>
   ```

4. **UI-Komponenten:**
   - `ProteinBarChart` — Horizontale Balken mit Farben (Rot=Fleisch, Blau=Fisch, Grün=Veg)
   - `TopRecipesList` — LazyColumn mit Ranking-Nummer + Bild + Name + Count
   - `ForgottenSection` — Horizontal scrollbare Rezept-Karten

**Aufwand:** ~4–5 Stunden  
**Dateien:** `StatsScreen.kt`, `StatsViewModel.kt`, `RecipeHistoryDao.kt`, `RecipeDao.kt`

---

### 16. Saisonaler Zutaten-Kalender

**Ziel:** Generator bevorzugt Rezepte deren Hauptzutaten gerade Saison haben (feiner als `seasonFit` auf Rezeptebene).

**Implementierungsschritte:**

1. **Statische Datenquelle erstellen** — `SeasonalData.kt`:
   ```kotlin
   object SeasonalData {
       val byMonth: Map<Int, Set<String>> = mapOf(
           1 to setOf("grünkohl", "rosenkohl", "feldsalat", "rote bete", "pastinake"),
           2 to setOf("chicorée", "lauch", "sellerie", "schwarzwurzel"),
           3 to setOf("bärlauch", "spinat", "rhabarber"),
           4 to setOf("spargel", "radieschen", "rucola", "kohlrabi"),
           5 to setOf("spargel", "erdbeeren", "rhabarber", "radieschen"),
           6 to setOf("kirschen", "zucchini", "erbsen", "blaubeeren", "gurke"),
           7 to setOf("tomaten", "paprika", "bohnen", "himbeeren", "pfifferlinge"),
           8 to setOf("mais", "aubergine", "pflaume", "brombeeren", "mirabelle"),
           9 to setOf("kürbis", "weintrauben", "zwetschge", "birne", "pilze"),
           10 to setOf("kürbis", "apfel", "maronen", "walnüsse", "quitten"),
           11 to setOf("grünkohl", "rosenkohl", "feldsalat", "steckrübe"),
           12 to setOf("rotkohl", "wirsing", "maronen", "orangen", "zimt"),
       )
       
       fun seasonalIngredients(): Set<String> = byMonth[java.time.LocalDate.now().monthValue] ?: emptySet()
   }
   ```

2. **Generator-Integration** in `WeekplanViewModel.generateWeekplan()`:
   ```kotlin
   val seasonal = SeasonalData.seasonalIngredients()
   
   // Beim Scoring der Kandidaten:
   val seasonalBonus = if (seasonal.any { s -> 
       ingredients.any { it.food.lowercase().contains(s) } 
   }) 1 else 0
   
   val score = feedbackScore + favoriteBonus + seasonalBonus
   ```

3. **Optional: UI-Hinweis** in `MetadataSection`:
   - Wenn ein Rezept saisonale Zutaten enthält: 🌱 Badge ("Saisonal")
   - Kleine Info in der Wochenplan-Bilanz-Zeile

**Aufwand:** ~2 Stunden  
**Dateien:** `SeasonalData.kt` (neu), `WeekplanViewModel.kt`, optional `RecipeDetailScreen.kt`

---

## Empfohlene Reihenfolge

| Priorität | Feature | Aufwand | Impact |
|-----------|---------|---------|--------|
| 1 | Reste-Verwertung (#13) | 1h | hoch — realistischere Pläne |
| 2 | AnchorPicker-Sheet (#12) | 2–3h | hoch — bester Workflow |
| 3 | Saisonaler Zutaten-Kalender (#16) | 2h | mittel — gesündere Pläne |
| 4 | Wochen-Statistik Dashboard (#15) | 4–5h | niedrig — nice-to-have |
