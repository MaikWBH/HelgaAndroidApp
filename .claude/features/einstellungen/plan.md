# Feature: Einstellungen & Onboarding

> **Status:** Interview erledigt · **Aufgaben:** 1 offen (3 erledigt) · **Stand:** 2026-08-30 · **Priorität:** ⭐

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
- **Root Cause zu „Einstellungen nur über Rezepte erreichbar" (aus dem Interview):**
  `HelgaNavGraph.kt:156-166` — nur `RecipeListScreen` bekommt den Parameter `onSettingsClick`;
  `ShoppingListScreen` und `WeekplanScreen` haben keinen Einstiegspunkt zu den Einstellungen.
  Der `Scaffold` in `HelgaNavGraph.kt:107-131` hat nur eine `bottomBar`, keine gemeinsame
  `topBar` — jeder Root-Screen baut seine eigene TopAppBar unabhängig, es gibt also keine
  zentrale Stelle, die automatisch für alle drei gilt.
- **Zwei Schalter schalten ins Leere.** `setNotifyShoppingDay` und `setNotifyCookReminder`
  steuern Benachrichtigungen, die auf Android 13+ gar nicht zugestellt werden — im Manifest
  fehlt `POST_NOTIFICATIONS`, und es gibt keine Laufzeitabfrage. Gefunden im Rezepte-Interview;
  Analyse und Aufgabe in [plattform](../plattform/plan.md) A4. Bis dahin versprechen die
  Einstellungen eine Funktion, die es nicht gibt.

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

## Fragen

1. **Findest du eine bestimmte Einstellung regelmäßig nicht wieder?**
   Antwort: Ja, kommt vor. Die Einstellungen wirken allgemein überladen/unstrukturiert.
   Zusätzlich: verwirrend, dass Einstellungen nur über die Rezepte-Karte aufrufbar sind — sollte
   zentraler und intuitiver sein. Siehe Root Cause oben.
2. **Welche Einstellungen änderst du tatsächlich mehr als einmal?**
   Antwort: Einkaufsliste (Standardliste, Emoji-Buttons); ansonsten kaum etwas öfter als einmal
   — die meisten Einstellungen werden einmal gesetzt und nie wieder angefasst.
3. **Datenexport: wofür genutzt, Format brauchbar?**
   Antwort: Nicht genutzt — Server-Sync reicht als Absicherung, der Docker-Container wird
   zusätzlich separat gebackupt.
4. **Onboarding: Hürden bei der letzten Ersteinrichtung?**
   Antwort: Problemlos.
5. **Sollen Einstellungen zwischen Geräten syncen?**
   Antwort: Nein, bewusst pro Gerät.
6. **Ist die aktuelle API-Schlüssel-Speicherung in Ordnung?**
   Antwort: Ja, in Ordnung.
7. **Fehlt ein Reset-Weg ohne Neuinstallation?**
   Antwort: Wäre nett, kein Muss.

## Ziele

- Einstellungen von allen drei Hauptscreens (Einkaufsliste, Rezepte, Wochenplan) aus erreichbar
  machen, nicht nur über Rezepte.
- Struktur überarbeiten: häufig genutzte Einstellungen (Einkaufsliste) vorn, selten geänderte
  Einstellungen in eine „Erweitert"-Sektion einklappen — passend zur Beobachtung, dass die
  meisten Einstellungen nur einmal gesetzt werden.
- Onboarding, Einstellungs-Sync-Verhalten, API-Key-Speicherung und Datenexport unverändert
  lassen — kein Bedarf geäußert.
- Reset-Funktion als Nice-to-have vormerken, keine aktuelle Priorität.

## Backlog

Aufwand: S (< 1 h) · M (halber Tag) · L (mehrere Tage)

- [x] **A1** — `!!` in `SettingsScreen.kt` (`syncError`) auflösen · S · Impact mittel —
      **umgesetzt:** lokale `val` statt `!!`
- [x] **A2** — Einstellungen von allen Hauptscreens erreichbar machen: `ShoppingListScreen`
  und `WeekplanScreen` bekommen einen Einstellungs-Zugang (z. B. Overflow-Menü, analog zum
  bereits vorhandenen Muster in `ShoppingListScreen.kt`), nicht nur `RecipeListScreen` ·
  M · Impact hoch — **umgesetzt:** neuer `DropdownMenuItem` „Einstellungen" in beiden
  Overflow-Menüs, `onNavigateToSettings`-Parameter durchgereicht bis `ROUTE_SETTINGS`
- [x] **A3** — Einstellungen umstrukturieren: häufig genutzte Einstellungen (Einkaufsliste)
  vorn, Rest in „Erweitert" einklappen, um die flache 11-Bereiche-Liste zu entzerren ·
  M · Impact hoch — **umgesetzt:** Einkaufsliste-Sektion (inkl. Standardliste) und
  Schnellbuttons stehen jetzt oben, direkt sichtbar; Darstellung, Benachrichtigungen, Server,
  Sync, KI-Massenlauf und Konto stecken hinter einem aufklappbaren „Erweitert"-Bereich
  (`showAdvanced`-State, `ExpandMore`/`ExpandLess`-Icon), standardmäßig eingeklappt
- [ ] **A4** — Reset-Funktion für alle lokalen Daten ohne Neuinstallation · M · Impact niedrig
  — Nice-to-have, kein Muss

_Weitere Aufgaben nach dem Interview._

## Entscheidungen

| Datum | Entscheidung | Begründung |
|-------|--------------|------------|
| 2026-08-30 | Einstellungen werden von allen drei Hauptscreens aus erreichbar gemacht | Aktuell nur über Rezepte erreichbar, explizit als verwirrend benannt |
| 2026-08-30 | Einstellungen werden nach Nutzungshäufigkeit umstrukturiert | Die meisten Einstellungen werden nur einmal gesetzt, Einkaufsliste-Einstellungen öfter |
| 2026-08-30 | Einstellungs-Sync, API-Key-Speicherung, Datenexport und Onboarding bleiben unverändert | Kein Bedarf geäußert, Server-Backup deckt Absicherung bereits ab |
| 2026-08-30 | Reset-Funktion wird als Nice-to-have vorgemerkt, nicht priorisiert | Kein aktiver Bedarf |
