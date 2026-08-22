# Feature: Wochenplan

> **Status:** Interview offen · **Aufgaben:** 0/0 · **Stand:** 2026-08-22 · **Priorität:** ⭐⭐⭐

Dritter Bottom-Nav-Tab und Bindeglied zwischen Rezepten und Einkaufsliste.

## Umfang

| Ebene | Dateien |
|-------|---------|
| UI | `app/src/main/kotlin/com/helga/android/ui/weekplan/WeekplanScreen.kt`, `WeekplanViewModel.kt`, `WeekplanRecipePickerScreen.kt`, `WeekplanRecipePickerViewModel.kt`, `app/src/main/kotlin/com/helga/android/ui/components/MealSlot.kt` |
| Room | `app/src/main/kotlin/com/helga/android/data/local/entity/WeekplanDayEntity.kt`, `WeekplanRecipeEntity.kt`, `WeekplanExtraEntity.kt`, `WeekplanSettingsEntity.kt`, `WeekplanConstraintsEntity.kt`, `WeekplanTemplateEntity.kt`, `WeekplanTemplateEntryEntity.kt` |
| DAO | `app/src/main/kotlin/com/helga/android/data/local/dao/WeekplanDao.kt`, `WeekplanSettingsDao.kt`, `WeekplanConstraintsDao.kt`, `WeekplanTemplateDao.kt` |
| Repository | `app/src/main/kotlin/com/helga/android/data/repository/WeekplanRepository.kt`, `WeekplanTemplateRepository.kt` |
| Server | KI-Planung läuft über die allgemeinen KI-Endpunkte in `server/app/ai.py`; ein eigener `/api/weekplan/generate` wurde entfernt |

## Ist-Analyse

`WeekplanViewModel` ist mit rund 40 öffentlichen Funktionen der umfangreichste ViewModel des
Projekts:

- **Navigation:** beliebige Wochen vor und zurück (`nextWeek`, `prevWeek`, `goToCurrentWeek`,
  `weekOffset`, `weekLabel`), Zeitraum 7/10/14 Tage über die Einstellungen (`ensureWeek`,
  `saveWeekplanSettings`).
- **Belegen:** Rezepte und freie Extra-Einträge je Tag (`addRecipe`, `removeRecipe`, `addExtra`,
  `removeExtra`), Tagesnotizen (`updateNote`), Tag löschen.
- **Tagestypen:** Schnelltag und Gästetag als Marker (`toggleQuickDay`, `toggleGuestDay`).
- **Rückmeldung:** Feedback je Tag (`setFeedback`, `feedbackForSelectedDay`) — fließt in die
  KI-Planung zurück.
- **Auswertung im Plan:** `weekBalance` (Verteilung der Proteintypen) und `weekNutrition`
  (Nährwert-Trend, Ø kcal/Tag, bester Nutri-Score) direkt in der Wochenansicht.
- **KI-Planung:** `generateWeekplan` mit Constraints-Editor (`ConstraintsEditorSheet`,
  `saveConstraints`), Vorschlagsansicht mit Annehmen/Verwerfen (`ProposalSheet`,
  `applyProposal`, `discardProposal`), gezieltes Neuwürfeln einzelner Tage
  (`regenerateProposalDay`, `regenerateDay`) und Planung um gesetzte Ankerrezepte herum
  (`generateWithAnchors`).
- **Vorlagen:** aktuelle Woche als Vorlage sichern, anwenden, löschen (`saveCurrentWeekAsTemplate`,
  `applyTemplate`, `deleteTemplate`, `TemplateSheet`) sowie `repeatLastWeek`.
- **Export:** einzelner Tag oder ganze Woche in eine wählbare Einkaufsliste
  (`exportToShoppingList`, `exportWeekToShoppingList`, `ShoppingListPickerDialog`) — Zutaten
  werden dabei einheitenbewusst zusammengeführt.
- **Allergene:** `userAllergies` blendet Warnungen im Plan ein.

