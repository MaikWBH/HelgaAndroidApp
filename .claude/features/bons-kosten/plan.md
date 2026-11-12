# Feature: Bons & Kosten

> **Status:** Interview erledigt · **Aufgaben:** 1 offen (3 erledigt) · **Stand:** 2026-08-31 · **Priorität:** ⭐⭐

Kassenbon-Scan, Preisverlauf und Ausgabenüberblick. Der größte Bereich, der in keiner
bisherigen Dokumentation auftauchte.

## Umfang

| Ebene | Dateien |
|-------|---------|
| UI | `app/src/main/kotlin/com/helga/android/ui/receipts/` — `ReceiptScanScreen.kt`, `ReceiptListScreen.kt`, `ReceiptDetailScreen.kt`, `ProductPriceListScreen.kt`, `ProductPriceDetailScreen.kt`, `CostOverviewScreen.kt` je mit ViewModel (12 Dateien) |
| Room | `app/src/main/kotlin/com/helga/android/data/local/entity/ReceiptEntity.kt`, `ReceiptItemEntity.kt`, `MonthlyBudgetEntity.kt` |
| DAO | `app/src/main/kotlin/com/helga/android/data/local/dao/ReceiptDao.kt`, `MonthlyBudgetDao.kt` |
| Repository | `app/src/main/kotlin/com/helga/android/data/repository/ReceiptRepository.kt` |
| Hilfsklassen | `app/src/main/kotlin/com/helga/android/data/util/ReceiptImagePreprocessor.kt`, `ReceiptItemNormalizer.kt` |
| Bon-Archiv (Phase 3a) | `app/src/main/kotlin/com/helga/android/data/receipts/` — `ReceiptSyncer`, `ReceiptUploader`, `ReceiptImageStore`, `ReceiptScanFlow`, `ReceiptReviewCheck`, `ReceiptMapping`, `ReceiptDates`; `ReceiptLexiconEntity`/`ReceiptLexiconDao`, `ReceiptLexiconRepository` |
| Server | `/api/ai/parse-receipt`, `/api/receipts/reconcile` in `server/app/main.py`; Bon-Archiv: `server/app/receipt_api.py`, `receipts.py`, `receipt_text.py`, `categories.py`, `settings.py`, `auth.py` (`/api/receipts…`) |
| Routen | 6 der 21 Navigationsrouten in `app/src/main/kotlin/com/helga/android/HelgaNavGraph.kt` |

## Ist-Analyse

- **Scannen** (`ReceiptScanViewModel`): Foto aufnehmen, Vorverarbeitung zur Bildqualität
  (`ReceiptImagePreprocessor`), Erkennung per Vision-Modell über `/api/ai/parse-receipt`.
  Positionen sind in der Vorschau manuell korrigierbar (`updateItem`, `removeItem`,
  `updateTotal`), Markt wird zugeordnet (`selectStore`, `updateStoreName`). Unsichere Positionen
  werden über eine Konfidenzprüfung markiert, die Scan-Quelle (KI oder lokal) wird angezeigt.
- **Normalisierung:** `ReceiptItemNormalizer` überführt Bon-Kürzel in vergleichbare
  Produktschlüssel — Grundlage für den Preisverlauf.
- **Abgleich** (`ReceiptDetailViewModel.reconcile`): gescannter Bon wird gegen die
  Einkaufsliste abgeglichen (`/api/receipts/reconcile`); das Ergebnis unterscheidet getroffene,
  fehlende und unerwartete Positionen (`matches`, `missing`, `unexpected` in `SyncDto`).
  Abschaltbar über die Einstellungen (`setReceiptReconciliationEnabled`).
- **Preisverlauf** (`ProductPriceListViewModel`, `ProductPriceDetailViewModel`): Produktliste mit
  Suche, je Produkt ein Verlauf der bezahlten Preise über die Zeit.
