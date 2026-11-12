# Phase 6 — Review-Bericht (Querschnitt Phasen 1–5)

> Gerüst aus Stufe A. Stufe B füllt die mit `<…>` markierten Stellen und fügt die Antworten der Sub-Agenten unter
> „Standards" und „Spec" je Block **wörtlich** ein (leicht bereinigt: nur Zeilen entfernen, die unter blocks.md
> Abschnitt 3 „Nicht melden" fallen; jede Entfernung als Zeile „entfernt: <#>, Grund" darunter). Die zwei Achsen
> werden nie zusammengeführt oder umsortiert.

- Review-Bereich: `2c148ab..451819d` · Sub-Agent-Modell: `sonnet` (Abweichung je Block vermerken)
- Spec: `.claude/phasen/phase-1…5`, `phase-3-daten/*` · Standards: `CLAUDE.md`, `server/CLAUDE.md`, `.claude/guidelines/*`
  und die Code-Smell-Basis des Skills `code-review`
- Akzeptierte Abweichungen der Phasen 1–5 werden nicht erneut gemeldet (Liste: `blocks.md` Abschnitt 3)

## Zusammenfassung
<S23: Tabelle Block × Achse × Anzahl je Grad; je Achse der schwerste Befund (keine Gesamtwertung über beide Achsen)>

## An den Nutzer zur Entscheidung
<S23: jeder Befund mit Markierung „ändert Verhalten" aus allen Blöcken plus V3, V5, V6, V10 — je Zeile: Nr., Befund,
Lösungsvorschlag aus diesem Bericht bzw. „Vorschlag: <ein Satz>">

## Vorbefunde (Stufe A, vor Block B gelesen und belegt)

