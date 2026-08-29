# Feature: Einkaufsliste

> **Status:** Interview erledigt · **Aufgaben:** 7 offen · **Stand:** 2026-08-22 · **Priorität:** ⭐⭐⭐

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

### Bugs
**Rotation wechselt die Liste bzw. wirft aus der Kochansicht.** Gemeldet im Interview (Frage 2).
Ursache belegt in `app/src/main/kotlin/com/helga/android/HelgaNavGraph.kt:96-104`: `MainActivity`
deklariert kein `android:configChanges`, eine Drehung zerstört und erstellt die Activity komplett
neu, `HelgaNavGraph()` wird als frische Composable-Instanz aufgebaut. Der darin enthaltene
`LaunchedEffect(Unit)` feuert bei **jeder** solchen Neuzusammensetzung erneut — nicht nur beim
echten Erststart — und navigiert unconditional zu `ROUTE_SHOPPING`, sobald
`preferences.connection.first().isConfigured` `true` ist (nach dem Onboarding immer der Fall).
Das erklärt den Sprung aus der Kochansicht (`RecipeCookScreen`) exakt. Die Zwangsnavigation legt
dabei einen neuen `ROUTE_SHOPPING`-Backstack-Eintrag an (kein `launchSingleTop`), also eine
frische `ShoppingListViewModel`-Instanz. Deren `_activeListId`
(`ShoppingListViewModel.kt:59,71-74`) ist ein reiner In-Memory-`MutableStateFlow<String?>(null)`
ohne `SavedStateHandle`; nach dem Reset fällt `activeListId` auf
`preferences.defaultShoppingListId` zurück statt auf die zuvor gezeigte Liste — daher der
sichtbare Listenwechsel. Fix-Richtung: den `LaunchedEffect(Unit)` auf die Onboarding-Route
eingrenzen (`if (currentRoute == ROUTE_ONBOARDING && conn.isConfigured)`), siehe Backlog A4.
Betrifft auch [rezepte](../rezepte/plan.md) (Kochansicht) und liegt strukturell in
[plattform](../plattform/plan.md), da `HelgaNavGraph.kt` dort im Umfang steht.

**Freitext-Vorschläge ohne Offline-Fallback.** `suggestItems()`
(`ShoppingListViewModel.kt:377-385`) ruft ausschließlich den Server auf; jede Exception —
auch „nicht erreichbar" — wird zu einer stillen leeren Liste. Der Debounce in
`ShoppingListScreen.kt:1150` (`delay(300)`) ist korrekt vorhanden; „langsam/unzuverlässig"
(Frage 3) kommt vom Netzwerk-Roundtrip und dem fehlenden lokalen Fallback, nicht vom Debouncing.
Server-seitig ist die Query (`server/app/main.py:166-181`) eine einfache SQL-`LIKE`-Suche über
Daten, die Room ohnehin lokal hält — widerspricht dem in `CLAUDE.md` festgehaltenen Prinzip
„ViewModels lesen immer aus Room, nie direkt aus Retrofit-Responses". Siehe Backlog A5.

### Funktion & UX
Größter im Interview genannter Reibungspunkt: falsche Gang-Zuordnung (Frage 1) — Items landen
im falschen Gang oder bleiben unzugeordnet, die gelernte Zuordnung trifft nicht zuverlässig
genug. Ursache noch nicht analysiert, siehe Backlog A6.

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

   **Antwort:** Falsche Gang-Zuordnung — Items landen im falschen Gang oder bleiben
   unzugeordnet, das Nachkorrigieren nervt.

2. Die Wischgeste ist selbst gebaut statt Material3 `SwipeToDismissBox`. Funktioniert sie im
   Alltag zuverlässig, oder gibt es Fehlauslösungen?

   **Antwort:** Die Wischgeste selbst funktioniert. Gemeldet wurde stattdessen ein Bug: Beim
   Wechsel vom Hochkant- in den Querformat-Modus springt die App in eine andere Einkaufsliste.
   Dasselbe passiert beim Kochen mit offener Kochansicht — die Drehung wirft aus der Kochansicht
   zurück in die Einkaufsliste. Ursache und Fix siehe „Bekannte Lücken → Bugs" und Backlog A4.

3. Fünf Wege, ein Item anzulegen (Freitext, Emoji, Stapel, Barcode, Katalog) — welche nutzt du
   tatsächlich, welche kann weg?

   **Antwort:** Genutzt werden Emoji-Schnellbuttons und Freitext. Barcode-Scan wurde nie
   genutzt. Zu Freitext der Wunsch: die Vorschläge sollen zuverlässiger und schneller kommen.
   Ursache und Fix siehe „Bekannte Lücken → Bugs" und Backlog A5.