Der Umfang der archivierten Phase 18 ist damit vollständig umgesetzt.

## Bekannte Lücken

### Funktion & UX
Offen bis zum Interview.

### Code-Qualität
- `!!`-Zugriffe: `WeekplanScreen.kt:156` (`exportPicker`), `WeekplanViewModel.kt:531`
  (`anchorDays[day.id]!!`) — letzterer ist ein Map-Zugriff und damit ein echtes Absturzrisiko,
  falls der Schlüssel fehlt.
- `items()` ohne `key`: `WeekplanScreen.kt:563` (extraSuggestions),
  `WeekplanRecipePickerScreen.kt:194` (allTags).
- `WeekplanViewModel.kt` bündelt Planung, KI, Vorlagen, Export, Nährwerte und Allergene in einer
  Klasse. Aufteilung prüfen.

### Tests
Keine. Die Constraint-Auswertung der KI-Planung und die Aggregation in `weekBalance` /
`weekNutrition` sind reine Logik und ohne Emulator testbar.

### Sync
**Belegte Lücke.** `WeekplanTemplateEntity` und `WeekplanTemplateEntryEntity` kommen in
`app/src/main/kotlin/com/helga/android/data/remote/dto/SyncDto.kt`,
`app/src/main/kotlin/com/helga/android/data/sync/SyncEngine.kt` und
`app/src/main/kotlin/com/helga/android/data/local/dao/SyncDao.kt` **nirgends** vor. Wochenplan-
Vorlagen existieren damit nur auf dem Gerät, auf dem sie angelegt wurden: kein Abgleich mit dem
Server, kein zweites Gerät, Verlust bei Neuinstallation. Alle übrigen fünf Entities des Bereichs
(`weekplanDays`, `weekplanRecipes`, `weekplanExtras`, `weekplanSettings`, `weekplanConstraints`)
sind vollständig angebunden.

## Offene Fragen

1. Nutzt du die Vorlagen? Falls ja, ist die fehlende Synchronisierung ein Problem oder egal?
2. Wie oft trifft die KI-Planung, ohne dass du nachbessern musst — und was änderst du typisch?
3. Ankerrezepte und tageweises Neuwürfeln: bekannt und genutzt, oder zu versteckt?
4. Schnelltag und Gästetag — genügen zwei Marker, oder fehlen weitere (Resteessen, auswärts)?
5. Der Nährwert-Trend steht direkt im Plan. Nützlich beim Planen oder eher Beiwerk?
6. Sollen mehrere Mahlzeiten pro Tag planbar sein (Mittag/Abend), oder bleibt es bei einer?
7. Fehlt eine Ansicht über mehrere Wochen hinweg, oder reicht die Wochennavigation?
8. Beim Export: soll die Zielliste gefragt werden wie bisher, oder immer die Standardliste?

## Ziele

_Nach dem Interview zu füllen._

## Backlog

Aufwand: S (< 1 h) · M (halber Tag) · L (mehrere Tage)

- [ ] **A1** — `WeekplanTemplateEntity` und `WeekplanTemplateEntryEntity` an den Sync anbinden: `SyncDto`, `SyncEngine`, `SyncDao` und Serverseite in `server/app/sync.py` · L · Impact hoch
- [ ] **A2** — `anchorDays[day.id]!!` in `WeekplanViewModel.kt:531` gegen fehlenden Schlüssel absichern · S · Impact hoch
- [ ] **A3** — `!!` in `WeekplanScreen.kt:156` auflösen · S · Impact mittel
- [ ] **A4** — `key`-Parameter in `WeekplanScreen.kt:563` und `WeekplanRecipePickerScreen.kt:194` ergänzen · S · Impact mittel
- [ ] **A5** — Unit-Tests für Constraint-Auswertung und `weekBalance` · M · Impact hoch

_Weitere Aufgaben nach dem Interview._

## Entscheidungen

| Datum | Entscheidung | Begründung |
|-------|--------------|------------|