| # | Block | Datei:Zeile (Stand `c0b84d8`) | Befund | Grad | Markierung | Status |
|---|-------|-------------------------------|--------|------|------------|--------|
| V1 | B7, B6 | `ui/receipts/charts/SpendingBarChart.kt:49,87,114`; `data/util/AisleKeywordMatcher.kt:54,69` | `!!` (kotlin-quality §1) | Mittel | – | behoben in S1 |
| V2 | B2 | `data/receipts/ReceiptSyncer.kt:39-40,73-74,111-112,144-150`; `ReceiptUploader.kt:104-109`; `data/repository/ReceiptRepository.kt:146-149,174-182,216-217,221-224,242-252,437-438` | Bon und Positionen ohne Transaktion geschrieben/gelöscht (kotlin-quality §5) | Hoch | – | behoben in S2–S4 |
| V3 | B2 | `data/sync/SyncWorker.kt:58-66` mit `data/receipts/ReceiptImageStore.kt:69-73` und `ReceiptRepository.kt:160-162` | Race: `cleanupImages()` löscht das Scan-Foto, solange die Room-Zeile noch fehlt (Server-Aufruf bis 120 s) → Foto weg, Bon mit verwaister `localImageUri`. Vermutung, mittlere Konfidenz | Hoch | ändert Verhalten | an Nutzer. Vorschlag: `cleanupOrphans` lässt Dateien jünger als 1 Stunde stehen (Datei-`lastModified`), oder `prepare()` legt die Zeile vor dem Server-Aufruf an |
| V4 | B4 | `server/app/url_guard.py:74-78` | 100.64.0.0/10 (CGNAT/Tailscale) nicht gesperrt (`is_private` False) → SSRF ins Homelab | Hoch | – | behoben in S5 |
| V5 | B4 | `server/app/ai.py:246` | Weiterleitung ohne `Location` → `KeyError` → HTTP 500 statt 422 | Niedrig | ändert Verhalten | an Nutzer. Vorschlag: `resp.headers.get("location")`, fehlt er → `ValueError("Ungültige Weiterleitung")` (422) |
| V6 | B6 | `data/shopping/AisleAssigner.kt:60-89` | Lost Update: nach dem KI-Aufruf werden Kopien des alten Artikelstands geschrieben; inzwischen abgehakt/umbenannt/von Hand zugeordnet wird überschrieben. Vermutung, mittlere Konfidenz | Mittel | ändert Verhalten | an Nutzer. Vorschlag: vor dem Schreiben Artikel neu laden und nur schreiben, wenn `aisle` noch leer, `isChecked == 0` und `name` unverändert |
| V7 | B8, B11 | `ui/receipts/ReceiptScanViewModel.kt:13-15`, `CostOverviewViewModel.kt:8`, `ProductPriceDetailViewModel.kt:7` | ViewModels nutzen `data/receipts` gegen architecture-layers; vom Plan so vorgegeben (Standards-Konflikt) | Niedrig | Ermessensfrage | aufgelöst in S24 (Guideline) |
| V8 | B9, B11 | `ui/common/UiState.kt` | `sealed class` gegen kotlin-quality §2; CLAUDE.md/Plan 1 verlangen es | Niedrig | Ermessensfrage | aufgelöst in S24 (Guideline) |
| V9 | B6 | `ui/stores/StoreListScreen.kt:391` | `menuOpen` mit `remember` statt `rememberSaveable` (ux §13) | Niedrig | – | behoben in S7 |
| V10 | B2 | `data/remote/dto/ReceiptDto.kt:89,95,141-142` | `receipt`, `period`, `dimension` ohne Standardwert (kotlin-quality §9); ein Standard-`receipt` würde einen leeren Bon in Room schreiben | Niedrig | Ermessensfrage, ändert Verhalten | an Nutzer. Vorschlag: so lassen und in kotlin-quality §9 Pflichtobjekte von Antworten ausnehmen |
| V11 | B3 | `server/app/receipts.py:349,454` | blockierendes Datei-I/O im Event-Loop | Niedrig | – | behoben in S6 |
| V12 | B2 | `ReceiptRepository.kt:298`, `SyncWorker.kt:63,76` | `CancellationException` verschluckt (`catch (e: Exception)`) | Niedrig | – | behoben in S8 |
| V13 | B2 | `ReceiptRepository.kt:451-452` | leerer `private companion object {}` | Niedrig | – | behoben in S9 |
| V14 | B11 | `.claude/phasen/phase-1-android-clean-architecture.md`, Abschluss | „Stand“-Abschnitte melden S17–S20 als offen, Checkboxen und Code sagen umgesetzt | Niedrig | – | behoben in S25 |
| V15 | B2, B6 | `$T` | Testlücken: `ReceiptLexiconRepository`, `ReceiptUploadWorker`, `AisleCategorizeWorker`, `reparse` in Scan-/Detail-ViewModel, Reihenfolge im `SyncWorker` | Niedrig | – | nur Bericht (Plan verlangte sie nicht) |
| V16 | B9 | `ui/recipes/RecipeListViewModel.kt:44-47`, `ui/weekplan/WeekplanRecipePickerViewModel.kt:37-39` | öffentliche `MutableStateFlow` (kotlin-quality §3), Altbestand | – | – | Beifang |

## B3 — Server-Bon-Archiv
_Sub-Agent-Modell: sonnet_

