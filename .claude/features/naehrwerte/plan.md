# Feature: Nährwerte & Allergene

> **Status:** Interview erledigt · **Aufgaben:** 4 offen · **Stand:** 2026-08-30 · **Priorität:** ⭐⭐

OpenFoodFacts-Anbindung, Nährwerte je Rezept, Nutri-Score und Allergenwarnungen. Querschnitts-
funktion ohne eigenen Screen — sichtbar in Rezepten, Einkaufsliste und Wochenplan.

## Umfang

| Ebene | Dateien |
|-------|---------|
| UI | `app/src/main/kotlin/com/helga/android/ui/components/AllergenWarningBanner.kt`, `app/src/main/kotlin/com/helga/android/ui/components/BarcodeScanner.kt` |
| Room | `app/src/main/kotlin/com/helga/android/data/local/entity/OffProductEntity.kt` |
| DAO | `app/src/main/kotlin/com/helga/android/data/local/dao/OffProductDao.kt` |
| Modell | `app/src/main/kotlin/com/helga/android/data/model/RecipeNutrition.kt`, `AllergyProfile.kt` |
| Hilfsklassen | `app/src/main/kotlin/com/helga/android/data/util/AllergyChecker.kt` |
| Server | `server/app/off.py`; Endpunkte `/api/off/lookup-barcode`, `/api/off/search`, `/api/ai/nutrition` in `server/app/main.py` |

## Ist-Analyse

- **Produktsuche:** Barcode-Scan (`BarcodeScanner.kt`) und Textsuche gegen OpenFoodFacts über
  den Server; Treffer landen als `OffProductEntity` im lokalen Katalog (`upsert`, `upsertAll`).
- **Katalog:** `allActive` und `observeFavorites` speisen die Ansicht „Meine Produkte" in der
  Einkaufsliste (`MyProductsSheet`, `CatalogProductCard`).
- **Nutri-Score:** `NutriScoreBadge` in der Einkaufsliste; im Wochenplan fließt der beste
  Nutri-Score in die Nährwert-Trend-Karte ein.
- **Rezept-Nährwerte:** per KI berechnet (`calculateNutritionWithAi` → `/api/ai/nutrition`) oder
  manuell gesetzt (`saveManualNutrition`); Datenmodell `RecipeNutrition.kt`. Zusätzlich werden
  Nährwerte aus verknüpften Bon-Artikeln befüllt.
- **Allergene:** `AllergyProfile` hält die persönlichen Ausschlüsse (`excludeAllergens` im
  `SyncDto`), `AllergyChecker` (`hasAllergens`, `hasAnyAllergen`) prüft Rezepte und Produkte,
  `AllergenWarningBanner` zeigt die Warnung. Wirkt bis in die Wochenplanung (`userAllergies`).

## Bekannte Lücken

### Funktion & UX
**Root Cause zu „Nährwerte je Portion" (aus dem Interview):** `RecipeNutrition.kt` berechnet
`kcalPerPortion` bereits als `totalKcal / NUTRITION_BASELINE_PORTIONS` (feste Basis: 4
Portionen), aber `protein`/`fat`/`carbs` bleiben absolute Werte **für diese 4 Portionen** —
inkonsistent zur kcal-Angabe daneben. `NutritionSection` in `RecipeDetailScreen.kt:534-538`
bekommt zudem keinen `scaleFactor`-Parameter (anders als `IngredientRow`, das mit dem
Portionswähler mitskaliert) — die Nährwertanzeige bleibt bei jeder Portionenzahl gleich.

**Nutri-Score kommt aus zwei unabhängigen Quellen**, beide laut Interview zum Entfernen
vorgesehen:
1. Produktkatalog (`OffProductEntity.nutriScore`, echter OFF-Wert) — Badge in
   `ShoppingListScreen.kt:1470-1471` (Karte) und `:1561-1587` (Detail-Sheet).
2. KI-geschätzter Rezept-Nutri-Score (`RecipeNutrition.nutriScore`, „a"–„e") — Anzeige in
   `RecipeDetailScreen.kt:564-588`, Eingabefeld in `NutritionEditDialog`, sowie aggregiert in
   `DayNutrition.avgNutriScore`/`WeekplanNutrition.weekAvgNutriScore` für die Trend-Karte in
   `WeekplanScreen.kt`. Zusätzlich nutzt `WeekplanConstraintsEntity.minNutriScore` (Default
   `"c"`) den Wert als Filterschwelle bei der Wochenplan-Generierung
   (`WeekplanRepository.kt`/`WeekplanViewModel.kt`) — Entfernung reicht damit in
   [wochenplan](../wochenplan/plan.md) hinein.

### Code-Qualität
Keine `!!`-Zugriffe, keine `items()`-Verstöße in diesem Bereich.

### Tests
Keine. `AllergyChecker` ist sicherheitsrelevant — eine übersehene Allergenwarnung ist der
schlimmste Fehlerfall der ganzen App und zugleich der am einfachsten testbare.

### Sync
**Belegte Lücke, beide Enden bereits gebaut.** Der Server hat Tabelle und Sync-Unterstützung für
`off_products` (`server/app/db.py`, `server/app/sync.py`), und
`app/src/main/kotlin/com/helga/android/data/local/dao/OffProductDao.kt` stellt mit
`dirtyProducts()` (Zeile 32) und `clearDirty()` (Zeile 35) genau die Methoden bereit, die das
Sync-Muster verlangt. **Nichts ruft sie auf:**
`app/src/main/kotlin/com/helga/android/data/sync/SyncEngine.kt` erwähnt `OffProduct` an keiner
Stelle. Der Produktkatalog bleibt damit gerätelokal, obwohl die Verdrahtung nur noch fehlt —
kein Konzeptproblem, sondern ein offener Anschluss.

