# Feature: Rezepte

> **Status:** Interview offen · **Aufgaben:** 0/0 · **Stand:** 2026-08-22 · **Priorität:** ⭐⭐⭐

Zweiter Bottom-Nav-Tab. Datenbasis für Wochenplan und Einkaufsliste.

## Umfang

| Ebene | Dateien |
|-------|---------|
| UI | `app/src/main/kotlin/com/helga/android/ui/recipes/` — `RecipeListScreen.kt`, `RecipeDetailScreen.kt`, `RecipeFormScreen.kt`, `RecipeCookScreen.kt`, `UrlImportScreen.kt` je mit ViewModel |
| Room | `app/src/main/kotlin/com/helga/android/data/local/entity/RecipeEntity.kt`, `IngredientEntity.kt`, `InstructionEntity.kt`, `TagEntity.kt`, `CategoryEntity.kt`, `RecipeHistoryEntity.kt`, `RecipeFeedbackEntity.kt` |
| DAO | `app/src/main/kotlin/com/helga/android/data/local/dao/RecipeDao.kt`, `RecipeHistoryDao.kt`, `RecipeFeedbackDao.kt` |
| Repository | `app/src/main/kotlin/com/helga/android/data/repository/RecipeRepository.kt` |
| Hilfsklassen | `app/src/main/kotlin/com/helga/android/data/util/IngredientLineParser.kt`, `app/src/main/kotlin/com/helga/android/data/model/RecipeNutrition.kt` |
| Server | `/api/ai/import-url` in `server/app/main.py`, Parsing in `server/app/ingredient_parser.py` |

## Ist-Analyse

- **Liste** (`RecipeListViewModel`): Freitextsuche (`setSearchQuery`), Favoritenfilter
  (`toggleFavoritesFilter`), Tag-Filter (`selectTag`, `toggleTag`, `clearTags`,
  `TagFilterDialog`), Sortierung (`setSortOrder`), KI-Massenklassifikation (`classifyBatch`,
  `BulkClassifyDialog`). Zeigt zusätzlich das für heute geplante Rezept aus dem Wochenplan an.
- **Detail** (`RecipeDetailViewModel`, 382 Zeilen): Portionsskalierung (`setServings`,
  `parseServings`), Favoriten-Toggle, Bewertung (`setRating`), persönliche Notizen
  (`savePersonalNotes`), Teilen (`shareRecipe`), Löschen, KI-Klassifikation (`classify`),
  Nährwerte per KI oder manuell (`calculateNutritionWithAi`, `saveManualNutrition`),
  Ein-Tipp-Export zur Standardliste (`addToDefaultShoppingList`) und gezielter Export
  (`exportToShoppingList`), Eintragen in einen Wochenplantag inklusive Wochennavigation
  (`addToWeekplanDay`, `nextWeek`, `prevWeek`, `goToCurrentWeek`).
- **Formular** (`RecipeFormScreen`): Anlegen und Bearbeiten, dynamische Zutaten- und
  Schrittlisten, Bildauswahl, clientseitige UUID.
- **Kochansicht** (`RecipeCookViewModel`): Zutaten und Schritte abhakbar (`toggleIngredient`,
  `toggleStep`), Portionsskalierung, Timer aus Schritttexten (`activeTimer` in
  `RecipeCookScreen.kt`), Kochbestätigung schreibt in die Historie (`confirmCooked`), plus
  Fokusansicht mit Wischnavigation.
- **URL-Import** (`UrlImportScreen`): Import über den Server, dazu lokaler JSON-LD-Parser
  (`app/src/main/kotlin/com/helga/android/ui/ai/RecipeJsonLdParser.kt`); Android-Share-Target
  nimmt URLs aus dem Browser entgegen.
- **Historie und Feedback:** `RecipeHistoryEntity` protokolliert, was wann gekocht wurde;
  `RecipeFeedbackEntity` hält die Bewertung je Kochvorgang. Beide speisen Statistik und
  KI-Wochenplanung.

Damit ist der gesamte Umfang der archivierten Phasen 15, 16 und 19 aus
[`improvement_plan.md`](../../archiv/improvement_plan.md) umgesetzt.

