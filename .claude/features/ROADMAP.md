# Umsetzungsreihenfolge

Bereichsübergreifende Sicht auf die 58 offenen Punkte aus den elf Bereichsplänen — sortiert
nach **Dringlichkeit × Aufwand**, damit Quick Wins nicht hinter großen Brocken liegen bleiben.

Die Details bleiben im jeweiligen `plan.md`; diese Datei sagt nur, **in welcher Reihenfolge**.
Statuspflege läuft weiter über die Bereichspläne und [README.md](README.md).

**Stand:** 2026-08-31 · Aufwand: S (< 1 h) · M (halber Tag) · L (mehrere Tage)

---

## ⚡ Quick Wins — vollständig umgesetzt (2026-08-30)

Alle 7 Gruppen (14 Einzelpunkte) sind erledigt, in drei Commits:

| # | Punkt | Wirkung |
|---|-------|---------|
| 1 | ✅ **plattform A4** — `POST_NOTIFICATIONS` deklarieren + Laufzeitabfrage | Macht die zwei fertig gebauten Erinnerungen überhaupt erst zustellbar; schaltet rezepte A8 frei |
| 2 | ✅ **sync A3** — App-Foreground-Sync-Trigger | Behebt den Urlaubslisten-Vorfall: Änderungen kommen sofort statt nach 15+ min an |
| 3 | ✅ **wochenplan A2** — `anchorDays[…]!!` absichern | Echtes Crash-Risiko |
| 4 | ✅ **maerkte A4** — Märkte aus der Einkaufsliste erreichbar | Von zwei Hops auf einen |
| 5 | ✅ **wochenplan A9** — tote String-Resource entfernen | Aufräumen, trivial |
| 6 | ✅ **`!!`-Sweep** — einkaufsliste A1 · rezepte A1 · wochenplan A3 · einstellungen A1 | 10 Fundstellen (nicht 4 Gruppen mit 1 Treffer — insgesamt mehr `!!` als ursprünglich gezählt), alle in einem Durchgang |
| 7 | ✅ **`key`-Sweep** — einkaufsliste A2 · rezepte A2 · wochenplan A4 · bons-kosten A2 · plattform A1 | 8 Fundstellen, alle in einem Durchgang |

Details: [einkaufsliste](einkaufsliste/plan.md), [rezepte](rezepte/plan.md),
[wochenplan](wochenplan/plan.md), [maerkte](maerkte/plan.md), [bons-kosten](bons-kosten/plan.md),
[sync](sync/plan.md), [plattform](plattform/plan.md), [einstellungen](einstellungen/plan.md) —
jeweils mit „umgesetzt"-Vermerk bei den betroffenen Punkten.

**Weiter geht's mit Welle 1** (Sichtbarkeit — Bons-Tab/Statistik, Einstellungen überall).

---

## 🧭 Welle 1 — Sichtbarkeit — vollständig umgesetzt (2026-08-30)

Alles Navigation, gemeinsam in einem Durchgang durch `HelgaNavGraph.kt` umgesetzt.

| Punkt | Inhalt |
|-------|--------|
| ✅ **bons-kosten A3 + statistik A2** | Vierter Bottom-Nav-Tab „Bons" (`ROUTE_RECEIPTS` als Root-Route), Insights-Button darin führt zum vorher komplett unverdrahteten Statistik-Screen |
| ✅ **einstellungen A2** | Einstellungen jetzt auch aus Einkaufsliste und Wochenplan erreichbar (Overflow-Menü), nicht mehr nur über Rezepte |
| ✅ **einstellungen A3** | Einkaufsliste-Einstellungen + Schnellbuttons oben sichtbar, Rest hinter aufklappbarem „Erweitert" |

Details: [bons-kosten](bons-kosten/plan.md) A3, [statistik](statistik/plan.md) A2,
[einstellungen](einstellungen/plan.md) A2/A3.

**Weiter geht's mit Welle 2** (Kernabläufe — URL-Import, Wochenplan-Filter, Nährwerte,
Gang-Zuordnung, Zutaten-Parser, Export-Vorschau, Stream-Abbruch).

---

## 🔧 Welle 2 — Kernabläufe reparieren — vollständig umgesetzt (2026-08-31)

Die Dinge, die im Alltag heute nicht richtig funktionieren.

| Punkt | Inhalt |
|-------|--------|
| ✅ **rezepte A10** | URL-Import reparieren (`supported_only=False` + verständliche Fehlermeldungen) |
| ✅ **wochenplan A6** | Filterlogik: Süßspeisen-als-Abendessen-Bug + `regenerateDay()` auf dieselben Filter |
| ✅ **naehrwerte A4** | Nährwerte korrekt pro Portion und mit dem Portionswähler skalierend |
| ✅ **maerkte A2** | Gang-Zuordnung normalisieren — größter Reibungspunkt der Einkaufsliste |
| ✅ **ki A4** | `IngredientLineParser` robuster (unbekannte Einheiten, ½/¼, Kopfzeilen) |
| ✅ **wochenplan A12** | Export in die Einkaufsliste: Extras mitnehmen + abwählbare Vorschau |
| ✅ **ki A2** | Verhalten bei Stream-Abbruch absichern |

