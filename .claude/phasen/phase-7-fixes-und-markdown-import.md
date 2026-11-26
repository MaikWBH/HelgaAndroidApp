# Phase 7 — Fixes aus dem Testlauf + Markdown-Rezept-Import

Basispfad `$B` = `app/src/main/kotlin/com/helga/android` · Testpfad `$T` = `app/src/test/kotlin/com/helga/android` ·
`$R` = `app/src/main/res` · `$SA` = `server/app` · `$ST` = `server/tests`

Quellen: Befunde F1–F19 in [testplan.md](testplan.md) Abschnitt 7 (F19 = Dauer roh „PT30M", Screenshot des Testlaufs);
Nutzerliste in [phase-6-daten/review-bericht.md](phase-6-daten/review-bericht.md) („An den Nutzer zur Entscheidung");
überholter Entwurf: [phase-7-entwurf.md](phase-7-entwurf.md). Folgephase: [phase-8](phase-8-server-haertung-und-robustheit.md).

## Modelle
- Definition: Opus 5.5 · Umsetzung: Sonnet 5.5 · Datum: 2026-11-26

## Ziel und Bereich
Die Befunde des Testlaufs vom 25.11.2026 beheben, Datenverlust zuerst (F13: vorläufiger Bon verliert seine OCR-Werte),
zusammen mit den Bon-Protokoll-Punkten der Nutzerliste. Danach Drehen (F1/F2/F10), Bon-Liste und Eingaben (F11/F12),
Sync-Anzeige (F3), Optik (F4, F5, F8, F9, F14, F15, F17, F19), Server und Build (F7, F18, F6). Zuletzt das neue Feature
**Markdown-Rezept-Import** (Bereich Rezepte, [features/rezepte/plan.md](../features/rezepte/plan.md)): Ein Tipp auf
„Markdown importieren" im FAB des Rezepte-Tabs legt eine feste KI-Vorlage in die Zwischenablage; der Nutzer gibt sie mit
Rezeptwunsch an Gemini, fügt die Antwort ein, sieht eine Vorschau und speichert ein oder mehrere Rezepte. Betroffen:
Bons (`data/receipts`, `ui/receipts`, `server/app/receipts*.py`), Wochenplan, Rezepte, Einstellungen, Server, Build.

## Entscheidungen (Nutzer, 2026-11-26; Fragerunden über den Koordinator)
- **Umfang:** Phase 7 = Blöcke A, B1–B6, C, E. Server-Härtung und App-Robustheit der Nutzerliste (Runden 3 und 4) und die
  Doku-statt-Code-Fälle gehen in **Phase 8**. B5-S6 (Pinning) bleibt offen.
- **F13:** beide Seiten, Server zuerst. Server legt die Hülle mit `updated_at = captured_at if captured_at > 0 else now`
  an, in beiden Zweigen (mit und ohne `preliminary`). App setzt `updatedAt = maxOf(now, server.updatedAt + 1)`.
  `ReceiptDao.upsertReceipt(s)` wird `@Upsert` statt `REPLACE` (kein CASCADE mehr). Tote Repository-Methoden fallen weg.
- **Bon-Protokoll (Nutzerliste Runde 2, alles umsetzen):** V3 `cleanupOrphans` schont Dateien jünger als 1 h ·
  B2-S1 Server-Antwort nur übernehmen, wenn die lokale Zeile nicht neuer und `dirty` ist · B8-S3 `triggerOneShot()` im
  Vorläufig-Zweig · B2-S2 Löschzweig im Pull schont `pendingUpload = 1` · B2-S3 „nie hochgeladen" nur bei
  `pendingUpload = 1` · B3-P1 `_set_parse_state` stempelt `server_seq` · B3-P2 nach 5 Fehlversuchen `failed` (auch bei
  Re-Parse und Scan) · B2-P4 HTTP 400/413/422 beim Push setzen `parseStatus = "rejected"` und `dirty = 0`.
- **F18:** Regex `[A-Za-z0-9_-]{1,64}` mit `fullmatch` auf **allen** `/{receipt_id}`-Routen und beim Scan; Antwort
  400 „Ungültige Bon-ID" (wie bisher beim Scan). Deckt B3-S2 ab.
- **F6:** ML Kit Text Recognition 16.0.1, CameraX 1.4.2, DataStore 1.1.7, `androidx.graphics:graphics-path` 1.0.1 als
  Constraint, `abiFilters` nur `arm64-v8a` und `x86_64` (32-Bit-Geräte entfallen; löst `libbarhopper_v3`, Barcode
  17.3.0 ist schon die neueste Version). Danach `zipalign -c -P 16` und `readelf` prüfen und den Befund notieren. Ob die
  Versionen 16-KB-tauglich sind, ist eine **Vermutung**; ein negativer Befund blockiert den Schritt nicht.
- **F5:** Orange hell `#E65100` → `#BF360C` (weiß darauf ≈ 5,6 : 1). Lila bleibt Tertiärfarbe bei Grün/Blau/Petrol;
  G01-Wortlaut im Testplan korrigiert (Stufe A).
- **F4:** inaktive bedienbare Symbole `alpha = 0.6f` statt `0.4f`; disabled (Neu-Erzeugen bei Sperre) bleibt 0.4.
- **F8:** kürzeres Label „Vorb." (neuer String `recipe_form_prep_time`).
- **Markdown-Import:** Vorschau, dann Speichern · mehrere Rezepte je Text, getrennt durch `---`/`***`/`___` oder eine neue
  `# `-Zeile · erweitertes Format, nur der Titel ist Pflicht · Rezept mit schon vorhandenem Namen (oder doppelt im
  Text) erscheint **abgewählt** mit Hinweis · gültige Blöcke werden gespeichert, fehlerhafte mit Grund angezeigt.
- **Festgelegt in Stufe A (technisch):**
  - Zwischenablage über `android.content.ClipboardManager` + `ClipData.newPlainText(label, text)` (Label „Helga
    Rezept-Vorlage"); kopiert wird beim ersten Öffnen des Import-Screens (`rememberSaveable`-Merker, nicht erneut beim
    Drehen) und über „Erneut kopieren". Eigene Snackbar „kopiert" nur bis API 32 (ab 33 bestätigt das System).
  - Dezimalregel (`DecimalInput`, S10): Leerzeichen und `€` entfernen; stehen `.` und `,` beide im Text, ist der zuletzt
    stehende das Dezimalzeichen, der andere wird entfernt; danach höchstens ein Dezimalzeichen, sonst ungültig;
    erlaubt `^-?(\d+(\.\d*)?|\.\d+)$`.
  - Regeltest gegen `remember` für Eingaben mit fester Erlaubnisliste (S9).
  - `MealSlots` zieht nach `data/model/MealSlots.kt` (Schichtregel: `data/util` darf nicht aus `ui/` importieren).
  - Dauern werden als ISO-8601 gespeichert (wie URL-Import, `_mins_to_iso`); `RecipeDuration` liefert Minuten, die
    Anzeige formatiert über Strings (`duration_*`). Das Rezeptformular zeigt weiterhin den Rohwert (Nicht-Ziel).
  - Klassifizierung nach dem Markdown-Import nur für Rezepte mit mindestens einem leeren Feld
    (`cuisine`, `effort`, `proteinType`, `seasonFit` leer oder `mealSlot == "other"`), höchstens 5 Rezepte je Import,
    nacheinander, je `withTimeoutOrNull(15_000)`, **vor** der Navigation; Priorität `recipe.x.ifBlank { result.x }`
    (bei `mealSlot`: nur wenn `"other"`); Fehler still, `CancellationException` wird weitergeworfen.
  - `recipeYield` wird wie geliefert gespeichert (z. B. „4"), kein Zusatz „Portionen" (wie URL-Import).
  - F9/F17: gemeinsame Konstante `FabListBottomPadding = 96.dp` (56 dp FAB + 16 dp Rand + 24 dp Luft).
  - F15: `ChartMath.labelStep(count) = if (count > 8) 3 else 1`, Labels dürfen ihren Slot überlaufen.
  - F11: Suche bis 12 Monate zurück bis zum ersten Monat mit Bon; Cursor gilt 24 h (B2-P2).
  - B6-S4: `draggedId` wird `remember` (Geste endet mit dem Konfigurationswechsel), `dragOffset` bleibt `remember`.

## Befunde
| # | Datei:Zeile (Stand `99735ec`) | Befund | Regel |
|---|-------------------------------|--------|-------|
| F13-B | `$B/data/receipts/ReceiptScanFlow.kt:55-61`; `$SA/receipts.py:433-445,118-119`; `$B/data/receipts/ReceiptSyncer.kt:42-44` | Hülle des Servers ist neuer als die lokalen OCR-Werte → PUT abgelehnt → `replaceWithServerState` löscht Positionen | sync-patterns LWW |
| F13-A | `$B/data/local/dao/ReceiptDao.kt:31-35`; `$B/data/receipts/ReceiptUploader.kt:66,89` | `@Insert(REPLACE)` + `ON DELETE CASCADE` löscht Positionen; bei fehlender Datei folgt ein PUT ohne Positionen (Vermutung) | kotlin-quality §5 |
| V3, B2-S1/S2/S3, B2-P4, B3-P1/P2, B8-S3 | siehe Bericht Phase 6 | Bon-Protokoll | sync-patterns Server-Archiv |
| F1/F2/F10 | `$B/ui/receipts/CostOverviewScreen.kt:485-486`; `$B/ui/weekplan/WeekplanScreen.kt:658`; `$B/ui/recipes/RecipeDetailScreen.kt:703` | `remember` statt `rememberSaveable` | ux §13 |
| F11 | `$B/ui/receipts/ReceiptListScreen.kt:156-225`; `$B/data/repository/ReceiptRepository.kt:257-286` | Kein „Ältere Bons laden" im Leerzustand; ein Monat je Druck ohne Rückmeldung | ux §5 |
| F12 | `$B/ui/receipts/ReceiptReviewComponents.kt:85,329-330,450-464` | ungültiger Betrag wird stumm 0,00 | ux §3 |
| F3 | `$B/data/sync/SyncEngine.kt:103-104`; `$B/ui/settings/SettingsScreen.kt:606` | Pull-Cursor als Datum angezeigt | – |
| F4 | `$B/ui/weekplan/WeekplanScreen.kt:574,581,588,596,947,956` | Kontrast 1,4–2,4 : 1 | ux §7 |
| F5 | `$B/ui/theme/Color.kt:23,45` | weiß auf `#E65100` = 3,8 : 1 | ux §7 |
| F8 | `$B/ui/recipes/RecipeFormScreen.kt:140` | Label bricht um | – |
| F9/F17 | `$B/ui/recipes/RecipeDetailScreen.kt:351-354`; `$B/ui/receipts/ReceiptListScreen.kt:186,206` | FAB überdeckt Listenende | ux §12 |
| F14 | `$B/ui/receipts/CostOverviewScreen.kt:236-257` | Chip „Gesamt" bricht um | – |
| F15 | `$B/ui/receipts/charts/SpendingBarChart.kt:94-106` | Achsenbeschriftung abgeschnitten | – |
| F19 | `$B/ui/recipes/RecipeListScreen.kt:407-410`; `$B/ui/recipes/RecipeDetailScreen.kt:518-522`; `$B/ui/ai/AiRemixScreen.kt:214-216` | Dauer roh „PT30M" | ux §11 |
| F7 | `$SA/main.py:36`; `$SA/ai_client.py:160-169` | Docs ohne Schlüssel; leerer KI-Schlüssel ruft Anbieter | server/CLAUDE.md |
| F18 | `$SA/receipts.py:19,334`; `$SA/receipt_api.py:89-131` | ID-Regex nur beim Scan, `match` statt `fullmatch` | – |
| F6 | `gradle/libs.versions.toml:18,33,34`; `app/build.gradle.kts:18-31` | Native Bibliotheken nicht 16-KB-ausgerichtet | – |

## Schritte (Stufe B arbeitet nur diese Liste ab)

**Vorgehen je Schritt:** Änderung → Verifikation → Checkbox abhaken → Commit (Deutsch, ein Satz, beginnt mit
„Phase 7 S<n>:", endet mit den Attributionszeilen aus der Session-Anweisung). Ein Commit pro Schritt. **Push** am Ende
jedes Blocks: `git pull --ff-only origin <branch> && git push -u origin <branch>` (die Cloud-Umgebung hat GitHub-Zugang).

**Verifikation `V`** (App):
```bash
export ANDROID_HOME=/root/android-sdk ANDROID_SDK_ROOT=/root/android-sdk
./gradlew :app:testDebugUnitTest :app:compileDebugKotlin --console=plain --no-parallel --max-workers=1 --no-configuration-cache
```
Bei HTTP 429 ca. 45 s warten und wiederholen (höchstens 3 Versuche). Auf dem PC: `./gradlew …` ohne `export`.

**Testzahl `TC`** (App, nach `V`):
```bash
cat app/build/test-results/testDebugUnitTest/*.xml | grep -o '<testsuite [^>]*' | grep -o ' tests="[0-9]*"' | grep -o '[0-9]*' | awk '{s+=$1} END {print s}'
```
**Verifikation `VS`** (Server, Cloud): `cd server && python3 -m pytest -q`. Fehlen Pakete: einmal
`pip install fastapi==0.115.5 aiosqlite==0.20.0 httpx==0.28.1 python-dotenv==1.0.1 python-multipart==0.0.20 pytest`
(nicht `.venv`, nicht `requirements.txt`). Auf dem PC: `cd server && .venv/bin/python -m pytest -q`.

**Verifikation `VA`** (16-KB-Ausrichtung, nur S0 und S25):
```bash
export ANDROID_HOME=/root/android-sdk ANDROID_SDK_ROOT=/root/android-sdk
./gradlew :app:assembleDebug --console=plain --no-parallel --max-workers=1 --no-configuration-cache
APK=$(ls app/build/outputs/apk/debug/*.apk | head -1); echo "$APK"
unzip -l "$APK" | grep -o 'lib/[^ ]*\.so' | sort
$ANDROID_HOME/build-tools/35.0.0/zipalign -c -P 16 -v 4 "$APK" | tail -n 3
rm -rf /tmp/va && mkdir /tmp/va && unzip -q -o "$APK" 'lib/*' -d /tmp/va
for so in /tmp/va/lib/*/*.so; do echo "$so $(readelf -lW "$so" | awk '/LOAD/{print $NF}' | sort -u | tr '\n' ' ')"; done
```
Erwartet für tauglich: Ausrichtung `0x4000` (oder größer) je LOAD-Segment, `zipalign` meldet „Verification successful".

**Allgemeine Regeln für Stufe B**
- Zeilennummern sind Stand `99735ec`; vor jeder Änderung per `grep -n` neu suchen.
- Läuft `V`/`VS` nach 3 Versuchen nicht grün, gilt der Schritt als **nicht verifiziert**: stoppen und per `grilling`
  melden. Ebenso bei unerwartetem Code oder einem Kriterium, das sich nicht erfüllen lässt.
- Keine Änderung über das Beschriebene hinaus; kein Room-Schema-Wechsel (Akzeptanz am Phasenende:
  `git diff --stat 99735ec -- app/schemas` leer), keine neuen Endpunkte, keine neuen Abhängigkeiten außer S25.
- Neue UI-Texte nur in `$R/values/strings.xml` (deutsch), Wortlaut wie angegeben. Neue Tests mit den angegebenen
  Namen und der angegebenen Anzahl `@Test`-Funktionen (Python: Testfunktionen; ein `parametrize` mit n Werten zählt n).
- Weicht eine Testzahl ab: notieren, nicht stoppen, solange `V`/`VS` grün sind.

### Block A — Baseline
- [x] **S0 Baseline** — `V` und `TC` (erwartet 287), `VS` (erwartet `180 passed`), `VA` (Liste der `.so` je ABI,
  Ausgabe von `zipalign` und die LOAD-Ausrichtung je Datei). Alle Werte unter „Abschluss" notieren. Kein Commit nötig,
  wenn sich keine Datei ändert; die Werte werden mit S1 committet.

### Block B1 — F13 Datenverlust und Bon-Protokoll (Server zuerst)
- [x] **S1 Server: Hülle mit `captured_at` (F13 Ursache B)** — Dateien: `$SA/receipts.py`, `$ST/test_receipt_scan.py`.
  In `mark_failed_attempt` nach `now = now_ms()` die Zeile `stamp = captured_at if captured_at and captured_at > 0 else now`
  einfügen und in **beiden** Payloads `"updated_at": now` durch `"updated_at": stamp` ersetzen (Zweig `preliminary` und
  Zweig ohne). `purchase_date` bleibt `captured_at or now`. Tests (+2): `test_shell_uses_captured_at_as_updated_at`
  (KI-Fehler ohne `preliminary` → `receipt["updated_at"] == CAPTURED`; danach `PUT` mit `updated_at = CAPTURED` und
  einer Position → Antwort `applied == True` und die Position steht im Bon) und
  `test_preliminary_uses_captured_at_as_updated_at` (KI-Fehler mit `preliminary` → `updated_at == CAPTURED`,
  Positionen aus `preliminary`). Akzeptanz: `VS` grün, 182 Tests.
- [x] **S2 Server: Parse-Status sichtbar und begrenzt (B3-P1, B3-P2)** — Dateien: `$SA/receipts.py`,
  `$ST/test_receipt_queue.py`. `_set_parse_state`: zuerst `seq = await next_seq(db)`, dann `server_seq = ?` zusätzlich
  setzen (`columns = {**columns, "server_seq": seq}`). In `reparse` (Misserfolg) und `mark_failed_attempt` den Status wie
  in `process_parse_queue` bilden: `attempts = await _attempts(db, receipt_id) + 1`,
  `parse_status="failed" if attempts >= MAX_PARSE_ATTEMPTS else "queued"`, `parse_attempts=attempts`. Tests (+2):
  `test_parse_state_change_bumps_server_seq` (Bon per PUT anlegen, `server_seq` merken, Warteschlange mit fehlschlagender
  KI-Attrappe einmal laufen lassen → `GET /api/receipts?since_seq=<gemerkt>` liefert den Bon) und
  `test_reparse_marks_failed_after_max_attempts` (Bon mit Foto, KI-Attrappe schlägt fehl, fünfmal
  `POST /api/receipts/{id}/reparse` → `parse_status == "failed"`). Muster: bestehende Tests in derselben Datei und in
  `test_receipt_scan.py`. Akzeptanz: `VS` grün, 184 Tests.
- [x] **S3 App: lokaler Stand neuer als die Hülle (F13 Ursache B, B8-S3)** — Dateien: `$B/data/receipts/ReceiptScanFlow.kt`,
  `$B/ui/receipts/ReceiptScanViewModel.kt`, `$T/ReceiptScanFlowTest.kt`. In `run` im Zweig nach `if (response.parsed)`:
  `val stamp = maxOf(now, response.receipt.updatedAt + 1)`; an `provisional(receiptId, bitmap, shoppingListId, stamp)`
  übergeben (setzt `updatedAt` von Bon und Positionen). Kommentar: „Hülle des Servers darf nicht neuer sein als die
  OCR-Werte (Phase 7, F13)." `ReceiptScanViewModel.scanImage`, Zweig `is ReceiptScanOutcome.Provisional`: vor dem
  Setzen des Zustands `syncScheduler.triggerOneShot()` (Feld ist vorhanden). Test (+1):
  `` `server shell newer than the device keeps the provisional values` `` — `response(false)` mit
  `updatedAt = now + 5_000` → `savedReceipt.updatedAt == now + 5_001`, alle `savedItems.updatedAt == now + 5_001`,
  `storeName == "Lidl"`. Bestehender Test „server side ai failure …" bleibt unverändert grün (`updatedAt = 50`).
  Akzeptanz: `V` grün; `TC` = 288.
- [x] **S4 `@Upsert` für Bons, tote Methoden weg (F13 Ursache A)** — Dateien: `$B/data/local/dao/ReceiptDao.kt`,
  `$B/data/repository/ReceiptRepository.kt`, `$T/ReceiptProtocolRulesTest.kt`. In `ReceiptDao` bei `upsertReceipt` und
  `upsertReceipts` `@Insert(onConflict = OnConflictStrategy.REPLACE)` → `@Upsert` (`import androidx.room.Upsert`;
  `upsertItem`/`upsertItems` bleiben `REPLACE`, Positionen haben keine Kinder). KDoc über `upsertReceipt`:
  „Kein REPLACE: das würde per ON DELETE CASCADE alle Positionen löschen (Phase 7, F13)." In `ReceiptRepository` die
  Methoden `updateReceipt`, `linkReceiptToShoppingList`, `updateReceiptStore` löschen (vorher
  `grep -rn "updateReceipt(\|linkReceiptToShoppingList\|updateReceiptStore" app/src wear/src` → nur die Definitionen; sonst
  stoppen). Regeltest (+1) `bon_upsert_ohne_replace`: in `data/local/dao/ReceiptDao.kt` steht für `upsertReceipt` und
  `upsertReceipts` als letzte Nicht-Kommentarzeile vor `suspend fun <Name>(` genau `@Upsert` (Aufbau wie
  `transaktionsmethoden_tragen_transaction`). Akzeptanz: `V` grün (Room/KSP baut die DAO); `TC` = 289;
  `git diff --stat -- app/schemas` leer.
- [x] **S5 Server-Antwort nur übernehmen, wenn lokal nicht neuer (B2-S1)** — Dateien: `$B/data/receipts/ReceiptSyncer.kt`,
  `$B/data/receipts/ReceiptUploader.kt`, `$T/ReceiptSyncerTest.kt`, `$T/ReceiptUploaderTest.kt`. In `ReceiptSyncer.kt`
  oberhalb der Klasse:
  ```kotlin
  /** true, wenn die lokale Zeile während des Server-Aufrufs geändert wurde und neuer ist (Phase 7, B2-S1). */
  internal fun keepsLocalChange(current: ReceiptEntity?, remote: ReceiptRemoteDto): Boolean =
      current != null && current.dirty == 1 && current.updatedAt > remote.updatedAt
  ```
  `pushDirty`, Zweig PUT: nach `putReceipt` `val current = receiptDao.findById(receipt.id)`; nur wenn
  `!keepsLocalChange(current, response.receipt)` → `writeServerReceipt(response.receipt, current ?: receipt)`; `count++`
  bleibt. `ReceiptUploader`: `replaceWithServer(remote, local)` liest `val current = receiptDao.findById(local.id) ?: local`;
  bei `keepsLocalChange(current, remote)` nur `receiptDao.clearPendingUpload(current.id, remote.imagePath)` und Ende,
  sonst `replaceWithServerState(remote.toEntity(current).copy(pendingUpload = 0, localImageUri = ""), items)`. Zweig
  „nicht ausgelesen" (`upsertReceipt(receipt.copy(…))`) nutzt ebenfalls `val current = receiptDao.findById(receipt.id) ?: receipt`
  und `current.copy(…)` mit denselben Feldern. `ReceiptUploaderTest.setUp`: `coEvery { dao.findById(any()) } returns null`
  ergänzen. Tests (+2): `` `pushDirty keeps a local change made during the call` `` (lokal `dirty=1, updatedAt=10`, `findById`
  liefert danach `dirty=1, updatedAt=20`, Antwort `updatedAt=10` → `coVerify(exactly = 0) { dao.replaceWithServerState(any(), any()) }`,
  Rückgabe 1) und `` `parsed answer does not overwrite a newer local change` `` (Uploader, `findById` liefert `dirty=1,
  updatedAt=999`, Antwort geparst mit `updatedAt=0` → `clearPendingUpload("p", "receipt_p.jpg")`, kein
  `replaceWithServerState`). Akzeptanz: `V` grün; `TC` = 291.
- [x] **S6 Löschen, „nie hochgeladen", abgelehnte Bons (B2-S2, B2-S3, B2-P4)** — Dateien: `$B/data/receipts/ReceiptSyncer.kt`,
  `$B/data/local/dao/ReceiptDao.kt`, `$B/data/repository/ReceiptRepository.kt`, `$B/ui/receipts/ReceiptGrouping.kt`,
  `$T/ReceiptSyncerTest.kt`, `$T/ReceiptRepositoryConfirmTest.kt`, `$T/ReceiptGroupingTest.kt`.
  (a) `pull`, Zweig `remote.deleted == 1`: Bedingung `if (!localWins && local?.pendingUpload != 1)`.
  (b) `ReceiptRepository.deleteReceipt`: `val neverOnServer = receipt.pendingUpload == 1` (Rest unverändert).
  (c) `ReceiptDao`: `@Query("UPDATE receipts SET dirty = 0, parseStatus = 'rejected' WHERE id = :id") suspend fun markPushRejected(id: String)`.
  `ReceiptSyncer`: `private companion object { val REJECTED_CODES = setOf(400, 413, 422) }`; im `catch (e: HttpException)`
  nach der 5xx-Prüfung `if (e.code() in REJECTED_CODES) receiptDao.markPushRejected(receipt.id)`, Log-Text
  „Bon %s vom Server abgelehnt (HTTP %d) — nicht erneut gesendet"; andere 4xx (401, 403, 404, 409, 429) bleiben wie bisher.
  (d) `receiptStatusKind`: erste Zeile `receipt.parseStatus == "failed" || receipt.parseStatus == "rejected" -> ReceiptStatusKind.FAILED`.
  Tests: `ReceiptRepositoryConfirmTest` Test „deleteReceipt hard deletes provisional receipts that never reached the server"
  umbenennen in `` `deleteReceipt soft deletes provisional receipts that reached the server` `` und erwarten
  `upsertReceiptWithItems(match { it.deleted == 1 && it.dirty == 1 }, any())`, kein `hardDeleteReceiptWithItems`. Neu (+3):
  `` `pull keeps pending uploads even when the server deleted them` `` (ReceiptSyncerTest), `` `pushDirty marks rejected receipts and stops retrying` ``
  (PUT wirft `HttpException` 422 → `coVerify { dao.markPushRejected("a") }`; bei 401 → `coVerify(exactly = 0) { dao.markPushRejected(any()) }`),
  `` `rejected push counts as failed` `` (ReceiptGroupingTest). Akzeptanz: `V` grün; `TC` = 294.
- [x] **S7 Foto-Race (V3)** — Dateien: `$B/data/receipts/ReceiptImageStore.kt`, `$T/ReceiptImageStoreTest.kt`.
  ```kotlin
  /** Fotos jünger als dies bleiben stehen: der Scan legt die Room-Zeile erst nach dem Server-Aufruf an (Phase 7, V3). */
  const val ORPHAN_MIN_AGE_MS = 60 * 60 * 1000L

  fun orphanFiles(existing: List<Pair<String, Long>>, referenced: Set<String>, nowMs: Long, minAgeMs: Long = ORPHAN_MIN_AGE_MS): List<String> =
      existing.filter { (path, modified) -> path !in referenced && nowMs - modified >= minAgeMs }.map { it.first }
  ```
  `cleanupOrphans(referenced: Set<String>, nowMs: Long = System.currentTimeMillis())`: `existing =
  photoDir.listFiles()?.map { it.absolutePath to it.lastModified() }.orEmpty()`. Bestehende zwei Tests auf Paare mit
  `modified = 0`, `nowMs = ORPHAN_MIN_AGE_MS` umstellen (Erwartungen gleich); neu (+1) `` `young orphan files stay` ``
  (`modified = nowMs - 1_000` → leer). Akzeptanz: `V` grün; `TC` = 295. **Danach Block B1 pushen.**

### Block B2 — Drehen (F1, F2, F10, B6-S4)
- [x] **S8 Eingaben aus dem Testlauf drehfest (F1, F2, F10)** — Dateien: `$B/ui/receipts/CostOverviewScreen.kt`,
  `$B/ui/weekplan/WeekplanScreen.kt`, `$B/ui/recipes/RecipeDetailScreen.kt`. `text`/`warnThreshold` (BudgetEditDialog)
  → `rememberSaveable { mutableStateOf(…) }`; `noteText` → `rememberSaveable(day.id) { mutableStateOf(day.note) }`
  (`DisposableEffect`/`onDone` bleiben); `draft` → `rememberSaveable(notes) { mutableStateOf(notes) }`. Imports
  `androidx.compose.runtime.saveable.rememberSaveable` ergänzen, wo sie fehlen. Kein Unit-Test (Geräteprüfung in E).
  Akzeptanz: `V` grün; `grep -n 'var text by remember {\|noteText by remember(\|draft by remember(' $B/ui -r` leer.
- [x] **S9 Restliche Eingaben, Gang-Ziehen, Regeltest (B6-S4)** — Dateien: `$B/ui/settings/SettingsScreen.kt` (`emoji`,
  `food`, `quantityText`, `unit` mit `rememberSaveable(item?.id)`; `name`, `color` mit `rememberSaveable(marker?.id)`),
  `$B/ui/shopping/ShoppingListScreen.kt` (`quantityText`, `unit`, `name`, `aisle` mit `rememberSaveable(item.id)`),
  `$B/ui/weekplan/WeekplanScreen.kt` (`maxMeat`, `maxFish`, `minVeg`, `maxRepeat`, `maxKcal`, `preferOrganic` mit
  `rememberSaveable(<gleicher Schlüssel>)`; `servings` → `rememberSaveable { mutableIntStateOf(2) }`; `selectedAllergens`
  bleibt), `$B/ui/stores/StoreListScreen.kt` (`draggedId` → `remember { mutableStateOf<String?>(null) }`; Kommentar
  „Ziehen endet mit dem Konfigurationswechsel; bewusst nicht saveable (Phase 7, B6-S4)"), `$T/UiConventionsTest.kt`.
  Regeltest (+1) `eingaben_mit_rememberSaveable`: `Regex("""var (\w+) by remember(\([^)]*\))? \{ mutable\w*StateOf""")`
  über die UI-Quellen (gleiche `sources()`); Treffer mit Namen außerhalb von `setOf("skipConfirmDay", "extraSuggestions",
  "deleteList", "editEmoji", "editMarker", "aislePickerItem", "editItem", "expanded", "suggestions", "rowWidthPx",
  "localOrder", "dragOffset", "draggedId", "aisleSheetStore", "prefill", "now", "classifyProgress", "selectedRecipes")`
  sind Verstöße (Meldung „Eingabe mit rememberSaveable halten (ux §13)"). Akzeptanz: `V` grün; `TC` = 296.

### Block B3 — Bon-Eingabe und Bon-Liste (F12, F11, B2-P2)
- [x] **S10 `DecimalInput` (F12, Teil 1)** — Dateien: neu `$B/ui/common/DecimalInput.kt`, neu `$T/DecimalInputTest.kt`.
  `object DecimalInput { fun parse(raw: String): Double? }` nach der Dezimalregel unter „Entscheidungen"; leere Eingabe →
  `null`. Tests (6): `komma_und_punkt_als_dezimalzeichen` („12,5"→12.5, „12.5"→12.5), `tausenderpunkt_mit_dezimalkomma`
  („1.234,56"→1234.56, „1,234.56"→1234.56), `euro_und_leerzeichen` („ 3,99 €"→3.99), `mehrere_trenner_ungueltig`
  („1,2,3", „1.2.3" → null), `buchstaben_ungueltig` („abc", „12a" → null), `leer_und_minus` („"→null, „-0,50"→-0.5,
  „,5"→0.5). Akzeptanz: `V` grün; `TC` = 302.
- [x] **S11 Dialoge mit Fehleranzeige und Sperre (F12, Teil 2)** — Datei: `$B/ui/receipts/ReceiptReviewComponents.kt`,
  `$R/values/strings.xml`. `parseDecimal` löschen, alle Aufrufe → `DecimalInput.parse`. String
  `receipt_decimal_invalid` = „Bitte eine Zahl wie 12,99 eingeben". Kopf-Dialog: `val totalError = totalText.isNotBlank()
  && DecimalInput.parse(totalText) == null`, analog `discountError`; beide Felder `isError`/`supportingText` wie beim
  Datum; „Speichern" `enabled = !dateError && !timeError && !totalError && !discountError`. Positionsdialog: gleiche
  Prüfung für `quantityText`, `unitPriceText`, `totalText`, `discountText` (leer = erlaubt, Fallback wie bisher);
  „Speichern" (`confirmButton`) `enabled = <keiner der vier Fehler>`. Kein Unit-Test. Akzeptanz: `V` grün;
  `grep -n parseDecimal $B -r` leer.
- [x] **S12 Ältere Bons gezielt suchen (F11, B2-P2)** — Dateien: neu `$B/data/model/OlderReceiptsResult.kt`,
  `$B/data/repository/ReceiptRepository.kt`, neu `$T/ReceiptRepositoryOlderTest.kt`.
  ```kotlin
  sealed interface OlderReceiptsResult {
      data class Loaded(val count: Int, val month: YearMonth) : OlderReceiptsResult
      data class NothingFound(val before: YearMonth) : OlderReceiptsResult
      data object Failed : OlderReceiptsResult
  }
  ```
  `loadOlderMonth(today: LocalDate = LocalDate.now(), nowMs: Long = System.currentTimeMillis()): OlderReceiptsResult`:
  Felder `olderMonthCursor: YearMonth?` und `olderCursorAt: Long = 0L`; ist `nowMs - olderCursorAt > OLDER_CACHE_MS`,
  Cursor auf `null`. `target = min(oldest, cursor, today).minusMonths(1)`, `start = target`; bis zu `OLDER_SEARCH_MONTHS`
  Mal `loadRange(target.atDay(1), target.atEndOfMonth(), nowMs + OLDER_CACHE_MS)`, danach Cursor = `target`,
  `olderCursorAt = nowMs`; `loaded > 0` → `Loaded(loaded, target)`, sonst `target = target.minusMonths(1)`. Nach der
  Schleife `NothingFound(start.plusMonths(1))`. `IOException`/`HttpException`/`IllegalArgumentException` → `Failed`
  (Log wie bisher). `private companion object { const val OLDER_CACHE_MS = 24 * 60 * 60 * 1000L; const val OLDER_SEARCH_MONTHS = 12 }`.
  Tests (4, Aufbau wie `ReceiptRepositoryConfirmTest`, aber mit eigenem `syncer: ReceiptSyncer = mockk()`):
  `` `loads the first older month that has receipts` `` (leer, leer, 2 → `Loaded(2, 2026-08)` bei `today = 2026-11-10` und
  `oldestPurchaseDay() = null`, drei Aufrufe), `` `nothing found after twelve months` `` (immer 0 → `NothingFound(2026-11)`,
  genau 12 Aufrufe), `` `network error returns failed` ``, `` `cursor older than a day is ignored` `` (erst `NothingFound`,
  dann mit `nowMs + 25 h` beginnt die Suche wieder bei 2026-10). Akzeptanz: `V` grün; `TC` = 306.
- [x] **S13 „Ältere Bons laden" auch im Leerzustand, mit Rückmeldung (F11)** — Dateien:
  `$B/ui/receipts/ReceiptListScreen.kt`, `$B/ui/receipts/ReceiptListViewModel.kt`, `$R/values/strings.xml`, neu
  `$T/ReceiptListViewModelTest.kt`. Fußzeile als `@Composable private fun LoadOlderFooter(loadingOlder: Boolean,
  onLoadOlder: () -> Unit, modifier: Modifier = Modifier)` (Inhalt der bisherigen Column); in der Liste
  `item(key = "footer") { LoadOlderFooter(loadingOlder, onLoadOlder, Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 72.dp)) }`,
  im Leerzweig nach dem Scan-Knopf `Spacer(Modifier.height(24.dp))` und `LoadOlderFooter(loadingOlder, onLoadOlder)`.
  ViewModel `loadOlder`: `when (val r = receiptRepository.loadOlderMonth())` → `Loaded` →
  `UiText.Res(R.string.receipt_list_older_loaded, listOf(r.count, r.month.format(MONTH)))`, `NothingFound` →
  `UiText.Res(R.string.receipt_list_older_none, listOf(r.before.format(MONTH)))`, `Failed` → `receipt_list_older_failed`
  (vorhanden); `private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)` (Dateiebene). Strings:
  `receipt_list_older_loaded` = „Nachgeladen aus %2$s: %1$d Bon(s)", `receipt_list_older_none` = „Keine Bons in den
  12 Monaten vor %1$s gefunden". Tests (3, Muster `CostOverviewViewModelTest` mit `setMain`, Turbine auf `olderMessage`):
  je Ergebnis die passende `UiText.Res`-ID. Akzeptanz: `V` grün; `TC` = 309.

### Block B4 — Sync-Anzeige (F3)
- [x] **S14 „Letzter Sync" mit echter Uhrzeit** — Dateien: `$B/data/preferences/AppPreferences.kt`,
  `$B/data/sync/SyncEngine.kt`, `$B/ui/settings/SettingsViewModel.kt`, `$B/ui/settings/SettingsScreen.kt`,
  `$T/SyncEngineTest.kt`. `KEY_LAST_SYNC_AT = longPreferencesKey("last_sync_at")`, `val lastSyncAt: Flow<Long>` (Standard 0),
  `suspend fun saveLastSyncAt(ms: Long)`, in `clearConnection` entfernen. `SyncEngine.runFullSync` direkt nach
  `saveLastSyncTs(newTs)`: `preferences.saveLastSyncAt(System.currentTimeMillis())`. `SettingsViewModel.lastSyncTs` →
  `lastSyncAt` aus `preferences.lastSyncAt`; `SettingsScreen` Zeilen 105/606 entsprechend. Cursor
  (`KEY_LAST_SYNC_TS`) und Bon-Cursor bleiben unberührt. Tests: im Abbruch-Test (Z. ~174)
  `coVerify(exactly = 0) { preferences.saveLastSyncAt(any()) }` ergänzen; neu (+1)
  `` `successful sync stores the wall clock time for the settings screen` ``
  (`coVerify { preferences.saveLastSyncAt(match { it > 1_700_000_000_000L }) }`). Akzeptanz: `V` grün; `TC` = 310.
  **Danach Blöcke B2–B4 pushen.**

### Block B5 — Optik (F4, F8, F9/F17, F14, F15, F19, F5)
- [x] **S15 Kontrast inaktiver Wochenplan-Symbole (F4)** — Datei: `$B/ui/weekplan/WeekplanScreen.kt`. An den sechs
  Stellen (⚡, 👥, 🚫, Schloss, 👍, 👎) `onSurfaceVariant.copy(alpha = 0.4f)` → `copy(alpha = 0.6f)`. Neu-Erzeugen
  (`enabled = day.isLocked == 0`) und Hintergrund (`surfaceVariant…`) bleiben. Akzeptanz: `V` grün;
  `grep -c 'alpha = 0.4f' $B/ui/weekplan/WeekplanScreen.kt` = 2; `grep -c 'alpha = 0.6f'` = 6.
- [x] **S16 Kurzes Label im Formular (F8)** — Dateien: `$R/values/strings.xml` (`recipe_form_prep_time` = „Vorb."),
  `$B/ui/recipes/RecipeFormScreen.kt` (Zeitfeld nutzt `R.string.recipe_form_prep_time`). Akzeptanz: `V` grün.
- [x] **S17 Freiraum unter dem FAB (F9, F17)** — Dateien: neu `$B/ui/components/FabSpacing.kt`
  (`/** Unterer Freiraum für Listen unter einem FAB (Phase 7, F9/F17). */ val FabListBottomPadding = 96.dp`),
  `$B/ui/recipes/RecipeDetailScreen.kt` (LazyColumn `contentPadding = PaddingValues(bottom = FabListBottomPadding)`),
  `$B/ui/receipts/ReceiptListScreen.kt` (`contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = FabListBottomPadding)`;
  Fußzeilen-Modifier `Modifier.fillMaxWidth().padding(top = 8.dp)`). Akzeptanz: `V` grün.
- [x] **S18 Zeitraum-Chips umbrechen (F14)** — Datei: `$B/ui/receipts/CostOverviewScreen.kt`. `RangeChips`: `Row` →
  `FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth())`,
  `@OptIn(ExperimentalLayoutApi::class)` an der Funktion, Label-`Text` mit `maxLines = 1, softWrap = false`.
  Akzeptanz: `V` grün.
- [x] **S19 Achsenbeschriftung (F15)** — Dateien: `$B/ui/receipts/charts/ChartMath.kt`,
  `$B/ui/receipts/charts/SpendingBarChart.kt`, `$T/ChartMathTest.kt`. `fun labelStep(count: Int): Int = if (count > 8) 3 else 1`;
  in `SpendingBarChart` `val step = ChartMath.labelStep(bars.size)`; Label-`Text`:
  `modifier = Modifier.weight(1f).wrapContentWidth(unbounded = true)`, `softWrap = false`, `overflow = TextOverflow.Visible`,
  `maxLines = 1`. Test (+1) `labelStep_jedes_dritte_ab_neun_balken` (8→1, 9→3, 12→3). Akzeptanz: `V` grün; `TC` = 311.
- [x] **S20 Dauer lesbar (F19)** — Dateien: neu `$B/data/util/RecipeDuration.kt`, neu `$B/ui/recipes/RecipeDurationText.kt`,
  `$B/ui/recipes/RecipeListScreen.kt`, `$B/ui/recipes/RecipeDetailScreen.kt`, `$B/ui/ai/AiRemixScreen.kt`,
  `$R/values/strings.xml`, neu `$T/RecipeDurationTest.kt`.
  `object RecipeDuration { fun parseMinutes(raw: String): Int?; fun toIso(minutes: Int): String }`:
  ISO `(?i)^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?$` mit mindestens einer Gruppe, Minuten =
  `Math.round((d*86400 + h*3600 + m*60 + s) / 60.0)`; Freitext: klein schreiben, `,` → `.`, Bereiche
  `(\d+(?:\.\d+)?)\s*[-–]\s*\d+(?:\.\d+)?` → `$1`; `^(\d+):(\d{2})\s*(?:h|std\.?)?$` → h*60+m; sonst Summe aus
  Stunden `(\d+(?:\.\d+)?)\s*(?:stunden|stunde|std\.?|h)(?![a-zäöü])` × 60 und Minuten
  `(\d+)\s*(?:minuten|minute|min\.?|m)(?![a-zäöü])`; reine Zahl `^\d+$` = Minuten. Ergebnis ≤ 0 oder nichts erkannt →
  `null`. `toIso` wie `_mins_to_iso` in `$SA/ai.py` (≤ 0 → „", `PT{h}H{m}M`, `PT{h}H`, `PT{m}M`).
  `@Composable fun formatRecipeDuration(raw: String): String` (leer → „"; nicht erkannt → `raw`; sonst `duration_minutes`
  „%1$d Min", `duration_hours` „%1$d Std", `duration_hours_minutes` „%1$d Std %2$d Min"). Anzeige: `RecipeListScreen`
  `text = formatRecipeDuration(recipe.totalTime)`; `RecipeDetailScreen.MetadataSection` vor `buildList` `val total =
  formatRecipeDuration(recipe.totalTime)` usw. für `prepTime`/`cookTime` und diese Werte einsetzen; `AiRemixScreen`
  ebenso. Tests (8): `iso_minuten_und_stunden` (PT45M 45, PT1H30M 90, PT2H 120, P0DT1H15M 75), `iso_sekunden_gerundet`
  (PT90S 2, PT20S null), `freitext_minuten` („30 Min", „30 min.", „30 Minuten", „30" → 30), `freitext_stunden_und_minuten`
  („1 Std 30 Min", „1 h 30 min", „1 Stunde 30 Minuten" → 90), `dezimalstunden` („1,5 h", „1.5 Std" → 90; „2 Stunden" → 120),
  `bereich_nimmt_erste_zahl` („30-40 Min" 30, „ca. 20 – 25 Minuten" 20), `unbekannt_ist_null` („", „schnell", „PT", „abc 5x"),
  `toIso_wie_server` (0 „", 45 PT45M, 60 PT1H, 90 PT1H30M). Akzeptanz: `V` grün; `TC` = 319.
- [x] **S21 Orange hell abdunkeln (F5)** — Datei: `$B/ui/theme/Color.kt` (`OrangeLight` Hauptton und Eintrag in
  `accentPrimaryColors`: `0xFFE65100` → `0xFFBF360C`). `DayMarker.kt` (Marker-Palette) bleibt. Akzeptanz: `V` grün;
  `grep -c E65100 $B/ui/theme/Color.kt` = 0. **Danach Block B5 pushen.**

### Block B6 — Server und Build (F7, F18, F6)
- [x] **S22 API-Doku abschalten (F7a, B4-P1)** — Dateien: `$SA/main.py`, `$ST/test_health_auth.py`.
  `FastAPI(title=…, version=…, lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)`. Test (+1)
  `test_docs_are_disabled` (`/docs`, `/redoc`, `/openapi.json` → 404). Akzeptanz: `VS` grün, 185 Tests.
- [x] **S23 Leerer KI-Schlüssel ohne Anbieter-Aufruf (F7b)** — Dateien: `$SA/ai_client.py`, `$ST/test_ai_errors.py`,
  `$ST/test_ai_receipt_config.py`. Neue Funktion `_require_ai_key() -> str` (leer → `raise AiUpstreamError("KI-Schlüssel fehlt")`),
  in `openai_headers()`/`anthropic_headers()` statt `settings.AI_API_KEY` verwenden. In
  `test_receipt_once_joins_text_blocks_and_sends_image` `monkeypatch.setattr(settings, "AI_API_KEY", "x")` ergänzen; weitere
  Tests, die nur mit „KI-Schlüssel fehlt" scheitern, bekommen dieselbe Zeile (in „Abschluss" auflisten). Test (+1)
  `test_missing_key_is_502_without_upstream_call` (Fixture `client`, `AI_API_KEY = ""`, `ai_client.httpx.AsyncClient`
  durch eine Attrappe ersetzen, die jeden Aufruf in einer Liste merkt; `POST /api/ai/classify` mit dem Body aus
  `test_classify_status_error_is_502_without_secret` → 502, Liste leer). Akzeptanz: `VS` grün, 186 Tests.
- [x] **S24 Einheitliche Bon-ID-Prüfung (F18, B3-S2)** — Dateien: `$SA/receipts.py`, `$SA/receipt_api.py`, neu
  `$ST/test_receipt_ids.py`. `RECEIPT_ID_RE = re.compile(r"[A-Za-z0-9_-]{1,64}")`; neue Funktion
  `check_receipt_id(receipt_id: str) -> None` (`fullmatch`, sonst `HTTPException(400, "Ungültige Bon-ID")`);
  `save_receipt_image` nutzt sie; in `receipt_api.py` erste Zeile von `scan_receipt`, `get_receipt`, `put_receipt`,
  `delete_receipt`, `upload_receipt_image`, `reparse_receipt`: `receipts.check_receipt_id(receipt_id)`. Tests (6):
  `test_invalid_ids_are_rejected` parametrisiert über `("get", "put", "delete", "image", "reparse")` mit den IDs
  `"a.b"`, `"x" * 65` und `"a%0A"` (je Route alle drei → 400), `test_readable_ids_are_accepted` (PUT und Scan mit
  `"testbon-lidl-0524"` → 200). `test_receipt_scan.py::test_validation` bleibt grün (`"../x"` → 400). Akzeptanz: `VS` grün,
  192 Tests.
- [x] **S25 16-KB-Ausrichtung (F6)** — Dateien: `gradle/libs.versions.toml` (`mlkit-text-recognition = "16.0.1"`,
  `camerax = "1.4.2"`, `datastore = "1.1.7"`, neu `graphicsPath = "1.0.1"` und
  `androidx-graphics-path = { group = "androidx.graphics", name = "graphics-path", version.ref = "graphicsPath" }`),
  `app/build.gradle.kts` (`defaultConfig { … ndk { abiFilters += listOf("arm64-v8a", "x86_64") } }`; in `dependencies`
  `constraints { implementation(libs.androidx.graphics.path) }`). Dann `V` und `VA`. Akzeptanz: `V` grün mit unveränderter
  `TC` (319); `VA` zeigt nur `lib/arm64-v8a` und `lib/x86_64`; das Ergebnis von `zipalign` und die LOAD-Ausrichtung je
  `.so` stehen unter „Abschluss" (auch wenn einzelne Bibliotheken noch `0x1000` zeigen — dann „F6 teilweise, Vermutung
  widerlegt für <Datei>"). **Danach Block B6 pushen.**

### Block C — Markdown-Rezept-Import
Format (verbindlich, auch für die Vorlage in S31 und die Doku in S34):
```markdown
# Titel                         ← Pflicht (erste `# `-Zeile; sonst erste Überschrift, die kein Abschnittsname ist)
Beschreibung als Fließtext oder `> Zitat` (optional)
- Portionen: 4                  ← Schlüsselzeilen: `- K: W`, `**K:** W`, `K: W` (auch `*`/`+` als Marker)
- Vorbereitung / Kochzeit / Gesamtzeit: 15 Min | 1 Std 30 Min | 1,5 h | PT15M
- Küche, Mahlzeit, Aufwand, Eiweiß, Saison, Tags, Nährwerte pro Portion, Notizen
## Zutaten                      ← Abschnitte `##`/`###` oder fette Zeile `**Zutaten:**`
- 200 g Mehl
## Zubereitung
1. Schritt …
## Notizen
Freitext
---                             ← Trenner zum nächsten Rezept (`---`, `***`, `___` oder eine neue `# `-Zeile)
```
- [x] **S26 `MealSlots` in die Datenschicht** — Dateien: neu `$B/data/model/MealSlots.kt` (Objekt unverändert, Paket
  `com.helga.android.data.model`), `$B/ui/components/MealSlot.kt` (Objekt entfernen, `import com.helga.android.data.model.MealSlots`),
  Imports in `RecipeFormScreen.kt`, `RecipeDetailScreen.kt`, `RecipeListScreen.kt`, `WeekplanScreen.kt` (und jede weitere
  Fundstelle von `grep -rn "MealSlots" $B`). Akzeptanz: `V` grün; `TC` = 319;
  `grep -rn "ui.components.MealSlots" app/src` leer.
- [x] **S27 Parser a: Gerüst, Blöcke, Titel** — Dateien: neu `$B/data/util/RecipeMarkdownParser.kt`, neu
  `$T/RecipeMarkdownParserTest.kt`. Typen (alle in dieser Datei):
  ```kotlin
  data class NutritionPerPortion(val kcal: Double, val protein: Double, val fat: Double, val carbs: Double)
  enum class MarkdownWarningCode { INVALID_SERVINGS, INVALID_DURATION, UNKNOWN_VALUE, NUTRITION_INCOMPLETE, NO_INGREDIENTS, NO_STEPS, UNKNOWN_SECTION }
  data class MarkdownWarning(val code: MarkdownWarningCode, val detail: String = "")
  enum class MarkdownErrorCode { MISSING_TITLE, EMPTY_RECIPE, TOO_MANY_LINES }
  data class MarkdownBlockError(val blockIndex: Int, val title: String, val code: MarkdownErrorCode)
  enum class MarkdownFatal { EMPTY_INPUT, TOO_LONG, TOO_MANY_RECIPES }
  data class ParsedRecipe(
      val blockIndex: Int, val name: String, val description: String = "", val recipeYield: String = "",
      val prepTime: String = "", val cookTime: String = "", val totalTime: String = "",   // ISO-8601
      val cuisine: String = "", val mealSlot: String = MealSlots.OTHER, val effort: String = "",
      val proteinType: String = "", val seasonFit: String = "", val tags: List<String> = emptyList(),
      val nutrition: NutritionPerPortion? = null, val ingredients: List<ParsedIngredientLine> = emptyList(),
      val steps: List<String> = emptyList(), val notes: String = "", val warnings: List<MarkdownWarning> = emptyList(),
  )
  data class MarkdownParseResult(val recipes: List<ParsedRecipe>, val errors: List<MarkdownBlockError>, val fatal: MarkdownFatal? = null)
  object RecipeMarkdownParser { const val MAX_CHARS = 100_000; const val MAX_RECIPES = 50; const val MAX_LINES = 100; fun parse(text: String): MarkdownParseResult }
  ```
  In diesem Schritt werden schon der Abschnittskopf-Regex und die Abschnittsnamen aus S29 als private Konstanten angelegt
  (nur zum Erkennen von Abschnittsgrenzen und für den Titel-Rückfall). Dazu: Vorverarbeitung (BOM `﻿` weg; `\r\n`/`\r` → `\n`; Zeilen `^\s*```[A-Za-z]*\s*$` löschen);
  leer nach `trim` → `fatal = EMPTY_INPUT`; Länge > `MAX_CHARS` → `TOO_LONG`; Blöcke an `^\s*(-{3,}|\*{3,}|_{3,})\s*$`
  und vor jeder weiteren `^#\s+`-Zeile eines Blocks teilen; leere Blöcke still verwerfen; mehr als `MAX_RECIPES` Blöcke →
  `TOO_MANY_RECIPES` (dann keine Rezepte). `blockIndex` ab 1. Titel: erste `^#\s+(.+)$`, sonst erste `^#{1,6}\s+(.+)$`,
  deren Name kein Abschnittsname (S29) ist; `**`/`#` am Rand entfernen, trimmen, auf 200 Zeichen kürzen; ohne Titel →
  `MISSING_TITLE` mit `title = ""`. Zeilen vor dem Titel ignorieren. Beschreibung: Zeilen zwischen Titel und erstem
  Abschnitt, die keine Schlüsselzeile sind (Schlüssel folgen in S28; bis dahin alle), führendes `>` entfernen, trimmen,
  leere Zeilen weglassen, mit `\n` verbinden. In S27 wird jedes Rezept mit Titel ohne Inhaltsprüfung zurückgegeben; die
  Prüfung auf Zutaten/Schritte kommt in S29 (die Tests von S27 prüfen nur Titel, Beschreibung, Blöcke, Fatal-Fälle). **Jedes Testrezept in S27 und S28 enthält
  `## Zutaten` mit `- 1 Prise Salz` und `## Zubereitung` mit `1. Würzen.`**, damit die Prüfungen aus S29 die Tests nicht
  brechen; bis S29 werden diese Zeilen noch nicht ausgewertet. Tests (7):
  `leere_eingabe_ist_fatal`, `zu_langer_text_ist_fatal`, `mehr_als_50_rezepte_ist_fatal`,
  `code_zaun_bom_und_crlf_werden_entfernt`, `trennlinien_und_neue_h1_teilen_rezepte` (`---`, `***`, `___`, zweite `# `),
  `fehlender_titel_ist_blockfehler_andere_bloecke_bleiben`, `titel_und_beschreibung_aus_fliesstext_und_zitat`.
  Akzeptanz: `V` grün; `TC` = 326.
- [x] **S28 Parser b: Schlüsselzeilen, Dauer, Vokabular, Nährwerte** — Dateien: `$B/data/util/RecipeMarkdownParser.kt`,
  `$T/RecipeMarkdownParserTest.kt`. Schlüsselzeile: `^\s*(?:[-*+]\s+)?\**\s*([A-Za-zÄÖÜäöüß][A-Za-zÄÖÜäöüß .]*?)\s*\**\s*:\s*\**\s*(.+?)\s*$`,
  Schlüssel klein, getrimmt; nur bekannte Schlüssel zählen, sonst bleibt die Zeile Beschreibung. Erkannt im Kopfbereich
  und in Abschnitten `infos`/`informationen`/`details`/`eckdaten`. Schlüssel → Feld: `portionen|portion|personen` →
  `recipeYield` (muss `\d` enthalten, sonst `INVALID_SERVINGS`; Wert unverändert); `vorbereitung|vorbereitungszeit|zubereitungszeit`
  → `prepTime`; `kochzeit|garzeit|backzeit` → `cookTime`; `gesamtzeit|gesamt|dauer` → `totalTime` (alle drei über
  `RecipeDuration.parseMinutes` + `toIso`; `null` → `INVALID_DURATION` mit `detail = Wert`); `küche|kueche` → `cuisine`
  (klein; in `deutsch, italienisch, asiatisch, mexikanisch, französisch, mediterran, orientalisch, amerikanisch, indisch,
  sonstige`, sonst `sonstige`); `mahlzeit|slot` → `mealSlot` (`frühstück|fruehstueck|breakfast` → breakfast;
  `mittag|mittagessen|lunch` → lunch; `abend|abendessen|abendbrot|dinner` → dinner; `snack|zwischenmahlzeit` → snack;
  sonst `other` + `UNKNOWN_VALUE`); `aufwand|schwierigkeit` → `effort` (`schnell|einfach` → schnell, `mittel|mittelschwer`
  → mittel, `aufwendig|aufwändig|schwer` → aufwendig, sonst leer + `UNKNOWN_VALUE`); `eiweiß|eiweiss|protein|proteinquelle`
  → `proteinType` (`fleisch, fisch, vegetarisch, vegan`, sonst leer + `UNKNOWN_VALUE`); `saison` → `seasonFit`
  (`winter`, `sommer`, `ganzjährig|ganzjaehrig|ganzes jahr` → ganzjährig, sonst leer + `UNKNOWN_VALUE`);
  `tags|schlagwörter|stichwörter` → `tags` (an `,`/`;` teilen, trimmen, führendes `#` weg, leere weg, Dubletten ohne
  Groß-/Kleinschreibung weg, höchstens 20); `nährwerte|naehrwerte|nährwerte pro portion|naehrwerte pro portion` →
  `nutrition` (Zahlen `(\d+(?:[.,]\d+)?)` vor `kcal`, vor `g` + `eiweiß|eiweiss|protein`, vor `g` + `fett`, vor `g` +
  `kohlenhydrate|kh`, ohne Groß-/Kleinschreibung; alle vier nötig, sonst `NUTRITION_INCOMPLETE` und `null`);
  `notizen|notiz|tipp|tipps` → `notes`. Abschnitt `nährwerte`/`naehrwerte`: alle Zeilen mit `, ` verbinden und wie der
  Nährwert-Schlüssel lesen. Tests (8): `schluesselzeilen_in_drei_schreibweisen`, `dauern_werden_iso`,
  `ungueltige_dauer_ergibt_warnung`, `kueche_ausserhalb_des_vokabulars_wird_sonstige`, `mahlzeit_deutsch_wird_slot`,
  `aufwand_eiweiss_saison_vokabular_und_synonyme`, `tags_komma_semikolon_ohne_dubletten`,
  `naehrwerte_vollstaendig_und_unvollstaendig`. Akzeptanz: `V` grün; `TC` = 334.
- [x] **S29 Parser c: Abschnitte, Zutaten, Schritte, Prüfungen** — Dateien: wie S28. Abschnittskopf: `^#{2,3}\s+(.+)$`
  oder `^\*\*(.+?):?\*\*:?\s*$`; Name klein, `:`/`*` am Rand weg. `zutaten|zutatenliste|ingredients` → Zutaten;
  `zubereitung|schritte|anleitung|zubereitungsschritte|instructions` → Schritte; `notizen|notiz|tipps|tipp|hinweise` →
  Notizen; Nährwerte/Infos wie S28. Eine fette Zeile und eine `###`-Zeile sind nur dann ein Abschnittskopf, wenn ihr Name
  bekannt ist; sonst sind sie im Zutaten- oder Schritte-Abschnitt eine verworfene Gruppenüberschrift (außerhalb davon
  ignoriert). Eine `##`-Zeile mit unbekanntem Namen ergibt `UNKNOWN_SECTION` (`detail = Name`), ihr Inhalt wird ignoriert. Zutatenzeile: Marker `^\s*(?:[-*+•]|\d+[.)])\s+` und Checkbox
  `^\[[ xX]\]\s*` entfernen, leere weg, `IngredientLineParser.isHeaderLine` → verwerfen, sonst
  `IngredientLineParser.parse(line).let { it.copy(food = it.food.ifBlank { line }) }`. Schritte: neue Zeile bei
  `(?i)^\s*(?:\d+[.)]|schritt\s+\d+\s*:?|[-*+•])\s+(.*)$`; Zeile ohne Marker wird mit `" "` an den letzten Schritt
  gehängt, nach einer Leerzeile oder ohne Vorgänger beginnt sie einen neuen Schritt. Notizen: Zeilen mit `\n`; Schlüssel
  „Notizen" aus S28 und Abschnitt werden mit `\n` verbunden. Prüfungen: weder Zutaten noch Schritte → Fehler
  `EMPTY_RECIPE` (nicht in `recipes`); mehr als `MAX_LINES` Zutaten oder Schritte → `TOO_MANY_LINES`; nur eins von beiden
  fehlt → Warnung `NO_INGREDIENTS` bzw. `NO_STEPS`. Tests (7): `zutaten_mit_markern_checkbox_und_umlauten`,
  `gruppenueberschriften_werden_verworfen`, `schritte_nummeriert_klammer_schrittwort_spiegelstrich`,
  `folgezeilen_werden_angehaengt`, `abschnitts_synonyme_und_fette_ueberschriften`, `notizen_aus_abschnitt_und_schluessel`,
  `leeres_rezept_und_zu_viele_zeilen_sind_fehler`. Akzeptanz: `V` grün; `TC` = 341.
- [x] **S30 Parser d: Mapper auf Entities** — Dateien: neu `$B/data/util/RecipeMarkdownMapper.kt`, neu
  `$T/RecipeMarkdownMapperTest.kt`. `data class ImportedRecipeBundle(val recipe: RecipeEntity, val ingredients: List<IngredientEntity>, val instructions: List<InstructionEntity>, val tags: List<TagEntity>)`;
  `object RecipeMarkdownMapper { fun toBundle(parsed: ParsedRecipe, now: Long, newId: () -> String = { UUID.randomUUID().toString() }): ImportedRecipeBundle }`:
  `id = slug = newId()`, `createdAt = now`, Felder aus `parsed`, `personalNotes = notes`, `sourceUrl = ""`,
  `mealSlot` nur aus `MealSlots.ALL` (sonst `OTHER`); Nährwerte vorhanden → `nutritionKcal/Protein/Fat/Carbs = Wert ×
  NUTRITION_BASELINE_PORTIONS`, `nutritionSource = "ai"`, sonst alle 0.0 und „"; Zutaten `position` ab 0, Schritte ab 1,
  Tags `TagEntity(id = newId(), recipeId = id, name = …)`. Tests (4): `ids_slug_positionen_zeitstempel`,
  `naehrwerte_mal_vier_mit_quelle_ai`, `ohne_naehrwerte_bleiben_felder_leer`, `tags_und_mealslot_werden_uebernommen`.
  Akzeptanz: `V` grün; `TC` = 345.
- [x] **S31 KI-Vorlage und Synchron-Test** — Dateien: neu `$R/raw/recipe_md_prompt.txt` (Inhalt **wörtlich** aus Anhang A),
  neu `$T/RecipePromptSyncTest.kt`. Test (+1) `beispiel_der_vorlage_parst_fehlerfrei`: liest
  `File("src/main/res/raw/recipe_md_prompt.txt")` (UTF-8), nimmt den Text zwischen den Zeilen `=== BEISPIEL ANFANG ===`
  und `=== BEISPIEL ENDE ===`, `RecipeMarkdownParser.parse` → `fatal == null`, `errors` leer, 2 Rezepte, jedes ohne
  Warnungen, mit `nutrition != null`, mindestens 3 Zutaten und 3 Schritten; Slots `dinner` und `lunch`; letzte
  nicht leere Zeile der Datei ist „Mein Wunsch:". Akzeptanz: `V` grün; `TC` = 346.
- [x] **S32 `MarkdownImportViewModel`** — Dateien: neu `$B/ui/recipes/MarkdownImportViewModel.kt`, neu
  `$T/MarkdownImportViewModelTest.kt`. `@HiltViewModel class MarkdownImportViewModel @Inject constructor(private val
  repository: RecipeRepository, private val aiRepository: AiRepository, private val syncScheduler: SyncScheduler)`.
  ```kotlin
  data class MarkdownPreviewItem(val recipe: ParsedRecipe, val selected: Boolean, val duplicate: Boolean)
  data class MarkdownImportState(val input: String = "", val preview: List<MarkdownPreviewItem>? = null,
      val errors: List<MarkdownBlockError> = emptyList(), val fatal: MarkdownFatal? = null, val isSaving: Boolean = false)
  sealed interface MarkdownSaveTarget { data class Detail(val recipeId: String) : MarkdownSaveTarget; data object RecipeList : MarkdownSaveTarget }
  ```
  `setInput(text)` (setzt `preview = null`, `errors`/`fatal` leer); `evaluate()` (in `viewModelScope`: `parse(input)`;
  vorhandene Namen = `repository.allRecipes().map { it.name.trim().lowercase() }`; `duplicate` = Name vorhanden oder
  schon weiter oben im selben Text; `selected = !duplicate`); `toggle(index)`; `save(onDone: (MarkdownSaveTarget) -> Unit)`:
  nichts ausgewählt → nichts tun; `isSaving = true`; je ausgewähltem Rezept `RecipeMarkdownMapper.toBundle(…, System.currentTimeMillis())`
  und `repository.saveRecipe(recipe, ingredients, instructions, tags)`; danach **einmal** `syncScheduler.triggerOneShot()`;
  Klassifizierung nach „Entscheidungen" (`AiClassifyRequest(name, description, tags, ingredients = food)`, Übernahme über
  `repository.upsertLocal(recipe.copy(…))`, nach mindestens einer Übernahme erneut `triggerOneShot()`); `isSaving = false`;
  `onDone(Detail(id))` bei genau einem gespeicherten Rezept, sonst `RecipeList`. Tests (5, Muster
  `CostOverviewViewModelTest`): `auswerten_erzeugt_vorschau_mit_auswahl`, `gleicher_name_ist_abgewaehlt`
  (vorhandenes Rezept und Dublette im Text), `speichern_je_auswahl_und_trigger_einmal` (2 von 3 ausgewählt →
  `saveRecipe` 2-mal, `triggerOneShot` genau 1-mal; `classifyRecipe` wirft `IOException`), `ziel_detail_bei_einem_rezept_sonst_liste`,
  `fatale_eingabe_ohne_vorschau`. Akzeptanz: `V` grün; `TC` = 351; `ArchitectureRulesTest` grün (kein DAO im ViewModel).
- [x] **S33 Screen, Zwischenablage, FAB, Navigation** — Dateien: neu `$B/ui/recipes/MarkdownImportScreen.kt`, neu
  `$B/ui/recipes/RecipePromptClipboard.kt`, `$B/ui/components/CreateFab.kt`, `$B/ui/recipes/RecipeListScreen.kt`,
  `$B/ui/recipes/RecipeDetailScreen.kt`, `$B/HelgaNavGraph.kt`, `$R/values/strings.xml`.
  `RecipePromptClipboard.kt`: `internal fun loadRecipePrompt(context: Context): String` (Raw-Ressource
  `R.raw.recipe_md_prompt`, UTF-8), `internal fun copyRecipePrompt(context: Context)` (`ClipboardManager` per
  `context.getSystemService(ClipboardManager::class.java)`, `ClipData.newPlainText(context.getString(R.string.md_import_clip_label), loadRecipePrompt(context))`),
  `internal fun readClipboardText(context: Context): String` (erstes Element, `coerceToText`, sonst „").
  `CreateFab`: Parameter `onMarkdownImport: () -> Unit` nach `onUrlImport`; Menüpunkt mit `Icons.Filled.ContentPaste`,
  Label `fab_markdown_import`, zwischen „URL importieren" und „Neues Rezept"; Preview anpassen. `RecipeListScreen`
  Parameter `onMarkdownImportClick`, `RecipeDetailScreen` Parameter `onMarkdownImport`, beide an `CreateFab` durchreichen.
  `HelgaNavGraph`: `internal const val ROUTE_RECIPE_MD_IMPORT = "recipe/md-import"` neben `ROUTE_RECIPE_URL_IMPORT`;
  beide Aufrufstellen navigieren dorthin; `composable(ROUTE_RECIPE_MD_IMPORT) { MarkdownImportScreen(onBack = { navController.popBackStack() },
  onSaved = { target -> when (target) { is MarkdownSaveTarget.Detail -> navController.navigate(recipeDetailRoute(target.recipeId)) { popUpTo(ROUTE_RECIPES) }; MarkdownSaveTarget.RecipeList -> navController.popBackStack(ROUTE_RECIPES, inclusive = false) } }) }`.
  `MarkdownImportScreen` (ViewModel, `collectAsStateWithLifecycle`, `var copied by rememberSaveable { mutableStateOf(false) }`,
  `LaunchedEffect(Unit) { if (!copied) { copyRecipePrompt(context); copied = true; if (Build.VERSION.SDK_INT < 33) snackbar(md_import_prompt_copied) } }`)
  und zustandsloses `MarkdownImportContent` (Muster `UrlImportScreen`: Scaffold, TopAppBar mit AutoMirrored-Zurück,
  `@ThemePreviews` mit Beispielzustand): Karte `md_import_prompt_card` mit TextButton `md_import_copy_again`; Texte
  `md_import_step1`, `md_import_step2`; `OutlinedTextField` (`md_import_input_label`, `minLines = 6`, `maxLines = 12`);
  Zeile mit `md_import_paste` (liest `readClipboardText`, ruft `setInput`) und Button `md_import_evaluate`; `fatal` →
  Fehlertext; Vorschau als Karten (Name; `md_import_preview_counts` mit Zutaten-/Schrittzahl; Zeiten über
  `formatRecipeDuration`; Tags; Warnungen je Code als Text; `md_import_duplicate` bei `duplicate`; `Checkbox` +
  `toggleable(role = Role.Checkbox)`), Blockfehler je Zeile `md_import_block_error` (Nummer, Titel oder `md_import_untitled`,
  Grund); unten Button `pluralStringResource(R.plurals.md_import_save_count, n, n)`, während `isSaving`
  `LoadingIndicator` und `md_import_saving`. Strings (deutsch, wörtlich): `fab_markdown_import` „Markdown importieren",
  `md_import_title` „Markdown importieren", `md_import_clip_label` „Helga Rezept-Vorlage", `md_import_prompt_copied`
  „Vorlage in die Zwischenablage kopiert", `md_import_prompt_card` „Die KI-Vorlage liegt in der Zwischenablage.",
  `md_import_copy_again` „Erneut kopieren", `md_import_step1` „1. Vorlage in Gemini oder eine andere KI einfügen und hinter
  „Mein Wunsch:" dein Rezept beschreiben.", `md_import_step2` „2. Die Antwort komplett kopieren, hier einfügen und
  auswerten.", `md_import_input_label` „Markdown-Text", `md_import_paste` „Einfügen", `md_import_evaluate` „Auswerten",
  Plural `md_import_save_count` (one „1 Rezept speichern", other „%d Rezepte speichern"), `md_import_preview_counts`
  „%1$d Zutaten · %2$d Schritte", `md_import_duplicate` „Ein Rezept mit diesem Namen gibt es schon – nicht ausgewählt.",
  `md_import_block_error` „Block %1$d (%2$s): %3$s", `md_import_untitled` „ohne Titel", `md_import_error_missing_title`
  „Kein Titel (# Titel) gefunden", `md_import_error_empty` „Weder Zutaten noch Schritte", `md_import_error_too_many_lines`
  „Mehr als 100 Zutaten oder Schritte", `md_import_fatal_empty` „Bitte zuerst einen Text einfügen.",
  `md_import_fatal_too_long` „Der Text ist zu lang (höchstens 100 000 Zeichen).", `md_import_fatal_too_many`
  „Zu viele Rezepte (höchstens 50 auf einmal).", `md_import_warn_servings` „Portionen ohne Zahl ignoriert",
  `md_import_warn_duration` „Zeitangabe „%1$s" nicht erkannt", `md_import_warn_value` „Wert „%1$s" nicht erkannt",
  `md_import_warn_nutrition` „Nährwerte unvollständig – nicht übernommen", `md_import_warn_no_ingredients` „Keine Zutaten",
  `md_import_warn_no_steps` „Keine Schritte", `md_import_warn_section` „Abschnitt „%1$s" ignoriert", `md_import_saving`
  „Speichern …". Kein Unit-Test. Akzeptanz: `V` grün; `TC` = 351; `UiConventionsTest` grün.
- [x] **S34 Format-Doku und Bereichsplan** — Dateien: neu `.claude/features/rezepte/markdown-format.md` (Format aus dem
  Kopf von Block C, Schlüssel und Vokabular aus S28, Abschnitte aus S29, Grenzen, Verhalten bei Fehlern/Dubletten,
  Verweis auf `$R/raw/recipe_md_prompt.txt` als einzige Quelle der Vorlage), `.claude/features/rezepte/plan.md`
  (Backlog-Eintrag **A11** „Markdown-Rezept-Import (Phase 7)" abgehakt mit Kurzbeschreibung; Zeile „Phase 7" am Ende).
  Akzeptanz: Dateien vorhanden; `V` grün. **Danach Block C pushen.**

### Block E — Abschluss
- [ ] **S35 Doku, Status, Guidelines** — Dateien: `.claude/phasen/README.md` (Phase 7 Status „umgesetzt (Stufe B;
  Gerätetests offen)"), `.claude/features/README.md` (Zeile Rezepte: Aufgaben-Zähler +1 erledigt; „Stand" auf das Datum),
  `.claude/features/bons-kosten/plan.md` und `.claude/features/sync/plan.md` (je Abschnitt „Phase 7" mit einer Zeile:
  F13/F11/F12 bzw. F3 und Bon-Protokoll), `.claude/guidelines/sync-patterns.md` (Abschnitt „Server-Archiv": zwei Zeilen
  „Server-Antworten werden nur übernommen, wenn die lokale Zeile nicht neuer und `dirty` ist (`keepsLocalChange`)." und
  „`ReceiptDao.upsertReceipt(s)` ist `@Upsert`, nie `REPLACE` (CASCADE auf Positionen)."), `.claude/guidelines/ux-accessibility.md`
  §13 (Satz „Abgesichert: `UiConventionsTest.eingaben_mit_rememberSaveable`."), `.claude/phasen/testplan.md` (neuer
  Abschnitt 8 „Nachtest Phase 7" mit der Nutzer-Liste aus S36 als Tabelle mit leerer Ergebnisspalte). Akzeptanz: `V`, `VS` grün.
- [ ] **S36 Abschluss** — diese Datei, Abschnitt „Abschluss" ausfüllen (Baseline, Endstand `TC`/`VS`, `VA`-Befund,
  Abweichungen je Schritt). **Danach pushen.** Die Gerätetests (Liste unten) macht der Nutzer.

**Nutzer-Liste für Gerät und Homelab (Block E; nicht in der Cloud prüfbar):**
1. Rollout: `make backup`, **Server zuerst** ausrollen, dann App (Debug `com.helga.android.debug` neben der echten App).
2. Galerie-Scan ohne KI (`AI_API_KEY=`): Bon behält Markt, Positionen, Summe; nach Sync am Server ebenfalls (Testplan 4.2, 4.9).
3. Drehen mit Budget-Dialog, Wochenplan-Notiz, Rezept-Notiz, QuickEmoji-/Marker-Dialog, Einkaufs-Bearbeiten: Text bleibt (3.13).
4. Gang ziehen und dabei drehen: keine Zeile bleibt „gezogen" stehen (5.6).
5. Bon-Kopfdialog „1,2,3" → Fehler, Speichern gesperrt; „1.234,56" wird 1.234,56 € (4.5).
6. Leere Bon-Liste mit nur älteren Bons am Server: Knopf da, Snackbar „Nachgeladen aus …"; offline: Fehlermeldung (4.7).
7. Einstellungen „Letzter Sync" zeigt die echte Uhrzeit.
8. Kontrast Wochenplan-Symbole (≥ 3 : 1 inaktiv gemessen), Orange hell, Chip „Gesamt" einzeilig, X-Achse „12/25" lesbar,
   FAB überdeckt weder „Speichern" im Notiz-Editor noch das Bon-Listenende, Label „Vorb." (3.1, 3.9, 5.1).
9. Rezeptliste/-detail: „30 Min" statt „PT30M".
10. Kaltstart Android 16: kommt der 16-KB-Dialog noch? Welche Bibliotheken nennt er? (3.4)
11. Markdown-Import: FAB → „Markdown importieren" → Zwischenablage enthält die Vorlage → in Gemini mit Wunsch „zwei
    vegetarische Abendessen" → Antwort einfügen → Vorschau → Speichern → beide in der Liste; Sync am Server
    (`GET /api/sync?since=0`). Fehlerfälle: Text ohne Titel, leere Eingabe, 51 Rezepte, vorhandener Name (abgewählt).
12. `/docs` und `/openapi.json` → 404; `PUT /api/receipts/testbon-lidl-0524` → 200; `…/a.b` → 400.

## Anhang A — Inhalt von `app/src/main/res/raw/recipe_md_prompt.txt` (wörtlich)
```text
Du bist ein Rezept-Autor für die App „Helga“. Schreibe das gewünschte Rezept auf Deutsch und gib NUR Markdown im folgenden Format aus.

Regeln:
- Kein Codeblock, keine Einleitung, kein Schlusssatz – nur die Rezepte.
- Jedes Rezept beginnt mit einer Zeile „# Titel“. Mehrere Rezepte trennst du durch eine Zeile, die nur aus drei Bindestrichen besteht.
- Nach dem Titel folgen ein kurzer Beschreibungssatz und die Eckdaten als Liste „- Schlüssel: Wert“.
- Erlaubte Schlüssel und Werte:
  - Portionen: eine Zahl
  - Vorbereitung, Kochzeit, Gesamtzeit: zum Beispiel „15 Min“ oder „1 Std 30 Min“
  - Küche: deutsch, italienisch, asiatisch, mexikanisch, französisch, mediterran, orientalisch, amerikanisch, indisch oder sonstige
  - Mahlzeit: Frühstück, Mittagessen, Abendessen oder Snack
  - Aufwand: schnell, mittel oder aufwendig
  - Eiweiß: fleisch, fisch, vegetarisch oder vegan
  - Saison: winter, sommer oder ganzjährig
  - Tags: Stichwörter, durch Komma getrennt
  - Nährwerte pro Portion: „<Zahl> kcal, <Zahl> g Eiweiß, <Zahl> g Fett, <Zahl> g Kohlenhydrate“
- Abschnitt „## Zutaten“: eine Zutat pro Zeile als „- Menge Einheit Zutat“, nur metrische Einheiten (g, kg, ml, l, EL, TL, Stück, Prise, Zehe).
- Abschnitt „## Zubereitung“: nummerierte Schritte „1. …“, ein Arbeitsschritt pro Zeile.
- Optional Abschnitt „## Notizen“ mit Tipps.
- Wünsche ich mehrere Rezepte, schreibe jedes als eigenen Block.

So sieht das Format aus (Beispiel mit zwei Rezepten):
=== BEISPIEL ANFANG ===
# Spaghetti aglio e olio

Schnelle Pasta mit Knoblauch, Olivenöl und Chili.

- Portionen: 2
- Vorbereitung: 5 Min
- Kochzeit: 10 Min
- Gesamtzeit: 15 Min
- Küche: italienisch
- Mahlzeit: Abendessen
- Aufwand: schnell
- Eiweiß: vegetarisch
- Saison: ganzjährig
- Tags: Pasta, Schnell
- Nährwerte pro Portion: 520 kcal, 15 g Eiweiß, 18 g Fett, 72 g Kohlenhydrate

## Zutaten
- 200 g Spaghetti
- 3 Zehen Knoblauch
- 4 EL Olivenöl
- 1 Prise Chiliflocken
- 1 TL Salz

## Zubereitung
1. Spaghetti in Salzwasser bissfest kochen.
2. Knoblauch in Scheiben schneiden und im Öl bei mittlerer Hitze goldgelb braten.
3. Chiliflocken zugeben, Spaghetti abgießen und in der Pfanne schwenken.

## Notizen
Mit frischer Petersilie bestreuen.

---

# Linsensuppe

Herzhafte Suppe für kalte Tage.

- Portionen: 4
- Vorbereitung: 15 Min
- Kochzeit: 45 Min
- Gesamtzeit: 1 Std
- Küche: deutsch
- Mahlzeit: Mittagessen
- Aufwand: mittel
- Eiweiß: vegan
- Saison: winter
- Tags: Suppe, Eintopf
- Nährwerte pro Portion: 380 kcal, 21 g Eiweiß, 6 g Fett, 55 g Kohlenhydrate

## Zutaten
- 250 g Linsen
- 2 Stück Karotten
- 1 Stück Zwiebel
- 1 l Gemüsebrühe
- 2 EL Essig

## Zubereitung
1. Zwiebel und Karotten würfeln und kurz andünsten.
2. Linsen und Brühe zugeben und 40 Minuten köcheln lassen.
3. Mit Essig, Salz und Pfeffer abschmecken.
=== BEISPIEL ENDE ===

Mein Wunsch:
```

## Nicht-Ziele
- Alles aus Phase 8 (Server-Härtung, App-Robustheit, Doku-statt-Code-Fälle) und B5-S6.
- Room-Schema-, DTO- und Endpunkt-Änderungen; neue Abhängigkeiten außer den Versionssprüngen in S25.
- Rezeptformular zeigt Dauern weiterhin roh (ISO); Marker-Farbpalette (`DayMarker.kt`) bleibt; `StoreListScreen`-Padding bleibt.
- Klassifizierung des URL-Imports (Priorität dort unverändert); Gruppenfeld für Zutaten (`###` wird verworfen).
- Gerätetests, Docker-Builds und echte KI-Aufrufe (Nutzer-Liste in Block E).

## Abschluss
- [x] Baseline S0: App `TC` 287 (erwartet 287), Server 180 passed (erwartet 180), `VA`: `zipalign -c -P 16` „Verification successful“; vier ABIs (arm64-v8a, armeabi-v7a, x86, x86_64) mit je fünf `.so`; LOAD-Ausrichtung `0x4000` bei `libandroidx.graphics.path`, `libdatastore_shared_counter` (alle ABIs) und `libbarhopper_v3` (arm64-v8a, x86_64); `0x1000` bei `libimage_processing_util_jni`, `libmlkit_google_ocr_pipeline` (alle ABIs) und `libbarhopper_v3` (armeabi-v7a, x86)
- [ ] Nach B1: `TC` … (erwartet 295), Server … (erwartet 184)
- [ ] Nach B2–B4: `TC` … (erwartet 310) · nach B5: … (erwartet 319) · nach B6: `TC` … (319), Server … (erwartet 192)
- [x] `VA` nach S25: ABIs nur `arm64-v8a` und `x86_64`; `zipalign -c -P 16`: „Verification succesful"; LOAD-Ausrichtung `0x4000` bei allen 12 `.so` (je 6 pro ABI: graphics.path, barhopper_v3, datastore_shared_counter, image_processing_util_jni, mlkit_google_ocr_pipeline, surface_util_jni). F6 behoben; `TC` unverändert 319.
- [ ] Nach C: `TC` … (erwartet 351) · `git diff --stat 99735ec -- app/schemas` leer
- [ ] Gemeldete Abweichungen von diesem Plan (je Schritt eine Zeile): …