- **Ausgabenüberblick** (`CostOverviewViewModel`): Summen je Zeitraum, je Markt und je Datum,
  Monatsbudget mit Warnschwelle (`saveBudget`).
- **Rückkopplung in die Einkaufsliste:** Die Kostenschätzung der Einkaufsliste zieht ihre Preise
  aus diesem Verlauf; nach einem Einkauf erinnert die App an den Bon-Scan.

## Bekannte Lücken

### Funktion & UX
**Root Cause bestätigt:** Der gesamte Bereich hat genau zwei Einstiege, beide innerhalb der
Einkaufsliste — `ShoppingListScreen.kt:301-308` (Menüpunkt „Kassenzettel" im Dreipunkt-Overflow,
dritter Eintrag) und `ShoppingListScreen.kt:390-392` (bedingtes Erinnerungs-Banner nach
Einkaufsabschluss). Kein Bottom-Nav-Tab, kein Zugriff außerhalb der Einkaufsliste
(`HelgaNavGraph.kt:83-87`, `bottomNavItems` enthält nur Einkaufsliste/Rezepte/Wochenplan). Das
erklärt den Befund aus dem Einkaufslisten-Interview (Kostenschätzung ungenutzt, weil Scannen zu
mühsam wirkt) direkter als angenommen: Nicht die Erkennungsqualität ist das Problem, sondern die
Auffindbarkeit selbst.

### Code-Qualität
- `items()` ohne `key`: `ProductPriceDetailScreen.kt:117` (history.points).
- `ReceiptListViewModel` stellt nur `receipts` bereit — keine Suche, kein Filter, keine
  Sortierung. Bei wachsender Bon-Zahl absehbar zu wenig.
- Kein `!!` in diesem Bereich.

### Tests
Keine. `ReceiptItemNormalizer` ist die Kernlogik des gesamten Preisverlaufs und reine
String-Verarbeitung — ohne Tests ist jede Änderung daran riskant.

### Sync
`receipts`, `receiptItems` und `monthlyBudgets` sind vollständig angebunden. Keine Lücke.

## Fragen

1. **Wie zuverlässig erkennt der Scan die Positionen — wie oft musst du korrigieren?**
   Antwort: Scannt kaum noch. Grund ist nicht die Erkennungsqualität, sondern dass der
   Einstiegspunkt zu versteckt ist — sollte von außerhalb der Einkaufsliste per Klick
   erreichbar sein.
2. **Scannst du jeden Bon oder nur ausgewählte?**
   Antwort: Fast nie (gleicher Grund wie oben).
3. **Nutzt du den Abgleich mit der Einkaufsliste, und was tust du mit dem Ergebnis?**
   Antwort: Würde Mehrwert bringen, bringt aber nichts, solange kaum gescannt wird — hängt
   direkt an Frage 1/2.
4. **Preisverlauf: aktive Warnung bei Preissteigerung oder Ansicht auf Abruf?**
   Antwort: Ansicht auf Abruf reicht.
5. **Budget: feinere Zeiträume oder je Markt nötig?**
   Antwort: Monatlich reicht, keine Änderung.
6. **Fehlt eine Auswertung, die manuell zusammengerechnet wird?**
   Antwort: Nein.
7. **Sollen Bons automatisch nach einer Zeit entfernt werden?**
   Antwort: Ja, aber einstellbar — Startwert 3 Monate.
8. **Braucht der Scan einen Offline-Weg?**
   Antwort: Server-Pflicht ist akzeptabel, kein Offline-Weg nötig.
   **Überholt durch Phase 3 (Entscheidung 4):** Offline wird gepuffert; bis der Server erreichbar ist, zeigt die App
   vorläufige ML-Kit-Werte, die Auswertung wird nachgeholt.
