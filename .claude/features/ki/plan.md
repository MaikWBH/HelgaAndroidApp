# Feature: KI

> **Status:** Interview erledigt · **Aufgaben:** 2 offen (2 erledigt) · **Stand:** 2026-08-31 · **Priorität:** ⭐⭐

Rezeptgenerierung, Remix und Klassifikation. Läuft ausschließlich serverseitig; die App sendet
Prompts und empfängt Ergebnisse per SSE-Streaming.

## Umfang

| Ebene | Dateien |
|-------|---------|
| UI | `app/src/main/kotlin/com/helga/android/ui/ai/AiGenerateScreen.kt`, `AiGenerateViewModel.kt`, `AiRemixScreen.kt`, `AiRemixViewModel.kt`, `RecipeJsonLdParser.kt` |
| Netzwerk | `app/src/main/kotlin/com/helga/android/data/remote/SseClient.kt` |
| Server | `server/app/ai.py`; Endpunkte `/api/ai/generate`, `/api/ai/remix`, `/api/ai/classify` in `server/app/main.py` |

Angrenzend, aber in eigenen Bereichen geführt: `/api/ai/parse-receipt` (Bons & Kosten),
`/api/ai/nutrition` (Nährwerte), `/api/ai/import-url` (Rezepte), KI-Wochenplanung (Wochenplan).

## Ist-Analyse

- **Generierung** (`AiGenerateViewModel`): Freitext-Prompt plus strukturierte Vorgaben —
  Küche (`setCuisine`), Ernährungsform (`setDietType`), Aufwand (`setEffort`), Kochzeit
  (`setCookTime`), Besonderheiten (`setSpecial`). Daraus baut `buildCustomInstructions` die
  Anweisung an das Modell.
- **Ablauf:** Streaming über `SseClient`, Vorschau vor dem Speichern, `regenerate` für einen
  neuen Versuch, `discardPreview` zum Verwerfen, `save` legt das Rezept an.
- **Rückmeldung:** `setFeedback`, `showFeedback`, `hideFeedback` — Bewertung des KI-Ergebnisses.
- **Remix** (`AiRemixViewModel`): Variation eines bestehenden Rezepts anhand eines
  Änderungsprompts.
- **Klassifikation:** einzeln aus der Rezeptdetailansicht (`classify`) und als Massenlauf über
  alle unklassifizierten Rezepte (`classifyBatch`, `BulkClassifyDialog`); zusätzlich
  `runBulkAi` in den Einstellungen.
- **Modellwahl und Schlüssel** liegen in den Einstellungen (`setApiKey`, Anbieterauswahl).

## Bekannte Lücken

### Funktion & UX
**Root Cause zu „Einheiten teilweise falsch/leer" (aus dem Interview):** Generierte
Zutatenzeilen (`recipeIngredient` als Freitext, z. B. `"200g Mehl"`) laufen durch
`IngredientLineParser.parse()` (`app/src/main/kotlin/com/helga/android/data/util/
IngredientLineParser.kt`). Der Parser erkennt Einheiten nur über eine feste Klartext-Tabelle
(`UNIT_CANONICAL`, ~20 Einträge) und Mengen nur als Dezimalzahl oder einfachen Bruch mit
Ziffern (`QUANTITY_RE`). Schreibt das Modell eine Einheit, die nicht in der Tabelle steht, oder
eine Menge als Unicode-Bruch (½, ¼) oder Bereich in Worten, bleibt die Einheit leer bzw. die
komplette Zeile landet unverarbeitet im `food`-Feld. Eine reine Kopfzeile wie „Für den Teig:"
wird ebenfalls als Zutat mit Menge 0 durchgereicht. Kein Fallback, keine Normalisierung —
passend zum geschilderten Bild (Zeile ohne erkennbare Einheit oder scheinbar leer).

### Code-Qualität
Keine `!!`-Zugriffe, keine `items()`-Verstöße in diesem Bereich.

### Funktion & UX
`RecipeJsonLdParser` wird in `AiGenerateViewModel.kt:91` und `AiRemixViewModel.kt:102` genutzt,
im URL-Import-Pfad dagegen nicht — dort läuft alles serverseitig ohne Fallback. Ein fertiger
Client-Parser liegt damit ungenutzt herum; mögliche Wiederverwendung siehe
[rezepte](../rezepte/plan.md) A10.

### Tests
Keine. `RecipeJsonLdParser` ist reine Parselogik und unmittelbar testbar. Für `SseClient` wäre
ein Test gegen einen Fake-Stream sinnvoll — Abbruch mitten im Stream ist der Fehlerfall, der im
Alltag auftritt.

### Sync
Keine eigenen Entities. Erzeugte Rezepte laufen über den Rezept-Sync.

## Fragen

1. **Wie oft ist ein generiertes Rezept ohne Nacharbeit brauchbar?**
   Antwort: Meistens direkt brauchbar. Problem liegt bei den Zutaten: Einheiten passen teils
   nicht oder fehlen ganz — Zeile zeigt nur Zahl ohne erkennbaren Rest. Siehe Root Cause oben
   (`IngredientLineParser`).
2. **Nutzt du die strukturierten Vorgaben (Küche, Diät, Aufwand, Zeit, Besonderheit), oder
   tippst du meist frei?**
   Antwort: Meistens Freitext, Küche gelegentlich genutzt.
3. **Was passiert bei Stream-Abbruch?**
   Antwort: Noch nie erlebt — kein persönlicher Erfahrungswert, bleibt als technische Aufgabe
   (A2) bestehen, da der Code-Pfad ungetestet ist.
