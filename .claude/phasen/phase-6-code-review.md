# Phase 6 — code-review (Querschnitt über Phasen 1–5)

Skill: code-review (zwei Achsen: Standards, Spec). Basispfad `$B` = `app/src/main/kotlin/com/helga/android` ·
Testpfad `$T` = `app/src/test/kotlin/com/helga/android` · `$SA` = `server/app` · `$ST` = `server/tests` ·
`$D` = `.claude/phasen/phase-6-daten`

Datendateien (verbindlich): [blocks.md](phase-6-daten/blocks.md) (Blöcke, Pfade, Kriterien, „Nicht melden",
Schweregrade) · [prompts.md](phase-6-daten/prompts.md) (Auftragstexte der Sub-Agenten) ·
[review-bericht.md](phase-6-daten/review-bericht.md) (Gerüst des Berichts mit Vorbefunden und Abhakliste).

## Modelle
- Definition: Opus 5.5 · Umsetzung: Sonnet 5.5 (Review-Sub-Agenten: `sonnet`) · Datum: 2026-11-12

## Ziel und Bereich
Review des Gesamtdiffs der Phasen 1–5 (`2c148ab` = `e21e0d0~1` bis Ende Block A dieser Phase, 141 Commits, 249
Dateien) auf zwei getrennten Achsen: **Standards** (CLAUDE.md, `server/CLAUDE.md`, `.claude/guidelines/*`,
Code-Smell-Basis des Skills) und **Spec** (Phasen-Dateien 1–5 und `phase-3-daten/*`). Vorab werden die eindeutigen,
in Stufe A belegten Befunde behoben (Block A). Ergebnis ist ein committeter Bericht `$D/review-bericht.md` mit einem
Abschnitt je Block, einer Liste „An den Nutzer zur Entscheidung" und einer gebündelten Abhakliste „offen am
Gerät/Server". Querschnittlich über alle Bereiche des Feature-Index.

## Entscheidungen (Nutzer, 2026-11-12)
- **Fixes:** Stufe B behebt **nur** die Schritte S1–S9 dieser Datei. Neue Funde der Review werden nur berichtet, nie
  behoben.
- **Verhalten:** Was beobachtbares Verhalten ändern würde, behebt Stufe B nie; es geht mit Lösungsvorschlag in
  „An den Nutzer zur Entscheidung" (aus Stufe A: V3 Foto-Race, V5 fehlender `Location`-Header, V6 Lost Update,
  V10 DTO-Pflichtobjekte).
- **Tiefe:** B3, B2, B1, B4, B6, B5 vollständig; B7–B10 im Checklisten-/grep-Modus; B11 nur Abgleich (Spec-Achse).
- **Prüflisten Phase 2–5:** statisch plausibilisieren und als **eine** Abhakliste (G01–G29) im Bericht bündeln.
- **Bericht:** eigene Datei `$D/review-bericht.md`, je Block ein Abschnitt, Achsen getrennt, committet.
- **Schweregrade:** Blocker/Hoch/Mittel/Niedrig + Markierungen „ändert Verhalten", „Ermessensfrage"
  (Definition: `blocks.md` Abschnitt 2).
- **Standard-Konflikte:** Guidelines anpassen (S23), kein Code: reine Hilfsobjekte aus `data/receipts` in ViewModels
  erlaubt, `data/shopping` aufgenommen; Ausnahme von kotlin-quality §2 für `UiState` dokumentiert (S24).
- **Reviewer:** je Block zwei getrennte Sub-Agenten (erst Standards, dann Spec), nacheinander, Modell `sonnet`,
  Auftragstext wörtlich aus `prompts.md`.
- **Annahme (Stufe A):** Die in den Phasen 1–5 akzeptierten Abweichungen werden nicht erneut gemeldet
  (Liste: `blocks.md` Abschnitt 3).

