# Feature: Wochenplan

> **Status:** Interview erledigt (8/8) · **Aufgaben:** 3 offen (11 erledigt; A7→A6 und A13→A12 zusammengelegt) · **Stand:** 2026-08-31 · **Priorität:** ⭐⭐⭐

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

- [x] **A1** — Vorlagen-Feature ersatzlos ausbauen (Nutzerentscheidung, nicht sync-fähig machen):
      `WeekplanTemplateEntity`, `WeekplanTemplateEntryEntity`, `WeekplanTemplateDao`,
      `WeekplanTemplateRepository.kt`, `TemplateSheet` in `WeekplanScreen.kt`, zehn Strings
      (`strings.xml:255-285`), Room-Migration zum sauberen Entfernen der Tabellen · M · Impact
      niedrig — **umgesetzt:** alle vier Dateien gelöscht (Entities, DAO, Repository),
      `saveCurrentWeekAsTemplate`/`applyTemplate`/`deleteTemplate`/`templates`-StateFlow aus
      `WeekplanViewModel.kt` entfernt, `TemplateSheet`-Composable (nie aufgerufen, bestätigt vor
      dem Löschen) aus `WeekplanScreen.kt` entfernt, alle zehn Vorlagen-Strings aus
      `strings.xml` entfernt (verifiziert, nicht aus dem alten Zeilenbereich übernommen — lagen
      inzwischen verstreut statt am Stück), DI-Provider in
      `DatabaseModule.kt` entfernt. Anders als bei den Nutri-Score-Spalten (siehe naehrwerte A3)
      hier eine echte Room-Migration (30→31 existierte schon, jetzt 31→32) mit `DROP TABLE IF
      EXISTS` für `weekplan_templates`/`weekplan_template_entries` — folgt dem bereits
      etablierten Muster aus `MIGRATION_29_30` (verwaiste Tabellen ohne Entity/DAO), weil hier
      im Gegensatz zum Nutri-Score-Fall zwei komplette Tabellen und keine Sync-Anbindung
      betroffen waren, die entfernt werden konnte, ohne andere Spalten/Tabellen anzufassen.
- [x] **A2** — `anchorDays[day.id]!!` gegen fehlenden Schlüssel absichern · S · Impact hoch —
      **umgesetzt:** Einzel-Lookup in eine lokale `val` gehoben statt doppeltem
      `in`-Check + `!!`-Zugriff (`WeekplanViewModel.kt`, Zeile war inzwischen auf 567
      gewandert)
- [x] **A3** — `!!` in `WeekplanScreen.kt` auflösen · S · Impact mittel — **umgesetzt:** war
      `exportPicker!!` (nicht `anchorDays`, das ist A2 in `WeekplanViewModel.kt`), lokale `val`
      statt `!!`
- [x] **A4** — `key`-Parameter in `WeekplanScreen.kt` und `WeekplanRecipePickerScreen.kt`
      ergänzen · S · Impact mittel — **umgesetzt:** `extraSuggestions` bzw. `allTags`, beide
      `key = { it }`
- [ ] **A5** — Unit-Tests für Constraint-Auswertung und `weekBalance` · M · Impact hoch
- [x] **A6** — **Filterlogik in einem Griff** (2026-08-30 mit dem früheren A7 zusammengelegt,
      weil beide dieselbe Stelle anfassen und A7 ohnehin die gemeinsame Funktion verlangte):
      1. mealSlot-Filter in `generateWeekplan()` von „nicht breakfast/snack" auf „ausschließlich
         lunch/dinner" verschärfen ODER `meal_slot` im Klassifikations-Prompt
         (`server/app/ai.py:46`) um `"dessert"` erweitern und ausschließen; Default `"other"`
         bei nie klassifizierten Rezepten ebenfalls ausschließen — behebt den
         Süßspeisen-als-Abendessen-Bug.
      2. Die Filter in eine gemeinsame Funktion ziehen und `regenerateDay()` darauf umstellen,
         das aktuell vier der Filter überspringt (Allergene, Kcal, Saison, mealSlot).
      Nach A16 umsetzen, dann entfällt der Nutri-Score-Filter von selbst · M · Impact hoch —
      **umgesetzt:** neue private Funktion `applyRecipeFilters()` in `WeekplanViewModel.kt`,
      gemeinsam genutzt von `generateWeekplan()`, `regenerateDay()` und
      `regenerateProposalDay()`. mealSlot-Filter von Blockliste (nur breakfast/snack raus) auf
      Positivliste (nur lunch/dinner rein) umgestellt — behebt den Süßspeisen-Bug auch für
      `"other"`, ohne auf eine Prompt-Änderung angewiesen zu sein. Saison-Filter ist jetzt
      echter Hard-Filter statt in zwei der drei Pfade wirkungsloser Präferenz. A16 (Nutri-Score
      entfernen) ist noch **nicht** umgesetzt — beim Nachgehen bestätigt, `minNutriScore` ist
      weiterhin voll verdrahtet, keine Altannahme mehr übernommen; der Filter bleibt deshalb
      vorerst in `applyRecipeFilters()` erhalten und fällt mit A16 automatisch weg. Kompiliert
      erfolgreich.
