# Phasenplan — App-Überarbeitung pro Skill

Übergeordneter Plan. Jede Phase entspricht **einem Skill** und überarbeitet den Bereich der App, für den
dieser Skill zuständig ist. Details einer Phase stehen erst nach ihrer Definition in
`phase-<n>-<skill>.md`. Aufgaben und Status der Features bleiben in `.claude/features/*/plan.md`
(Index: `.claude/features/README.md`); dieser Plan ergänzt sie, ersetzt sie nicht.

## Zweistufiger Ablauf pro Phase

**Gate 0 — Modellwahl (immer zuerst, nie überspringen).**
Vor dem Start einer Phase werden zwei Fragen gestellt (AskUserQuestion):
1. Welches (größere) Modell **definiert** die Phase aus?
2. Welches (kleinere) Modell **setzt** sie um?

Die Phasen sollen von *unterschiedlichen* Modellen ausgeführt werden. Die Wahl wird in der
Phasen-Datei unter „Modelle" festgehalten (Name, kein Commit-Text).

**Stufe A — Ausdefinieren (größeres Modell).**
1. Skill per Skill-Tool laden, zuständigen Bereich laut Feature-Index bestimmen, dessen `plan.md` lesen.
2. Code gegen Skill und `.claude/guidelines/` prüfen (Befunde mit Dateipfad).
3. Offene Entscheidungen mit `grilling` / `/grill-me` klären (runden­weise, mit Empfehlung).
4. `phase-<n>-<skill>.md` nach `_PHASE_TEMPLATE.md` schreiben: kleine, in sich geschlossene Schritte mit
   Checkbox, Dateien, Akzeptanzkriterium und Verifikationsbefehl. **Keine Designentscheidung bleibt
   für Stufe B offen.**
5. Freigabe durch den Nutzer, Commit des Phasenplans.

**Stufe B — Umsetzen (kleineres Modell).**
1. Phasen-Datei Schritt für Schritt abarbeiten, Checkbox abhaken, Verifikation je Schritt ausführen.
2. Der Nutzer wird nur *durchgeleitet*: Ausgabe je Schritt = was getan, Ergebnis der Verifikation,
   nächster Schritt.
3. Unklarheit oder Abweichung vom Plan: **stoppen und per `grilling` rückfragen**, nicht selbst entscheiden.
4. Pro Schritt/Block ein Commit auf dem Entwicklungsbranch, Status in Bereichs-`plan.md` und
   Feature-Index nachziehen. Kein PR ohne ausdrückliche Anfrage.

Modellwechsel: Nutzer per `/model` oder die Stufe läuft als Sub-Agent mit gewähltem Modell.

## Phasen

| Phase | Skill | Bereich | Definition (groß) | Umsetzung (klein) | Status |
|-------|-------|---------|-------------------|-------------------|--------|
| 1 | android-clean-architecture | `data/`, `di/`, Repositories, Sync-Fluss | Sonnet 5.5 | Haiku 5.5 | umgesetzt → [phase-1](phase-1-android-clean-architecture.md) |
| 2 | mobile-android-design | `ui/` (Compose, Material 3, Theming) | Fable 5.2 | Sonnet 5.5 | umgesetzt (Stufe B) → [phase-2](phase-2-mobile-android-design.md) |
| 3 | android-clean-architecture + fastapi-templates + mobile-android-design | Bons, Kategorien, Ausgaben-Auswertung (App + Server) | Fable 5.2 | manuell (offen) | definiert (wartet auf Freigabe) → [3a](phase-3a-bons-server-scan.md) · [3b](phase-3b-auswertung-gang-kategorien.md) · [Entscheidungen](phase-3-bons-kategorien-entscheidungen.md) · [Daten](phase-3-daten/) |
| 4 | fastapi-templates | `server/app/` (Sync-Backend, restliche Teile) | – | – | offen |
| 5 | multi-stage-dockerfile | `server/Dockerfile`, Compose | – | – | offen |
| 6 | code-review | Querschnitt über Phasen 1–5 | – | – | offen |

Hilfs-Skills ohne eigene Phase: `find-skills` (Skill-Suche), `grill-me`/`grilling` (Rückfragen in
Stufe A und B). Die Zuordnung Skill → Bereich ist eine Ableitung aus den Skill-Beschreibungen und
muss vor Phase 1 bestätigt werden.

## Verifikation (global)

Android: `./gradlew :app:testDebugUnitTest` + Lint · Server: Tests in `server/`, `docker compose up -d` ·
Docker: `docker build`, Image-Größe vorher/nachher · Abschluss: `code-review` auf den Gesamtdiff.
