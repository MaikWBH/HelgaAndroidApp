# Phase 3b — Ausgaben-Auswertung, Diagramme und Gang-Kategorien

Skills: mobile-android-design (Diagramm-UI), android-clean-architecture (Datenschicht), fastapi-templates (Server).
Voraussetzung: [Phase 3a](phase-3a-bons-server-scan.md) ist umgesetzt (Room v37, Bon-Protokoll, Kategorien-Katalog).
Grundlage: [Entscheidungen](phase-3-bons-kategorien-entscheidungen.md).

Pfade: `$S` = `server` · `$SA` = `server/app` · `$ST` = `server/tests` ·
`$B` = `app/src/main/kotlin/com/helga/android` · `$U` = `$B/ui` · `$T` = `app/src/test/kotlin/com/helga/android` ·
`$R` = `app/src/main/res` · `$D` = `.claude/phasen/phase-3-daten`

Datendateien (verbindlich): [server-vertrag.md](phase-3-daten/server-vertrag.md) (Abschnitte 5.4, 6) ·
[kategorien.md](phase-3-daten/kategorien.md) · [aisle-keywords.md](phase-3-daten/aisle-keywords.md) ·
[aisle-testfaelle.md](phase-3-daten/aisle-testfaelle.md).

## Modelle
- Definition: Fable 5.2 · Umsetzung: vom Nutzer manuell gewählt (offen) · Datum: 2026-11-11

## Ziel und Bereich
Die Kostenübersicht bekommt vier selbst gezeichnete Diagramme (Compose Canvas), die auch offline für die gesamte Zeit
funktionieren, weil das Handy Monatsaggregate vom Server vorhält. Märkte bekommen je Gang einen Kategorie-Schlüssel; neue
Artikel der Einkaufsliste werden in der Reihenfolge gelerntes Lexikon → lokale Stichwortliste → Server-KI einem Gang
zugeordnet, und Tests belegen, wie gut das funktioniert. Bereiche laut Feature-Index: Bons & Kosten
([plan](../features/bons-kosten/plan.md)), Märkte & Gänge ([plan](../features/maerkte/plan.md)), Einkaufsliste
([plan](../features/einkaufsliste/plan.md)), Sync, Server.

## Entscheidungen
Aus dem grilling (Nummern wie in der Entscheidungsdatei): 2 Monatsaggregate offline · 7 vier Diagramme im
Canvas-Eigenbau · 8 gemeinsame Kategorienliste · 9 Kategorie-Schlüssel je Gang · 10 Hybrid-Zuordnung mit Tests.
Daraus und aus dem Code abgeleitet (Stufe A, zur Freigabe unter „Annahmen"):

- **Aggregate:** Der Server berechnet sie bei jedem Abruf aus den Bons (keine gespeicherte Tabelle). Die App ersetzt
  ihren Abzug (`receipt_aggregates`) nach jedem Bon-Abgleich vollständig. Diagramme lesen **nur** aus diesem Abzug.
- **Diagramme:** (1) Balken „Ausgaben je Monat/Woche" mit Budgetlinie, (2) Ring „Anteil je Markt", (3) Querbalken
  „Ausgaben je Kategorie", (4) Querbalken „Top-Artikel" mit Linie „Preisentwicklung" in der Artikel-Detailansicht.
  Farben ausschließlich aus `MaterialTheme.colorScheme`; jedes Diagramm hat eine Textbeschreibung für Screenreader und
  zeigt seine Werte zusätzlich als Text.
- **Gang-Schlüssel:** genau ein Schlüssel je Gang; fehlt einem Markt der passende Gang, greift die einstufige
  Ausweichkette aus `$D/kategorien.md`.
- **Stichwortliste:** 605 Begriffe statt „ca. 300", als Raw-String im Code (kein Asset, damit Unit-Tests ohne Android
  laufen).
- **Server-KI für Unbekanntes** läuft entkoppelt per WorkManager; das Hinzufügen eines Artikels wartet nie auf das Netz.
- **Alt-Clients:** Der Server überschreibt `category_key` nicht mit leer, wenn ein Client das Feld nicht mitschickt.

## Annahmen zur Freigabe
1. **Vorläufige Bons fehlen in den Diagrammen**, bis der Server sie ausgewertet hat; die Kostenübersicht weist mit einer
   Zeile darauf hin.
2. **Bon-weite Rabatte** werden nicht auf Kategorien verteilt; die Kategorien-Karte nennt die Rabattsumme als Fußnote.
3. **Preisliste und Einzelkäufe** (`ProductPriceListScreen`, Liste in der Detailansicht) zeigen nur die Bons, die auf dem
   Handy liegen (Standard 3 Monate); „Top-Artikel" und „Preisentwicklung" decken die gesamte Zeit ab.
4. **Zeiträume** der Karten 2–4: „Dieser Monat", „3 Monate", „12 Monate", „Gesamt" (Kalendermonate). Karte 1 zeigt immer
   die letzten 12 Monate bzw. 12 Wochen; die Budgetlinie in der Wochenansicht ist Monatsbudget × 12 / 52.
5. **Bestehende Märkte:** Gänge mit einem der zehn alten Standardnamen bekommen ihren Schlüssel automatisch; alle
   anderen ordnet der Nutzer im Gang-Editor zu. Ein Knopf ergänzt fehlende Standardgänge.
6. **Neue Märkte** mit Vorlage bekommen 15 Gänge (Katalogeinträge 1–15) statt bisher 10.
7. **Server-KI ordnet nur zu, wenn ein Markt aktiv ist** und der Artikel noch keinen Gang hat; das Ergebnis wird als
   gelernte Zuordnung (`AisleProduct`) gespeichert und gesynct. Ordnet die KI „Sonstiges" zu, bleibt der Artikel ohne
   Gang; der Server merkt sich das und fragt die KI nicht erneut.
8. **Trefferquote der Stichwortliste:** 150 von 150 Pflichtfällen, 55 von 60 in der unabhängigen Stichprobe (91,7 %).
   Die fünf bekannten Fehlzuordnungen bleiben bestehen und werden über Lexikon oder Handkorrektur aufgefangen.
9. **`/api/suggestions/aisles`** (ungenutzt, ohne Normalisierung) bleibt unverändert und wird in Phase 4 entschieden.

