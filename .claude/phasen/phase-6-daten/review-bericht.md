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

### Spec — B3 — Server-Bon-Archiv

| # | Datei:Zeile | Art (a/b/c/d) | Befund | Spec-Zitat (Datei, Stelle) | Grad | Markierung |
|---|---|---|---|---|---|---|
| P1 | `server/app/receipts.py:382,447,514,559` | c | `_set_parse_state` ändert `parse_status` und `parse_attempts` ohne `next_seq` und ohne `server_seq`. Das betrifft `failed` (Warteschlange), `queued` (Reparse-Misserfolg) und `queued` beim Re-Scan eines vorhandenen Bons. Per `since_seq` erreicht der Wechsel kein Gerät, obwohl die App `failed` auswertet (`ReceiptGrouping.kt:12`). | server-vertrag §3.1 „Schreiben allgemein": „Jeder Schreibvorgang an einem Bon … erhöht sync_state.seq um 1 und stempelt … server_seq auf den Bon" | Mittel | [ändert Verhalten] |
| P2 | `server/app/receipts.py:514` | c | Ein Reparse-Misserfolg erhöht `parse_attempts` ohne Obergrenze. Ab 5 bleibt der Bon `queued`, wird aber nie mehr aufgenommen (`parse_attempts < 5`) und nie `failed`. | server-vertrag §3.1 Reparse: „bei Misserfolg bleiben die bisherigen Werte, nur `parse_status = 'queued'`" | Mittel | [ändert Verhalten] [Ermessensfrage] |
| P3 | `server/app/receipts.py:203,276,397,535,249` | b | Gegenüber dem Wortlaut kommen fünf Bedingungen dazu. Lernen entfällt bei Bon- oder Positions-`deleted`. `apply_parse` setzt `updated_at = max(now_ms(), alt+1)`. Die Warteschlange filtert `deleted = 0`. Der Lexikon-Name wird nur bei nicht leerem Eintrag übernommen. | server-vertrag §3.1 PUT „Danach Lernen, wenn applied und status in (confirmed, reconciled)"; Scan Schritt 4 „updated_at = now_ms()"; §4 „name übernehmen" | Niedrig | [Ermessensfrage] |
| P4 | `server/app/receipts.py` (`apply_parse`) | a | Die Spec nennt die Funktion `write_parsed`. Im Code heißt sie `apply_parse`, `write_parsed` existiert nirgends im Repo außerhalb der Spec. | phase-3a S10 „`write_parsed`, `save_receipt_image(receipt_id, upload) -> str`" | Niedrig | – |
| P5 | `server/app/receipts.py:342,352,494` | – (Beifang) | Gleiche Art wie V11: `dest.open("wb")`, `unlink` und `is_file()` laufen weiter synchron im Event-Loop. | – | Niedrig | – |
| P6 | `server/scripts/restore_purged_receipts.py:59` | c | Vermutung: `sqlite3.connect` legt bei falschem `--db`-Pfad auch ohne `--apply` eine leere Datei an. Es gibt keinen Nur-Lese-Modus. | phase-3a S13 „Ohne `--apply` nur Liste" | Niedrig | [Ermessensfrage] |
| P7 | `server/tests/test_schema_migration.py:96` | d | `test_init_db_is_idempotent` prüft nur die Zeilenzahl. Er prüft nicht, dass der zweite Lauf nichts ändert (Backfill läuft nicht erneut, `purchase_day` und `source` bleiben leer). | phase-3a S3 „(3) zweiter `init_db()`-Lauf ändert nichts und wirft nicht" | Niedrig | – |
| P8 | `server/tests/test_categorize_items.py:43` | d | „`user` bleibt" fehlt. Der Test lässt nur einen Treffer ohne KI-Aufruf zu. Der Schutz `WHERE source != 'user'` beim Speichern ist nicht getestet. | phase-3b S3 „… KI wirft → `""`, nichts gespeichert; `user` bleibt; …" | Niedrig | – |
| P9 | `server/tests/test_receipt_aggregates.py` | d | „Häufigster Name" wird nie mit abweichenden Namen getestet. Die Dimension `product` prüft nicht, dass vorläufige, gelöschte und datumlose Bons fehlen. | phase-3b S2 „store-Schlüssel und häufigster Name … vorläufige/gelöschte/datumlose Bons fehlen überall" | Niedrig | – |

