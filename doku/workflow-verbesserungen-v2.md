# Helga Android – Workflow-Verbesserungen v2

**Ziel:** In möglichst wenigen Schritten einen funktionierenden, ausgewogenen Wochenplan erstellen.

**Stand:** Mai 2026, DB v13 – nach Implementierung der ersten Verbesserungsrunde.

---

## Aktueller Workflow (nach v1-Verbesserungen)

```
1. App öffnen → Wochenplan-Tab
2. "Generieren" → Vorschlag mit Bildern/Protein/Zeit begutachten → Annehmen
3. Optional: Einzeltag regenerieren (🔄), Tages-Flags setzen (⚡/👥)
4. Snackbar "Zur Einkaufsliste" → Export in einem Klick
5. Einkaufen-Tab → Markt wählen → Gangsortiert einkaufen
```

### Was bereits funktioniert (implementiert)

| Feature | Status |
|---------|--------|
| Smart-Generator mit Constraints (Fleisch/Fisch/Veg/Wiederholung) | ✅ |
| Saison-Filter im Generator | ✅ |
| Ernährungsbilanz-Zeile (🥩🐟🥬❓) | ✅ |
| Einzeltag regenerieren (Refresh-Button) | ✅ |
| Direkt-Export-Snackbar nach Generierung | ✅ |
| Rezept-Vorschau im Proposal-Sheet (Bild + Emoji + Zeit) | ✅ |
| Tages-Flags (⚡ schnell, 👥 Gäste) | ✅ |
| Feedback-System (👍/👎) mit Gewichtung | ✅ |
| Recipe-History / Rotations-Tracking | ✅ |
| Tag-Filter in Rezeptliste (Multi-Select) | ✅ |
| Rezept → Wochenplan hinzufügen (aus Detail) | ✅ |
| Store-Dropdown im Einkaufen-Tab | ✅ |
| Wochenplan-Templates (speichern/laden) | ✅ (Backend, kein UI) |
| KI-Klassifizierung (einzeln + Bulk) | ✅ |
| URL-Import + KI-Generierung | ✅ |

---

## Verbleibende Schwachstellen

1. **Template-UI fehlt** – Templates existieren im ViewModel/Repository, aber kein Screen zum Verwalten
2. **Kein „Schnellstart" vom Startscreen** – User muss zum Wochenplan-Tab navigieren
3. **Unklassifizierte Rezepte unsichtbar** – Generator behandelt sie als „Sonstige", User merkt es nicht
4. **Keine Rezept-Abwechslung nach Küche** – 7× italienisch ist möglich
5. **Einkaufslisten-Merge nicht validiert** – Mengenaggregation bei gleichem Produkt unklar
6. **Keine Portionsanpassung beim Export** – Zutaten werden 1:1 exportiert, egal für wieviele Personen
7. **Kein „Heute kochen"-Shortcut** – Schnellzugriff auf das heutige Rezept fehlt
8. **Proposal-Sheet nicht editierbar** – Einzelne Tage im Vorschlag nicht austauschbar
9. **Reste-Verwertung nicht berücksichtigt** – Große Portionen könnten Folgetage vereinfachen
10. **Keine wochenübergreifende Planung** – Keine Sicht auf die nächsten 2-3 Wochen gleichzeitig

---

## Neue Verbesserungsvorschläge

### Phase 1 – Quick Wins (kein DB-Change, hohe Wirkung)

#### 1.1 – Template-Verwaltung im Wochenplan

**Problem:** `WeekplanTemplateRepository` und `applyTemplate()`/`saveAsTemplate()` existieren, aber es gibt keine UI zum Speichern, Laden oder Löschen von Templates.

**Nutzen:** „Standardwoche" speichern (z.B. Mo=schnell, Mi=Fisch, Sa=aufwendig) und mit einem Tap wiederverwenden.

**Umsetzung:**
- Neuer Menüpunkt im TopAppBar-Overflow: "Vorlagen"
- Dialog/Sheet mit Liste gespeicherter Templates
- "Aktuelle Woche als Vorlage speichern" Button
- "Vorlage anwenden" überschreibt die aktuelle Woche

**Dateien:** `WeekplanScreen.kt` (neues TemplateSheet), bestehende ViewModel-Methoden nutzen

**Aufwand:** Mittel | **Wirkung:** Hoch – spart komplette Neugenerierung bei wiederkehrenden Mustern

---

#### 1.2 – Unklassifizierte Rezepte kennzeichnen + Warnung