- **A7** — → **in A6 aufgegangen** (2026-08-30). Verweise auf A7 bleiben gültig, die Arbeit
      steckt dort.
- [x] **A8** — Constraints-Dialog vergrößern/direkter ins Wochenplan-UI integrieren; Richtung:
      recherchiertes Muster (Sperren + Tages-Reroll direkt in der Tageskarte statt Extra-Dialog),
      siehe A10 · L · Impact mittel — **umgesetzt:** Das inhaltliche Kernproblem hinter A8
      (ungeeignete KI-Vorschläge, Süßspeisen als Abendessen) war schon durch A6 behoben — A8 blieb
      damit ein reines UI-Problem: `ConstraintsEditorSheet` war ein `ModalBottomSheet`, nur über
      das Tune-Icon erreichbar, komplett vom restlichen Wochenplan abgekoppelt („separates
      Sheet" laut Interview-Kritik). Ersetzt durch `ConstraintsPanel`, eine Karte fest in der
      Wochenplan-`LazyColumn` (zwischen Wochennavigation und Bilanzzeile) statt eines Overlays.
      Eingeklappt zeigt sie eine Kompaktzeile mit allen aktiven Grenzwerten auf einen Blick
      (🥩≤/🐟≤/🥬≥/🔥≤) — vorher waren diese Werte komplett unsichtbar, solange man den Dialog
      nicht öffnete. Tippen auf die Karte oder das Tune-Icon (jetzt Toggle statt Dialog-Öffner)
      klappt den vollen Editor (Slider, Bio-Switch, Allergie-Chips — Inhalt unverändert) direkt
      darunter auf. `rememberModalBottomSheetState`/`ModalBottomSheet`-Wrapper entfernt, Inhalt in
      `ConstraintsEditorContent` ausgelagert. Keine Datenmodell- oder ViewModel-Änderung nötig,
      reines UI-Refactoring.
- [x] **A16** — `WeekplanConstraintsEntity.minNutriScore` entfernen: Nutri-Score verschwindet laut
      [naehrwerte](../naehrwerte/plan.md) A3 komplett aus der App, der Generierungs-Filter in
      `WeekplanRepository.kt`/`WeekplanViewModel.kt` wird damit hinfällig · S · Impact mittel —
      **umgesetzt:** zusammen mit naehrwerte A3. `applyRecipeFilters()` hat keine
      Nutri-Score-Stufe mehr (Saison-Filter greift jetzt direkt nach dem Kcal-Filter), Min-
      Nutri-Score-Auswahl aus dem `ConstraintsEditorSheet` entfernt, `saveConstraints()` ohne
      `minNutriScore`-Parameter. Die Room-Spalte selbst bleibt bestehen (siehe naehrwerte A3).
- [x] **A9** — Totes String-Resource `weekplan_ai_generate` entfernen · S · Impact niedrig —
      **umgesetzt:** Zeile aus `strings.xml` entfernt (verifiziert unbenutzt). Den zweiten Teil
      („KI"-Framing aus `development_plan.md` tilgen) bewusst nicht angefasst —
      `development_plan.md` ist laut README ein Historie-Dokument, kein aktueller Stand;
      rückwirkendes Umschreiben verzerrt die Historie eher, als dass es nützt.
- [x] **A10** — Ankerrezepte anbinden: `generateWithAnchors` im UI erreichbar machen, orientiert
      am Lock+Reroll-Muster (Sperr-Icon und Reroll-Icon direkt in `DayCard`) · L · Impact hoch —
      **umgesetzt:** Reroll-Icon (`Icons.Filled.Refresh`) gab es in `DayCard` bereits, verdrahtet
      auf `WeekplanViewModel.regenerateDay()`. `generateWithAnchors(startDate)` war jedoch
      totes Fassaden-Wrapping — rief nur `generateWeekplan()` auf und ignorierte den Parameter;
      ersatzlos entfernt statt "erreichbar gemacht", da kein eigenständiger Mechanismus dahinter
      steckte. Stattdessen den bisher impliziten Anker-Mechanismus ("jeder Tag mit Rezept bleibt
      bei `generateWeekplan()` automatisch stehen") durch ein explizites Sperr-Flag ersetzt:
      neue Spalte `WeekplanDayEntity.isLocked` (Room-Migration 33→34 `ALTER TABLE weekplan_days
      ADD COLUMN isLocked`, DB jetzt v34), volle Sync-Anbindung (`WeekplanDayDto.isLocked`,
      `SyncEngine`-Mapper, Server `db.py`/`models.py`/`sync.py`). Schloss-Icon (`Icons.Filled.Lock`
      / `LockOpen`) neben Skip-Icon in `DayCard`, toggelt über neue `WeekplanViewModel.toggleLocked()`.
      Verhaltensänderung bewusst so gewählt (recherchierter Standard „lock meals you like and
      regenerate the rest"): `generateWeekplan()` überschreibt jetzt auch bereits befüllte,
      aber ungesperrte Tage — vorher blieb jeder Tag mit Rezept automatisch unangetastet, was
      "Woche neu generieren" bei einer teilbefüllten Woche faktisch nutzlos machte. Der Nutzer
      sieht die volle Zuordnung vor dem Übernehmen im `ProposalSheet` (Review-Schritt existierte
      schon), kein blindes Überschreiben. Reroll (`regenerateDay`, `regenerateProposalDay`)
      respektiert die Sperre ebenfalls (kein Effekt auf gesperrte Tage, Icon in `DayCard`
      und `ProposalSheet` entsprechend deaktiviert/ausgeblendet). Nebenbei entdeckt und
      behoben: `WeekplanDayRecord` in `server/app/models.py` hatte `is_skipped` nie deklariert,
      obwohl `TABLE_COLUMNS`/`sync.py` die Spalte längst führten — jeder Push eines
      `weekplan_days`-Datensatzes wäre an der NOT-NULL-Constraint gescheitert (`rec_dict.get(c)`
      liefert `None` für ein nicht deklariertes Pydantic-Feld). Ergänzt, zusammen mit `is_locked`.
- [ ] **A11** — Nur noch der weitergehende Teil: nutzerdefinierte, frei anlegbare Tagesmarker
      (über „Auswärts/kein Kochen" hinaus). Der Basisfall ist durch A15 abgedeckt. Braucht
      eigene Rückfrage zum Datenmodell (Tag-artiges System) vor der Umsetzung · L · Impact
      niedrig
- [x] **A12** — **Export in die Einkaufsliste überarbeiten** (2026-08-30 mit dem früheren A13
      zusammengelegt — ein Ablauf, eine Umsetzung):
      1. Extra-Einträge (`WeekplanExtraEntity`) in `exportToShoppingList()`
         (`WeekplanRepository.kt:94-113`) mit exportieren, aktuell nur Rezept-Zutaten.
      2. Vorschau vor dem Übernehmen: alle Produkte (inkl. der Extras aus Schritt 1) zeigen,
         einzeln abwählbar; Listen-Rückfrage bleibt bestehen, aber mit der Standardliste
         vorbelegt statt neutral zu starten · M · Impact hoch — **umgesetzt:** Export in
         `WeekplanRepository.kt` in „Collect (pure) + Apply (write)" aufgeteilt —
         `collectExportItems()` liest jetzt zusätzlich `weekplanDao.extrasForDay()` (neuer
         Typ `WeekplanExportItem`), `applyExportItems()` schreibt erst nach Bestätigung.
         `WeekplanViewModel` hält den Zwischenstand in `ExportPreviewState` (`items`, `listId`,
         `deselected`), neue Funktionen `startExportPreview`/`toggleExportItem`/
         `confirmExportPreview`/`cancelExportPreview`. `WeekplanScreen.kt`:
         `ShoppingListPickerDialog` wählt jetzt per `RadioButton` vor (Standardliste als
         Vorauswahl über `defaultShoppingListId`) statt sofort feuernder Zeilen, neue
         `ExportPreviewDialog` (Checkbox-Zeilen im `IngredientCheckRow`-Muster aus
         `RecipeCookScreen.kt`) zeigt alle Positionen abwählbar vor dem Schreiben. Kompiliert
         erfolgreich.
- **A13** — → **in A12 aufgegangen** (2026-08-30). Verweise auf A13 bleiben gültig, die Arbeit
      steckt dort.
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
