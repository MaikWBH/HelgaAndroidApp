# Iteratives Feature-Planungssystem — Bootstrap-Prompt

> Portables Setup-Prompt für ein Bereich-für-Bereich-Planungssystem, wie es in diesem Projekt
> unter `.claude/features/` läuft. In ein neues Projekt kopieren, den Platzhalter-Block unten
> ausfüllen und als erste Nachricht an Claude schicken (oder als `.claude/CLAUDE.md`-Abschnitt /
> eigene Skill-Datei einbinden). Ergebnis nach Durchlauf aller Phasen: ein Feature-Index, ein
> `plan.md` pro Bereich mit Ist-Analyse/Interview/Backlog, und eine projektweite Roadmap, die
> alle Backlog-Punkte nach Dringlichkeit × Aufwand sortiert.

---

## Ziel & Grundidee

Große "verbessere die App"-Aufträge scheitern, wenn man versucht alles auf einmal zu planen:
der Plan ist entweder zu vage zum Umsetzen oder zu detailliert um noch stimmen. Dieses System
löst das durch zwei Trennungen:

1. **Breite vor Tiefe.** Erst das Produkt in klar abgegrenzte Bereiche zerlegen (z. B. nach
   Modul, Screen-Gruppe oder Domäne). Jeder Bereich bekommt seinen eigenen Plan — Detailarbeit
   passiert dort, nicht in einem einzigen riesigen Dokument.
2. **Ist-Analyse vor Meinung.** Bevor irgendetwas geplant wird, steht fest was der Code **heute
   tatsächlich** tut. Erst danach werden gezielte Rückfragen gestellt — abgeleitet aus Lücken,
   die die Analyse gefunden hat, nicht aus Vermutungen.

Das Ergebnis ist kein Wasserfall-Lastenheft, sondern ein lebendes System: jeder Bereich hat
einen Status (Interview offen → Interview erledigt → Backlog wird abgearbeitet), und eine
projektweite Roadmap zieht daraus die Reihenfolge — sortiert danach, was schnell viel bringt.

---

## Artefakt-Übersicht

Vier Dateitypen, alle unter `.claude/features/` (Ordnername beliebig, aber konsistent halten):

| Datei | Rolle |
|-------|-------|
| `README.md` | **Einziger Ort für Gesamtstatus.** Index aller Bereiche, Routing-Regel, Code-Zuordnungstabelle, bereichsübergreifende Befunde. |
| `ROADMAP.md` | **Einzige Quelle für die Reihenfolge.** Alle Backlog-Punkte aller Bereiche, gruppiert nach Dringlichkeit × Aufwand. Keine Inhalte, nur Reihenfolge + Kurzbeschreibung + Verweis. |
| `<bereich>/plan.md` | **Einzige Stelle für Inhalt und Entscheidungen eines Bereichs.** Ist-Analyse, offene Fragen samt Antworten, Ziele, Backlog mit Aufgaben, Entscheidungs-Log. |
| `_TEMPLATE.md` | Kopiervorlage für neue Bereiche (siehe unten). |

Harte Regel, die das ganze System zusammenhält: **jede Information hat genau einen Ort.**
Aufgaben stehen nur im Backlog des Bereichs, nie zusätzlich im Index. Reihenfolge steht nur in
der Roadmap, nie im Bereichsplan. Wird das verletzt, laufen Kopien auseinander und niemand
weiß mehr, welche stimmt.

---

## Phase 0 — Setup

Einmalig zu Beginn:

1. Verzeichnis anlegen: `.claude/features/` mit `README.md`, `ROADMAP.md`, `_TEMPLATE.md`
   (Inhalte siehe Vorlagen-Abschnitt unten).
2. Falls das Projekt verbindliche Code-Konventionen hat (Architekturregeln, Performance-Regeln,
   ein wiederkehrender Stolperstein wie "neue Datenmodelle brauchen X, Y, Z"), diese in
   `.claude/guidelines/*.md` festhalten oder auf bestehende Docs verweisen. Diese Guidelines
   werden in Phase 2 (Lücken suchen) und Phase 5 (umsetzen) aktiv herangezogen, nicht nur
   verlinkt.
