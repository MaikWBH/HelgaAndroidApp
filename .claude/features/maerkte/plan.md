# Feature: Märkte & Gänge

> **Status:** Interview offen · **Aufgaben:** 0/0 · **Stand:** 2026-08-22 · **Priorität:** ⭐

Bestimmt die Reihenfolge, in der die Einkaufsliste sortiert wird. Kleiner Bereich mit direkter
Wirkung auf den Einkauf.

## Umfang

| Ebene | Dateien |
|-------|---------|
| UI | `app/src/main/kotlin/com/helga/android/ui/stores/StoreListScreen.kt`, `StoreListViewModel.kt` |
| Room | `app/src/main/kotlin/com/helga/android/data/local/entity/StoreEntity.kt`, `StoreAisleEntity.kt`, `AisleProductEntity.kt` |
| DAO | `app/src/main/kotlin/com/helga/android/data/local/dao/StoreDao.kt` |
| Repository | `app/src/main/kotlin/com/helga/android/data/repository/StoreRepository.kt` |
| Server | `/api/suggestions/aisles` in `server/app/main.py` |

## Ist-Analyse

- **Märkte:** anlegen, löschen, aktiven Markt setzen (`createStore`, `deleteStore`,
  `setActiveStore`, `selectStore`).
- **Gänge:** je Markt eigene Gangliste (`addAisle`, `deleteAisle`), Reihenfolge über
  Hoch/Runter-Buttons (`moveAisleUp`, `moveAisleDown`) — kein Drag-and-Drop.
- **Produktzuordnung:** `AisleProductEntity` merkt sich, welches Produkt zu welchem Gang gehört.
  Zuordnungen entstehen automatisch beim Einkaufen und sind über den `AislePickerDialog` in der
  Einkaufsliste nachträglich korrigierbar.
- **Wirkung:** Die Einkaufsliste gruppiert und sortiert nach der Gangreihenfolge des gewählten
  Markts.

## Bekannte Lücken

### Funktion & UX
- Gangreihenfolge nur über Pfeil-Buttons. Bei vielen Gängen mühsam; Drag-and-Drop wäre der
  erwartete Weg.

### Code-Qualität
Keine `!!`-Zugriffe, keine `items()`-Verstöße in diesem Bereich.

### Tests
Keine.

### Sync
`stores`, `storeAisles` und `aisleProducts` sind vollständig angebunden. Keine Lücke.

## Offene Fragen

1. Wie viele Märkte und Gänge nutzt du tatsächlich? Danach richtet sich, ob Drag-and-Drop lohnt.
2. Stimmt die gelernte Produkt-zu-Gang-Zuordnung meist, oder korrigierst du oft nach?
3. Soll ein Markt einen Standard-Gangsatz mitbringen, statt jeden Gang einzeln anzulegen?
4. Wechselst du den Markt in der Einkaufsliste, oder ist faktisch immer derselbe aktiv?
5. Sollen Gänge zwischen Märkten kopierbar sein?
6. Fehlt etwas am Markt selbst — Öffnungszeiten, Adresse, Notiz?

## Ziele

_Nach dem Interview zu füllen._

## Backlog

Aufwand: S (< 1 h) · M (halber Tag) · L (mehrere Tage)

- [ ] **A1** — Drag-and-Drop für die Gangreihenfolge prüfen, abhängig von Frage 1 · M · Impact mittel

_Weitere Aufgaben nach dem Interview._

## Entscheidungen

| Datum | Entscheidung | Begründung |
|-------|--------------|------------|
