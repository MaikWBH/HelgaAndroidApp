# Wochenplan — Optimierungs-Ideen

Analyse des aktuellen Wochenplan-Workflows in RocksRecipes und priorisierte Vorschläge,
um Geschwindigkeit, Auto-Generierung und ernährungsphysiologische Balance zu verbessern.

---

## Status quo

- **Plan-Generierung** macht heute zwei Dinge: `random.sample` aus Schlagwortfilter,
  oder pro Tag/Kategorie ein Zufalls-Rezept (`app.py:1031` `weekplan_generate_all`)
- **Keine Klassifikation** der Rezepte über `tags`/`categories` hinaus —
  Felder wie „Fleisch/Vegetarisch/Fisch", „schnell/aufwendig", „Cuisine" gibt es nicht
- **Kein Verlauf**: `last_planned_at`, `planned_count` werden nirgends gespeichert
  → der Generator kann nicht erkennen, was die letzten Wochen schon dran war
- **Kein Constraint-System**: User trägt manuell Zahlen in Kategorien ein und hofft auf Balance
- **Navigation**: „Rezept hinzufügen" springt auf `/recipes` und zurück — viele Klicks

---

## Vorschläge (geordnet nach Wirkung/Aufwand)

### Tier 1 – Fundament für intelligente Pläne

#### 1. Auto-Tagging der Rezeptbibliothek

Neue Spalten in `recipes`:

- `protein_type` (fleisch / fisch / vegetarisch / vegan)
- `effort` (schnell / mittel / aufwendig)
- `cuisine` (deutsch / italienisch / asiatisch / mexikanisch / …)
- `meal_type` (pasta / eintopf / ofen / salat / suppe / grill / …)
- `season_fit` (winter / sommer / ganzjährig)

**Ohne diese Daten kann kein Algorithmus „balanciert" planen.**

##### Tagging-Strategie je Rezeptquelle

Da ständig KI über jedes neue Rezept laufen zu lassen unwirtschaftlich ist,
wird je nach Herkunft unterschiedlich getaggt:

**Bestand (einmalig):** Ein Bulk-Endpoint klassifiziert alle vorhandenen
Rezepte in einem Rutsch per AI. Danach nie wieder nötig.

**KI-generierte Rezepte:** Die 5 Klassifikations-Felder werden direkt in den
Generierungs-Prompt eingebaut. Die KI liefert sie im JSON-LD mit zurück —
kein Extra-Aufruf, kein Mehraufwand.

**Importierte Rezepte (URL / Mealie):** Im Preview-Schritt nach dem Import
erscheinen 5 Dropdowns. Der User wählt optional in ~10 Sekunden aus.
Alle Felder `NULL`-fähig, kein Pflichtfeld.

**Manuell erstellte Rezepte:** Dieselben 5 Dropdowns werden in
`recipe_form.html` ergänzt — direkt beim Anlegen befüllbar.

**On-Demand-Button:** Auf der Rezept-Detailseite ein kleiner „Auto-Tag"-Button,
der sichtbar ist, wenn alle 5 Felder leer sind. Triggert einen einzelnen
AI-Call für genau dieses Rezept — bewusste User-Entscheidung, kein Automatismus.

#### 2. Smart-Plan-Generator mit Constraints

Statt purem Random ein einfacher Greedy-Solver, der Regeln einhält:

- max. 2× Fleisch, min. 2× Vegetarisch, min. 1× Fisch
- max. 1× aufwendig, min. 3× schnell
- keine zwei gleichen Cuisines hintereinander

