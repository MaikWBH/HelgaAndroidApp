# Feature: Sync

> **Status:** Interview erledigt · **Aufgaben:** 2 offen (2 erledigt) · **Stand:** 2026-08-31 · **Priorität:** ⭐⭐

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
- **Auslöser:** periodisch über `SyncWorker` (WorkManager, 15-Minuten-Intervall,
  `SyncScheduler.kt:29`), bei Netzwechsel über `NetworkObserver` und manuell aus den
  Einstellungen (`syncNow`). **Korrektur zur bisherigen Annahme:** Kein App-Start/Foreground-
  Trigger vorhanden — `CLAUDE.md` führt „App-Foreground" als Trigger auf, aber
  `syncScheduler.triggerOneShot()` wird im gesamten Code nur aus `NetworkObserver.kt:35` und aus
  einzelnen ViewModel-Schreibaktionen aufgerufen, nie beim App-Start oder Vordergrundwechsel.
- **Bilder faktisch teilweise bidirektional:** `ImageUploadWorker` lädt nur hoch, aber
  `RecipeDetailScreen.kt:420-422` lässt Coil bei fehlendem lokalem Cache vom Server laden
  (`ImageUrls.serverImageUrl`) — Bilder kommen so auf einem zweiten Gerät an, sobald der
  Datensatz gesynct ist und das Bild einmal online betrachtet wird. Kein proaktiver
  Download/Offline-Cache, echtes Offline-First für Bilder auf einem neuen Gerät fehlt.
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
**Stand bei Interview (2026-08-30):** Zwei Entities waren nicht angebunden — beide in ihren
Fachbereichen als Aufgabe geführt:
- `WeekplanTemplateEntity`, `WeekplanTemplateEntryEntity` → [wochenplan](../wochenplan/plan.md), A1
- `OffProductEntity` → [naehrwerte](../naehrwerte/plan.md), A2 (DAO und Server sind fertig, nur der Aufruf fehlt)

Damit waren 22 von 25 Entities angebunden. Ein wiederkehrendes Muster: eine neue Entity wird
angelegt und der Sync-Anschluss vergessen. Eine Prüfung, die das automatisch bemerkt, wäre
wirksamer als jede Einzelkorrektur — siehe Backlog A1.

**Update 2026-08-31:** Beide Lücken geschlossen — Vorlagen-Feature per wochenplan A1 komplett
entfernt statt angebunden, `OffProductEntity` per naehrwerte A2 angebunden. Damit sind aktuell
alle 23 verbliebenen Entities angebunden (0 offene Lücken), und `SyncCompletenessTest.kt`
(Backlog A1 hier) bewacht das automatisch für künftige neue Entities.

## Fragen

**Aus dem Einkaufslisten-Interview:** Sync-Geschwindigkeit und Datenvolumen sind dem Nutzer
explizit wichtig — die Einkaufsliste muss offline funktionieren, der Sync danach schnell und
datensparsam sein. Als Priorität für dieses Interview vermerkt.

1. **Fällt dir im Alltag auf, wenn der Sync scheitert, oder merkst du es erst an fehlenden
   Daten?**
   Antwort: Ist mir noch nie negativ aufgefallen — bezogen auf sichtbare Fehleranzeigen.
2. **Nutzt du mehr als ein Gerät?**
   Antwort: Ja, mehrere Geräte aktiv.
3. **Wie oft ist der Server erreichbar?**
   Antwort: Dauerhaft erreichbar.
4. **Gab es schon Datenverlust oder überschriebene Änderungen durch Last-Write-Wins?**
   Antwort: Kein bewusster LWW-Konflikt, aber ein konkreter Vorfall: eine neu angelegte
   Urlaubs-Einkaufsliste erschien auf dem zweiten Gerät (Ehefrau) erst sehr spät. Root Cause
   siehe oben — der App-Foreground-Trigger aus `CLAUDE.md` existiert im Code nicht; bei
   dauerhaft aktivem Netz feuert `NetworkObserver` nicht (kein Connectivity-Wechsel), also blieb
   nur der 15-Minuten-Worker, der von Android zusätzlich verzögert werden kann.
5. **Soll die App beim Öffnen/Vordergrund automatisch synchronisieren?**
   Antwort: Ja, unbedingt — behebt den Vorfall direkt.
6. **Automatische Prüfung gegen vergessene Sync-Anbindungen sinnvoll?**
   Antwort: Ja.
6b. **Sollen Sync-Konflikte sichtbar gemacht werden, statt still per LWW entschieden zu
    werden?**
    Antwort: Nein, still per LWW reicht — kein bekannter Vorfall mit verlorenen Änderungen.
