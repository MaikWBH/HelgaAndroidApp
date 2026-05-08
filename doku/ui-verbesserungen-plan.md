# Helga Android – UI-Verbesserungen & Bugfixes (Phase 16+)

Abgeleitet aus `doku/todo.md`. Alle Punkte sind priorisiert nach Sichtbarkeit/Impact für den Nutzer.

---

## Übersicht

| # | Bereich | Aufwand | Priorität |
|---|---------|---------|-----------|
| 1 | Bug: Tab-Wechsel Rezepte→Einkaufen | klein | kritisch |
| 2 | Bug: Rezeptbilder werden nicht angezeigt | klein–mittel | kritisch |
| 3 | Rezeptliste: Plus-Button in der Liste | klein | hoch |
| 4 | Rezeptdetail: Rezeptname abgehackt | klein | hoch |
| 5 | Rezeptdetail: Arbeitsschritte ab 1 | trivial | hoch |
| 5.1 | Rezeptdetail: Zutaten Ansicht könnte optisch ansprechender sein (etwas mehr mittig und als Tabelle gegliedert) | trivial | mittel |
| 5.2 | Rezeptdetail: Die Metadaten eine Rezeptes sollten in der Rezept ansicht vlt als Emojie dargestellt werden um Platz zu sparen | trivial | mittel |
| 6 | Rezeptdetail: Alle Schritte sichtbar + abhaken | mittel | hoch |
| 7 | Rezept-Tags: Filtermenü statt permanente Anzeige | mittel | mittel |
| 8 | KI-Klassifizierung deaktivieren wenn vorhanden | klein | mittel |
| 9 | Einkaufen: „Nicht zugewiesen"-Standardgang | klein | hoch |
| 10 | Einkaufen: Emoji-Buttons ohne Beschriftung | trivial | hoch |
| 11 | Einkaufen: Emoji-Buttons scrollbar prüfen | klein | mittel |
| 12 | Einkaufen: Vorrat direkt in Liste laden | mittel | hoch |
| 13 | Einkaufen: Herkunfts-Tag (Vorrat / Rezept) | mittel | mittel |
| 14 | Einkaufen: Abhak-Modus als Einstellung | mittel | mittel |
| 15 | Einkaufen: Vorschläge beim Vorrat-Hinzufügen | klein | mittel |
| 16 | Wochenplan: Vorlage-Feature entfernen | trivial | hoch |
| 17 | Wochenplan: Extrazeile mit Vorschlägen | klein | hoch |
| 18 | Wochenplan: Rezeptbilder größer | trivial | mittel |
| 19 | Wochenplan: KI-Wochenplan-Button entfernen | trivial | hoch |
| 20 | Generell: Autocomplete bei allen Artikeleingaben | mittel | hoch |

---

## Bugs (kritisch zuerst)

### Bug 1 – Tab-Wechsel Rezepte → Einkaufen bleibt schwarz

**Symptom:** Nach Wechsel vom Rezepte-Tab in den Einkaufen-Tab bleibt der Inhalt schwarz und der Rezepte-Button bleibt aktiv markiert. Erst ein zweiter Tap auf Einkaufen öffnet die Ansicht korrekt.

**Wahrscheinliche Ursache:** Navigation Back-Stack oder `SaveableStateHolder` verhält sich unerwartet beim ersten Navigieren auf diesen Tab. Ggf. ein `remember`-State, der beim ersten Composition nicht korrekt initialisiert wird.

**Vorgehen:**
1. `NavHost`-Setup und Tab-Navigation in `MainActivity` / `HelgaNavGraph` prüfen
2. Prüfen ob `ShoppingListScreen` einen Ladeeffekt hat, der beim ersten `onResume` nicht getriggert wird
3. Ggf. `LaunchedEffect(Unit)` → `LaunchedEffect(true)` oder `key` am NavHost anpassen

---

### Bug 2 – Rezeptbilder werden nicht angezeigt

**Symptom:** Bilder in der Rezeptliste und im Rezeptdetail erscheinen nicht.