### Standards — B3 — Server-Bon-Archiv
| # | Datei:Zeile | Befund | Regel (Datei §) | Grad | Markierung |
|---|-------------|--------|-----------------|------|------------|
| S1 | receipt_api.py:99,61,53 (Modelle models.py:429-472) | PUT und `preliminary` nehmen `ReceiptIn`/`ReceiptItemIn` ohne jedes Limit (Texte, `id`, `items` unbegrenzt); Form `shopping_list_id` ungedeckelt | server/CLAUDE.md §Konventionen „Eingabe-Limits in models.py“ | Mittel | ändert Verhalten |
| S2 | receipts.py:19,334 | `^…$` mit `match` lässt abschließendes `\n` durch; Dateiname `receipt_<id>\n.jpg` | §Konventionen Pfad-/Eingabeprüfung | Niedrig | ändert Verhalten |
| S3 | receipts.py:342,352,494 | V11-Nachtrag: `dest.open("wb")`, `dest.unlink`, `is_file()` laufen noch direkt im Event-Loop | §Konventionen „kein blockierendes Datei-I/O“ (Muster images_api.py) | Niedrig | – |
| S4 | receipts.py:460,702; receipt_api.py:129 | KI-Ausfall in `_ai_parse`/`categorize`/`reparse` ergibt 200 (leer bzw. `parsed:false`) statt 502; dokumentiert ist nur der Scan-Fallback | §Konventionen „KI-Ausfall → 502“ (Plan-Konflikt) | Niedrig | ändert Verhalten, Ermessensfrage |
| S5 | receipt_api.py:65-69,119 | Router fragt per SQL direkt Bon-Zustand ab, obwohl `receipts._receipt_row` existiert | Feature Envy | Niedrig | Ermessensfrage |
| S6 | receipts.py:1-2,497,521,523,596 | Docstring „committet nicht selbst“ falsch (`categorize` :717, `reparse`, `process_parse_queue`); `db_factory` in `process_parse_queue` ohne abweichenden Aufrufer; Imports im Funktionskörper, obwohl `.db` oben importiert wird | Speculative Generality | Niedrig | Ermessensfrage |
| S7 | sync.py:244-283, :229, :271; db.py:538-540 | `_push_legacy_receipts` wiederholt LWW-/Upsert-Form von `push_records`; `strftime(…'+2 hours')` doppelt (db.py, sync.py); Tabellen-Sonderfälle per `if table ==` neben `PRESERVE_IF_UNSET` | Duplicated Code / Repeated Switches | Niedrig | Ermessensfrage |
| S8 | scripts/receipt_testset.py:32,39; restore_purged_receipts.py:63 | `_clean` dupliziert `receipt_text._clean_store_name`; `utcfromtimestamp(...).strftime` doppelt, ab Python 3.12 veraltet | Duplicated Code | Niedrig | Ermessensfrage |
| S9 | receipts.py:591-674 | `aggregate_rows`: fünfmal gleiche Zeilenform mit 9 Schlüsseln; Namen `t,w,st,c,p,v` | Duplicated Code / Mysterious Name | Niedrig | Ermessensfrage |
| S10 | receipt_api.py:74; receipts.py:393,429,466 | `image_name, shopping_list_id, captured_at` (+`preliminary`) wandern gemeinsam durch drei Funktionen | Data Clumps | Niedrig | Ermessensfrage |
| S11 | receipts.py:412,421,437,447,561,600; receipt_api.py:69 | Status-/Parse-Status-Strings als Literale verstreut; nur `LEARNING_STATUSES` ist Konstante | Primitive Obsession | Niedrig | Ermessensfrage |
| S12 | receipts.py (719 Zeilen) | CRUD, Lexikon/Lernen, Foto-I/O, KI-Scan, Warteschlange, Aggregate (:591), Kategorisieren (:677) in einem Modul | Divergent Change | Niedrig | Ermessensfrage |
| S13 | receipt_api.py:79-86 | Nur eine Leerzeile vor `@router.get("/aggregates")`; verwaister Platzhalterkommentar „weitere feste Pfade folgen…“ | Stil | Niedrig | – |
| S14 | test_receipt_aggregates.py:1, test_receipt_lexicon.py:23, test_receipt_scan.py:41, test_receipts_crud.py:21, test_sync_legacy_receipts.py:10 | `put`-Helfer fünfmal identisch; `RID`/`scan` in queue+scan doppelt; Reconcile-Endpunkttest doppelt (test_receipts_crud.py:107, test_reconcile_receipt.py:59) | Duplicated Code | Niedrig | Ermessensfrage |

