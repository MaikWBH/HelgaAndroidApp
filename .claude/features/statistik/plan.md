# Feature: Statistik

> **Status:** Interview erledigt · **Aufgaben:** 2 offen (1 erledigt) · **Stand:** 2026-08-30 · **Priorität:** ⭐

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
- **Root Cause zu „Screen nicht erreichbar" (bestätigt vor dem Interview):** `StatsScreen` hat
  keine Route in `HelgaNavGraph.kt` und wird sonst nirgends im Code referenziert
  (`grep -r StatsScreen app/` → ein einziger Treffer, die Definition selbst). Der Screen lässt
  sich im aktuellen Build nicht öffnen — kein Bottom-Nav-Fehlen, sondern vollständig
  unverdrahteter Code.

### Code-Qualität
Keine `!!`-Zugriffe, keine `items()`-Verstöße in diesem Bereich.

### Tests
Keine. Die Aggregation ist reine Logik über eine Liste und damit gut testbar.

### Sync
Keine eigenen Entities; `recipeHistory` wird im Rezepte-Bereich gesynct.

## Fragen

1. **Screen erreichbar machen oder ist Statistik verzichtbar?**
   Antwort: Erreichbar machen — Richtung: über den neuen Bons-Tab, der ohnehin für den
   Bon-Scan geplant ist (siehe [bons-kosten](../bons-kosten/plan.md) A3). Ein Tab könnte damit
   beides bedienen: Bons scannen und Statistik einsehen, statt einen fünften Bottom-Nav-Eintrag
   zu schaffen.
2. **Fehlt ein Zeitraumfilter wie beim Ausgabenüberblick?**
   Antwort: Ja, wäre sinnvoll.
3. **Welche Frage soll die Statistik primär beantworten?**
   Antwort: Wie ausgewogen war der Zeitraum — die vorhandenen Felder (`meatCount`, `fishCount`,
   `vegCount`, `otherCount`) passen dazu, brauchen nur die Zeitraum-Eingrenzung aus Frage 2.
4. **Sollen Kosten und Nährwerte hier mit einfließen?**
   Antwort: Getrennt lassen — eigene Themen, eigene Ansichten bleiben sinnvoll.
5. **„Lange nicht gekocht" als Wochenplan-Vorschlag statt reiner Anzeige?**
   Antwort: Reine Anzeige reicht, keine aktive Vorschlagsfunktion.

## Ziele

- Statistik-Screen erreichbar machen — voraussichtlich über den neuen Bons-Tab statt einem
  eigenen Bottom-Nav-Eintrag, siehe [bons-kosten](../bons-kosten/plan.md) A3.
- Zeitraumfilter ergänzen, damit die Ausgewogenheits-Auswertung (Protein-/Kategorienverteilung)
  sich auf einen sinnvollen Zeitraum statt den Gesamtbestand bezieht.
- Kosten und Nährwerte bleiben in ihren eigenen Ansichten — keine Zusammenführung.
- Keine aktive Vorschlagsfunktion für den Wochenplan — Statistik bleibt reine Anzeige.

## Backlog

Aufwand: S (< 1 h) · M (halber Tag) · L (mehrere Tage)

- [ ] **A1** — Unit-Tests für die Aggregationslogik · S · Impact mittel
- [x] **A2** — Statistik-Screen erreichbar machen: keine Route in `HelgaNavGraph.kt`, `StatsScreen`
  komplett unverdrahtet. Richtung: in den neuen Bons-Tab integrieren statt eigenem
  Bottom-Nav-Eintrag — bei Umsetzung mit [bons-kosten](../bons-kosten/plan.md) A3 zusammen
  planen · M · Impact hoch — **umgesetzt:** `ROUTE_STATS` neu angelegt, erreichbar über einen
  Insights-Icon-Button in der TopBar von `ReceiptListScreen` (dem neuen Bons-Tab), `onBack`
  führt zurück dorthin
- [ ] **A3** — Zeitraumfilter ergänzen, analog zum Ausgabenüberblick
  (`CostOverviewViewModel.setPeriod`-Muster wiederverwenden) · M · Impact hoch

_Weitere Aufgaben nach dem Interview._

## Entscheidungen

| Datum | Entscheidung | Begründung |
|-------|--------------|------------|
| 2026-08-30 | Statistik wird über den neuen Bons-Tab erreichbar gemacht, nicht über einen eigenen Bottom-Nav-Eintrag | Nutzerwunsch, ein Tab bedient Bon-Scan und Statistik gemeinsam statt einen fünften Tab zu schaffen |
| 2026-08-30 | Zeitraumfilter wird ergänzt | Ohne Zeitbezug sind reine Gesamtzahlen wenig aussagekräftig |
| 2026-08-30 | Kosten/Nährwerte bleiben getrennt von der Statistik | Unterschiedliche Themen, getrennte Ansichten gewünscht |
| 2026-08-30 | Keine Vorschlagsfunktion „lange nicht gekocht" im Wochenplan | Reine Anzeige reicht aus |