maerkte A2 und ki A4 nutzen am Ende **keinen** gemeinsamen Normalisierungscode — beim Umsetzen
zeigte sich, dass beide unterschiedliche Ziele verfolgen (Lookup-Schlüssel bilden vs.
Zutatenzeile zerlegen); jeweils eigenständig gelöst (neues `AisleProductKey.normalize()` bzw.
Erweiterung von `IngredientLineParser`).

Details: [rezepte](rezepte/plan.md) A10, [wochenplan](wochenplan/plan.md) A6/A12,
[naehrwerte](naehrwerte/plan.md) A4, [maerkte](maerkte/plan.md) A2, [ki](ki/plan.md) A2/A4 —
jeweils mit „umgesetzt"-Vermerk.

**Weiter geht's mit Welle 3** (Aufräumen & Fundament — Nutri-Score entfernen, Vorlagen-Feature
ausbauen, Sync-Lücken schließen, Tests für sicherheitsrelevante Logik).

---

## 🧹 Welle 3 — Aufräumen & Fundament — vollständig umgesetzt (2026-08-31)

| Punkt | Inhalt |
|-------|--------|
| ✅ **naehrwerte A3 + wochenplan A16** | Nutri-Score vollständig entfernen (inkl. `minNutriScore`-Filter) — **vor** wochenplan A6 spart Arbeit |
| ✅ **wochenplan A1** | Vorlagen-Feature ersatzlos ausbauen (unerreichbarer Code) |
| ✅ **naehrwerte A2** | `OffProductEntity` an den Sync anbinden — DAO und Server sind fertig |
| ✅ **sync A1** | Test, der eine nicht angebundene Entity künftig automatisch meldet |
| ✅ **naehrwerte A1** | `AllergyChecker`-Tests · S · sicherheitsrelevanteste Logik der App |
| ✅ **rezepte A5** | Suche auf Tags und Zutaten erweitern |
| ✅ **statistik A3** | Zeitraumfilter analog zum Ausgabenüberblick |
| ✅ **bons-kosten A4** | Einstellbare automatische Bon-Löschung, Default 3 Monate |
| ✅ **sync A4** | Bilder proaktiv herunterladen (Offline-Verfügbarkeit auf dem zweiten Gerät) |

Details: [naehrwerte](naehrwerte/plan.md) A1–A3, [wochenplan](wochenplan/plan.md) A1/A16,
[sync](sync/plan.md) A1/A4, [rezepte](rezepte/plan.md) A5, [statistik](statistik/plan.md) A3,
[bons-kosten](bons-kosten/plan.md) A4 — jeweils mit „umgesetzt"-Vermerk.

**Weiter geht's mit Welle 4** (Größere Features — Wear-Modul, Timer, Kochansicht,
Bewertung, Wochenplan-Erweiterungen, u. a.).

---

## 🏗 Welle 4 — Größere Features — vollständig umgesetzt (2026-09-01)

| Punkt | Inhalt |
|-------|--------|
| ✅ **plattform A3 → einkaufsliste A7** | Eigenes `:wear`-Modul, danach Abhaken auf der Uhr — der Ablauf, den du dir vom Einkaufen versprichst |
| ✅ **rezepte A8** | Timer: mehrere parallel, Hintergrundlauf, Benachrichtigung (braucht plattform A4 ✓ aus den Quick Wins) |
| ✅ **rezepte A7** | Kochansicht: geteilter Landscape-Modus |
| ✅ **rezepte A6** | Bewertung zusammenlegen (nur beim Kochen bewerten) |
| ✅ **rezepte A9** | Portionsskalierung je Rezept merken |
| ✅ **wochenplan A10** | Ankerrezepte anbinden (`generateWithAnchors` ist toter Code) |
| ✅ **wochenplan A8** | Constraints direkt ins UI statt Dialog |
| ✅ **wochenplan A14** | Mahlzeiten-Tag je Eintrag |
| ✅ **wochenplan A11** | Frei anlegbare Tagesmarker — braucht vorher eine Datenmodell-Rückfrage |
| ✅ **ki A3** | Server-Erreichbarkeit vor jeder KI-Nutzung anzeigen |
| ✅ **maerkte A1** | Drag-and-Drop für die Gangreihenfolge |
| ✅ **maerkte A3** | Neue Märkte per Standard-Gangsatz oder Kopie vorbefüllen |
| ✅ **einstellungen A4** | Reset aller lokalen Daten ohne Neuinstallation |
| ✅ **plattform A2** | 82× `contentDescription` durchsehen · Impact niedrig |

