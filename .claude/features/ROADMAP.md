# Umsetzungsreihenfolge

Bereichsübergreifende Sicht auf die 58 offenen Punkte aus den elf Bereichsplänen — sortiert
nach **Dringlichkeit × Aufwand**, damit Quick Wins nicht hinter großen Brocken liegen bleiben.

Die Details bleiben im jeweiligen `plan.md`; diese Datei sagt nur, **in welcher Reihenfolge**.
Statuspflege läuft weiter über die Bereichspläne und [README.md](README.md).

**Stand:** 2026-08-30 · Aufwand: S (< 1 h) · M (halber Tag) · L (mehrere Tage)

---

## ⚡ Quick Wins — 14 Punkte, alle S

Zusammen an einem Nachmittag machbar. Behebt zwei stille Bugs, ein Crash-Risiko und räumt
mechanisch auf.

| # | Punkt | Wirkung |
|---|-------|---------|
| 1 | ✅ **plattform A4** — `POST_NOTIFICATIONS` deklarieren + Laufzeitabfrage | Macht die zwei fertig gebauten Erinnerungen überhaupt erst zustellbar; schaltet rezepte A8 frei |
| 2 | ✅ **sync A3** — App-Foreground-Sync-Trigger | Behebt den Urlaubslisten-Vorfall: Änderungen kommen sofort statt nach 15+ min an |
| 3 | **wochenplan A2** — `anchorDays[…]!!` absichern | Echtes Crash-Risiko |
| 4 | ✅ **maerkte A4** — Märkte aus der Einkaufsliste erreichbar | Von zwei Hops auf einen |
| 5 | **wochenplan A9** — tote String-Resource entfernen | Aufräumen, trivial |
| 6 | **`!!`-Sweep** — einkaufsliste A1 · rezepte A1 · wochenplan A3 · einstellungen A1 | 4 Guideline-Verstöße in einem Durchgang |
| 7 | **`key`-Sweep** — einkaufsliste A2 · rezepte A2 · wochenplan A4 · bons-kosten A2 · plattform A1 | 5 Compose-Listen in einem Durchgang |

**Umgesetzt (2026-08-30):** 1, 2, 4 — Commit folgt in dieser Runde. 3, 5, 6, 7 folgen direkt im
Anschluss (2 weitere Commits, siehe unten).

---

## 🧭 Welle 1 — Sichtbarkeit · 4 Punkte, M

Alles Navigation. Gemeinsam umsetzen, sonst wird `HelgaNavGraph.kt` viermal angefasst.

| Punkt | Inhalt |
|-------|--------|
| **bons-kosten A3 + statistik A2** | Vierter Bottom-Nav-Tab „Bons", der auch den heute komplett unverdrahteten Statistik-Screen erreichbar macht |
| **einstellungen A2** | Einstellungen von allen Hauptscreens aus, nicht nur über Rezepte |
| **einstellungen A3** | Einstellungen nach Nutzungshäufigkeit gliedern, Rest in „Erweitert" |

---

## 🔧 Welle 2 — Kernabläufe reparieren · 7 Punkte, M

Die Dinge, die im Alltag heute nicht richtig funktionieren.

| Punkt | Inhalt |
|-------|--------|
| **rezepte A10** | URL-Import reparieren (`supported_only=False` + verständliche Fehlermeldungen) |
| **wochenplan A6** | Filterlogik: Süßspeisen-als-Abendessen-Bug + `regenerateDay()` auf dieselben Filter |
| **naehrwerte A4** | Nährwerte korrekt pro Portion und mit dem Portionswähler skalierend |
| **maerkte A2** | Gang-Zuordnung normalisieren — größter Reibungspunkt der Einkaufsliste |
| **ki A4** | `IngredientLineParser` robuster (unbekannte Einheiten, ½/¼, Kopfzeilen) |
| **wochenplan A12** | Export in die Einkaufsliste: Extras mitnehmen + abwählbare Vorschau |
| **ki A2** | Verhalten bei Stream-Abbruch absichern |

