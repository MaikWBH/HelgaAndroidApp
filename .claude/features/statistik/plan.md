# Feature: Statistik

> **Status:** Interview offen · **Aufgaben:** 0/0 · **Stand:** 2026-08-22 · **Priorität:** ⭐

Auswertung der Kochhistorie. Der kleinste Bereich der App.

## Umfang

| Ebene | Dateien |
|-------|---------|
| UI | `app/src/main/kotlin/com/helga/android/ui/stats/StatsScreen.kt`, `StatsViewModel.kt` |
| Datenquelle | `app/src/main/kotlin/com/helga/android/data/local/entity/RecipeHistoryEntity.kt` über `app/src/main/kotlin/com/helga/android/data/local/dao/RecipeHistoryDao.kt` |

## Ist-Analyse

`StatsViewModel` stellt ausschließlich lesende Flows bereit, keine Aktionen:

- `totalCooked` — Gesamtzahl der Kochvorgänge
- `meatCount`, `fishCount`, `vegCount`, `otherCount` — Verteilung nach Proteintyp
- `topRecipes` — meistgekochte Rezepte
- `firstTimers` — erstmals gekochte Rezepte
- `stats` — zusammengefasster Zustand für den Screen

Die Daten stammen vollständig aus `RecipeHistoryEntity`, das beim Bestätigen in der Kochansicht
(`confirmCooked`) geschrieben wird. Ohne bestätigte Kochvorgänge bleibt der Screen leer.

## Bekannte Lücken

### Funktion & UX
- Kein Zeitraumfilter — die Zahlen sind immer der Gesamtbestand. Anders als der
  Ausgabenüberblick, der `setPeriod` anbietet.
- Der Screen ist über keinen Bottom-Nav-Tab erreichbar; Einstiegspunkt im Interview zu klären.

### Code-Qualität
Keine `!!`-Zugriffe, keine `items()`-Verstöße in diesem Bereich.

### Tests
Keine. Die Aggregation ist reine Logik über eine Liste und damit gut testbar.

### Sync
Keine eigenen Entities; `recipeHistory` wird im Rezepte-Bereich gesynct.

## Offene Fragen

1. Öffnest du den Screen überhaupt? Falls nein: entfernen oder sichtbarer machen?
2. Fehlt ein Zeitraumfilter wie beim Ausgabenüberblick?
3. Welche Frage soll die Statistik beantworten — „was koche ich zu oft", „was habe ich lange
   nicht gekocht", „wie ausgewogen war der Monat"?
4. Sollen Kosten aus den Bons und Nährwerte hier zusammenlaufen, statt in drei Ansichten zu
   liegen?
5. Wäre eine Auswertung „lange nicht gekocht" als Vorschlagsquelle für die Wochenplanung
   nützlicher als die reine Anzeige?

## Ziele

_Nach dem Interview zu füllen._

## Backlog

Aufwand: S (< 1 h) · M (halber Tag) · L (mehrere Tage)

- [ ] **A1** — Unit-Tests für die Aggregationslogik · S · Impact mittel

_Weitere Aufgaben nach dem Interview._

## Entscheidungen

| Datum | Entscheidung | Begründung |
|-------|--------------|------------|
