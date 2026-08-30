# Feature: Wochenplan

> **Status:** Interview erledigt (8/8) · **Aufgaben:** 14 offen (1 erledigt) · **Stand:** 2026-08-22 · **Priorität:** ⭐⭐⭐

Dritter Bottom-Nav-Tab und Bindeglied zwischen Rezepten und Einkaufsliste.

## Umfang

| Ebene | Dateien |
|-------|---------|
| UI | `app/src/main/kotlin/com/helga/android/ui/weekplan/WeekplanScreen.kt`, `WeekplanViewModel.kt`, `WeekplanRecipePickerScreen.kt`, `WeekplanRecipePickerViewModel.kt`, `app/src/main/kotlin/com/helga/android/ui/components/MealSlot.kt` |
| Room | `app/src/main/kotlin/com/helga/android/data/local/entity/WeekplanDayEntity.kt`, `WeekplanRecipeEntity.kt`, `WeekplanExtraEntity.kt`, `WeekplanSettingsEntity.kt`, `WeekplanConstraintsEntity.kt`, `WeekplanTemplateEntity.kt`, `WeekplanTemplateEntryEntity.kt` |
| DAO | `app/src/main/kotlin/com/helga/android/data/local/dao/WeekplanDao.kt`, `WeekplanSettingsDao.kt`, `WeekplanConstraintsDao.kt`, `WeekplanTemplateDao.kt` |
| Repository | `app/src/main/kotlin/com/helga/android/data/repository/WeekplanRepository.kt`, `WeekplanTemplateRepository.kt` |
| Server | **Keine Server-Beteiligung an der Planung selbst** — `generateWeekplan()` ist vollständig lokal (siehe Ist-Analyse); `server/app/ai.py` fließt nur indirekt über die Rezept-Klassifikation (`proteinType`/`effort`/`mealSlot`) ein; ein eigener `/api/weekplan/generate` wurde entfernt |

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
- **Lokale Planung (keine KI — siehe „Bugs" unten):** `generateWeekplan` mit Constraints-Editor
  (`ConstraintsEditorSheet`, `saveConstraints`), Vorschlagsansicht mit Annehmen/Verwerfen
  (`ProposalSheet`, `applyProposal`, `discardProposal`), gezieltes Neuwürfeln einzelner Tage
  (`regenerateProposalDay`, `regenerateDay`) und Planung um gesetzte Ankerrezepte herum
  (`generateWithAnchors`). Trotz Namens und Icon-Framing läuft hier kein Server- oder KI-Aufruf:
  `apiFactory` wird im gesamten `WeekplanViewModel.kt` genau einmal verwendet
  (`suggestItems` für Extra-Item-Vorschläge, Zeile 888), nicht für die Planung. Die komplette
  Logik ist deterministisches Kotlin — Filterung nach `mealSlot`, Allergenen, Kcal-Budget,
  Nutri-Score, Saison; Balancierung nach Proteintyp/Aufwand entlang der Constraints; Scoring
  nach Feedback, Favorit- und Bio-Bonus; zufällige Wahl aus den Top 40 % der Kandidaten
  (`WeekplanViewModel.kt:402` ff.). Passt zum Commit „Toten KI-Wochenplan-Endpunkt entfernen"
  (`807a50e`), der die serverseitige LLM-Anbindung bereits vor dieser Session entfernt hat.
- **Vorlagen (toter Code, siehe „Bugs"):** `saveCurrentWeekAsTemplate`, `applyTemplate`,
  `deleteTemplate`, `TemplateSheet` existieren vollständig, sind aber an keiner Stelle im UI
  erreichbar. Erreichbar ist nur `repeatLastWeek` über das Überlaufmenü.
- **Export:** einzelner Tag oder ganze Woche in eine wählbare Einkaufsliste
  (`exportToShoppingList`, `exportWeekToShoppingList`, `ShoppingListPickerDialog`) — Zutaten
  werden dabei einheitenbewusst zusammengeführt. Berücksichtigt nur Rezept-Zutaten; freie
  Extra-Einträge (`WeekplanExtraEntity`) werden dabei **nicht** mit exportiert (siehe „Bugs").
- **Allergene:** `userAllergies` blendet Warnungen im Plan ein.

Der Umfang der archivierten Phase 18 ist damit vollständig umgesetzt.

## Bekannte Lücken

### Bugs

**„KI-Wochenplan" ist irreführend beschriftet — kein Code-Fehler, nur Doku/String.** Die
Bezeichnung „KI-Wochenplan erstellen" (`strings.xml:258`, `weekplan_ai_generate`) ist ein
**totes String-Resource**, im Code nirgends referenziert. Der tatsächliche Button-Text ist
bereits korrekt „Plan generieren" (`weekplan_generate`, `strings.xml:274`), kein `AutoAwesome`-
Icon im aktuellen `WeekplanScreen.kt`. Die „KI"-Rahmung existiert nur noch in
`development_plan.md` Phase 10 und existierte bis eben auch in der Ist-Analyse hier. Siehe
Backlog A9.

**Süßspeisen als Abendessen-Vorschlag.** Zwei Faktoren zusammen: (1) Der
Klassifikations-Prompt erzwingt genau vier `meal_slot`-Werte ohne „dessert"-Option
(`server/app/ai.py:46` — `["breakfast", "lunch", "dinner", "snack"]`); `RecipeEntity.mealSlot`
(`RecipeEntity.kt:36`) hat den lokalen Default `"other"`, den jedes nie (re-)klassifizierte
Rezept behält. (2) Der Kandidatenfilter in `generateWeekplan()`
(`WeekplanViewModel.kt:445-451`, Kommentar „mealSlot-Filter: Keine Frühstück/Snack ins
Dinner-Rezept platzieren") schließt nur `breakfast` und `snack` aus — `"other"` passiert
unverändert und ist für jeden Tag wählbar, auch Abendessen. Siehe Backlog A6.

**`regenerateDay()` respektiert weniger Constraints als `generateWeekplan()`.** Vergleich:

| Filter | `generateWeekplan()` | `regenerateDay()` |
|---|---|---|
| mealSlot (kein Frühstück/Snack) | ✅ Zeile 445-451 | ❌ fehlt |
| Allergene | ✅ Zeile 453-461 | ❌ fehlt |
| Kcal-Budget | ✅ Zeile 464-467 | ❌ fehlt |
| Nutri-Score | ✅ Zeile 469-473 | ❌ fehlt |
| Saison | ✅ Zeile 475-482 | ❌ fehlt |
| Fleisch/Fisch/Vegetarisch-Balance | ✅ | ✅ Zeile 742-749 |
| Schnell-/Gästetag-Aufwand | ✅ | ✅ Zeile 753-762 |

`regenerateDay()` (Zeile 710-767) übernimmt nur Protein- und Aufwandsbalance. Ein Neuwürfeln
kann also z. B. ein Frühstücksrezept, ein allergenbelastetes Rezept oder eines über dem
Kcal-Budget vorschlagen — Verhalten, das die Erstplanung längst ausschließt. Siehe Backlog A7.

**Ankerrezepte sind fertig gebaut, aber im UI unerreichbar.** `generateWithAnchors()`
(`WeekplanViewModel.kt:869`) wird von keiner Stelle in `WeekplanScreen.kt` aufgerufen — echter
toter Code, nicht nur „versteckt". Nur `generateWeekplan()` (ohne Anker) hängt an einem Button
(`WeekplanScreen.kt:195`). Siehe Backlog A10.

Kurz recherchiert (Anfrage „meal planning app UX pattern regenerate day lock recipe weekly plan
generator 2026"): Tage sperren + einzeln neu würfeln direkt im Kalender statt in einem
Extra-Dialog ist 2026 verbreiteter Standard bei Meal-Planning-Generatoren („lock meals you like
and regenerate the rest", Zuweisung per Ein-Tipp aus der Tageskarte). Deckt sich mit dem
unverdrahteten Ankerrezepte-Mechanismus — die Datenbasis existiert bereits.

**Wochenplan-Vorlagen sind komplett unerreichbar — nicht nur ein einzelner Menüpunkt fehlt.**
Verifiziert: `viewModel.saveCurrentWeekAsTemplate`, `applyTemplate`, `deleteTemplate` und
`templates` haben **je null Aufrufe** irgendwo im UI-Code. `TemplateSheet`
(`WeekplanScreen.kt:1216`) wird nie aufgerufen. Das Überlaufmenü hat genau einen Eintrag
(„Letzte Woche wiederholen"). Zehn Vorlagen-Strings (`strings.xml:255-285`), zwei Room-Entities,
ein eigenes Repository (`WeekplanTemplateRepository.kt`) und ein DAO existieren — für ein
Feature, das kein Nutzer je zu Gesicht bekommt. Erklärt die Antwort zu Frage 1 vollständig
(unten). Entscheidung: ersatzlos entfernen statt fertigzubauen, siehe Backlog A1 (ersetzt).

**Extra-Einträge fehlen beim Export in die Einkaufsliste.** `WeekplanRepository.kt:94-113`
(`exportToShoppingList`) liest ausschließlich `weekplanDao.recipesForDay(dayId)` — freie
Extra-Einträge (`WeekplanExtraEntity`, z. B. „Brot besorgen") werden nie mitgenommen, weder bei
Einzeltag- noch bei Wochen-Export. Siehe Backlog A12.

**Die 7/10/14-Tage-Einstellung wirkt nie auf die Anzeige.** `days`-StateFlow
(`WeekplanViewModel.kt:98-105`) ist hart auf `monday..monday+6` (immer exakt 7 Tage) codiert.
`preferences.weekplanDays` wird nur beim Anlegen der Tage (`ensureWeek()`, Zeile 234) und beim
Zuschneiden der KI-Kandidaten (`generateWeekplan()`, Zeile 407) gelesen, nie bei der Anzeige.
Wer 10 oder 14 Tage einstellt, sieht trotzdem nur 7 — Tage 8-14 werden in der DB angelegt, aber
nie sichtbar oder über die Wochennavigation erreichbar. Eigenständiger Bestandsbug, gefunden
beim Entwurf von A15, dessen Fix (dynamisches Zeitfenster) diesen Bug mitbehebt.

### Funktion & UX
Größter offener Punkt aus dem Interview: die KI-Planung schlägt oft ungeeignete Rezepte vor
(Süßspeisen als Abendessen, siehe „Bugs"); Constraints-Dialog soll größer und direkter ins
Wochenplan-UI eingreifen statt als separates Sheet (Backlog A8). Export soll vor dem Übernehmen
eine abwählbare Produktvorschau zeigen (Backlog A13). Mehrere Rezepte pro Mahlzeit mit dezenter
farblicher Unterscheidung gewünscht (Backlog A14).

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
`WeekplanTemplateEntity` und `WeekplanTemplateEntryEntity` kommen in `SyncDto.kt`, `SyncEngine.kt`
und `SyncDao.kt` nirgends vor — ursprünglich als Lücke geführt, mittlerweile gegenstandslos: das
Vorlagen-Feature selbst ist unerreichbarer Code (siehe „Bugs") und wird auf Nutzerwunsch entfernt
statt angebunden (Backlog A1). Alle übrigen fünf Entities des Bereichs (`weekplanDays`,
`weekplanRecipes`, `weekplanExtras`, `weekplanSettings`, `weekplanConstraints`) sind vollständig
angebunden.

## Offene Fragen

**Aus dem Rezepte-Interview:** Der Wunsch, die Rezeptsuche nach Tag und Zutat („Reis, Nudeln,
vegetarisch") mit der automatischen Wochenplan-Erstellung zu verbinden — also den Plan gezielt
aus einer Zutaten- oder Tag-Auswahl heraus generieren zu lassen, statt nur über die bestehenden
Constraints. Als Ausgangspunkt für Frage 2 und 3 vermerkt, hier zu entscheiden.

1. Nutzt du die Vorlagen? Falls ja, ist die fehlende Synchronisierung ein Problem oder egal?

   **Zunächst missverstanden, dann aufgelöst.** Die Frage war unscharf gestellt — „Vorlagen"
   wurde gefragt, gemeint waren die synclosen `WeekplanTemplateEntity`/`WeekplanTemplateEntryEntity`.
   Die erste Antwort bezog sich stattdessen auf die **Constraints** (Einstell-Optionen der
   Planung: max. Fleisch, min. Vegetarisch etc.) — ein anderes Feature, siehe unten. In der
   zweiten Runde direkt nachgefragt und geklärt: Beim Nachgehen zeigte sich, dass die Vorlagen
   im UI **komplett unerreichbar** sind (siehe „Bekannte Lücken → Bugs") — die Antwort „weiß
   nicht was gemeint ist" erklärt sich damit von selbst. **Entscheidung: ersatzlos entfernen**,
   siehe Backlog A1 (ersetzt die ursprüngliche Sync-Aufgabe).

   **Antwort zu den Constraints (statt Vorlagen):** Werden **selten genutzt** — mögliche
   Vereinfachung oder Verschiebung in die Einstellungen. Die automatische Planerstellung selbst
   wird dagegen **häufig genutzt**. Kritikpunkte: oft ungeeignete Vorschläge (Süßspeisen als
   Abendessen — siehe „Bugs", Backlog A6); Zweifel, ob Neuwürfeln die Constraints einhält
   (bestätigt, siehe „Bugs", Backlog A7); der Dialog soll größer sein und direkter ins
   Wochenplan-UI eingreifen statt als separates Sheet — Vorschlag: ein „Plan Mode", der freie
   Tage plant und pro Tag Neuwürfeln-Buttons anbietet (Backlog A8, siehe Recherche unter
   „Bugs"). Ausdrücklich gewünscht: möglichst keine KI/Tokens für den Wochenplan — bereits
   erfüllt, siehe „Bugs" oben (Backlog A9 für die Doku-Korrektur).

2. Wie oft trifft die KI-Planung, ohne dass du nachbessern musst — und was änderst du typisch?

   **Antwort:** Nacharbeit gilt als normal und erwartet — außer bei Süßspeisen, die für normale
   Tage als Abendessen vorgeschlagen werden und nicht zum Familienessen passen, oder wenn das
   gewählte Rezept nicht dem aktuellen Geschmack entspricht. Das Süßspeisen-Problem wird als
   echter Fehler benannt (siehe „Bugs", Backlog A6).

3. Ankerrezepte und tageweises Neuwürfeln: bekannt und genutzt, oder zu versteckt?

   **Antwort:** Ja, wäre nützlich. (Fund beim Nachgehen: nicht nur „versteckt" — die Funktion
   ist im UI gar nicht verdrahtet, siehe „Bugs", Backlog A10.)

4. Schnelltag und Gästetag — genügen zwei Marker, oder fehlen weitere (Resteessen, auswärts)?

   **Antwort:** „Auswärts/kein Kochen" fehlt, **und** der Wunsch nach frei anlegbaren eigenen
   Markern — mehr als ein drittes festes Flag. Braucht eine Datenmodell-Entscheidung (drittes
   Boolean-Feld vs. Tag-artiges System) vor der Umsetzung, siehe Backlog A11.

5. Der Nährwert-Trend steht direkt im Plan. Nützlich beim Planen oder eher Beiwerk?

   **Antwort:** Beiwerk, stört aber nicht. Bleibt unverändert, keine Aufgabe.

6. Sollen mehrere Mahlzeiten pro Tag planbar sein (Mittag/Abend), oder bleibt es bei einer?

   **Antwort:** Mehrere Rezepte pro Mahlzeit sollen möglich sein (z. B. mehrere Gerichte an
   einem Abend), mit einer Art Tag zur Unterscheidung der Mahlzeit — dezente farbliche
   Hervorhebung je Mahlzeitentyp gewünscht (z. B. Frühstück grün). Fund beim Nachgehen: Mehrere
   Rezepte pro Tag sind technisch schon möglich (`WeekplanRecipeEntity.position`), nur ohne
   Mahlzeiten-Unterscheidung. Siehe Backlog A14.

7. Fehlt eine Ansicht über mehrere Wochen hinweg, oder reicht die Wochennavigation?

   **Antwort:** Nicht gestellt — Fragenkontingent durch die ergiebigen Antworten zu 1, 6 und 8
   ausgeschöpft. Für eine Anschlussrunde vormerken.

8. Beim Export: soll die Zielliste gefragt werden wie bisher, oder immer die Standardliste?

   **Antwort:** Direkt die Standardliste vorschlagen statt neutral zu starten — aber vor dem
   Übernehmen alle Produkte einmal in einer Vorschau zeigen und einzeln abwählbar machen (z. B.
   Salz, das meist schon vorhanden ist). Nachträglich präzisiert: die Listen-Rückfrage soll
   trotzdem bestehen bleiben, nur mit der Standardliste vorbelegt statt neutral. Zusätzlich
   bestätigt: Extra-Einträge sollen ebenfalls in der Vorschau/Liste auftauchen (siehe „Bugs",
   Backlog A12). Siehe Backlog A13.

## Ziele

- „KI-Wochenplan"-Framing entfernt (Doku + totes String), Feature bleibt lokal wie es ist.
- Süßspeisen tauchen nicht mehr als Abendessen-Vorschlag auf.
- Neuwürfeln respektiert dieselben Regeln wie die Erstplanung.
- Ankerrezepte sind im UI erreichbar — Sperren und Tages-Reroll direkt in der Tageskarte.
- Ein dritter/frei definierbarer Tagesmarker ist verfügbar.
- Vorlagen-Feature ist vollständig entfernt statt halb fertig im Code zu liegen.
- Export nimmt Extra-Einträge mit; Standardliste ist vorbelegt, Produkte einzeln abwählbar vor
  der Übernahme.
- Mehrere Rezepte pro Mahlzeit möglich, dezent farblich nach Mahlzeitentyp unterschieden.
- Eine einzelne Woche lässt sich schnell und einmalig kürzen (Tage als „kein Kochen" markieren)
  oder verlängern, ohne die globale 7/10/14-Tage-Einstellung zu ändern.

_Ziel zu Frage 7 (Mehrwochen-Ansicht) nach der Anschlussrunde._

## Backlog

Aufwand: S (< 1 h) · M (halber Tag) · L (mehrere Tage)

- [ ] **A1** — Vorlagen-Feature ersatzlos ausbauen (Nutzerentscheidung, nicht sync-fähig machen):
      `WeekplanTemplateEntity`, `WeekplanTemplateEntryEntity`, `WeekplanTemplateDao`,
      `WeekplanTemplateRepository.kt`, `TemplateSheet` in `WeekplanScreen.kt`, zehn Strings
      (`strings.xml:255-285`), Room-Migration zum sauberen Entfernen der Tabellen · M · Impact
      niedrig
- [ ] **A2** — `anchorDays[day.id]!!` in `WeekplanViewModel.kt:531` gegen fehlenden Schlüssel absichern · S · Impact hoch
- [ ] **A3** — `!!` in `WeekplanScreen.kt:156` auflösen · S · Impact mittel
- [ ] **A4** — `key`-Parameter in `WeekplanScreen.kt:563` und `WeekplanRecipePickerScreen.kt:194` ergänzen · S · Impact mittel
- [ ] **A5** — Unit-Tests für Constraint-Auswertung und `weekBalance` · M · Impact hoch
- [ ] **A6** — mealSlot-Filter in `generateWeekplan()` von „nicht breakfast/snack" auf
      „ausschließlich lunch/dinner" verschärfen ODER `meal_slot` im Klassifikations-Prompt
      (`server/app/ai.py:46`) um `"dessert"` erweitern und im Filter ausschließen; Default
      `"other"` bei nie klassifizierten Rezepten ebenfalls ausschließen · M · Impact hoch
- [ ] **A7** — `regenerateDay()` auf dieselben fünf Filter wie `generateWeekplan()` bringen
      (mealSlot, Allergene, Kcal, Nutri-Score, Saison) — gemeinsame Filterfunktion extrahieren
      statt duplizieren · M · Impact hoch
- [ ] **A8** — Constraints-Dialog vergrößern/direkter ins Wochenplan-UI integrieren; Richtung:
      recherchiertes Muster (Sperren + Tages-Reroll direkt in der Tageskarte statt Extra-Dialog),
      siehe A10 · L · Impact mittel
- [ ] **A9** — Totes String-Resource `weekplan_ai_generate` entfernen; „KI"-Framing aus internen
      Docs (`development_plan.md`) tilgen · S · Impact niedrig
- [ ] **A10** — Ankerrezepte anbinden: `generateWithAnchors` im UI erreichbar machen, orientiert
      am Lock+Reroll-Muster (Sperr-Icon und Reroll-Icon direkt in `DayCard`) · L · Impact hoch
- [ ] **A11** — Nur noch der weitergehende Teil: nutzerdefinierte, frei anlegbare Tagesmarker
      (über „Auswärts/kein Kochen" hinaus). Der Basisfall ist durch A15 abgedeckt. Braucht
      eigene Rückfrage zum Datenmodell (Tag-artiges System) vor der Umsetzung · L · Impact
      niedrig
- [ ] **A12** — Extra-Einträge (`WeekplanExtraEntity`) in `exportToShoppingList()`
      (`WeekplanRepository.kt:94-113`) mit exportieren, aktuell nur Rezept-Zutaten · M · Impact
      hoch
- [ ] **A13** — Export-Vorschau vor dem Übernehmen: alle Produkte (inkl. Extras aus A12) zeigen,
      einzeln abwählbar; Listen-Rückfrage bleibt bestehen, aber mit der Standardliste vorbelegt
      statt neutral zu starten · M · Impact hoch
- [ ] **A14** — Mahlzeiten-Tag je `WeekplanRecipeEntity`-Eintrag (mehrere Rezepte pro Mahlzeit
      möglich, `position` existiert bereits als Sortierbasis), dezente Material-3-Tonfarbe je
      Mahlzeitentyp plus Textlabel (nie Farbe allein, siehe
      [ux-accessibility](../../guidelines/ux-accessibility.md) Regel 7); Room-Migration nötig ·
      L · Impact mittel
- [x] **A15** — Woche einmalig anpassen (Nutzerwunsch, nicht aus dem Interview): neues Feld
      `isSkipped` auf `WeekplanDayEntity` (Basisfall von A11 „kein Kochen", gleiches UI-Muster
      wie `isQuickDay`/`isGuestDay`) markiert einen Tag als nicht zu planen — bleibt sichtbar,
      fällt aus KI-Planung/Neuwürfeln und `weekBalance`/`weekNutrition` raus; hat der Tag schon
      ein Rezept, erst Warnung, dann beim Bestätigen entfernen. Dazu „+ Tag"-Aktion zum
      einmaligen Verlängern einer bestimmten Woche (bis 14 Tage), ohne die globale Einstellung
      zu ändern; behebt dabei den oben genannten Anzeige-Bug (dynamisches statt festes
      7-Tage-Fenster) · L · Impact hoch — **umgesetzt:** Room-Migration 30→31, `WeekplanDao`
      (`setSkipped`), `WeekplanRepository` (`clearDay`/`setSkipped`, `deleteDay` darauf
      umgestellt), `WeekplanViewModel` (`days`-Fenster auf 14 Tage, `toggleSkipped`,
      `addDayToWeek`, `canExtendWeek`, Ausschluss in `generateWeekplan`/`regenerateDay`),
      `WeekplanScreen` (Badge, Toggle, Bestätigungsdialog, „+ Tag"-Button), Sync
      (`SyncDto`/`SyncEngine`/`server/app/db.py`/`server/app/sync.py`). Kompiliert erfolgreich.

_Weitere Aufgaben zu Frage 7 nach der Anschlussrunde._

## Entscheidungen

| Datum | Entscheidung | Begründung |
|-------|--------------|------------|
| 2026-08-22 | „KI-Wochenplan"-Framing wird entfernt, Feature bleibt lokal (keine KI-Integration) | Entspricht bereits dem Nutzerwunsch „keine Tokens für den Wochenplan" — nur die Beschriftung war irreführend |
| 2026-08-22 | Vorlagen-Feature wird ersatzlos entfernt statt fertiggebaut/synchronisiert | Feature ist im UI komplett unerreichbar; „Letzte Woche wiederholen" deckt den Bedarf bereits ab |
| 2026-08-22 | Nährwert-Trend bleibt unverändert | Interview: Beiwerk, stört aber nicht |
| 2026-08-22 | Export-Listenauswahl bleibt bestehen, aber mit Standardliste vorbelegt statt neutral | Interview: erst „nur Standardliste", nach Rückfrage präzisiert auf „Rückfrage behalten, nur vorbelegt" |
| 2026-08-22 | Woche einmalig anpassen nutzt denselben Mechanismus wie der A11-Basisfall (ein `isSkipped`-Feld statt zwei getrennter Features) | Nutzerentscheidung: „Dasselbe – ein Mechanismus" |
| 2026-08-22 | Entfernte Tage bleiben in der Wochenansicht sichtbar (nur markiert), keine Navigationslücken; bei bereits geplantem Rezept erst Warnung, dann Löschen | Nutzerentscheidung: „Warnen vor Verlust" |
