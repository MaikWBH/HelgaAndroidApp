# Feature: Rezepte

> **Status:** Interview erledigt · **Aufgaben:** 2 offen (8 erledigt) · **Stand:** 2026-08-31 · **Priorität:** ⭐⭐⭐

Zweiter Bottom-Nav-Tab. Datenbasis für Wochenplan und Einkaufsliste.

## Umfang

| Ebene | Dateien |
|-------|---------|
| UI | `app/src/main/kotlin/com/helga/android/ui/recipes/` — `RecipeListScreen.kt`, `RecipeDetailScreen.kt`, `RecipeFormScreen.kt`, `RecipeCookScreen.kt`, `UrlImportScreen.kt` je mit ViewModel |
| Room | `app/src/main/kotlin/com/helga/android/data/local/entity/RecipeEntity.kt`, `IngredientEntity.kt`, `InstructionEntity.kt`, `TagEntity.kt`, `CategoryEntity.kt`, `RecipeHistoryEntity.kt`, `RecipeFeedbackEntity.kt` |
| DAO | `app/src/main/kotlin/com/helga/android/data/local/dao/RecipeDao.kt`, `RecipeHistoryDao.kt`, `RecipeFeedbackDao.kt` |
| Repository | `app/src/main/kotlin/com/helga/android/data/repository/RecipeRepository.kt` |
| Hilfsklassen | `app/src/main/kotlin/com/helga/android/data/util/IngredientLineParser.kt`, `app/src/main/kotlin/com/helga/android/data/model/RecipeNutrition.kt` |
| Server | `/api/ai/import-url` in `server/app/main.py`, Parsing in `server/app/ingredient_parser.py` |

## Ist-Analyse

- **Liste** (`RecipeListViewModel`): Freitextsuche (`setSearchQuery`), Favoritenfilter
  (`toggleFavoritesFilter`), Tag-Filter (`selectTag`, `toggleTag`, `clearTags`,
  `TagFilterDialog`), Sortierung (`setSortOrder`), KI-Massenklassifikation (`classifyBatch`,
  `BulkClassifyDialog`). Zeigt zusätzlich das für heute geplante Rezept aus dem Wochenplan an.
  Das Suchfeld ist ein sichtbares `OutlinedTextField` über der `FilterBar`
  (`RecipeListScreen.kt:189`) — nicht versteckt. Gefiltert wird allerdings **im Speicher** über
  die bereits geladene Gesamtliste und nur auf `name` und `description`
  (`RecipeListViewModel.kt:69-74`), nicht per Room-Query und ohne Zutaten oder Tags.
- **Detail** (`RecipeDetailViewModel`, 382 Zeilen): Portionsskalierung (`setServings`,
  `parseServings`), Favoriten-Toggle, Bewertung (`setRating`), persönliche Notizen
  (`savePersonalNotes`), Teilen (`shareRecipe`), Löschen, KI-Klassifikation (`classify`),
  Nährwerte per KI oder manuell (`calculateNutritionWithAi`, `saveManualNutrition`),
  Ein-Tipp-Export zur Standardliste (`addToDefaultShoppingList`) und gezielter Export
  (`exportToShoppingList`), Eintragen in einen Wochenplantag inklusive Wochennavigation
  (`addToWeekplanDay`, `nextWeek`, `prevWeek`, `goToCurrentWeek`).
- **Formular** (`RecipeFormScreen`): Anlegen und Bearbeiten, dynamische Zutaten- und
  Schrittlisten, Bildauswahl, clientseitige UUID.
- **Kochansicht** (`RecipeCookViewModel`): Zutaten und Schritte abhakbar (`toggleIngredient`,
  `toggleStep`), Portionsskalierung, Kochbestätigung schreibt in die Historie (`confirmCooked`),
  Fokusansicht mit Wischnavigation. Der Bildschirm bleibt beim Kochen an
  (`RecipeCookScreen.kt:178-179`, `view.keepScreenOn = true` mit Rücksetzung in `onDispose`).
  Timer werden per `TIMER_REGEX` aus dem Schritttext erkannt (`RecipeCookScreen.kt:77-79`,
  erfasst Stunden/Std/Minuten/Min/min/Sekunden/Sek). **Es läuft genau ein Timer gleichzeitig**
  (`activeTimer` ist ein einzelner nullable State, Zeile 150) und er ist an die Kochansicht
  gebunden: die Zähllogik steckt in einem `LaunchedEffect` (Zeile 155-160), das beim Verlassen
  des Screens abgebrochen wird. Keine Benachrichtigung, kein Weiterlaufen im Hintergrund.
