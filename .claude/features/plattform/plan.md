# Feature: Plattform-Integration

> **Status:** Interview offen · **Aufgaben:** 0/0 · **Stand:** 2026-08-22 · **Priorität:** ⭐

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
- **Rotationsbug in `HelgaNavGraph.kt:96-104`** (aus dem Einkaufslisten-Interview): Ein
  unconditional feuernder `LaunchedEffect(Unit)` navigiert bei jeder Activity-Neuerstellung
  (z. B. Bildschirmdrehung, da `MainActivity` kein `android:configChanges` deklariert) zurück zu
  `ROUTE_SHOPPING` — unabhängig vom aktuellen Screen. Wirft aus der Kochansicht und wechselt die
  aktive Einkaufsliste. Volle Ursachenanalyse und der eigentliche Fix liegen in
  [einkaufsliste/plan.md](../einkaufsliste/plan.md) A4, um die Aufgabe nicht doppelt zu führen —
  hier nur der Verweis, da die Datei strukturell in diesen Bereich gehört.
- **Wear OS ist kein eigenständiges Wear-App-Modul.** `MainActivity.kt` unterscheidet zur
  Laufzeit per `isRunningOnWearOs()` (`packageManager.hasSystemFeature(FEATURE_WATCH)`) und
  zeigt dieselbe APK entweder als Handy-Nav-Graph oder als `ShoppingListWearScreen`. Ohne
  separates `:wear`-Gradle-Modul installiert sich die App nicht automatisch auf eine gepaarte
  Uhr — der Nutzer muss manuell sideloaden. Aus dem Einkaufslisten-Interview (Frage 7): genau
  das war der Grund, warum der Wear-Screen nie genutzt wurde. Ausbau der Abhak-Funktion selbst
  steht in [einkaufsliste/plan.md](../einkaufsliste/plan.md) A7, die Modul-Umstellung hier in A3.

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

## Offene Fragen

1. Nutzt du Widget und Wear-Screen tatsächlich? Beide sind Aufwand ohne erkennbare Rückmeldung.
2. Findest du Bons, Statistik und Märkte schnell genug, oder sollen sie in die Hauptnavigation?
3. Wie installierst du neue Versionen — APK aus der CI, oder anders?
4. Wären UI-Tests für die drei Kernabläufe die Mühe wert, oder reicht dir manuelles Prüfen?
5. Soll die App auf ein Release-Signing umgestellt werden, oder bleibt der Debug-Keystore?
6. Fehlt eine Plattform-Anbindung, die du erwartest — Quick Settings, Assistant, Freigabe von
   Rezepten an andere Apps?

## Ziele

_Nach dem Interview zu füllen._

## Backlog

Aufwand: S (< 1 h) · M (halber Tag) · L (mehrere Tage)

- [ ] **A1** — `key`-Parameter in `ShoppingListWearScreen.kt:69` ergänzen · S · Impact mittel
- [ ] **A2** — Durchsicht aller 82 `contentDescription = null`: dekorativ belassen, Bedienelemente beschriften · M · Impact hoch
- [ ] **A3** — Eigenständiges `:wear`-Gradle-Modul statt Laufzeit-Unterscheidung in
      `MainActivity.kt`, damit die Watch-App automatisch mit der Handy-App auf die gepaarte Uhr
      installiert wird · L · Impact mittel — Voraussetzung für
      [einkaufsliste/plan.md](../einkaufsliste/plan.md) A7

_Weitere Aufgaben nach dem Interview._

## Entscheidungen

| Datum | Entscheidung | Begründung |
|-------|--------------|------------|