Details: [plattform](plattform/plan.md) A2/A3, [einkaufsliste](einkaufsliste/plan.md) A7,
[rezepte](rezepte/plan.md) A6–A9, [wochenplan](wochenplan/plan.md) A8/A10/A11/A14,
[ki](ki/plan.md) A3, [maerkte](maerkte/plan.md) A1/A3, [einstellungen](einstellungen/plan.md) A4
— jeweils mit „umgesetzt"-Vermerk. plattform A3 mit Einschränkung: die Data-Layer-Bridge zur Uhr
steht, automatisches Mitinstallieren beim Koppeln bräuchte eine gemeinsame Play-Store-
Veröffentlichung (außerhalb dieser Session) — Details im plattform-Plan.

---

## 🧪 Tests — 8 Punkte, vollständig umgesetzt (2026-09-01)

Entscheidung vom 2026-08-30: **nur bei kritischer Logik vorne**. `naehrwerte` A1 (Allergene)
und `sync` A1 (Sync-Vollständigkeit) stehen deshalb schon in Welle 3. Der Rest sollte
zusammen mit dem Fix laufen, der denselben Code ohnehin anfasst — inzwischen alle nachgezogen:

| Punkt | Läuft mit |
|-------|-----------|
| ✅ **ki A1** (`RecipeJsonLdParser`) | ki A4 / rezepte A10 |
| ✅ **einkaufsliste A3** (`ShoppingUnitConverter`, `IngredientLineParser`) | ki A4 |
| ✅ **bons-kosten A1** (`ReceiptItemNormalizer`) | bons-kosten A4 |
| ✅ **wochenplan A5** (Constraints, `weekBalance`) | wochenplan A6 |
| ✅ **rezepte A3** (Timer-Erkennung) | rezepte A8 |
| ✅ **statistik A1** (Aggregation) | statistik A3 |
| ✅ **sync A2** (Konfliktfälle) | frei |
| ✅ **rezepte A4** (History/Feedback-Sync vereinheitlichen) | frei · Impact niedrig |

Details: [ki](ki/plan.md) A1, [einkaufsliste](einkaufsliste/plan.md) A3,
[bons-kosten](bons-kosten/plan.md) A1, [wochenplan](wochenplan/plan.md) A5,
[rezepte](rezepte/plan.md) A3/A4, [statistik](statistik/plan.md) A1,
[sync](sync/plan.md) A2 — jeweils mit „umgesetzt"-Vermerk. Für die drei Tests, deren Logik
bisher inline in einem ViewModel mit direktem DAO-Zugriff lag
(`filterCandidateRecipes`/`computeWeekBalance` in `WeekplanViewModel.kt`, `aggregateMonthStats`
in `StatsViewModel.kt`), wurde die Logik verhaltensgleich in reine, Room-freie Top-Level-
Funktionen ausgelagert — sonst wären sie ohne Robolectric/Fake-DAOs gar nicht sinnvoll
unit-testbar gewesen. `RecipeJsonLdParserTest` brauchte zusätzlich `testImplementation(libs.org.json)`
(echte JVM-Implementierung, das `android.jar`-Stub wirft zur Laufzeit „Method not mocked").
**sync A2** ließ sich nicht auf eine reine Formel reduzieren (Ablaufsteuerung, nicht
Vergleichslogik) — stattdessen `SyncEngineTest.kt` mit MockK direkt gegen die echte
`SyncEngine`-Klasse, inkl. gemocktem `database.withTransaction {}`. Dabei fiel **rezepte A4**
als echter Bug ab (nicht nur Stildeviation): `recipeHistory`/`recipeFeedback` prüften beim Pull
nur `updatedAt > 0`, nicht LWW gegen den lokalen Stand wie jede andere Tabelle — ein älterer
Server-Datensatz hätte eine neuere, noch nicht gepushte lokale Änderung stillschweigend
überschrieben. Behoben (`SyncDao.historyTimestamps()`/`feedbackTimestamps()` ergänzt, Standard-
`filterServerWins()` verwendet) und mit zwei `SyncEngineTest`-Fällen abgesichert — beide
empirisch gegen den alten Code verifiziert (schlagen dort nachweislich fehl).

---

## Zusammengelegt am 2026-08-30

| Vorher | Jetzt | Grund |
|--------|-------|-------|
| einkaufsliste A6 | → maerkte A2 | Ursache war „noch offen", ist durch das Märkte-Interview root-caused |
| rezepte A3 (Parser-Teil) | → ki A1 | Identischer Test, Parser liegt in `ui/ai/` |
| wochenplan A7 | → wochenplan A6 | Dieselbe Filterstelle; A7 verlangte ohnehin die gemeinsame Funktion |
| wochenplan A13 | → wochenplan A12 | Ein Ablauf (Export), eine Umsetzung |

Prioritätskorrekturen derselben Runde: `plattform` A3 (Wear) von „gesenkt" zurück auf **hoch**
— die Nichtnutzung ist Folge der Sideload-Hürde, nicht mangelnden Interesses. `plattform` A2
(contentDescription) von hoch auf **niedrig**.