**Problem:** Rezepte ohne `proteinType` werden als "Sonstige" gezählt. Bei vielen unklassifizierten Rezepten ist die Bilanz-Anzeige irreführend.

**Umsetzung:**
- In der Bilanz-Zeile: Wenn `other > 2`, rot/orange einfärben + Tooltip
- Optional: Hinweis-Banner "X Rezepte sind nicht klassifiziert – Bulk-Klassifizierung starten?"
- In RecipeListScreen: Kleiner ⚠️-Badge bei Rezepten ohne proteinType

**Dateien:** `WeekplanScreen.kt` (Bilanz-Warnung), `RecipeListScreen.kt` (Badge)

**Aufwand:** Klein | **Wirkung:** Motiviert zur Klassifizierung → bessere Pläne

---

#### 1.3 – Küchen-Diversität im Generator

**Problem:** `cuisine` (italienisch, asiatisch, deutsch, etc.) existiert auf RecipeEntity, wird aber vom Generator ignoriert. 7× Pasta ist möglich.

**Umsetzung im Generator:**
```kotlin
// Bereits zugewiesene Küchen tracken
val usedCuisines = mutableListOf<String>()
// Beim Filtern: Rezepte bevorzugen deren Küche noch nicht 2× vorkommt
val diversePool = finalPool.sortedBy { 
    usedCuisines.count { c -> c == it.cuisine } 
}
```

**Aufwand:** Klein | **Wirkung:** Abwechslungsreichere Wochenpläne

---

#### 1.4 – „Heute kochen" Shortcut

**Problem:** User muss zum Wochenplan navigieren, den richtigen Tag finden, Rezept antippen → 4+ Klicks zum Starten der Kochansicht.

**Umsetzung:**
- Floating-Banner unten im Einkaufen-Tab oder Rezepte-Tab: "Heute: [Rezeptname] 🍳"
- Tap → direkt zur Kochansicht (`RecipeCookScreen`)
- Nur anzeigen wenn heute ein Rezept geplant ist

**Dateien:** `HelgaNavGraph.kt` oder jeweiliger Screen, `WeekplanRepository` (Query: heutiges Rezept)

**Aufwand:** Mittel | **Wirkung:** Hoch – häufigster Use-Case am Kochtag

---

#### 1.5 – Proposal-Sheet: Einzelnen Tag tauschen

**Problem:** Im Vorschlag-Sheet kann nur die gesamte Woche angenommen oder verworfen werden. Wenn 1 von 7 Tagen nicht passt → kompletter Neustart.

**Umsetzung:**
- Swipe-to-Dismiss oder 🔄-Button pro Zeile im ProposalSheet
- Tap → regeneriert nur diesen einen Tag im Vorschlag (nicht in DB, nur in der assignments-Liste)
- ViewModel: `regenerateProposalDay(index: Int)` – gleiche Logik wie `regenerateDay()` aber auf der Proposal-Liste

**Aufwand:** Mittel | **Wirkung:** Hoch – feingranulare Kontrolle ohne Neugenerierung

---

### Phase 2 – Workflow-Beschleunigung

#### 2.1 – Wochenplan-Schnellstart (Deep-Link vom Einkaufen-Tab)

**Problem:** Der häufigste Workflow: Sonntag → Plan erstellen → Einkaufen. Aber die Tabs sind getrennt.

**Umsetzung:**
- Wenn Einkaufsliste leer + Wochenplan der aktuellen Woche existiert:
  Banner: "Wochenplan KW 19 bereit – [Zur Einkaufsliste exportieren]"
- Wenn kein Wochenplan existiert:
  Banner: "Noch kein Wochenplan – [Jetzt erstellen]" → Navigiert zum Wochenplan-Tab + triggert Generate

**Dateien:** `ShoppingListScreen.kt`, `ShoppingListViewModel.kt` (Wochenplan-Zustand prüfen)

**Aufwand:** Mittel | **Wirkung:** Verbindet die zwei wichtigsten Workflows

---

#### 2.2 – Portionszahl beim Export berücksichtigen

**Problem:** Export übernimmt Zutaten 1:1. Wenn ein Rezept für 4 Personen ist, aber nur für 2 gekocht wird, stehen doppelte Mengen in der Liste.

**Umsetzung:**
- Beim Export-Dialog: Portionenzahl-Slider (Default: aus Rezept `recipeYield`)
- Mengen proportional anpassen: `quantity * (gewünscht / original)`
- Nur für numerische Mengen; textuelle Mengen ("etwas", "nach Geschmack") ignorieren