> maerkte A2 und ki A4 brauchen beide eine Namensnormalisierung — zusammen denken.

---

## 🧹 Welle 3 — Aufräumen & Fundament · 10 Punkte, M/L

| Punkt | Inhalt |
|-------|--------|
| **naehrwerte A3 + wochenplan A16** | Nutri-Score vollständig entfernen (inkl. `minNutriScore`-Filter) — **vor** wochenplan A6 spart Arbeit |
| **wochenplan A1** | Vorlagen-Feature ersatzlos ausbauen (unerreichbarer Code) |
| **naehrwerte A2** | `OffProductEntity` an den Sync anbinden — DAO und Server sind fertig |
| **sync A1** | Test, der eine nicht angebundene Entity künftig automatisch meldet |
| **naehrwerte A1** | `AllergyChecker`-Tests · S · sicherheitsrelevanteste Logik der App |
| **rezepte A5** | Suche auf Tags und Zutaten erweitern |
| **statistik A3** | Zeitraumfilter analog zum Ausgabenüberblick |
| **bons-kosten A4** | Einstellbare automatische Bon-Löschung, Default 3 Monate |
| **sync A4** | Bilder proaktiv herunterladen (Offline-Verfügbarkeit auf dem zweiten Gerät) |

---

## 🏗 Welle 4 — Größere Features · 15 Punkte, überwiegend L

| Punkt | Inhalt |
|-------|--------|
| **plattform A3 → einkaufsliste A7** | Eigenes `:wear`-Modul, danach Abhaken auf der Uhr — der Ablauf, den du dir vom Einkaufen versprichst |
| **rezepte A8** | Timer: mehrere parallel, Hintergrundlauf, Benachrichtigung (braucht plattform A4 ✓ aus den Quick Wins) |
| **rezepte A7** | Kochansicht: geteilter Landscape-Modus |
| **rezepte A6** | Bewertung zusammenlegen (nur beim Kochen bewerten) |
| **rezepte A9** | Portionsskalierung je Rezept merken |
| **wochenplan A10** | Ankerrezepte anbinden (`generateWithAnchors` ist toter Code) |
| **wochenplan A8** | Constraints direkt ins UI statt Dialog |
| **wochenplan A14** | Mahlzeiten-Tag je Eintrag |
| **wochenplan A11** | Frei anlegbare Tagesmarker — braucht vorher eine Datenmodell-Rückfrage |
| **ki A3** | Server-Erreichbarkeit vor jeder KI-Nutzung anzeigen |
| **maerkte A1** | Drag-and-Drop für die Gangreihenfolge |
| **maerkte A3** | Neue Märkte per Standard-Gangsatz oder Kopie vorbefüllen |
| **einstellungen A4** | Reset aller lokalen Daten ohne Neuinstallation |
| **plattform A2** | 82× `contentDescription` durchsehen · Impact niedrig |

---

## 🧪 Tests — 8 Punkte, laufen mit

Entscheidung vom 2026-08-30: **nur bei kritischer Logik vorne**. `naehrwerte` A1 (Allergene)
und `sync` A1 (Sync-Vollständigkeit) stehen deshalb schon in Welle 3. Der Rest läuft am besten
zusammen mit dem Fix, der denselben Code ohnehin anfasst:

| Punkt | Läuft mit |
|-------|-----------|
| **ki A1** (`RecipeJsonLdParser`) | ki A4 / rezepte A10 |
| **einkaufsliste A3** (`ShoppingUnitConverter`, `IngredientLineParser`) | ki A4 |
| **bons-kosten A1** (`ReceiptItemNormalizer`) | bons-kosten A4 |
| **wochenplan A5** (Constraints, `weekBalance`) | wochenplan A6 |
| **rezepte A3** (Timer-Erkennung) | rezepte A8 |
| **statistik A1** (Aggregation) | statistik A3 |
| **sync A2** (Konfliktfälle) | frei |
| **rezepte A4** (History/Feedback-Sync vereinheitlichen) | frei · Impact niedrig |

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
