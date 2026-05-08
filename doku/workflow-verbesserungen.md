# Helga Android – Workflow-Verbesserungen für schnelle, gesunde Wochenplanung

Analyse des gesamten App-Workflows mit dem Ziel: **In möglichst wenigen Schritten einen funktionierenden, ausgewogenen Wochenplan erstellen.**

Basierend auf Review aller Screens, ViewModels, Entities, DAOs, Navigation und bestehender Pläne (Stand: Mai 2026, DB v13).

---

## Aktueller Workflow (Ist-Zustand)

```
1. App öffnen → Einkaufen-Tab (Standard)
2. → Rezepte-Tab → Rezepte durchsuchen/importieren/KI-erstellen
3. → Wochenplan-Tab → "Generieren" drücken → Vorschlag annehmen/verwerfen
4. → Pro Tag: Rezept hinzufügen, Extras notieren, Tagesnotizen
5. → Export → Einkaufsliste erstellen
6. → Einkaufen-Tab → Einkaufen gehen
```

### Stärken des aktuellen Systems
- Lokaler Smart-Generator mit Constraints (Fleisch/Fisch/Veg/Wiederholung)
- Tages-Flags (⚡ schnell, 👥 Gäste) werden vom Generator berücksichtigt
- Feedback-System (👍/👎) beeinflusst zukünftige Gewichtung
- Recipe-History verhindert Wiederholungen
- Export → Einkaufsliste funktioniert pro Tag und pro Woche
- Markt-Gangsortierung für effizientes Einkaufen

### Schwachstellen / Reibungspunkte
1. **Kein Saisonfilter im Generator** – `seasonFit` existiert auf Rezepten, wird aber nicht genutzt
2. **Keine Übersicht über Ernährungsbilanz** – User sieht nicht auf einen Blick, wie die Woche verteilt ist
3. **Generierung zeigt keinen Kontext** – User sieht beim Vorschlag nicht, *warum* ein Rezept gewählt wurde
4. **Rezepte ohne Klassifizierung** rutschen durch den Generator als "unkategorisiert"
5. **Kein Quick-Regenerate für einzelne Tage** – nur Gesamtwoche neu generierbar
6. **Wochenplan → Einkaufen erfordert Tabwechsel + manuelle Listenwahl**
7. **Keine Nährwert-Hinweise** – "gesund" wird nur über Protein-Typ approximiert

---

## Verbesserungsvorschläge

### Phase 1 – Quick Wins (kein DB-Change, hohe Wirkung)

#### 1.1 – Saison-Filter im Generator aktivieren

**Problem:** `RecipeEntity.seasonFit` wird bei der Klassifizierung gesetzt (winter/sommer/ganzjährig), aber der lokale Generator ignoriert es.

**Umsetzung in `WeekplanViewModel.generateWeekplan()`:**
```kotlin
// Aktuellen Monat bestimmen
val currentMonth = LocalDate.now().monthValue
val currentSeason = when (currentMonth) {
    in 3..5 -> "frühling"
    in 6..8 -> "sommer"
    in 9..11 -> "herbst"
    else -> "winter"
}

// Beim Filtern der Kandidaten saisonale Rezepte bevorzugen
val seasonFiltered = candidates.partition { recipe ->
    recipe.seasonFit.isBlank() ||
    recipe.seasonFit.lowercase() == "ganzjährig" ||
    recipe.seasonFit.lowercase() == currentSeason
}
// Saisonale zuerst, dann Rest als Fallback
val orderedCandidates = seasonFiltered.first + seasonFiltered.second
```

**Aufwand:** Klein | **Wirkung:** Saisongerechte Rezepte werden bevorzugt

---

#### 1.2 – Ernährungsbilanz-Anzeige in der Wochenübersicht

**Problem:** User sieht nicht, wie die aktuelle Woche verteilt ist (Fleisch/Fisch/Veg).

**Umsetzung:** Kleine Bilanz-Zeile unter der Wochennavigation:

```
KW 19 · 05.05.–11.05.
🥩 2 · 🐟 1 · 🥬 3 · ❓ 1
```

**In `WeekplanScreen.kt`** nach dem Week-Nav-Item:
```kotlin
item(key = "balance") {
    val balance = remember(days, allRecipes) {
        // Zähle proteinType über alle Tages-Rezepte
    }
    BalanceRow(meat = balance.meat, fish = balance.fish, veg = balance.veg, other = balance.other)
}
```

**Dateien:** `WeekplanViewModel.kt` (neuer StateFlow `weekBalance`), `WeekplanScreen.kt` (neue `BalanceRow` Composable)

**Aufwand:** Klein | **Wirkung:** Sofortige Übersicht über Ernährungsbalance

---

#### 1.3 – Einzelnen Tag neu generieren

**Problem:** Wenn nur ein Tag nicht passt, muss die gesamte Woche neu generiert werden.

**Umsetzung:** Long-Press oder Button in DayCard → "Rezept austauschen" → Generator wählt ein neues Rezept unter Berücksichtigung der bestehenden Wochenbilanz.

