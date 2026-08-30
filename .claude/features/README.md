# Feature-Index — Helga Android

Routing-Einstieg für die Feature-für-Feature-Verbesserung. Diese Datei ist die **einzige
Stelle mit Gesamtstatus**; der inhaltliche Stand liegt jeweils im `plan.md` des Bereichs.

**Stand:** 2026-08-22 · **Basis:** DB v30, 127 Kotlin-Dateien, 25 Room-Entities

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
| 1 | Einkaufsliste | [einkaufsliste](einkaufsliste/plan.md) | erledigt | 5 offen (2 erledigt) | ⭐⭐⭐ |
| 2 | Rezepte | [rezepte](rezepte/plan.md) | erledigt | 10 offen | ⭐⭐⭐ |
| 3 | Wochenplan | [wochenplan](wochenplan/plan.md) | erledigt | 14 offen (1 erledigt) | ⭐⭐⭐ |
| 4 | Bons & Kosten | [bons-kosten](bons-kosten/plan.md) | offen | – | ⭐⭐ |
| 5 | KI | [ki](ki/plan.md) | offen | – | ⭐⭐ |
| 6 | Nährwerte & Allergene | [naehrwerte](naehrwerte/plan.md) | offen | – | ⭐⭐ |
| 7 | Märkte & Gänge | [maerkte](maerkte/plan.md) | offen | – | ⭐ |
| 8 | Statistik | [statistik](statistik/plan.md) | offen | – | ⭐ |
| 9 | Sync | [sync](sync/plan.md) | offen | – | ⭐⭐ |
| 10 | Einstellungen & Onboarding | [einstellungen](einstellungen/plan.md) | offen | – | ⭐ |
| 11 | Plattform-Integration | [plattform](plattform/plan.md) | offen | 4 offen | ⭐ |

**Interview-Reihenfolge:** 1 → 2 → 3 (Kernablauf und die drei Bottom-Nav-Tabs), dann 4 → 5 → 6,
danach 7–11. Ein Bereich pro Runde: Fragen stellen, Antworten einarbeiten, Ziele und Backlog
füllen, Status hier nachziehen.

---

## Code-Zuordnung

Basis aller UI-Pfade: `app/src/main/kotlin/com/helga/android/`

| Bereich | UI | Room-Entities | Repository | Server |
|---------|----|---------------|------------|--------|
| Einkaufsliste | `ui/shopping/` | ShoppingList, ShoppingItem, ShoppingListStaple, QuickEmoji | ShoppingRepository | `/api/suggestions/*` |
| Rezepte | `ui/recipes/` | Recipe, Ingredient, Instruction, Tag, Category, RecipeHistory, RecipeFeedback | RecipeRepository | `/api/ai/import-url` |
| Wochenplan | `ui/weekplan/` | WeekplanDay, WeekplanRecipe, WeekplanExtra, WeekplanSettings, WeekplanConstraints, WeekplanTemplate, WeekplanTemplateEntry | WeekplanRepository, WeekplanTemplateRepository | – |
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
| Testabdeckung | 3 Testklassen / 14 Tests bei 127 Kotlin-Dateien; ein `androidTest`-Quellverzeichnis existiert nicht | – |
| `!!`-Operator | 10 Stellen, verboten laut Guideline | [kotlin-quality](../guidelines/kotlin-quality.md) |
| `items()` ohne `key` | 9 Stellen in Compose-Listen | [compose-performance](../guidelines/compose-performance.md) |
| `contentDescription = null` | 82 Stellen — je Fall zu prüfen, ob dekorativ oder Bedienelement | [ux-accessibility](../guidelines/ux-accessibility.md) |
| Nicht gesyncte Entities | 3 von 25: `WeekplanTemplateEntity` und `WeekplanTemplateEntryEntity` fehlen überall; `OffProductEntity` hat DAO und Serverseite fertig, aber keinen Aufruf in `SyncEngine` | [sync-patterns](../guidelines/sync-patterns.md) |
| Benachrichtigungen wirkungslos | `POST_NOTIFICATIONS` fehlt im Manifest und wird nie zur Laufzeit angefragt — bei `targetSdk 35` verwirft Android 13+ jede Zustellung. Betrifft die fertigen Einkaufstag- und Koch-Erinnerungen; Aufgabe in [plattform](plattform/plan.md) A4 | – |

## Bewusst offene Punkte

- **Mealie-Import** — in der archivierten Flask-Feature-Liste als Bereich 6 geführt, in der
  Android-App und im Server **nie umgesetzt** (`grep -ri mealie app/ server/` → keine Treffer).
  Weder eingeplant noch verworfen; Entscheidung steht aus.
- **Zeitraumfilter in der Statistik.** Der Ausgabenüberblick hat einen, die Statistik nicht —
  Absicht oder Versäumnis ist ungeklärt. Im Bereich Statistik zu entscheiden.

## Historie

- [`.claude/development_plan.md`](../development_plan.md) — Phasen 0–14, abgeschlossen. Historie, kein Routing-Ziel.
- [`.claude/archiv/helga_features.md`](../archiv/helga_features.md) — Feature-Liste der **Flask**-Vorgänger-App.
- [`.claude/archiv/improvement_plan.md`](../archiv/improvement_plan.md) — Phasen 15–19, inhaltlich vollständig umgesetzt.
