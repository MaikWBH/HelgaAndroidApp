# Feature: Märkte & Gänge

> **Status:** Interview erledigt · **Aufgaben:** 4 offen · **Stand:** 2026-08-30 · **Priorität:** ⭐

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
- **Root Cause zu „muss oft korrigieren" (aus dem Interview):** Die Gang-Zuordnung matcht
  Produktnamen exakt. `StoreDao.kt:31` (`findAisleForProduct`) und `:34`
  (`findAisleProductEntry`) vergleichen mit `WHERE productName = :productName` — nur
  klein geschrieben normalisiert (`StoreRepository.kt:92/103/113`), keine Normalisierung von
  Plural/Singular, Klammerzusätzen oder Zubereitungshinweisen. „Zwiebel" (gelernt) und
  „Zwiebeln" (nächstes Rezept) oder „Tomaten (klein)" treffen sich nie, obwohl dasselbe Produkt
  gemeint ist — jede Abweichung erzwingt eine erneute manuelle Zuordnung.

### Code-Qualität
Keine `!!`-Zugriffe, keine `items()`-Verstöße in diesem Bereich.

### Tests
Keine.

### Sync
`stores`, `storeAisles` und `aisleProducts` sind vollständig angebunden. Keine Lücke.

## Fragen

1. **Wie viele Märkte und Gänge nutzt du tatsächlich?**
   Antwort: Mehrere Märkte, viele Gänge — Drag-and-Drop lohnt sich.
2. **Stimmt die gelernte Gang-Zuordnung meist, oder korrigierst du oft nach?**
   Antwort: Muss oft korrigieren. Siehe Root Cause oben (exakter String-Match ohne
   Normalisierung).
3. **Soll ein Markt einen Standard-Gangsatz mitbringen?**
   Antwort: Ja, wäre praktisch.
4. **Wechselst du den aktiven Markt tatsächlich, oder ist faktisch immer derselbe aktiv?**
   Antwort: Praktisch immer derselbe — kein Ausbau der Wechsel-UI nötig.
5. **Sollen Gänge zwischen Märkten kopierbar sein?**
   Antwort: Ja, wäre praktisch — ergänzt Frage 3 (beides löst „nicht bei null anfangen").
6. **Fehlt etwas am Markt selbst (Öffnungszeiten, Adresse, Notiz)?**
   Antwort: Nicht nötig.

## Ziele

- Gang-Zuordnung zuverlässiger lernen lassen — exaktes String-Matching ist die Hauptursache für
  häufiges Nachkorrigieren, nicht die Lernlogik an sich.
- Neue Märkte nicht bei null anfangen lassen — Standard-Gangsatz oder Kopie eines bestehenden
  Markts als Startpunkt.
- Drag-and-Drop für die Gangreihenfolge umsetzen — bei mehreren Märkten mit vielen Gängen
  bestätigt sich der Bedarf aus der Ist-Analyse.
- Markt-Wechsel-UI und Markt-Metadaten unverändert lassen — kein Bedarf geäußert.

## Backlog

Aufwand: S (< 1 h) · M (halber Tag) · L (mehrere Tage)

- [ ] **A1** — Drag-and-Drop für die Gangreihenfolge · M · Impact mittel — bestätigter Bedarf
  aus dem Interview (mehrere Märkte, viele Gänge)
- [ ] **A2** — Gang-Zuordnung robuster machen: `findAisleForProduct`/`findAisleProductEntry`
  (`StoreDao.kt:31`/`:34`) matchen exakt, keine Normalisierung von Plural/Singular oder
  Klammerzusätzen (`Zwiebel` ↔ `Zwiebeln`, `Tomaten (klein)`) · M · Impact hoch
- [ ] **A3** — Neue Märkte vorbefüllen: Standard-Gangsatz als Vorlage anbieten, alternativ
  Gänge von einem bestehenden Markt kopieren · M · Impact mittel
- [ ] **A4** — Märkte direkter erreichbar machen: aktuell nur über Einstellungen → Märkte
  (`HelgaNavGraph.kt:210`, `onStoresClick` kommt ausschließlich aus `SettingsScreen`), zwei
  Hops tief. Gefunden im [plattform](../plattform/plan.md)-Interview ("zu langsam/versteckt").
  Naheliegend: Eintrag im Overflow-Menü der Einkaufsliste, analog zu „Kassenzettel" ·
  S · Impact mittel

_Weitere Aufgaben nach dem Interview._

## Entscheidungen

| Datum | Entscheidung | Begründung |
|-------|--------------|------------|
| 2026-08-30 | Drag-and-Drop wird umgesetzt | Mehrere Märkte mit vielen Gängen bestätigt den Bedarf |
| 2026-08-30 | Gang-Zuordnung bekommt eine robustere Matching-Logik statt reinem Exakt-Match | Häufiges Nachkorrigieren ist der größte Reibungspunkt im Bereich |
| 2026-08-30 | Neue Märkte lassen sich per Standard-Gangsatz oder Kopie vorbefüllen | Beide Wege wurden gewünscht, lösen dasselbe Problem gemeinsam |
| 2026-08-30 | Markt-Wechsel-UI und Markt-Metadaten bleiben unverändert | Kein Bedarf geäußert |