```kotlin
fun regenerateDay(dayId: String) {
    viewModelScope.launch {
        val day = days.value.find { it.id == dayId } ?: return@launch
        val existingRecipeIds = days.value.flatMap { d ->
            weekplanDao.recipesForDay(d.id).map { it.recipeId }
        }.toSet()
        // Neues Rezept unter Berücksichtigung der Constraints wählen
        // ... (ähnlich wie im Generator, aber für 1 Tag)
        syncScheduler.triggerOneShot()
    }
}
```

**Dateien:** `WeekplanViewModel.kt`, `WeekplanScreen.kt` (neuer Button in DayCard)

**Aufwand:** Mittel | **Wirkung:** Hoch – spart komplette Neugenerierung

---

#### 1.4 – Unklassifizierte Rezepte kennzeichnen

**Problem:** Rezepte ohne `proteinType` werden im Generator als "Sonstige" behandelt und können die Balance verfälschen.

**Umsetzung:**
- In `RecipeListScreen`: Badge/Icon bei unklassifizierten Rezepten anzeigen
- Im Generator: Warnung wenn >30% der Rezepte unklassifiziert sind
- Optional: Hinweis-Snackbar "12 Rezepte sind noch nicht klassifiziert"

**Dateien:** `RecipeListScreen.kt` (Badge), `WeekplanViewModel.kt` (Warnung)

**Aufwand:** Klein | **Wirkung:** Motiviert User zur Klassifizierung → bessere Pläne

---

### Phase 2 – Workflow-Beschleunigung (kein DB-Change)

#### 2.1 – Direkt-Export nach Generierung

**Problem:** Nach dem Generieren muss der User manuell "Export → Einkaufsliste" triggern.

**Umsetzung:** Nach `applyProposal()` eine Snackbar mit Aktion zeigen:
```
"Wochenplan erstellt"  [ZUR EINKAUFSLISTE]
```

Klick → exportiert automatisch die gesamte Woche in die Standard-Einkaufsliste und wechselt zum Einkaufen-Tab.

**Dateien:** `WeekplanScreen.kt` (Snackbar mit Action), `WeekplanViewModel.kt` (Export-Methode)

**Aufwand:** Klein | **Wirkung:** Spart 3-4 Klicks nach jeder Generierung

---

#### 2.2 – Rezept-Vorschau im Proposal-Sheet

**Problem:** Das Proposal-Sheet zeigt nur Rezeptnamen. User kann nicht beurteilen, ob das Rezept passt, ohne jedes einzeln anzutippen.

**Umsetzung:** Im `ProposalSheet` neben dem Rezeptnamen:
- Kleines Thumbnail (48dp)
- Protein-Typ als Emoji (🥩🐟🥬)
- `effort` als Badge (⚡/🍳/👨‍🍳)
- `totalTime` wenn vorhanden

**Dateien:** `WeekplanScreen.kt` (`ProposalSheet` erweitern)

**Aufwand:** Klein | **Wirkung:** Bessere Entscheidungsgrundlage beim Annehmen/Verwerfen

---

#### 2.3 – Wochenplan-Schnellstart vom Einkaufen-Tab

**Problem:** Der häufigste Workflow beginnt beim Einkaufen-Tab, aber der Wochenplan ist 2 Taps entfernt.

**Umsetzung:** Wenn keine aktive Einkaufsliste befüllt ist und ein Wochenplan existiert:
- Banner anzeigen: "Wochenplan KW 19 bereit — [Zur Einkaufsliste exportieren]"

**Dateien:** `ShoppingListScreen.kt` (bedingtes Banner), `ShoppingListViewModel.kt` (Wochenplan-Check)

**Aufwand:** Mittel | **Wirkung:** Verbindet die zwei wichtigsten Workflows

---

### Phase 3 – Intelligentere Planung (ggf. DB-Change)

#### 3.1 – Mahlzeit-Typ berücksichtigen (Mittag vs. Abend)

**Problem:** Generator unterscheidet nicht zwischen Mittag- und Abendessen. `mealType` existiert auf Rezepten, wird aber nicht genutzt.

**Umsetzung:** Optional pro Tag 2 Slots (Mittag/Abend) statt nur 1 Rezept.

**DB-Change:** `WeekplanRecipeEntity` → neues Feld `slot` (VARCHAR, "lunch"/"dinner"/"any")

**Aufwand:** Groß | **Wirkung:** Realistischere Wochenplanung für Familien

---

#### 3.2 – "Reste-Verwertung" Logik

**Problem:** Wenn Montag ein großes Gericht gekocht wird, könnte Dienstag ein leichtes Gericht / Resteessen sein.

**Umsetzung:** Generator berücksichtigt `recipeYield` (Portionen) und setzt am Folgetag ein schnelles/einfaches Rezept:
```kotlin
if (previousDayRecipe.recipeYield.contains(Regex("\\d+")) &&
    previousDayRecipe.recipeYield.extractNumber() >= 6) {
    // Folgetag: bevorzuge schnelle/einfache Rezepte
}
```

**Aufwand:** Mittel | **Wirkung:** Realistischerer Plan, weniger Food-Waste

---