4. Die Kostenschätzung hängt am Bon-Preisverlauf. Wie brauchbar sind die Schätzungen bisher?

   **Antwort:** Noch nie genutzt — das Fotografieren langer Bons ist zu mühselig. Das Problem
   liegt im Scan-Ablauf, nicht in der Kostenschätzung selbst; als Ausgangspunkt in
   [bons-kosten](../bons-kosten/plan.md) vermerkt.

5. Fehlt eine Sortierung oder Gruppierung, die du vermisst (z. B. nach Menge, Häufigkeit,
   zuletzt gekauft)?

   **Antwort:** Gang-Gruppierung reicht so, wie sie ist.

6. Sollen erledigte Items automatisch nach einer Zeit verschwinden, oder bewusst manuell?

   **Antwort:** Manuell wie bisher.

7. Wear OS: nutzt du den Screen, und soll er ausgebaut werden oder eher entfallen?

   **Antwort:** Bisher nicht genutzt, weil unklar ist, wie die App überhaupt auf die Uhr kommt.
   Ausdrücklicher Wunsch: gerade das Abhaken der Einkaufsliste auf der Uhr hat viel Potenzial —
   ausbauen und die Installation auf die Uhr deutlich einfacher machen. Die Installationsfrage
   ist technisch eine eigene Wear-App-Modulstruktur statt der aktuellen
   Laufzeit-Unterscheidung in `MainActivity.kt` (`isRunningOnWearOs()`) — Aufgabe in
   [plattform](../plattform/plan.md), Ausbau der Abhak-Funktion hier in Backlog A7.

8. Soll die Liste offline-fähig bleiben wie bisher, oder gibt es Stellen, wo ein Serverzugriff
   im Laden akzeptabel wäre (z. B. Preisabfrage)?

   **Antwort:** Die Einkaufsliste MUSS offline funktionieren — das ist nicht verhandelbar.
   Zusätzlich soll die Synchronisation zum Server schnell sein und wenig Datenvolumen
   verbrauchen. Als Kontext in [sync](../sync/plan.md) vermerkt.

## Ziele

- Rotation ändert nie mehr Screen oder Liste — Kochansicht und Einkaufsliste bleiben stabil.
- Gang-Zuordnung ist spürbar zuverlässiger, „falscher Gang" ist nicht mehr der Hauptärger.
- Freitext-Vorschläge funktionieren spürbar auch ohne (schnelle) Serverantwort.
- Abhaken auf der Wear-Uhr ist ein vollwertiger, leicht einzurichtender Zweitweg zum Einkaufen.

## Backlog

Aufwand: S (< 1 h) · M (halber Tag) · L (mehrere Tage)

- [ ] **A1** — `!!` in `ShoppingListScreen.kt:486-487` durch `let`-Block ersetzen · S · Impact mittel
- [ ] **A2** — `key`-Parameter in den vier `items()`-Aufrufen ergänzen · S · Impact mittel
- [ ] **A3** — Unit-Tests für `ShoppingUnitConverter` und `IngredientLineParser` · M · Impact hoch
- [ ] **A4** — `LaunchedEffect(Unit)` in `HelgaNavGraph.kt:96-104` auf die Onboarding-Route
      eingrenzen, behebt Rotationsbug (Listenwechsel + Rauswurf aus Kochansicht) · S · Impact hoch
- [ ] **A5** — `suggestItems()` um lokalen Room-Fallback ergänzen (Rezeptzutaten +
      `ShoppingDao`-Namen), Server nur als Ergänzung · M · Impact hoch
- [ ] **A6** — Genauigkeit der gelernten Gang-Zuordnung untersuchen (`assignAisle`,
      `AisleProductEntity`-Lernlogik) — größter genannter Reibungspunkt, Ursache noch offen · M ·
      Impact hoch
- [ ] **A7** — Abhaken auf dem Wear-Screen ausbauen (`ShoppingListWearScreen.kt`) · M · Impact
      mittel — setzt das Wear-Modul aus [plattform](../plattform/plan.md) voraus

## Entscheidungen

| Datum | Entscheidung | Begründung |
|-------|--------------|------------|
| 2026-08-22 | Gang-Gruppierung bleibt unverändert, keine neue Sortierung | Interview: reicht, sobald die Zuordnung selbst stimmt (siehe A6) |
| 2026-08-22 | Erledigte Items bleiben manuell löschbar, kein Autoclean | Interview: volle Kontrolle gewünscht |
| 2026-08-22 | Barcode-Scan vorerst kein Ausbaufokus | Interview: wird nicht genutzt |
| 2026-08-22 | Offline-Pflicht für die Einkaufsliste bestätigt; Sync-Effizienz (Geschwindigkeit, Datenvolumen) als Priorität festgehalten | Interview: nicht verhandelbare Anforderung |