3. Optional, falls das Werkzeug Skills/Slash-Commands unterstützt: einen Routing-Skill anlegen,
   der ohne Argument den nächsten offenen Roadmap-Block wiedergibt und mit Argument
   (`/feature <bereich>`) direkt in den Interview- oder Backlog-Modus dieses Bereichs springt
   (Vorlage ganz unten). Nicht zwingend — die Phasen unten funktionieren genauso als reiner
   Gesprächsablauf ohne Tooling.

---

## Phase 1 — Bereiche finden

Ausgangspunkt ist der **grobe Plan des Nutzers** — ein paar Sätze oder eine Liste, keine
Feinplanung. Diese Phase zerlegt ihn in Bereiche.

1. Das Projekt überfliegen (Verzeichnisstruktur, Einstiegspunkte, ggf. bestehende
   Doku/READMEs) und einen Vorschlag für die Bereichsaufteilung machen — typischerweise entlang
   fachlicher Domänen (z. B. "Einkaufsliste", "Rezepte", "Sync") statt technischer Schichten
   (nicht "Controller", "Service", "Repository" als eigene Bereiche — die gehören quer über
   alle Domänen).
2. Groblinie: 8–15 Bereiche sind der praktikable Bereich. Deutlich weniger → wahrscheinlich zu
   grob geschnitten, Interviews werden unübersichtlich. Deutlich mehr → wahrscheinlich zu fein,
   Overhead pro Bereich lohnt sich nicht.
3. Vorschlag dem Nutzer zur Bestätigung/Korrektur vorlegen, **bevor** Ordner angelegt werden —
   das ist die einzige globale Rückfrage dieser Phase.
4. Je bestätigtem Bereich: Ordner `.claude/features/<bereich>/` mit `plan.md` aus
   `_TEMPLATE.md` anlegen, Abschnitt **Umfang** ausfüllen (Dateipfade so konkret wie möglich —
   UI, Datenmodell, Business-Logik, externe Schnittstellen, was auch immer im Projekt die
   analogen Schichten sind).
5. `README.md`-Index mit allen Bereichen befüllen (Status `Interview offen`), plus die
   Code-Zuordnungstabelle aus den Umfang-Angaben.

---

## Phase 2 — Ist-Analyse je Bereich

Für jeden Bereich, **bevor** irgendeine Frage an den Nutzer gestellt wird:

1. Den tatsächlichen Code des Bereichs lesen (bei großen Bereichen: mehrere Recherche-Agenten
   parallel auf Unterbereiche ansetzen, um Kontext zu sparen). Ziel ist eine Antwort auf: *was
   kann dieser Bereich heute wirklich, nicht was sollte er können.*
2. Ergebnis in **Ist-Analyse** eintragen. Eiserne Regel: **was nicht im Code steht, steht nicht
   hier.** Keine Wunschfunktionen, keine Annahmen aus alter Doku, keine Vermutungen über
   Nutzerverhalten.
3. Aus der Ist-Analyse **Bekannte Lücken** ableiten, in projektpassende Kategorien sortiert.
   Bewährtes Muster (Kategorien nach Bedarf anpassen):
   - **Funktion & UX** — was fehlt oder ist umständlich, rein aus dem Code ersichtlich
   - **Code-Qualität** — Verstöße gegen die Guidelines aus Phase 0 (Anti-Pattern, fehlende
     Fehlerbehandlung, tote Codepfade)
   - **Tests** — kritische Logik ohne Testabdeckung
   - **[projektspezifisches Querschnittsthema]** — z. B. bei diesem Projekt "Sync": jede neue
     Entität muss an drei bestimmte Stellen angebunden sein, sonst bricht die Synchronisation
     lautlos. Jedes Projekt hat mindestens ein solches leicht vergessenes Muster — es lohnt
     sich, dafür eine eigene Kategorie zu führen statt es unter "Code-Qualität" zu verstecken.
4. Wiederkehrende Befunde, die für **mehrere** Bereiche gelten (z. B. ein global fehlendes
   `key`-Argument in Listen, ein durchgängig fehlender Berechtigungs-Check), zentral im
   `README.md` unter **Bereichsübergreifende Befunde** sammeln statt in jedem Bereichsplan zu
   wiederholen.

Diese Phase erzeugt bewusst noch keine Aufgaben — nur Befunde. Aufgaben entstehen erst nach dem
Interview, wenn klar ist, was der Nutzer davon überhaupt will.