**Beifang (Altbestand, ohne Grad):** keiner

**Ergebnis:** 0 Blocker, 0 Hoch, 1 Mittel, 13 Niedrig; schwerster Befund: S1

<Spec-Antwort>

## B2 — Bon-Protokoll App
<Standards-Antwort> · <Spec-Antwort>

## B1 — Room-Migrationen, Schema, DAOs
<Standards-Antwort> · <Spec-Antwort>

## B4 — Server-Sicherheit und -Struktur
<Standards-Antwort> · <Spec-Antwort>

## B6 — Auto-Zuordnung zum Gang
<Standards-Antwort> · <Spec-Antwort>

## B5 — Container
<Standards-Antwort> · <Spec-Antwort>

## B7 — Auswertung und Diagramme (Checkliste)
<Standards-Antwort> · <Spec-Antwort>

## B8 — Bon-UI (Checkliste)
<Standards-Antwort> · <Spec-Antwort>

## B9 — Architektur aus Phase 1 (Checkliste)
<Standards-Antwort> · <Spec-Antwort>

## B10 — UI-Querschnitt aus Phase 2 (Checkliste)
<Standards-Antwort> · <Spec-Antwort>

## B11 — Doku-Abgleich (nur Spec)
<Spec-Antwort>

## Offen am Gerät/Server (Abhakliste, gebündelt aus Phase 2–5)

Spalte „Statisch": `ja (Datei:Zeile)` = Code für die Prüfung gefunden und plausibel; `Abweichung (Nr.)` = Code passt
nicht, Befund-Nr. aus diesem Bericht; `nicht statisch prüfbar` = nur am Gerät/Server entscheidbar (Rollout, Optik,
echte KI). Die Spalte „Gerät" bleibt leer für den Nutzer.