## Befunde (Stand 2026-11-11)
| # | Datei:Zeile | Befund | Regel (Skill/Guideline) |
|---|-------------|--------|-------------------------|
| 1 | `$U/receipts/CostOverviewViewModel.kt:63-124` | Summen per Einmal-Abfrage aus den lokalen Bons; nach 3a nur noch 3 Monate vorhanden | Entscheidung 2 |
| 2 | `$U/receipts/CostOverviewScreen.kt:218-306` | Karten und Listen (`CostBarItem` als gefüllte `Box`), kein Diagramm, kein `Screen`/`Content`, keine Preview | Entscheidung 7; Skill Best Practice 6/8 |
| 3 | `$U/receipts/CostOverviewViewModel.kt:126-139` | „Monat" = letzte 30 Tage, nicht Kalendermonat; Budgetvergleich nutzt aber den Kalendermonat (Z.69-77) | Konsistenz |
| 4 | `$U/receipts/ProductPriceDetailScreen.kt:187-255` | einziges Canvas-Diagramm (Linie), fest in den Screen eingebaut, ohne Semantik | Entscheidung 7; ux §4 |
| 5 | `$B/data/local/dao/ReceiptDao.kt:77-151` | fünf Aggregat-Abfragen auf `receipts`, künftig ohne Verwendung | – |
| 6 | `$B/data/local/entity/StoreAisleEntity.kt`, `$SA/db.py:152-159`, `$SA/sync.py:31-33` | kein Kategorie-Schlüssel am Gang | Entscheidung 9 |
| 7 | `$B/data/repository/StoreRepository.kt:21-25` | 10 Standardgänge als Namen ohne Schlüssel | Entscheidung 9 |
| 8 | `$U/shopping/ShoppingListViewModel.kt:249, 261, 293, 414, 443`, `$B/data/repository/ShoppingRepository.kt:231` | sechs Stellen fragen `findAisleForProduct` einzeln ab; fünf davon im ViewModel | Entscheidung 10; architecture-layers |
| 9 | `$B/data/repository/StoreRepository.kt:137-138` | Zuordnung nur aus Gelerntem; unbekannte Artikel bleiben ohne Gang | Entscheidung 10 |
| 10 | `$T/` | keine Tests für Gang-Zuordnung, `StoreRepository`, Kostenberechnung | Auftrag: Testfälle mit Erwartungswerten |
| 11 | `$SA/main.py:186-196` | `/api/suggestions/aisles` vergleicht exakt, App ruft es nicht auf | **nur dokumentiert**, Phase 4 |
| 12 | `$SA/sync.py:189-218` | Upsert setzt alle Spalten aus dem Client-Datensatz; ein Alt-Client würde neue Spalten leeren | sync-patterns (alte Daten erhalten) |

## Schritte (Stufe B arbeitet nur diese Liste ab)

**Vorgehen, `VS`, `V` und allgemeine Regeln:** wie in [Phase 3a](phase-3a-bons-server-scan.md), Abschnitt „Schritte".
Zusätzlich:
- Datenblöcke aus `$D/aisle-keywords.md` und `$D/aisle-testfaelle.md` werden zeichengenau kopiert.
- Diagramme: keine Chart-Bibliothek, keine neue Abhängigkeit. Keine `Color(0x…)` außerhalb `ui/theme/`.
  Zeichenlogik, die rechnet (Skalierung, Winkel, Zeiträume), liegt in reinen Funktionen mit Unit-Test.

### Block 0 — Basis
- [x] **S0 Baseline** — `VS` und `V` ausführen, Testzahlen unter „Abschluss" notieren;
  `grep -c 'version = 37' $B/data/local/AppDatabase.kt` (erwartet 1). Steht dort nicht 37: stoppen (3a fehlt).

