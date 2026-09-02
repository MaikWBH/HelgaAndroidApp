---
name: feature
description: Routing-Einstieg für die Feature-für-Feature-Verbesserung der Helga-App. Öffnet den Plan eines Feature-Bereichs, fasst offene Aufgaben zusammen und startet bei noch offenem Interview die Fragerunde. Verwenden, wenn an einem Feature gearbeitet, ein Feature-Plan gelesen oder fortgeschrieben werden soll, oder wenn gefragt wird, woran als Nächstes gearbeitet wird.
---

# Feature-Routing

Die Verbesserung der App läuft Bereich für Bereich über die Pläne in `.claude/features/`.
Dieser Skill ist die Abkürzung dorthin.

## Ohne Argument

`.claude/features/ROADMAP.md` lesen und den nächsten offenen Block wiedergeben — Quick Wins
zuerst, danach die Wellen. Ergänzend `.claude/features/README.md` für den Gesamtstatus
(Bereich, offene Aufgaben, Priorität). Keine Datei ändern.

## Mit Argument (`/feature einkaufsliste`)

1. Bereich aus `.claude/features/README.md` auflösen. Bei Tippfehler oder Mehrdeutigkeit die
   möglichen Bereiche nennen und nachfragen, statt zu raten.
2. `.claude/features/<bereich>/plan.md` vollständig lesen.
3. Danach richtet sich das Vorgehen nach dem Status im Kopf der Datei:

   **Interview offen** → Die Fragen aus *Offene Fragen* stellen. Nicht alle auf einmal: in
   Gruppen von höchstens vier, mit `AskUserQuestion` und konkreten Antwortoptionen, die aus der
   Ist-Analyse abgeleitet sind. Jede Antwort direkt unter der jeweiligen Frage in `plan.md`
   eintragen — Frage und Entscheidung bleiben zusammen. Danach *Ziele* und *Backlog* füllen,
   Status auf `Interview erledigt` setzen und die Zeile im Index nachziehen.

   **Interview erledigt** → Offene Aufgaben aus dem *Backlog* zusammenfassen, nach Impact
   sortiert, und fragen, welche angegangen werden soll.

## Beim Umsetzen einer Aufgabe

- Vorher die einschlägige Guideline lesen: `.claude/guidelines/compose-performance.md`,
  `kotlin-quality.md`, `sync-patterns.md`, `ux-accessibility.md`.
- Neue oder geänderte Room-Entity: Sync-Anschluss in `SyncDto`, `SyncEngine` und `SyncDao`
  prüfen — das ist die im Projekt am häufigsten vergessene Stelle (drei Entities sind aktuell
  nicht angebunden). Schema-Änderungen brauchen eine `@Migration`.
- Nach Abschluss: Kästchen im `plan.md` abhaken, Entscheidungen in die Tabelle am Ende
  eintragen, Aufgabenzähler im Index aktualisieren.

## Regeln

- Der Plan des Bereichs ist die einzige Stelle für Aufgaben und Entscheidungen dieses Bereichs.
  Keine parallelen Notizen, keine Aufgaben im Index.
- Die Ist-Analyse beschreibt, was im Code steht. Wird beim Arbeiten eine Abweichung entdeckt,
  zuerst die Ist-Analyse korrigieren, dann weiterarbeiten.
- Ein Bereich, der zu groß wird, darf aufgeteilt werden: neuen Ordner nach
  `.claude/features/_TEMPLATE.md` anlegen und im Index eintragen.
- `.claude/archiv/` ist Historie. Nicht als Quelle für den Ist-Stand verwenden.