4. **Remix: genutzt oder vergessenes Feature?**
   Antwort: Regelmäßig genutzt — aktives Kernfeature, beim Verbessern der Generierung
   mitdenken.
5. **Modellwahl je Aufgabe oder global?**
   Antwort: Global reicht, keine Änderung.
6. **Beeinflusst das Feedback künftige Prompts, oder ist es nur Protokoll?**
   Antwort: War nicht bekannt, dass es die Funktion gibt — geringe Priorität, keine Aufgabe
   daraus.
7. **Fehlt ein Prompt-Verlauf?**
   Antwort: Nicht nötig.
8. **Soll die App deutlicher anzeigen, wenn ohne Server keine KI-Funktion möglich ist?**
   Antwort: Ja, ausdrücklich — soll grundsätzlich immer sichtbar anzeigen, wenn eine Funktion
   den Server braucht und dieser gerade nicht erreichbar ist. Aktuell existiert dafür keine
   Mechanik im Code (`grep` nach `isServerReachable`/`ServerStatus` u. ä. ohne Treffer) — der
   Fehler zeigt sich bisher erst nach einem gescheiterten Versuch (`AiGenerateStatus.Error`).

## Ziele

- Server-Erreichbarkeit für KI-Funktionen proaktiv anzeigen, bevor Zeit in einen Prompt
  investiert wird — nicht erst nach einem gescheiterten Versuch.
- Zutatenzeilen aus der KI-Generierung zuverlässiger in Menge/Einheit/Lebensmittel zerlegen;
  unbekannte Einheiten und Sonderfälle dürfen nicht zu leeren/unbrauchbaren Zeilen führen.
- Remix als aktiv genutztes Kernfeature bei künftigen Verbesserungen mitdenken, nicht als
  Nebenfunktion behandeln.
- Modellwahl, Prompt-Verlauf und Feedback-gesteuerte Prompt-Anpassung bleiben unverändert — im
  Interview kein Bedarf geäußert.

## Backlog

Aufwand: S (< 1 h) · M (halber Tag) · L (mehrere Tage)

- [ ] **A1** — Unit-Tests für `RecipeJsonLdParser` · M · Impact mittel — deckt auch den
  Parser-Teil ab, der zuvor doppelt in [rezepte](../rezepte/plan.md) A3 stand
- [x] **A2** — Verhalten bei Stream-Abbruch prüfen und absichern · M · Impact hoch —
  **umgesetzt:** `SseClient.kt` trackt jetzt, ob `[DONE]` tatsächlich empfangen wurde; bricht
  die Verbindung vorher ab (Server-Timeout, Netzwerkabbruch), wirft `collect()` jetzt
  `IOException("Stream wurde vorzeitig beendet")` statt ein still abgeschnittenes Teilergebnis
  zurückzugeben. `AiGenerateViewModel`/`AiRemixViewModel` zeigen `e.message` bereits unverändert
  an — die neue Meldung kommt dadurch ohne weitere Anpassung im UI an.
- [ ] **A3** — Server-Erreichbarkeit vor KI-Nutzung deutlich anzeigen (Generieren, Remix,
  Klassifikation): Reachability-Check + persistenter Hinweis, statt den Fehler erst nach einem
  gescheiterten Versuch zu zeigen. Kein bestehender Mechanismus im Code — neue Infrastruktur
  nötig, potenziell auch für [sync](../sync/plan.md) relevant · L · Impact hoch
- [x] **A4** — `IngredientLineParser` robuster für KI-generierte Zutatenzeilen machen
  (unbekannte Einheiten nicht verwerfen, Unicode-Brüche wie ½/¼ erkennen, reine Kopfzeilen wie
  „Für den Teig:" nicht als Zutat durchreichen) · M · Impact hoch — Testabdeckung dafür bereits
  in [einkaufsliste](../einkaufsliste/plan.md) A3 vorgemerkt — **umgesetzt:** unbekannte
  Einheiten wurden beim Nachgehen bereits nicht verworfen (Rest der Zeile blieb immer als
  `food` erhalten, verifiziert statt angenommen). Neu: `UNICODE_FRACTION_RE` erkennt ½/¼/¾ und
  die übrigen gängigen Unicode-Bruchzeichen, auch mit vorangestellter Ganzzahl ("1½"); neue
  `isHeaderLine()`-Funktion erkennt Kopfzeilen (kein Ziffernzeichen **und** endet auf „:" oder
  komplett in Markdown-Fettschrift), gefiltert vor `mapIndexed` in `AiGenerateViewModel.kt` und
  `AiRemixViewModel.kt`. Beide Fixes zusätzlich nach `server/app/ingredient_parser.py` gespiegelt
  (dessen Docstring verlangt Parität mit `IngredientLineParser.kt`) und im URL-Import
  (`ai.py:import_url`) angewendet.

_Weitere Aufgaben nach dem Interview._

## Entscheidungen

| Datum | Entscheidung | Begründung |
|-------|--------------|------------|
| 2026-08-30 | Server-Erreichbarkeit wird proaktiv angezeigt, nicht erst nach Fehlschlag | Ausdrücklicher Nutzerwunsch, aktuell keine Reachability-Mechanik im Code vorhanden |
| 2026-08-30 | Modellwahl bleibt global | Kein Bedarf für Aufgaben-spezifische Modelle geäußert |
| 2026-08-30 | Prompt-Verlauf und Feedback-Wirkung auf künftige Prompts werden nicht umgesetzt | Kein Bedarf, Feedback-Funktion war dem Nutzer nicht mal bekannt |
