# Feature: Bons & Kosten

> **Status:** Interview erledigt · **Aufgaben:** 4 offen · **Stand:** 2026-08-30 · **Priorität:** ⭐⭐

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
| Server | `/api/ai/parse-receipt`, `/api/receipts/reconcile` in `server/app/main.py` |
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

- [ ] **A1** — Unit-Tests für `ReceiptItemNormalizer` · M · Impact hoch
- [ ] **A2** — `key`-Parameter in `ProductPriceDetailScreen.kt:117` ergänzen · S · Impact mittel
- [ ] **A3** — Vierten Bottom-Nav-Tab „Bons" ergänzen, der auf `ROUTE_RECEIPTS` zeigt; Menüpunkt
  „Kassenzettel" und das Erinnerungs-Banner in der Einkaufsliste können bleiben (zusätzlicher
  Weg schadet nicht) · M · Impact hoch — behebt die Kernursache aus dem Interview
- [ ] **A4** — Einstellbare automatische Löschung alter Bons (Einstellungen: Zeitraum, Default 3
  Monate; WorkManager- oder Sync-getriggerte Bereinigung von `ReceiptEntity`/`ReceiptItemEntity`
  älter als Grenze) · M · Impact mittel

_Weitere Aufgaben nach dem Interview._

## Entscheidungen

| Datum | Entscheidung | Begründung |
|-------|--------------|------------|
| 2026-08-30 | Vierter Bottom-Nav-Tab statt App-Shortcut oder TopBar-Icon | Nutzer bevorzugt den klassischen, immer sichtbaren Weg gegenüber weniger entdeckbaren Alternativen |
| 2026-08-30 | Bestehende Einstiege (Menüpunkt, Erinnerungs-Banner) bleiben zusätzlich bestehen | Kein Grund zum Entfernen, der neue Tab ergänzt statt ersetzt |
| 2026-08-30 | Automatische Bon-Löschung wird einstellbar, nicht fest | Nutzer will Kontrolle über die Aufbewahrungsdauer, Startwert 3 Monate |
| 2026-08-30 | Preiswarnung, Budget-Granularität, Offline-Scan bleiben unverändert | Im Interview kein Bedarf geäußert |
