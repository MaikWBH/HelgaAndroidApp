# Feature-Index — Helga Android

Routing-Einstieg für die Feature-für-Feature-Verbesserung. Diese Datei ist die **einzige
Stelle mit Gesamtstatus**; der inhaltliche Stand liegt jeweils im `plan.md` des Bereichs.

**Stand:** 2026-08-31 · **Basis:** DB v32, 123 Kotlin-Dateien, 23 Room-Entities — alle elf
Bereiche interviewt

➡️ **Womit anfangen?** [ROADMAP.md](ROADMAP.md) sortiert alle 58 offenen Punkte nach
Dringlichkeit und Aufwand — Quick Wins zuerst.

---

## Routing-Regel

Bei Arbeit an einem Feature:

1. Diese Datei lesen und den zuständigen Bereich bestimmen.
2. Dessen `plan.md` öffnen — **ausschließlich dort** werden Aufgaben abgehakt, Antworten
   eingetragen und Entscheidungen protokolliert.
3. Vor Code-Änderungen die einschlägige Guideline heranziehen:
   [`.claude/guidelines/compose-performance.md`](../guidelines/compose-performance.md) ·
   [`.claude/guidelines/kotlin-quality.md`](../guidelines/kotlin-quality.md) ·
   [`.claude/guidelines/sync-patterns.md`](../guidelines/sync-patterns.md) ·
   [`.claude/guidelines/ux-accessibility.md`](../guidelines/ux-accessibility.md)
4. Nach getaner Arbeit die Statusspalten hier nachziehen.

Abkürzung: `/feature <bereich>` erledigt Schritt 1–2 automatisch
(siehe [`.claude/skills/feature/SKILL.md`](../skills/feature/SKILL.md)).

Ein Bereich, der zu groß wird, darf aufgeteilt werden — dann neuen Ordner nach
[`_TEMPLATE.md`](_TEMPLATE.md) anlegen und hier eintragen.

---

## Bereiche

| # | Bereich | Plan | Interview | Aufgaben | Priorität |
|---|---------|------|-----------|----------|-----------|
| 1 | Einkaufsliste | [einkaufsliste](einkaufsliste/plan.md) | erledigt | 2 offen (4 erledigt) | ⭐⭐⭐ |
| 2 | Rezepte | [rezepte](rezepte/plan.md) | erledigt | 6 offen (4 erledigt) | ⭐⭐⭐ |
| 3 | Wochenplan | [wochenplan](wochenplan/plan.md) | erledigt | 5 offen (9 erledigt) | ⭐⭐⭐ |
| 4 | Bons & Kosten | [bons-kosten](bons-kosten/plan.md) | erledigt | 1 offen (3 erledigt) | ⭐⭐ |
| 5 | KI | [ki](ki/plan.md) | erledigt | 2 offen (2 erledigt) | ⭐⭐ |
| 6 | Nährwerte & Allergene | [naehrwerte](naehrwerte/plan.md) | erledigt | 0 offen (4 erledigt) | ⭐⭐ |
| 7 | Märkte & Gänge | [maerkte](maerkte/plan.md) | erledigt | 2 offen (2 erledigt) | ⭐ |
| 8 | Statistik | [statistik](statistik/plan.md) | erledigt | 1 offen (2 erledigt) | ⭐ |
| 9 | Sync | [sync](sync/plan.md) | erledigt | 1 offen (3 erledigt) | ⭐⭐ |
| 10 | Einstellungen & Onboarding | [einstellungen](einstellungen/plan.md) | erledigt | 1 offen (3 erledigt) | ⭐ |
| 11 | Plattform-Integration | [plattform](plattform/plan.md) | erledigt | 2 offen (2 erledigt) | ⭐ |

**Alle Interviews abgeschlossen** (2026-08-30). Die Reihenfolge der Umsetzung steht jetzt in
[ROADMAP.md](ROADMAP.md); die Bereichspläne bleiben die Heimat der Details und der Statuspflege.

---

## Code-Zuordnung

Basis aller UI-Pfade: `app/src/main/kotlin/com/helga/android/`

