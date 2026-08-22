# Feature: Einstellungen & Onboarding

> **Status:** Interview offen · **Aufgaben:** 0/0 · **Stand:** 2026-08-22 · **Priorität:** ⭐

Erste Einrichtung und zentrale Konfiguration. `SettingsViewModel` ist mit 25 öffentlichen
Funktionen die Sammelstelle für Einstellungen aller anderen Bereiche.

## Umfang

| Ebene | Dateien |
|-------|---------|
| UI | `app/src/main/kotlin/com/helga/android/ui/settings/SettingsScreen.kt`, `SettingsViewModel.kt`, `app/src/main/kotlin/com/helga/android/ui/onboarding/OnboardingScreen.kt`, `OnboardingViewModel.kt` |
| Speicher | `app/src/main/kotlin/com/helga/android/data/preferences/AppPreferences.kt` (DataStore) |
| Room | `app/src/main/kotlin/com/helga/android/data/local/entity/WeekplanSettingsEntity.kt`, `QuickEmojiEntity.kt`, `MonthlyBudgetEntity.kt` |
| Strings | `app/src/main/res/values/strings.xml` (322 Einträge) |

## Ist-Analyse

- **Server:** URL eingeben, testen und speichern (`setServerUrl`, `testAndSave`), abmelden
  (`logout`), manueller Sync (`syncNow`) mit Fehleranzeige.
- **KI:** API-Schlüssel (`setApiKey`), Massenlauf über unklassifizierte Rezepte (`runBulkAi`,
  `dismissBulkAiResult`).
- **Darstellung:** Theme (`setThemeMode`), Akzentfarbe (`setAccentColor`).
- **Wochenplan:** Zeitraum 7/10/14 Tage (`setWeekplanDays`), Einkaufstag (`setShoppingDay`).
- **Einkaufsliste:** Standardliste (`setDefaultShoppingListId`), Listen löschen
  (`deleteShoppingList`), Abhak-Verhalten (`setCheckMode`), Emoji-Schnellbuttons anlegen,
  ändern, löschen (`addQuickEmoji`, `updateQuickEmoji`, `deleteQuickEmoji`).
- **Bons:** Abgleich ein-/ausschalten (`setReceiptReconciliationEnabled`), Schwelle für die
  Scan-Erinnerung (`setScanReminderThreshold`).
- **Benachrichtigungen:** Einkaufstag (`setNotifyShoppingDay`), Koch-Erinnerung
  (`setNotifyCookReminder`).
- **Datenexport:** `exportAllData`, `clearExport`.
- **Feature-Schalter:** `saveFeatureSettings`.
- **Onboarding:** eigener Screen für die Ersteinrichtung, Einstiegspunkt der Navigation
  (`ROUTE_ONBOARDING`).

## Bekannte Lücken

### Funktion & UX
- `SettingsScreen.kt` sammelt die Einstellungen aller elf Bereiche in einer flachen Ansicht.
  Ob die Gliederung bei diesem Umfang noch trägt, klärt Frage 1.

### Code-Qualität
- `!!`-Zugriff: `SettingsScreen.kt:352` auf `syncError`.
- `SettingsViewModel.kt` mit 450 Zeilen und 25 Funktionen ist ein Sammelbecken; eine Aufteilung
  entlang der Fachbereiche wäre möglich, erhöht aber die Zahl der Klassen.

### Tests
`app/src/test/kotlin/` enthält `SettingsValidationTest.kt` — deckt die URL-Validierung ab. Alles
andere ungetestet.

### Sync
Keine eigene Entity ohne Anschluss. `weekplanSettings` und `quickEmojis` sind angebunden;
reine Gerätepräferenzen in `AppPreferences.kt` sind bewusst lokal.

## Offene Fragen

1. Findest du eine bestimmte Einstellung regelmäßig nicht wieder?
2. Welche Einstellungen änderst du tatsächlich mehr als einmal — der Rest könnte einklappen.
3. Der Datenexport: wofür nutzt du ihn, und ist das Format brauchbar?
4. Onboarding: Hat die Ersteinrichtung beim letzten Mal funktioniert, oder gab es Hürden?
5. Sollen Einstellungen zwischen Geräten synchronisiert werden, oder bewusst pro Gerät bleiben?
6. Der API-Schlüssel liegt in den Einstellungen. Ist die aktuelle Speicherung für dich in
   Ordnung, oder soll das abgesichert werden?
7. Fehlt ein Weg, alle Daten zurückzusetzen, ohne die App neu zu installieren?

## Ziele

_Nach dem Interview zu füllen._

## Backlog

Aufwand: S (< 1 h) · M (halber Tag) · L (mehrere Tage)

- [ ] **A1** — `!!` in `SettingsScreen.kt:352` auflösen · S · Impact mittel

_Weitere Aufgaben nach dem Interview._

## Entscheidungen

| Datum | Entscheidung | Begründung |
|-------|--------------|------------|