#### 3.3 – Einkaufslisten-Zusammenführung

**Problem:** Beim Export werden Zutaten 1:1 übernommen. Wenn Montag und Mittwoch beide "2 Zwiebeln" brauchen, stehen sie doppelt in der Liste.

**Prüfung:** Aktuell nutzt `addOrMergeItem()` bereits einen Merge – prüfen ob die Mengenaggregation korrekt funktioniert (gleiche Einheit → addieren, verschiedene Einheiten → beide listen).

**Aufwand:** Klein (Validierung) | **Wirkung:** Saubere Einkaufsliste

---

### Phase 4 – Langfristige Verbesserungen

#### 4.1 – Wochen-Statistik / Dashboard

Monatliche Übersicht:
- Wie oft Fleisch/Fisch/Veg in den letzten 4 Wochen
- Lieblingsrezepte (aus Feedback-Daten)
- Rezepte die noch nie probiert wurden

**Datenquelle:** `recipe_history` + `recipe_feedback` – beides existiert bereits

**Aufwand:** Groß | **Wirkung:** Langfristige Ernährungsoptimierung

---

#### 4.2 – Saisonaler Zutaten-Kalender

Hinweis auf saisonale Zutaten beim Generieren:
- "Mai: Spargel, Erdbeeren, Rhabarber, Radieschen"
- Generator bevorzugt Rezepte mit saisonalen Hauptzutaten

**Umsetzung:** Statische Saison-Map im Code (kein Server/DB nötig)

**Aufwand:** Mittel | **Wirkung:** Gesündere + günstigere Einkäufe

---

#### 4.3 – Budget-Awareness

Optionales Feld `estimatedCost` auf Rezepten. Generator kann Wochen-Budget einhalten.

**Aufwand:** Groß (DB-Change + Datenpflege) | **Wirkung:** Niedrig (schwer zu pflegen)

---

## Empfohlene Reihenfolge

| # | Feature | Aufwand | Wirkung | Priorität |
|---|---------|---------|---------|-----------|
| 1 | 1.1 – Saison-Filter im Generator | klein | hoch | ⭐⭐⭐ |
| 2 | 1.2 – Ernährungsbilanz-Anzeige | klein | hoch | ⭐⭐⭐ |
| 3 | 2.1 – Direkt-Export nach Generierung | klein | hoch | ⭐⭐⭐ |
| 4 | 1.3 – Einzelnen Tag neu generieren | mittel | hoch | ⭐⭐⭐ |
| 5 | 2.2 – Rezept-Vorschau im Proposal | klein | mittel | ⭐⭐ |
| 6 | 1.4 – Unklassifizierte Rezepte Badge | klein | mittel | ⭐⭐ |
| 7 | 3.3 – Einkaufslisten-Merge validieren | klein | mittel | ⭐⭐ |
| 8 | 2.3 – Wochenplan-Banner im Einkaufen-Tab | mittel | mittel | ⭐⭐ |
| 9 | 4.2 – Saisonaler Zutaten-Kalender | mittel | mittel | ⭐ |
| 10 | 3.2 – Reste-Verwertung Logik | mittel | niedrig | ⭐ |
| 11 | 4.1 – Wochen-Statistik Dashboard | groß | niedrig | ⭐ |
| 12 | 3.1 – Mahlzeit-Slots (Mittag/Abend) | groß | niedrig | ⭐ |
| 13 | 4.3 – Budget-Awareness | groß | niedrig | – |

---

## Bereits implementiert (nicht in diesem Plan)

| Feature | Status |
|---------|--------|
| Smart-Generator mit Constraints | ✅ Lokal, greedy mit Feedback-Gewichtung |
| Tages-Flags (schnell/Gäste) | ✅ `isQuickDay`, `isGuestDay` |
| Mini-Feedback (👍/👎) | ✅ `recipe_feedback` Tabelle + UI |
| Fisch-Constraint | ✅ `maxFishPerWeek` in Constraints |
| Recipe-History / Rotations-Tracking | ✅ `recipe_history` Tabelle |
| Tag-Filter in Rezeptliste (Multi-Select) | ✅ `TagFilterDialog` |
| Rezept → Wochenplan hinzufügen | ✅ `WeekplanDayPickerDialog` |
| Store-Dropdown im Einkaufen-Tab | ✅ Markt-Auswahl für Gangsortierung |
| Export Woche/Tag → Einkaufsliste | ✅ ShoppingCart-Buttons |
| KI-Klassifizierung (einzeln + Bulk) | ✅ Server-Endpoint |

---

## Nicht empfohlen

| Feature | Begründung |
|---------|------------|
| AI-Wochenplan-Assistent (LLM-Call pro Woche) | Greedy-Generator deckt 80/20 ab; LLM-Calls teuer |
| Drag & Drop Rezepte auf Wochentage | Touch-UI: RecipePicker + Dialog reicht; DnD komplex |
| Constraint-Profile (benannte Sätze) | Globale Constraints reichen; unnötige Komplexität |
| Nährwert-Tracking pro Rezept | Erfordert Nährwert-Datenbank; sehr großer Aufwand |
