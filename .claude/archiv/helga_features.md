> **ARCHIVIERT am 2026-08-22 — nicht als Referenz verwenden.**
>
> Diese Datei beschreibt die **Flask-Vorgänger-App** (siehe „Original-Stack" unten), nicht die
> Android-App. Sie war Migrationsvorlage und wurde nie auf den Android-Stand nachgezogen.
> Abweichungen zum heutigen Code: Kassenbon-Scan, Preisverlauf, Nährwerte/OpenFoodFacts,
> Allergene, Barcode, Statistik, Widget, Wear OS, Onboarding, Wochenplan-Vorlagen sowie
> Rezept-Historie und -Feedback fehlen hier vollständig; der aufgeführte **Mealie-Import**
> existiert umgekehrt weder in der App noch im Server.
>
> Aktuelles Inventar: [`.claude/features/README.md`](../features/README.md)

---

# Helga – Feature-Übersicht für Android-App

**App-Zweck:** Self-hosted Rezeptverwaltung mit KI-gestützter Rezepterstellung, Wochenplanung und intelligentem Einkaufslistenmanagement.

**Original-Stack:** Flask/Python, SQLite, Jinja2-Templates, Vanilla JS, Docker  
**Sprache:** Deutsch (alle UI-Texte auf Deutsch)

---

## Kern-Datenmodell

| Entity | Felder |
|--------|--------|
| `Rezept` | Name, Beschreibung, Bild, Quelle-URL, Bewertung (1-5), Prep-/Koch-/Gesamtzeit, Portionen |
| `Zutat` | Menge, Einheit, Lebensmittel (verknüpft mit Rezept) |
| `Zubereitungsschritt` | Text, Reihenfolge (verknüpft mit Rezept) |
| `Tag / Kategorie` | Freitexttag pro Rezept |
| `Klassifikation` | Proteintyp (Fleisch/Fisch/Vegetarisch/Vegan), Aufwand (Schnell/Mittel/Aufwendig), Küche |
| `Wochenplan` | Tag → Rezepte, Notizen, Extra-Items |
| `Einkaufsliste` | Name, Items (Menge/Einheit/Lebensmittel/Gang/Erledigt) |
| `Markt` | Name, Gangreihenfolge |
| `Gangzuordnung` | Lebensmittel → Gang je Markt |
| `Vorratsstapel` | Häufige Grundzutaten je Liste |
| `Einstellungen` | KI-Anbieter/Modell, Wochenstart, Einkaufstag |

---

## Feature-Bereiche

### 1. Rezept-Bibliothek

| Feature | Beschreibung |
|---------|--------------|
| **Rezeptliste** | Übersicht aller Rezepte mit Thumbnail, Kochzeit und Tags. Filterung nach Tags, Sortierung nach Name/Bewertung. |
| **Rezept-Detailansicht** | Vollständige Anzeige mit Zutaten, Schritten, Metadaten, Bewertung und Quell-Link. |
| **Manuell erstellen** | Formular zur manuellen Eingabe von Rezept, Zutaten, Schritten, Klassifikation, Tags und Bild. |
| **Bearbeiten** | Vollständiges Editformular für alle Rezeptdaten. |
| **Löschen** | Rezept inkl. aller verknüpften Daten entfernen. |
| **URL-Import** | Rezept von einer URL einlesen (Web Scraping), Vorschau und Bearbeitung vor dem Speichern. |
| **Bewertung** | 1–5 Sterne Bewertung, direkt in der Detailansicht setzbar. |
| **Kochansicht** | Schritt-für-Schritt Anzeige mit abhakbaren Schritten zur Fortschrittsverfolgung. |
| **Bildverwaltung** | Bilder beim Erstellen/Bearbeiten hochladen, lokal gespeichert und angezeigt. |

---

### 2. KI-Rezepterstellung

| Feature | Beschreibung |
|---------|--------------|
| **Rezept generieren** | Aus einem Freitext-Prompt ein vollständiges Rezept per KI erstellen (OpenAI oder Anthropic). |
| **Rezept remixen** | Variation eines bestehenden Rezepts anhand eines Änderungsprompts generieren. |
| **Vorschau & Speichern** | KI-Ergebnis vor dem Speichern anzeigen, erneut generieren oder verwerfen. |
| **KI-Klassifikation** | Rezepte automatisch in Proteintyp, Aufwand und Küche klassifizieren. Auch als Massenoperation für unkategorisierte Rezepte. |
| **Modell-Auswahl** | OpenAI (GPT-4o, GPT-4o-mini) und Anthropic (Claude Opus/Sonnet/Haiku) wählbar. |

---

### 3. Wochenplanung

| Feature | Beschreibung |
|---------|--------------|
| **Wochenplan-Ansicht** | Kalenderartige Ansicht für 1–14 Tage; Rezepte per Drag-and-Drop in Tage einteilen. |
| **Rezept zuweisen** | Rezepte einem Tag hinzufügen, entfernen oder zwischen Tagen verschieben. |
| **Tagesnotizen** | Freitext-Notizen pro Tag (z. B. „Gäste kommen"). |
| **Extra-Items** | Freie Einträge pro Tag außerhalb von Rezepten (fließen in Einkaufsliste ein). |
| **KI-Planerstellung** | KI wählt Rezepte unter Berücksichtigung von Einschränkungen: max. Fleischmahlzeiten, min. Fisch, min. Vegetarisch, max. Aufwendige, min. Schnelle. Berücksichtigt die letzten 4 Wochen zur Vermeidung von Wiederholungen. |
| **Einkaufslisten-Vorschau** | Vor dem Abschluss des Plans alle benötigten Zutaten (zusammengeführt über alle Tage) vorab anzeigen. |
| **Plan übernehmen** | Finalisierten Wochenplan in Einkaufsliste umwandeln (Zutaten je Markt/Gang eingetragen). |

---

### 4. Einkaufsliste

| Feature | Beschreibung |
|---------|--------------|
| **Listenansicht** | Items gruppiert nach Markt-Gang mit Checkbox; erledigte Items separat angezeigt. |
| **Mehrere Listen** | Benannte Einkaufslisten erstellen (z. B. „Wocheneinkauf", „Sonntagsmarkt"); zwischen Listen wechseln. |
| **Schnell hinzufügen** | Items per Kurzschreibweise eingeben: „200g Tomate", „2x Brot"; wird in Menge/Einheit/Lebensmittel geparst. |
| **Zutaten aus Rezept** | Rezept direkt zur aktiven Einkaufsliste hinzufügen (Zutaten werden extrahiert). |
| **Item-Verwaltung** | Items abhaken (erledigt), löschen, Menge/Einheit/Lebensmittel inline bearbeiten. |
| **Gangzuordnung** | Produkte werden automatisch dem bekannten Gang zugeordnet; manuelle Korrekturen werden für zukünftige Einkäufe gespeichert. |
| **Unzugeordnete Items** | Items ohne Gangzuordnung in separater Sektion; Tippen öffnet Dialog zur Gang-Zuweisung (wird gemerkt). |
| **Erledigte löschen** | Alle abgehakten Items in einem Schritt entfernen. |
| **Vorratsstapel** | Häufig gekaufte Grundzutaten je Liste konfigurieren; per Knopfdruck alle Stapel zur Liste hinzufügen. |
| **Emoji-Schnellbuttons** | Konfigurierbare Emoji-Buttons für häufig gekaufte Einzelitems (z. B. 🥛, 🍅); ein Tippen fügt das Item hinzu. |

---

### 5. Markt-Verwaltung

| Feature | Beschreibung |
|---------|--------------|
| **Märkte anlegen** | Mehrere Märkte mit eigenem Namen konfigurieren (z. B. Rewe, Aldi). |
| **Gangreihenfolge** | Benutzerdefinierte Reihenfolge der Gänge pro Markt; Einkaufsliste sortiert sich entsprechend. |
| **Gangzuordnungen** | Produkt-zu-Gang-Zuordnungen werden automatisch gelernt und manuell korrigierbar gespeichert. |

---

### 6. Mealie-Import

| Feature | Beschreibung |
|---------|--------------|
| **Mealie-Verbindung** | Optionale Verbindung zu einer externen Mealie-Instanz via URL + Token. |
| **Rezept-Import** | Alle Mealie-Rezepte auflisten; bereits importierte markiert; Massenimport per Streaming. |
| **KI-Normalisierung** | Jedes importierte Rezept wird per KI normalisiert: Zutaten strukturiert, Schritte formatiert, Tags vorgeschlagen. |

---

### 7. Einstellungen

| Feature | Beschreibung |
|---------|--------------|
| **KI-Konfiguration** | Anbieter (OpenAI/Anthropic), API-Key und Modell auswählen. |
| **Wochenplan-Einstellungen** | Wochenstartag und Einkaufstag festlegen. |
| **Markt-Einstellungen** | Märkte anlegen/entfernen, Gangreihenfolge konfigurieren. |
| **Einkaufslisten-Einstellungen** | Listen erstellen/löschen; Standard-Liste für „Rezept hinzufügen" und „Wochenplan übernehmen" setzen. |
| **Emoji-Schnellbuttons** | Benutzerdefinierte Schnellbuttons erstellen, bearbeiten und löschen. |

---

## KI-Einschränkungen / Wochenplan-Constraints

Die KI-Wochenplanung berücksichtigt konfigurierbare Grenzen:
- Max. Fleischmahlzeiten pro Woche
- Min. Fischmahlzeiten pro Woche
- Min. vegetarische Mahlzeiten pro Woche
- Max. aufwendige Rezepte pro Woche
- Min. schnelle Rezepte pro Woche
- Verlauf der letzten 4 Wochen (Wiederholungen vermeiden)

---

## Autocomplete / Vorschläge

- **Produktname-Autocomplete** beim Hinzufügen von Items
- **Gang-Vorschlag** basierend auf Kaufhistorie
- **Einheiten-Vorschlag** basierend auf bekannten Produkten
