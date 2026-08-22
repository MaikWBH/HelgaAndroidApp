# Feature: Nährwerte & Allergene

> **Status:** Interview offen · **Aufgaben:** 0/0 · **Stand:** 2026-08-22 · **Priorität:** ⭐⭐

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
Offen bis zum Interview.

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

## Offene Fragen

1. Wie oft nutzt du den Barcode-Scan im Laden — lohnt sich Ausbau, oder ist es eine Randfunktion?
2. Der Produktkatalog synct nicht (siehe oben). Soll er das, oder ist er bewusst gerätelokal?
3. Nährwerte kommen aus drei Quellen: KI, manuelle Eingabe, Bon-Verknüpfung. Welcher Weg ist in
   der Praxis der verlässlichste?
4. Sind Allergene bei dir echter Bedarf oder Vorsichtsmaßnahme? Davon hängt ab, wie streng die
   Warnung sein muss.
5. Soll die Allergenwarnung blockierend sein (Rezept lässt sich nicht in den Plan ziehen) oder
   weiterhin nur ein Hinweis?
6. Nutri-Score erscheint in Einkaufsliste und Wochenplan. Fehlt er im Rezeptdetail?
7. Sollen Nährwerte je Portion oder je Rezept angezeigt werden — und passt sich das an die
   Portionsskalierung an?
8. Wäre ein Tages- oder Wochenziel für Kalorien sinnvoll, oder ist das über das Ziel hinaus?

## Ziele

_Nach dem Interview zu füllen._

## Backlog

Aufwand: S (< 1 h) · M (halber Tag) · L (mehrere Tage)

- [ ] **A1** — Unit-Tests für `AllergyChecker` (Treffer, Teilwort, Groß-/Kleinschreibung, leeres Profil) · S · Impact hoch
- [ ] **A2** — `OffProductEntity` an `SyncEngine` anbinden; DAO und Serverseite sind fertig · M · Impact mittel

_Weitere Aufgaben nach dem Interview._

## Entscheidungen

| Datum | Entscheidung | Begründung |
|-------|--------------|------------|