Alle übrigen Pflichtfragen habe ich gegen den Code geprüft und ohne Abweichung gefunden. Das gilt für PUT, DELETE, Scan, Reparse, Warteschlange, Lernen, Aggregate (Filter, Rundung, Sortierung, Pfand), Alt-Clients (`deleted = 1` ignoriert, nur alte Spalten), `PRESERVE_IF_UNSET`, Schema und den Schreibzugriff des Restore-Skripts.

**Ergebnis:** Blocker 0, Hoch 0, Mittel 2, Niedrig 7; schwerster Befund: P1

## B2 — Bon-Protokoll App
_Sub-Agent-Modell: sonnet_

### Standards — B2 — Bon-Protokoll App
| # | Datei:Zeile | Befund | Regel (Datei §) | Grad | Markierung |
|---|-------------|--------|-----------------|------|------------|
| S1 | ReceiptSyncer.kt:44; ReceiptUploader.kt:87,104-106 | Server-Antwort ersetzt den Bon aus einer alten Zeilenkopie, ohne erneutes Lesen oder LWW-Vergleich, immer mit `dirty=0`. Eine Änderung während des Aufrufs (Scan bis 120 s) geht verloren. Vermutung | K-SP LWW / Server-Archiv | Mittel | ändert Verhalten |
| S2 | ReceiptSyncer.kt:68-73 | Löschzweig in `pull()` löscht hart, auch bei `pendingUpload=1` und bei `dirty=1` mit älterem Stand. Nur der Schreibzweig prüft `pendingUpload`. Vermutung: braucht einen Server-Tombstone mit gleicher ID | K-SP Server-Archiv (hartes Löschen nur `dirty=0`/`pendingUpload=0`) | Mittel | ändert Verhalten |
| S3 | ReceiptRepository.kt:208-212 | `imagePath` leer und `provisional` gilt als „nie hochgeladen". Der Uploader (ReceiptUploader.kt:66-69) setzt bei fehlender Datei aber `dirty=1`, danach geht der Bon per PUT zum Server. Löschen entfernt ihn dann nur lokal, der Server behält ihn. Vermutung | K-SP Server-Archiv | Mittel | ändert Verhalten |
| S4 | ReceiptUploadWorker.kt:27 | `catch (e: Exception)` verschluckt `CancellationException` (weitere Fundstelle zu V12) | K-KQ §6 | Niedrig | – |
| S5 | ReceiptRepository.kt:110 | Literal `"global"` statt `const val` (ebenso WeekplanRepository.kt:40) | K-KQ §10 | Niedrig | – |
| S6 | ReceiptScanFlowTest.kt:64,84; ReceiptUploaderTest.kt:35 | `!!` und `lateinit` im Testcode; der Regeltest scannt nur `src/main` | K-KQ §1, §7 | Niedrig | – |
| S7 | ReceiptSyncer.kt:141-144 ≈ ReceiptUploader.kt:104-106 ≈ ReceiptScanFlow.kt:52; ReceiptRepository.kt:131-141 ≈ ReceiptUploader.kt:72-84 | Duplicated Code: Server-Bon plus Positionen nach Room dreifach, Multipart-Scan-Aufruf doppelt | K-SM Duplicated Code | Niedrig | Ermessensfrage |
| S8 | ReceiptSyncer.kt:92-102; ReceiptRepository.kt:272-283; ReceiptLexiconRepository.kt:44-55; ReceiptScanFlow.kt:38-45; ReceiptUploader.kt:36-52 | Dieselbe Kaskade `IOException`/`HttpException`/`IllegalArgumentException` fünfmal. IAE (aus `require` in `ApiProvider.api()`) steht für „nicht konfiguriert" und fängt auch echte IAE | K-SM Duplicated Code, Primitive Obsession | Niedrig | Ermessensfrage |
| S9 | ReceiptRepository.kt:209,239,434; ReceiptScanFlow.kt:57,83-84; ReceiptUploader.kt:64,91 | Status-, Quell- und Parse-Strings (`"provisional"`, `"confirmed"`, `"queued"`, `"on_device"`) als Literale in Daten- und UI-Schicht verstreut | K-SM Primitive Obsession, Shotgun Surgery (§10 sinngemäß) | Niedrig | Ermessensfrage |
| S10 | ReceiptScanFlow.kt:36,60-75 | Data Clump `receiptId, bitmap, shoppingListId, now` läuft durch `run`/`offline`/`provisional`; `ScanRequest` existiert, wird dort nicht genutzt | K-SM Data Clumps | Niedrig | Ermessensfrage |
| S11 | ReceiptRepository.kt:98-117,257 | Divergent Change: Scan, Budget, Preisverlauf, Abgleich, OFF-Suche in einer Klasse. Dazu veränderlicher, nicht threadsicherer Singleton-Zustand `olderMonthCursor` | K-SM Divergent Change | Niedrig | Ermessensfrage |
| S12 | SyncApi.kt:88 | `getReceipt` hat in `main` keinen Aufrufer | K-SM Speculative Generality | Niedrig | Ermessensfrage |
| S13 | SyncScheduler.kt:24-26,43-45,74,86 | Constraints-Aufbau (`NetworkType.CONNECTED`) viermal | K-SM Duplicated Code | Niedrig | Ermessensfrage |
| S14 | AppPreferences.kt:55,147 | Magische `-1` (`ACCENT_SYSTEM` liegt in `ui/theme`, `data/` darf nicht importieren) an zwei Stellen dupliziert | K-SM Mysterious Name (K-AL) | Niedrig | Ermessensfrage |