- **Portionsskalierung** ist reiner UI-Zustand in `RecipeDetailViewModel`/`RecipeCookViewModel`
  und wird nicht persistiert — beim erneuten Öffnen stehen wieder die Originalportionen.
- **URL-Import** (`UrlImportScreen`): Import über den Server, dazu lokaler JSON-LD-Parser
  (`app/src/main/kotlin/com/helga/android/ui/ai/RecipeJsonLdParser.kt`); Android-Share-Target
  nimmt URLs aus dem Browser entgegen.
- **Historie und Feedback:** `RecipeHistoryEntity` protokolliert, was wann gekocht wurde;
  `RecipeFeedbackEntity` hält die Bewertung je Kochvorgang. Beide speisen Statistik und
  KI-Wochenplanung.

Damit ist der gesamte Umfang der archivierten Phasen 15, 16 und 19 aus
[`improvement_plan.md`](../../archiv/improvement_plan.md) umgesetzt.

## Bekannte Lücken

### Bugs

**URL-Import scheitert an einem Default-Parameter.** `server/app/ai.py:270` ruft
`scrape_html(resp.text, org_url=req.url)`. Die Signatur der Bibliothek lautet
`scrape_html(html, org_url, *, online=False, supported_only=None, wild_mode=None, best_image=None)`
— `supported_only` ist per Default aktiv. Für jede Seite, die nicht in der gepflegten Liste von
`recipe-scrapers` steht, fliegt damit `WebsiteNotImplementedError`, obwohl die Bibliothek mit
`supported_only=False` auf generisches schema.org-/JSON-LD-Parsing zurückfallen könnte (dann
`NoSchemaFoundInWildMode`, falls wirklich nichts vorhanden ist). Das erklärt „scheitert häufig"
unabhängig von der konkreten Seite. `wild_mode` ist der veraltete Aliasname und soll nicht
verwendet werden. Verstärkend: `resp.raise_for_status()` (`ai.py:269`) lässt jede 403 aus
Bot-Schutz hart durchschlagen, und clientseitig zeigt `UrlImportViewModel.kt:61-62` rohes
`e.message` — bei einem Server-500 also „HTTP 500" ohne Handlungshinweis. Siehe Backlog A10.

**Benachrichtigungen kommen auf keinem modernen Gerät an.** `POST_NOTIFICATIONS` fehlt im
Manifest (`app/src/main/AndroidManifest.xml:5-8` listet nur `INTERNET`, `ACCESS_NETWORK_STATE`,
`WAKE_LOCK`, `CAMERA`) und wird nirgends zur Laufzeit angefragt. Bei `targetSdk = 35` verwirft
Android 13+ jede Zustellung still. Das blockiert den gewünschten Timer mit Benachrichtigung
(A8). Aufgabe und volle Analyse in [plattform/plan.md](../plattform/plan.md) A4.

**Rotationsbug betrifft auch die Kochansicht.** Aus dem Einkaufslisten-Interview: Eine
Bildschirmdrehung während des Kochens (`RecipeCookScreen`) wirft unvermittelt zurück in die
Einkaufsliste. Behoben in [einkaufsliste/plan.md](../einkaufsliste/plan.md) A4 (Commit
`f223441`) — beim nächsten Gerätetest mitprüfen.

