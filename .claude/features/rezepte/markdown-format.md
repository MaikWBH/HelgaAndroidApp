# Markdown-Rezeptformat (Import)

Rezepte werden über **FAB → „Markdown importieren"** aus dem Text einer KI (z. B. Gemini) eingelesen. Die App legt die
KI-Vorlage beim Öffnen des Screens in die Zwischenablage; der Nutzer fügt sie in die KI ein, ergänzt hinter
„Mein Wunsch:" seinen Wunsch, kopiert die Antwort und fügt sie in der App ein. Alles läuft offline in der App
(`RecipeMarkdownParser`, `RecipeMarkdownMapper`); nur die optionale Klassifizierung nach dem Speichern nutzt den Server.

**Einzige Quelle der Vorlage:** `app/src/main/res/raw/recipe_md_prompt.txt`. `RecipePromptSyncTest` parst das Beispiel
der Vorlage mit dem Parser und schlägt fehl, wenn beides auseinanderläuft.

## Format

```markdown
# Titel                         ← Pflicht (erste `# `-Zeile; sonst erste Überschrift, die kein Abschnittsname ist)
Beschreibung als Fließtext oder `> Zitat` (optional)
- Portionen: 4                  ← Schlüsselzeilen: `- K: W`, `**K:** W`, `K: W` (auch `*`/`+` als Marker)
- Vorbereitung / Kochzeit / Gesamtzeit: 15 Min | 1 Std 30 Min | 1,5 h | PT15M
- Küche, Mahlzeit, Aufwand, Eiweiß, Saison, Tags, Nährwerte pro Portion, Notizen
## Zutaten                      ← Abschnitte `##`/`###` oder fette Zeile `**Zutaten:**`
- 200 g Mehl
## Zubereitung
1. Schritt …
## Notizen
Freitext
---                             ← Trenner zum nächsten Rezept (`---`, `***`, `___` oder eine neue `# `-Zeile)
```

Vorverarbeitung: BOM weg, Zeilenenden vereinheitlicht, Zeilen mit Code-Zaun (```` ``` ````) gelöscht.

## Schlüssel (Kopfbereich und Abschnitte `Infos`/`Informationen`/`Details`/`Eckdaten`)

| Schlüssel | Feld | Werte / Regel |
|-----------|------|---------------|
| Portionen, Portion, Personen | `recipeYield` | muss eine Ziffer enthalten, sonst Warnung; Wert unverändert |
| Vorbereitung, Vorbereitungszeit, Zubereitungszeit | `prepTime` | Dauer → ISO-8601 |
| Kochzeit, Garzeit, Backzeit | `cookTime` | Dauer → ISO-8601 |
| Gesamtzeit, Gesamt, Dauer | `totalTime` | Dauer → ISO-8601 |
| Küche | `cuisine` | deutsch, italienisch, asiatisch, mexikanisch, französisch, mediterran, orientalisch, amerikanisch, indisch, sonstige (alles andere → sonstige) |
| Mahlzeit, Slot | `mealSlot` | Frühstück · Mittag/Mittagessen · Abend/Abendessen/Abendbrot · Snack/Zwischenmahlzeit; sonst `other` + Warnung |
| Aufwand, Schwierigkeit | `effort` | schnell/einfach · mittel/mittelschwer · aufwendig/aufwändig/schwer; sonst leer + Warnung |
| Eiweiß, Protein, Proteinquelle | `proteinType` | fleisch, fisch, vegetarisch, vegan; sonst leer + Warnung |
| Saison | `seasonFit` | winter, sommer, ganzjährig (auch „ganzes Jahr"); sonst leer + Warnung |
| Tags, Schlagwörter, Stichwörter | Tags | an `,`/`;` geteilt, `#` entfernt, Dubletten ohne Groß-/Kleinschreibung weg, höchstens 20 |
| Nährwerte (pro Portion) | Nährwerte | `<Zahl> kcal`, `<Zahl> g Eiweiß`, `… g Fett`, `… g Kohlenhydrate` (alle vier nötig, sonst Warnung und keine Nährwerte) |
| Notizen, Notiz, Tipp, Tipps | Notizen | mit Abschnitt „Notizen" zusammengeführt |

Dauern: `15 Min`, `1 Std 30 Min`, `1,5 h`, `PT15M`, `30-40 Min`; nicht Erkanntes → Warnung, Feld bleibt leer.

## Abschnitte

- Zutaten: `Zutaten`, `Zutatenliste`, `Ingredients`. Zeilen mit Marker (`-`, `*`, `+`, `•`, `1.`, `1)`) und optionaler
  Checkbox (`[ ]`/`[x]`); Gruppenüberschriften („Für den Teig:", `###`, fette Zeile) werden verworfen.
- Schritte: `Zubereitung`, `Schritte`, `Anleitung`, `Zubereitungsschritte`, `Instructions`. Neuer Schritt bei `1.`, `1)`,
  `Schritt 1:` oder Spiegelstrich; Zeile ohne Marker wird an den vorigen Schritt gehängt, nach einer Leerzeile beginnt
  sie einen neuen.
- Notizen: `Notizen`, `Notiz`, `Tipps`, `Tipp`, `Hinweise`. Nährwerte: `Nährwerte` (Zeilen werden zusammengeführt).
- Eine `##`-Zeile mit unbekanntem Namen wird samt Inhalt ignoriert (Warnung); `###` und fette Zeilen sind nur bei
  bekanntem Namen ein Abschnitt.

## Grenzen und Fehler

| Fall | Verhalten |
|------|-----------|
| Leere Eingabe | Meldung, keine Vorschau |
| Mehr als 100 000 Zeichen / mehr als 50 Rezepte | Meldung, keine Vorschau |
| Block ohne Titel | Blockfehler; andere Blöcke bleiben |
| Weder Zutaten noch Schritte | Blockfehler `EMPTY_RECIPE` |
| Mehr als 100 Zutaten oder Schritte | Blockfehler `TOO_MANY_LINES` |
| Nur Zutaten oder nur Schritte | Warnung, Rezept bleibt |
| Rezeptname gibt es schon (ohne Groß-/Kleinschreibung) oder steht weiter oben im Text | in der Vorschau abgewählt, mit Hinweis; manuell wieder wählbar |

## Speichern

Pro ausgewähltem Rezept neue UUIDs, `sourceUrl = ""`, Nährwerte je Portion × 4 (`NUTRITION_BASELINE_PORTIONS`) mit
Quelle `ai`. Danach einmal Sync. Rezepte mit leerem Feld (Küche, Aufwand, Eiweiß, Saison oder Mahlzeit „other") werden
höchstens 5 je Import nacheinander klassifiziert (15 s Timeout je Rezept, Fehler still). Genau ein Rezept → Detailansicht,
sonst Rezeptliste.