| # | Prüfung (gekürzt) | Quellen | Statisch | Gerät |
|---|-------------------|---------|----------|-------|
| G01 | Jede der 6 Akzentfarben hell/dunkel: Akzent an FAB, Leiste, Chips; kein Lila außer bei Lila; Button-Text lesbar | P2 S1 | <…> | [ ] |
| G02 | Android 12+: Neuinstallation zeigt Wallpaper-Farben; Auswahl Grün → grün | P2 S2 | <…> | [ ] |
| G03 | TalkBack liest Farbnamen und „ausgewählt"; Option „System" fehlt unter Android 12 | P2 S3 | <…> | [ ] |
| G04 | Kaltstart im Dark Mode ohne weißes Aufblitzen; Statusleisten-Icons passend zum App-Modus | P2 S4 | <…> | [ ] |
| G05 | Nichts hinter der NavigationBar auf vier Tabs (FAB „Scannen", QuickAdd, letzter Eintrag); keine doppelte Lücke | P2 S6 | <…> | [ ] |
| G06 | Kostenübersicht: Titel und Zurück sichtbar, Inhalt unter der Leiste | P2 S7 | <…> | [ ] |
| G07 | Startziel ohne Aufblitzen; Share-URL öffnet Import, Zurück zur Liste, Drehen öffnet nicht erneut; Abmelden → Onboarding | P2 S8 | <…> | [ ] |
| G08 | Predictive Back (Android 14+): Vorschau, Sheets/Dialoge zuerst | P2 S9 | <…> | [ ] |
| G09 | Tastatur verdeckt keine Felder (QuickAdd, Rezeptformular, Einstellungen, Onboarding); Sheets unverändert | P2 S10 | <…> | [ ] |
| G10 | ≥ 600 dp: Rail statt Leiste, keine Rail auf Detail-Screens; ≥ 840 dp Inhalt mittig begrenzt (auch Kostenübersicht) | P2 S11, P3b S15 | <…> | [ ] |
| G11 | TalkBack: Zeilen der Kochansicht als Kontrollkästchen mit Zustand | P2 S24 | <…> | [ ] |
| G12 | Slot-Chip gut zu treffen, Tageszeile nicht höher; Marker-Chips hell/dunkel lesbar | P2 S25 | <…> | [ ] |
| G13 | Drehung erhält offene Dialoge/Sheets und Text (Einkaufsliste, Wochenplan, Einstellungen, Rezeptdetail, Bon-Review) | P2 S28, P3a S29 | <…> | [ ] |
| G14 | Testset mit 10–20 echten Bon-Fotos, Bericht ansehen, ggf. `AI_RECEIPT_EFFORT=high` | P3a S12 | nicht statisch prüfbar | [ ] |
| G15 | Vor der Umschaltung: `make backup`, `restore_purged_receipts.py` erst listen, dann `--apply` | P3a S13 | <…> | [ ] |
| G16 | Zwei Geräte: Bon ändern/löschen kommt an; Rezept-/Listen-Sync unverändert; Gang-Kategorie kommt an | P3a S23, P3b S18 | <…> | [ ] |
| G17 | Offline-Scan: vorläufig sofort, nach Netz KI-Werte und „Bitte prüfen", keine Datei mehr in `files/receipts/` (siehe V3) | P3a S25 | <…> | [ ] |
| G18 | Review: Uhrzeit, Adresse, Rabatt, Pfand-Chip, Kategorien; Korrektur wird beim nächsten Scan übernommen; „Produkt zuordnen"; TalkBack | P3a S29 | <…> | [ ] |
| G19 | Bon-Liste: Chips, „Ältere Bons laden" (offline Meldung), Leerzustand | P3a S30 | <…> | [ ] |
| G20 | Bon-Detail: vorläufig mit lokalem Foto, nicht bearbeitbar; „Neu auslesen"; altes Foto vom Server | P3a S31 | <…> | [ ] |
| G21 | Lexikon: Eintrag nach Korrektur, Löschen wirkt beim nächsten Scan | P3a S32 | <…> | [ ] |
| G22 | Ausroll-Reihenfolge: erst Server, dann App auf beiden Handys | P3a S33 | nicht statisch prüfbar | [ ] |
| G23 | Kostenübersicht: vier Diagramme hell/dunkel/alle Akzente, Budgetlinie, offline, Monat/Woche, Zeitraum-Chips, Top-Artikel → Verlauf, TalkBack | P3b S15 | <…> | [ ] |
| G24 | Artikel > 3 Monate ohne Kauf zeigt Preisentwicklung mit Hinweis | P3b S16 | <…> | [ ] |
| G25 | Gang-Editor: neuer Markt mit Vorlage hat 15 Gänge; Ziehen funktioniert; TalkBack liest Gang und Kategorie | P3b S18 | <…> | [ ] |
| G26 | Tomaten, Milch, Spülmittel, Bier landen automatisch im Gang; Handkorrektur wird gelernt | P3b S22 | <…> | [ ] |
| G27 | Server-KI: Geschenkpapier/Leinsamen nach Sekunden im Gang, danach sofort; Flugmodus: sofortige Reaktion ohne Gang (siehe V6) | P3b S23 | <…> | [ ] |
| G28 | Server-Rollout: `API_KEY` in Prod-`.env`; Image neu bauen (Tag, `app.sh bump`, `deploy.sh`); Datenordner UID 1000; Homelab-Override überschreibt weder `read_only`/`tmpfs` noch Healthcheck | P4 Hinweis, P5 Hinweis | nicht statisch prüfbar | [ ] |
| G29 | Server nach Deploy: `healthy`, zwei Worker; Sync, Foto-Upload (auch 5–8 MB), Bon-Scan; URL-Import öffentlich ok, `http://192.168.x.x` und `http://100.64.x.x` abgelehnt; falscher `AI_API_KEY` → Fehler statt Hängen | P4 Prüfliste, P5 Prüfliste, P6 S5 | <…> | [ ] |