### Block A — Server (abwärtskompatibel; Befunde 6, 12)
- [x] **S1 Gang-Schlüssel im Sync** — Dateien: `$SA/db.py`, `$SA/sync.py`, `$SA/models.py`, neu
  `$ST/test_sync_aisle_category.py`. `store_aisles` bekommt `category_key TEXT NOT NULL DEFAULT ''` (in `SCHEMA` und
  `ADDED_COLUMNS`); `COLUMN_BACKFILL[("store_aisles", "category_key")]` = `UPDATE store_aisles SET category_key = CASE
  aisle_name WHEN 'Obst & Gemüse' THEN 'obst_gemuese' … ELSE '' END` mit den zehn Zeilen aus `$D/kategorien.md`
  („Zuordnung der bisherigen Standardgänge"). `TABLE_COLUMNS["store_aisles"]` und `StoreAisleRecord` um `category_key`
  (Standard `""`). Neu in `sync.py`: `PRESERVE_IF_UNSET = {"store_aisles": ["category_key"]}`; in `push_records` wird
  für diese Spalten der `excluded`-Wert nur gesetzt, wenn das Feld in `record.model_fields_set` liegt (sonst bleibt
  der Server-Wert; beim Neuanlegen gilt der Standardwert). Ungültige Schlüssel → `""` (`categories.coerce`). Test:
  Push mit `category_key` → Pull liefert ihn; Push desselben Gangs **ohne** das Feld und mit neuerem `updated_at`
  (Umbenennung) → Schlüssel bleibt; Push mit `"category_key": ""` → Schlüssel wird geleert; Backfill auf einer
  Alt-DB mit „Obst & Gemüse" und „Kasse" → `obst_gemuese` bzw. `''`. Akzeptanz: `VS` grün.
- [x] **S2 Aggregate** — Dateien: `$SA/receipts.py`, `$SA/receipt_api.py`, `$SA/models.py`, neu
  `$ST/test_receipt_aggregates.py`. `aggregate_rows(db) -> list[dict]` und `GET /api/receipts/aggregates` (vor
  `/{receipt_id}` deklariert) nach Vertrag 6; Gruppierung in Python aus zwei Abfragen (Bons; Positionen mit
  Bon-Feldern). Test mit festen Daten: drei Bons in zwei Monaten und zwei Märkten, einer `provisional`, einer
  gelöscht, einer ohne `purchase_day`; Positionen mit Rabatt, Pfand, leerer Kategorie. Geprüft: `total` je Monat
  (Betrag, `count`, `discount`), `week` für `2026-11-10` = `2026-W46`, `store`-Schlüssel und häufigster Name,
  `category` inkl. `sonstiges` und `pfand`, `product` (Schlüssel = `normalize_item_key(name)`, `quantity`,
  `avg_unit_price`, Pfand fehlt), vorläufige/gelöschte/datumlose Bons fehlen überall, Sortierung, Rundung auf 2
  Stellen, leere DB → `rows == []`. Akzeptanz: `VS` grün.
- [x] **S3 Kategorisieren** — Dateien: `$SA/ai.py`, `$SA/receipts.py`, `$SA/models.py`, `$SA/main.py`, neu
  `$ST/test_categorize_items.py`. `ai.categorize_items(names) -> list[str]` nach Vertrag 5.4 über `_receipt_once`.
  `receipts.categorize(db, names) -> list[dict]`: je Name `product_key = normalize_item_key(name)`; leere Namen →
  `""`; Treffer in `product_categories` direkt; alle übrigen (ohne Dubletten, höchstens 50) in **einem** KI-Aufruf;
  Ergebnisse mit `source = 'ai'` speichern (`sonstiges` wird gespeichert); wirft die KI, bekommen die offenen Namen
  `""` und nichts wird gespeichert. Einträge mit `source = 'user'` werden nie von `ai` überschrieben. Route `POST
  /api/ai/categorize-items` in `main.py` (mehr als 50 Namen → 422 über `Field(max_length=50)`). Test (Attrappe mit
  Aufrufzähler): Treffer aus dem Speicher ohne KI-Aufruf; Fehlende in einem Aufruf, danach gespeichert; zweiter Aufruf
  ohne KI; unbekannter Schlüssel → `sonstiges`; KI wirft → `""`, nichts gespeichert; `user` bleibt; Reihenfolge der
  Antwort = Reihenfolge der Eingabe; 51 Namen → 422. Akzeptanz: `VS` grün.
- [x] **S4 Server-Doku** — Datei: `$S/CLAUDE.md`: Endpunkt-Tabelle um Aggregate und `categorize-items`, Hinweis auf
  `PRESERVE_IF_UNSET` im Abschnitt „Sync-Protokoll" („Neue Spalten an Sync-Tabellen dort eintragen, solange alte
  App-Versionen pushen"). Akzeptanz: `grep -c 'PRESERVE_IF_UNSET' $S/CLAUDE.md` ≥ 1. Danach Block A pushen.

### Block B — App: Daten (Befunde 1, 5, 6)
- [x] **S5 Room v38** — Dateien: `$B/data/local/entity/StoreAisleEntity.kt`, neu
  `$B/data/local/entity/ReceiptAggregateEntity.kt`, `$B/data/local/AppDatabase.kt`, `app/schemas/**/38.json`,
  `$T/SyncCompletenessTest.kt`, neu `$T/Migration37To38Test.kt`. `StoreAisleEntity.categoryKey: String = ""`.
  `ReceiptAggregateEntity` (`receipt_aggregates`): `id: String` (PK, `"$dimension|$period|$key"`), `period`,
  `dimension`, `key`, `label` (`String = ""`), `amount`, `quantity`, `avgUnitPrice`, `discount` (`Double = 0.0`),
  `count: Int = 0`; Indizes `dimension` und `period`. `MIGRATION_37_38` (`internal`): `ALTER TABLE store_aisles ADD
  COLUMN categoryKey TEXT NOT NULL DEFAULT ''`; zehn `UPDATE store_aisles SET categoryKey = '<schluessel>' WHERE
  aisleName = '<alter Name>' AND categoryKey = ''` nach `$D/kategorien.md` (ohne `dirty`: der Server füllt per
  Backfill dieselben Werte); `CREATE TABLE IF NOT EXISTS receipt_aggregates (…)` + zwei Indizes. `version = 38`,
  `addMigrations`. `SyncCompletenessTest`: `ReceiptAggregate` in `intentionallyLocalOnly` („Abzug der
  Server-Auswertung"). Test über `RoomMigrationHarness`: Schema 37, Gänge „Obst & Gemüse", „Drogerie & Haushalt",
  „Kasse" → Migration → `assertMatchesSchema(conn, 38)`; Schlüssel `obst_gemuese`, `drogerie`, `''`. Akzeptanz: `V`
  grün; `38.json` committet.
- [x] **S6 DTOs, Sync-Abbildung, API** — Dateien: `$B/data/remote/dto/SyncDto.kt`, `$B/data/sync/SyncEngine.kt`,
  `$B/data/remote/SyncApi.kt`, `$T/SyncEngineTest.kt` (falls Konstruktoren betroffen), neu `$T/StoreAisleSyncTest.kt`.
  `StoreAisleDto.categoryKey` (`@Json(name = "category_key")`, Standard `""`); Mapper `StoreAisleDto.toEntity`/
  `StoreAisleEntity.toDto` (Z.341–343, 407–410) reichen ihn durch. Neue DTOs `ReceiptAggregateDto`,
  `ReceiptAggregatesResponse(generatedAt, rows)`, `CategorizeRequest(names)`, `CategorizeResultDto(name, category)`,
  `CategorizeResponse(results)`; `SyncApi.receiptAggregates()` (`GET api/receipts/aggregates`),
  `categorizeItems(@Body)` (`POST api/ai/categorize-items`); Test-Attrappen ergänzen. Die Mapper sind `private`:
  der Test prüft über Moshi, dass `StoreAisleDto` ohne `category_key` zu `""` wird und mit Wert serialisiert wird,
  und über `SyncEngine` (Aufbau wie `SyncEngineTest`), dass ein gepullter Gang mit Schlüssel so in `upsertAisles`
  ankommt. Akzeptanz: `V` grün.
- [x] **S7 Aggregat-Abzug** — Dateien: neu `$B/data/local/dao/ReceiptAggregateDao.kt`, `$B/data/local/AppDatabase.kt`,
  `$B/di/DatabaseModule.kt`, `$B/data/receipts/ReceiptSyncer.kt`, `$B/data/receipts/ReceiptMapping.kt`,
  `$B/data/sync/SyncWorker.kt`, `$T/ReceiptSyncerTest.kt`. DAO: `observeAll(): Flow<List<ReceiptAggregateEntity>>`,
  `count()`, `@Transaction replaceAll(rows)` (alles löschen, einfügen). `ReceiptSyncer.refreshAggregates(): Boolean`
  (API → `replaceAll`; Fehler → `false`, Abzug bleibt). `SyncWorker` ruft es nach `pull()` im selben `try/catch`.
  Test: Erfolg ersetzt, `IOException` lässt den Abzug unverändert. Akzeptanz: `V` grün.
- [x] **S8 Auswertungslogik** — Dateien: neu `$B/data/model/ReceiptStatsModels.kt`, neu
  `$B/data/receipts/ReceiptStats.kt`, neu `$B/data/repository/ReceiptStatsRepository.kt`, neu
  `$T/ReceiptStatsTest.kt`. Modelle: `enum class StatsRange { THIS_MONTH, THREE_MONTHS, TWELVE_MONTHS, ALL }`,
  `data class PeriodAmount(period: String, amount: Double, count: Int)`, `data class KeyAmount(key: String, label:
  String, amount: Double, quantity: Double, count: Int)`, `data class TrendPoint(period: String, avgUnitPrice:
  Double)`. `object ReceiptStats` (reines Kotlin, Eingabe `List<ReceiptAggregateEntity>` und `today: LocalDate`):
  `monthPeriods(today, n): List<String>` (älteste zuerst, `YYYY-MM`), `weekPeriods(today, n)` (ISO-Woche über
  `IsoFields.WEEK_BASED_YEAR`/`WEEK_OF_WEEK_BASED_YEAR`, Format `YYYY-Www`), `monthSeries(rows, today, n = 12)` und
  `weekSeries(rows, today, n = 12)` (fehlende Perioden mit 0), `rangeMonths(range, today): Set<String>?` (`ALL` →
  `null`), `storeTotals(rows, range, today)`, `categoryTotals(rows, range, today)`, `topProducts(rows, range, today,
  limit = 10)` (alle drei summieren je `key`, sortieren absteigend nach `amount`, `label` = Label der jüngsten
  Periode), `productTrend(rows, key)` (aufsteigend nach Periode, nur `avgUnitPrice > 0`), `currentMonthTotal(rows,
  today)`, `discountTotal(rows, range, today)`, `monthlyAverage(series)` (Mittel über Perioden mit `amount > 0`).
  `ReceiptStatsRepository` (`@Singleton`): `observeAggregates()`, `suspend fun refresh(): Boolean`
  (`receiptSyncer.refreshAggregates()`), `observeProvisionalCount()`. Test mit festem Datensatz (15 Zeilen, `today =
  2026-11-11`): je Funktion mindestens ein Fall, dazu Jahreswechsel (`2027-01-03` liegt in `2026-W53`), leere Eingabe,
  Gleichstand in `topProducts` (stabil nach `key`). Akzeptanz: `V` grün. Danach Block B pushen.

### Block C — App: Diagramme und Kostenübersicht (Befunde 1–5)
- [x] **S9 Rechenkern der Diagramme** — Dateien: neu `$U/receipts/charts/ChartMath.kt`, neu `$T/ChartMathTest.kt`.
  Reines Kotlin, kein Compose-Import: `niceMax(value: Double): Double` (kleinster Wert aus {1, 2, 2.5, 5, 10} × 10ⁿ,
  der ≥ `value` ist; `value <= 0` → 1.0); `fraction(value, max): Float` (auf 0–1 begrenzt, `max <= 0` → 0);
  `data class ShareSlice(key, label, amount, fraction, startAngle, sweepAngle)` und `shareSlices(items:
  List<KeyAmount>, maxSlices: Int = 5, otherLabel: String): List<ShareSlice>` (nur `amount > 0`, absteigend, ab dem
  `maxSlices + 1`-ten zu einem Eintrag `key = "__other"` zusammengefasst, Start bei −90°, Winkel summieren sich auf
  360°); `linePositions(values: List<Double>): List<Pair<Float, Float>>` (x gleichmäßig 0–1, y = 1 − normiert auf
  Min/Max; ein Wert oder alle gleich → y = 0.5); `periodLabel(period: String): String` (`2026-11` → `11/26`,
  `2026-W46` → `46`). Test: Tabellen für jede Funktion (u. a. `niceMax(0) == 1.0`, `niceMax(137.4) == 200.0`,
  `niceMax(250.0) == 250.0`, sieben Märkte → 5 + „Sonstige", Winkelsumme 360 ± 0,01, leere Liste → leer).
  Akzeptanz: `V` grün.
- [x] **S10 Balkendiagramm** — Dateien: neu `$U/theme/ChartColors.kt`, neu `$U/receipts/charts/SpendingBarChart.kt`,
  `$R/values/strings.xml`. `ChartColors.kt`: `@Composable fun chartSeriesColors(): List<Color>` =
  `colorScheme.primary, tertiary, secondary, error, inversePrimary`; `@Composable fun chartOtherColor() =
  colorScheme.outline`. `data class ChartBar(val label: String, val value: Double, val highlighted: Boolean = false)`.
  `@Composable fun SpendingBarChart(bars: List<ChartBar>, budget: Double?, description: String, modifier: Modifier =
  Modifier)`: `Column`; oben `Text` (labelSmall, `onSurfaceVariant`) mit dem Achsen-Maximum `formatEuro(niceMax(max(
  größter Wert, budget ?: 0)))`; `Canvas` Höhe 180 dp, `fillMaxWidth`, `semantics { contentDescription = description
  }`: Grundlinie 1 dp `outlineVariant`; je Balken gleich breiter Slot, Balkenbreite 60 % des Slots, obere Ecken 4 dp
  (`drawRoundRect` + Abdeckung unten oder `Path`), Farbe `primary` für `highlighted`, sonst `primary.copy(alpha =
  0.45f)`; Budgetlinie waagerecht, 2 dp, `error`, gestrichelt (`PathEffect.dashPathEffect(floatArrayOf(12f, 8f))`),
  nur wenn `budget != null && budget > 0`. Darunter eine `Row` mit je einem `Text` (labelSmall, `weight(1f)`,
  zentriert) pro Balken; bei mehr als 8 Balken nur jedes zweite Label (übrige als leerer Text). Liegt eine
  Budgetlinie an, darunter eine Legendenzeile: 16 dp breiter Strich in `error` + Text „Budget %1$s". Leere `bars` →
  nichts zeichnen. Zwei `@ThemePreviews` (12 Monate mit Budget; 12 Wochen ohne). Akzeptanz: `V` grün;
  `grep -rn 'Color(0x' $U | grep -v theme/` leer.
- [x] **S11 Ringdiagramm** — Datei: neu `$U/receipts/charts/ShareDonutChart.kt`. `@Composable fun
  ShareDonutChart(slices: List<ShareSlice>, centerText: String, description: String, modifier: Modifier = Modifier)`:
  `Canvas` 160 dp quadratisch mit `semantics`; je Scheibe `drawArc(useCenter = false, style = Stroke(width = 28.dp,
  cap = StrokeCap.Butt))`, 1,5° Lücke zwischen Scheiben (bei nur einer Scheibe keine); Farben
  `chartSeriesColors()[index]`, `__other` in `chartOtherColor()`; `centerText` als `Text` (titleMedium) mittig
  darüber (`Box`). Legende darunter als `Column`: je Zeile Farbpunkt 12 dp (`CircleShape`), Label (`weight(1f)`,
  eine Zeile, Ellipsis), Betrag (`formatEuro`), Anteil (`%1$d %%`). Leere `slices` → nur der Leertext des Aufrufers.
  `@ThemePreviews` mit 6 Scheiben. Akzeptanz: `V` grün.
- [x] **S12 Querbalken** — Datei: neu `$U/receipts/charts/HorizontalBarChart.kt`. `data class ChartRow(val key:
  String, val label: String, val value: Double, val detail: String = "")`. `@Composable fun HorizontalBarChart(rows:
  List<ChartRow>, description: String, modifier: Modifier = Modifier, onRowClick: ((String) -> Unit)? = null)`:
  `Column(semantics { contentDescription = description })`; je Zeile: `Row` mit Label (bodyMedium, `weight(1f)`,
  Ellipsis) und `formatEuro(value)`; optional `detail` (bodySmall, `onSurfaceVariant`); darunter `Canvas` Höhe 10 dp:
  Spur `surfaceVariant`, Füllung `primary` bis `fraction(value, größter Wert)`, beide mit Eckenradius 5 dp. Mit
  `onRowClick` ist die Zeile `clickable(role = Role.Button)` und mindestens 48 dp hoch. `@ThemePreviews`.
  Akzeptanz: `V` grün.
- [x] **S13 Liniendiagramm** — Dateien: neu `$U/receipts/charts/TrendLineChart.kt`,
  `$U/receipts/ProductPriceDetailScreen.kt`. `data class ChartPoint(val label: String, val value: Double)`.
  `@Composable fun TrendLineChart(points: List<ChartPoint>, description: String, modifier: Modifier = Modifier)`:
  `Canvas` Höhe 160 dp mit `semantics`; Innenabstand 12 dp; Linie `primary` 2 dp (`Path` über `linePositions`),
  Punkte als Kreise Radius 4 dp `primary` mit 2 dp Rand `surface`; waagerechte Hilfslinien oben und unten 1 dp
  `outlineVariant`; darüber `Row` mit Maximum links und Minimum rechts als Text (`formatEuro`), darunter erstes und
  letztes Label. Weniger als zwei Punkte → Text „Noch zu wenige Käufe für einen Verlauf." statt Canvas. Der bisherige
  `PriceSparklineSection` (`ProductPriceDetailScreen.kt:187-255`) wird durch `TrendLineChart` ersetzt (Punkte aus
  `history.points`, älteste zuerst, Label = Datum `TT.MM.`); gleiches Verhalten, jetzt mit Beschreibung
  „Preisverlauf von %1$s: zuletzt %2$s, niedrigster Preis %3$s, höchster Preis %4$s". `@ThemePreviews`.
  Akzeptanz: `V` grün; `grep -c 'Canvas(' $U/receipts/ProductPriceDetailScreen.kt` = 0.
- [x] **S14 ViewModel der Kostenübersicht** — Dateien: `$U/receipts/CostOverviewViewModel.kt`, neu
  `$T/CostOverviewViewModelTest.kt`. Konstruktor: `ReceiptStatsRepository`, `ReceiptRepository` (Budget),
  `SyncScheduler`. `enum class Granularity { MONTH, WEEK }`; `data class CostOverviewUiState(val loaded: Boolean,
  val empty: Boolean, val provisionalCount: Int, val granularity: Granularity, val range: StatsRange, val bars:
  List<PeriodAmount>, val barBudget: Double?, val average: Double, val monthSpending: Double, val budget: Double,
  val warnThreshold: Double, val stores: List<KeyAmount>, val categories: List<KeyAmount>, val categoryDiscount:
  Double, val topProducts: List<KeyAmount>)`. `uiState: StateFlow<CostOverviewUiState>` aus `combine(
  observeAggregates(), observeBudget(), observeProvisionalCount(), granularity, range)` über `ReceiptStats`
  (`barBudget` = Budget bzw. Budget × 12 / 52, `null` bei Budget 0; `today` über einen injizierbaren `() ->
  LocalDate`-Parameter mit Standard `LocalDate::now`). `init`: `viewModelScope.launch { statsRepository.refresh() }`.
  `setGranularity`, `setRange`, `saveBudget` (unverändert inkl. `triggerOneShot`). `TimePeriod`, `loadData`,
  `loadMonthSpending`, `getEpochRange`, `getMaxCost`, `formatDate`, `formatCurrency` entfallen. Test (Attrappen-
  Repositories mit `MutableStateFlow`, `kotlinx-coroutines-test`, `turbine`): Zustand für festen Datensatz; Wechsel
  Monat ↔ Woche ändert `bars` und `barBudget`; Wechsel des Zeitraums ändert `stores`; leerer Abzug → `empty == true`.
  Akzeptanz: `V` grün; `ArchitectureRulesTest` grün.
- [x] **S15 Screen der Kostenübersicht** — Dateien: `$U/receipts/CostOverviewScreen.kt`, `$B/HelgaNavGraph.kt`,
  `$R/values/strings.xml`. `CostOverviewScreen(onBack, onProductClick, onAllProductsClick, viewModel)` sammelt State;
  `internal fun CostOverviewContent(state, <Callbacks>, modifier)` als `LazyColumn` (Abstand 16 dp, je Abschnitt eine
  `Card` mit Titel `titleMedium`), Reihenfolge:
  1. Hinweis (nur `provisionalCount > 0`, Plural `cost_provisional_hint`: „%1$d vorläufiger Bon ist noch nicht
     enthalten." / „%1$d vorläufige Bons sind noch nicht enthalten.").
  2. Bei `empty`: Leerzustand (Icon `BarChart`, Text „Noch keine Auswertung. Sie erscheint nach dem ersten Abgleich
     mit dem Server.") und sonst nichts außer der Budget-Karte.
  3. Budget-Karte (bestehende `BudgetCard`/`BudgetEditDialog`, Wert aus `state.monthSpending`).
  4. „Ausgaben": `SingleChoiceSegmentedButtonRow` „Monat" | „Woche"; `SpendingBarChart` (Label `periodLabel`,
     letzter Balken `highlighted`); darunter „Ø %1$s je Monat" bzw. „… je Woche".
  5. `Row` aus vier `FilterChip` für `StatsRange` („Dieser Monat", „3 Monate", „12 Monate", „Gesamt").
  6. „Märkte": `ShareDonutChart` (`centerText` = Gesamtsumme; Label leer → „Unbekannter Markt"; `otherLabel`
     „Sonstige"); ohne Daten Text „Keine Bons in diesem Zeitraum."
  7. „Kategorien": `HorizontalBarChart` (Label über `ProductCategory.fromKey(key).labelRes()`); Fußnote „Bon-Rabatte
     in diesem Zeitraum: %1$s" nur bei `categoryDiscount > 0`.
  8. „Top-Artikel": `HorizontalBarChart` mit `detail` „%1$d× gekauft", `onRowClick = onProductClick`; darunter
     `TextButton` „Alle Artikel" → `onAllProductsClick`.
  Beschreibungen der Diagramme als Strings mit den wichtigsten Zahlen (z. B. „Ausgaben der letzten 12 Monate, zuletzt
  %1$s, Durchschnitt %2$s"). `showBudgetDialog` → `rememberSaveable`. Die alten `CostSummaryCard`/`CostBarItem`
  entfallen. Zwei `@ThemePreviews` (mit Daten, leer). `HelgaNavGraph`: `onProductClick` →
  `ROUTE_PRODUCT_PRICE_DETAIL` (Schlüssel URL-kodiert wie beim bestehenden Aufruf aus der Preisliste),
  `onAllProductsClick` → `ROUTE_RECEIPTS_PRODUCTS`. Akzeptanz: `V` grün; `G-S $U/receipts/CostOverviewScreen.kt`
  regelkonform; `grep -c 'viewModel\.' $U/receipts/CostOverviewScreen.kt` nur innerhalb von `CostOverviewScreen`.
  Prüfliste (Gerät): alle vier Diagramme hell und dunkel und mit jeder Akzentfarbe lesbar; Budgetlinie sitzt auf Höhe
  des Budgets; Flugmodus → Diagramme bleiben; Wechsel Monat/Woche und der Zeitraum-Chips wirkt; Tippen auf einen
  Top-Artikel öffnet dessen Preisverlauf; TalkBack liest zu jedem Diagramm die Beschreibung; Querformat/Tablet:
  Inhalt bleibt auf 840 dp begrenzt.
- [x] **S16 Preisentwicklung und Aufräumen** — Dateien: `$U/receipts/ProductPriceDetailViewModel.kt`,
  `$U/receipts/ProductPriceDetailScreen.kt`, `$U/receipts/ProductPriceListScreen.kt`,
  `$B/data/repository/ReceiptRepository.kt`, `$B/data/local/dao/ReceiptDao.kt`, `$B/data/model/ReceiptCosts.kt`,
  `$R/values/strings.xml`. ViewModel zusätzlich: `trend: StateFlow<List<TrendPoint>>` =
  `statsRepository.observeAggregates().map { ReceiptStats.productTrend(it, productKey) }`. Screen: neuer Abschnitt
  „Preisentwicklung (alle Monate)" mit `TrendLineChart` (Label `periodLabel`) **über** dem bestehenden Verlauf, der
  die Überschrift „Einzelkäufe der letzten Monate" bekommt; gibt es lokal keine Einzelkäufe mehr (`history == null`),
  aber Trendpunkte, zeigt der Screen nur den Trend und den Text „Einzelne Käufe liegen nur für die letzten Monate auf
  dem Handy." Preisliste: Untertitel-Zeile „Aus den Bons der letzten Monate". Löschen, sofern per `grep` unbenutzt:
  `ReceiptDao.costByStore/costByStoreRange/costByDate/costByDateRange/totalCostForRange/receiptsInRange/
  observeReceiptsForStore`, `ReceiptRepository.totalCost/costByStore/costByDate`, `CostByStore`/`CostByDate`/
  `CostSummary`. Akzeptanz: `V` grün; `grep -rn 'costByStore\|CostSummary' $B` leer. Prüfliste (Gerät): Artikel, der
  seit über 3 Monaten nicht gekauft wurde, zeigt über „Top-Artikel → Gesamt" weiterhin seine Preisentwicklung. Danach
  Block C pushen.

### Block D — Gang-Kategorien und Auto-Zuordnung (Befunde 6–10)
- [x] **S17 Repository der Märkte** — Dateien: `$B/data/model/ProductCategory.kt`,
  `$B/data/repository/StoreRepository.kt`, `$B/data/local/dao/StoreDao.kt`, neu `$T/StoreRepositoryAisleTest.kt`.
  `ProductCategory` bekommt `val defaultAisleName: String` (deutscher Name aus dem Katalog; Datenwert wie der
  bisherige `DEFAULT_AISLE_TEMPLATE`, kein UI-Text). `DEFAULT_AISLE_TEMPLATE` wird zu
  `ProductCategory.aisleCategories` (15 Gänge, Name + Schlüssel). `createStore`: Vorlage setzt `categoryKey`;
  `CopyFrom` kopiert Name **und** Schlüssel. `addAisle(storeId, aisleName, categoryKey: String = "")`: ist der
  Schlüssel leer und der getrimmte Name gleich (ohne Groß-/Kleinschreibung) einem `defaultAisleName`, dessen
  Schlüssel setzen. Neu: `setAisleCategory(aisle, categoryKey)` (`updatedAt`, `dirty = 1`),
  `addMissingDefaultAisles(storeId): Int` (hängt für jede Gang-Kategorie ohne Gang mit diesem Schlüssel einen Gang
  ans Ende an), `aislesForStore(storeId)`. Test (`mockk`-DAO mit Aufzeichnung der `upsert`-Aufrufe): Vorlage erzeugt
  15 Gänge mit Schlüsseln in Katalogreihenfolge; Kopie übernimmt Schlüssel; `addAisle("Fisch")` setzt `fisch`,
  `addAisle("Kasse")` setzt nichts; `addMissingDefaultAisles` bei den 10 alten Standardgängen ergänzt genau
  die fünf Gänge mit den Schlüsseln `wuerzen_backen`, `fruehstueck`, `alkohol`, `feinkost`, `haushalt` und beim
  zweiten Aufruf keinen mehr; jede Mutation setzt `dirty = 1`. Akzeptanz: `V` grün.
- [x] **S18 Gang-Editor** — Dateien: `$U/stores/StoreListScreen.kt`, `$U/stores/StoreListViewModel.kt`,
  `$R/values/strings.xml`. ViewModel: `setAisleCategory(aisle, key)`, `addMissingDefaultAisles()` (beide mit
  `triggerOneShot`). `AisleRow` (≈ Z.358): `supportingContent` = Kategorie-Name bzw. „Ohne Kategorie"
  (`onSurfaceVariant`); neuer `IconButton` (Icon `Icons.Filled.Category`, `contentDescription` „Kategorie wählen")
  öffnet ein `DropdownMenu` mit „Ohne Kategorie" und den 15 Gang-Kategorien (aktuelle mit Häkchen). Im
  `AisleEditorSheet` unter dem Hinweis zum Ziehen: Text (bodySmall) „Die Kategorie bestimmt, welche Artikel
  automatisch in diesen Gang sortiert werden." und `TextButton` „Fehlende Standardgänge ergänzen". Akzeptanz: `V`
  grün; `G-S $U/stores/StoreListScreen.kt` regelkonform. Prüfliste (Gerät): Kategorie eines Gangs ändern → auf dem
  zweiten Handy nach Sync sichtbar; neuer Markt mit Vorlage hat 15 Gänge; Ziehen der Reihenfolge funktioniert weiter;
  TalkBack liest Gangname und Kategorie.
- [x] **S19 Stichwortliste** — Dateien: neu `$B/data/util/AisleKeywords.kt`, neu `$T/AisleKeywordsTest.kt`.
  `object AisleKeywords { internal const val RAW = """…""" ; data class Entry(val word: String, val category: String,
  val priority: Boolean); val entries: List<Entry> by lazy { parse(RAW) } ; internal fun parse(raw: String):
  List<Entry> }` — `RAW` ist der Datenblock aus `$D/aisle-keywords.md` zeichengenau; `parse` nach „Format des
  Datenblocks" (Wörter mit `AisleKeywordMatcher.normalize` normalisieren; die Funktion entsteht in S20 — für diesen
  Schritt `normalize` bereits als `internal fun` in `AisleKeywords.kt` anlegen und in S20 von dort verwenden). Test:
  Gesamtzahl 605, Anzahl je Kategorie nach der Tabelle „Prüfzahlen", 5 Vorrang-Einträge, 26 Phrasen, keine Dublette,
  jede Kategorie ist ein Gang-Schlüssel aus `ProductCategory.aisleCategories`. Akzeptanz: `V` grün.
- [x] **S20 Stichwort-Zuordnung** — Dateien: neu `$B/data/util/AisleKeywordMatcher.kt`, neu
  `$T/AisleKeywordMatcherTest.kt`. `object AisleKeywordMatcher { fun normalize(raw: String): String; fun
  categoryFor(name: String): String }` exakt nach „Algorithmus" in `$D/aisle-keywords.md` (Schritte 1–6, gleiche
  Reihenfolge der Entscheidungskriterien). Test, Blöcke aus `$D/aisle-testfaelle.md` als Raw-Strings: (1) **Liste A**:
  alle 150 Zeilen exakt, Fehlermeldung nennt jede Abweichung; (2) **Liste B**: Trefferzahl ≥ 54 von 60, die
  Trefferzahl wird per `println("Stichprobe: $hits/60")` ausgegeben; (3) die fünf bekannten Fehlzuordnungen liefern
  genau das dort genannte Ist-Ergebnis; (4) Einzelfälle der Regeln: `"  TOMATEN (klein) "` → `obst_gemuese`,
  `"H-Milch"` → `milch_eier` (Phrase nach Normalisierung), `"Dosentomaten"` → `trockenware` (Vorrang, Wortanfang),
  `"Eis"` → `tiefkuehl`, `"Tee"` → `fruehstueck` und `"Eistee"` → `getraenke` (längstes Stichwort), `"Türkei"` und
  `"Brei"` → `""` (Wörter unter 4 Zeichen treffen nur als ganzes Wort), `""` → `""`, `"Geschenkpapier"` → `""`.
  Akzeptanz: `V` grün; die ausgegebene Trefferzahl der Stichprobe unter „Abschluss" notieren (erwartet 55/60).
- [x] **S21 `AisleAssigner` (lokal)** — Dateien: neu `$B/data/shopping/AisleAssigner.kt`, neu
  `$T/AisleAssignerTest.kt`. `@Singleton class AisleAssigner(storeDao, shoppingDao, apiProvider)`: `suspend fun
  aisleForCategory(storeId: String, categoryKey: String): String` (Gänge des Markts nach `sortOrder`; erster mit dem
  Schlüssel, sonst erster mit `ProductCategory.fallbackFor(...)`, sonst `""`; `sonstiges`, `pfand`, unbekannt →
  `""`); `suspend fun resolveLocal(productName: String, storeId: String?): String` (`storeId == null` → `""`;
  1. `storeDao.findAisleForProduct(AisleProductKey.normalize(productName), storeId)`, 2.
  `aisleForCategory(storeId, AisleKeywordMatcher.categoryFor(productName))`). Test: Fälle 1–9 der **Liste C** aus
  `$D/aisle-testfaelle.md` mit `mockk`-DAOs; für Fall 9 zusätzlich `coVerify(exactly = 0)` auf jeden API-Aufruf;
  zwei Gänge mit gleichem Schlüssel → kleinere `sortOrder` gewinnt. Akzeptanz: `V` grün.
- [x] **S22 Einbau in die Einkaufsliste** — Dateien: `$B/data/repository/ShoppingRepository.kt`,
  `$U/shopping/ShoppingListViewModel.kt`, betroffene Tests in `$T` (alle, die `ShoppingRepository` konstruieren),
  neu `$T/ShoppingAisleAssignmentTest.kt`. `ShoppingRepository` injiziert `AisleAssigner`; `addItem`: ist `aisle`
  leer, `aisleAssigner.resolveLocal(name, storeRepository.findActiveStoreId())` verwenden; `addOrMergeItem`
  (≈ Z.231) nutzt dieselbe Zeile statt `findAisleForProduct`. Im ViewModel entfallen die fünf eigenen Abfragen
  (Z.249, 261, 293, 414, 443) samt der lokalen `storeId`/`aisle`-Variablen; die Aufrufe übergeben keinen `aisle`
  mehr. `assignAisle` (Handkorrektur, lernt über `saveAisleProduct`) bleibt unverändert. Test: `addItem("Tomaten")`
  bei aktivem Markt mit Gang `obst_gemuese` speichert diesen Gangnamen; explizit übergebener `aisle` bleibt;
  ohne aktiven Markt leer; `addOrMergeItem` für einen neuen Artikel ebenso. Akzeptanz: `V` grün;
  `grep -c 'findAisleForProduct' $U/shopping/ShoppingListViewModel.kt` = 0. Prüfliste (Gerät): „Tomaten", „Milch",
  „Spülmittel", „Bier" landen ohne Zutun im passenden Gang; ein von Hand umsortierter Artikel landet beim nächsten Mal
  im gewählten Gang.
- [x] **S23 Server-KI für Unbekanntes** — Dateien: `$B/data/shopping/AisleAssigner.kt`,
  `$B/data/local/dao/ShoppingDao.kt`, neu `$B/data/sync/AisleCategorizeWorker.kt`, `$B/data/sync/SyncScheduler.kt`,
  `$B/data/repository/ShoppingRepository.kt`, `$T/AisleAssignerTest.kt`. `ShoppingDao.uncheckedItemsWithoutAisle()`
  (`aisle = '' AND isChecked = 0 AND deleted = 0`). `AisleAssigner.assignRemote(): Int`: aktiver Markt fehlt → 0;
  offene Artikel laden; zuerst je Artikel `resolveLocal` (inzwischen angelegte Gänge/Gelerntes) und Treffer sofort
  schreiben; für die übrigen die unterschiedlichen Namen (höchstens 50) an `categorizeItems`; je Ergebnis mit
  `aisleForCategory(...) != ""`: alle offenen Artikel dieses Namens auf den Gang setzen (`updatedAt`, `dirty = 1`)
  und `saveAisleProduct`-gleich lernen (Eintrag in `aisle_products` mit `AisleProductKey.normalize(name)`,
  `dirty = 1`); Ausnahmen des API-Aufrufs werden geloggt und ergeben 0. Rückgabe = Anzahl geänderter Artikel.
  `AisleCategorizeWorker` (`@HiltWorker`): `assignRemote()`, bei > 0 `syncScheduler.triggerOneShot()`, immer
  `Result.success()`. `SyncScheduler.enqueueAisleCategorize()`: `NetworkType.CONNECTED`, Startverzögerung 5 s,
  `enqueueUniqueWork("helga.aisle.categorize", ExistingWorkPolicy.KEEP, …)`; in `cancelAll()` aufnehmen.
  `ShoppingRepository` injiziert `SyncScheduler` und ruft `enqueueAisleCategorize()`, wenn ein neuer Artikel nach
  `resolveLocal` ohne Gang bleibt **und** ein Markt aktiv ist. Test: Fälle 10–12 der Liste C; ein Name mit zwei
  offenen Artikeln ändert beide und lernt einmal; kein aktiver Markt → kein API-Aufruf. Akzeptanz: `V` grün.
  Prüfliste (Gerät, Server mit KI): „Geschenkpapier" oder „Leinsamen" hinzufügen → nach einigen Sekunden im passenden
  Gang; erneut hinzufügen → sofort (gelernt); im Flugmodus bleibt der Artikel ohne Gang und die App reagiert sofort.
  Danach Block D pushen.

### Block E — Dokumentation und Abschluss
- [x] **S24 Guidelines, Pläne, Index** — Dateien: `.claude/guidelines/ux-accessibility.md` (Abschnitt 13 um drei
  Zeilen: Diagramme aus `ui/receipts/charts/`, Farben über `chartSeriesColors()`, jedes Diagramm mit Beschreibung und
  Werten als Text), `.claude/guidelines/sync-patterns.md` (Zeile zu `PRESERVE_IF_UNSET` in der Server-Checkliste),
  `.claude/features/bons-kosten/plan.md` (Abschnitt „Phase 3b", Umfang um `charts/`), `.claude/features/maerkte/plan.md`
  und `.claude/features/einkaufsliste/plan.md` (Abschnitt „Phase 3b": Gang-Kategorien, Auto-Zuordnung, gemessene
  Trefferquote, bekannte Grenzen), `.claude/features/README.md` (Stand: DB v38, Entity-Zahl),
  `.claude/phasen/README.md` (Phase 3 → „umgesetzt (Stufe B)"). Akzeptanz: Dateien geändert; `V` und `VS` grün.

## Nicht-Ziele
- Chart-Bibliothek, Animationen, Zoom/Tooltip/Antippen einzelner Balken, Export der Auswertung.
- Budget je Markt oder Kategorie, feinere Budget-Zeiträume (Interview: monatlich reicht).
- Preiswarnungen (Interview: Ansicht auf Abruf reicht).
- Mehrere Kategorie-Schlüssel je Gang, frei definierbare Kategorien, Bearbeiten des Katalogs in der App.
- Nachträgliches Umsortieren bereits zugeordneter Artikel; Zuordnung abgehakter Artikel.
- Lemmatisierung oder unscharfe Suche in der Stichwortliste; Korrektur der fünf bekannten Fehlzuordnungen.
- Kategorie an Einkaufslisten-Artikeln speichern (es bleibt beim Gangnamen in `ShoppingItemEntity.aisle`).
- `/api/suggestions/aisles` und der restliche Server → Phase 4.
- `:wear`-Modul und `ui/widget/`.

## Abschluss

Stand nach Stufe B (Sonnet 5.5), alle Schritte S0–S24 umgesetzt und committet. **Nicht auf einem Gerät geprüft** (kein
Emulator in der Umgebung, kein echter KI-Schlüssel): alles unter „Prüfliste (Gerät)" ist offen.

- [x] Baseline aus S0 notiert: Server 91 Tests, App 213 Tests (alle grün).
- [x] `VS` grün: **110** Server-Tests (+19). `V` grün: **282** App-Tests (+69), `:app:compileDebugKotlin` grün.
- [x] `app/schemas/com.helga.android.data.local.AppDatabase/38.json` committet, `Migration37To38Test` grün.
- [x] Trefferquote der Auto-Zuordnung (Ausgabe aus S20): Liste A **150/150**, Liste B **55/60** (91,7 %); die fünf
      bekannten Fehlzuordnungen sind als eigene Zusicherung im Test festgehalten.
- [x] Bereichs-`plan.md` (bons-kosten, maerkte, einkaufsliste), Feature-Index, Guidelines (`ux-accessibility`,
      `sync-patterns`) und Phasen-README aktualisiert (S24).
- [x] Gemeldete Abweichungen und Ausnahmen:
  - S14: `CostOverviewViewModel` hat einen `internal`-Primärkonstruktor (mit `today`-Lieferant) und einen
    `@Inject`-Sekundärkonstruktor, damit der Test das Datum festlegen kann; Hilt baut damit fehlerfrei.
  - S16: Die bisherige Linie „Preisverlauf" aus den lokalen Einzelkäufen bleibt zusätzlich zur neuen Linie
    „Preisentwicklung (alle Monate)" bestehen; auf dem Artikel-Screen stehen also bis zu zwei Liniendiagramme.
    Der String `product_prices_history_title` („Kaufverlauf") wurde durch `price_single_purchases_title` ersetzt.
  - S17: `addAisle` setzt bei einem erkannten Standardnamen nur den Schlüssel; der Gangname bleibt wie eingegeben
    (nur getrimmt), z. B. „fisch" bleibt „fisch".
  - S23: `assignRemote` gibt die tatsächlich geänderte Zahl zurück — auch wenn lokale Treffer geschrieben wurden und
    der anschließende Server-Aufruf scheitert (Plantext: „ergeben 0"). Die Server-KI-Zuordnung wird mit
    `AisleProductKey.normalize` gelernt, das Wortendungen kürzt (z. B. „Geschenkpapier" → „geschenkpapi"); das ist das
    bestehende Verhalten des Lexikons.
  - S22: Alle fünf Aufrufstellen im `ShoppingListViewModel` übergeben keinen Gang mehr; `grep -c findAisleForProduct`
    liefert dort 0.
  - Server-Test der KI-Kategorisierung nutzt einen Mock; ein echter Haiku-Aufruf wurde nicht ausgeführt.
- [ ] **Prüfliste (Gerät)** für den Nutzer — von Stufe B bewusst nicht abgehakt:
  - **Kostenübersicht (S15):** alle vier Diagramme hell und dunkel und mit jeder Akzentfarbe lesbar; Budgetlinie sitzt
    auf Höhe des Budgets; Flugmodus → Diagramme bleiben; Wechsel Monat/Woche und der Zeitraum-Chips wirkt; Tippen auf
    einen Top-Artikel öffnet dessen Preisverlauf; TalkBack liest zu jedem Diagramm die Beschreibung; Querformat/Tablet:
    Inhalt bleibt auf 840 dp begrenzt.
  - **Preisentwicklung (S16):** Ein Artikel, der seit über 3 Monaten nicht gekauft wurde, zeigt über
    „Top-Artikel → Gesamt" weiterhin seine Preisentwicklung (mit dem Hinweis zu den Einzelkäufen).
  - **Gang-Editor (S18):** Kategorie eines Gangs ändern → auf dem zweiten Handy nach Sync sichtbar; neuer Markt mit
    Vorlage hat 15 Gänge; Ziehen der Reihenfolge funktioniert weiter; TalkBack liest Gangname und Kategorie.
  - **Einkaufsliste (S22):** „Tomaten", „Milch", „Spülmittel", „Bier" landen ohne Zutun im passenden Gang; ein von Hand
    umsortierter Artikel landet beim nächsten Mal im gewählten Gang.
  - **Server-KI (S23, Server mit KI):** „Geschenkpapier" oder „Leinsamen" hinzufügen → nach einigen Sekunden im
    passenden Gang; erneut hinzufügen → sofort (gelernt); im Flugmodus bleibt der Artikel ohne Gang und die App
    reagiert sofort.