**Wahrscheinliche Ursachen:**
- Coil-Cache-Pfad stimmt nicht mit gespeichertem Pfad überein
- `FileProvider` konfiguriert, aber Pfad falsch referenziert
- Nach Sync: Server-URL für Bilder wird nicht korrekt aufgelöst

**Vorgehen:**
1. Coil-Loader-Konfiguration und `ImageLoader` in `HelgaApp.kt` prüfen
2. `RecipeEntity.imagePath` – prüfen ob lokaler Pfad oder Server-URL gespeichert wird
3. `FileProvider`-Authority und `res/xml/file_paths.xml` validieren
4. Logging: Coil `Logger` aktivieren um fehlgeschlagene Requests zu sehen

---

## Rezepte-Ansicht

### 3 – Plus-Button in der Rezeptliste

Derzeit ist der FAB zum Erstellen eines neuen Rezepts nur auf dem Hauptscreen. Er soll auch innerhalb der Rezeptliste sichtbar sein.

**Umsetzung:** `RecipeListScreen` um einen `FloatingActionButton` (oder `ExtendedFloatingActionButton`) ergänzen, der dieselbe Aktion wie der bestehende FAB auslöst → Navigation zu `RecipeFormScreen`.

---

### 4 – Rezeptname in Detailansicht abgeschnitten

**Symptom:** Lange Rezeptnamen werden in der Detailansicht abgehackt.

**Ziel:** Name wird vollständig in einer Zeile dargestellt. Bei zu langem Text soll ein `MarqueeText` (Lauftext) angezeigt werden.

**Umsetzung:**
```kotlin
// Compose hat kein Marquee nativ – BasicMarquee-Modifier (API 23+):
Text(
    text = recipe.name,
    maxLines = 1,
    modifier = Modifier.basicMarquee()
)
```
`basicMarquee()` ist seit Compose 1.4 verfügbar (BOM 2023.03+). Fallback: `overflow = TextOverflow.Ellipsis` wenn nicht verfügbar.

---

### 5 – Arbeitsschritte starten mit 0 statt 1

**Ursache:** Index wird direkt aus der Liste (`index`) gezogen, ohne +1.

**Fix:** Überall wo Schrittnummern angezeigt werden: `index + 1` statt `index`.

---

### 6 – Alle Arbeitsschritte gleichzeitig sichtbar + Abhaken

**Aktuell:** Nur ein Schritt auf einmal sichtbar (Schritt-für-Schritt-Modus).

