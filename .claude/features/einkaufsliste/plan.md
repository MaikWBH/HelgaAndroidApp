# Feature: Einkaufsliste

> **Status:** Interview offen · **Aufgaben:** 0/0 · **Stand:** 2026-08-22 · **Priorität:** ⭐⭐⭐

Erster Bottom-Nav-Tab und Endpunkt des Kernablaufs Rezept → Wochenplan → Einkauf.

## Umfang

| Ebene | Dateien |
|-------|---------|
| UI | `app/src/main/kotlin/com/helga/android/ui/shopping/ShoppingListScreen.kt` (20 Composables), `ShoppingListViewModel.kt` (499 Zeilen), `ShoppingListWearScreen.kt` |
| Room | `app/src/main/kotlin/com/helga/android/data/local/entity/ShoppingListEntity.kt`, `ShoppingItemEntity.kt`, `ShoppingListStapleEntity.kt`, `QuickEmojiEntity.kt` |
| DAO | `app/src/main/kotlin/com/helga/android/data/local/dao/ShoppingDao.kt`, `QuickEmojiDao.kt` |
| Repository | `app/src/main/kotlin/com/helga/android/data/repository/ShoppingRepository.kt` |
| Hilfsklassen | `app/src/main/kotlin/com/helga/android/data/util/ShoppingUnitConverter.kt`, `IngredientLineParser.kt`, `app/src/main/kotlin/com/helga/android/data/model/ShoppingModels.kt`, `ItemOrigin.kt` |
| Server | `/api/suggestions/items`, `/api/suggestions/aisles`, `/api/suggestions/units` in `server/app/main.py` |

## Ist-Analyse

Der Bereich ist der mit Abstand am weitesten ausgebaute. Belegt durch die öffentliche
ViewModel-API:

- **Listen:** mehrere benannte Listen (`createList`, `selectList`), Standardliste über die
  Einstellungen.
- **Items erfassen** auf fünf Wegen: Freitext mit Mengen-/Einheitenparsing (`addItem` über
  `QuickAddBar`), Emoji-Schnellbuttons (`addEmojiItem`), Vorratsstapel einzeln und gesammelt
  (`addStaple`, `addStaplesToList`), Barcode-Scan (`addItemFromBarcode`) und eigener
  Produktkatalog (`addCatalogProductToList`, `MyProductsSheet`).
- **Gänge:** Gruppierung über `AisleHeader`, nachträgliche Zuordnung per `assignAisle` und
  `AislePickerDialog`, Sortierung nach der Gangreihenfolge des gewählten Markts (`selectStore`).
- **Abhaken und Aufräumen:** `toggleChecked`, `deleteItem`, `deleteCheckedItems`;
  `SwipeableShoppingItem` bietet Wischgesten (eigene Implementierung, nicht Material3
  `SwipeToDismissBox`).
- **Bearbeiten:** `EditItemDialog` ändert Menge, Einheit und Name inline (`updateItem`).
- **Kostenschätzung:** `CostEstimateCard` schätzt die Listensumme aus dem Preisverlauf der
  Kassenbons; Budget-Warnschwelle einstellbar.
- **Herkunft:** `SourceBadge` und `OriginBreakdown` zeigen, woher ein Item stammt (Rezept,
  Wochenplan, manuell) — Datenbasis `ItemOrigin.kt`.
- **Nährwerte im Kontext:** `NutriScoreBadge` und `ScannedProductDialog` binden
  OpenFoodFacts-Daten direkt in die Liste ein.
- **Einheitenbewusstes Zusammenführen:** `ShoppingUnitConverter` fasst g/kg und ml/l/cl beim
  Import zusammen, statt Duplikate anzulegen.
- **Bon-Erinnerung:** `dismissScanReminder` und `receiptScannedTodayForList` erinnern nach dem
  Einkauf an den Bon-Scan.
- **Wear OS:** `ShoppingListWearScreen.kt` als eigene, reduzierte Ansicht.

## Bekannte Lücken

### Funktion & UX
Ohne Interview nicht seriös bestimmbar — der Bereich ist funktional dicht. Die Frageliste unten
zielt darauf, wo es im Alltag trotzdem hakt.

### Code-Qualität
- `ShoppingListScreen.kt:486-487` — zwei `!!`-Zugriffe auf `costEstimate`, verboten laut
  [kotlin-quality](../../guidelines/kotlin-quality.md). Ein `let`-Block löst beide auf.
- `items()` ohne `key`: `ShoppingListScreen.kt:975` (aisles), `:1042` und `:1179`
  (suggestions), `ShoppingListWearScreen.kt:69` (items) — verstößt gegen
  [compose-performance](../../guidelines/compose-performance.md).
- `ShoppingListScreen.kt` ist mit 20 Composables in einer Datei der größte UI-Brocken des
  Projekts. Aufteilung prüfen.

### Tests
Keine. Weder Unit- noch UI-Tests berühren diesen Bereich. Besonders auffällig bei
`ShoppingUnitConverter` und `IngredientLineParser` — reine Logik ohne Android-Abhängigkeit, also
unmittelbar testbar.

### Sync
Alle vier Entities sind vollständig angebunden (`shoppingLists`, `shoppingItems`,
`shoppingListStaples`, `quickEmojis` in `SyncDto`, `SyncEngine` und `SyncDao`). Keine Lücke.

## Offene Fragen

1. Welcher Schritt beim Einkaufen selbst nervt am meisten — Suchen eines Items in der Liste,
   Abhaken, oder etwas anderes?
2. Die Wischgeste ist selbst gebaut statt Material3 `SwipeToDismissBox`. Funktioniert sie im
   Alltag zuverlässig, oder gibt es Fehlauslösungen?
3. Fünf Wege, ein Item anzulegen (Freitext, Emoji, Stapel, Barcode, Katalog) — welche nutzt du
   tatsächlich, welche kann weg?
4. Die Kostenschätzung hängt am Bon-Preisverlauf. Wie brauchbar sind die Schätzungen bisher?
5. Fehlt eine Sortierung oder Gruppierung, die du vermisst (z. B. nach Menge, Häufigkeit,
   zuletzt gekauft)?
6. Sollen erledigte Items automatisch nach einer Zeit verschwinden, oder bewusst manuell?
7. Wear OS: nutzt du den Screen, und soll er ausgebaut werden oder eher entfallen?
8. Soll die Liste offline-fähig bleiben wie bisher, oder gibt es Stellen, wo ein Serverzugriff
   im Laden akzeptabel wäre (z. B. Preisabfrage)?

## Ziele

_Nach dem Interview zu füllen._

## Backlog

Aufwand: S (< 1 h) · M (halber Tag) · L (mehrere Tage)

- [ ] **A1** — `!!` in `ShoppingListScreen.kt:486-487` durch `let`-Block ersetzen · S · Impact mittel
- [ ] **A2** — `key`-Parameter in den vier `items()`-Aufrufen ergänzen · S · Impact mittel
- [ ] **A3** — Unit-Tests für `ShoppingUnitConverter` und `IngredientLineParser` · M · Impact hoch

_Weitere Aufgaben nach dem Interview._

## Entscheidungen

| Datum | Entscheidung | Begründung |
|-------|--------------|------------|