9. **Nachfrage — wo soll der Ein-Klick-Zugang liegen?**
   Antwort: Eigener Bottom-Nav-Tab (viertes Icon neben Einkaufsliste/Rezepte/Wochenplan), führt
   auf `ReceiptListScreen` — die bietet bereits Scan/Kostenüberblick/Preisverlauf als
   Unterpunkte, keine neue Screen-Struktur nötig.

## Ziele

- Bon-Scan und Preisverlauf aus dem Alltag erreichbar machen, statt im Einkaufslisten-Menü
  vergraben zu bleiben — das ist die eigentliche Ursache für die Nichtnutzung, nicht die
  Scan-Qualität.
- Bestehende Funktionalität (Abgleich, Preisverlauf, Budget) unverändert lassen — sie wurde im
  Interview inhaltlich nicht beanstandet, nur der Zugang dazu.
- Bon-Verlauf soll nicht unbegrenzt wachsen, aber die Aufbewahrungsdauer soll der Nutzer selbst
  bestimmen können.

## Backlog

Aufwand: S (< 1 h) · M (halber Tag) · L (mehrere Tage)

- [x] **A1** — Unit-Tests für `ReceiptItemNormalizer` · M · Impact hoch —
      **umgesetzt (2026-09-01):** `ReceiptItemNormalizerTest.kt`, 9 Tests — Kleinschreibung,
      Trimmen, Kollabieren mehrfacher Leerzeichen, Entfernen einer angehängten einzelnen
      Steuerklassen-Buchstabens/„EUR"/„€" (nur wenn durch Whitespace abgetrennt — ein direkt
      angehängtes „1kg" bleibt bewusst unangetastet, da keine echte Steuerklasse), leere Eingabe
- [x] **A2** — `key`-Parameter in `ProductPriceDetailScreen.kt` ergänzen · S · Impact mittel —
      **umgesetzt:** `history.points` hat kein `id`-Feld, zusammengesetzter Schlüssel
      `key = { "${it.purchaseDate}_${it.storeId}" }`
- [x] **A3** — Vierten Bottom-Nav-Tab „Bons" ergänzen, der auf `ROUTE_RECEIPTS` zeigt; Menüpunkt
  „Kassenzettel" und das Erinnerungs-Banner in der Einkaufsliste können bleiben (zusätzlicher
  Weg schadet nicht) · M · Impact hoch — behebt die Kernursache aus dem Interview. Laut
  [statistik](../statistik/plan.md) A2 soll derselbe Tab auch den (aktuell unverdrahteten)
  Statistik-Screen erreichbar machen — bei Umsetzung zusammen planen, nicht zwei separate
  Tabs bauen — **umgesetzt:** `ROUTE_RECEIPTS` ist jetzt vierter Root-Route/Bottom-Nav-Eintrag
  (Icon `ReceiptLong`, String `nav_receipts` = „Bons"); Zurück-Pfeil auf `ReceiptListScreen`
  entfernt (wie die anderen drei Root-Screens); neuer Insights-Button in der TopBar führt zu
  `StatsScreen` (siehe [statistik](../statistik/plan.md) A2)
- [x] **A4** — Einstellbare automatische Löschung alter Bons (Einstellungen: Zeitraum, Default 3
  Monate; WorkManager- oder Sync-getriggerte Bereinigung von `ReceiptEntity`/`ReceiptItemEntity`
  älter als Grenze) · M · Impact mittel — **umgesetzt:** Sync-getriggert statt eigener
  WorkManager-Job — `SyncWorker.doWork()` ruft `purgeOldReceipts()` als allerersten Schritt auf,
  unabhängig von Server-Konfiguration, läuft also bei jedem Worker-Lauf (periodisch,
  Connectivity-Change, App-Vordergrund) auch offline. Neue `AppPreferences.receiptRetentionMonths`
  (0/1/3/6/12, Default 3; 0 = nie löschen), `ReceiptDao.findIdsOlderThan()` +
  `ReceiptRepository.purgeReceiptsOlderThan()` nutzen exakt denselben Soft-Delete-Pfad wie das
  bestehende manuelle `deleteReceipt()` (kein rohes `DELETE`, `deleted=1`/`dirty=1` für den
  Sync). Neuer Einstellungen-Abschnitt „Bons" mit Dropdown, analog zum bestehenden
  `SettingsPlanDaysDropdown`-Muster.

_Weitere Aufgaben nach dem Interview._

## Entscheidungen

| Datum | Entscheidung | Begründung |
|-------|--------------|------------|
| 2026-08-30 | Vierter Bottom-Nav-Tab statt App-Shortcut oder TopBar-Icon | Nutzer bevorzugt den klassischen, immer sichtbaren Weg gegenüber weniger entdeckbaren Alternativen |
| 2026-08-30 | Bestehende Einstiege (Menüpunkt, Erinnerungs-Banner) bleiben zusätzlich bestehen | Kein Grund zum Entfernen, der neue Tab ergänzt statt ersetzt |
| 2026-08-30 | Automatische Bon-Löschung wird einstellbar, nicht fest | Nutzer will Kontrolle über die Aufbewahrungsdauer, Startwert 3 Monate |
| 2026-08-30 | Preiswarnung und Budget-Granularität bleiben unverändert | Im Interview kein Bedarf geäußert |
| 2026-11-11 | Bons liegen als Archiv nur auf dem Server; das Handy hält die letzten Monate plus eine Upload-Warteschlange | Handy soll nicht zugemüllt werden; eigenes bon-weises Protokoll statt Voll-Sync (Phase 3) |
| 2026-11-11 | Alle Bon-KI-Aufrufe laufen mit Haiku 5.5 (`AI_RECEIPT_MODEL`), Qualität per Testset geprüft | Kosten gering halten; kein automatischer Rückfall auf ein größeres Modell |
| 2026-11-11 | Lernendes Produkt-Lexikon je Markt (Kürzel → Artikel), lernt automatisch aus Korrekturen im Review | Gleicher Bon-Artikel soll künftig ohne Nachkorrektur stimmen |

---

**Phase 1 umgesetzt** (android-clean-architecture): ViewModels nutzen nur Repositories/UseCases; Details in [phasen/phase-1](../../phasen/phase-1-android-clean-architecture.md).

## Phase 2 (mobile-android-design)

- Phase 2 umgesetzt (2026-11-11): Theme, Insets, Strings, Rollen und Zustandserhalt für diesen Bereich angeglichen; siehe [phase-2](../../phasen/phase-2-mobile-android-design.md).
- Leer-/Fehlerzustände und Layout der Bon-Screens folgen in Phase 3.

## Phase 3a (Bons, Server-Archiv, Scan, Review)

- Server: `/api/receipts…` (Liste, PUT/DELETE, Scan, Foto, Re-Parse, Lexikon), Warteschlange für fehlgeschlagene KI-Auswertung, Bon-Modell Haiku 5.5, Testset-Skript, Wiederherstellungs-Skript.
- Room v37: Uhrzeit, Filiale, Rabatte, Pfand, Einheit, Kategorie, `pendingUpload`, `cachedUntil`; `receipt_lexicon`.
- Scan: Foto verkleinert zum Server, KI liest aus; ohne Server vorläufige ML-Kit-Werte und Upload-Warteschlange (WorkManager).
- Review-Ansicht mit Kopfkarte, Rabatt je Position, Pfand, Kategorie und OFF-Produktzuordnung; Bon-Liste mit Monatsgruppen, Status-Chips und „Ältere Bons laden"; Detail mit Neu-auslesen.
- Einstellungen: „Bons offline behalten" (Server löscht nie), Lexikon-Ansicht.
- Details und Prüflisten: [phase-3a](../../phasen/phase-3a-bons-server-scan.md). Auswertung, Diagramme und Gang-Kategorien folgen in [phase-3b](../../phasen/phase-3b-auswertung-gang-kategorien.md).