Constraints sind in einem **Profil** gespeichert (z. B. „Familienwoche")
und mit einem Klick anwendbar.

#### 3. Rotations-Tracking

Neue Tabelle `recipe_history(recipe_id, planned_date)`. Generator vermeidet
Rezepte aus den letzten 3–4 Wochen automatisch und bevorzugt lange ungekochte.
Löst das „immer dasselbe"-Problem von selbst.

---

### Tier 2 – Geschwindigkeit

#### 4. Ein-Klick „Klassische Woche"

Ein dicker Button auf der Wochenplan-Seite, der direkt das Default-Profil +
Smart-Generator anwendet. Erstes echtes „in 1 Sekunde steht ein sinnvoller Plan".

#### 5. „Worauf hast du Lust?"-Anker-Auswahl

Beim Generieren: 6–8 zufällige Rezeptkarten anzeigen, User pickt 2–3 als Anker,
Generator füllt um diese herum balanciert auf. Löst das „weiß nicht, worauf ich
Lust habe"-Problem mit visuellem Anstoß statt leerer Schlagwort-Box.

#### 6. Side-Panel mit Rezeptbibliothek auf der Wochenplan-Seite

Filterbare Liste rechts/unten, von dort direkt per Drag aufs Datum.
Spart die Navigation auf `/recipes` und zurück.

---

### Tier 3 – Kontext & Komfort

#### 7. „Vorherige Woche kopieren"

Button „Letzte Woche übernehmen" oder „Plan von Woche XY laden" —
80/20-Lösung für sich wiederholende Familienwochen.

#### 8. Tages-Kontext-Flags

Pro Tag ein optionales Flag „schnell heute" oder „Gäste".
Generator beachtet das (z. B. Mittwoch Sport → schnelles Rezept).

#### 9. Mini-Feedback nach der Woche

Am Wochenende inline pro Tag „War gut? 👍/👎". Erhöht/senkt einen leichten Score
→ fließt in zukünftiges Auto-Planen ein.

---

### Tier 4 – AI als echter Co-Pilot

#### 10. AI-Wochenplan-Assistent

Endpoint, der Rezept-Summary + Constraints + Saison + Wochenkontext an die AI
gibt und 7 Slugs mit kurzer Begründung zurückbekommt
(„Dienstag Linseneintopf weil Reste am Mittwoch verwertbar").
Teurer als Greedy, aber bei Bedarf.

---

## Empfehlung für die erste Iteration

**Pakete 1 + 2 + 3 zusammen** lösen das Hauptproblem komplett:

- AI klassifiziert einmalig die Bibliothek
- Smart-Generator nutzt Klassifikation + Historie
- „Klassische Woche"-Button generiert in 1 Klick einen balancierten,
  abwechslungsreichen Plan

Pakete 5 (Anker-Auswahl) und 7 (Vorwoche kopieren) sind danach kleine Add-ons
mit hohem Comfort-Gewinn.

---

## Relevante Code-Stellen für die Umsetzung

| Bereich | Datei / Zeile |
|---|---|
| Plan-Generator (Random/Kategorie) | `app.py:1031` `weekplan_generate_all` |
| Plan-Anzeige Route | `app.py:723` `weekly_plan` |
| Rezepte-Schema | `db_client.py:112` `CREATE TABLE recipes` |
| Wochenplan-Schema | `db_client.py:258` `CREATE TABLE weekplan_days` |
| Recipe-Summary für Generator | `db_client.py:420` `get_all_recipes_summary` |
| Wochenplan-Template | `templates/weekly_plan.html` |
| Generate-UI | `templates/weekly_plan.html:106-167` |
| AI-Generator (für Auto-Tagging) | `ai_generator.py:72` `normalize_recipe` als Vorlage |
---

## Umsetzungs-Status (Stand: 2026-04-22)

### Implementiert (Iteration 1 — Pakete 1 + 2 + 3)

**Phase A — Fundament**
- `db_client.py` Tabelle `recipe_history` + 5 neue Spalten auf `recipes`
  (`protein_type`, `effort`, `cuisine`, `meal_type`, `season_fit`)
- Zentrale Wertelisten in `DatabaseClient.CLASSIFICATION_VALUES`
- Helper: `update_recipe_classification`, `get_recipes_without_classification`,
  `record_planned_recipes`, `get_recently_planned_recipe_ids`
- `ai_generator.classify_recipe()` — JSON-Response mit 5 Feldern

**Phase B — UI für Tagging**
- `recipe_form.html` — 5 Dropdowns (Create / Edit / URL-Import-Preview)
- `recipe_detail.html` — Klassifikations-Badges + Auto-Tag-Button (AI) wenn leer
- `recipes.html` — „Alle klassifizieren"-Button (Bulk)
- Endpoints: `POST /recipes/classify-all`, `POST /recipes/<slug>/classify`

**Phase C — KI-Prompt erweitert**
- `_build_system_prompt` fordert `rocks_protein_type` etc. im JSON-LD an
- Parser in `import_recipe_from_html` übernimmt sie direkt beim Import

**Phase D — Smart-Generator + Rotation**
- Neues Modul `smart_planner.py` mit Greedy-Scoring
  (Quoten, History-Malus, Cuisine-Rotation, Rating-Tiebreaker)
- Routen: `POST /weekly-plan/generate-classic` (Ein-Klick),
  `POST /weekly-plan/generate-smart` (Constraint-Overrides im Formular)
- `weekly_plan.html` — „Klassische Woche"-Button + neuer „Smart"-Tab
- `_persist_full_plan` / `_persist_plan_day` schreiben in `recipe_history`
  → Smart-Generator vermeidet Rezepte der letzten 4 Wochen

---

### Offen

**Tier 1 — Restarbeiten**
- **Preview-Dropdowns in `preview.html` (AI-Preview):** Nicht ergänzt.
  AI-generierte Rezepte bekommen die Klassifikation stattdessen direkt
  via JSON-LD (`rocks_*`-Felder) aus dem Generator. Manuelle Override-UI
  zum Zeitpunkt der AI-Vorschau fehlt — User muss im Nachgang über das
  Edit-Formular korrigieren.
- **Mealie-Import-Preview:** Import läuft als Bulk-Stream ohne Pro-Rezept-
  Dropdown-Schritt. Nach Import muss „Alle klassifizieren" manuell
  gestartet werden.
- **Constraint-Profile:** Smart-Tab akzeptiert nur Ad-hoc-Overrides;
  benannte Profile („Familienwoche" o. ä.) werden nicht gespeichert.
  Default-Profil fest in `smart_planner.DEFAULT_CONSTRAINTS`.

**Tier 2 — Geschwindigkeit**
- **#5 „Worauf hast du Lust?"-Anker-Auswahl:** Backend vorbereitet
  (`generate_smart_plan(anchor_ids=...)`), UI für Anker-Kachel-Auswahl fehlt.
- **#6 Side-Panel mit Rezeptbibliothek auf Wochenplan-Seite:** Nicht
  umgesetzt. Rezept-Hinzufügen springt weiterhin zu `/recipes` und zurück.

**Tier 3 — Kontext & Komfort**
- **#7 „Vorherige Woche kopieren":** Nicht umgesetzt. Daten sind über
  `weekplan_days` + `weekplan_recipes` grundsätzlich verfügbar —
  bräuchte eine Lade-Route und einen Button.
- **#8 Tages-Kontext-Flags („schnell heute" / „Gäste"):** Nicht umgesetzt.
  Schema und Generator-Logik müssten beide erweitert werden.
- **#9 Mini-Feedback pro Tag (👍/👎):** Nicht umgesetzt. Würde eine
  neue Tabelle `recipe_feedback` und ein Scoring im Generator benötigen.

**Tier 4 — AI-Wochenplan-Assistent**
- **#10 AI-Planer:** Nicht umgesetzt. Smart-Generator deckt den
  80/20-Fall greedy ab; AI-basiert wäre teurer und bisher nicht nötig.

---

### Hinweise zum Betrieb

- Nach Deploy einmal „Alle klassifizieren" laufen lassen — der Greedy-
  Generator degradiert graceful, wenn nicht genug Rezepte klassifiziert
  sind, meldet das aber als Warnung.
- `recipe_history` wird ab jetzt bei jedem Speichern eines Plans befüllt.
  Alt-Pläne vor diesem Deploy tauchen nicht in der History auf
  (Rotations-Filter beginnt ab Heute zu greifen).   