**Beifang (Altbestand, ohne Grad):** SyncWorker.kt:46 `catch (e: Exception)` verschluckt `CancellationException` und setzt `SyncStatus.Error`, dadurch wirkt das `throw e` in `syncReceipts()` nicht. ImageUploadWorker.kt (Konstruktor) injiziert `SyncApiFactory` statt `ApiProvider` (K-AL). ReceiptRepository.kt:186 `addItemToReceipt` hat keinen Aufrufer und setzt nur die Position auf dirty, nicht den Bon (bon-weiser Push würde sie nie senden). `productSummaries`/`productPriceHistory` sind `suspend` statt `Flow` (K-KQ §4).

**Ergebnis:** Blocker 0, Hoch 0, Mittel 3 (S1–S3), Niedrig 11 (S4–S14); schwerster Befund: S1

### Spec — B2 — Bon-Protokoll App
| # | Datei:Zeile | Art (a/b/c/d) | Befund | Spec-Zitat (Datei, Stelle) | Grad | Markierung |
|---|-------------|---------------|--------|----------------------------|------|------------|
| P1 | `data/receipts/ReceiptScanFlow.kt:55-59` mit `ReceiptSyncer.kt:42-44` | c | Vermutung: Scan mit Server-KI-Fehler sendet kein `preliminary`, der Server legt eine leere Hülle mit `updated_at = now_ms()` an. Der Bon wird lokal mit `dirty = 1` und `updatedAt` = Uhrzeit vor dem Aufruf gespeichert. Der nächste Push verliert per LWW (`applied = false`) und überschreibt die ML-Kit-Werte durch die leere Hülle. Das hängt von der Uhrenabweichung ab; die Werte sind weg, bis die Server-Warteschlange den Bon ausliest (nach 5 Fehlversuchen bleibt er leer). Der Code folgt dem Wortlaut, der Widerspruch liegt in der Spec. | phase-3a S24 (4): „… `dirty = 1`, `updatedAt = now`“; server-vertrag §3.1 PUT: „Server `updated_at` > Eingabe → `applied = false`“, Scan (5): „leere Hülle“ | Mittel | ändert Verhalten |
| P2 | `data/repository/ReceiptRepository.kt:257-272` | b/c | `loadOlderMonth` nutzt einen In-Memory-`olderMonthCursor`, den die Spec nicht kennt. Der Cursor wird nie zurückgesetzt. Nach 24 h evictet `SyncWorker` die geladenen Monate, der Cursor steht aber weiter. Der nächste Klick überspringt die verdrängten Monate und lässt eine Lücke. | phase-3a S26: „`loadOlderMonth()` (Monat vor `oldestPurchaseDay()`, bzw. vor dem aktuellen Monat; … 24 h)“ | Mittel | ändert Verhalten |
| P3 | `data/receipts/ReceiptUploader.kt:96-99` | a/c | Vermutung: Bestandsfotos (unverkleinerte Kopie des Altcodes) werden unverändert hochgeladen. Der Server begrenzt auf 8 MB. Ein 413 zählt als 4xx, die Zeile bleibt mit `pendingUpload = 1`. Dann überspringt der Pull sie dauerhaft (`ReceiptSyncer.kt:75`), `evictableIds` nimmt sie nie heraus, und der Foto-Upload wird bei jedem Sync wiederholt. | phase-3a Annahme 1: „… vor dem Upload verkleinert (2000 px, JPEG 85)“; S25: „4xx → loggen, Zeile bleibt“ | Mittel | ändert Verhalten |
| P4 | `data/receipts/ReceiptSyncer.kt:47-50` | b | `pushDirty` fängt alle HTTP-4xx ab und lässt den Bon dirty. Das steht nicht in der Spec; dort gibt es nur PUT/DELETE und Room-Schreiben. Bei 401 oder 422 wird jeder Sync-Lauf alle Bons erneut versuchen. | phase-3a S22: „`pushDirty()` — … `deleteReceipt` … sonst `putReceipt` …“ | Niedrig | Ermessensfrage |
| P5 | `data/remote/dto/ReceiptDto.kt:12,35,61,75,105,128` | c | Weitere Fundstellen zu V10: `id` (4 DTOs), `updatedAt` in `ReceiptUpsertDto` und `query` in `OffSearchRequest` haben keinen Standardwert. | phase-3a S18: „alle Felder mit Standardwerten“ | Niedrig | Ermessensfrage |
| P6 | `ReceiptSyncerTest.kt:62-73` | d | Der Pull-Test prüft nur die Argumente von `replaceWithServerState`. „entfernt fehlende Positionen“ wird nicht geprüft, die Logik liegt im ungetesteten DAO. | phase-3a S22 Test: „Pull legt an, ersetzt, entfernt fehlende Positionen …“ | Niedrig | – |
| P7 | `ReceiptUploader.kt:36-49`, `ReceiptUploadWorker.kt:26`, `SyncWorker.kt:75` | c | Vermutung: Kein Mutex. `ReceiptUploadWorker` und `SyncWorker` (Connectivity-Trigger) können parallel `uploadPending()` laufen lassen. Der zweite Lauf kann mit veraltetem Snapshot in den Zweig „Datei fehlt“ geraten. Dort überschreibt er den Server-Stand mit `dirty = 1`. Der nächste Push heilt das per LWW; dazu kommt eine doppelte KI-Auswertung. | phase-3a S25: „`ReceiptUploader.uploadPending()` … Datei fehlt → `pendingUpload = 0` … `dirty = 1`“ | Niedrig | – |