## Befunde
Vollständige Tabelle mit Grad, Markierung und Status: `$D/review-bericht.md`, Abschnitt „Vorbefunde" (V1–V16).
Behoben werden hier: V1 (S1), V2 (S2–S4), V4 (S5), V11 (S6), V9 (S7), V12 (S8), V13 (S9); Doku: V7/V8 (S24),
V14 (S25). Nur Bericht: V3, V5, V6, V10, V15, V16.

## Schritte (Stufe B arbeitet nur diese Liste ab)

**Vorgehen je Schritt:** Änderung → Verifikation → Checkbox abhaken → Commit (Deutsch, ein Satz, beginnt mit
„Phase 6 S<n>:", Attribution laut Session-Anweisung). Ein Commit pro Schritt. Push am Ende jedes Blocks.

**Verifikation `V`** (App):
```bash
export ANDROID_HOME=/root/android-sdk ANDROID_SDK_ROOT=/root/android-sdk
./gradlew :app:testDebugUnitTest :app:compileDebugKotlin --console=plain --no-parallel --max-workers=1 --no-configuration-cache
```
Bei HTTP 429 ca. 45 s warten und wiederholen (höchstens 3 Versuche).

**Testzahl `TC`** (App, nach `V`):
```bash
cat app/build/test-results/testDebugUnitTest/*.xml | grep -o '<testsuite [^>]*' | grep -o ' tests="[0-9]*"' | grep -o '[0-9]*' | awk '{s+=$1} END {print s}'
```
**Verifikation `VS`** (Server): `cd server && python3 -m pytest -q` (Abhängigkeiten: pip-Zeile aus `server/CLAUDE.md`,
Abschnitt „Tests").

**Allgemeine Regeln für Stufe B**
- Zeilennummern sind Stand `c0b84d8`; immer per `grep -n` neu suchen.
- Läuft `V` oder `VS` nach 3 Versuchen nicht, gilt der Schritt als **nicht verifiziert**: stoppen und per `grilling`
  melden. Ebenso bei unerwartetem Code oder einem Kriterium, das sich nicht erfüllen lässt.
- Keine Änderung an Verhalten, Schema, DTOs, Endpunkten über das in S1–S9 Beschriebene hinaus.
- In Block B wird **nichts** am Code geändert, auch wenn ein Befund trivial erscheint.

### Block 0 — Basis
- [x] **S0 Baseline** — `V` und `TC` (erwartet 282), `VS` (erwartet `177 passed`); dazu
  `grep -rn '!!' $B | wc -l` (erwartet 5). Werte unter „Abschluss" notieren. Weicht eine Zahl ab: notieren,
  weitermachen.

### Block A — Vorab festgelegte Fixes (V1, V2, V4, V9, V11, V12, V13)
- [x] **S1 `!!` entfernen und absichern (V1)** — Dateien: `$B/ui/receipts/charts/SpendingBarChart.kt`,
  `$B/data/util/AisleKeywordMatcher.kt`, neu `$T/KotlinQualityRulesTest.kt`.
  `SpendingBarChart`: die Zeile `val showBudget = budget != null && budget > 0.0` ersetzen durch
  `val budgetLine: Double? = budget?.takeIf { it > 0.0 }`; in `maxValue` `if (showBudget) budget!! else 0.0` →
  `budgetLine ?: 0.0`; im Canvas `if (showBudget) { val y = … fraction(budget!!, maxValue) …` →
  `budgetLine?.let { line -> val y = size.height * (1f - ChartMath.fraction(line, maxValue)); drawLine(…unverändert…) }`;
  die Legende `if (showBudget) { … formatEuro(budget!!) … }` → `if (budgetLine != null) { … formatEuro(budgetLine) … }`
  (lokale `val`, Smart Cast greift). `showBudget` entfällt, wenn danach unbenutzt.
  `AisleKeywordMatcher` (zwei Stellen): `if (candidate != null && (best == null || candidateOrder.compare(candidate, best!!) > 0))`
  → `val current = best` direkt davor und `if (candidate != null && (current == null || candidateOrder.compare(candidate, current) > 0))`;
  in Stufe 2 analog `val current = best` und `if (current == null || candidateOrder.compare(candidate, current) > 0)`.
  `KotlinQualityRulesTest` (Aufbau wie `ReceiptProtocolRulesTest`: liest `src/main/kotlin/com/helga/android`, alle
  `.kt`): Test `kein_not_null_assertion_operator` — je Zeile den Teil vor `//` nehmen, Inhalte von String-Literalen
  `"…"` entfernen (`Regex("\"(\\\\.|[^\"\\\\])*\"")` durch `""` ersetzen); enthält der Rest `!!`, ist es ein Verstoß;
  Fehlermeldung listet `Datei:Zeile: Text`. Akzeptanz: `grep -rn '!!' $B` leer; `V` grün; `TC` = Baseline + 1.
- [x] **S2 Transaktionsmethoden im `ReceiptDao` (V2, Teil 1)** — Dateien: `$B/data/local/dao/ReceiptDao.kt`,
  `$T/ReceiptProtocolRulesTest.kt`. Unter dem Block „Cache-Verdrängung …" in `ReceiptDao` (Muster
  `ReceiptLexiconDao.replaceAll`, `import androidx.room.Transaction`) drei Methoden ergänzen:
  ```kotlin
  /** Bon und Positionen in einem Schritt schreiben (Repository, Scan). */
  @Transaction
  suspend fun upsertReceiptWithItems(receipt: ReceiptEntity, items: List<ReceiptItemEntity>) {
      upsertReceipt(receipt)
      if (items.isNotEmpty()) upsertItems(items)
  }

  /** Server-Stand übernehmen: Bon, Positionen, lokal überzählige Positionen entfernen — atomar. */
  @Transaction
  suspend fun replaceWithServerState(receipt: ReceiptEntity, items: List<ReceiptItemEntity>) {
      upsertReceipt(receipt)
      upsertItems(items)
      hardDeleteItemsExcept(receipt.id, items.map { it.id })
  }

  /** Bon samt Positionen hart löschen (Cache-Verdrängung, bestätigte Server-Löschung, nie hochgeladene Bons). */
  @Transaction
  suspend fun hardDeleteReceiptWithItems(id: String) {
      hardDeleteItemsOf(id)
      hardDeleteReceipt(id)
  }
  ```
  `ReceiptProtocolRulesTest` neuer Test `transaktionsmethoden_tragen_transaction`: in `data/local/dao/ReceiptDao.kt`
  steht für jeden der drei Namen die Zeile `@Transaction` unmittelbar (nur Leer-/Kommentarzeilen dazwischen erlaubt)
  vor `suspend fun <Name>(`. Noch keine Aufrufer ändern. Akzeptanz: `V` grün (Room baut die DAO); `TC` = S1 + 1.
- [x] **S3 `ReceiptSyncer` und `ReceiptUploader` atomar (V2, Teil 2)** — Dateien:
  `$B/data/receipts/ReceiptSyncer.kt`, `$B/data/receipts/ReceiptUploader.kt`, `$T/ReceiptSyncerTest.kt`,
  `$T/ReceiptUploaderTest.kt`. `ReceiptSyncer`: jedes Paar `hardDeleteItemsOf(x)` + `hardDeleteReceipt(x)` (in
  `pushDirty`, `pull`, `evict`) → `receiptDao.hardDeleteReceiptWithItems(x)`; `writeServerReceipt` →
  `receiptDao.replaceWithServerState(entity, items)` (gleiche `entity`/`items` wie bisher). `ReceiptUploader.replaceWithServerState`
  (private) → `receiptDao.replaceWithServerState(remote.toEntity(local).copy(pendingUpload = 0, localImageUri = ""), items)`.
  Tests anpassen, Erwartungen inhaltlich gleich: `coVerify { dao.upsertReceipt(match { A }) }` (+ ggf. `upsertItems(match { B })`,
  `hardDeleteItemsExcept(id, ids)`) wird zu `coVerify { dao.replaceWithServerState(match { A }, match { B }) }`
  (ohne `upsertItems`-Prüfung im Original: `any()` als zweites Argument); `coVerify(exactly = 0) { dao.upsertReceipt(any()) }`
  wird zu `coVerify(exactly = 0) { dao.replaceWithServerState(any(), any()) }`; `hardDeleteItemsOf(x)` + `hardDeleteReceipt(x)`
  → `hardDeleteReceiptWithItems(x)`; `coVerify(exactly = 0) { dao.hardDeleteReceipt("keep") }` →
  `coVerify(exactly = 0) { dao.hardDeleteReceiptWithItems("keep") }`. In `ReceiptUploaderTest` nur der Test
  „provisional receipt with parsed answer …" (`upsertReceipt` → `replaceWithServerState(match { … }, any())`); die
  Tests mit `upsertReceipt` für fehlende Datei und „could not parse" bleiben (Einzel-Tabellen-Schreibzugriffe).
  Akzeptanz: `grep -n 'hardDeleteItemsOf\|hardDeleteItemsExcept\|upsertItems' $B/data/receipts/*.kt` leer; `V` grün;
  `TC` unverändert gegenüber S2.
- [x] **S4 `ReceiptRepository` atomar und Regel-Test (V2, Teil 3)** — Dateien:
  `$B/data/repository/ReceiptRepository.kt`, `$T/ReceiptRepositoryConfirmTest.kt`, `$T/ReceiptProtocolRulesTest.kt`.
  Ersetzen: `scanFlow.saveLocal` → `receiptDao.upsertReceiptWithItems(receipt, items)`; `saveReceipt` →
  `receiptDao.upsertReceiptWithItems(receipt.copy(updatedAt = now, dirty = 1), items.map { … wie bisher … })` (bei leerer
  Liste schreibt die DAO-Methode nur den Bon — gleiches Verhalten); `deleteReceipt` harter Zweig →
  `receiptDao.hardDeleteReceiptWithItems(receiptId)`, weicher Zweig →
  `receiptDao.upsertReceiptWithItems(receipt.copy(deleted = 1, updatedAt = now, dirty = 1), receiptDao.itemsForReceipt(receiptId).map { it.copy(deleted = 1, updatedAt = now, dirty = 1) })`;
  `confirmReceipt` → `receiptDao.upsertReceiptWithItems(receipt.copy(…unverändert…), confirmedItems + removed)`;
  `reconcile` → `receiptDao.upsertReceiptWithItems(receipt.copy(status = "reconciled", updatedAt = now, dirty = 1), updatedItems)`
  (ersetzt beide Zeilen). Tests anpassen: in `confirmReceipt …`-Tests
  `coEvery { dao.upsertReceipt(capture(receiptSlot)) }` + `coEvery { dao.upsertItems(capture(itemsSlot)) }` →
  `coEvery { dao.upsertReceiptWithItems(capture(receiptSlot), capture(itemsSlot)) } returns Unit`; Löschtests:
  `hardDeleteItemsOf("r1")` + `hardDeleteReceipt("r1")` → `hardDeleteReceiptWithItems("r1")`,
  `coVerify(exactly = 0) { dao.upsertReceipt(any()) }` → `coVerify(exactly = 0) { dao.upsertReceiptWithItems(any(), any()) }`,
  weicher Zweig: `upsertReceipt(match { deleted == 1 && dirty == 1 })` + `upsertItem(match { id == "a" … })` →
  `coVerify { dao.upsertReceiptWithItems(match { it.deleted == 1 && it.dirty == 1 }, match { items -> items.any { it.id == "a" && it.deleted == 1 && it.dirty == 1 } }) }`,
  `coVerify(exactly = 0) { dao.hardDeleteReceipt(any()) }` → `… hardDeleteReceiptWithItems(any()) …`.
  `ReceiptProtocolRulesTest` neuer Test `bon_und_positionen_nur_ueber_transaktionsmethoden`: in allen Dateien unter
  `data/receipts/` und in `data/repository/ReceiptRepository.kt` darf keine Zeile
  `Regex("""receiptDao\.(upsertItems|hardDeleteItemsOf|hardDeleteItemsExcept|hardDeleteReceipt)\(""")` treffen.
  Akzeptanz: `V` grün; `TC` = S2 + 1; `grep -c 'upsertReceiptWithItems\|hardDeleteReceiptWithItems' $B/data/repository/ReceiptRepository.kt` ≥ 5.
  Danach nicht pushen (Push nach S9).
- [x] **S5 CGNAT sperren (V4)** — Dateien: `$SA/url_guard.py`, `$ST/test_url_guard.py`, `server/CLAUDE.md`.
  `_is_forbidden`: Rückgabe um `or not ip.is_global` erweitern (bestehende Prüfungen bleiben davor stehen);
  Docstring/Modulkommentar: „Gesperrt ist jede nicht global routbare Adresse, u. a. 100.64.0.0/10 (CGNAT, Tailscale)".
  Test: in `test_blocked_addresses` die Parameter `"http://100.64.0.1/"` und `"http://100.127.255.254/"` ergänzen.
  `server/CLAUDE.md`, Konventionen, Zeile „Rezept-Import per URL": nach „unspezifizierte Adressen" einfügen
  „sowie alles nicht global Routbare (z. B. 100.64.0.0/10)". Akzeptanz: `VS` grün mit 179 Tests;
  `test_public_address_allowed` bleibt grün.
- [x] **S6 Datei-I/O der Bon-Fotos aus dem Event-Loop (V11)** — Dateien: `$SA/receipts.py`, neu
  `$ST/test_receipt_io_threads.py`. Oben `import asyncio` ergänzen, das lokale `import asyncio` in `queue_loop` löschen.
  `save_receipt_image`: `out.write(chunk)` → `await asyncio.to_thread(out.write, chunk)`. `_ai_parse`:
  `data = (settings.IMAGES_DIR / image_name).read_bytes()` → `data = await asyncio.to_thread((settings.IMAGES_DIR / image_name).read_bytes)`.
  Sonst nichts ändern. Test: Aufbau wie Test (1) in `test_receipt_scan.py` (Fixtures `client`, `ai.parse_receipt` per
  `monkeypatch` mit erfolgreicher Attrappe); zusätzlich `monkeypatch.setattr(receipts.asyncio, "to_thread", recorder)`,
  wobei `recorder(func, *args, **kwargs)` den Aufruf in einer Liste merkt und `await original(func, *args, **kwargs)`
  zurückgibt (`original = asyncio.to_thread` vor dem Patch sichern); nach einem Scan-Aufruf enthält die Liste
  mindestens einen Aufruf mit `func.__name__ == "write"` und einen mit `func.__name__ == "read_bytes"`; Antwort
  `parsed == true`. Akzeptanz: `VS` grün mit 180 Tests; `grep -n 'read_bytes()' $SA/receipts.py` leer.
- [x] **S7 `rememberSaveable` im Gang-Menü (V9)** — Datei: `$B/ui/stores/StoreListScreen.kt`. In `AisleRow`
  `var menuOpen by remember { mutableStateOf(false) }` → `var menuOpen by rememberSaveable { mutableStateOf(false) }`
  (Import vorhanden). Kein Unit-Test (UI-Zustand, ohne Emulator nicht prüfbar; Wirkung über G13). Akzeptanz:
  `grep -n 'menuOpen by remember {' $B/ui/stores/StoreListScreen.kt` leer; `V` grün.
- [ ] **S8 `CancellationException` weiterwerfen (V12)** — Dateien: `$B/data/repository/ReceiptRepository.kt`,
  `$B/data/sync/SyncWorker.kt`, neu `$T/ReceiptRepositoryOffSuggestionsTest.kt`. Vor jedem der drei
  `catch (e: Exception)` (`suggestOffProducts`, `SyncWorker.evictOldReceipts`, `SyncWorker.syncReceipts`) einfügen:
  `catch (e: CancellationException) { throw e }` mit `import kotlinx.coroutines.CancellationException` (wie in
  `AisleAssigner`). `SyncWorker.doWork` bleibt unverändert (Altbestand). Test (Konstruktion von `ReceiptRepository`
  wie in `ReceiptRepositoryConfirmTest`, aber mit eigenem `provider: ApiProvider = mockk()` und `api: SyncApi = mockk()`,
  `coEvery { provider.api() } returns api`): (1) `coEvery { api.searchOff(any()) } throws CancellationException("abgebrochen")`
  → `assertThrows(CancellationException::class.java) { runBlocking { repo.suggestOffProducts("Milch") } }`;
  (2) `throws IOException("offline")` → `runTest { assertEquals(emptyList<OffProductDto>(), repo.suggestOffProducts("Milch")) }`.
  Akzeptanz: `V` grün; `TC` = S4 + 2.
- [ ] **S9 Leeren Companion entfernen (V13)** — Datei: `$B/data/repository/ReceiptRepository.kt`. Die zwei Zeilen
  `private companion object {` / `}` am Dateiende löschen. Akzeptanz: `grep -n 'companion object' $B/data/repository/ReceiptRepository.kt`
  leer; `V` grün. Danach Block A pushen.

### Block B — Review-Läufe (nur lesen, nichts am Code ändern)
Ablauf je Lauf R1–R11 (ein Schritt = ein Block): (1) die zwei Auftragstexte aus `$D/prompts.md` (Vorlage S, dann
Vorlage P; bei B11 nur P) mit den Angaben des Blocks aus `$D/blocks.md` füllen; (2) Sub-Agent mit Modell `sonnet` und
frischem Kontext starten, Antwort abwarten, dann den zweiten; (3) beide Antworten wörtlich unter dem Block-Abschnitt in
`$D/review-bericht.md` einfügen, Bereinigung nur nach der Regel im Kopf des Berichts; (4) unklare oder offensichtlich
falsche Befunde **nicht** selbst bewerten, sondern mit dem Zusatz „(von Stufe B nicht nachgeprüft)" stehen lassen;
(5) Commit „Phase 6 S<n>: Review <Block>". Akzeptanz je Schritt: Abschnitt enthält beide Tabellen (bei B11 eine) mit
den Kopfzeilen aus `prompts.md` und die Zeile „Ergebnis"; `git diff --stat` zeigt nur `$D/review-bericht.md`.
- [ ] **S10 Review-Bereich festlegen** — `git rev-parse --short HEAD` nach S9 = `$H`. In `$D/review-bericht.md` die
  Zeile „Review-Bereich" auf `2c148ab..$H` setzen; Prüfung `git diff --stat 2c148ab $H | tail -1` (erwartet etwa
  255 Dateien) unter „Abschluss" notieren. Commit.
