# Feature: Sync

> **Status:** Interview offen · **Aufgaben:** 0/0 · **Stand:** 2026-08-22 · **Priorität:** ⭐⭐

Bidirektionaler Abgleich mit dem Homeserver, Last-Write-Wins. Kein eigener Screen, aber
Grundlage aller anderen Bereiche — und der einzige Bereich mit vorhandenen Tests.

## Umfang

| Ebene | Dateien |
|-------|---------|
| Engine | `app/src/main/kotlin/com/helga/android/data/sync/SyncEngine.kt`, `SyncWorker.kt`, `SyncScheduler.kt`, `NetworkObserver.kt`, `SyncStatus.kt`, `SyncStatusHolder.kt`, `ImageUploadWorker.kt`, `NotificationScheduler.kt` |
| Netzwerk | `app/src/main/kotlin/com/helga/android/data/remote/SyncApi.kt`, `SyncApiFactory.kt`, `app/src/main/kotlin/com/helga/android/data/remote/dto/SyncDto.kt` |
| DAO | `app/src/main/kotlin/com/helga/android/data/local/dao/SyncDao.kt` |
| UI | `app/src/main/kotlin/com/helga/android/ui/components/SyncStatusIcon.kt`; Statusanzeige und manueller Sync in `app/src/main/kotlin/com/helga/android/ui/settings/SettingsScreen.kt` |
| Server | `server/app/sync.py`, `server/app/db.py`; Endpunkt `/api/sync` in `server/app/main.py` |
| Tests | `app/src/test/kotlin/` — `SyncLwwTest.kt`, `SyncStatusTest.kt` |

## Ist-Analyse

- **Ablauf:** `GET /api/sync?since=<ts>` holt Serveränderungen, LWW-Merge in Room,
  `POST /api/sync` schiebt alle `dirty`-Records; danach werden die Dirty-Flags gezielt gelöscht
  (`clearDirtyFlagsExcept` in `SyncEngine.kt:99,202`) — bewusst nur für die Datensätze, die der
  Server übernommen hat.
- **Auslöser:** periodisch über `SyncWorker` (WorkManager), bei Netzwechsel über
  `NetworkObserver`, beim App-Start und manuell aus den Einstellungen (`syncNow`).
- **Status:** `SyncStatusHolder` als Flow, `SyncStatusIcon` in der Top-Bar, Fehlertext in den
  Einstellungen.
- **Bilder:** `ImageUploadWorker` lädt lokal gespeicherte Bilder nach erfolgreichem Sync hoch.
- **Benachrichtigungen:** `NotificationScheduler` für Einkaufstag- und Koch-Erinnerung.
- **Abdeckung:** `SyncEngine` bedient 20 Entity-Typen über `SyncDao` (`*Timestamps`-Abfragen),
  dazu `recipeHistory` und `recipeFeedback` über deren eigene DAOs.

## Bekannte Lücken

### Funktion & UX
Offen bis zum Interview.

### Code-Qualität
Keine `!!`-Zugriffe im Sync-Paket. `SyncEngine.kt` ist die zentrale Klasse mit der höchsten
Änderungslast — jede neue Entity berührt sie an drei Stellen (Timestamps, Push, Dirty-Clear).
Ob sich das generisch fassen lässt, ist eine eigene Frage.

### Tests
Die einzigen Tests des Projekts liegen hier: `SyncLwwTest.kt` prüft die LWW-Logik,
`SyncStatusTest.kt` den Status-Flow. Nicht abgedeckt: Konfliktfälle mit gleichem Zeitstempel,
Teilfehler beim Push, Abbruch mitten im Sync.

### Sync
Zwei Entities sind nicht angebunden — beide in ihren Fachbereichen als Aufgabe geführt:
- `WeekplanTemplateEntity`, `WeekplanTemplateEntryEntity` → [wochenplan](../wochenplan/plan.md), A1
- `OffProductEntity` → [naehrwerte](../naehrwerte/plan.md), A2 (DAO und Server sind fertig, nur der Aufruf fehlt)

Damit sind 22 von 25 Entities angebunden. Ein wiederkehrendes Muster: eine neue Entity wird
angelegt und der Sync-Anschluss vergessen. Eine Prüfung, die das automatisch bemerkt, wäre
wirksamer als jede Einzelkorrektur.

## Offene Fragen

1. Fällt dir im Alltag auf, wenn der Sync scheitert — oder merkst du es erst an fehlenden Daten?
2. Nutzt du mehr als ein Gerät? Davon hängt ab, wie schwer die beiden Sync-Lücken wiegen.
3. Wie oft ist der Server erreichbar — dauerhaft im Heimnetz oder nur zeitweise?
4. Gab es schon Datenverlust oder überschriebene Änderungen durch Last-Write-Wins?
5. Soll die App Konflikte sichtbar machen, statt sie still nach Zeitstempel zu entscheiden?
6. Wäre eine automatische Prüfung sinnvoll, die beim Bauen meldet, wenn eine Entity nicht im
   Sync auftaucht?
7. Sollen Bilder auch in die Gegenrichtung synchronisiert werden, oder reicht der Upload?

## Ziele

_Nach dem Interview zu füllen._

## Backlog

Aufwand: S (< 1 h) · M (halber Tag) · L (mehrere Tage)

- [ ] **A1** — Test, der alle Entities aus `AppDatabase` gegen `SyncDto`/`SyncEngine` abgleicht und bei fehlendem Anschluss fehlschlägt · M · Impact hoch
- [ ] **A2** — Tests für Konfliktfälle: gleicher Zeitstempel, Teilfehler beim Push, Abbruch mitten im Sync · M · Impact hoch

_Weitere Aufgaben nach dem Interview._

## Entscheidungen

| Datum | Entscheidung | Begründung |
|-------|--------------|------------|
