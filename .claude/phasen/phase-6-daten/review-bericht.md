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
_Sub-Agent-Modell: sonnet_

### Standards — B4 — Server-Sicherheit und -Struktur
| # | Datei:Zeile | Befund | Regel (Datei §) | Grad | Markierung |
|---|-------------|--------|-----------------|------|------------|
| S1 | models.py:429-472 (`ReceiptItemIn`, `ReceiptIn`), :399 | Bon-Archiv-Modelle haben keine Limits: `items`-Liste und alle Strings sind unbegrenzt. `ReceiptParseRequest.mime_type` ist unbegrenzt und geht ungeprüft in die KI-Nutzlast. | server/CLAUDE.md Konventionen „Eingabe-Limits in models.py" | Mittel | ändert Verhalten |
| S2 | ai_client.py:216-243 (`_stream_anthropic`), :183-213 | Ein Anthropic-`error`-Event im Stream wird übersprungen, der Stream endet dann mit `[DONE]`. Nicht-dict-JSON-Zeilen lösen AttributeError/TypeError aus statt `AiUpstreamError` (Vermutung, nicht ausgeführt). | server/CLAUDE.md Konventionen „KI-Ausfall → 502 … unbrauchbare Antwort"; „SSE … ohne [DONE]" | Mittel | ändert Verhalten |
| S3 | ai_api.py:90-95; tests/test_ai_errors.py:141 (Verhalten in receipts.py:699-703) | `categorize-items` schluckt KI-Ausfall und antwortet 200 mit leeren Kategorien. Der Test schreibt das fest. | server/CLAUDE.md Konventionen „KI-Ausfall → 502" | Niedrig | ändert Verhalten, Ermessensfrage (Plan-Vorgabe) |
| S4 | tests/test_url_guard.py:73 | Die Attrappe `_Resp.is_redirect` verlangt `Location`. Echtes httpx 0.28.1 liefert `is_redirect` für jede 3xx-Antwort (gelesen). Der Test verdeckt deshalb V5; kein Test für Weiterleitung ohne `Location`. | Test-Treue zu V5 (weitere Fundstelle) | Niedrig | Ermessensfrage |
| S5 | url_guard.py:25-26; tests/test_url_guard.py:36-46 | Der IPv4-mapped-Zweig (`::ffff:…`) hat keinen Test. `0.0.0.0` und Multicast sind ebenfalls nicht abgedeckt. | server/CLAUDE.md „SSRF-Regeln in url_guard.py" | Niedrig | – |
| S6 | ai.py:19-28; ai_client.py:87, 118, 259-309 | Der Alias-Block in `ai.py` ist Middle Man. `_model`, `_vision_model`, `_vision_once` haben keinen Aufrufer. `vision_text`, `_vision_openai`, `_vision_anthropic`, `vision_model`, `DEFAULT_VISION_MODEL` sind seit der Umstellung von `parse_receipt` auf `_receipt_once` toter Code. | K-SM Speculative Generality / Middle Man | Niedrig | Ermessensfrage |
| S7 | ai.py:487, 525, 585; ai.py:221 / images_api.py:20 | Duplicated Code: Markdown-Zaun entfernen, Klammern suchen, `json.loads` steht dreimal in den geänderten Funktionen. `_read_limited` gibt es zweimal mit verschiedener Signatur (zusätzlich eine Lese-Schleife in receipts.py:342-352). | K-SM Duplicated Code | Niedrig | Ermessensfrage |
| S8 | ai_client.py:175, 260, 312 | Repeated Switches: `if AI_PROVIDER == "anthropic"` steht in drei Funktionen. Der OpenAI- und Anthropic-Bildaufbau ist in `_vision_*` und `receipt_text` doppelt. Dazu der Data Clump (`system`, `user_text`, `image_b64`, `mime_type`). | K-SM Repeated Switches / Data Clumps | Niedrig | Ermessensfrage |
| S9 | ai.py:230-260, 330-480 | Divergent Change: `ai.py` vereint Prompts, SSRF-sicheres Abrufen (`_fetch_html`) und Bon-Normalisierung. Dazu Redundanz: `_truthy` mit totem bool-Zweig (:399), `not raw_text and not name` (:438). | K-SM Divergent Change | Niedrig | Ermessensfrage |
| S10 | models.py:450-452, 454-500 | `ReceiptItemOut(ReceiptItemIn): pass` ist überflüssig. `ReceiptIn`/`ReceiptOut` und `ReceiptParseItem` wiederholen fast alle Felder. | K-SM Speculative Generality / Duplicated Code | Niedrig | Ermessensfrage |
| S11 | settings.py:49 | `MAX_RECEIPT_IMAGE_BYTES = MAX_IMAGE_BYTES` ist eine Kopie zur Importzeit. Das widerspricht dem Docstring (Lesen zur Aufrufzeit). Ein Test, der `MAX_IMAGE_BYTES` patcht, wirkt nicht auf Bons. | settings.py Docstring | Niedrig | Ermessensfrage |
| S12 | suggestions_api.py:16, 35, 48 | `q`/`item` haben kein `max_length`. Ein LIKE-Muster über 50000 Zeichen führt vermutlich zu einem SQLite-Fehler und HTTP 500 (Vermutung). | Konventionen „Eingabe-Limits" (sinngemäß) | Niedrig | ändert Verhalten, Ermessensfrage |
| S13 | tests/test_ai_receipt_config.py:1 | Unbenutzter Import `asyncio`. | – | Niedrig | – |

**Beifang (Altbestand, ohne Grad):**
- ai.py:151 (`classify`): `json.loads` ohne try gibt bei kaputtem KI-JSON HTTP 500 statt 502.
- images_api.py:49: `path.exists()`/`is_file()` blockieren im Event-Loop (verschoben, nicht neu).
- receipts.py:339-352 (außerhalb des Umfangs): `dest.open("wb")` und `unlink` blockieren im Event-Loop.
- V5 bestätigt, keine neue Fundstelle: ai.py:246 `resp.headers["location"]`.
- „Dateitype" in images_api.py:33 ist ein Tippfehler, den der Test festschreibt.

**Ergebnis:** Blocker 0, Hoch 0, Mittel 2, Niedrig 11; schwerster Befund: S2 (gleichrangig S1)