**Dateien:** `WeekplanRepository.exportToShoppingList()`, neuer Parameter `portionMultiplier`

**Aufwand:** Mittel | **Wirkung:** Genauere Einkaufslisten, weniger Verschwendung

---

#### 2.3 – Intelligente Einkaufslisten-Aggregation validieren

**Problem:** `ShoppingRepository.addOrMergeItem()` existiert, aber unklar ob:
- "200g Hähnchen" + "300g Hähnchen" = "500g Hähnchen" ✓
- "2 Zwiebeln" + "3 Zwiebeln" = "5 Zwiebeln" ✓
- "1 Dose Tomaten" + "200g Tomaten" = korrekt separate Einträge ✓

**Umsetzung:** Unit-Test + ggf. Bugfix in der Merge-Logik

**Aufwand:** Klein | **Wirkung:** Saubere Einkaufslisten

---

#### 2.4 – „Vergangene Woche wiederholen" Button

**Problem:** Manchmal war letzte Woche perfekt. Statt Template speichern + laden:

**Umsetzung:**
- Button in WeekplanScreen: "Letzte Woche übernehmen"
- Kopiert die Rezept-Zuordnungen der Vorwoche in die aktuelle Woche
- Zeigt Vorschau ähnlich ProposalSheet

**Aufwand:** Klein | **Wirkung:** Schnellster Weg zu einem Plan wenn letzte Woche gut war

---

### Phase 3 – Intelligentere Planung

#### 3.1 – Reste-Verwertung / Großportionen-Logik

**Problem:** Wenn Sonntag ein 6-Portionen-Gericht gekocht wird, könnte Montag planfrei sein oder ein schnelles Gericht werden.

**Umsetzung im Generator:**
```kotlin
if (previousDayRecipe?.let { parseYield(it.recipeYield) >= 6 } == true) {
    // Aktuellen Tag als QuickDay behandeln ODER optional leer lassen
}
```

**Aufwand:** Mittel | **Wirkung:** Realistischere Pläne, weniger Food-Waste

---

#### 3.2 – Mahlzeit-Typ nutzen (mealType)

**Problem:** `RecipeEntity.mealType` existiert (Frühstück/Mittagessen/Abendessen), wird aber nicht genutzt. Generator könnte für Abendessen keine Frühstücksrezepte wählen.

**Umsetzung:** Im Generator `mealType`-Filter hinzufügen – nur "Abendessen"/"Mittagessen"/"" zulassen (nicht "Frühstück", "Dessert", "Snack").

**Aufwand:** Klein | **Wirkung:** Verhindert unsinnige Zuordnungen

---

#### 3.3 – Favoriten-Boost im Generator

**Problem:** `isFavorite` existiert auf Rezepten, wird aber nicht bevorzugt. Favoriten sollten häufiger erscheinen.

**Umsetzung:** In der gewichteten Auswahl einen Bonus für Favoriten:
```kotlin
val score = (feedbackScores[it.id] ?: 0) + if (it.isFavorite == 1) 2 else 0
```

**Aufwand:** Minimal | **Wirkung:** Pläne enthalten mehr Lieblingsrezepte

---

#### 3.4 – Generator: "Anker-Rezepte" respektieren

**Problem:** Wenn ein Tag bereits manuell ein Rezept hat (z.B. durch "Rezept zum Wochenplan hinzufügen"), überschreibt der Generator es.

**Umsetzung:**
- Vor Generierung prüfen welche Tage bereits Rezepte haben
- Diese Tage überspringen (oder nur leere Tage generieren)
- Checkbox im Generate-Dialog: "Bestehende Rezepte beibehalten"

**Aufwand:** Klein | **Wirkung:** Hoch – ermöglicht Hybrid-Workflow (teilweise manuell + Generator für den Rest)

---

### Phase 4 – Langfristige Features

#### 4.1 – Wochen-Statistik Dashboard

**Problem:** Kein Überblick über langfristige Ernährungsmuster.

**Umsetzung:** Neuer Screen (erreichbar aus Settings oder Wochenplan):
- Letzte 4 Wochen: Fleisch/Fisch/Veg-Verteilung als Balkendiagramm
- Top-5 Rezepte (aus History)
- Rezepte die >30 Tage nicht gekocht wurden ("Vergessene Schätze")
- Datenquelle: `recipe_history` + `recipe_feedback` (existieren bereits)