### Funktion & UX
- Suche greift nur auf Name und Beschreibung, nicht auf Zutaten und Tags — der im Interview
  genannte Hauptzugriff („Reis, Nudeln, vegetarisch"). Siehe A5.
- Bewertung existiert an zwei Orten (Sterne am Rezept, Daumen je Kochtermin) ohne erkennbare
  Rollenteilung — laut Interview verwirrend. Siehe A6.
- Kochansicht zwingt zum Wechsel zwischen Zutaten- und Schritt-Tab. Siehe A7.
- Portionsskalierung wird nicht gemerkt. Siehe A9.

### Code-Qualität
- `!!`-Zugriffe: `RecipeCookScreen.kt:165,166,170` auf `activeTimer`,
  `RecipeListScreen.kt:220,240` auf `todayRecipe` — fünf der zehn Projektfälle liegen hier.
- `items()` ohne `key`: `RecipeListScreen.kt:504` (allTags), `:569` (unclassifiedRecipes).
- `RecipeDetailViewModel.kt` bündelt mit 382 Zeilen Rezeptdetails, Nährwerte, Einkaufsliste und
  Wochenplannavigation. Kandidat für Aufteilung.

### Tests
Keine. `IngredientLineParser` und `RecipeJsonLdParser` sind reine Logik und ohne Emulator
testbar — die Schrittparsing-Regeln des Timers ebenso.

### Sync
`recipes`, `ingredients`, `instructions`, `tags`, `recipeTags`, `recipeCategories`,
`availableTags` laufen über `SyncDao`. `recipeHistory` und `recipeFeedback` werden in
`SyncEngine` abweichend über die eigenen DAOs (`RecipeHistoryDao`, `RecipeFeedbackDao`)
behandelt statt über `SyncDao` — funktioniert, bricht aber das Muster aus
[sync-patterns](../../guidelines/sync-patterns.md). Vereinheitlichen oder bewusst dokumentieren.

## Offene Fragen

1. ~~Findest du ein Rezept über die Suche schnell genug, oder ist das Suchfeld zu versteckt?~~
   *Frage beim Stellen korrigiert: Das Suchfeld ist sichtbar (`RecipeListScreen.kt:189`), die
   Prämisse war falsch. Gefragt wurde stattdessen nach dem Suchumfang — siehe Frage 2.*

2. Wonach suchst du in der Praxis — Name, Zutat, Tag? Aktuell greift die Suche nur auf Name und
   Beschreibung.

   **Antwort:** Nach **Tag und Zutat**. Typischer Einstieg: „Rezepte aus Reis, Nudeln,
   vegetarisch oder ähnliches". Zusätzlich die Idee, so etwas mit der automatisierten
   Wochenplan-Erstellung zu verbinden — als Querverweis in
   [wochenplan/plan.md](../wochenplan/plan.md) vermerkt, dort zu entscheiden.

3. Die Bewertung existiert doppelt: Sterne am Rezept und Feedback je Kochvorgang. Ist das
   gewollt oder verwirrend?

   **Antwort:** Verwirrend — zusammenlegen. Auf Nachfrage präzisiert: **nur noch beim Kochen
   bewerten**, die Sterne am Rezept werden daraus automatisch berechnet und sind nur noch
   Anzeige, kein Eingabefeld. Ein Eingabeort, Bewertung bleibt aktuell.

4. ~~Kochansicht: Reicht die Fokusansicht, oder fehlt etwas (Bildschirm anlassen, …)?~~
   *„Bildschirm anlassen" existiert bereits (`RecipeCookScreen.kt:178-179`) — die Frage wurde
   ohne diese Option gestellt.*

   **Antwort:** **Zutaten und Schritt gleichzeitig** sehen; das ständige Wechseln zwischen den
   Tabs nervt. Konkreter Vorschlag: ein **zweigeteilter Landscape-Modus**.

5. Timer werden aus dem Schritttext erkannt. Wie zuverlässig trifft das, und sollen mehrere
   Timer parallel laufen können?

   **Antwort:** **Mehrere Timer parallel und mit Benachrichtigung**, die auch im Hintergrund
   weiterlaufen. Beim Kochen laufen mehrere Dinge gleichzeitig.

6. Beim URL-Import: Welche Seiten scheitern regelmäßig?

   **Antwort:** Scheitert häufig; konkrete Seiten wurden nicht genannt. Die Ursache ist aber
   generisch und im Code belegt (siehe „Bekannte Lücken → Bugs"): der Default
   `supported_only=True` in `scrape_html`. Für die Abnahme nach dem Fix wären zwei bis drei
   deiner Seiten hilfreich.

7. Sollen Rezepte in Sammlungen oder Kochbücher gruppierbar sein, oder reichen Tags?

   **Antwort:** **Tags reichen** — mit der verbesserten Tag-Suche aus Frage 2 braucht es keine
   zweite Gruppierungsebene.

8. Die Skalierung ist reiner UI-Zustand und wird nicht gespeichert. Soll eine geänderte
   Portionszahl beim nächsten Öffnen erhalten bleiben?

   **Antwort:** **Merken pro Rezept.** Wenn ein Rezept immer für 2 statt 4 gekocht wird, soll das
   dauerhaft so bleiben. (Keine globale Haushaltsgröße.)

## Ziele

- Die Suche findet Rezepte über **Tag und Zutat**, nicht nur über den Namen.
- Bewertung hat **genau einen Eingabeort** (beim Kochen); die Sterne am Rezept sind abgeleitete
  Anzeige.
- Kochen ist **ohne Tab-Wechsel** möglich — Zutaten und aktueller Schritt gleichzeitig sichtbar.
- Timer überleben Screen- und App-Wechsel, laufen **mehrfach parallel** und melden sich.
- Eine geänderte Portionszahl **bleibt je Rezept erhalten**.
- Der URL-Import bricht **nicht mehr an unbekannten Seiten** ab und erklärt Fehler verständlich.

## Backlog

Aufwand: S (< 1 h) · M (halber Tag) · L (mehrere Tage)

- [x] **A1** — Fünf `!!`-Zugriffe in `RecipeCookScreen.kt` und `RecipeListScreen.kt` auflösen ·
      S · Impact mittel — **umgesetzt:** lokale `val`s statt wiederholtem `!!`
- [x] **A2** — `key`-Parameter in `RecipeListScreen.kt` ergänzen · S · Impact mittel —
      **umgesetzt:** `allTags` (key = it) und `unclassifiedRecipes` (key = it.id)
- [x] **A3** — Unit-Tests für die **Timer-Erkennung** · S · Impact mittel — der
      `RecipeJsonLdParser`-Teil war deckungsgleich mit [ki](../ki/plan.md) A1 und wird dort
      geführt (Parser liegt in `ui/ai/`) — **umgesetzt (2026-09-01):** `TimerDetectionTest.kt`,
      10 Tests gegen `extractTimers()`/`DetectedTimer` aus `RecipeCookScreen.kt` — Minuten,
      Stunden, Abkürzungen „Sek."/„Min"/„Std" mit und ohne Punkt, Groß-/Kleinschreibung,
      Deduplizierung wenn zwei Formulierungen auf dieselbe Sekundenzahl führen („5 Minuten" /
      „300 Sekunden"), Null-Dauer wird nicht als Timer zurückgegeben, Text ganz ohne
      Zeitangabe, mehrere unterschiedliche Dauern in einem Schritt in Reihenfolge.
- [ ] **A4** — `recipeHistory`/`recipeFeedback` im Sync auf das `SyncDao`-Muster vereinheitlichen oder Abweichung dokumentieren · M · Impact niedrig
- [x] **A5** — Suche auf **Tags und Zutaten** erweitern und dabei vom In-Memory-Filter
      (`RecipeListViewModel.kt:69-74`) auf eine Room-Query umstellen; Muster liegen mit
      `RecipeDao.observeRecipeIdsByTag(s)` (Zeile 33-36) und `searchIngredientNames` bereit · M ·
      Impact hoch — **umgesetzt:** neue `RecipeDao.observeRecipeIdsByTagOrIngredientSearch()`
      (`UNION` über `recipe_tags`/`recipe_ingredients`, `LIKE ... COLLATE NOCASE`) statt eines
      In-Memory-Joins, da Tags/Zutaten in Nebentabellen liegen. Name/Beschreibung bleiben
      bewusst als In-Memory-Filter (die Rezeptliste ist ohnehin schon vollständig geladen,
      dafür lohnt sich keine eigene Query). `RecipeListViewModel.recipes` kombiniert beide
      Ergebnisse per OR — ein Treffer in Name, Beschreibung, Tag ODER Zutat reicht.
- [x] **A6** — Bewertung zusammenlegen: `RecipeFeedbackEntity` wird alleinige Eingabequelle,
      `RecipeEntity.rating` daraus abgeleitet und im Detail nur noch angezeigt; Room-Migration
      und Sync-Anpassung nötig · L · Impact hoch — **umgesetzt:** entgegen der ursprünglichen
      Einschätzung **ohne** Room-Migration/Sync-Anpassung — beide Entities (`RecipeEntity.rating`,
      `RecipeFeedbackEntity.liked`) existierten bereits, nur die Verdrahtung fehlte. Neues
      `RecipeRepository.recalculateRating(recipeId)`: Durchschnitt aller `liked`-Werte (-1..1)
      auf 1–5 Sterne gemappt (`3 + avg*2`, gerundet); bleibt ein Rezept ganz ohne Feedback,
      **bleibt ein alter manueller Wert unangetastet** statt auf „unbewertet" zurückgesetzt zu
      werden (kein rückwirkender Datenverlust). Aufgerufen nach jedem Feedback-Eintrag — sowohl
      vom bestehenden Wochenplan-Tageskärtchen (`WeekplanViewModel.setFeedback`) als auch von
      einem neuen zweiten Eingang: einem "Wie war's?"-Dialog (👍/👎/Überspringen) beim Abschluss
      der Kochansicht (`RecipeCookScreen.kt`, `RecipeCookViewModel.confirmCooked(liked)`) — beide
      schreiben denselben `RecipeFeedbackEntity`-Datensatz, keine zwei getrennten Mechanismen
      mehr. Die freie Sterne-Eingabe im Rezeptdetail (`RatingSection`) ist entfernt,
      `RecipeDetailViewModel.setRating()` gelöscht; die Sektion ist jetzt reine Anzeige und
      blendet sich aus, solange kein Feedback vorliegt.
- [x] **A7** — Kochansicht: geteilter Landscape-Modus mit Zutaten und aktuellem Schritt
      nebeneinander · M · Impact hoch — **umgesetzt:** neue `CookSplitView` in
      `RecipeCookScreen.kt`, automatisch aktiv sobald `LocalConfiguration.current.orientation ==
      Configuration.ORIENTATION_LANDSCAPE` — kein manueller Umschalter, ersetzt im Querformat
      sowohl die Listen- als auch die bisherige Fokusansicht. Links eine eigene Spalte mit
      Portionssteuerung + Zutaten-Checkliste (hier immer ausgeklappt, kein Auf-/Zuklappen wie im
      Hochformat), rechts unverändert `CookFocusView` (Schritt-Pager mit Timer-Chips), nur
      schmaler gerendert statt vollflächig — kein Duplikat, dieselbe Komponente. Der
      Fokus-Umschalter im TopAppBar wird im Querformat ausgeblendet, da die Split-Ansicht ihn
      ersetzt. Persönliche Notizen bleiben im Split bewusst außen vor (nicht Teil des Auftrags,
      im Hochformat weiterhin sichtbar). Reine UI-Änderung, keine Datenmodell-Anpassung. **Nicht
      visuell auf Emulator getestet** — diese Umgebung hat keinen laufenden Android-Emulator
      (Standard-Vorgehen dieser Session: nur Compile + Unit-Tests als Verifikation), Rotation
      und Split-Layout sollten vor dem nächsten Release einmal auf einem echten Gerät/Emulator
      geprüft werden.
- [x] **A8** — Timer: mehrere parallel, laufen im Hintergrund weiter, melden sich per
      Benachrichtigung; Kanal `helga_reminders` aus `NotificationScheduler.kt` wiederverwendbar,
      aber mit `IMPORTANCE_HIGH`. **Setzt [plattform](../plattform/plan.md) A4 voraus** · L · Impact hoch —
      **umgesetzt:** Korrektur der ursprünglichen Annahme — `helga_reminders` ließ sich nicht
      wiederverwenden, weil Android die Priorität eines Kanals nach dem Erstanlegen nicht mehr
      ändern lässt und `helga_reminders` bereits mit `IMPORTANCE_DEFAULT` existiert; stattdessen
      neuer eigener Kanal `helga_timers` mit `IMPORTANCE_HIGH`. Neuer `CookingTimerManager`
      (Singleton, `data/cooking/`) hält eine geteilte `StateFlow<List<ActiveCookingTimer>>` und
      plant pro Timer einen `WorkManager`-Einmaljob (`CookingTimerWorker`, `@HiltWorker`,
      eindeutiger Work-Name je Timer-ID) mit `setInitialDelay()` — das eigentliche "Klingeln"
      hängt damit nicht am App-Prozess oder Compose-Lifecycle wie vorher (ein einzelner lokaler
      `activeTimer`-State im Bildschirm, verschwand beim Verlassen), sondern lebt in WorkManagers
      eigener DB und feuert auch nach Backgrounding/Prozessende pünktlich die Benachrichtigung.
      `RecipeCookScreen.kt` komplett auf Mehrfach-Timer umgebaut: neue `ActiveTimersRow`
      (Chip-Leiste mit Live-Countdown) sichtbar in Listen-, Fokus- und Split-Ansicht gleichermaßen
      statt eines einzelnen blockierenden Dialogs; Tippen auf einen Chip öffnet `TimerDialog` für
      Details. Bewusste Vereinfachung dabei: kein Pause/Fortsetzen mehr (ergab bei
      Hintergrund-Timern keinen Sinn mehr — die ganze Idee ist ja, dass er weiterläuft), nur noch
      Zurücksetzen und Abbrechen. Voraussetzung [plattform](../plattform/plan.md) A4
      (POST_NOTIFICATIONS) war bereits erledigt. **Nicht auf echtem Gerät/Emulator getestet**
      (keine laufende Android-Umgebung in dieser Session) — insbesondere das tatsächliche
      Hintergrund-Verhalten (App komplett schließen, Timer läuft trotzdem ab und benachrichtigt)
      sollte vor dem nächsten Release einmal manuell verifiziert werden.
- [x] **A9** — Portionsskalierung je Rezept persistieren (neues Feld in `RecipeEntity` +
      Room-Migration v31 + Sync-Anbindung) · M · Impact mittel — **umgesetzt:** neues Feld
      `RecipeEntity.lastServings` (0 = noch nie geändert, dann gilt weiterhin der aus
      `recipeYield` geparste Standardwert), Room-Migration 32→33 (nicht v31 — DB war durch
      zwischenzeitliche Arbeit inzwischen bei v32). `RecipeDetailViewModel.setServings()`
      schreibt bei jeder Änderung über `RecipeRepository.updateLastServings()` und triggert
      Sync, `init` liest `lastServings` statt immer auf den Rezept-Standard zurückzufallen.
      Serverseitig `last_servings`-Spalte non-destruktiv ergänzt (`db.py` CREATE TABLE +
      `TABLE_COLUMNS`, `models.py`, `sync.py`).
- [x] **A10** — URL-Import reparieren: `supported_only=False` in `server/app/ai.py:270`,
      `WebsiteNotImplementedError` / `NoSchemaFoundInWildMode` / HTTP-Fehler getrennt behandeln
      und als verständliche deutsche Meldung ausgeben statt rohem `e.message`
      (`UrlImportViewModel.kt:61-62`); `RecipeJsonLdParser` als Client-Fallback prüfen · M ·
      Impact hoch — **umgesetzt:** `import_url()` in `server/app/ai.py` fängt jetzt
      `httpx.HTTPStatusError`/`httpx.RequestError` beim Abruf und
      `WebsiteNotImplementedError`/`NoSchemaFoundInWildMode`/generische Scraper-Fehler getrennt
      ab, jeweils als `HTTPException(422, detail="...")` mit verständlichem deutschen Text.
      `scrape_html()` läuft jetzt mit `supported_only=False` — Seiten ohne dediziertem Scraper
      werden per generischem schema.org-Parser versucht statt sofort abgelehnt zu werden, der
      Haupt-Fehlerfall überhaupt. `UrlImportViewModel.kt` liest bei `HttpException` das
      `detail`-Feld aus dem Fehlerbody (`org.json.JSONObject`, gleiches Muster wie
      `SettingsViewModel.kt`) statt `e.message` zu zeigen; `IOException` (offline) und
      unbekannte Fehler bleiben als Fallback. **Bewusst nicht umgesetzt:** clientseitiger
      `RecipeJsonLdParser`-Fallback bei komplettem Scraper-Fehlschlag — eigenständige neue
      Fähigkeit (Client müsste die Seite selbst laden), durch den serverseitigen Wild-Mode-Fix
      deckt der Server jetzt bereits den weit überwiegenden Fehlerfall ab; als Folgeaufgabe
      vermerkt statt in dieser Runde mit umgesetzt.

## Entscheidungen

| Datum | Entscheidung | Begründung |
|-------|--------------|------------|
| 2026-08-22 | Keine Sammlungen/Kochbücher — Tags bleiben die einzige Gruppierungsebene | Interview: mit besserer Tag-Suche (A5) keine zweite Ebene nötig |
| 2026-08-22 | Bewertung wird auf „nur beim Kochen bewerten" vereinheitlicht, Sterne am Rezept werden aus den Kochbewertungen abgeleitet | Interview: doppelte Bewertung war verwirrend; ein Eingabeort, Wert bleibt automatisch aktuell |
| 2026-08-22 | Portionsskalierung wird je Rezept gemerkt, keine globale Haushaltsgröße | Interview: Skalierung ist rezeptspezifisch, nicht haushaltsweit |
| 2026-08-22 | Timer bekommen Hintergrundlauf und Benachrichtigung statt nur Screen-Bindung | Interview: beim Kochen laufen mehrere Dinge parallel |