- [ ] **S11 R1 — B3 Server-Bon-Archiv (voll)**
- [ ] **S12 R2 — B2 Bon-Protokoll App (voll)**
- [ ] **S13 R3 — B1 Migrationen, Schema, DAOs (voll)**
- [ ] **S14 R4 — B4 Server-Sicherheit und -Struktur (voll)**
- [ ] **S15 R5 — B6 Auto-Zuordnung zum Gang (voll)**
- [ ] **S16 R6 — B5 Container (voll)**
- [ ] **S17 R7 — B7 Auswertung und Diagramme (Checkliste)**
- [ ] **S18 R8 — B8 Bon-UI (Checkliste)**
- [ ] **S19 R9 — B9 Architektur aus Phase 1 (Checkliste)**
- [ ] **S20 R10 — B10 UI-Querschnitt aus Phase 2 (Checkliste)**
- [ ] **S21 R11 — B11 Doku-Abgleich (nur Spec)** — danach Block B pushen.

### Block C — Bericht, Abhakliste, Doku
- [ ] **S22 Abhakliste G01–G29** — Datei: `$D/review-bericht.md`. Für jede Zeile die Spalte „Statisch" füllen: die
  in der Quelle genannte Funktion per `grep -rn` im Code suchen (z. B. G08: `enableOnBackInvokedCallback` im Manifest;
  G17: Zweige in `ReceiptScanFlow`/`ReceiptUploader`; G29: `is_global` in `url_guard.py`) und `ja (Datei:Zeile)`,
  `Abweichung (Nr.)` (nur wenn ein Befund aus Block B oder V1–V16 genau diese Prüfung betrifft) oder
  `nicht statisch prüfbar` (nur bei Rollout-, Optik- und Echt-KI-Prüfungen: G01, G04, G12, G14, G22, G23 Optik-Teil,
  G28) eintragen. Spalte „Gerät" bleibt `[ ]`. Akzeptanz: `grep -c '<…>' $D/review-bericht.md` zählt nur noch die
  Platzhalter von S23 (Zusammenfassung, Nutzerliste).