---

## Phase 3 — Interview je Bereich

1. Aus **Bekannte Lücken** fünf bis zehn gezielte Fragen ableiten — jede muss auf einem
   konkreten Befund aus der Ist-Analyse aufbauen, nicht generisch sein ("Was fehlt euch beim
   Einkaufen?" ist zu vage; "Die Gang-Zuordnung matcht Produktnamen exakt ohne
   Singular/Plural-Normalisierung — ist das der Grund für die ungenauen Vorschläge?" ist konkret
   und beantwortbar).
2. Fragen in **Gruppen von höchstens vier** stellen, mit dem strukturierten Frage-Werkzeug des
   Werkzeugs (falls vorhanden) und konkreten, aus der Analyse abgeleiteten Antwortoptionen statt
   offener Freitextfragen, wo sinnvoll möglich.
3. Jede Antwort **direkt unter der jeweiligen Frage** in `plan.md` eintragen — Frage und
   Entscheidung bleiben zusammen, nie in eine separate Notizdatei auslagern.
4. Nach Beantwortung aller Fragen:
   - **Ziele** füllen — als Endzustand formuliert ("Der Bereich kann danach X"), nicht als
     Aufgabenliste.
   - **Backlog** füllen — nummerierte Aufgaben (`A1`, `A2`, …) mit Aufwandsschätzung (S = unter
     einer Stunde, M = halber Tag, L = mehrere Tage) und Impact-Einschätzung
     (niedrig/mittel/hoch). Format:
     ```
     - [ ] **A3** — Kurze, umsetzbare Beschreibung · Aufwand M · Impact hoch
     ```
   - Status im Kopf der Datei auf `Interview erledigt` setzen, Zeile im `README.md`-Index
     nachziehen (Aufgabenzahl, Priorität).
5. Ein Punkt, der offensichtlich eine grundsätzliche Architekturentscheidung braucht (neues
   Datenmodell, neuer Prozess-/Modul-Zuschnitt) statt einer lokalen Änderung: das direkt im
   Backlog-Eintrag vermerken ("braucht eigene Rückfrage zum Datenmodell vor der Umsetzung") statt
   die Entscheidung stillschweigend beim Umsetzen zu treffen.

---

## Phase 4 — Roadmap bilden (Konsolidierung)

Erst wenn **alle** Bereiche interviewt sind (oder zumindest die priorisierten):

1. Alle offenen Backlog-Punkte aller Bereiche einsammeln.
2. Duplikate und thematisch verschmolzene Punkte zusammenlegen — in einer eigenen Tabelle
   **Zusammengelegt am …** mit Vorher/Nachher/Begründung festhalten, damit die Historie
   nachvollziehbar bleibt und niemand später denkt, ein Punkt sei verlorengegangen.