| Bereich | UI | Room-Entities | Repository | Server |
|---------|----|---------------|------------|--------|
| Einkaufsliste | `ui/shopping/` | ShoppingList, ShoppingItem, ShoppingListStaple, QuickEmoji | ShoppingRepository | `/api/suggestions/*` |
| Rezepte | `ui/recipes/` | Recipe, Ingredient, Instruction, Tag, Category, RecipeHistory, RecipeFeedback | RecipeRepository | `/api/ai/import-url` |
| Wochenplan | `ui/weekplan/` | WeekplanDay, WeekplanRecipe, WeekplanExtra, WeekplanSettings, WeekplanConstraints | WeekplanRepository | – |
| Bons & Kosten | `ui/receipts/` | Receipt, ReceiptItem, MonthlyBudget | ReceiptRepository | `/api/ai/parse-receipt`, `/api/receipts/reconcile` |
| KI | `ui/ai/` | – | – | `/api/ai/generate`, `/api/ai/remix`, `/api/ai/classify` |
| Nährwerte | `ui/components/AllergenWarningBanner.kt`, `ui/components/BarcodeScanner.kt` | OffProduct | – | `/api/ai/nutrition`, `/api/off/*` |
| Märkte | `ui/stores/` | Store, StoreAisle, AisleProduct | StoreRepository | – |
| Statistik | `ui/stats/` | – | – | – |
| Sync | `data/sync/`, `data/remote/` | – | – | `/api/sync` |
| Einstellungen | `ui/settings/`, `ui/onboarding/`, `data/preferences/` | – | – | – |
| Plattform | `ui/widget/`, `ui/shopping/ShoppingListWearScreen.kt` | – | – | – |

---

## Bereichsübergreifende Befunde

Gilt für alle Pläne, hier einmal zentral festgehalten statt elfmal wiederholt:

| Befund | Zahl | Guideline |
|--------|------|-----------|
| Testabdeckung | 5 Testklassen / 33 Tests bei 123 Kotlin-Dateien (`src/main`); ein `androidTest`-Quellverzeichnis existiert nicht | – |
| `!!`-Operator | 10 Stellen, verboten laut Guideline | [kotlin-quality](../guidelines/kotlin-quality.md) |
| `items()` ohne `key` | 9 Stellen in Compose-Listen | [compose-performance](../guidelines/compose-performance.md) |
| `contentDescription = null` | 82 Stellen — je Fall zu prüfen, ob dekorativ oder Bedienelement | [ux-accessibility](../guidelines/ux-accessibility.md) |
| Nicht gesyncte Entities | 0 von 23 — behoben: `OffProductEntity` jetzt an `SyncEngine` angebunden (naehrwerte A2), `WeekplanTemplateEntity`/`WeekplanTemplateEntryEntity` per wochenplan A1 komplett entfernt statt angebunden | [sync-patterns](../guidelines/sync-patterns.md) |
| Benachrichtigungen wirkungslos | `POST_NOTIFICATIONS` fehlt im Manifest und wird nie zur Laufzeit angefragt — bei `targetSdk 35` verwirft Android 13+ jede Zustellung. Betrifft die fertigen Einkaufstag- und Koch-Erinnerungen; Aufgabe in [plattform](plattform/plan.md) A4 | – |

## Bewusst offene Punkte

- **Mealie-Import** — in der archivierten Flask-Feature-Liste als Bereich 6 geführt, in der
  Android-App und im Server **nie umgesetzt** (`grep -ri mealie app/ server/` → keine Treffer).
  Weder eingeplant noch verworfen; Entscheidung steht aus.

## Historie

- [`.claude/development_plan.md`](../development_plan.md) — Phasen 0–14, abgeschlossen. Historie, kein Routing-Ziel.
- [`.claude/archiv/helga_features.md`](../archiv/helga_features.md) — Feature-Liste der **Flask**-Vorgänger-App.
- [`.claude/archiv/improvement_plan.md`](../archiv/improvement_plan.md) — Phasen 15–19, inhaltlich vollständig umgesetzt.
