# Feature: Bons & Kosten

> **Status:** Interview offen · **Aufgaben:** 0/0 · **Stand:** 2026-08-22 · **Priorität:** ⭐⭐

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
Offen bis zum Interview.

**Aus dem Einkaufslisten-Interview:** Die Kostenschätzung in der Einkaufsliste wird nicht
genutzt, weil das Fotografieren langer Bons zu mühsam ist — das Hindernis liegt im
Scan-Ablauf hier, nicht in der Kostenschätzung selbst. Guter Startpunkt für Frage 1 unten.

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

## Offene Fragen

1. Wie zuverlässig erkennt der Scan die Positionen inzwischen — wie oft musst du korrigieren?
2. Scannst du jeden Bon oder nur ausgewählte? Danach richtet sich, wie viel Aufwand die
   Listenansicht verdient.
3. Der Abgleich mit der Einkaufsliste ist abschaltbar. Nutzt du ihn, und was tust du mit dem
   Ergebnis?
4. Preisverlauf: Willst du bei einer Preissteigerung aktiv gewarnt werden, oder reicht die
   Ansicht auf Abruf?
5. Das Budget ist monatlich. Brauchst du feinere Zeiträume oder ein Budget je Markt?
6. Fehlt eine Auswertung, die du regelmäßig manuell zusammenrechnest?
7. Sollen Bons nach einer Zeit automatisch entfernt werden, oder ist der Verlauf dauerhaft
   wertvoll?
8. Der Scan braucht den Server. Soll es einen brauchbaren Offline-Weg geben, oder ist das
   akzeptabel?

## Ziele

_Nach dem Interview zu füllen._

## Backlog

Aufwand: S (< 1 h) · M (halber Tag) · L (mehrere Tage)

- [ ] **A1** — Unit-Tests für `ReceiptItemNormalizer` · M · Impact hoch
- [ ] **A2** — `key`-Parameter in `ProductPriceDetailScreen.kt:117` ergänzen · S · Impact mittel

_Weitere Aufgaben nach dem Interview._

## Entscheidungen

| Datum | Entscheidung | Begründung |
|-------|--------------|------------|
