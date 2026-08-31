# Feature: Plattform-Integration

> **Status:** Interview erledigt · **Aufgaben:** 1 offen (3 erledigt) · **Stand:** 2026-08-31 · **Priorität:** ⭐

Alles, was die App mit dem Gerät und der Auslieferung verbindet: Widget, Wear OS, Share-Target,
Build und CI. Kein Fachbereich, sondern die Hülle.

## Umfang

| Ebene | Dateien |
|-------|---------|
| Widget | `app/src/main/kotlin/com/helga/android/ui/widget/TodayRecipeWidget.kt` |
| Wear OS | `app/src/main/kotlin/com/helga/android/ui/shopping/ShoppingListWearScreen.kt` |
| Einstieg | `app/src/main/kotlin/com/helga/android/MainActivity.kt`, `app/src/main/kotlin/com/helga/android/HelgaNavGraph.kt` (21 Routen), `app/src/main/AndroidManifest.xml` |
| Build | `app/build.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `app/proguard-rules.pro` |
| CI | `.github/workflows/android-ci.yml` |
| Benchmark | `benchmark/` (Baseline Profile) |

## Ist-Analyse

- **Widget:** `TodayRecipeWidget` zeigt das für heute geplante Rezept auf dem Startbildschirm.
- **Wear OS:** `ShoppingListWearScreen` als reduzierte Einkaufsliste.
- **Share-Target:** Die App nimmt URLs aus dem Browser entgegen und leitet sie in den
  Rezept-Import.
- **Navigation:** Single-Activity mit 21 Routen; Bottom-Nav zeigt drei Tabs (Einkaufsliste,
  Rezepte, Wochenplan) — Bons, Statistik, Märkte und Einstellungen werden über andere Wege
  erreicht.
- **Build:** R8 mit Resource Shrinking, ausgebaute Proguard-Regeln, `versionCode` aus dem
  Git-Commit-Count, fester Debug-Keystore für gleichbleibende APK-Signatur, Baseline Profile
  über das Benchmark-Modul.
- **Diagnose im Debug-Build:** StrictMode und LeakCanary.
- **CI:** Lint, Unit-Tests und APK-Bau pro Push; die Debug-APK wird als Artefakt hochgeladen.
- **Datenschutz:** Die Room-Datenbank ist vom Android-Auto-Backup ausgenommen.

## Bekannte Lücken

### Funktion & UX
- Vier von elf Bereichen liegen nicht im Bottom-Nav. Ob die Einstiegspunkte auffindbar sind,
  klärt Frage 2.
- **Bestätigt im Interview:** Bons, Statistik und Märkte sind zu langsam/versteckt erreichbar.
  Für Bons und Statistik existieren bereits Lösungen (neuer Bons-Tab, siehe
  [bons-kosten](../bons-kosten/plan.md) A3 und [statistik](../statistik/plan.md) A2). Märkte
  ist noch nicht adressiert: `HelgaNavGraph.kt:210` — `onStoresClick` kommt ausschließlich aus
  `SettingsScreen`, also Einkaufsliste/Rezepte/Wochenplan → Einstellungen → Märkte, zwei Hops
  tief. Aufgabe ergänzt in [maerkte](../maerkte/plan.md) A4.
- **Wear OS ist kein eigenständiges Wear-App-Modul.** `MainActivity.kt` unterscheidet zur
  Laufzeit per `isRunningOnWearOs()` (`packageManager.hasSystemFeature(FEATURE_WATCH)`) und
  zeigt dieselbe APK entweder als Handy-Nav-Graph oder als `ShoppingListWearScreen`. Ohne
  separates `:wear`-Gradle-Modul installiert sich die App nicht automatisch auf eine gepaarte
  Uhr — der Nutzer muss manuell sideloaden. Aus dem Einkaufslisten-Interview (Frage 7): genau
  das war der Grund, warum der Wear-Screen nie genutzt wurde. Ausbau der Abhak-Funktion selbst
  steht in [einkaufsliste/plan.md](../einkaufsliste/plan.md) A7, die Modul-Umstellung hier in A3.

### Bugs

**`POST_NOTIFICATIONS` fehlt — alle Benachrichtigungen sind auf modernen Geräten wirkungslos.**
`app/src/main/AndroidManifest.xml:5-8` deklariert nur `INTERNET`, `ACCESS_NETWORK_STATE`,
`WAKE_LOCK` und `CAMERA`. Die Berechtigung fehlt vollständig, und es gibt nirgends eine
Laufzeitabfrage dafür — der einzige Permission-Launcher im Projekt betrifft die Kamera
(`ReceiptScanScreen.kt:112`). Bei `targetSdk = 35` verwirft Android 13+ (API 33+) damit jede
`nm.notify()`-Zustellung still.

Betroffen sind die beiden bereits fertig gebauten Erinnerungen in
`app/src/main/kotlin/com/helga/android/data/sync/NotificationScheduler.kt` (`NOTIFY_ID_SHOPPING`,
`NOTIFY_ID_COOK`) samt ihren Schaltern in den Einstellungen — sie sehen aus wie funktionierende
Features, tun aber nichts. Gefunden im Rezepte-Interview, weil derselbe Mangel den dort
gewünschten Timer mit Benachrichtigung blockiert ([rezepte](../rezepte/plan.md) A8).

Die übrige Infrastruktur ist brauchbar: Kanal `helga_reminders`, `ensureChannel()` und
`notify()` lassen sich wiederverwenden; ein Timer braucht allerdings `IMPORTANCE_HIGH` statt des
aktuellen `IMPORTANCE_DEFAULT`.

**Rotationsbug in `HelgaNavGraph.kt` — behoben.** Ein unconditional feuernder
`LaunchedEffect(Unit)` navigierte bei jeder Activity-Neuerstellung (Bildschirmdrehung, da
`MainActivity` kein `android:configChanges` deklariert) zurück zu `ROUTE_SHOPPING`, unabhängig
vom aktuellen Screen. Fix in [einkaufsliste/plan.md](../einkaufsliste/plan.md) A4, Commit
`f223441`. Gerätetest steht noch aus.

### Code-Qualität
- `items()` ohne `key`: `ShoppingListWearScreen.kt:69`.
- `contentDescription = null` an 82 Stellen im Projekt. Ein Teil davon ist bei rein dekorativen
  Icons korrekt; jeder Fall an einem Bedienelement ist ein Verstoß gegen
  [ux-accessibility](../../guidelines/ux-accessibility.md). Die Trennung ist Handarbeit und
  gehört als eigene Durchsicht geplant, nicht in einen Sammel-Fix.

### Tests
Keine Instrumentierungstests — ein `androidTest`-Quellverzeichnis existiert nicht. Damit ist keine
Navigation, kein Screen und keine Nutzerinteraktion automatisiert abgedeckt. Die CI baut zwar,
prüft aber nichts, was ein Nutzer sieht.

### Sync
Nicht zutreffend.

## Fragen

1. **Nutzt du Widget und Wear-Screen tatsächlich?**
   Antwort Widget: Kaum/nie. Antwort Wear: Nie genutzt — weiß nicht mal, wie das Sideloading
   funktioniert. Bestätigt: Die Sideload-Hürde aus der Ist-Analyse ist real und hat die Nutzung
   auf null gedrückt, nicht nur reduziert.
2. **Findest du Bons, Statistik und Märkte schnell genug?**
   Antwort: Schon zu langsam/versteckt. Siehe Root Cause oben — für Bons/Statistik schon gelöst,
   für Märkte neu ergänzt.
3. **Wie installierst du neue Versionen?**
   Antwort: APK aus der CI (GitHub Actions Artefakt).
4. **UI-Tests für die drei Kernabläufe die Mühe wert?**
   Antwort: Manuelles Prüfen reicht.
5. **Release-Signing statt Debug-Keystore?**
   Antwort: Debug-Keystore bleibt — passt zum Installationsweg per CI-Artefakt.
6. **Fehlt eine erwartete Plattform-Anbindung?**
   Antwort: Nein, nichts vermisst.

## Ziele

- Bons, Statistik und Märkte aus der Hauptnavigation heraus erreichbar machen — für Bons und
  Statistik bereits als Aufgabe in den jeweiligen Bereichen verankert, für Märkte hier neu
  ergänzt.
- Wear-Modul-Umstellung (A3) regulär einplanen: Die Uhr wird aktuell nicht genutzt, **weil**
  die Sideload-Hürde besteht — der Nutzer erwartet vom Umbau genau die Nutzung, die heute
  fehlt (Einkaufsliste auf der Uhr abhaken). Ursache und Wirkung nicht verwechseln.
- Kein Ausbau bei UI-Tests, Release-Signing oder zusätzlichen Plattform-Anbindungen — kein
  Bedarf geäußert, aktueller Installationsweg (CI-APK) bleibt bestehen.

## Backlog

Aufwand: S (< 1 h) · M (halber Tag) · L (mehrere Tage)

- [x] **A1** — `key`-Parameter in `ShoppingListWearScreen.kt` ergänzen · S · Impact mittel —
      **umgesetzt:** `key = { it.id }`
- [x] **A2** — Durchsicht aller 82 `contentDescription = null`: dekorativ belassen, Bedienelemente
      beschriften · M · Impact **niedrig** (2026-08-30 herabgestuft: Guideline-getrieben, kein
      Screenreader-Bedarf in dieser App) — **umgesetzt:** alle 82 Fundstellen in 18 Dateien
      einzeln geprüft. 27 waren echte, unbeschriftete Bedienelemente (IconButton ohne
      begleitenden Text) — beschriftet: Zurück-Buttons (`action_back`, neu wiederverwendet für
      `StatsScreen`/`AiGenerateScreen`/`AiRemixScreen`), Overflow-Menüs (`action_more`, neu),
      Suche-leeren (`action_clear_search`, neu, in `RecipeListScreen` und
      `WeekplanRecipePickerScreen`), Portionen +/− (`servings_decrease`/`servings_increase`,
      neu, an drei Stellen: `RecipeCookScreen`, `RecipeDetailScreen`,
      `WeekplanScreen`/`ShoppingListPickerDialog`), diverse Löschen-Buttons (bestehendes
      `recipe_delete` wiederverwendet: Einkaufsliste/Schnellbutton in `SettingsScreen`,
      Fest-Artikel in `ShoppingListScreen`, Gang in `StoreListScreen`), sowie je Bildschirm
      spezifische Buttons (Tag/Zutat/Schritt hinzufügen/entfernen in `RecipeFormScreen`,
      Extra hinzufügen/entfernen in `WeekplanScreen`, Fest-Artikel/Schnell-Hinzufügen in
      `ShoppingListScreen`, aktiven Markt setzen/Gang hinzufügen in `StoreListScreen`,
      Schnellbutton bearbeiten in `SettingsScreen`). Die übrigen 55 blieben `null` — durchweg
      redundant mit sichtbarem Text direkt am selben Element (Chips/DropdownMenuItems mit
      `label`/`text`, Buttons mit Icon+Text, Bilder/Fallback-Icons neben Namens-Text, rein
      visuelle Sternebewertungen, Zwischenablage-Swipe-Hintergrund) oder redundant mit einem
      Textfeld-Label/Placeholder (Such-/URL-Icons). Keine einzige echte Regression gefunden — die
      Guideline-Vermutung "still overengineered accessibility debt" bestätigte sich nicht, es
      waren tatsächlich fehlende Labels an konkreten Bedienelementen.
- [ ] **A3** — Eigenständiges `:wear`-Gradle-Modul statt Laufzeit-Unterscheidung in
      `MainActivity.kt`, damit die Watch-App automatisch mit der Handy-App auf die gepaarte Uhr
      installiert wird · L · Impact **hoch** — Voraussetzung für
      [einkaufsliste/plan.md](../einkaufsliste/plan.md) A7. Der Nutzer erwartet künftig
      genau diesen Ablauf (Liste auf der Uhr beim Einkaufen abhaken); die Sideload-Hürde ist
      der einzige Grund für die aktuelle Nichtnutzung
- [x] **A4** — `POST_NOTIFICATIONS` im Manifest deklarieren und zur Laufzeit anfragen (Muster:
      Kamera-Abfrage in `ReceiptScanScreen.kt:112`). Ohne das sind die bestehenden Einkaufstag-
      und Koch-Erinnerungen auf Android 13+ wirkungslos · S · **Impact hoch** — Voraussetzung
      für [rezepte](../rezepte/plan.md) A8 (Timer mit Benachrichtigung) — **umgesetzt:**
      Manifest-Eintrag, `NotificationScheduler.notify()` prüft die Berechtigung vor jedem Aufruf
      (still übersprungen statt Crash/Fehlversand), `SettingsScreen` fragt die Berechtigung an,
      sobald einer der beiden Erinnerungs-Schalter aktiviert wird

_Weitere Aufgaben nach dem Interview. Widget-Nutzung bewusst nicht als Aufgabe aufgenommen —
bestätigt kaum genutzt, aber kein Änderungswunsch geäußert, nur eine Beobachtung._

## Entscheidungen

| Datum | Entscheidung | Begründung |
|-------|--------------|------------|
| 2026-08-30 | ~~A3 (:wear-Modul) mit gesenkter Priorität~~ **revidiert am selben Tag:** A3 wird regulär eingeplant, Impact hoch | Nachfrage ergab: Die Nichtnutzung ist Folge der Sideload-Hürde, nicht mangelnden Interesses — der Nutzer erwartet vom Umbau aktive Nutzung |
| 2026-08-30 | A2 (contentDescription) von Impact hoch auf niedrig | Kommt aus der Guideline, nicht aus dem Bedarf: private App ohne Screenreader-Nutzung |
| 2026-08-30 | Märkte-Erreichbarkeit wird in maerkte/plan.md als A4 ergänzt | Zwei Hops tief (Settings → Stores), im Interview als "zu versteckt" bestätigt |
| 2026-08-30 | Debug-Keystore bleibt, keine UI-Tests, keine weiteren Plattform-Anbindungen | Passt zum aktuellen CI-APK-Installationsweg, kein Bedarf geäußert |