3. Jeden Punkt nach **Dringlichkeit × Aufwand** einsortieren:
   - **Quick Wins** — kleiner Aufwand (S, vereinzelt M), hoher direkter Nutzen, technisch
     voneinander unabhängig genug um in einem Rutsch durchgezogen zu werden. Explizites Ziel:
     "an einem Nachmittag machbar."
   - **Wellen** (thematisch benannt, z. B. "Sichtbarkeit", "Kernabläufe reparieren", "Aufräumen
     & Fundament", "Größere Features") — mittlere bis große Punkte, gruppiert nach Thema statt
     nach Bereich, damit zusammenhängende Änderungen (z. B. alles was denselben Screen anfasst)
     in einem Rutsch laufen. Reihenfolge der Wellen: was am dringendsten korrigiert oder am
     meisten fundamentiert werden muss, zuerst.
   - **Tests** — Punkte, die bewusst nicht vorgezogen werden, sondern **mit dem Fix laufen
     sollen, der denselben Code ohnehin anfasst.** Kritische Logik (Geld, Sicherheit,
     Datenverlust-Risiko) kommt trotzdem sofort in eine frühe Welle statt hier zu warten. Diese
     Sektion braucht eine eigene Nachverfolgung, siehe Phase 6.
4. Jede Sektion bekommt eine kurze Vorschau auf die nächste ("Weiter geht's mit Welle 2 —
   …"), damit beim Abschluss einer Welle sofort klar ist, was ansteht.
5. `README.md` einmal zentral aktualisieren: Gesamtstand, Link auf die Roadmap als
   Einstiegspunkt.

---

## Phase 5 — Umsetzung (Welle für Welle)

Pro Aufgabe, in der von der Roadmap vorgegebenen Reihenfolge:

1. Vor Code-Änderungen die einschlägige Guideline heranziehen (Phase 0). Bei Berührung des
   projektspezifischen Querschnittsthemas (Phase 2, vierte Kategorie) das explizit prüfen.
2. Umsetzen. Trifft die Umsetzung auf eine Abweichung von dem, was in der Ist-Analyse steht
   (Code verhält sich anders als dokumentiert) — **zuerst die Ist-Analyse korrigieren, dann
   weiterarbeiten.** Der Plan folgt dem Code, nie umgekehrt.
3. Verifizieren, so weit im Projekt möglich (Kompilieren, Tests, Linting). Wenn eine
   Verifikationsart nicht verfügbar ist (z. B. keine Hardware für eine bestimmte
   Plattform-Zielgruppe, kein Zugriff auf einen externen Dienst), das offen benennen statt
   Erfolg zu behaupten.
4. Abschluss im Backlog-Eintrag dokumentieren: Checkbox auf `[x]`, direkt dahinter ein
   **„umgesetzt"-Vermerk** mit dem, was tatsächlich gebaut wurde — inklusive Abweichungen vom
   ursprünglichen Plan und deren Begründung (nicht nur "erledigt", sondern *was* und *warum so
   und nicht anders*). Das ist die wichtigste Gewohnheit dieses Systems: der Backlog-Eintrag ist
   am Ende die Doku der Entscheidung, nicht nur ein Häkchen.
5. Bei einer bewussten Entscheidung, die über die einzelne Aufgabe hinaus wirkt (z. B. ein neues
   Architekturmuster eingeführt, eine Priorität nachträglich korrigiert): in die
   **Entscheidungen**-Tabelle des Bereichsplans eintragen (`Datum | Entscheidung | Begründung`).
6. `README.md`-Zähler (Aufgaben offen/erledigt, ggf. globale Kennzahlen wie Dateianzahl/
   Testabdeckung) nachziehen.
7. Committen. Ist eine ganze Welle/Gruppe fertig, deren Kopfzeile in der Roadmap auf
   „vollständig umgesetzt (Datum)" setzen und auf die nächste Welle verweisen.
8. **Groß und riskant ≠ einfach anfangen.** Trifft eine Aufgabe auf ungewöhnlich hohen Umfang,
   ein neues strukturelles Element (neues Modul, neue externe Abhängigkeit) oder fehlende
   Verifikationsmöglichkeit (keine Testumgebung, kein Zugriff) — vor dem Loslegen kurz mit dem
   Nutzer den Ansatz abstimmen statt blind draufloszubauen. Das Nachfragen kostet eine Nachricht,
   ein falscher großer Wurf kostet den ganzen Umbau.

---

## Phase 6 — Pflege / laufender Betrieb

Das System ist nie "fertig", es läuft weiter, solange am Projekt gearbeitet wird:

- **Nächster Schritt bestimmen:** Roadmap von oben nach unten lesen, ersten offenen Block
  wiedergeben. Kein Rätselraten — die Roadmap ist die einzige Quelle für "was jetzt".
- **Tests-Sektion regelmäßig gegenprüfen:** sobald ein "läuft mit"-Partner-Fix gelandet ist,
  ist der zugehörige Test fällig — das passiert nicht automatisch, sondern muss aktiv beim
  nächsten Durchgang durch die Roadmap erkannt werden.
- **Bereich zu groß geworden?** Darf jederzeit aufgeteilt werden: neuen Unterordner nach
  `_TEMPLATE.md` anlegen, im Index eintragen, betroffene Backlog-Punkte umziehen statt
  duplizieren.
- **Archiv statt Löschen:** veraltete/abgelöste Altdokumentation nicht löschen, sondern in ein
  Archiv-Verzeichnis verschieben und im Kopf vermerken, dass sie nicht mehr als Quelle für den
  Ist-Stand dient — nur der aktuelle Bereichsplan gilt.
- **Am Ende einer Welle** immer den kurzen Statusblock (README/Roadmap-Kopf) aktualisieren,
  bevor zur nächsten übergegangen wird — das hält den Einstiegspunkt für die nächste Sitzung
  (menschlich oder KI) verlässlich.

---

## Leitprinzipien (Kurzfassung)

- Ein Ort pro Information: Inhalt im Bereichsplan, Reihenfolge in der Roadmap, Status im Index.
- Ist-Analyse ist Beobachtung, keine Meinung. Code widerspricht Doku → Doku verliert.
- Fragen erst stellen, wenn die Analyse eine konkrete Lücke gefunden hat.
- Rückfragen in kleinen Gruppen, mit konkreten Optionen, nicht als Frage-Wand.
- Jede erledigte Aufgabe bekommt eine Notiz, *was* gebaut wurde und *warum so* — nicht nur ein
  Häkchen.
- Groß + riskant + nicht verifizierbar → erst kurz abstimmen, dann bauen.
- Das System läuft iterativ weiter, nicht nach einem Durchlauf fertig.

---

## Vorlagen zum Kopieren

### `.claude/features/README.md` (Skelett)

```markdown
# Feature-Index — <Projektname>

Routing-Einstieg für die Feature-für-Feature-Verbesserung. Diese Datei ist die **einzige
Stelle mit Gesamtstatus**; der inhaltliche Stand liegt jeweils im `plan.md` des Bereichs.

**Stand:** <Datum> · **Basis:** <ein bis zwei Kennzahlen, die Umfang grob greifbar machen>

➡️ **Womit anfangen?** [ROADMAP.md](ROADMAP.md) sortiert alle offenen Punkte nach
Dringlichkeit und Aufwand — Quick Wins zuerst.

---

## Routing-Regel

Bei Arbeit an einem Feature:

1. Diese Datei lesen und den zuständigen Bereich bestimmen.
2. Dessen `plan.md` öffnen — **ausschließlich dort** werden Aufgaben abgehakt, Antworten
   eingetragen und Entscheidungen protokolliert.
3. Vor Code-Änderungen die einschlägige Guideline heranziehen (siehe `.claude/guidelines/`).
4. Nach getaner Arbeit die Statusspalten hier nachziehen.

Ein Bereich, der zu groß wird, darf aufgeteilt werden — dann neuen Ordner nach
[`_TEMPLATE.md`](_TEMPLATE.md) anlegen und hier eintragen.

---

## Bereiche

| # | Bereich | Plan | Interview | Aufgaben | Priorität |
|---|---------|------|-----------|----------|-----------|
| 1 | <Bereich> | [<bereich>](<bereich>/plan.md) | offen | – | – |

---

## Code-Zuordnung

| Bereich | UI / Einstieg | Datenmodell | Business-Logik | Externe Schnittstellen |
|---------|----------------|-------------|-----------------|------------------------|
| <Bereich> | | | | |

---

## Bereichsübergreifende Befunde

Gilt für alle Pläne, hier einmal zentral festgehalten statt mehrfach wiederholt:

| Befund | Zahl | Guideline |
|--------|------|-----------|
```

### `.claude/features/_TEMPLATE.md`

```markdown
# Feature: <Name>

> **Status:** Interview offen · **Aufgaben:** 0/0 · **Stand:** JJJJ-MM-TT

Kopiervorlage für einen neuen Feature-Bereich. Nach dem Anlegen im Index
[`.claude/features/README.md`](../README.md) eintragen.

## Umfang

Was zu diesem Bereich gehört — mit vollständigen Pfaden, damit Referenzen prüfbar bleiben.

| Ebene | Dateien |
|-------|---------|
| UI | |
| Datenmodell | |
| Business-Logik | |
| Externe Schnittstelle | |

## Ist-Analyse

Was der Bereich **heute tatsächlich** kann, aus dem Code abgeleitet. Regel: Was nicht im Code
steht, steht nicht hier. Keine Wunschfunktionen, keine Annahmen aus älteren Plänen.

## Bekannte Lücken

Vor dem Interview befüllt, soweit ohne Rückfrage feststellbar.

### Funktion & UX
### Code-Qualität
### Tests
### <projektspezifisches Querschnittsthema>

## Offene Fragen

Fünf bis zehn gezielte Fragen, aus der Ist-Analyse abgeleitet. Antworten werden direkt hier
unter der jeweiligen Frage ergänzt, damit Frage und Entscheidung zusammenbleiben.

1. …

## Ziele

Erst nach dem Interview füllen. Was der Bereich danach können soll — als Ergebnis formuliert,
nicht als Aufgabenliste.

## Backlog

Aufwand: S (< 1 h) · M (halber Tag) · L (mehrere Tage)

- [ ] **A1** — <Aufgabe> · Aufwand S · Impact hoch

## Entscheidungen

| Datum | Entscheidung | Begründung |
|-------|--------------|------------|
```

### `.claude/features/ROADMAP.md` (Skelett)

```markdown
# Umsetzungsreihenfolge

Bereichsübergreifende Sicht auf alle offenen Punkte aus den Bereichsplänen — sortiert
nach **Dringlichkeit × Aufwand**, damit Quick Wins nicht hinter großen Brocken liegen bleiben.

Die Details bleiben im jeweiligen `plan.md`; diese Datei sagt nur, **in welcher Reihenfolge**.

**Stand:** <Datum> · Aufwand: S (< 1 h) · M (halber Tag) · L (mehrere Tage)

---

## ⚡ Quick Wins

| # | Punkt | Wirkung |
|---|-------|---------|

**Weiter geht's mit Welle 1** (<Kurzbeschreibung des Themas>).

---

## 🧭 Welle 1 — <Thema>

| Punkt | Inhalt |
|-------|--------|

Details: [<bereich>](<bereich>/plan.md) <Punkte> — jeweils mit „umgesetzt"-Vermerk.

**Weiter geht's mit Welle 2** (<Kurzbeschreibung>).

---

## 🧪 Tests — laufen mit

Entscheidung: **nur bei kritischer Logik vorne**, der Rest läuft am besten zusammen mit dem
Fix, der denselben Code ohnehin anfasst.

| Punkt | Läuft mit |
|-------|-----------|

---

## Zusammengelegt am <Datum>

| Vorher | Jetzt | Grund |
|--------|-------|-------|
```

### Optionaler Skill (`.claude/skills/feature/SKILL.md`), falls das Werkzeug Skills unterstützt

```markdown
---
name: feature
description: Routing-Einstieg für die Feature-für-Feature-Verbesserung. Öffnet den Plan eines
  Feature-Bereichs, fasst offene Aufgaben zusammen und startet bei noch offenem Interview die
  Fragerunde. Verwenden, wenn an einem Feature gearbeitet, ein Feature-Plan gelesen oder
  fortgeschrieben werden soll, oder wenn gefragt wird, woran als Nächstes gearbeitet wird.
---

# Feature-Routing

## Ohne Argument

`.claude/features/ROADMAP.md` lesen und den nächsten offenen Block wiedergeben. Ergänzend
`.claude/features/README.md` für den Gesamtstatus. Keine Datei ändern.

## Mit Argument (`/feature <bereich>`)

1. Bereich aus `.claude/features/README.md` auflösen. Bei Tippfehler/Mehrdeutigkeit
   nachfragen statt zu raten.
2. `.claude/features/<bereich>/plan.md` vollständig lesen.
3. **Interview offen** → Fragen aus *Offene Fragen* stellen, in Gruppen von höchstens vier,
   Antworten direkt unter der jeweiligen Frage eintragen. Danach *Ziele* und *Backlog* füllen,
   Status auf `Interview erledigt` setzen, Index nachziehen.
   **Interview erledigt** → Offene Aufgaben aus dem Backlog zusammenfassen, nach Impact
   sortiert, fragen welche angegangen werden soll.

## Beim Umsetzen einer Aufgabe

- Vorher die einschlägige Guideline lesen.
- Nach Abschluss: Checkbox abhaken + „umgesetzt"-Vermerk, Entscheidungen eintragen,
  Index-Zähler aktualisieren.

## Regeln

- Der Plan des Bereichs ist die einzige Stelle für Aufgaben und Entscheidungen dieses Bereichs.
- Ist-Analyse beschreibt den Code. Abweichung entdeckt → zuerst Ist-Analyse korrigieren.
- Bereich zu groß → aufteilen, `_TEMPLATE.md` nutzen, im Index eintragen.
```