- [ ] **S23 Zusammenfassung und Nutzerliste** — Datei: `$D/review-bericht.md`. „Zusammenfassung": Tabelle mit Zeilen
  B1–B11 und Spalten „Standards: Blocker/Hoch/Mittel/Niedrig" und „Spec: Blocker/Hoch/Mittel/Niedrig" (Zahlen aus den
  „Ergebnis"-Zeilen), darunter je Achse ein Satz mit dem schwersten Befund (Nr. und Block) — keine Wertung über beide
  Achsen hinweg. „An den Nutzer zur Entscheidung": V3, V5, V6, V10 (Vorschlag aus der Vorbefund-Tabelle übernehmen)
  und jeder Befund aus Block B mit Markierung „ändert Verhalten" oder Grad Blocker/Hoch (Vorschlag: der Satz aus dem
  Befund, sonst „Vorschlag offen"). Akzeptanz: `grep -c '<' $D/review-bericht.md` liefert nur noch Treffer aus
  Tabelleninhalten (keine `<S2…`-Platzhalter); Commit.
- [ ] **S24 Guidelines anpassen (V7, V8; kein Code)** — Dateien: `.claude/guidelines/architecture-layers.md`,
  `.claude/guidelines/kotlin-quality.md`, `.claude/guidelines/sync-patterns.md`.
  `architecture-layers.md`: (a) im Diagramm unter `data/receipts/` eine Zeile
  `data/shopping/   (Dienst der Gang-Zuordnung: AisleAssigner)`; (b) den Satz „`data/receipts/` … ViewModels nicht
  direkt." ersetzen durch: „`data/receipts/` ist das Dienst-Paket des Bon-Archivs. Dienste mit Netz oder Room
  (`ReceiptSyncer`, `ReceiptUploader`, `ReceiptImageStore`, `ReceiptScanFlow`) nutzen nur Repositories und Worker.
  **Reine Hilfsobjekte ohne Room/Netz** (`ReceiptStats`, `ReceiptReviewCheck`, `ReceiptDates`, `ReceiptScanOutcome`)
  dürfen ViewModels und Screens direkt verwenden (Entscheidung Phase 6)."; (c) Satz ergänzen: „`data/shopping/`
  (`AisleAssigner`) wird nur von `ShoppingRepository` und `AisleCategorizeWorker` genutzt." —
  `kotlin-quality.md` §2: am Ende „**Ausnahme:** `ui/common/UiState.kt` bleibt `sealed class UiState<out T>`, weil
  CLAUDE.md und Phase 1 (S2) es so festlegen (Entscheidung Phase 6). Neue Status-Typen: `sealed interface`."; §1 am
  Ende: „Abgesichert per Test: `KotlinQualityRulesTest` schlägt bei `!!` fehl." ; §5 am Ende: „Bons: Bon und Positionen
  nur über `ReceiptDao.upsertReceiptWithItems`, `replaceWithServerState`, `hardDeleteReceiptWithItems` (geprüft durch
  `ReceiptProtocolRulesTest`)." — `sync-patterns.md`, Abschnitt „Server-Archiv": Zeile ergänzen „Bon und Positionen
  werden nur gemeinsam und atomar geschrieben bzw. gelöscht (`@Transaction`-Methoden im `ReceiptDao`)." Akzeptanz:
  `grep -c 'Phase 6' .claude/guidelines/architecture-layers.md .claude/guidelines/kotlin-quality.md` je ≥ 1; `V` grün.
- [ ] **S25 Phase-1-Datei klarstellen (V14)** — Datei: `.claude/phasen/phase-1-android-clean-architecture.md`. Direkt
  unter der Überschrift „### Abschluss Stufe B (Stand: Stopp bei S16)" und unter „### Stand nach Entscheidungen
  Stufe B (STOPP bei S17 …)" je eine Zeile einfügen: „> Überholt (Phase 6): S17 mit 555 Zeilen vom Nutzer akzeptiert;
  S18–S20 umgesetzt (Commits `58126ab`, `40f4d67`)." Sonst nichts ändern. Akzeptanz: `grep -c 'Überholt (Phase 6)'` = 2.
- [ ] **S26 Index und Pläne** — Dateien: `.claude/features/README.md` (Zeile „`!!`-Operator": Zahl → „0 (Phase 6,
  abgesichert durch `KotlinQualityRulesTest`)"; neue Zeile unter „Bereichsübergreifende Befunde":
  „Code-Review Phasen 1–5 | Bericht: `.claude/phasen/phase-6-daten/review-bericht.md` (Entscheidungen offen: siehe
  ‚An den Nutzer zur Entscheidung') | alle Guidelines"), `.claude/phasen/README.md` (Phase-6-Zeile Status →
  „umgesetzt (Stufe B; Nutzerentscheidungen und Geräte-Abhakliste offen) → [phase-6](phase-6-code-review.md) ·
  [Bericht](phase-6-daten/review-bericht.md)"), `.claude/features/sync/plan.md` und
  `.claude/features/bons-kosten/plan.md` (je Abschnitt „Phase 6": eine Zeile mit Verweis auf den Bericht und die
  behobenen Punkte V2/V11 bzw. V1/V2). Akzeptanz: Dateien geändert; `V` und `VS` grün; Abschnitt „Abschluss" ausgefüllt.
  Danach pushen.

## Nicht-Ziele
- Beheben von Befunden aus Block B, von V3, V5, V6, V10, V15, V16 und jeder Änderung mit „ändert Verhalten".
- Review der generierten Schemas, der Phasen-Dateien selbst und von Altbestand außerhalb des Diffs.
- Docker-Builds (kein Daemon nötig; B5 wird statisch geprüft); Gerätetests; echte KI-Aufrufe.
- Neue Abhängigkeiten, Schema-, DTO- oder Endpunkt-Änderungen; Aufteilen großer Dateien.
- Erneute Meldung der in Phase 1–5 akzeptierten Abweichungen.

## Abschluss
- [x] Baseline S0: App `TC` 282 (erwartet 282), Server 177 passed (erwartet 177), `!!` 5 (erwartet 5)
- [ ] Nach Block A: App `TC` <…> (erwartet 287 = 282 + 1 S1 + 1 S2 + 1 S4 + 2 S8), Server <…> (erwartet 180 = 177 + 2 S5 + 1 S6), `!!` 0
- [ ] Review-Bereich `2c148ab..<$H>`, `git diff --stat` <…>
- [ ] Je Block: Sub-Agent-Modell und Anzahl Befunde je Achse (aus der Zusammenfassung des Berichts)
- [ ] Endstand `V`/`VS` grün nach S26 (Zahlen)
- [ ] Bericht committet; „An den Nutzer zur Entscheidung" und Abhakliste G01–G29 offen für den Nutzer
- [ ] Gemeldete Abweichungen von diesem Plan (je Schritt eine Zeile)