**Aufwand:** Groß | **Wirkung:** Langfristige Ernährungsoptimierung

---

#### 4.2 – Saisonaler Zutaten-Kalender

**Problem:** `seasonFit` auf Rezeptebene ist grob. Besser wäre ein Hinweis auf saisonale *Zutaten*.

**Umsetzung:** Statische Map `Map<Int, List<String>>` (Monat → saisonale Lebensmittel). Generator bevorzugt Rezepte deren Hauptzutaten gerade Saison haben.

```kotlin
val seasonalIngredients = mapOf(
    5 to listOf("spargel", "erdbeeren", "rhabarber", "radieschen", "bärlauch"),
    6 to listOf("kirschen", "zucchini", "erbsen", "blaubeeren"),
    // ...
)
```

**Aufwand:** Mittel | **Wirkung:** Gesündere + günstigere Einkäufe

---

#### 4.3 – Mehrpersonen-Haushalt / Portionen pro Tag

**Problem:** Manche Tage wird für 2 Personen gekocht, andere für 4 (Gäste). `isGuestDay` ist binär.

**DB-Change:** `WeekplanDayEntity` → neues Feld `portions: Int = 2`
**Export-Integration:** Zutatenmengen beim Export proportional anpassen

**Aufwand:** Groß | **Wirkung:** Genauere Einkaufslisten für Familien

---

## Empfohlene Reihenfolge (Prio nach Impact/Aufwand)

| # | Feature | Aufwand | Wirkung | Priorität |
|---|---------|---------|---------|-----------|
| 1 | 3.4 – Anker-Rezepte respektieren | klein | hoch | ⭐⭐⭐ |
| 2 | 3.3 – Favoriten-Boost | minimal | mittel | ⭐⭐⭐ |
| 3 | 3.2 – mealType-Filter | klein | mittel | ⭐⭐⭐ |
| 4 | 1.3 – Küchen-Diversität | klein | hoch | ⭐⭐⭐ |
| 5 | 1.5 – Proposal-Sheet Tag tauschen | mittel | hoch | ⭐⭐⭐ |
| 6 | 1.4 – "Heute kochen" Shortcut | mittel | hoch | ⭐⭐ |
| 7 | 1.2 – Unklassifizierte-Warnung | klein | mittel | ⭐⭐ |
| 8 | 1.1 – Template-UI | mittel | mittel | ⭐⭐ |
| 9 | 2.4 – Letzte Woche wiederholen | klein | mittel | ⭐⭐ |
| 10 | 2.1 – Schnellstart-Banner | mittel | mittel | ⭐⭐ |
| 11 | 2.3 – Einkaufslisten-Merge prüfen | klein | mittel | ⭐⭐ |
| 12 | 3.1 – Reste-Verwertung | mittel | niedrig | ⭐ |
| 13 | 2.2 – Portionszahl beim Export | mittel | mittel | ⭐ |
| 14 | 4.2 – Saisonaler Zutaten-Kalender | mittel | niedrig | ⭐ |
| 15 | 4.1 – Wochen-Statistik | groß | niedrig | ⭐ |
| 16 | 4.3 – Portionen pro Tag | groß | niedrig | – |

---

## Nicht empfohlen

| Feature | Begründung |
|---------|------------|
| AI-Wochenplan per LLM (Server-Call) | Lokaler Generator deckt 80/20; LLM-Calls teuer + langsam |
| Drag & Drop im Wochenplan | Touch-UX komplex; bestehende Buttons reichen |
| Nährwert-Tracking | Erfordert Nährwert-DB; riesiger Aufwand für wenig Mehrwert |
| Constraint-Profile (benannte Sets) | Globale Constraints reichen; unnötige Komplexität |
| Synchrone Mehrpersonen-Planung | Kein Mehrbenutzersystem geplant |

---

## Schnellster Weg zu einem guten Plan (Ideal-Workflow nach allen Verbesserungen)

```
1. App öffnen → Wochenplan-Tab
2. Optional: 1-2 Favoriten manuell auf bestimmte Tage setzen ("Anker")
3. "Generieren" → Generator füllt restliche Tage (respektiert Anker)
4. Proposal-Sheet: Vorschau mit Bildern prüfen → ggf. 1 Tag tauschen
5. "Annehmen" → Snackbar → "Zur Einkaufsliste" → fertig

Gesamtzeit: ~60 Sekunden für einen ausgewogenen 7-Tage-Plan
```