Pflichtfragen ohne Abweichung von der Spec:
- Pull-Cursor, Fenster-Vergrößerung und `localWins`/`pendingUpload` sind wortgleich umgesetzt (S22).
- Die Zweige von `ReceiptUploader` stimmen mit S25 überein. Auch die Retry-Regeln stimmen.
- Der Scan nutzt die drei Zweige aus S24. `HttpException` und `IllegalArgumentException` zählen als offline.
- `deleteReceipt` löscht hart oder weich genau nach S26.
- Die Reihenfolge im `SyncWorker` ist Upload → Push → Pull → Aggregate.
- Die Datei-Löschung nach Upload ist umgesetzt (Annahme 1), ebenso die Kamera-Temp-Löschung.
- S23-Greps sind leer. Die Timeouts liegen bei 120 s (Annahme 12). Der Bon-Teil im `ImageDownloadWorker` ist entfernt (Annahme 13).

**Ergebnis:** 0 Blocker, 0 Hoch, 3 Mittel (P1–P3), 4 Niedrig (P4–P7); schwerster Befund: P1

## B1 — Room-Migrationen, Schema, DAOs
_Sub-Agent-Modell: sonnet_

### Standards — B1 — Room-Migrationen, Schema, DAOs
| # | Datei:Zeile | Befund | Regel (Datei §) | Grad | Markierung |
|---|-------------|--------|-----------------|------|------------|
| S1 | AppDatabase.kt:879-880 | Migration 36→37 löscht per `DELETE` auch lokal gelöschte Bons mit `dirty = 1` samt Positionen. Ihre Löschung ist nie beim Server angekommen, die Bons können beim Pull aus dem Archiv wieder auftauchen. Der Plan verlangt es (Annahme 7, `phase-3-bons-kategorien-entscheidungen.md:61`), die Guideline sagt Gegenteiliges. Das ist ein Standards-Konflikt, kein Spec-Verstoß. | sync-patterns.md „Server-Archiv" (hartes Löschen nur für `dirty = 0`); CLAUDE.md „Soft-Delete" | Niedrig | ändert Verhalten, Ermessensfrage |
| S2 | ReceiptEntity.kt:32-33 | Der Kommentar zu `source` verweist auf `SyncEngine.toDto()`. Dieser Mapper wurde im selben Diff entfernt. | – (Doku) | Niedrig | – |
| S3 | ReceiptDao.kt:34-35 | `upsertReceipts(List)` hat keinen Aufrufer mehr, seit der SyncEngine-Aufruf entfällt. Das ist toter Code. | Speculative Generality | Niedrig | Ermessensfrage |
| S4 | ReceiptAggregateDao.kt:17-18 | `count()` hat keinen Aufrufer (grep über `app/src/main`: keiner, im Test habe ich nicht gesucht). | Speculative Generality | Niedrig | Ermessensfrage |
| S5 | ReceiptDao.kt:28-29 und 53-55 | `itemsForReceipt` (ohne gelöschte) und `allItemsForReceipt` (mit gelöschten) heißen fast gleich. Der Unterschied steht nur im KDoc. | Mysterious Name | Niedrig | Ermessensfrage |
| S6 | ReceiptLexiconDao.kt:25-28, ReceiptAggregateDao.kt:28-31 | `replaceAll` hat in beiden DAOs dieselbe Form: `deleteAll()` und danach `insertAll()` in `@Transaction`. | Duplicated Code | Niedrig | Ermessensfrage |
| S7 | ReceiptEntity.kt:44 (`parseStatus`), ReceiptAggregateEntity.kt (`dimension`) | Status und Dimension sind rohe Strings (`"none"`, `"queued"`, `"failed"`, `"total"`, `"week"`). Die Literale stehen verteilt in ReceiptUploader, ReceiptScanFlow und ReceiptGrouping, eine gemeinsame Konstante fehlt. | Primitive Obsession; kotlin-quality §2/§10 sinngemäß | Niedrig | Ermessensfrage |
| S8 | StoreAisleSyncTest.kt:46, 52 | `!!` in Testcode. `KotlinQualityRulesTest` scannt nur `src/main`, das erzwingt es also nicht. | kotlin-quality §1 | Niedrig | – |
| S9 | StoreAisleSyncTest.kt:67-90, 108 | Der Konstruktor-Aufbau mit 13 positionalen Mocks wiederholt `SyncEngineTest` (der Kommentar sagt „Aufbau wie SyncEngineTest"). Außerdem steht ein voll qualifizierter Klassenname inline. | Duplicated Code | Niedrig | Ermessensfrage |
| S10 | Migration36To37Test.kt:62 | `assertFalse(scalar(...) != 0)` ist schwer lesbar, `assertEquals(0, …)` wäre klarer. | – (Stil) | Niedrig | – |
| S11 | RoomMigrationHarness.kt:55 | Der Harness stubbt nur `execSQL(String)`. Der relaxed Mock verschluckt `execSQL(String, Array)` und `query` stillschweigend. Künftige Migrationen mit Bind-Args würden nicht ausgeführt und der Test bliebe grün (Vermutung zur Zukunft). Das ist die dokumentierte Vorlage für alle Migrationstests. | sync-patterns.md Checkliste „Migrationstest über RoomMigrationHarness" | Niedrig | Ermessensfrage |

Geprüft und ohne Befund:
- Indizes und Pflichtfelder von `ReceiptEntity` und `StoreAisleEntity` sind vorhanden.
- Beide Migrationen sind in `addMigrations` eingetragen, und `fallbackToDestructiveMigration` kommt nicht vor.
- Die Schema-JSONs 37 und 38 sind exportiert, und beide Migrationen haben Tests.
- `PRESERVE_IF_UNSET` enthält `store_aisles.category_key`.
- Die DAOs sind in `DatabaseModule` bereitgestellt, und `SyncCompletenessTest` führt Lexikon, Aggregat und Bons.
- kotlin-quality §5: Die Mehrtabellen-Schreibzugriffe im ReceiptDao laufen in `@Transaction`.
- kotlin-quality §9: `StoreAisleDto.categoryKey` hat den Default `""`.

**Beifang (Altbestand, ohne Grad):** ReceiptRepository.kt:303 und 314 und ReceiptUploader.kt:66 und 89 rufen `receiptDao.upsertReceipt(...)` einzeln auf. Die Methode nutzt `REPLACE`, und `receipt_items` hat eine Fremdschlüssel-Beziehung mit `ON DELETE CASCADE` auf `receipts`. Vermutung: `INSERT OR REPLACE` auf den Bon löscht bei aktivem `foreign_keys` die Positionen mit. Ich habe das weder getestet noch geprüft, ob Room `PRAGMA foreign_keys` setzt. Betroffen wäre auch der Pfad ohne Parse-Ergebnis in `uploadOne` (ReceiptUploader.kt:89). Die Dateien liegen außerhalb des B1-Umfangs.

_Anmerkung Stufe B zum Beifang (kein Teil der Sub-Agent-Antwort):_ Nachgeprüft wurden nur die Fakten. `ReceiptItemEntity` hat einen Fremdschlüssel auf `receipts` mit `ON DELETE CASCADE` (auch in `38.json`), und `ReceiptDao.upsertReceipt` ist `@Insert(onConflict = REPLACE)`. Mit einem Python-`sqlite3`-Versuch (ohne Room) wurde bestätigt: `INSERT OR REPLACE` auf die Elternzeile löscht bei `PRAGMA foreign_keys=ON` alle Kindzeilen (0 von 2 Positionen übrig). **Nicht geprüft:** ob Room die Fremdschlüssel in der App tatsächlich aktiviert (Hinweis: `RoomDatabase.performClear` setzt `PRAGMA foreign_keys` selbst) und wie sich das im Gerät auswirkt. Bei Bestätigung wäre es Datenverlust (Hoch/Blocker, ändert Verhalten); Einzelaufrufer ohne anschließendes `upsertItems`: `ReceiptRepository.updateReceipt`, `linkReceiptToShoppingList`, `updateReceiptStore`, `ReceiptUploader.kt:66,89`.

**Ergebnis:** Blocker 0, Hoch 0, Mittel 0, Niedrig 11; schwerster Befund: S1 (Niedrig, Standards-Konflikt mit dem Plan).

### Spec — B1 — Room-Migrationen, Schema, DAOs
| # | Datei:Zeile | Art (a/b/c/d) | Befund | Spec-Zitat (Datei, Stelle) | Grad | Markierung |
|---|-------------|---------------|--------|----------------------------|------|------------|
| P1 | `app/src/main/kotlin/com/helga/android/data/local/entity/ReceiptEntity.kt:32-33` | c | Der Kommentar zu `source` sagt, das Feld werde "bewusst nicht synchronisiert (siehe SyncEngine.toDto())". Dieser Mapper existiert nach S23 nicht mehr. Mit dem Bon-Protokoll wird `source` synchronisiert (`ReceiptMapping.kt:27` übernimmt es vom Server, `:100` schickt es mit). Die Zeile liegt als Kontext im Diff, der Kommentar ist durch ihn falsch geworden. | phase-3a S19: "`source` vom Server, leer → `local?.source ?: "ai"`"; S23: "alle Bon-Zeilen entfernen … Mapper Z.535–561" | Niedrig | – |
| P2 | `app/src/main/kotlin/com/helga/android/data/local/dao/ReceiptDao.kt:67-86` | b | Drei `@Transaction`-Methoden sind neu: `upsertReceiptWithItems`, `replaceWithServerState`, `hardDeleteReceiptWithItems`. S20 listet sie nicht. S22 beschreibt dieselben Abläufe als einzelne Aufrufe (`upsertReceipt`, `upsertItems`, `hardDeleteItemsExcept`, `hardDeleteItemsOf` + `hardDeleteReceipt`). Sie sind inzwischen von `ReceiptSyncer`, `ReceiptUploader`, `ReceiptRepository` und den Tests verwendet und werden von `ReceiptProtocolRulesTest` abgedeckt. Vorteil: Schreiben und Löschen laufen atomar. Das ist Scope-Erweiterung gegenüber dem Planwortlaut. | phase-3a S20: "`ReceiptDao` neu: `pendingUploads()` … `clearPendingUpload(id, imagePath, updatedAt)`"; S22: "danach `hardDeleteItemsOf` + `hardDeleteReceipt`" | Niedrig | [ändert Verhalten] [Ermessensfrage] |
| P3 | `app/src/main/kotlin/com/helga/android/data/local/entity/ReceiptAggregateEntity.kt:24-25` | c | `period` und `dimension` haben keinen Default (`String = ""`). Die Spec nennt `period`, `dimension`, `key`, `label` zusammen mit "(`String = ""`)". Die Klammer lässt sich auf alle vier Felder lesen (Vermutung). Schema und `38.json` sind davon nicht betroffen. | phase-3b S5: "`period`, `dimension`, `key`, `label` (`String = ""`)" | Niedrig | [Ermessensfrage] |

**Geprüft und ohne Befund (Pflichtfragen):**
- **Migration 36→37:** Alle neun Spalten von `receipts` stimmen in Typ und Default. Das gilt auch für die sechs von `receipt_items` und die Tabelle `receipt_lexicon`. Die Indizes `index_receipts_pendingUpload` und `index_receipt_lexicon_storeKey` sind vorhanden. Die drei Daten-Updates und die zwei `DELETE` sind wörtlich wie im Plan. `'+2 hours'` ist die akzeptierte Abweichung.
- **Migration 37→38:** `categoryKey TEXT NOT NULL DEFAULT ''` ist da. Die zehn `UPDATE … WHERE aisleName = … AND categoryKey = ''` entsprechen der Tabelle in `kategorien.md` (Namen und Schlüssel verglichen, NFC-normalisiert), und `dirty` wird nicht angefasst. `receipt_aggregates` hat die Indizes `dimension` und `period`.
- **Schema-Dateien:** `37.json` und `38.json` sind committet, mit `version` 37 und 38. Ein skriptgestützter Vergleich 36→37→38 liefert genau die Spalten, Tabellen und Indizes der Entities und der Migrationen.
- **Tests:** `RoomMigrationHarness` erfüllt alle in S15 verlangten Punkte: Spaltenname, Affinität, `notNull`, PK und Indexnamen. `RoomMigrationHarnessTest` prüft den `assertThrows`-Fall. `Migration36To37Test` und `Migration37To38Test` bilden die Fälle aus S16 und S5 ab. `SyncCompletenessTest` enthält `ReceiptLexicon`, `ReceiptAggregate`, `Receipt` und `ReceiptItem`.
- **SyncEngine ohne Bons:** `grep -i receipt` ist in `SyncEngine.kt`, `ImageUploadWorker.kt` und `ImageDownloadWorker.kt` leer. `receiptDao` ist aus dem Konstruktor entfernt. `SyncPullResponse` und `SyncPushRequest` behalten ihre Bon-Felder.
- **`StoreAisleDto.categoryKey`:** Das DTO (`category_key`, Default `""`), `toEntity` und `toDto` reichen den Wert durch. `StoreAisleSyncTest` prüft Pull und Push.
- **DAOs:** `ReceiptDao`, `ReceiptLexiconDao` und `ReceiptAggregateDao` entsprechen S20 und S7. In `SyncDao` sind `receiptTimestamps` und `receiptItemTimestamps` gelöscht, und die gelöschten `ReceiptDao`-Methoden haben keine Verwender mehr. Die Abweichungen `clearPendingUpload` ohne `updatedAt` und der entfernte `findIdsOlderThan` sind akzeptiert.

**Ergebnis:** 0 Blocker, 0 Hoch, 0 Mittel, 3 Niedrig; schwerster Befund: P2 (Niedrig)

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