## Bekannte Lücken

### Funktion & UX
Offen bis zum Interview. Auffällig: `RecipeListScreen.kt` hat keine eigene SearchBar-Composable
— die Suche hängt in `FilterBar`. Ob das im Alltag gut auffindbar ist, klärt Frage 1.

**Rotationsbug betrifft auch die Kochansicht.** Aus dem Einkaufslisten-Interview: Eine
Bildschirmdrehung während des Kochens (`RecipeCookScreen`) wirft unvermittelt zurück in die
Einkaufsliste. Ursache und Fix liegen in
[einkaufsliste/plan.md](../einkaufsliste/plan.md) A4 bzw. [plattform/plan.md](../plattform/plan.md).

### Code-Qualität
- `!!`-Zugriffe: `RecipeCookScreen.kt:165,166,170` auf `activeTimer`,
  `RecipeListScreen.kt:220,240` auf `todayRecipe` — fünf der zehn Projektfälle liegen hier.
- `items()` ohne `key`: `RecipeListScreen.kt:504` (allTags), `:569` (unclassifiedRecipes).
- `RecipeDetailViewModel.kt` bündelt mit 382 Zeilen Rezeptdetails, Nährwerte, Einkaufsliste und
  Wochenplannavigation. Kandidat für Aufteilung.

### Tests
Keine. `IngredientLineParser` und `RecipeJsonLdParser` sind reine Logik und ohne Emulator
testbar — die Schrittparsing-Regeln des Timers ebenso.

### Sync
`recipes`, `ingredients`, `instructions`, `tags`, `recipeTags`, `recipeCategories`,
`availableTags` laufen über `SyncDao`. `recipeHistory` und `recipeFeedback` werden in
`SyncEngine` abweichend über die eigenen DAOs (`RecipeHistoryDao`, `RecipeFeedbackDao`)
behandelt statt über `SyncDao` — funktioniert, bricht aber das Muster aus
[sync-patterns](../../guidelines/sync-patterns.md). Vereinheitlichen oder bewusst dokumentieren.

## Offene Fragen

1. Findest du ein Rezept über die Suche schnell genug, oder ist das Suchfeld zu versteckt?
2. Wonach suchst du in der Praxis — Name, Zutat, Tag? Aktuell greift die Suche nur auf Name und
   Beschreibung.
3. Die Bewertung existiert doppelt: Sterne am Rezept und Feedback je Kochvorgang. Ist das
   gewollt oder verwirrend?
4. Kochansicht: Reicht die Fokusansicht, oder fehlt etwas beim tatsächlichen Kochen
   (Bildschirm anlassen, größere Schrift, Sprachsteuerung)?
5. Timer werden aus dem Schritttext erkannt. Wie zuverlässig trifft das, und sollen mehrere
   Timer parallel laufen können?
6. Beim URL-Import: Welche Seiten scheitern regelmäßig?
7. Sollen Rezepte in Sammlungen oder Kochbücher gruppierbar sein, oder reichen Tags?
8. Die Skalierung ist reiner UI-Zustand und wird nicht gespeichert. Soll eine geänderte
   Portionszahl beim nächsten Öffnen erhalten bleiben?

## Ziele

_Nach dem Interview zu füllen._

## Backlog

Aufwand: S (< 1 h) · M (halber Tag) · L (mehrere Tage)

- [ ] **A1** — Fünf `!!`-Zugriffe in `RecipeCookScreen.kt` und `RecipeListScreen.kt` auflösen · S · Impact mittel
- [ ] **A2** — `key`-Parameter in `RecipeListScreen.kt:504,569` ergänzen · S · Impact mittel
- [ ] **A3** — Unit-Tests für `RecipeJsonLdParser` und die Timer-Erkennung · M · Impact hoch
- [ ] **A4** — `recipeHistory`/`recipeFeedback` im Sync auf das `SyncDao`-Muster vereinheitlichen oder Abweichung dokumentieren · M · Impact niedrig

_Weitere Aufgaben nach dem Interview._

## Entscheidungen

| Datum | Entscheidung | Begründung |
|-------|--------------|------------|
