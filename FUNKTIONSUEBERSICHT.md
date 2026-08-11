# Helga – Funktionsübersicht

*Vollständige Liste der Funktionen, gegliedert nach Reitern. Jede Zeile nennt,
wo die Funktion zu finden ist und womit sie bedient wird.*

**Stand:** 11.08.2026

---

## Inhalt

- [So liest du diese Liste](#so-liest-du-diese-liste)
- [Legende: Bedienelemente](#legende-bedienelemente)
- [Legende: Status](#legende-status)
- [Legende: Notizen](#legende-notizen)
- [Navigation auf einen Blick](#navigation-auf-einen-blick)
- [🛒 EK – Einkaufen](#-ek--einkaufen)
- [🍽 RZ – Rezepte](#-rz--rezepte)
- [📅 WP – Wochenplan](#-wp--wochenplan)
- [🧾 KB – Kassenzettel & Preise](#-kb--kassenzettel--preise)
- [🏬 MK – Märkte & Gänge](#-mk--märkte--gänge)
- [⚙️ ST – Einstellungen](#️-st--einstellungen)
- [🔄 AL – Übergreifend](#-al--übergreifend)
- [Nicht erreichbare Funktionen](#nicht-erreichbare-funktionen)

---

## So liest du diese Liste

Jede Funktion steht in genau einer Zeile mit fünf Angaben:

| Spalte | Bedeutung |
|---|---|
| **ID** | Feste Kennung, z. B. `EK-A-03`. Bereich – Untergruppe – laufende Nummer. Ändert sich nie, auch wenn Zeilen umsortiert werden. Zum Verweisen aus Notizen, Aufgaben und Commits. |
| **Funktion** | Was die Funktion für den Nutzer tut. |
| **Wo** | Pfad zum Einstiegspunkt, mit `›` getrennt: `Reiter › Bereich › Element`. |
| **Bedienelement** | Womit die Funktion ausgelöst wird – Begriffe aus der Legende unten. |
| **Status** | Ob die Funktion nutzbar ist. |
| **Code** | Zuständige Datei, bei größeren Dateien mit dem Namen der Funktion dahinter. |
| **Notiz** | Frei für Bugs, Verbesserungswünsche und Ideen zu genau dieser Funktion. Leer = nichts offen. |

**Bereichskürzel:** `EK` Einkaufen · `RZ` Rezepte · `WP` Wochenplan ·
`KB` Kassenzettel & Preise · `MK` Märkte & Gänge · `ST` Einstellungen · `AL` Übergreifend

---

## Legende: Bedienelemente

Beim Erweitern der Liste bitte immer diese Begriffe verwenden.

| Begriff | Was gemeint ist |
|---|---|
| **Button** | Flächiger Knopf mit Beschriftung |
| **Icon-Button** | Symbol ohne Text, meist in der Titelzeile |
| **FAB** | Schwebender Knopf unten rechts; als *Speed-Dial*, wenn er sich zu mehreren Einträgen aufklappt |
| **Dropdown-Menü** | Aufklappende Auswahl an Ort und Stelle |
| **Überlauf-Menü** | Menü hinter dem ⋮-Symbol |
| **Menüeintrag** | Einzelne Zeile innerhalb eines Menüs |
| **Dialog** | Kleines Fenster über dem Inhalt, muss bestätigt oder geschlossen werden |
| **Bottom Sheet** | Von unten einfahrende Fläche |
| **Chip** | Kleines rundes Element; *Filter-Chip*, wenn es einen Filter an-/ausschaltet |
| **Checkbox** | Kästchen zum Abhaken |
| **Schalter** | Zweizustands-Umschalter (an/aus) |
| **Regler** | Stufenlose Werteinstellung |
| **Stepper** | Wert per − / + verändern |
| **Textfeld** | Freie Eingabe |
| **Karte** | Antippbarer Block mit Inhalt |
| **Banner** | Situativer Hinweisstreifen mit Aktion |
| **Statussymbol** | Zeigt nur einen Zustand an |
| **Zeile antippen** | Tipp auf einen Listeneintrag |
| **Wischgeste** | Eintrag seitwärts wischen |
| **Zum Aktualisieren ziehen** | Inhalt nach unten ziehen |
| **automatisch** | Kein Bedienelement – passiert von selbst |

---

## Legende: Status

| Zeichen | Bedeutung |
|---|---|
| ✅ | Nutzbar |
| ⚠️ | Teilweise nutzbar oder eingeschränkt |
| ⛔ | Im Code vorhanden, aber nicht erreichbar |
| 💡 | Geplant, noch nicht gebaut |

---

## Legende: Notizen

Kurzzeichen am Anfang der Notiz, damit die Spalte filterbar bleibt.

| Zeichen | Bedeutung |
|---|---|
| 🐛 | Bug – funktioniert nicht wie beschrieben |
| ✨ | Verbesserungswunsch |
| ❓ | Offene Frage, noch zu klären |

Beispiel: `🐛 Gang wird nach dem Sync zurückgesetzt`

---

## Navigation auf einen Blick

```
Helga
├── 🛒 Einkaufen        ← Startbildschirm
│   ├── Kassenzettel    (Überlauf-Menü)
│   └── Meine Produkte  (Überlauf-Menü)
├── 🍽 Rezepte
│   ├── Rezept-Detail → Kochansicht · Bearbeiten · Remix
│   ├── Neu: manuell · KI · URL-Import
│   └── Einstellungen   (Icon-Button)
│       └── Märkte & Gänge
└── 📅 Wochenplan
    └── Rezept-Auswahl
```

---

# 🛒 EK – Einkaufen

Startbildschirm nach dem Öffnen der App.

## EK-L · Listen

| ID | Funktion | Wo | Bedienelement | Status | Code | Notiz |
|---|---|---|---|---|---|---|
| EK-L-01 | Zwischen Einkaufslisten wechseln | Einkaufen › Titelzeile | Dropdown-Menü | ✅ | `ShoppingListScreen.kt` |  |
| EK-L-02 | Neue Liste anlegen | Einkaufen › Titelzeile › Dropdown | Menüeintrag → Dialog | ✅ | `ShoppingListScreen.kt` · `NewListDialog` |  |
| EK-L-03 | Standard-Liste festlegen | Einstellungen › Einkaufen | Dropdown-Menü | ✅ | `SettingsScreen.kt` · `SettingsDefaultListDropdown` |  |
| EK-L-04 | Liste löschen | Einstellungen › Einkaufslisten verwalten | Icon-Button → Dialog | ✅ | `SettingsScreen.kt` |  |
| EK-L-05 | Liste aktualisieren | Einkaufen › Liste | Zum Aktualisieren ziehen | ✅ | `ShoppingListViewModel.kt` · `refresh` |  |

## EK-M · Markt

| ID | Funktion | Wo | Bedienelement | Status | Code | Notiz |
|---|---|---|---|---|---|---|
| EK-M-01 | Aktiven Markt wählen | Einkaufen › Leiste über der Liste | Dropdown-Menü | ✅ | `ShoppingListScreen.kt` |  |
| EK-M-02 | Ohne Markt arbeiten | Einkaufen › Markt-Dropdown | Menüeintrag „Kein Markt" | ✅ | `ShoppingListScreen.kt` |  |
| EK-M-03 | Gang-Reihenfolge des Marktes sortiert die Liste | Einkaufen › Liste | automatisch | ✅ | `ShoppingListViewModel.kt` · `aisleSortMap` |  |

## EK-E · Artikel erfassen

| ID | Funktion | Wo | Bedienelement | Status | Code | Notiz |
|---|---|---|---|---|---|---|
| EK-E-01 | Artikel per Freitext hinzufügen | Einkaufen › Eingabezeile unten | Textfeld + Icon-Button | ✅ | `ShoppingListScreen.kt` · `QuickAddBar` |  |
| EK-E-02 | Menge, Einheit und Name automatisch trennen | Einkaufen › Eingabezeile | automatisch | ✅ | `IngredientLineParser.kt` |  |
| EK-E-03 | Brüche (`1/2`) und Bereiche (`2-3`) verstehen | Einkaufen › Eingabezeile | automatisch | ✅ | `IngredientLineParser.kt` |  |
| EK-E-04 | Klammer am Zeilenende als Notiz übernehmen | Einkaufen › Eingabezeile | automatisch | ✅ | `IngredientLineParser.kt` |  |
| EK-E-05 | Autovervollständigung aus früheren Einkäufen | Einkaufen › Eingabezeile | Vorschlags-Chips | ✅ | `ShoppingListScreen.kt` · `QuickAddBar` |  |
| EK-E-06 | Artikel per Emoji-Schnellbutton anlegen | Einkaufen › über der Eingabezeile | Chip-Leiste | ✅ | `ShoppingListScreen.kt` · `EmojiQuickButton` |  |
| EK-E-07 | Barcode scannen | Einkaufen › Eingabezeile | Icon-Button → Kamera | ✅ | `BarcodeScanner.kt` |  |
| EK-E-08 | Gescanntes Produkt prüfen und übernehmen | nach dem Scan | Dialog | ✅ | `ShoppingListScreen.kt` · `ScannedProductDialog` |  |
| EK-E-09 | Vorratsstapel öffnen | Einkaufen › Überlauf-Menü | Menüeintrag → Bottom Sheet | ✅ | `ShoppingListScreen.kt` · `StaplesSheet` |  |
| EK-E-10 | Alle Vorratsartikel auf einmal hinzufügen | Einkaufen › Überlauf-Menü | Menüeintrag | ✅ | `ShoppingListViewModel.kt` · `addStaplesToList` |  |
| EK-E-11 | Vorratsartikel anlegen und löschen | Vorratsstapel-Sheet | Textfeld · Icon-Button | ✅ | `ShoppingListScreen.kt` · `StaplesSheet` |  |
| EK-E-12 | Produktkatalog „Meine Produkte" öffnen | Einkaufen › Überlauf-Menü | Menüeintrag → Bottom Sheet | ✅ | `ShoppingListScreen.kt` · `MyProductsSheet` |  |
| EK-E-13 | Produkt aus dem Katalog übernehmen | Meine-Produkte-Sheet | Karte | ✅ | `ShoppingListScreen.kt` · `CatalogProductCard` |  |

## EK-A · Liste und Artikel

| ID | Funktion | Wo | Bedienelement | Status | Code | Notiz |
|---|---|---|---|---|---|---|
| EK-A-01 | Artikel nach Gang gruppieren | Einkaufen › Liste | automatisch | ✅ | `ShoppingListScreen.kt` · `AisleHeader` |  |
| EK-A-02 | Abschnitt „Ohne Gang" für Nicht-Zugeordnetes | Einkaufen › Liste | automatisch | ✅ | `ShoppingListScreen.kt` |  |
| EK-A-03 | Gang pro Artikel zuweisen | Einkaufen › Artikel antippen | Dialog mit Auswahlliste | ✅ | `ShoppingListScreen.kt` · `AislePickerDialog` |  |
| EK-A-04 | Gangzuordnung merken und künftig anwenden | Einkaufen › Liste | automatisch | ✅ | `StoreRepository.kt` |  |
| EK-A-05 | Artikel abhaken | Einkaufen › Artikelzeile | Checkbox | ✅ | `ShoppingListScreen.kt` · `ShoppingItemRow` |  |
| EK-A-06 | Artikel löschen | Einkaufen › Artikelzeile | Wischgeste | ✅ | `ShoppingListScreen.kt` · `SwipeableShoppingItem` |  |
| EK-A-07 | Menge, Einheit und Name ändern | Einkaufen › Artikel antippen | Dialog | ✅ | `ShoppingListScreen.kt` · `EditItemDialog` |  |
| EK-A-08 | Herkunft anzeigen (Rezept · Vorrat · manuell) | Einkaufen › Artikelzeile | Badge | ✅ | `ShoppingListScreen.kt` · `SourceBadge` |  |
| EK-A-09 | Zusammengeführten Artikel aufschlüsseln | Einkaufen › Badge antippen | Zeile antippen | ✅ | `ShoppingListScreen.kt` · `OriginBreakdown` |  |
| EK-A-10 | Gleiche Artikel zusammenführen statt doppelt anlegen | Einkaufen › Liste | automatisch | ✅ | `ShoppingRepository.kt` |  |
| EK-A-11 | Einheiten beim Zusammenführen umrechnen (g/kg, ml/l/cl) | Einkaufen › Liste | automatisch | ✅ | `ShoppingUnitConverter.kt` |  |
| EK-A-12 | Warnung bei enthaltenem Allergen | Einkaufen › über der Liste | Banner | ⛔ | `AllergenWarningBanner.kt` |  |

> **EK-A-12** funktioniert technisch, bleibt aber unsichtbar: es gibt keine Oberfläche,
> um Allergien einzutragen (siehe [Nicht erreichbare Funktionen](#nicht-erreichbare-funktionen)).

## EK-C · Abhaken und Abschluss

| ID | Funktion | Wo | Bedienelement | Status | Code | Notiz |
|---|---|---|---|---|---|---|
| EK-C-01 | Abhak-Modus wählen (Behalten / Verschieben) | Einstellungen › Einkaufen | Segmentierte Auswahl | ✅ | `SettingsScreen.kt` |  |
| EK-C-02 | Abgehaktes in eigenen Abschnitt verschieben | Einkaufen › Liste | automatisch (Modus „Verschieben") | ✅ | `ShoppingListScreen.kt` |  |
| EK-C-03 | Einkauf beenden | Einkaufen › Titelzeile | Icon-Button (Haken) | ✅ | `ShoppingListViewModel.kt` · `deleteCheckedItems` |  |
| EK-C-04 | Erledigte Artikel löschen | Einkaufen › Überlauf-Menü | Menüeintrag | ✅ | `ShoppingListViewModel.kt` · `deleteCheckedItems` |  |

## EK-K · Kosten

| ID | Funktion | Wo | Bedienelement | Status | Code | Notiz |
|---|---|---|---|---|---|---|
| EK-K-01 | Kosten der aktuellen Liste schätzen | Einkaufen › Karte über der Liste | automatisch | ✅ | `ShoppingListScreen.kt` · `CostEstimateCard` |  |
| EK-K-02 | Warnen, wenn das Monatsbudget belastet wird | Einkaufen › Kostenkarte | automatisch | ✅ | `ShoppingListScreen.kt` · `CostEstimateCard` |  |

## EK-B · Hinweisbanner

| ID | Funktion | Wo | Bedienelement | Status | Code | Notiz |
|---|---|---|---|---|---|---|
| EK-B-01 | „Wochenplan bereit" – Zutaten übernehmen | Einkaufen › oben | Banner | ✅ | `ShoppingListScreen.kt` |  |
| EK-B-02 | „Noch kein Wochenplan" – zur Planung springen | Einkaufen › oben | Banner | ✅ | `ShoppingListScreen.kt` |  |
| EK-B-03 | „Kassenzettel scannen" – nach dem Einkauf | Einkaufen › oben | Banner + Icon-Button zum Schließen | ✅ | `ShoppingListViewModel.kt` · `showScanReminder` |  |
| EK-B-04 | Ab welchem Abhak-Anteil EK-B-03 erscheint | Einstellungen › Einkaufen | Regler | ✅ | `SettingsScreen.kt` |  |

---

# 🍽 RZ – Rezepte

## RZ-L · Rezeptliste

| ID | Funktion | Wo | Bedienelement | Status | Code | Notiz |
|---|---|---|---|---|---|---|
| RZ-L-01 | Rezepte per Freitext durchsuchen (Name, Beschreibung) | Rezepte › Suchfeld | Textfeld | ✅ | `RecipeListViewModel.kt` · `searchQuery` |  |
| RZ-L-02 | Alle Rezepte anzeigen | Rezepte › Filterleiste | Filter-Chip | ✅ | `RecipeListScreen.kt` · `FilterBar` |  |
| RZ-L-03 | Nur Favoriten anzeigen | Rezepte › Filterleiste | Filter-Chip | ✅ | `RecipeListScreen.kt` · `FilterBar` |  |
| RZ-L-04 | Nach Tags filtern (Mehrfachauswahl) | Rezepte › Filterleiste | Filter-Chip → Dialog | ✅ | `RecipeListScreen.kt` · `TagFilterDialog` |  |
| RZ-L-05 | Alle Tag-Filter abwählen | Tag-Dialog | Button | ✅ | `RecipeListScreen.kt` · `TagFilterDialog` |  |
| RZ-L-06 | Sortieren (Name · Bewertung · Zuletzt geändert) | Rezepte › Filterleiste | Icon-Button → Dropdown-Menü | ✅ | `RecipeListScreen.kt` · `SortButton` |  |
| RZ-L-07 | Rezept des heutigen Wochenplan-Tags anzeigen | Rezepte › oben | Karte („Heute kochen") | ✅ | `RecipeListViewModel.kt` · `todayRecipe` |  |
| RZ-L-08 | Alle unklassifizierten Rezepte per KI klassifizieren | Rezepte › Überlauf-Menü (mit Zähler-Badge) | Menüeintrag → Dialog | ✅ | `RecipeListScreen.kt` · `BulkClassifyDialog` |  |
| RZ-L-09 | Sync-Zustand anzeigen und Sync auslösen | Rezepte › Titelzeile | Icon-Button mit Statussymbol | ✅ | `SyncStatusIcon.kt` |  |
| RZ-L-10 | Liste aktualisieren | Rezepte › Liste | Zum Aktualisieren ziehen | ✅ | `RecipeListViewModel.kt` · `refresh` |  |
| RZ-L-11 | Einstellungen öffnen | Rezepte › Titelzeile | Icon-Button (Zahnrad) | ✅ | `RecipeListScreen.kt` |  |

## RZ-N · Rezept anlegen

| ID | Funktion | Wo | Bedienelement | Status | Code | Notiz |
|---|---|---|---|---|---|---|
| RZ-N-01 | Erstellen-Menü aufklappen | Rezepte › unten rechts | FAB (Speed-Dial) | ✅ | `CreateFab.kt` |  |
| RZ-N-02 | Rezept manuell anlegen | Rezepte › FAB | Menüeintrag → Formular | ✅ | `RecipeFormScreen.kt` |  |
| RZ-N-03 | Name und Beschreibung erfassen | Rezept-Formular | Textfeld | ✅ | `RecipeFormScreen.kt` |  |
| RZ-N-04 | Bild aus der Galerie wählen | Rezept-Formular | Button → Galerie | ✅ | `RecipeFormScreen.kt` |  |
| RZ-N-05 | Zeiten und Portionen erfassen | Rezept-Formular › Zeiten & Metadaten | Textfeld | ✅ | `RecipeFormScreen.kt` |  |
| RZ-N-06 | Protein-Typ, Saison und Mahlzeit setzen | Rezept-Formular | Dropdown-Menü | ✅ | `RecipeFormScreen.kt` |  |
| RZ-N-07 | Tags hinzufügen | Rezept-Formular › Tags | Textfeld + Chips | ✅ | `RecipeFormScreen.kt` |  |
| RZ-N-08 | Zutaten zeilenweise erfassen (Menge · Einheit · Zutat) | Rezept-Formular | Button „Zutat hinzufügen" | ✅ | `RecipeFormScreen.kt` |  |
| RZ-N-09 | Zubereitungsschritte erfassen | Rezept-Formular | Button „Schritt hinzufügen" | ✅ | `RecipeFormScreen.kt` |  |
| RZ-N-10 | Quell-URL hinterlegen | Rezept-Formular | Textfeld | ✅ | `RecipeFormScreen.kt` |  |
| RZ-N-11 | Rezept von einer Webseite importieren | Rezepte › FAB | Menüeintrag → Textfeld + Button | ✅ | `UrlImportScreen.kt` |  |
| RZ-N-12 | Import vorab prüfen (Anzahl Zutaten und Schritte) | URL-Import | Karte | ✅ | `UrlImportScreen.kt` |  |
| RZ-N-13 | Rezept-Link aus einer anderen App teilen | Android-Teilen-Menü | System-Teilen-Ziel | ✅ | `MainActivity.kt` · `resolveSharedUrl` |  |
| RZ-N-14 | Rezept bearbeiten | Rezept-Detail › Überlauf-Menü | Menüeintrag → Formular | ✅ | `RecipeFormScreen.kt` |  |
| RZ-N-15 | Rezept löschen | Rezept-Detail › Überlauf-Menü | Menüeintrag → Dialog | ✅ | `RecipeDetailViewModel.kt` · `deleteRecipe` |  |

## RZ-K · KI-Rezepte

| ID | Funktion | Wo | Bedienelement | Status | Code | Notiz |
|---|---|---|---|---|---|---|
| RZ-K-01 | Rezept per KI erstellen | Rezepte › FAB | Menüeintrag | ✅ | `AiGenerateScreen.kt` |  |
| RZ-K-02 | Wunsch als Freitext angeben | KI-Erstellung | Textfeld | ✅ | `AiGenerateScreen.kt` |  |
| RZ-K-03 | Ernährungsweise vorgeben (Vegan · Vegetarisch · Fleisch · Fisch) | KI-Erstellung › Rahmenbedingungen | Chip-Auswahl | ✅ | `AiGenerateScreen.kt` · `DIET_OPTIONS` |  |
| RZ-K-04 | Kochzeit vorgeben (< 30 · 30–60 · > 60 Min) | KI-Erstellung › Rahmenbedingungen | Chip-Auswahl | ✅ | `AiGenerateScreen.kt` · `COOKTIME_OPTIONS` |  |
| RZ-K-05 | Schwierigkeit vorgeben (Kindgerecht … Anspruchsvoll) | KI-Erstellung › Rahmenbedingungen | Chip-Auswahl | ✅ | `AiGenerateScreen.kt` · `EFFORT_OPTIONS` |  |
| RZ-K-06 | Küche bzw. Stil vorgeben | KI-Erstellung › Rahmenbedingungen | Textfeld | ✅ | `AiGenerateScreen.kt` |  |
| RZ-K-07 | Besonderheiten angeben („Low Carb", „günstig") | KI-Erstellung › Rahmenbedingungen | Textfeld | ✅ | `AiGenerateScreen.kt` |  |
| RZ-K-08 | Rezept beim Entstehen mitlesen | KI-Erstellung | automatisch (Streaming) | ✅ | `SseClient.kt` |  |
| RZ-K-09 | Ergebnis zur Bibliothek hinzufügen | KI-Erstellung | Button | ✅ | `AiGenerateViewModel.kt` |  |
| RZ-K-10 | Mit Änderungswunsch neu generieren | KI-Erstellung | Button → Dialog | ✅ | `AiGenerateScreen.kt` |  |
| RZ-K-11 | Komplett anderes Rezept anfordern | KI-Erstellung | Button | ✅ | `AiGenerateScreen.kt` |  |
| RZ-K-12 | Vorschlag verwerfen | KI-Erstellung | Button | ✅ | `AiGenerateScreen.kt` |  |
| RZ-K-13 | Bestehendes Rezept remixen („Mache es vegan") | Rezept-Detail › FAB | Menüeintrag | ✅ | `AiRemixScreen.kt` |  |
| RZ-K-14 | Remix als neues Rezept speichern, Original behalten | KI-Remix | Button | ✅ | `AiRemixViewModel.kt` |  |
| RZ-K-15 | Einzelnes Rezept per KI klassifizieren | Rezept-Detail › Überlauf-Menü | Menüeintrag | ✅ | `RecipeDetailViewModel.kt` · `classify` |  |

## RZ-D · Rezept-Detail

| ID | Funktion | Wo | Bedienelement | Status | Code | Notiz |
|---|---|---|---|---|---|---|
| RZ-D-01 | Titelbild anzeigen | Rezept-Detail › oben | automatisch | ✅ | `RecipeDetailScreen.kt` · `HeroImage` |  |
| RZ-D-02 | Mit 1–5 Sternen bewerten | Rezept-Detail | Zeile antippen (Sterne) | ✅ | `RecipeDetailScreen.kt` · `RatingSection` |  |
| RZ-D-03 | Als Favorit markieren | Rezept-Detail › Titelzeile | Icon-Button (Herz) | ✅ | `RecipeDetailViewModel.kt` · `toggleFavorite` |  |
| RZ-D-04 | Metadaten anzeigen (Portionen, Zeiten, Küche, Aufwand) | Rezept-Detail | automatisch | ✅ | `RecipeDetailScreen.kt` · `MetadataSection` |  |
| RZ-D-05 | Tags anzeigen | Rezept-Detail | automatisch | ✅ | `RecipeDetailScreen.kt` · `TagsSection` |  |
| RZ-D-06 | Portionen ändern, Zutatenmengen rechnen mit | Rezept-Detail › über den Zutaten | Stepper | ✅ | `RecipeDetailScreen.kt` · `ServingsStepper` |  |
| RZ-D-07 | Zutatenliste anzeigen | Rezept-Detail | automatisch | ✅ | `RecipeDetailScreen.kt` · `IngredientRow` |  |
| RZ-D-08 | Zubereitungsschritte anzeigen | Rezept-Detail | automatisch | ✅ | `RecipeDetailScreen.kt` · `InstructionRow` |  |
| RZ-D-09 | Nährwerte per KI berechnen | Rezept-Detail › Nährwerte | Button | ✅ | `RecipeDetailViewModel.kt` · `calculateNutritionWithAi` |  |
| RZ-D-10 | Nährwerte manuell eintragen | Rezept-Detail › Nährwerte | Button → Dialog | ✅ | `RecipeDetailScreen.kt` · `NutritionEditDialog` |  |
| RZ-D-11 | Quelle der Nährwerte anzeigen (KI / manuell) | Rezept-Detail › Nährwerte | automatisch | ✅ | `RecipeDetailScreen.kt` · `NutritionSection` |  |
| RZ-D-12 | Eigene Notiz zum Rezept speichern | Rezept-Detail › Meine Notizen | Textfeld + Button | ✅ | `RecipeDetailScreen.kt` · `PersonalNotesSection` |  |
| RZ-D-13 | Zutaten zur Standard-Einkaufsliste hinzufügen | Rezept-Detail › Überlauf-Menü | Menüeintrag | ✅ | `RecipeDetailViewModel.kt` · `addToDefaultShoppingList` |  |
| RZ-D-14 | Zutaten zu einer anderen Liste hinzufügen | Rezept-Detail › Überlauf-Menü | Menüeintrag → Dialog | ✅ | `RecipeDetailScreen.kt` · `ShoppingListSelectDialog` |  |
| RZ-D-15 | Rezept einem Wochenplan-Tag zuweisen | Rezept-Detail › Überlauf-Menü | Menüeintrag → Dialog | ✅ | `RecipeDetailScreen.kt` · `WeekplanDayPickerDialog` |  |
| RZ-D-16 | Im Zuweisungs-Dialog die Woche wechseln | Wochentag-Dialog | Icon-Button (‹ ›) | ✅ | `RecipeDetailViewModel.kt` · `nextWeek` / `prevWeek` |  |
| RZ-D-17 | Rezept teilen | Rezept-Detail › Überlauf-Menü | Menüeintrag | ✅ | `RecipeDetailViewModel.kt` · `shareRecipe` |  |
| RZ-D-18 | Kochansicht öffnen | Rezept-Detail › Titelzeile | Icon-Button (Buch) | ✅ | `RecipeCookScreen.kt` |  |

## RZ-C · Kochansicht

| ID | Funktion | Wo | Bedienelement | Status | Code | Notiz |
|---|---|---|---|---|---|---|
| RZ-C-01 | Zwischen Listen- und Fokusansicht wechseln | Kochansicht › Titelzeile | Icon-Button | ✅ | `RecipeCookScreen.kt` · `CookFocusView` |  |
| RZ-C-02 | In der Fokusansicht durch Schritte blättern | Kochansicht (Fokus) | Wischgeste | ✅ | `RecipeCookScreen.kt` · `CookFocusView` |  |
| RZ-C-03 | Bildschirm bleibt an | Kochansicht | automatisch | ✅ | `RecipeCookScreen.kt` · `keepScreenOn` |  |
| RZ-C-04 | Zutaten beim Kochen abhaken | Kochansicht › Zutaten | Checkbox | ✅ | `RecipeCookScreen.kt` · `IngredientCheckRow` |  |
| RZ-C-05 | Schritte abhaken | Kochansicht › Zubereitung | Checkbox | ✅ | `RecipeCookViewModel.kt` · `toggleStep` |  |
| RZ-C-06 | Portionen anpassen, Mengen rechnen mit | Kochansicht › Zutaten | Stepper | ✅ | `RecipeCookViewModel.kt` · `setServings` |  |
| RZ-C-07 | Zeitangaben im Text als Timer erkennen | Kochansicht › Schritt | automatisch | ✅ | `RecipeCookScreen.kt` · `extractTimers` |  |
| RZ-C-08 | Timer starten, pausieren, zurücksetzen | Kochansicht › Schritt | Icon-Button → Dialog | ✅ | `RecipeCookScreen.kt` · `TimerDialog` |  |
| RZ-C-09 | Eigene Notiz mitlesen | Kochansicht › oben | automatisch | ✅ | `RecipeCookScreen.kt` |  |
| RZ-C-10 | Kochen bestätigen und in die Historie schreiben | Kochansicht › unten | Button („Fertig!") | ✅ | `RecipeCookViewModel.kt` · `confirmCooked` |  |

---

# 📅 WP – Wochenplan

## WP-W · Woche

| ID | Funktion | Wo | Bedienelement | Status | Code | Notiz |
|---|---|---|---|---|---|---|
| WP-W-01 | Planungszeitraum festlegen (7 / 10 / 14 Tage) | Einstellungen › Einkaufen | Dropdown-Menü | ✅ | `SettingsScreen.kt` · `SettingsPlanDaysDropdown` |  |
| WP-W-02 | Zur vorherigen Woche blättern | Wochenplan › Titelzeile | Icon-Button | ✅ | `WeekplanViewModel.kt` · `prevWeek` |  |
| WP-W-03 | Zur nächsten Woche blättern | Wochenplan › Titelzeile | Icon-Button | ✅ | `WeekplanViewModel.kt` · `nextWeek` |  |
| WP-W-04 | Zur aktuellen Woche springen | Wochenplan › Titelzeile | Icon-Button („Heute") | ✅ | `WeekplanViewModel.kt` · `goToCurrentWeek` |  |
| WP-W-05 | Wochenbilanz anzeigen (🥩 Fleisch · 🐟 Fisch · 🥬 Vegetarisch) | Wochenplan › oben | automatisch | ✅ | `WeekplanScreen.kt` · `weekBalance` |  |

## WP-T · Tag

| ID | Funktion | Wo | Bedienelement | Status | Code | Notiz |
|---|---|---|---|---|---|---|
| WP-T-01 | Rezept einem Tag zuweisen | Wochenplan › Tageskarte | Button → Rezept-Auswahl | ✅ | `WeekplanRecipePickerScreen.kt` |  |
| WP-T-02 | In der Rezept-Auswahl suchen | Rezept-Auswahl | Textfeld | ✅ | `WeekplanRecipePickerViewModel.kt` · `searchQuery` |  |
| WP-T-03 | In der Rezept-Auswahl nach Tag filtern | Rezept-Auswahl | Filter-Chip | ✅ | `WeekplanRecipePickerScreen.kt` · `PickerFilterBar` |  |
| WP-T-04 | In der Rezept-Auswahl sortieren | Rezept-Auswahl | Icon-Button → Dropdown-Menü | ✅ | `WeekplanRecipePickerScreen.kt` · `PickerSortButton` |  |
| WP-T-05 | Rezept vom Tag entfernen | Wochenplan › Rezeptzeile | Icon-Button | ✅ | `WeekplanViewModel.kt` · `removeRecipe` |  |
| WP-T-06 | Vom Tag ins Rezept-Detail springen | Wochenplan › Rezeptzeile | Zeile antippen | ✅ | `WeekplanScreen.kt` · `RecipeItemRow` |  |
| WP-T-07 | Notiz zum Tag erfassen | Wochenplan › Tageskarte | Textfeld | ✅ | `WeekplanViewModel.kt` · `updateNote` |  |
| WP-T-08 | Extrazeile zum Tag hinzufügen | Wochenplan › Tageskarte | Textfeld | ✅ | `WeekplanViewModel.kt` · `addExtra` |  |
| WP-T-09 | Extrazeile entfernen | Wochenplan › Extra-Chip | Chip mit Icon-Button | ✅ | `WeekplanScreen.kt` · `ExtraChip` |  |
| WP-T-10 | Tag als Schnelltag markieren (⚡) | Wochenplan › Tageskarte | Icon-Button | ✅ | `WeekplanViewModel.kt` · `toggleQuickDay` |  |
| WP-T-11 | Tag als Gästetag markieren (👥) | Wochenplan › Tageskarte | Icon-Button | ✅ | `WeekplanViewModel.kt` · `toggleGuestDay` |  |
| WP-T-12 | Rückmeldung zu einem gekochten Rezept geben | Wochenplan › Rezeptzeile | Icon-Button (👍 / 👎) | ✅ | `WeekplanViewModel.kt` · `setFeedback` |  |
| WP-T-13 | Einzelnen Tag neu generieren | Wochenplan › Tageskarte | Menüeintrag | ✅ | `WeekplanViewModel.kt` · `regenerateDay` |  |

## WP-G · Plan erzeugen

| ID | Funktion | Wo | Bedienelement | Status | Code | Notiz |
|---|---|---|---|---|---|---|
| WP-G-01 | Kompletten Plan per KI vorschlagen lassen | Wochenplan › Titelzeile | Button („Plan generieren") | ✅ | `WeekplanViewModel.kt` · `generateWeekplan` |  |
| WP-G-02 | Vorschlag vor dem Übernehmen ansehen | nach dem Generieren | Bottom Sheet | ✅ | `WeekplanScreen.kt` · `ProposalSheet` |  |
| WP-G-03 | Einzelnen Tag im Vorschlag neu würfeln | Vorschlags-Sheet | Icon-Button | ✅ | `WeekplanViewModel.kt` · `regenerateProposalDay` |  |
| WP-G-04 | Vorschlag annehmen | Vorschlags-Sheet | Button | ✅ | `WeekplanViewModel.kt` · `applyProposal` |  |
| WP-G-05 | Vorschlag verwerfen | Vorschlags-Sheet | Button | ✅ | `WeekplanViewModel.kt` · `discardProposal` |  |
| WP-G-06 | Plan der Vorwoche übernehmen | Wochenplan › Mehr-Menü | Menüeintrag | ✅ | `WeekplanViewModel.kt` · `repeatLastWeek` |  |
| WP-G-07 | Aktuelle Woche als Vorlage speichern | Wochenplan › Vorlagen-Sheet | Textfeld + Button | ✅ | `WeekplanViewModel.kt` · `saveCurrentWeekAsTemplate` |  |
| WP-G-08 | Vorlage laden | Wochenplan › Vorlagen-Sheet | Button | ✅ | `WeekplanViewModel.kt` · `applyTemplate` |  |
| WP-G-09 | Vorlage löschen | Wochenplan › Vorlagen-Sheet | Icon-Button | ✅ | `WeekplanViewModel.kt` · `deleteTemplate` |  |

## WP-C · Planungs-Constraints

Alle Einstellungen im selben Bottom Sheet, erreichbar über das Mehr-Menü im Wochenplan.

| ID | Funktion | Wo | Bedienelement | Status | Code | Notiz |
|---|---|---|---|---|---|---|
| WP-C-01 | Max. Fleisch-Tage pro Woche | Constraints-Sheet | Regler | ✅ | `WeekplanScreen.kt` · `ConstraintsEditorSheet` |  |
| WP-C-02 | Max. Fisch-Tage pro Woche | Constraints-Sheet | Regler | ✅ | `WeekplanScreen.kt` · `ConstraintsEditorSheet` |  |
| WP-C-03 | Min. vegetarische Tage pro Woche | Constraints-Sheet | Regler | ✅ | `WeekplanScreen.kt` · `ConstraintsEditorSheet` |  |
| WP-C-04 | Wiederholungssperre in Tagen | Constraints-Sheet | Regler | ✅ | `WeekplanScreen.kt` · `ConstraintsEditorSheet` |  |
| WP-C-05 | Max. Kalorien pro Portion | Constraints-Sheet | Regler | ✅ | `WeekplanScreen.kt` · `ConstraintsEditorSheet` |  |
| WP-C-06 | Mindest-Nutri-Score | Constraints-Sheet | Chip-Auswahl | ✅ | `WeekplanScreen.kt` · `ConstraintsEditorSheet` |  |
| WP-C-07 | Bio bevorzugen | Constraints-Sheet | Schalter | ✅ | `WeekplanScreen.kt` · `ConstraintsEditorSheet` |  |
| WP-C-08 | Allergene ausschließen | Constraints-Sheet | Filter-Chips | ⛔ | `WeekplanScreen.kt` · `ConstraintsEditorSheet` |  |
| WP-C-09 | Constraints speichern | Constraints-Sheet | Button | ✅ | `WeekplanViewModel.kt` · `saveConstraints` |  |

> **WP-C-08** erscheint nur, wenn Allergien hinterlegt sind – wofür die Oberfläche fehlt.

## WP-E · Auf die Einkaufsliste

| ID | Funktion | Wo | Bedienelement | Status | Code | Notiz |
|---|---|---|---|---|---|---|
| WP-E-01 | Zutaten eines Tages übernehmen | Wochenplan › Tageskarte | Menüeintrag → Dialog | ✅ | `WeekplanViewModel.kt` · `exportToShoppingList` |  |
| WP-E-02 | Zutaten der ganzen Woche übernehmen | Wochenplan › Mehr-Menü | Menüeintrag → Dialog | ✅ | `WeekplanViewModel.kt` · `exportWeekToShoppingList` |  |
| WP-E-03 | Ziel-Einkaufsliste wählen | Export-Dialog | Dialog mit Auswahlliste | ✅ | `WeekplanScreen.kt` · `ShoppingListPickerDialog` |  |
| WP-E-04 | Portionen vor dem Export einstellen | Export-Dialog | Stepper | ✅ | `WeekplanScreen.kt` · `ShoppingListPickerDialog` |  |

---

# 🧾 KB – Kassenzettel & Preise

Erreichbar über das Überlauf-Menü im Reiter *Einkaufen*.

## KB-S · Bon scannen

| ID | Funktion | Wo | Bedienelement | Status | Code | Notiz |
|---|---|---|---|---|---|---|
| KB-S-01 | Bon fotografieren | Einkäufe › Scannen | Button → Kamera | ✅ | `ReceiptScanScreen.kt` · `CaptureButtons` |  |
| KB-S-02 | Bon aus der Galerie wählen | Einkäufe › Scannen | Button → Galerie | ✅ | `ReceiptScanScreen.kt` · `CaptureButtons` |  |
| KB-S-03 | Text auf dem Gerät erkennen | nach der Aufnahme | automatisch | ✅ | `ReceiptScanner.kt` |  |
| KB-S-04 | Bon zusätzlich per KI auswerten | nach der Aufnahme | automatisch | ✅ | `ReceiptScanViewModel.kt` · `scanImage` |  |
| KB-S-05 | Erkennungsquelle anzeigen (Gerät / KI) | Prüfansicht | automatisch | ✅ | `ReceiptScanScreen.kt` · `ScanSourceInfo` |  |
| KB-S-06 | Markt zuordnen oder benennen | Prüfansicht | Dropdown-Menü + Textfeld | ✅ | `ReceiptScanViewModel.kt` · `selectStore` |  |
| KB-S-07 | Erkannte Position bearbeiten | Prüfansicht › Position | Zeile antippen → Dialog | ✅ | `ReceiptScanScreen.kt` · `EditItemDialog` |  |
| KB-S-08 | Position entfernen | Prüfansicht › Position | Icon-Button | ✅ | `ReceiptScanViewModel.kt` · `removeItem` |  |
| KB-S-09 | Gesamtbetrag korrigieren | Prüfansicht | Textfeld | ✅ | `ReceiptScanViewModel.kt` · `updateTotal` |  |
| KB-S-10 | Bon speichern | Prüfansicht | Button | ✅ | `ReceiptScanViewModel.kt` · `save` |  |
| KB-S-11 | Bon mit der Einkaufsliste abgleichen (KI) | Einstellungen › Einkaufen | Schalter | ✅ | `SettingsViewModel.kt` · `setReceiptReconciliationEnabled` |  |

## KB-L · Einkäufe

| ID | Funktion | Wo | Bedienelement | Status | Code | Notiz |
|---|---|---|---|---|---|---|
| KB-L-01 | Alle gescannten Bons auflisten | Einkaufen › Überlauf-Menü › Kassenzettel | Menüeintrag | ✅ | `ReceiptListScreen.kt` |  |
| KB-L-02 | Bon-Detail mit allen Positionen öffnen | Einkäufe › Bon | Zeile antippen | ✅ | `ReceiptDetailScreen.kt` |  |
| KB-L-03 | Von einer Position zum Preisverlauf springen | Bon-Detail › Position | Zeile antippen | ✅ | `ReceiptDetailScreen.kt` |  |
| KB-L-04 | Neuen Scan starten | Einkäufe | FAB („Scannen") | ✅ | `ReceiptListScreen.kt` |  |

## KB-P · Preise

| ID | Funktion | Wo | Bedienelement | Status | Code | Notiz |
|---|---|---|---|---|---|---|
| KB-P-01 | Produkte durchsuchen | Einkäufe › Preise | Textfeld | ✅ | `ProductPriceListScreen.kt` |  |
| KB-P-02 | Preisverlauf als Kurve anzeigen | Preise › Produkt | automatisch | ✅ | `ProductPriceDetailScreen.kt` · `PriceSparklineSection` |  |
| KB-P-03 | Zuletzt gezahlten und günstigsten Preis anzeigen | Preise › Produkt | automatisch | ✅ | `ProductPriceDetailScreen.kt` · `PriceHeaderCard` |  |
| KB-P-04 | Günstigsten Markt anzeigen | Preise › Produkt | automatisch | ✅ | `ProductPriceDetailScreen.kt` · `PriceHeaderCard` |  |
| KB-P-05 | Märkte im Preis vergleichen | Preise › Produkt | automatisch | ✅ | `ProductPriceDetailScreen.kt` · `StoreComparisonSection` |  |
| KB-P-06 | Kaufverlauf mit Datum, Markt und Preis anzeigen | Preise › Produkt | automatisch | ✅ | `ProductPriceDetailScreen.kt` · `PricePointRow` |  |

## KB-K · Kostenübersicht

| ID | Funktion | Wo | Bedienelement | Status | Code | Notiz |
|---|---|---|---|---|---|---|
| KB-K-01 | Zeitraum wählen (Woche · Monat · Alle) | Einkäufe › Kostenübersicht | Filter-Chips | ✅ | `CostOverviewViewModel.kt` · `setPeriod` |  |
| KB-K-02 | Gesamtausgaben und Anzahl Bons anzeigen | Kostenübersicht | automatisch | ✅ | `CostOverviewScreen.kt` · `CostSummaryCard` |  |
| KB-K-03 | Ausgaben pro Markt als Balken anzeigen | Kostenübersicht | automatisch | ✅ | `CostOverviewScreen.kt` · `CostBarItem` |  |
| KB-K-04 | Ausgabenverlauf nach Datum anzeigen | Kostenübersicht | automatisch | ✅ | `CostOverviewScreen.kt` · `CostBarItem` |  |
| KB-K-05 | Monatsbudget festlegen | Kostenübersicht › Budget | Karte → Dialog | ✅ | `CostOverviewScreen.kt` · `BudgetEditDialog` |  |
| KB-K-06 | Warnschwelle zum Budget festlegen | Budget-Dialog | Textfeld | ✅ | `CostOverviewViewModel.kt` · `saveBudget` |  |

---

# 🏬 MK – Märkte & Gänge

Erreichbar über Einstellungen › Märkte verwalten.

| ID | Funktion | Wo | Bedienelement | Status | Code | Notiz |
|---|---|---|---|---|---|---|
| MK-01 | Markt anlegen | Märkte | FAB → Dialog | ✅ | `StoreListScreen.kt` · `NewStoreDialog` |  |
| MK-02 | Markt löschen | Märkte › Marktzeile | Icon-Button | ✅ | `StoreListViewModel.kt` · `deleteStore` |  |
| MK-03 | Aktiven Markt festlegen | Märkte › Marktzeile | Zeile antippen | ✅ | `StoreListViewModel.kt` · `setActiveStore` |  |
| MK-04 | Gänge eines Marktes bearbeiten | Märkte › Marktzeile | Icon-Button → Bottom Sheet | ✅ | `StoreListScreen.kt` · `AisleEditorSheet` |  |
| MK-05 | Gang anlegen | Gang-Sheet | Textfeld + Icon-Button | ✅ | `StoreListViewModel.kt` · `addAisle` |  |
| MK-06 | Gang löschen | Gang-Sheet › Gangzeile | Icon-Button | ✅ | `StoreListViewModel.kt` · `deleteAisle` |  |
| MK-07 | Gang nach oben schieben | Gang-Sheet › Gangzeile | Icon-Button (▲) | ✅ | `StoreListViewModel.kt` · `moveAisleUp` |  |
| MK-08 | Gang nach unten schieben | Gang-Sheet › Gangzeile | Icon-Button (▼) | ✅ | `StoreListViewModel.kt` · `moveAisleDown` |  |
| MK-09 | Gelernte Produkt-zu-Gang-Zuordnung je Markt | – | automatisch | ✅ | `StoreRepository.kt` |  |

---

# ⚙️ ST – Einstellungen

Erreichbar über den Icon-Button (Zahnrad) im Reiter *Rezepte*.

## ST-A · Erscheinungsbild

| ID | Funktion | Wo | Bedienelement | Status | Code | Notiz |
|---|---|---|---|---|---|---|
| ST-A-01 | Darstellungsmodus wählen (System · Hell · Dunkel) | Einstellungen › Erscheinungsbild | Segmentierte Auswahl | ✅ | `SettingsViewModel.kt` · `setThemeMode` |  |
| ST-A-02 | Akzentfarbe wählen | Einstellungen › Erscheinungsbild | Farbfeld-Auswahl | ✅ | `SettingsViewModel.kt` · `setAccentColor` |  |

## ST-N · Benachrichtigungen

| ID | Funktion | Wo | Bedienelement | Status | Code | Notiz |
|---|---|---|---|---|---|---|
| ST-N-01 | Einkaufstag-Erinnerung ein-/ausschalten | Einstellungen › Benachrichtigungen | Schalter | ✅ | `SettingsViewModel.kt` · `setNotifyShoppingDay` |  |
| ST-N-02 | Koch-Erinnerung ein-/ausschalten | Einstellungen › Benachrichtigungen | Schalter | ✅ | `SettingsViewModel.kt` · `setNotifyCookReminder` |  |
| ST-N-03 | Einkaufstag festlegen | Einstellungen › Einkaufen | Dropdown-Menü | ✅ | `SettingsScreen.kt` · `SettingsShoppingDayDropdown` |  |

## ST-S · Server & Synchronisation

| ID | Funktion | Wo | Bedienelement | Status | Code | Notiz |
|---|---|---|---|---|---|---|
| ST-S-01 | Server-URL und API-Schlüssel eingeben | Einstellungen › Server | Textfeld | ✅ | `SettingsViewModel.kt` · `setServerUrl` |  |
| ST-S-02 | Verbindung speichern und testen | Einstellungen › Server | Button | ✅ | `SettingsViewModel.kt` · `testAndSave` |  |
| ST-S-03 | Zeitpunkt des letzten Syncs anzeigen | Einstellungen › Synchronisation | automatisch | ✅ | `SettingsScreen.kt` · `formatTimestamp` |  |
| ST-S-04 | Sync sofort auslösen | Einstellungen › Synchronisation | Button | ✅ | `SettingsViewModel.kt` · `syncNow` |  |

## ST-K · KI-Sammelaktionen

| ID | Funktion | Wo | Bedienelement | Status | Code | Notiz |
|---|---|---|---|---|---|---|
| ST-K-01 | Nährwerte für alle Rezepte berechnen | Einstellungen › KI-Sammelaktionen | Button | ✅ | `SettingsViewModel.kt` · `runBulkAi` |  |
| ST-K-02 | Alle Rezepte klassifizieren | Einstellungen › KI-Sammelaktionen | Button | ✅ | `SettingsViewModel.kt` · `runBulkAi` |  |
| ST-K-03 | Beides in einem Durchlauf ausführen | Einstellungen › KI-Sammelaktionen | Button | ✅ | `SettingsViewModel.kt` · `runBulkAi` |  |
| ST-K-04 | Fortschritt und Ergebnis anzeigen | Einstellungen › KI-Sammelaktionen | automatisch | ✅ | `SettingsScreen.kt` |  |

## ST-E · Einkaufen

| ID | Funktion | Wo | Bedienelement | Status | Code | Notiz |
|---|---|---|---|---|---|---|
| ST-E-01 | Märkte verwalten öffnen | Einstellungen › Einkaufen | Button | ✅ | `StoreListScreen.kt` |  |
| ST-E-02 | Einkaufslisten verwalten und löschen | Einstellungen › Einkaufen | Liste + Icon-Button | ✅ | `SettingsViewModel.kt` · `deleteShoppingList` |  |
| ST-E-03 | Schnellbutton anlegen (Emoji · Artikel · Menge · Einheit) | Einstellungen › Schnellbuttons | Button → Dialog | ✅ | `SettingsScreen.kt` · `QuickEmojiDialog` |  |
| ST-E-04 | Schnellbutton bearbeiten | Einstellungen › Schnellbuttons | Zeile antippen → Dialog | ✅ | `SettingsViewModel.kt` · `updateQuickEmoji` |  |
| ST-E-05 | Schnellbutton löschen | Einstellungen › Schnellbuttons | Icon-Button | ✅ | `SettingsViewModel.kt` · `deleteQuickEmoji` |  |
| ST-E-06 | Einkaufs- und Wochenplan-Einstellungen speichern | Einstellungen › Einkaufen | Button | ✅ | `SettingsViewModel.kt` · `saveFeatureSettings` |  |
| ST-E-07 | Alle Daten exportieren | Einstellungen › Einkaufen | Button | ✅ | `SettingsViewModel.kt` · `exportAllData` |  |

## ST-C · Konto

| ID | Funktion | Wo | Bedienelement | Status | Code | Notiz |
|---|---|---|---|---|---|---|
| ST-C-01 | Abmelden (Server-URL und Schlüssel entfernen, Rezepte behalten) | Einstellungen › Konto | Button → Dialog | ✅ | `SettingsViewModel.kt` · `logout` |  |

---

# 🔄 AL – Übergreifend

| ID | Funktion | Wo | Bedienelement | Status | Code | Notiz |
|---|---|---|---|---|---|---|
| AL-01 | Alle Funktionen ohne Internet nutzbar | überall | automatisch | ✅ | `AppDatabase.kt` |  |
| AL-02 | Im Hintergrund synchronisieren, sobald Verbindung besteht | – | automatisch | ✅ | `SyncWorker.kt` · `NetworkObserver.kt` |  |
| AL-03 | Sync-Zustand anzeigen (bereit · läuft · erfolgreich · offline · Fehler) | Rezepte › Titelzeile | Statussymbol | ✅ | `SyncStatusIcon.kt` |  |
| AL-04 | Gelöschtes erst nach bestätigtem Sync endgültig entfernen | – | automatisch | ✅ | `SyncEngine.kt` |  |
| AL-05 | Bilder im Hintergrund zum Server hochladen | – | automatisch | ✅ | `ImageUploadWorker.kt` |  |
| AL-06 | Ersteinrichtung: Server-URL und Schlüssel eingeben | Onboarding | Textfeld | ✅ | `OnboardingScreen.kt` |  |
| AL-07 | Verbindung testen mit klarer Fehlermeldung | Onboarding | Button | ✅ | `OnboardingViewModel.kt` |  |
| AL-08 | Heutiges Rezept auf dem Homescreen anzeigen | Android-Homescreen | Widget | ✅ | `TodayRecipeWidget.kt` |  |
| AL-09 | Einkaufsliste auf der Smartwatch abhaken | Wear OS | eigener Bildschirm | ✅ | `ShoppingListWearScreen.kt` |  |
| AL-10 | Erinnerungen als Systembenachrichtigung senden | Android-Benachrichtigungen | automatisch | ✅ | `NotificationScheduler.kt` |  |

---

# Nicht erreichbare Funktionen

Im Code vorhanden, aber über die Oberfläche nicht zugänglich. Sortiert nach Nutzen
im Verhältnis zum Aufwand.

| ID | Funktion | Status | Was fehlt | Auswirkung | Code | Notiz |
|---|---|---|---|---|---|---|
| AL-11 | Allergie-Profil | ⛔ | Ein Eingabefeld in den Einstellungen. `saveAllergies()` hat keinen einzigen Aufrufer. | Die Allergie-Liste bleibt immer leer. Dadurch bleiben **EK-A-12** (Warnbanner) und **WP-C-08** (Allergene ausschließen) unsichtbar, obwohl beide fertig sind – ebenso die Weitergabe an KI-Erstellung und Remix. | `AppPreferences.kt` · `saveAllergies` |  |
| AL-12 | Monats-Report | ⛔ | Ein Einstiegspunkt. Der Bildschirm mit „X Rezepte gekocht", Top-Rezepten und Entdeckungen ist fertig, wird aber nirgends aufgerufen. | Funktion nicht auffindbar. | `StatsScreen.kt` |  |
| AL-13 | Vorrats-Verwaltung | ⛔ | Der Bildschirm selbst. Die deutschen Texte (Vorrat, Artikelname, Menge, Einheit, Kategorie) liegen bereits vor. | Nur der Vorratsstapel in der Einkaufsliste (**EK-E-09** bis **EK-E-11**) ist nutzbar, eine echte Vorratshaltung nicht. | `strings.xml` |  |
| AL-14 | Zutat einem Produkt zuordnen | ⛔ | Die Oberfläche. Datenmodell und Tabelle bestehen. | Rezeptzutaten lassen sich nicht mit konkreten Produkten und deren Preisen verknüpfen. | `ingredient_mapping_title` |  |