**Ziel:**
- Alle Schritte werden untereinander angezeigt (kein Paging)
- Tap auf einen Schritt graut ihn aus (visuelles „erledigt"), kein DB-Write

**Umsetzung:**
```kotlin
// RecipeCookViewModel
val completedSteps = MutableStateFlow<Set<Int>>(emptySet())

fun toggleStep(index: Int) {
    _completedSteps.update { if (index in it) it - index else it + index }
}
```
```kotlin
// RecipeCookScreen – LazyColumn statt Pager
items(steps.size) { index ->
    val done = completedSteps.contains(index)
    StepRow(
        step = steps[index],
        number = index + 1,
        done = done,
        onClick = { vm.toggleStep(index) }
    )
}
```

---

### 7 – Rezept-Tags: Filtermenü statt Dauer-Anzeige

**Problem:** Zu viele Tags machen die Ansicht unübersichtlich.

**Ziel:** Tags sind standardmäßig ausgeblendet. Ein dediziertes Filtermenü (z.B. `ModalBottomSheet` oder `DropdownMenu`) zeigt alle verfügbaren Tags und erlaubt Mehrfachselektion.

**Umsetzung:**
- Filter-Icon (Funnel) in der `TopAppBar` der `RecipeListScreen`
- Tap öffnet `ModalBottomSheet` mit `FilterChip`s pro Tag
- Aktive Filter werden durch einen Badge am Filter-Icon angezeigt
- Filterlogik bleibt in `RecipeListViewModel` (bestehender `selectedTag`-State auf `Set<String>` erweitern)

---

### 8 – KI-Klassifizierung deaktivieren wenn Klassifizierung bereits vorhanden

**Ziel:** Wenn ein Rezept bereits manuell oder per KI klassifiziert wurde (Tags gesetzt), soll die automatische KI-Klassifizierung nicht erneut ausgeführt werden.

**Umsetzung:**
- Vor dem Aufruf der KI-Klassifizierung prüfen: `if (recipe.tags.isNotEmpty()) return`
- Option: Checkbox/Toggle in `RecipeDetailScreen` → „KI-Klassifizierung erlauben" (überschreibt das Flag)

---

## Einkaufen

### 9 – „Nicht zugewiesen" als Standardgang

**Problem:** Items ohne Gangzuweisung tauchen nicht klar gruppiert auf.

**Umsetzung:**
- Items ohne `aisleId` werden unter einer virtuellen Gruppe „Nicht zugewiesen" gelistet
- Diese Gruppe erscheint ans Ende der Liste (niedrigste Sortierpriorität)
- Kein neuer DB-Eintrag nötig – rein UI-seitige Gruppierung:

```kotlin
val grouped = items.groupBy { it.aisleName ?: "Nicht zugewiesen" }
```

---

### 10 – Emoji-Buttons: nur Emoji, kein Text

**Fix:** In der `QuickEmojiRow` den Beschriftungstext entfernen – nur das Emoji-Symbol anzeigen.

```kotlin
// Vorher:
Text("${emoji.symbol} ${emoji.label}")
// Nachher:
Text(emoji.symbol)
```

---

### 11 – Emoji-Buttons scrollbar prüfen

Prüfen ob `QuickEmojiRow` bei vielen Emojis korrekt horizontal scrollt (`LazyRow` oder `Row` mit `horizontalScroll`). Falls nicht: auf `LazyRow` umstellen.

---

### 12 – Vorrat direkt in Einkaufsliste laden

**Ziel:** Ein Button (z.B. in der TopAppBar oder als FAB-Erweiterung) lädt alle aktiven Vorrats-Staples direkt in die aktuelle Einkaufsliste.

**Umsetzung:**
```kotlin
// ShoppingListViewModel
fun loadStaplesToList(listId: String) {
    viewModelScope.launch {
        val staples = stapleDao.getAllActive()
        staples.forEach { staple ->
            shoppingRepository.addOrMergeItem(listId, staple.name, staple.quantity, staple.unit, staple.aisleId)
        }
    }
}
```
Button: Icon `Icons.Filled.Inventory` oder ähnliches in der TopAppBar der `ShoppingListScreen`.

---

### 13 – Herkunfts-Tag: Vorrat / Rezept / Manuell

**Ziel:** Hinzugefügte Items zeigen einen kleinen Chip/Tag der Herkunft:
- `Vorrat` – aus dem Vorratsstapel geladen
- Rezeptname – aus einem Rezept exportiert
- Kein Tag – manuell hinzugefügt

**Umsetzung:**
- `ShoppingItemEntity` um Feld `source: String?` erweitern (DB-Migration nötig)
  - Werte: `null` (manuell), `"staple"`, `"recipe:<rezeptname>"`
- `ShoppingItemRow` zeigt kleinen `AssistChip` oder `Badge` wenn `source != null`

---

### 14 – Abhak-Modus als Einstellung

**Ziel:** Nutzer kann in den Einstellungen wählen:
- **Modus A (Standard):** Abgehakte Items bleiben in der Liste stehen. Button „Erledigte löschen" vorhanden.
- **Modus B:** Abgehakte Items wandern sofort in eine „Abgehakt"-Sektion. Statt „Erledigte löschen" gibt es einen Button „Einkauf beenden" (oben links neben den drei Punkten), der alle abgehakten Items endgültig entfernt.

**Umsetzung:**
- Neue Preferences-Einstellung `checkMode: KEEP | MOVE`
- `ShoppingListViewModel` reagiert auf die Einstellung
- Im MOVE-Modus: `LazyColumn` mit zwei Sektionen (Offen / Abgehakt)
- „Einkauf beenden"-Button: `IconButton` in `TopAppBar` (nur sichtbar wenn `checkMode == MOVE`)

---

### 15 – Vorschläge beim Vorrat-Hinzufügen

Beim Hinzufügen eines Artikels im Vorratsstapel soll während der Eingabe Autocomplete greifen – analog zur `QuickAddBar` in der Einkaufsliste. Bestehende `SuggestionsApi`/`SuggestionsRepository` nutzen.

---

## Wochenplanung

### 16 – Vorlage-Feature entfernen

Das Template-/Vorlagefeature aus dem Wochenplan entfernen (UI-Einstiegspunkte und ggf. ViewModel-Methoden). DB-Tabellen können vorerst bestehen bleiben (kein Breaking Change).

---

### 17 – Extrazeile mit Autocomplete

**Ziel:** Das Textfeld „Extrazeile hinzufügen" soll während der Eingabe Vorschläge anzeigen (Items, Mengen, Einheiten).

**Umsetzung:** Autocomplete-Dropdown analog zur `QuickAddBar` implementieren. Bestehende Suggestions-Logik wiederverwenden. Mengenangaben wie „500 g" oder „1 Stück" direkt parsen und übernehmen.

---

### 18 – Rezeptbilder im Wochenplan größer

Die Rezeptbilder in den Tageskarten der `WeekplanScreen` sollen größer dargestellt werden. Konkrete Zielgröße: mind. `80.dp` Höhe statt aktuellem Wert.

---

### 19 – KI-Wochenplan-Button entfernen

Den Button „KI-Wochenplan erstellen" aus der `WeekplanScreen`-TopAppBar entfernen. Zugehörige ViewModel-Logik und Server-Endpunkt bleiben erhalten (kein Breaking Change).

---

## Generell

### 20 – Autocomplete bei allen Artikeleingaben

**Ziel:** Überall wo ein Artikel/Lebensmittel eingegeben wird, erscheinen während der Eingabe Vorschläge. Mengenangaben (z.B. `1 Stück`, `500 g`, `400 g`) werden direkt erkannt und in Menge + Einheit + Name aufgeteilt.

**Betrifft:**
- `QuickAddBar` in `ShoppingListScreen` ✅ (bereits vorhanden)
- Vorratsstapel: neues Item hinzufügen (siehe #15)
- Wochenplan: Extrazeile hinzufügen (siehe #17)
- `RecipeFormScreen`: Zutateneingabe

**Parser-Logik (zentral):**
```kotlin
// Beispiel: "500 g Mehl" → Quantity(500.0, "g", "Mehl")
val QUANTITY_REGEX = Regex("""^(\d+(?:[.,]\d+)?)\s*(g|kg|ml|l|Stück|EL|TL|Prise|Bund|Dose)?\s+(.+)$""", RegexOption.IGNORE_CASE)
```
Diese Logik in ein gemeinsames `QuantityParser`-Utility extrahieren und an allen Eingabefeldern verwenden.

---

## Empfohlene Umsetzungsreihenfolge

| Priorität | Task | Aufwand |
|-----------|------|---------|
| 1 | Bug 1: Tab-Wechsel | klein |
| 2 | Bug 2: Bilder anzeigen | klein–mittel |
| 3 | #5 Schritte ab 1 | trivial |
| 4 | #10 Emoji ohne Text | trivial |
| 5 | #16 Vorlage entfernen | trivial |
| 6 | #18 Bilder größer | trivial |
| 7 | #19 KI-Wochenplan entfernen | trivial |
| 8 | #3 Plus-Button in Liste | klein |
| 9 | #4 Rezeptname Marquee | klein |
| 10 | #9 Nicht-zugewiesen-Gang | klein |
| 11 | #11 Emoji scrollbar | klein |
| 12 | #15 Vorschläge Vorrat | klein |
| 13 | #6 Alle Schritte + abhaken | mittel |
| 14 | #7 Tag-Filtermenü | mittel |
| 15 | #8 KI-Klassifizierung Guard | klein |
| 16 | #12 Vorrat → Liste laden | mittel |
| 17 | #17 Extrazeile mit Autocomplete | klein |
| 18 | #20 Autocomplete überall | mittel |
| 19 | #13 Herkunfts-Tag | mittel (DB-Migration) |
| 20 | #14 Abhak-Modus Einstellung | mittel |