7. **Bilder auch in die Gegenrichtung synchronisieren?**
   Antwort: Ja, wichtig, dass Bilder auf allen Geräten ankommen. Faktisch bereits teilweise
   gelöst (Coil-Fallback aufs Server-Bild, siehe Ist-Analyse) — es fehlt der proaktive
   Offline-Download für ein neues/zweites Gerät.

## Ziele

- App-Foreground-Sync-Trigger ergänzen — schließt die Lücke zwischen der `CLAUDE.md`-Doku und
  dem tatsächlichen Code und behebt den geschilderten Vorfall direkt.
- Automatisierte Prüfung gegen vergessene Sync-Anbindungen einführen, damit sich das Muster
  „neue Entity, Sync-Anschluss vergessen" nicht wiederholt.
- Bilder proaktiv im Hintergrund herunterladen, damit sie auch offline auf einem neuen Gerät
  verfügbar sind, statt nur bei Bedarf live vom Server geladen zu werden.
- Bestehende Sync-Mechanik (LWW, Connectivity-Trigger, periodischer Worker, Datensparsamkeit)
  unverändert lassen — kein bewusster Datenverlust bekannt, Server ist dauerhaft erreichbar.

## Backlog

Aufwand: S (< 1 h) · M (halber Tag) · L (mehrere Tage)

- [x] **A1** — Test, der alle Entities aus `AppDatabase` gegen `SyncDto`/`SyncEngine` abgleicht und bei fehlendem Anschluss fehlschlägt · M · Impact hoch — bestätigter Bedarf aus dem Interview —
  **umgesetzt:** `SyncCompletenessTest.kt` (reines JVM-Test analog zu `SyncLwwTest`, keine
  Android-Runtime nötig). Liest `AppDatabase.kt`/`SyncEngine.kt` als Quelltext, extrahiert alle
  `<Name>Entity::class`-Einträge aus dem `entities = [...]`-Block per Regex und prüft für jede,
  ob `SyncEngine.kt` sowohl `fun <Name>Entity.toDto(): <Name>Dto` als auch
  `fun <Name>Dto.toEntity(): <Name>Entity` enthält — das durchgängige Namensmuster aller 23
  bestehenden Mapper. Ein mitgeführtes, aktuell leeres `intentionallyLocalOnly`-Set erlaubt
  künftige bewusste Ausnahmen mit Begründung. Verifiziert: Test schlägt fehl, wenn eine Entity
  ohne Mapper-Paar eingespeist wird (Positiv- und Negativfall geprüft, letzterer über einen
  temporären Test-Patch statt Produktivcode).
- [ ] **A2** — Tests für Konfliktfälle: gleicher Zeitstempel, Teilfehler beim Push, Abbruch mitten im Sync · M · Impact hoch
- [x] **A3** — App-Foreground-Sync-Trigger implementieren (z. B. `ProcessLifecycleOwner` in der
  Application-Klasse, `syncScheduler.triggerOneShot()` beim Vordergrundwechsel): kein
  bestehender Code-Pfad ruft das aus, obwohl `CLAUDE.md` es als Trigger dokumentiert · S ·
  Impact hoch — root-caused Vorfall aus dem Interview — **umgesetzt:** neue
  `ForegroundSyncObserver` (Muster von `NetworkObserver`), registriert sich auf
  `ProcessLifecycleOwner`, `onStart()` triggert `syncScheduler.triggerOneShot()`; in `HelgaApp`
  neben `networkObserver.start()` gestartet
- [ ] **A4** — Bilder proaktiv im Hintergrund herunterladen (Download-Pendant zu
  `ImageUploadWorker`), damit sie auch offline auf einem neuen/zweiten Gerät verfügbar sind ·
  M · Impact mittel

_Weitere Aufgaben nach dem Interview._

## Entscheidungen

| Datum | Entscheidung | Begründung |
|-------|--------------|------------|
| 2026-08-30 | App-Foreground-Sync-Trigger wird ergänzt | Behebt einen konkret erlebten Vorfall (späte Einkaufsliste), schließt Doku/Code-Lücke |
| 2026-08-30 | Automatisierte Sync-Vollständigkeitsprüfung wird eingeführt | Verhindert Wiederholung des „vergessene Entity"-Musters |
| 2026-08-30 | Bilder bekommen einen proaktiven Download-Mechanismus | Nutzer legt Wert auf Bilder auf allen Geräten, auch offline |
| 2026-08-30 | Konflikt-Sichtbarkeit (Frage 5 aus der urspr. Liste) bleibt wie bisher still per LWW | Kein bewusster Datenverlust bekannt, kein Bedarf an sichtbarer Konfliktauflösung geäußert |