## Fragen

1. **Wie oft nutzt du den Barcode-Scan im Laden?**
   Antwort: So gut wie nie — Randfunktion bestätigt, kein Ausbau.
2. **Soll der Produktkatalog geräteübergreifend syncen?**
   Antwort (nach Rückfrage zur Konkretisierung): Ja — einmal gescannt/gesucht soll auf allen
   Geräten verfügbar sein.
3. **Welcher Nährwert-Weg (KI, manuell, Bon-Verknüpfung) ist am verlässlichsten?**
   Antwort: KI läuft meistens mit; eine einmalige KI-Berechnung pro Rezept reicht für einen
   überschlägigen Wert — keine laufende Neuberechnung nötig.
4. **Sind Allergene echter Bedarf oder Vorsichtsmaßnahme?**
   Antwort: Vorsorglich, kein akuter Bedarf.
5. **Soll die Allergenwarnung blockierend werden?**
   Antwort: Nein, bleibt reiner Hinweis.
6. **Fehlt der Nutri-Score im Rezeptdetail?**
   Antwort: Gegenteil — Nutri-Score soll komplett aus der App verschwinden. Echte Nährwerte
   (kcal/Protein/Fett/KH) sind aussagekräftiger.
7. **Nährwerte je Portion, skalierend?**
   Antwort: Ja, je Portion mit Skalierung gewünscht — deckt sich mit dem Root Cause oben
   (aktuell inkonsistent: kcal ist pro Portion, Makros sind es nicht, nichts skaliert live).
8. **Tages-/Wochenziel für Kalorien sinnvoll?**
   Antwort: Wäre nett, kein Muss — niedrige Priorität, kein Backlog-Item jetzt.

## Ziele

- Nutri-Score vollständig aus der App entfernen — beide Quellen (Produktkatalog und
  KI-geschätzter Rezeptwert inkl. Wochenplan-Trend und Generierungs-Filter).
- Echte Nährwerte (kcal, Protein, Fett, Kohlenhydrate) im Rezept konsistent pro Portion zeigen
  und mit dem Portionswähler mitskalieren lassen.
- Produktkatalog geräteübergreifend synchronisieren, wie der Rest der App.
- Barcode-Scan unverändert lassen — kein Ausbau, da kaum genutzt.
- Allergenwarnung bleibt reiner Hinweis, nicht blockierend.
- Kalorienziel als spätere Idee vormerken, aktuell keine Priorität.

## Backlog

Aufwand: S (< 1 h) · M (halber Tag) · L (mehrere Tage)

- [ ] **A1** — Unit-Tests für `AllergyChecker` (Treffer, Teilwort, Groß-/Kleinschreibung, leeres Profil) · S · Impact hoch
- [ ] **A2** — `OffProductEntity` an `SyncEngine` anbinden; DAO und Serverseite sind fertig · M · Impact mittel — bestätigter Bedarf aus dem Interview
- [ ] **A3** — Nutri-Score vollständig entfernen: Badges in der Einkaufsliste
  (`ShoppingListScreen.kt:1470-1471`/`:1561-1587`), Anzeige + Eingabefeld in
  `RecipeDetailScreen.kt` (`NutritionSection`/`NutritionEditDialog`), Wochenplan-Trendkarte
  (`DayNutrition.avgNutriScore`/`WeekplanNutrition.weekAvgNutriScore`) sowie den
  Generierungs-Filter `WeekplanConstraintsEntity.minNutriScore` in
  [wochenplan](../wochenplan/plan.md). Room-Spalten bleiben bestehen (nicht destruktiv), werden
  nur nicht mehr befüllt/angezeigt · L · Impact hoch
- [ ] **A4** — Nährwerte korrekt pro Portion berechnen und mit dem Portionswähler skalieren:
  `protein`/`fat`/`carbs` in `RecipeNutrition` sind aktuell fix für die 4er-Baseline
  (`NUTRITION_BASELINE_PORTIONS`), `NutritionSection` bekommt keinen `scaleFactor` · M · Impact
  hoch

_Weitere Aufgaben nach dem Interview._

## Entscheidungen

| Datum | Entscheidung | Begründung |
|-------|--------------|------------|
| 2026-08-30 | Nutri-Score wird vollständig entfernt (Produktkatalog + Rezept/Wochenplan) | Echte Nährwerte sind aussagekräftiger, Nutzer hat keinen Bedarf am Score |
| 2026-08-30 | Produktkatalog wird geräteübergreifend gesynct | Passt zum Offline-First-Sync-Prinzip der App, Server/DAO sind bereits fertig |
| 2026-08-30 | Allergenwarnung bleibt nicht-blockierender Hinweis | Vorsorgliche Nutzung ohne akuten Bedarf, Blockieren wäre zu streng |
| 2026-08-30 | Barcode-Scan bleibt unverändert | Kaum genutzt, kein Ausbau gerechtfertigt |
| 2026-08-30 | Kalorienziel wird nicht umgesetzt | Nice-to-have ohne aktiven Bedarf |
