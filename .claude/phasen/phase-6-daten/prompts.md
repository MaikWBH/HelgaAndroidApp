# Phase 6 — Prompt-Vorlagen für die Review-Sub-Agenten (Datendatei)

Stufe B startet je Block **zwei** Sub-Agenten nacheinander (erst Standards, dann Spec; bei B11 nur Spec), jeweils mit
frischem Kontext. **Modell der Sub-Agenten: `sonnet`.** Bietet das Sub-Agent-Werkzeug keinen Modell-Parameter, wird
das Standardmodell genutzt und das im Bericht beim Block vermerkt („Sub-Agent-Modell: Standard").

Stufe B ersetzt nur die Platzhalter `{…}` durch die Angaben aus [blocks.md](blocks.md) (wörtlich kopiert, Pfade mit
`$B`/`$T`/`$SA`/`$ST`/`$P` ausgeschrieben, `$H` durch die SHA aus S10) und ändert sonst **kein Wort**.

| Platzhalter | Quelle |
|-------------|--------|
| `{BLOCK}` | Kennung und Titel, z. B. `B3 — Server-Bon-Archiv` |
| `{MODUS}` | `voll` oder `Checkliste` (Abschnitt 1 der blocks.md) |
| `{WORTE}` | 400 bei `voll`, 250 bei `Checkliste` |
| `{H}` | SHA aus S10 |
| `{PFADE}` | Zeile „Pfade" des Blocks |
| `{KRITERIEN}` | Zeile „Standards" bzw. „Checkliste Standards"; jeder Baustein `K-…` wird durch seinen vollständigen Text aus blocks.md Abschnitt 4 ersetzt; für `K-SM` werden die zwölf Aufzählungspunkte aus `.claude/skills/code-review/SKILL.md` (Abschnitt „3. Identify the standards sources", von „Mysterious Name" bis „Refused Bequest") wörtlich eingefügt |
| `{SPEC}` | Zeilen „Spec" und „Pflichtfragen Spec" bzw. „Checkliste Spec" |
| `{NICHT_MELDEN}` | Abschnitt 3 der blocks.md vollständig |
| `{SCHWERE}` | Abschnitt 2 der blocks.md vollständig |
| `{VORBEFUNDE}` | die Zeilen des Blocks aus `review-bericht.md`, Abschnitt „Vorbefunde" (oder „keine") |

---

## Vorlage S — Standards-Achse

```text
Du bist Reviewer für die Achse STANDARDS im Repo /home/user/HelgaAndroidApp, Block {BLOCK}, Modus {MODUS}.
Du änderst NICHTS: keine Datei schreiben, nichts committen, keine Tests starten. Nur lesen (git, grep, Dateien).

Umfang: `git diff 2c148ab {H} -- {PFADE}` und `git log --oneline 2c148ab..{H} -- {PFADE}`.
Modus voll: lies jeden Hunk. Modus Checkliste: arbeite nur die Kriterien unten ab, führe die genannten grep-Befehle
aus und lies nur deren Treffer bzw. die genannten Dateien.

Geprüft wird gegen diese dokumentierten Standards (Quelle steht jeweils dabei):
{KRITERIEN}

Bewertung:
{SCHWERE}

Nicht melden:
{NICHT_MELDEN}

Schon bekannte Befunde (nicht erneut melden, nur weitere Fundstellen derselben Art ergänzen):
{VORBEFUNDE}

Melde (a) jede Stelle im Diff, die einen dokumentierten Standard verletzt (Quelle: Datei + Regel), und (b) jeden
Code-Smell der Basis, den du siehst (immer [Ermessensfrage]). Unterscheide harte Verstöße gegen dokumentierte
Standards von Ermessensfragen; ein dokumentierter Repo-Standard geht der Smell-Basis vor. Jede Fundstelle musst du
selbst gelesen haben; Vermutungen kennzeichnest du mit „Vermutung".

Antworte ausschließlich in diesem Format, höchstens {WORTE} Wörter:
### Standards — {BLOCK}
| # | Datei:Zeile | Befund | Regel (Datei §) | Grad | Markierung |
|---|-------------|--------|-----------------|------|------------|
(eine Zeile je Befund; # = S1, S2, …; Markierung: „ändert Verhalten", „Ermessensfrage", beides oder „–")
**Beifang (Altbestand, ohne Grad):** <Liste oder „keiner">
**Ergebnis:** <Anzahl Befunde je Grad>; schwerster Befund: <# oder „keiner">
```

## Vorlage P — Spec-Achse

```text
Du bist Reviewer für die Achse SPEC im Repo /home/user/HelgaAndroidApp, Block {BLOCK}, Modus {MODUS}.
Du änderst NICHTS: keine Datei schreiben, nichts committen, keine Tests starten. Nur lesen (git, grep, Dateien).

Umfang: `git diff 2c148ab {H} -- {PFADE}` und `git log --oneline 2c148ab..{H} -- {PFADE}`.
Modus voll: lies jeden Hunk. Modus Checkliste/Abgleich: arbeite nur die Punkte unten ab.

Die Spec (lies diese Dateien bzw. Abschnitte vollständig, sie sind verbindlich und wörtlich gemeint):
{SPEC}

Bewertung:
{SCHWERE}

Nicht melden (insbesondere die akzeptierten Abweichungen):
{NICHT_MELDEN}

Schon bekannte Befunde (nicht erneut melden):
{VORBEFUNDE}

Melde: (a) Anforderungen der Spec, die fehlen oder nur teilweise umgesetzt sind; (b) Verhalten im Diff, das die Spec
nicht verlangt (Scope Creep, auch Verstöße gegen „Nicht-Ziele"); (c) Anforderungen, die umgesetzt aussehen, aber falsch
umgesetzt sind; (d) verlangte Tests, die fehlen oder etwas anderes prüfen als verlangt. Zitiere zu jedem Befund die
Spec-Zeile (Datei, Schritt/Abschnitt, Wortlaut gekürzt). Jede Fundstelle musst du selbst gelesen haben; Vermutungen
kennzeichnest du mit „Vermutung".

Antworte ausschließlich in diesem Format, höchstens {WORTE} Wörter:
### Spec — {BLOCK}
| # | Datei:Zeile | Art (a/b/c/d) | Befund | Spec-Zitat (Datei, Stelle) | Grad | Markierung |
|---|-------------|---------------|--------|----------------------------|------|------------|
(eine Zeile je Befund; # = P1, P2, …)
**Ergebnis:** <Anzahl Befunde je Grad>; schwerster Befund: <# oder „keiner">
```
