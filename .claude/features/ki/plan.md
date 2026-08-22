# Feature: KI

> **Status:** Interview offen · **Aufgaben:** 0/0 · **Stand:** 2026-08-22 · **Priorität:** ⭐⭐

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
Offen bis zum Interview.

### Code-Qualität
Keine `!!`-Zugriffe, keine `items()`-Verstöße in diesem Bereich.

### Tests
Keine. `RecipeJsonLdParser` ist reine Parselogik und unmittelbar testbar. Für `SseClient` wäre
ein Test gegen einen Fake-Stream sinnvoll — Abbruch mitten im Stream ist der Fehlerfall, der im
Alltag auftritt.

### Sync
Keine eigenen Entities. Erzeugte Rezepte laufen über den Rezept-Sync.

## Offene Fragen

1. Wie oft ist ein generiertes Rezept ohne Nacharbeit brauchbar?
2. Die strukturierten Vorgaben (Küche, Diät, Aufwand, Zeit, Besonderheit) — nutzt du alle, oder
   tippst du meist frei?
3. Was passiert heute, wenn der Stream mitten in der Generierung abbricht? Falls unklar: das ist
   selbst eine Aufgabe.
4. Remix: genutzt oder vergessenes Feature?
5. Soll die App das Modell je Aufgabe wählen können (günstig für Klassifikation, stark für
   Generierung), oder bleibt eine globale Einstellung?
6. Das Feedback zu KI-Ergebnissen — soll es die künftigen Prompts beeinflussen, oder ist es nur
   Protokoll?
7. Fehlt ein Verlauf der letzten Prompts zum Wiederverwenden?
8. Ohne Server geht keine KI-Funktion. Soll die App das deutlicher anzeigen, bevor man einen
   Prompt tippt?

## Ziele

_Nach dem Interview zu füllen._

## Backlog

Aufwand: S (< 1 h) · M (halber Tag) · L (mehrere Tage)

- [ ] **A1** — Unit-Tests für `RecipeJsonLdParser` · M · Impact hoch
- [ ] **A2** — Verhalten bei Stream-Abbruch prüfen und absichern · M · Impact hoch

_Weitere Aufgaben nach dem Interview._

## Entscheidungen

| Datum | Entscheidung | Begründung |
|-------|--------------|------------|