### Spec — B4 — Server-Sicherheit und -Struktur
| # | Datei:Zeile | Art (a/b/c/d) | Befund | Spec-Zitat (Datei, Stelle) | Grad | Markierung |
|---|-------------|---------------|--------|----------------------------|------|------------|
| P1 | `server/app/main.py:36` (`app = FastAPI(...)`, Altbestand, Zeile nicht im Diff) | a | `/docs`, `/redoc` und `/openapi.json` bleiben ohne `require_auth` offen (kein `docs_url=None`/`openapi_url=None`). Das sind Starlette-`Route`, keine `APIRoute`. `test_routes_guard.py` (Test 1 und 2) sieht sie nicht. Schema der öffentlich erreichbaren API ist ohne Key lesbar. Nur Schema, keine Daten. | Pflichtfrage „jede Route außer Health mit Auth"; phase-4 „Geprüft und in Ordnung: alle Routen außer Health mit Auth"; `server/CLAUDE.md` „Auth immer … nie weglassen" | Niedrig | [ändert Verhalten] [Ermessensfrage] |
| P2 | `server/app/ai_client.py:197-210`, `:231-243` | c | Die zwei Anbieter-Streams bilden fehlerhafte Antwortformen nicht auf `AiUpstreamError` ab. `KeyError`/`IndexError`/`JSONDecodeError` werden still übersprungen. `TypeError`/`AttributeError` (z. B. Frame `data: null` oder `data: 123`, dann `obj["choices"]` bzw. `obj.get`) werden gar nicht gefangen. Vor dem ersten Chunk gibt das HTTP 500 statt 502. Danach bricht der Stream ohne Log ab. In `_post_json`, `_openai_text`, `_anthropic_text` ist `_SHAPE_ERRORS` abgedeckt. | phase-4 S8 „alle Anbieter-Aufrufe fangen … fehlerhafte Antwortformen (KeyError, IndexError, ValueError …) → ‚unerwartete Antwort des KI-Anbieters'"; Abschluss S8 „zusätzlich TypeError und AttributeError" | Mittel | [ändert Verhalten] |
| P3 | `server/app/images_api.py:25-39` | c | Vermutung (nicht ausgeführt): FastAPI 0.115 parst das Multipart-Formular (`File(...)`) vor den Dependencies. Starlette spoolt die komplette Datei, bevor `require_auth` und das 413-Limit greifen. Das Limit schützt daher nur das Schreiben nach `IMAGES_DIR`, nicht Speicher/Platte (Prod: `/tmp` ist tmpfs). Gleiches gilt für JSON-Bodys, die vor der Prüfung der S10-Limits komplett gelesen werden. Das ist wörtlich wie in S11 umgesetzt, das Ziel („riesige Uploads unbehandelt") ist nur teilweise erreicht. | phase-4 „Ziel": „riesige Uploads … sind unbehandelt"; Befund 5 „liest die ganze Datei in den Speicher, kein Größenlimit"; S11 „bei Überschreitung 413" | Mittel | [Ermessensfrage] [ändert Verhalten] |
| P4 | `server/tests/test_suggestions.py:41-47` | d | S6 verlangt für `q="%"` „liefert nur Namen, die ein echtes `%` enthalten (hier: leer)". Der Test legt „100% Saft" an und erwartet `["100% Saft"]`. Das ist strenger, weicht aber vom Wortlaut ab und steht nicht in den gemeldeten Abweichungen (dort nur `q="oma"`). Das Escapen von `\` ist ungetestet (verlangt S6 nicht). | phase-4 S6 „`q="%"` liefert nur Namen, die ein echtes `%` enthalten (hier: leer)" | Niedrig | – |

Geprüft und ohne Befund:
- Alle sechs Router haben `dependencies=[Depends(require_auth)]`. Die Routenmenge (28) stimmt mit dem Test überein, Reihenfolge der `include_router`-Aufrufe wie verlangt.
- Pfade, Felder und Formate der bestehenden Endpunkte sind unverändert.
- 502-Meldung ohne Geheimnisse, 5 Hops, alle aufgelösten Adressen, 5 MiB (dekomprimiert gezählt).
- S10-Limits Feld für Feld gleich, `settings` zur Aufrufzeit gelesen. `db.DB_PATH` und `logging.basicConfig` sind laut Spec.
- Bon-Nutzlast (`receipt_payload`) ohne `temperature`/`top_p`/`top_k`/`thinking` bei Effort-Modellen, `max_tokens` 16000.
- Prompt `RECEIPT_PARSE_USER` ist Byte-identisch zu Vertrag 5.2 (per Skript verglichen).
- `main.py` hat 54 Zeilen, `ai.py` 606.
- Phase 4 hat `receipts.py`, `receipt_api.py` und `sync.py` nicht angefasst.
- Die Tests der Schritte S2, S3, S6 (bis auf P4), S8, S9, S10, S11 und 3a S5 bis S7 entsprechen dem Verlangten.

**Ergebnis:** 0 Blocker, 0 Hoch, 2 Mittel (P2, P3), 2 Niedrig (P1, P4); schwerster Befund: P2 (P3 ist eine Vermutung)

## B6 — Auto-Zuordnung zum Gang
_Sub-Agent-Modell: sonnet_

### Standards — B6 — Auto-Zuordnung zum Gang
| # | Datei:Zeile | Befund | Regel (Datei §) | Grad | Markierung |
|---|-------------|--------|-----------------|------|------------|
| S1 | `data/sync/AisleCategorizeWorker.kt:27`; `data/repository/ShoppingRepository.kt:271` | `catch (e: Exception)` ohne Rethrow von `CancellationException`. Im Worker wird die (in `assignRemote` korrekt weitergereichte) Abbruch-Exception wieder verschluckt, geloggt und mit `Result.success()` beendet. In `suggestItems` (aus dem ViewModel verschoben) gilt dasselbe. | K-KQ §6 | Mittel | ändert Verhalten |
| S2 | `data/shopping/AisleAssigner.kt:89-91` | Je Server-Ergebnis werden `shopping_items` (`upsertItem`) und `aisle_products` (`learn`) ohne `withTransaction` geschrieben. Es wird dafür keine DB injiziert. Ein Abbruch dazwischen lässt Zuordnung und Gelerntes auseinanderlaufen. | K-KQ §5 | Mittel | – |
| S3 | `ui/stores/StoreListScreen.kt:415,436` | Der neue Kategorie-`IconButton` liegt ohne Abstand (0 dp) neben dem Löschen-`IconButton`. Ein Fehltipp löscht den Gang sofort (der Löschaufruf selbst ist Altbestand). | K-UX §1 (Mindestabstand 8 dp) | Mittel | – |
| S4 | `ui/stores/StoreListScreen.kt:320` (mit `LaunchedEffect` bei :326) | `draggedId` ist jetzt `rememberSaveable`, `dragOffset` und die Geste sind es nicht. Vermutung (nicht ausgeführt): Ein Konfigurationswechsel mitten im Ziehen lässt `draggedId` gesetzt. Die Zeile bleibt als „gezogen" markiert, und `localOrder` wird nicht mehr aus Room aktualisiert, sodass neu gesetzte Kategorien nicht erscheinen. | keine Regel (K-UX §13 meint nur Dialog-/Sheet-Sichtbarkeit und Eingaben) | Mittel | ändert Verhalten, Ermessensfrage |
| S5 | `ui/stores/StoreListScreen.kt:528-532` | Radio-Zeile `PrefillOption` mit `clickable(role = Role.Button)`. Verlangt ist `selectable(role = RadioButton)`. | K-UX §13 (Rollen) | Niedrig | – |
| S6 | `ui/stores/StoreListScreen.kt:461` | Das Auswahl-Häkchen im `CategoryMenuItem` trägt den Zustand „ausgewählt", hat aber `contentDescription = null`. TalkBack erfährt die aktuelle Kategorie nicht. | K-UX §2 | Niedrig | – |
| S7 | `data/shopping/AisleAssigner.kt:96-110` | Duplicated Code: `learn` wiederholt `StoreRepository.saveAisleProduct` (Zeile 158) fast wörtlich (vorhandenen Eintrag ändern, sonst neu anlegen). | K-SM Duplicated Code | Niedrig | Ermessensfrage |
| S8 | `data/repository/StoreRepository.kt:142,180` | Speculative Generality: `aislesForStore` ist neu und ohne Aufrufer. `findAisleForProduct` hat seinen letzten Aufrufer durch das Diff verloren. `aislesForStore` ist außerdem ein `suspend`-Snapshot (K-KQ §4). | K-SM Speculative Generality | Niedrig | Ermessensfrage |
| S9 | `data/util/AisleKeywordMatcher.kt:19`; `AisleKeywords.kt:11`; `AisleAssigner.kt:34,43` | Primitive Obsession: Die Kategorie ist ein `String`, `""` heißt „keine". Das gilt für `categoryFor`, `Entry.category`, `aisleForCategory` und den Rückgabewert von `resolveLocal`, obwohl `ProductCategory` existiert. | K-SM Primitive Obsession | Niedrig | Ermessensfrage |
| S10 | `data/util/AisleKeywordMatcher.kt:55,71` | Der Block „Kandidat vergleichen, `best` und `bestCategory` setzen" steht doppelt. `best` und `bestCategory` sind ein Data Clump (Kategorie gehört in `Candidate`). `categoryFor` ist rund 65 Zeilen lang. | K-SM Duplicated Code, Data Clumps | Niedrig | Ermessensfrage |
| S11 | `data/shopping/AisleAssigner.kt:55`; `AisleKeywordMatcher.kt:11` | Der Name `assignRemote` täuscht: Die Funktion ordnet zuerst lokal zu, dann per Server. `AisleKeywordMatcher.normalize` ist ein öffentlicher Durchreicher (Middle Man) und wird nur intern genutzt. | K-SM Mysterious Name, Middle Man | Niedrig | Ermessensfrage |
| S12 | `data/repository/ShoppingRepository.kt:278`; `ui/shopping/ShoppingListViewModel.kt` (Barcode-Block) | `lookupBarcode` reicht den Retrofit-DTO unverändert an das ViewModel durch. Das ViewModel baut daraus die `OffProductEntity` selbst und setzt sie in den UI-State, ohne dass Room beteiligt ist (Middle Man, K-AL „nie aus Retrofit-Antworten"). Zusätzlich hat `ShoppingRepository` jetzt Quick-Emoji, Server-Vorschläge, Barcode und Gang-Zuordnung (Divergent Change). | K-AL (architecture-layers §Erlaubte Abhängigkeiten); K-SM | Niedrig | Ermessensfrage |
| S13 | `AisleAssignerTest.kt:31`; `ShoppingAisleAssignmentTest.kt:30`; `StoreRepositoryAisleTest.kt:23` | Neue `lateinit var` in Testklassen (Initialisierung in `@Before`). | K-KQ §7 | Niedrig | Ermessensfrage |

**Beifang (Altbestand, ohne Grad):**
- `StoreListScreen.kt:88`: `aisleSheetStore` mit `remember` (K-UX §13).
- `StoreListScreen.kt:473`: `prefill` mit `remember`, während `name` daneben `rememberSaveable` ist.
- `StoreListScreen.kt:436`: Gang-Löschen ohne Bestätigung oder Undo (K-UX §6).
- `ShoppingListViewModel.kt`: `catch (e: Exception)` in den Barcode- und Katalog-Funktionen ohne `CancellationException`-Rethrow (K-KQ §6).
- `StoreRepository.createStore`: schreibt `stores` und `store_aisles` ohne Transaktion (K-KQ §5).

**Ergebnis:** Blocker 0, Hoch 0, Mittel 4 (S1–S4), Niedrig 9 (S5–S13); schwerster Befund: S1.

### Spec — B6 — Auto-Zuordnung zum Gang
| # | Datei:Zeile | Art (a/b/c/d) | Befund | Spec-Zitat (Datei, Stelle) | Grad | Markierung |
|---|-------------|---------------|--------|----------------------------|------|------------|
| P1 | `data/sync/SyncScheduler.kt:89` mit `data/shopping/AisleAssigner.kt:57` | c | Vermutung: `KEEP` verwirft ein neues Einreihen, solange der Worker läuft (laufende Arbeit gilt als nicht abgeschlossen). `assignRemote()` liest die offenen Artikel einmal am Anfang. Ein Artikel, der während des KI-Aufrufs ohne Gang angelegt wird, bleibt ohne Gang, bis später wieder ein unbekannter Artikel dazukommt. Die Umsetzung entspricht dem Wortlaut der Spec; es ist eine Lücke im Spec-Entwurf, kein Abweichen von ihm. | phase-3b S23: „`enqueueUniqueWork("helga.aisle.categorize", ExistingWorkPolicy.KEEP, …)`"; Annahme 7: „ordnet nur zu, … Artikel noch keinen Gang hat" | Niedrig | [Ermessensfrage] [ändert Verhalten] |
| P2 | `data/repository/StoreRepository.kt:180-181` (Hinweis auch `data/util/AisleProductKey.kt:9`) | b | `StoreRepository.findAisleForProduct` hat nach S22/S21 keinen Aufrufer mehr. `AisleAssigner` fragt `storeDao` direkt ab, wie die Spec es vorgibt. Die Funktion bleibt als toter Code stehen. Der KDoc von `AisleProductKey` nennt sie weiter als Lesepfad. Die Spec verlangt kein Entfernen. | phase-3b S22: „Im ViewModel entfallen die fünf eigenen Abfragen (Z.249, 261, 293, 414, 443)"; S21: „1. `storeDao.findAisleForProduct(AisleProductKey.normalize(productName), storeId)`" | Niedrig | [Ermessensfrage] |

**Ergebnis:** Blocker 0, Hoch 0, Mittel 0, Niedrig 2; schwerster Befund: P1. Ohne Abweichung geprüft:
- Alle Pflichtfragen: Reihenfolge Lexikon → Stichwort → Server-KI, Ausweichkette genau eine Stufe, `sonstiges`/`pfand` nie als Gang, KI nur mit aktivem Markt und nur für Artikel ohne Gang, Hinzufügen wartet nie auf das Netz.
- Algorithmus Schritte 1–6.
- S17–S23 vollständig umgesetzt, auch `kategorien.md` (Namen und Ausweichkette).
- Test-Blöcke Liste A und B sowie `RAW` sind per `diff` zeichengleich zur Spec.
- Die verlangten Tests (Liste C Fälle 1–12, S17, S19, S20, S22) sind vorhanden.

## B5 — Container
_Sub-Agent-Modell: sonnet_

### Standards — B5 — Container
| # | Datei:Zeile | Befund | Regel (Datei §) | Grad | Markierung |
|---|-------------|--------|-----------------|------|------------|
| S1 | doku/casaos-deployment.md:82 vs :95 | Zeile 82 sagt "Ohne gesetzten Wert startet der Server nicht". Zeile 95 sagt "Fehlt `API_KEY`, beendet sich der Container nicht, er wird `unhealthy`". Zeile 82 stammt aus Phase 4 und wurde in Phase 5 nicht angepasst. Beide Sätze stehen nur zwei Schritte auseinander, Leser sehen einen Widerspruch. Die akzeptierte Abweichung betrifft das Containerverhalten, nicht diese Formulierung. | server/CLAUDE.md §Deployment (Hinweis zu fehlendem `API_KEY`) | Niedrig | – |
| S2 | doku/casaos-deployment.md:90-95 | Der Hinweis "Datenordner muss UID 1000 gehören" steht nach `docker compose up -d --build`. Vermutung: Legt Docker `./data` selbst an, gehört es root. `settings.py:26` (`IMAGES_DIR.mkdir`) scheitert dann bei read_only/UID 1000, und `chown` kommt zu spät. Zudem steht "(Phase 5)" als interne Projektbezeichnung in der Betriebsdoku. | server/CLAUDE.md §Konventionen (neue Schreibpfade nur unter `/app/data`), Doku-Qualität | Niedrig | Ermessensfrage |
| S3 | server/Dockerfile:20 | Test-Stage prüft per `python -c` hartkodiert dieselben Importnamen wie `app/ai.py:261-262` (`scrape_html`, `NoSchemaFoundInWildMode`, `WebsiteNotImplementedError`). Es fehlt ein Kommentar zum Zweck. Ändert sich der Import in `ai.py`, muss die Dockerfile-Zeile mitgezogen werden. Der Plan gibt die Zeile wörtlich vor, daher als Standards-Konflikt gemeldet. | K-SM Duplicated Code / Mysterious Name | Niedrig | Ermessensfrage |
| S4 | server/Dockerfile:5,12; :33,35,36; docker-compose.prod.yml:6 | `ENV PIP_NO_CACHE_DIR… PIP_DISABLE_PIP_VERSION_CHECK…` steht in builder und test doppelt. Port 8000 steht in EXPOSE, HEALTHCHECK, CMD und Compose-Ports je einzeln. | K-SM Duplicated Code | Niedrig | Ermessensfrage |
| S5 | server/Dockerfile:29-30 | `COPY --chown=helga:helga` macht Code (`app/`, `scripts/`) für den Laufzeitnutzer beschreibbar. Ein einfaches `COPY` (root-eigen, lesbar) hätte keine Extra-Schicht und wäre restriktiver. Der Plan schreibt `--chown` vor, daher Standards-Konflikt. Die Prod-Compose mit `read_only` mildert es, das Dev-Compose nicht. | .agents/skills/multi-stage-dockerfile §Security Practices ("Set restrictive file permissions") | Niedrig | Ermessensfrage |
| S6 | server/requirements-dev.txt:2; Dockerfile:1 | `pytest>=8` ist ungepinnt, obwohl Phase 5 `recipe-scrapers` für reproduzierbare Builds pinnt. Auch das Basis-Image (`python:3.12-slim-bookworm`, ohne Patchversion oder Digest) und transitive Abhängigkeiten sind nicht fixiert. | multi-stage-dockerfile §Base Images / Phase-5-Befund 4 ("reproducible builds") | Niedrig | Ermessensfrage, ändert Verhalten (Build-Ergebnis) |
| S7 | server/.env.example:22-29 | `ALLOW_NO_AUTH`, `LOG_LEVEL`, `RECEIPT_QUEUE_INTERVAL_SEC` und `WEB_CONCURRENCY` stehen unter der Überschrift "---- KI ----". `settings.py` gruppiert sie dagegen nach Zugang, Logging und Warteschlange. `ALLOW_NO_AUTH` gehört thematisch zu `API_KEY`. | K-SM Mysterious Name (Gliederung) | Niedrig | Ermessensfrage |

Geprüft und ohne Befund:
- K-SV Schreibpfade: Im Code schreiben nur `/app/data` (`db.py:617`, `images_api.py:39`, `receipts.py:342`, `settings.py:26`) und `/tmp`. Das passt zu `read_only` und `tmpfs`.
- Healthcheck steht nur noch im Dockerfile.
- `.dockerignore` schließt `.env` und `data/` aus dem Kontext aus.
- `.env.example`-Angaben (Defaults, `low|medium|high`, 600/0) stimmen mit `settings.py` und `ai_client.py` überein.
- Die Hilfezeile `docker-test` und der Container ohne `API_KEY` sind akzeptierte Abweichungen und nicht gemeldet.

**Beifang (Altbestand, ohne Grad):**
- `.env.example:5`: `API_KEY=changeme` wird vom Start akzeptiert, `main.py:21` prüft nur auf leer. Eine unveränderte Kopie ergibt ein bekanntes Secret bei öffentlicher API.
- `doku/casaos-deployment.md:133-141`: Migration mit `--src /DATA/AppData/helga/…` im Container, dort ist dieser Pfad nicht gemountet.
- `doku/casaos-deployment.md:166`: nennt `SERVER_PATH`, das Makefile kennt nur `HOMELAB_PATH`. Zeile 175 beschreibt `make deploy` als "git pull + rebuild", das Ziel gibt aber nur einen Hinweis aus.
- `server/Makefile:5`: personenbezogener Host und IP fest eingetragen.

**Ergebnis:** 0 Blocker, 0 Hoch, 0 Mittel, 7 Niedrig; schwerster Befund: S1

### Spec — B5 — Container
| # | Datei:Zeile | Art (a/b/c/d) | Befund | Spec-Zitat (Datei, Stelle) | Grad | Markierung |
|---|-------------|---------------|--------|----------------------------|------|------------|
| P1 | doku/casaos-deployment.md:82 und :95 | b/c | Der Phase-5-Hinweis (Z. 95) sagt „Fehlt `API_KEY`, beendet sich der Container nicht, er wird `unhealthy`". Drei Zeilen darüber steht aus Phase 4 (Z. 82) noch „Ohne gesetzten Wert startet der Server nicht". Die Doku widerspricht sich also. Der API_KEY-Satz in Z. 95 ist zudem mehr als die Spec für diese Datei verlangt; der Widerspruch ist unaufgelöst geblieben. | phase-5-…md, S6: „`doku/casaos-deployment.md` (kurzer Hinweis nahe der Compose-Befehle: Datenordner muss UID 1000 gehören; Container ist `read_only`)". Der API_KEY-Satz ist dort nur für `server/CLAUDE.md` verlangt. | Niedrig | Ermessensfrage |

Geprüft ohne Befund:
- `server/Dockerfile` ist zeichengleich mit dem Codeblock in S3. Ich habe den Plan-Block extrahiert und mit `git show 451819d:server/Dockerfile` per `diff` verglichen: kein Unterschied.
- `server/.dockerignore` ist zeichengleich mit dem Block in S1 (`diff` ohne Unterschied).
- `server/requirements.txt`: nur die Zeile `recipe-scrapers==15.13.0` ist geändert (S2).
- `server/docker-compose.prod.yml`:
  - Der Healthcheck-Block ist entfernt.
  - `read_only: true`, `tmpfs: - /tmp`, `cap_drop: - ALL` und `security_opt: - no-new-privileges:true` sind vorhanden.
  - `build`, `restart`, `ports`, `volumes`, `env_file` und `logging` sind unverändert (S5).
- Das Dev-Compose ist nicht im Diff (Nicht-Ziel eingehalten). Die letzte Stage ist `runtime`, daher baut der Default-Build ohne Test-Stage.
- `server/Makefile`: `docker-test` steht unter `.PHONY` und in der Hilfe, der Befehl lautet `docker build --target test -t helga-test .` (S4). Die Hilfezeile hat eine Spalte mehr Abstand, das ist die akzeptierte Abweichung.
- `server/.env.example`: `# WEB_CONCURRENCY=2` mit dem Kommentar „Anzahl Uvicorn-Worker, Standard 2" steht drin (S6).
- Schreibzugriffe im Server-Code (`grep -rn` über `server/app` und `server/scripts`):
  - `settings.py:26` legt `IMAGES_DIR` an (Default `data/images`).
  - `images_api.py:39` und `receipts.py:342` schreiben nach `IMAGES_DIR`.
  - `db.py:564/617/618` (aiosqlite, WAL), `migrate_from_helga.py:47-49` und `restore_purged_receipts.py:59` schreiben die DB unter `/app/data`.
  - `receipt_testset.py:155` schreibt nach `--out`. Das ist ein Entwicklerskript und läuft nicht im Prod-Container.
  - Keine Verwendung von `tempfile` oder `NamedTemporaryFile`. `PYTHONDONTWRITEBYTECODE=1` ist gesetzt.
  - Ergebnis: nichts schreibt außerhalb von `/app/data` und `/tmp`.
- Beifang (ohne Grad): Der Diff-Bereich ab `2c148ab` enthält auch Phase-3a/4-Änderungen in `.env.example`, `Makefile` (`testset`), `requirements-dev.txt` und `casaos-deployment.md` Z. 82. Das ist Altbestand früherer Phasen, nicht Phase 5.

**Ergebnis:** Blocker 0, Hoch 0, Mittel 0, Niedrig 1; schwerster Befund: P1

## B7 — Auswertung und Diagramme (Checkliste)
_Sub-Agent-Modell: sonnet_

### Standards — B7 — Auswertung und Diagramme
| # | Datei:Zeile | Befund | Regel (Datei §) | Grad | Markierung |
|---|-------------|--------|-----------------|------|------------|
| S1 | CostOverviewScreen.kt:216-223,265-266,284,308 | Kein `remember` um Balken-Mapping/`periodLabel`, `shareSlices` (Sortieren/Filtern), `filter{}.map{}` der drei Karten | compose-performance §3 | Mittel | – |
| S2 | ProductPriceDetailScreen.kt:164,240-248 | `DateTimeFormatter.ofPattern`, Datumsformat, `reversed().map` ohne `remember` | compose-performance §3 | Mittel | – |
| S3 | CostOverviewScreen.kt:485-486 | `text`/`warnThreshold` im `BudgetEditDialog` mit `remember`, Eingabe geht bei Rotation verloren (Zeilen unverändert, Checklistenpunkt 6; `showBudgetDialog` korrekt) | ux-accessibility §13 Zustand | Mittel | ändert Verhalten |
| S4 | ShareDonutChart.kt:60-65 | Winkelrechnung (Lücke 1,5°, Mindest-Sweep 0,5°) im Canvas-Block statt in `ChartMath` | ux-accessibility §13 Diagramme; Plan 3b | Mittel | – |
| S5 | ReceiptStatsTest.kt:73-74; CostOverviewViewModelTest.kt:82,103 | `!!` in Testcode, weitere Fundstelle zu V1 (`KotlinQualityRulesTest` prüft nur `src/main`) | kotlin-quality §1 | Mittel | – |
| S6 | CostOverviewViewModel.kt:25 | `CostOverviewUiState` mit `List`-Feldern ohne `@Immutable` (in `ui/` hat keine UiState-Klasse die Annotation) | compose-performance §1 | Mittel | – |
| S7 | CostOverviewScreen.kt:150 | `state.loaded` wird nicht ausgewertet, vor der ersten Emission erscheint der Leerzustand | ux-accessibility §4/§5 | Niedrig | ändert Verhalten, Ermessensfrage |
| S8 | ProductPriceDetailScreen.kt:37; ProductPriceListScreen.kt:30 | Unbenutzter Import `collectAsState`; Imports unsortiert (DetailScreen:3-5,19; ListScreen:3-5,20; ListViewModel:3); voll qualifizierte Namen (CostOverviewScreen.kt:129,337; ShareDonutChart.kt:102; Test `io.mockk.coVerify`) | Kotlin-Stil | Niedrig | – |
| S9 | ReceiptStats.kt:34; ChartMath.kt:77-78 | `"%d-W%02d".format` ohne Locale für Periodenschlüssel; zwei `Regex` pro `periodLabel`-Aufruf neu gebaut | – | Niedrig | Ermessensfrage |
| S10 | CostOverviewViewModel.kt:84; SpendingBarChart.kt:95 | Wochenbudget `×12/52` im ViewModel und Label-Ausdünnung `bars.size > 8` im Composable statt in `ReceiptStats`/`ChartMath` | ux §13 Zahlenlogik | Niedrig | Ermessensfrage |

Geprüft und ohne Befund: Checklistenpunkt 1 (grep leer), Punkt 2 (alle vier Diagramme haben `description` und `semantics`), Punkt 5 (`MutableStateFlow` in beiden ViewModels privat).

**Beifang (Altbestand, ohne Grad):** keiner

**Ergebnis:** 0 Blocker, 0 Hoch, 6 Mittel, 4 Niedrig; schwerster Befund: S3

### Spec — B7 — Auswertung und Diagramme
Gelesen: Spec S8–S16, alle Diffs im Umfang und die Dateien auf Stand 451819d. Die Dateien im Umfang sind bis HEAD unverändert.

| # | Datei:Zeile | Art (a/b/c/d) | Befund | Spec-Zitat (Datei, Stelle) | Grad | Markierung |
|---|-------------|---------------|--------|----------------------------|------|------------|
| P1 | `ui/receipts/CostOverviewScreen.kt:141-151` | c | Die Budget-Karte steht vor dem Leerzustand (Reihenfolge Hinweis, Budget, Leerzustand). Die Spec nennt den Leerzustand als Punkt 2 vor der Budget-Karte (Punkt 3). | phase-3b, S15: „2. Bei `empty`: Leerzustand … und sonst nichts außer der Budget-Karte. 3. Budget-Karte" | Niedrig | [ändert Verhalten] |
| P2 | `ui/receipts/CostOverviewScreen.kt` (ganze Datei), `CostOverviewViewModel.kt:23,65` | c | `state.loaded` wird im Screen nirgends gelesen. Der Startzustand hat `empty = true`, also blitzt bis zur ersten Room-Emission „Noch keine Auswertung …" auf. Der alte Screen hatte einen `isLoading`-Zweig, der jetzt fehlt. | phase-3b, S14: `CostOverviewUiState(val loaded: Boolean, val empty: Boolean, …)`; S15 Punkt 2: Leerzustand „Bei `empty`" | Niedrig | [ändert Verhalten] |
| P3 | `ReceiptStatsTest.kt:23-40` | d | Der Datensatz hat 16 statt 15 Zeilen (Zeile 40 hängt per `+ row(...)` eine an), der Kommentar „15 Zeilen" stimmt nicht. | phase-3b, S8: „festem Datensatz (15 Zeilen, `today = 2026-11-11`)" | Niedrig | – |
| P4 | `data/receipts/ReceiptStats.kt:34` | c | `"%d-W%02d".format(...)` nutzt das Standard-Locale. Bei Geräte-Locales mit nicht-lateinischen Ziffern passen die Wochenschlüssel nicht mehr zum Server-Format `YYYY-Www`, die Wochenreihe wäre leer (Vermutung, nicht getestet). | phase-3b, S8: „Format `YYYY-Www`"; server-vertrag §6: „ISO-Woche `YYYY-Www` … zweistellig" | Niedrig | [Ermessensfrage] [ändert Verhalten] |
| P5 | `ReceiptStats.kt:95-98`, `CostOverviewScreen.kt:294-300` | c | Die Fußnote „Bon-Rabatte" summiert `total.discount`. Der Server addiert dort auch Positionsrabatte (`receipts.py:621,638`), die in den Kategorie-Beträgen schon abgezogen sind. Die Fußnote überzeichnet damit die nicht verteilten Bon-weiten Rabatte. Die Umsetzung folgt S8 und Vertrag §6 wörtlich, der Widerspruch liegt in der Spec (Vermutung zur Absicht). | phase-3b, Annahme 2: „Bon-weite Rabatte werden nicht auf Kategorien verteilt; … Fußnote" | Niedrig | [Ermessensfrage] |

Punkte, die ich geprüft habe und die der Spec entsprechen:
- **Reihenfolge der Karten:** 1, 3–8 stimmen.
- **Zeiträume:** Karte 1 zeigt immer 12 Monate bzw. 12 Wochen, die Chips wirken nur auf die Karten Märkte, Kategorien und Top-Artikel (`ReceiptStats.kt:44-48`).
- **Wochenbudget:** `budget * 12.0 / 52.0` im ViewModel (Z. 78), per Test belegt.
- **Vorläufige Bons:** Hinweis als Plural, auch im leeren Zustand sichtbar.
- **Datenquelle:** Die Diagramme lesen nur `receipt_aggregates`. Das Budget und die Anzahl vorläufiger Bons kommen aus `receipts`, das ist nach Spec zulässig.
- **Verbotene Treffer:** `grep costByStore\|CostSummary` im App-Code ist leer.
- **ViewModel-Zugriff:** `viewModel.` kommt nur in `CostOverviewScreen` vor (Treffer 1).
- **Farben:** Außerhalb von `ui/theme` gibt es kein `Color(0x`.

**Ergebnis:** Blocker 0, Hoch 0, Mittel 0, Niedrig 5 (P1–P5); schwerster Befund: P1 und P2 (beide Niedrig mit Nutzerwirkung)

## B8 — Bon-UI (Checkliste)
_Sub-Agent-Modell: sonnet_

### Standards — B8 — Bon-UI
| # | Datei:Zeile | Befund | Regel (Datei §) | Grad | Markierung |
|---|-------------|--------|-----------------|------|------------|
| S1 | ReceiptLexiconScreen.kt:152, ReceiptLexiconViewModel.kt:210 | Lexikon-Eintrag wird ohne Bestätigung oder Undo gelöscht (Server-Löschung, nicht umkehrbar) | ux-accessibility §6 | Mittel | ändert Verhalten |
| S2 | ReceiptReviewComponents.kt:246-317, 478-543 | Alle TextFields in HeaderEditDialog und EditItemDialog ohne `imeAction`/`KeyboardActions` | ux-accessibility §10 | Mittel | ändert Verhalten |
| S3 | ReceiptScanViewModel.kt:99-101 | Provisional-Zweig ohne `triggerOneShot()`. `ReceiptScanFlow.kt:57` schreibt dort `dirty = 1`. Der Trigger steht nur im Parsed-Zweig (`dirty = 0`). Folge: Push erst beim periodischen Sync (Vermutung) | sync-patterns „triggerOneShot nach Mutationen" | Mittel | ändert Verhalten |
| S4 | ReceiptLexiconScreen.kt:100-107 | Leerzustand nur Text, keine Aktion. Pull-to-Refresh auf nicht scrollbarem Box greift wohl nicht (Vermutung). Plan S32 gibt nur „Leertext" vor (Konflikt mit der Guideline) | ux-accessibility §5 | Niedrig | Ermessensfrage, ändert Verhalten |
| S5 | ReceiptScanViewModel.kt:103,140,249; ReceiptDetailViewModel.kt:79,98 | `catch (e: Exception)` ohne `CancellationException`-Rethrow | kotlin-quality §6 | Niedrig | – |
| S6 | ReceiptReviewComponents.kt:413 | Klickbarer Betrags-Text deutlich kleiner als 48 dp (die Spalte daneben ist ohnehin klickbar) | ux-accessibility §1 | Niedrig | – |
| S7 | ReceiptDetailScreen.kt:244 | Button „Erneut versuchen" schließt nur den Fehler, startet nichts neu | – | Niedrig | Ermessensfrage, ändert Verhalten |
| S8 | ReceiptDetailViewModel.kt:3; ReceiptListScreen.kt:3-7; ReceiptDetailScreen.kt:204,423; ReceiptLexiconScreen.kt:110 | Ungenutzter Compose-Import im ViewModel; Imports unsortiert; voll qualifizierte Namen statt Import | – | Niedrig | Ermessensfrage |
| S9 | ReceiptGrouping.kt:12-14; ReceiptDetailScreen.kt:65,134,181,221 | Status-Literale ("provisional", "review", "failed", …) verstreut, keine Konstanten | kotlin-quality §10 (sinngemäß) | Niedrig | Ermessensfrage |
| S10 | ReceiptLexiconScreen.kt:109 | `groupBy` ohne `remember(entries)` (die Liste nutzt es) | compose-performance §3 | Niedrig | Ermessensfrage |

**Beifang (Altbestand, ohne Grad):** `ReceiptListScreen.kt:71` `formatQuantity` nutzt `String.format("%.2f")` ohne Locale; der neue `EditItemDialog` rundet damit 0,512 beim Bestätigen auf 0,51.
**Ergebnis:** 0 Blocker, 0 Hoch, 3 Mittel, 7 Niedrig; schwerster Befund: S1 (S2 und S3 gleich schwer). Checkliste (1) keine `remember { mutableStateOf`-Treffer, (2) alle drei `clickable` mit `role`, (3) alle `items(` mit `key`, (5) alle Mutable-Flows privat, Bestätigungsdialoge für Löschen und Neu-auslesen sowie die Snackbars (Nachladen, Lexikon) vorhanden, Liste-Leerzustand mit Text und Button: ohne Befund.

### Spec — B8 — Bon-UI
| # | Datei:Zeile | Art (a/b/c/d) | Befund | Spec-Zitat (Datei, Stelle) | Grad | Markierung |
|---|-------------|---------------|--------|----------------------------|------|------------|
| P1 | ui/receipts/ReceiptReviewComponents.kt:302-317, 329-330 | c | `HeaderEditDialog` prüft nur Datum und Uhrzeit. Bei Gesamtbetrag und Bon-Rabatt gibt es weder Fehlertext noch Sperre. Ungültige Eingabe wird per `parseDecimal(..) ?: 0.0` stumm zu 0,00 (löscht den Betrag). | phase-3a, S29: „Fehlertext unter dem Feld bei ungültiger Eingabe, „Speichern" nur bei gültigen Werten" | Mittel | [ändert Verhalten] |
| P2 | ui/receipts/ReceiptListScreen.kt:205-228 | a | Im Leerzustand fehlt „Ältere Bons laden". Liegen nur ältere Bons auf dem Server, steht dort „Noch keine Bons" und es gibt keinen Weg ins Archiv. | phase-3a, S30: „Am Listenende `TextButton` „Ältere Bons laden"" und Leerzustand „Noch keine Bons…" | Mittel | [ändert Verhalten] [Ermessensfrage] |
| P3 | ui/receipts/ReceiptScanScreen.kt:358, 384-387 | c | „Positionen %1$s" zeigt `expectedTotal` (Σ minus Bon-Rabatt), nicht `itemsSum`. Mit Bon-Rabatt passt der Wert nicht zum Label. Vermutung: gewollt, damit er zur `sumMismatch`-Prüfung passt. | phase-3a, S29: „Summenzeile „Positionen %1$s · Bon %2$s" mit Warnfarbe `error` bei `sumMismatch`" | Niedrig | [ändert Verhalten] [Ermessensfrage] |
| P4 | ui/receipts/ReceiptDetailScreen.kt:242-245 | c | Im Fehlerzustand von „Neu auslesen" trägt der Button „Erneut versuchen" (`action_retry`), schließt aber nur die Meldung (`dismissReparseError`) und liest nicht neu aus. | phase-3a, S31: „`reparseState` (`Idle/Running/Error(UiText)`), `fun reparse()`" | Niedrig | [ändert Verhalten] [Ermessensfrage] |
| P5 | ui/receipts/ReceiptDetailScreen.kt:140-145 | c | „Neu auslesen" erscheint bei jedem Bon mit `imagePath`, auch bei vorläufigen. Vermutung: Ein vorläufiger Bon mit Server-Foto lässt sich so ersetzen, obwohl er nur ansehbar und löschbar sein soll. | phase-3a, Annahme 9: „schreibgeschützt (nur Ansehen und Löschen)"; S31: „nur wenn `imagePath` gesetzt" | Niedrig | [Ermessensfrage] |
| P6 | ui/receipts/ReceiptScanViewModel.kt:147-160; ReceiptDetailViewModel.kt:3 | b | `updateStoreName`/`updateTotal` werden nicht mehr aufgerufen (toter Code). Neuer, ungenutzter Compose-Import `stringResource` im ViewModel. | phase-3a, S28: Liste der ViewModel-Funktionen nennt beides nicht | Niedrig | [Ermessensfrage] |

Geprüft und spec-konform: Texte S29–S32 wörtlich, Chip-Farben, `rememberSaveable`, `Role.Button`, Icon-Zuordnung `CloudOff`/`HourglassTop`, „Bearbeiten" bei `provisional` ausgeblendet, S27-Tests.

**Ergebnis:** 0 Blocker, 0 Hoch, 2 Mittel, 4 Niedrig; schwerster Befund: P1

## B9 — Architektur aus Phase 1 (Checkliste)
_Sub-Agent-Modell: sonnet_

### Standards — B9 — Architektur aus Phase 1
Checkliste (1) und (2) leer (grep ohne Treffer). (4) alle Mutationen mit `triggerOneShot()`. (3) `markCooked`/`saveFeedback`/`toggleFeedback`/`saveSettings`/`setLocked` setzen `updatedAt` und `dirty = 1`.

| # | Datei:Zeile | Befund | Regel (Datei §) | Grad | Markierung |
|---|-------------|--------|-----------------|------|------------|
| S1 | WeekplanGenerator.kt:198-207, 339-342 | `applyProposal`/`regenerateDay` schreiben `weekplan_recipes` (Soft-Delete + Insert) und `recipe_history` einzeln ohne `withTransaction` (keine DB injiziert) | K-KQ §5; blocks.md B9 (6) | Mittel | – |
| S2 | RecipeRepository.kt:66-95 | `toggleFeedback`/`saveFeedback` schreiben `recipe_feedback` und über `recalculateRating` `recipes` ohne Transaktion | K-KQ §5 | Mittel | – |
| S3 | WeekplanRepository.kt:59; WeekplanViewModel.kt:192 | Neue `suspend recipesForDay` (Snapshot) in `combine(days, allRecipes)`; Vermutung: `weekBalance` veraltet nach add/removeRecipe. Flow `observeRecipesForDays` existiert. `daysBetween`, `recipeIdsBefore`, `searchIngredientNames` sind Einmal-Lesen, vertretbar | K-KQ §4 | Mittel | ändert Verhalten |
| S4 | WeekplanRepository.kt:54,57 | `upsertDay`/`upsertConstraints` reichen nur durch, `updatedAt`/`dirty` setzen die Aufrufer (WeekplanViewModel:289-306, 478-493); ebenso `ShoppingRepository.upsertQuickEmoji` aus SettingsViewModel. Kein Datenfehler | architecture-layers § Schreiben | Mittel | – |
| S5 | WeekplanRepository.kt:40 | Literal `"global"` statt `const val` (weitere Fundstelle zu S5 im Bericht) | K-KQ §10 | Niedrig | – |
| S6 | ServerConnectionChecker.kt:41; AiRepository.kt:40; WeekplanGenerator.kt:192,386; AiGenerate-/AiRemixViewModel:109/120; RecipeDetailViewModel:194,370 | `catch (e: Exception)` ohne `CancellationException`-Rethrow | K-KQ §6 (blocks.md) | Niedrig | ändert Verhalten |
| S7 | AiGenerateViewModel.kt:3; AiRemixViewModel.kt:3; RecipeDetailViewModel.kt:3 | Unbenutzter Import `androidx.compose.ui.res.stringResource` im ViewModel | Stil | Niedrig | – |
| S8 | RecipeRepository.kt:62; WeekplanRepository.kt:61; WeekplanFilters.kt:5-19 | KDoc von `saveFeedback` hängt an `observeFeedbackForDate`; `@OptIn` vor KDoc; KDoc verweist auf nicht mehr vorhandenes `WeekplanViewModel.applyRecipeFilters` | Doku | Niedrig | – |
| S9 | AiRepository.kt:37-41; ServerConnectionChecker.kt:43; WeekplanGenerator.kt:45,65,361; WeekplanFilters.kt:29,43 | Deutsche Nutzertexte als Literale in der Datenschicht, obwohl im Diff `UiText` eingeführt wird | CLAUDE.md Konventionen | Niedrig | Ermessensfrage |
| S10 | WeekplanGenerator.kt:36-190, 214-345 | Long Method `generate` (~155 Z.), Protein-/Aufwand-Listen mehrfach dupliziert, Constraint-Auswahl in `pickProposalReplacement`/`regenerateDay` nahezu identisch; `toggleFeedback`/`saveFeedback` doppelt; zwei gleiche Marker-Exceptions (AiGenerate/AiRemix) | Smell | Niedrig | Ermessensfrage |
| S11 | RecipeRepositoryCookTest.kt | `toggleFeedback`, `saveFeedback`, `saveSettings` ohne Test (nur `markCooked`) | – | Niedrig | Ermessensfrage |

**Beifang (Altbestand, ohne Grad):** WeekplanViewModel.kt:202 öffentliches `MutableSharedFlow` `exportEvent` (weitere Fundstelle zu V16, K-KQ §3); WeekplanViewModel.kt:44 `@OptIn` an `DaySummary`; WeekplanViewModel.kt:483 Literal `"global"`; SettingsViewModel.kt:50,296 injiziert `AppDatabase` (`clearAllTables`), architecture-layers verlangt nur Repositories/UseCases.
**Ergebnis:** Blocker 0, Hoch 0, Mittel 4, Niedrig 7; schwerster Befund: S1 (gleichrangig S2, S3, S4)

### Spec — B9 — Architektur aus Phase 1
| # | Datei:Zeile | Art (a/b/c/d) | Befund | Spec-Zitat (Datei, Stelle) | Grad | Markierung |
|---|-------------|---------------|--------|----------------------------|------|------------|
| P1 | `data/repository/AiRepository.kt:27-31`, `ui/recipes/RecipeDetailViewModel.kt:195,371` | a | `classifyRecipe`/`estimateNutrition` reichen Exceptions roh durch, die VMs zeigen `e.message` (z. B. „HTTP 500 …", IO-Rohtext). Die Block-A-Abbildung (errorBody-Detail, „Server nicht erreichbar") gilt nur in `importFromUrl` und `ServerConnectionChecker`. Vermutung: S4 verlangt nur Durchreichung, dann kein Verstoß. | phase-1, Block A: „Gemeinsame Fehlerabbildung: HttpException → errorBody, IOException → ‚Server nicht erreichbar', sonst e.message" | Niedrig | [ändert Verhalten] [Ermessensfrage] |
| P2 | `data/weekplan/WeekplanFilters.kt:15` | c | Veralteter KDoc-Verweis `[WeekplanViewModel.applyRecipeFilters]`. Die Methode liegt jetzt privat in `WeekplanGenerator.kt:392`. | phase-1, Entscheidungen Stufe B / S16: Helfer „unverändert" in die Datenschicht verschoben | Niedrig | – |
| P3 | `data/repository/RecipeRepository.kt:62-63,82` | c | KDoc „Speichert 👍/👎 zum Kochdatum …" hängt an `observeFeedbackForDate`. `saveFeedback` hat keine KDoc. | phase-1, S10: „`saveFeedback(...)`" | Niedrig | – |
| P4 | `data/sync/SyncEngine.kt:85`, `ServerReachabilityMonitor.kt:24`, `ImageUploadWorker.kt:24` | a | Drei Dienste injizieren weiter `SyncApiFactory` statt `ApiProvider`. Altbestand, nicht im Diff. `SyncEngine` ist Nicht-Ziel. | phase-1, S1: „Nur Repositories/Services injizieren künftig `ApiProvider`" | – | Beifang |

**Ergebnis:** Blocker 0, Hoch 0, Mittel 0, Niedrig 3 (P1–P3), Beifang 1 (P4); schwerster Befund: P1.

## B10 — UI-Querschnitt aus Phase 2 (Checkliste)
_Sub-Agent-Modell: sonnet_

### Standards — B10 — UI-Querschnitt aus Phase 2
| # | Datei:Zeile | Befund | Regel (Datei §) | Grad | Markierung |
|---|-------------|--------|-----------------|------|------------|
| S1 | ui/components/ColorSwatchPicker.kt:82 | Häkchen-Tint `Color.White` fest verdrahtet statt Rolle aus dem Farbschema (Kontrast auf hellen Farbtönen nicht abgesichert) | ux-accessibility.md §7, §13 | Niedrig | Ermessensfrage |
| S2 | RecipeListScreen.kt:58,69; SettingsScreen.kt:13,27,29,72; ShoppingListScreen.kt:88; WeekplanScreen.kt:98 | Durch den Diff neu ungenutzte Imports (`clickable`, `Dp`, `border`, `LazyRow`, `itemsIndexed`, `Color`). Neue Imports stehen unsortiert am Dateianfang (z. B. SettingsScreen.kt:3-4, DayMarker.kt:3-4, SyncStatusIcon.kt:3-5) | keine dokumentierte Regel | Niedrig | Ermessensfrage |
| S3 | res/values/strings.xml:449, 487-489 | Tote Strings: `receipt_list_empty`, `cost_receipts_summary`, `cost_bon_one/_other` ohne Verwendung. Die Ausnahme „S18 cost_bon_one/other“ gilt nur für die Umsetzung, nicht für ungenutzte Ressourcen | keine dokumentierte Regel | Niedrig | Ermessensfrage |
| S4 | ui/recipes/RecipeCookScreen.kt:407, 434-445 (ähnlich :658) | Timer-`SuggestionChip` liegen in einer `toggleable`-Zeile (merged Semantics). Vermutung: Der Chip-Klick ist für TalkBack nicht mehr einzeln erreichbar | ux-accessibility.md §13 „Rollen“ | Niedrig | Ermessensfrage, ändert Verhalten |

**Beifang (Altbestand, ohne Grad):**
- DayMarker.kt:26 `Color.Gray` (ux-accessibility §7).
- HelgaNavGraph.kt:244ff. Argumentnamen `navArgument("recipeId")` usw. als Literale (kotlin-quality §10). Zeilen nur eingerückt.
- Zählstrings ohne Plurals: strings.xml:105, 106, 386 (`import_url_ingredients`, `import_url_steps`, `stats_cooked`).

**Ergebnis:** Blocker 0, Hoch 0, Mittel 0, Niedrig 4; schwerster Befund: S4 (Niedrig, Vermutung). Checks (1) bis (4), (6), (7) und (8) sind sauber. Bei (5) bleibt nur WeekplanScreen.kt:451 (Scrim, dokumentierte Ausnahme). Die zweite Ausnahme SettingsScreen:998 ist durch ColorSwatchPicker entfallen. Die Arbeitskopie entspricht für `app/src` dem Stand 451819d.

### Spec — B10 — UI-Querschnitt aus Phase 2
| # | Datei:Zeile | Art (a/b/c/d) | Befund | Spec-Zitat (Datei, Stelle) | Grad | Markierung |
|---|-------------|---------------|--------|----------------------------|------|------------|
| (keine Befunde) | | | | | | |

**Ergebnis:** Blocker 0, Hoch 0, Mittel 0, Niedrig 0; schwerster Befund: keiner.

Geprüfte Checklistenpunkte, jeweils am Stand 451819d und selbst gelesen:
- **T1–T3:** In `ui/theme/Color.kt` (Z.19–38, 68–127) stimmen alle Werte der Tabelle T1 überein, also nicht nur Grün hell/dunkel und Orange hell. Die Neutralrollen aus T2 stimmen für Hell und Dunkel. `inversePrimary` ist je das `main` der anderen Helligkeit. Die Zuordnung der Akzente in `Theme.kt` Z.38–45 entspricht T3 (Lila-hell dunkel = Lila dunkel). `Color(0x` kommt außerhalb von `theme/` nicht vor.
- **S2:** `ACCENT_SYSTEM = -1` steht in `Color.kt`. `AppPreferences` nutzt `?: -1` mit Kommentar und `coerceIn(-1, 5)`. `HelgaTheme` hat den Default `ACCENT_SYSTEM`, die Bedingung `== ACCENT_SYSTEM && SDK_INT >= S` und `coerceIn(0, lastIndex)`. `MainActivity` nutzt `collectAsStateWithLifecycle(initialValue = ACCENT_SYSTEM)`. `SettingsUiState.accentColor` hat den Default `ACCENT_SYSTEM`.
- **S8:** `HelgaNavGraph.kt` Z.125–143 verwendet `rememberSaveable` für `startRoute` und `importHandled`, das Surface mit `return` und `LaunchedEffect(start)`. `rememberNavController()` steht vor dem `return`. `!!` kommt nicht vor, und `startDestination = ROUTE_ONBOARDING` ist weg.
- **S11:** Die Abhängigkeit steht in `libs.versions.toml` und `app/build.gradle.kts`. `useRail`, `navigateToRoot`, `bottomBar` nur ohne Rail, die `Row` mit `padding`, `consumeWindowInsets` und `imePadding`, die `Box` mit `TopCenter` und `widthIn(max = 840.dp)` nur bei `Expanded` entsprechen der Spec. `NavigationRail(modifier = Modifier.fillMaxHeight())` ist die akzeptierte Abweichung.
- **Nicht-Ziele:** Es gibt kein Grid, keinen Zweispalter, kein `scrollBehavior`, kein `animateItem` und keine eigene Typografie. In den Groß-Screens gibt es keine neuen Composables oder `Content`-Funktionen. In `ui/receipts` hat Phase 2 weder `rememberSaveable` noch Rollen ergänzt. `DayMarker` hat nur Shape und Preview geändert.

Nicht gemeldet: Die zweite neue Zeile `sqlite-jdbc` unter `[libraries]` stammt aus Phase 3a S15 (132f757), nicht aus Phase 2.

## B11 — Doku-Abgleich (nur Spec)
### Spec — B11 — Doku-Abgleich
| # | Datei:Zeile | Art | Befund | Spec-Zitat | Grad | Markierung |
|---|---|---|---|---|---|---|
| P1 | `.claude/guidelines/architecture-layers.md:5-11` | a | Nennt nur repository, usecase, weekplan, local/dao, remote, receipts. Es fehlen cooking, model, preferences, shopping, sync, util, wear (7 von 13 Paketen unter `data/`). ViewModels importieren sync, preferences, model, cooking und util direkt, die Regel „nur Repositories und UseCases" wirkt daher strenger als der Ist-Stand. | Punkt (3): „nennt alle Pakete unter data/" | Niedrig | – |
| P2 | `server/CLAUDE.md:75-89` | a | KI-Tabelle fehlt `POST /api/ai/nutrition`, `POST /api/ai/import-url`, `POST /api/receipts/reconcile`, `POST /api/receipts/{id}/image` und `/reparse`. Die Zeile „GET/PUT/DELETE /api/receipts…" deckt POST nicht ab. | Punkt (2): „KI-Endpunkt-Tabelle gegen EXPECTED_ROUTES" (`test_routes_guard.py:29,30,36,38,39`) | Niedrig | – |
| P3 | `server/CLAUDE.md:24-49` | a | `app/__init__.py` fehlt im Struktur-Abschnitt. Alle übrigen 20 Dateien sind genannt. | Punkt (1): „nennt jede Datei unter server/app/*.py" | Niedrig | Ermessensfrage |
| P4 | `.claude/phasen/README.md:42,44,45` | c | Phasen 2, 4, 5 stehen als „umgesetzt (Stufe B)". Ihre Phasen-Dateien haben offene Nutzer-Prüflisten (P4 Z.212,215 und P5 Z.217,220 jeweils `[ ]`; P2 Z.443 „nicht abgehakt"). Die Phase-3-Zeile nennt „Prüflisten offen", die anderen nicht. | Punkt (5): „Status der Phasen-Datei" | Niedrig | – |
| P5 | `CLAUDE.md:5` | c | Projektziel: „alle Daten liegen lokal", Server „ausschließlich Datenlager". Das widerspricht der neuen Bon-Ausnahme (`CLAUDE.md:107`, `sync-patterns.md:109-111`: Bons vollständig auf dem Server, Handy nur Zeitfenster). | Punkt (6) | Niedrig | Ermessensfrage |
| P6 | `CLAUDE.md:136` vs `sync-patterns.md:119` | c | CLAUDE.md erlaubt als Ausnahme nur die Cache-Verdrängung. Die Guideline erlaubt zusätzlich hartes Löschen im `ReceiptRepository` für nie hochgeladene Bons. | Punkt (6) | Niedrig | – |
| P7 | `server/CLAUDE.md:69` | c | Beifang, nicht im Diff: „Spalten in db.py → TABLE_COLUMNS". `TABLE_COLUMNS` liegt in `sync.py:9`, und die Guideline-Checkliste nennt `sync.py`. | Punkt (6) | – | – |
| P8 | `.claude/features/README.md:84` | c | Beifang, nicht im Diff: „115 Tests bei 131 Kotlin-Dateien" ist veraltet gegen die Stand-Zeile (181). | Punkt (4) | – | – |

**Ergebnis:** 0 Blocker, 0 Hoch, 0 Mittel, 6 Niedrig, 2 Beifang; schwerster Befund: P1. Punkt (4) ist korrekt: DB v38 und 27 Entities stimmen mit `AppDatabase.kt` überein.

## Offen am Gerät/Server (Abhakliste, gebündelt aus Phase 2–5)

Spalte „Statisch": `ja (Datei:Zeile)` = Code für die Prüfung gefunden und plausibel; `Abweichung (Nr.)` = Code passt
nicht, Befund-Nr. aus diesem Bericht; `nicht statisch prüfbar` = nur am Gerät/Server entscheidbar (Rollout, Optik,
echte KI). Die Spalte „Gerät" bleibt leer für den Nutzer.

| # | Prüfung (gekürzt) | Quellen | Statisch | Gerät |
|---|-------------------|---------|----------|-------|
| G01 | Jede der 6 Akzentfarben hell/dunkel: Akzent an FAB, Leiste, Chips; kein Lila außer bei Lila; Button-Text lesbar | P2 S1 | nicht statisch prüfbar | [ ] |
| G02 | Android 12+: Neuinstallation zeigt Wallpaper-Farben; Auswahl Grün → grün | P2 S2 | ja (`ui/theme/Theme.kt:60-62`) | [ ] |
| G03 | TalkBack liest Farbnamen und „ausgewählt"; Option „System" fehlt unter Android 12 | P2 S3 | ja (`ui/components/ColorSwatchPicker.kt:49-54`) | [ ] |
| G04 | Kaltstart im Dark Mode ohne weißes Aufblitzen; Statusleisten-Icons passend zum App-Modus | P2 S4 | nicht statisch prüfbar | [ ] |
| G05 | Nichts hinter der NavigationBar auf vier Tabs (FAB „Scannen", QuickAdd, letzter Eintrag); keine doppelte Lücke | P2 S6 | ja (`HelgaNavGraph.kt:158,180`) | [ ] |
| G06 | Kostenübersicht: Titel und Zurück sichtbar, Inhalt unter der Leiste | P2 S7 | ja (`ui/receipts/CostOverviewScreen.kt:116-120`) | [ ] |
| G07 | Startziel ohne Aufblitzen; Share-URL öffnet Import, Zurück zur Liste, Drehen öffnet nicht erneut; Abmelden → Onboarding | P2 S8 | ja (`HelgaNavGraph.kt:125-128`) | [ ] |
| G08 | Predictive Back (Android 14+): Vorschau, Sheets/Dialoge zuerst | P2 S9 | ja (`AndroidManifest.xml:18`) | [ ] |
| G09 | Tastatur verdeckt keine Felder (QuickAdd, Rezeptformular, Einstellungen, Onboarding); Sheets unverändert | P2 S10 | ja (`HelgaNavGraph.kt:181`) | [ ] |
| G10 | ≥ 600 dp: Rail statt Leiste, keine Rail auf Detail-Screens; ≥ 840 dp Inhalt mittig begrenzt (auch Kostenübersicht) | P2 S11, P3b S15 | ja (`HelgaNavGraph.kt:155-160`) | [ ] |
| G11 | TalkBack: Zeilen der Kochansicht als Kontrollkästchen mit Zustand | P2 S24 | ja (`ui/recipes/RecipeCookScreen.kt:121,407,658`); Hinweis B10-S4 (Timer-Chips in der `toggleable`-Zeile) | [ ] |
| G12 | Slot-Chip gut zu treffen, Tageszeile nicht höher; Marker-Chips hell/dunkel lesbar | P2 S25 | nicht statisch prüfbar | [ ] |
| G13 | Drehung erhält offene Dialoge/Sheets und Text (Einkaufsliste, Wochenplan, Einstellungen, Rezeptdetail, Bon-Review) | P2 S28, P3a S29 | ja (`ui/shopping/ShoppingListScreen.kt:137-139` u. a.); Hinweis B7-S3 (Budget-Dialog), B6-S4 | [ ] |
| G14 | Testset mit 10–20 echten Bon-Fotos, Bericht ansehen, ggf. `AI_RECEIPT_EFFORT=high` | P3a S12 | nicht statisch prüfbar | [ ] |
| G15 | Vor der Umschaltung: `make backup`, `restore_purged_receipts.py` erst listen, dann `--apply` | P3a S13 | ja (`server/Makefile:39`; `server/scripts/restore_purged_receipts.py:3-8`) | [ ] |
| G16 | Zwei Geräte: Bon ändern/löschen kommt an; Rezept-/Listen-Sync unverändert; Gang-Kategorie kommt an | P3a S23, P3b S18 | ja (`data/receipts/ReceiptSyncer.kt:32,56`; `data/remote/dto/SyncDto.kt:131`) | [ ] |
| G17 | Offline-Scan: vorläufig sofort, nach Netz KI-Werte und „Bitte prüfen", keine Datei mehr in `files/receipts/` (siehe V3) | P3a S25 | Abweichung (V3) | [ ] |
| G18 | Review: Uhrzeit, Adresse, Rabatt, Pfand-Chip, Kategorien; Korrektur wird beim nächsten Scan übernommen; „Produkt zuordnen"; TalkBack | P3a S29 | ja (`ui/receipts/ReceiptReviewComponents.kt:214,400,429`); Hinweis B8-P1, B8-S2 | [ ] |
| G19 | Bon-Liste: Chips, „Ältere Bons laden" (offline Meldung), Leerzustand | P3a S30 | Abweichung (B8-P2) | [ ] |
| G20 | Bon-Detail: vorläufig mit lokalem Foto, nicht bearbeitbar; „Neu auslesen"; altes Foto vom Server | P3a S31 | Abweichung (B8-P5) | [ ] |
| G21 | Lexikon: Eintrag nach Korrektur, Löschen wirkt beim nächsten Scan | P3a S32 | ja (`data/repository/ReceiptLexiconRepository.kt:18`; `ui/receipts/ReceiptLexiconViewModel.kt:22`) | [ ] |
| G22 | Ausroll-Reihenfolge: erst Server, dann App auf beiden Handys | P3a S33 | nicht statisch prüfbar | [ ] |
| G23 | Kostenübersicht: vier Diagramme hell/dunkel/alle Akzente, Budgetlinie, offline, Monat/Woche, Zeitraum-Chips, Top-Artikel → Verlauf, TalkBack | P3b S15 | ja (`ui/receipts/CostOverviewScreen.kt:215,272,290,314`); Optik nicht statisch prüfbar; Hinweis B7-P1, B7-P2 | [ ] |
| G24 | Artikel > 3 Monate ohne Kauf zeigt Preisentwicklung mit Hinweis | P3b S16 | ja (`ui/receipts/ProductPriceDetailScreen.kt:54,75-82`) | [ ] |
| G25 | Gang-Editor: neuer Markt mit Vorlage hat 15 Gänge; Ziehen funktioniert; TalkBack liest Gang und Kategorie | P3b S18 | ja (`data/repository/StoreRepository.kt:47`; `ui/stores/StoreListScreen.kt` DropdownMenu); Hinweis B6-S3, B6-S4 | [ ] |
| G26 | Tomaten, Milch, Spülmittel, Bier landen automatisch im Gang; Handkorrektur wird gelernt | P3b S22 | ja (`data/shopping/AisleAssigner.kt:43`; `data/repository/ShoppingRepository.kt:79,117`) | [ ] |
| G27 | Server-KI: Geschenkpapier/Leinsamen nach Sekunden im Gang, danach sofort; Flugmodus: sofortige Reaktion ohne Gang (siehe V6) | P3b S23 | Abweichung (V6) | [ ] |
| G28 | Server-Rollout: `API_KEY` in Prod-`.env`; Image neu bauen (Tag, `app.sh bump`, `deploy.sh`); Datenordner UID 1000; Homelab-Override überschreibt weder `read_only`/`tmpfs` noch Healthcheck | P4 Hinweis, P5 Hinweis | nicht statisch prüfbar | [ ] |
| G29 | Server nach Deploy: `healthy`, zwei Worker; Sync, Foto-Upload (auch 5–8 MB), Bon-Scan; URL-Import öffentlich ok, `http://192.168.x.x` und `http://100.64.x.x` abgelehnt; falscher `AI_API_KEY` → Fehler statt Hängen | P4 Prüfliste, P5 Prüfliste, P6 S5 | ja (`server/app/url_guard.py:29`, `server/app/settings.py:48`); `healthy`, zwei Worker, Rollout nicht statisch prüfbar | [ ] |
