# Phase 3 — Server-Vertrag für Bons (Datendatei)

Verbindliche Schnittstelle zwischen `server/` und der App für Phase 3a und 3b. Feldnamen im JSON sind `snake_case`.
Alle Endpunkte hängen an `Depends(require_auth)`. Geldbeträge sind `float` mit Punkt, Zeitstempel Unix-ms.

## 1. Schema

### 1.1 Neue Spalten (über `ADDED_COLUMNS` in `db.py`, nicht destruktiv)

`receipts`:

| Spalte | DDL | Bedeutung |
|--------|-----|-----------|
| `store_address` | `TEXT NOT NULL DEFAULT ''` | Filiale/Adresse, einzeilig („REWE Markt Müller, Hauptstr. 1, 12345 Ort") |
| `purchase_day` | `TEXT NOT NULL DEFAULT ''` | Kaufdatum wie gedruckt, `YYYY-MM-DD` |
| `purchase_time` | `TEXT NOT NULL DEFAULT ''` | Uhrzeit wie gedruckt, `HH:MM` |
| `discount_total` | `REAL NOT NULL DEFAULT 0.0` | Rabatte auf Bon-Ebene, positiv |
| `deposit_total` | `REAL NOT NULL DEFAULT 0.0` | Summe aller Pfand-Positionen (Leergut negativ) |
| `confidence` | `REAL NOT NULL DEFAULT 1.0` | Gesamt-Sicherheit der KI 0–1 |
| `source` | `TEXT NOT NULL DEFAULT ''` | `ai` · `on_device` · `manual` · `legacy` |
| `parse_status` | `TEXT NOT NULL DEFAULT 'none'` | `none` · `queued` · `running` · `done` · `failed` |
| `parse_attempts` | `INTEGER NOT NULL DEFAULT 0` | Anzahl KI-Versuche |
| `parse_claimed_at` | `INTEGER NOT NULL DEFAULT 0` | Zeitpunkt der Übernahme durch die Warteschlange |
| `parse_model` | `TEXT NOT NULL DEFAULT ''` | Modell des letzten erfolgreichen Auslesens |

`receipt_items`:

| Spalte | DDL | Bedeutung |
|--------|-----|-----------|
| `unit` | `TEXT NOT NULL DEFAULT ''` | `stk` · `kg` · `g` · `l` · `ml` · `''` |
| `discount` | `REAL NOT NULL DEFAULT 0.0` | Rabatt dieser Position, positiv |
| `is_deposit` | `INTEGER NOT NULL DEFAULT 0` | 1 = Pfand/Leergut |
| `category` | `TEXT NOT NULL DEFAULT ''` | Schlüssel aus [kategorien.md](kategorien.md) |
| `off_barcode` | `TEXT NOT NULL DEFAULT ''` | bestätigtes Open-Food-Facts-Produkt |
| `confidence` | `REAL NOT NULL DEFAULT 1.0` | Sicherheit der KI 0–1 |

Bestehende Spalten bekommen eine klare Rolle: `raw_text` = Text wie auf dem Bon (Kürzel), `name` = lesbarer Artikelname
(von KI oder Lexikon, vom Nutzer korrigierbar). Bestandsbons haben `raw_text == name`; das bleibt so.

`COLUMN_BACKFILL` (läuft nur beim Anlegen der Spalte):

```sql
-- ("receipts", "purchase_day")
UPDATE receipts SET purchase_day = CASE WHEN purchase_date > 0
    THEN strftime('%Y-%m-%d', purchase_date / 1000, 'unixepoch', '+2 hours') ELSE '' END
-- ("receipts", "source")
UPDATE receipts SET source = 'legacy'
```

(`+2 hours`: Bestandswerte sind Mitternacht UTC oder der Scan-Zeitpunkt; so liegt der Tag in deutscher Ortszeit richtig.)

`status` (bestehende Spalte) bekommt diese Werte: `provisional` (vorläufige ML-Kit-Werte, KI steht aus) · `review`
(KI hat ausgelesen, Nutzer hat noch nicht bestätigt) · `confirmed` · `reconciled` (bestätigt und mit Einkaufsliste
abgeglichen) · `scanned` (Bestand, gilt als bestätigt).

Kennzahl-Regeln: `total_price` einer Position ist der Zeilenbetrag **vor** ihrem Positionsrabatt; der wirksame Betrag
ist `total_price - discount`. Erwartung: `Σ(total_price - discount) - discount_total ≈ total_amount`.
`purchase_date` = Mitternacht **UTC** des `purchase_day` (`calendar.timegm`), reiner Sortierschlüssel; ist
`purchase_day` leer, der vom Client gemeldete Aufnahmezeitpunkt.

### 1.2 Neue Tabellen (in `SCHEMA`, nicht in `SYNC_TABLES`)

```sql
CREATE TABLE IF NOT EXISTS receipt_lexicon (
    id           TEXT PRIMARY KEY,
    store_key    TEXT NOT NULL DEFAULT '',
    store_name   TEXT NOT NULL DEFAULT '',
    raw_key      TEXT NOT NULL DEFAULT '',
    raw_text     TEXT NOT NULL DEFAULT '',
    name         TEXT NOT NULL DEFAULT '',
    category     TEXT NOT NULL DEFAULT '',
    off_barcode  TEXT NOT NULL DEFAULT '',
    hits         INTEGER NOT NULL DEFAULT 0,
    updated_at   INTEGER NOT NULL DEFAULT 0,
    deleted      INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS product_categories (
    product_key  TEXT PRIMARY KEY,
    product_name TEXT NOT NULL DEFAULT '',
    category     TEXT NOT NULL DEFAULT '',
    source       TEXT NOT NULL DEFAULT '',
    updated_at   INTEGER NOT NULL DEFAULT 0
);
```

Indizes: `CREATE UNIQUE INDEX IF NOT EXISTS idx_receipt_lexicon_key ON receipt_lexicon(store_key, raw_key)`,
`CREATE INDEX IF NOT EXISTS idx_receipts_day ON receipts(purchase_day)`,
`CREATE INDEX IF NOT EXISTS idx_receipts_parse_status ON receipts(parse_status)`.

Phase 3b ergänzt `store_aisles.category_key TEXT NOT NULL DEFAULT ''` (Sync-Spalte).

### 1.3 Schlüssel

- `normalize_item_key(text)`: `strip`, `lower`, Mehrfach-Leerraum zu einem Leerzeichen, dann ein angehängtes
  `\s+(?:[a-z]|€|eur)$` entfernen. Identisch zu `ReceiptItemNormalizer.normalize` der App
  (`app/.../data/util/ReceiptItemNormalizer.kt`); die Testfälle aus `ReceiptItemNormalizerTest.kt` gelten 1:1.
- `store_key(store_id, store_name)`: `store_id`, falls nicht leer; sonst `"name:" + s`, wobei `s` der kleingeschriebene
  Marktname ist, alle Zeichen außer `a-z0-9äöüß` durch Leerzeichen ersetzt, zusammengezogen, getrimmt. Beides leer → `""`.
- `match_store(store_name, stores)`: `stores` = nicht gelöschte Zeilen aus `stores`. Vergleich auf den wie oben
  bereinigten Namen `n` und `m`: Treffer, wenn `m` nicht leer ist und (`n == m` oder `n` beginnt mit `m + " "` oder `m`
  beginnt mit `n + " "`). Bei mehreren Treffern der mit dem längsten `m`. Kein Treffer → `store_id` bleibt leer.

## 2. Modelle

`ReceiptItemOut` / `ReceiptItemIn`:
`id`, `receipt_id`, `position`, `raw_text`, `name`, `quantity`, `unit`, `unit_price`, `total_price`, `discount`,
`is_deposit`, `category`, `off_barcode`, `confidence`, `matched_shopping_item_id`, `match_status`, `updated_at`, `deleted`.

`ReceiptOut`: `id`, `store_id`, `store_name`, `store_address`, `shopping_list_id`, `purchase_date`, `purchase_day`,
`purchase_time`, `total_amount`, `discount_total`, `deposit_total`, `currency`, `image_path`, `status`, `source`,
`confidence`, `parse_status`, `updated_at`, `deleted`, `server_seq`, `items: [ReceiptItemOut]` (nur nicht gelöschte,
nach `position`).

`ReceiptIn` (Client → Server): wie `ReceiptOut` ohne `image_path`, `parse_status`, `server_seq`, `confidence`;
`items: [ReceiptItemIn]` ist immer die **vollständige** Positionsliste. Alle Felder außer `id` und `updated_at` haben
Standardwerte.

## 3. Endpunkte

Router `app/receipt_api.py`, Präfix `/api/receipts`. **Deklarationsreihenfolge:** feste Pfade (`/scan`, `/lexicon`,
`/lexicon/{entry_id}`, `/aggregates`) vor `/{receipt_id}`. `POST /api/receipts/reconcile` bleibt unverändert in `main.py`.

| Methode + Pfad | Eingabe | Antwort |
|----------------|---------|---------|
| `GET /api/receipts` | Query `from_day`, `to_day` (`YYYY-MM-DD`, beide optional, inklusiv), `since_seq` (int, Standard 0), `include_deleted` (0/1, Standard 0) | `{"server_seq": int, "receipts": [ReceiptOut]}` |
| `GET /api/receipts/{receipt_id}` | – | `ReceiptOut`, 404 wenn unbekannt oder gelöscht |
| `PUT /api/receipts/{receipt_id}` | `ReceiptIn` | `{"applied": bool, "receipt": ReceiptOut}` |
| `DELETE /api/receipts/{receipt_id}` | Query `updated_at` (ms) | `{"ok": true}` (auch wenn unbekannt) |
| `POST /api/receipts/scan` | Multipart: `file`, Formfelder `receipt_id`, `shopping_list_id` (Standard `''`), `captured_at` (ms), `preliminary` (JSON-Text eines `ReceiptIn`, optional) | `{"parsed": bool, "receipt": ReceiptOut}` |
| `POST /api/receipts/{receipt_id}/image` | Multipart: `file` | `{"image_path": str}` |
| `POST /api/receipts/{receipt_id}/reparse` | – | `{"parsed": bool, "receipt": ReceiptOut}` |
| `GET /api/receipts/lexicon` | Query `store_key` (optional) | `{"entries": [{id, store_key, store_name, raw_text, name, category, off_barcode, hits, updated_at}]}` |
| `DELETE /api/receipts/lexicon/{entry_id}` | – | `{"ok": true}` |
| `GET /api/receipts/aggregates` (Phase 3b) | – | `{"generated_at": ms, "rows": [AggregateRow]}` |
| `POST /api/ai/categorize-items` (Phase 3b, in `main.py`) | `{"names": [str]}` (höchstens 50) | `{"results": [{"name": str, "category": str}]}` |

### 3.1 Regeln

**Liste (`GET /api/receipts`).** Filter: `server_seq > since_seq`; mit `from_day`: `(purchase_day = '' OR purchase_day
>= from_day)`; mit `to_day`: `purchase_day <= to_day`; ohne `include_deleted=1` nur `deleted = 0`. Gelöschte Bons kommen
ohne Positionen. Sortierung `purchase_date DESC`. `server_seq` der Antwort = aktueller Wert aus `sync_state`
(vor dem Lesen der Zeilen gelesen, wie in `sync.pull_since`).

**Schreiben allgemein.** Jeder Schreibvorgang an einem Bon oder seinen Positionen erhöht `sync_state.seq` um 1 und
stempelt den neuen Wert als `server_seq` auf den **Bon** (Positionen ebenfalls). Dadurch reicht der Bon-Cursor für
Positionsänderungen.

**PUT.** Unbekannter Bon → anlegen. Bekannter Bon mit `updated_at` (Server) `>` `updated_at` (Eingabe) →
`applied = false`, nichts ändern, Server-Stand zurückgeben. Sonst übernehmen: alle Felder aus `ReceiptIn`
(`image_path`, `parse_*`, `confidence` bleiben), Positionen per `id` upserten, serverseitige Positionen, die in der
Eingabe fehlen, auf `deleted = 1`. `category` jeder Position durch `categories.coerce` schicken. `deposit_total` =
Summe `total_price` der Positionen mit `is_deposit = 1`. Ist `purchase_day` gesetzt, `purchase_date` daraus neu
berechnen. Ist `store_id` leer und `store_name` nicht, `match_store` anwenden. Danach **Lernen** (Abschnitt 4), wenn
`applied` und `status` in (`confirmed`, `reconciled`).

**DELETE.** Bon und Positionen auf `deleted = 1`, `updated_at = max(Eingabe, bisheriger Wert)`. Die Bilddatei bleibt
liegen (Archiv, Soft-Delete).

**Scan.** (1) `receipt_id` muss `^[0-9a-fA-F-]{36}$` erfüllen, sonst 400. Dateiendung aus `ALLOWED_IMAGE_EXTS` ohne
`.gif`, sonst 400. Mehr als 8 MB → 413. (2) Datei als `receipt_<receipt_id><ext>` in `IMAGES_DIR` speichern
(überschreiben), `image_path` = Dateiname. (3) Gibt es den Bon schon mit `parse_status = 'done'`: nichts neu auslesen,
`{"parsed": true, receipt}` zurück (idempotente Wiederholung). (4) Sonst `ai.parse_receipt` aufrufen. Ergebnis mit
Inhalt (mindestens eine Position oder `total_amount > 0`) → Bon schreiben: `status = 'review'`, `source = 'ai'`,
`parse_status = 'done'`, `parse_model`, `parse_attempts + 1`, `updated_at = now_ms()`, vorhandene Positionen auf
`deleted = 1`, neue Positionen mit neuen UUIDs, dann **Anreichern** (Abschnitt 4); `parsed = true`. (5) Ausnahme der KI
oder Ergebnis ohne Inhalt → Bon aus `preliminary` übernehmen (falls vorhanden und der Bon noch nicht existiert;
`status = 'provisional'`, `source = 'on_device'`) oder als leere Hülle anlegen (`status = 'provisional'`,
`purchase_date = captured_at`), `parse_status = 'queued'`, `parse_attempts + 1`; `parsed = false`, HTTP 200.

**Bild.** Speichert wie Scan Schritt 1–2 und setzt nur `image_path` (kein Auslesen). 404, wenn der Bon unbekannt ist.

**Reparse.** 404 wenn Bon unbekannt, 409 (`detail` „Kein Foto vorhanden") wenn `image_path` leer oder Datei fehlt.
Sonst wie Scan Schritt 4–5 auf dem gespeicherten Foto (bei Misserfolg bleiben die bisherigen Werte, nur
`parse_status = 'queued'`).

**Warteschlange.** `process_parse_queue(limit=5)`: Zeilen mit `parse_status = 'queued'` und `parse_attempts < 5`,
älteste zuerst. Übernahme atomar: `UPDATE receipts SET parse_status='running', parse_claimed_at=? WHERE id=? AND
parse_status='queued'`, nur bei `rowcount == 1` weiter. Erfolg wie Scan Schritt 4. Misserfolg: `parse_attempts + 1`,
`parse_status = 'queued'`, ab 5 Versuchen `'failed'`. Vor jedem Lauf: `running`-Zeilen mit `parse_claimed_at` älter als
15 Minuten zurück auf `queued`. Start in `lifespan` als `asyncio`-Task, der alle `RECEIPT_QUEUE_INTERVAL_SEC` Sekunden
läuft (Standard 600; `0` = kein Task). Ausnahmen im Task werden geloggt und beenden die Schleife nicht.

**Lexikon.** Liste: nicht gelöschte Einträge, sortiert nach `store_name`, `raw_text`. Löschen: `deleted = 1`.

## 4. Anreichern und Lernen

**Anreichern (nach jedem erfolgreichen Auslesen).**
1. `store_id` per `match_store`, falls leer.
2. Je Position ohne `is_deposit`: Lexikon-Eintrag zu (`store_key`, `normalize_item_key(raw_text)`) suchen. Treffer →
   `name` übernehmen; `category` und `off_barcode` übernehmen, wenn im Eintrag nicht leer; `hits + 1`.
3. Je Position, deren `category` danach leer ist: `product_categories` zu `normalize_item_key(name)` suchen und
   übernehmen.
4. Positionen mit `is_deposit = 1` bekommen `category = 'pfand'`.

**Lernen (bei PUT mit Status `confirmed`/`reconciled`).** Für jede Position der Eingabe ohne `is_deposit`, mit nicht
leerem `raw_text`, die es vor dem PUT schon auf dem Server gab: Weicht `name`, `category` oder `off_barcode` vom
bisherigen Server-Wert ab, den Lexikon-Eintrag (`store_key`, `raw_key`) upserten (`name`, `category`, `off_barcode` aus
der Eingabe, `raw_text`, `store_name`, `hits + 1`, `deleted = 0`). Ist dabei `category` nicht leer, zusätzlich
`product_categories` zu `normalize_item_key(name)` mit `source = 'user'` upserten. Ohne `store_key` wird kein
Lexikon-Eintrag geschrieben (nur `product_categories`).

## 5. KI

### 5.1 Konfiguration

| Variable | Standard | Wirkung |
|----------|----------|---------|
| `AI_RECEIPT_MODEL` | leer → Anthropic `claude-haiku-5-5`, OpenAI `gpt-4o-mini` | Modell für **alle** Bon-Aufrufe: Auslesen, Abgleich, Kategorisieren |
| `AI_RECEIPT_EFFORT` | `medium` | `output_config.effort` für Modelle, die es kennen (`low`/`medium`/`high`) |
| `RECEIPT_QUEUE_INTERVAL_SEC` | `600` | Takt der Re-Parse-Warteschlange, `0` = aus |
| `AI_VISION_MODEL` | wie bisher | nur noch Referenzmodell des Testset-Skripts |

`_receipt_model()` = `AI_RECEIPT_MODEL or DEFAULT_RECEIPT_MODEL[AI_PROVIDER]`. Kein Rückfall auf ein größeres Modell.

Anthropic-Nutzlast für Bon-Aufrufe (`_receipt_payload(model, system, content)`), nicht gestreamt, `max_tokens = 16000`
(Denk-Tokens zählen mit):
- Modell beginnt mit einem von `claude-haiku-5`, `claude-sonnet-5`, `claude-opus-5`, `claude-opus-4-7`,
  `claude-opus-4-8`, `claude-fable`, `claude-mythos` → **kein** `temperature`/`top_p`/`top_k` (Haiku 5.5 antwortet sonst
  mit HTTP 400), stattdessen `"output_config": {"effort": AI_RECEIPT_EFFORT}`; `thinking` nicht senden.
- sonst (z. B. `claude-sonnet-4-6`, `claude-haiku-4-5-…`) → `"temperature": 0`, kein `output_config`.
- Antworttext = alle Inhaltsblöcke mit `type == "text"` aneinandergehängt (eine Antwort kann mit `thinking`-Blöcken
  beginnen). Kein Assistant-Prefill.
- OpenAI-Pfad unverändert (`temperature: 0`, `detail: high`).

### 5.2 Prompt für das Auslesen

`RECEIPT_PARSE_SYSTEM` bleibt (`ai.py:460-464`). `RECEIPT_PARSE_USER` wird durch diesen Text ersetzt; `{categories}`
wird beim Modulstart durch die Zeilen `- <schluessel>: <Name>` aller 17 Katalogeinträge ersetzt.

```text
Lies diesen Kassenbon vollständig aus und gib die Daten als JSON zurück.

VORGEHEN:
- Gehe den Bon Zeile für Zeile von OBEN nach UNTEN durch.
- Erfasse JEDE Artikelzeile zwischen Kopf und Summenblock. Überspringe oder kürze nichts.
- Ein Artikelname kann über ZWEI Zeilen gehen (Marke in Zeile 1, Produkt in Zeile 2): fasse ihn zu EINEM Eintrag zusammen.
- Ist ein Name oder Preis teils unleserlich: trage deine beste Schätzung ein und setze eine niedrige confidence. Lasse die Position nicht weg.

KOPF:
- store_name: Name der Kette oder des Geschäfts (z. B. "REWE", "ALDI SÜD", "EDEKA", "Lidl", "dm"). Leer, wenn nicht erkennbar.
- store_address: Filiale und Adresse in einer Zeile, so weit gedruckt (z. B. "REWE Markt Müller oHG, Hauptstr. 12, 12345 Musterstadt"). Leer, wenn nicht gedruckt. Keine Telefonnummer, keine Steuernummer.
- purchase_date: Kaufdatum als "YYYY-MM-DD". Leer, wenn nicht erkennbar.
- purchase_time: Uhrzeit des Kaufs als "HH:MM" (24 Stunden). Leer, wenn nicht gedruckt.
- total_amount: zu zahlender Gesamtbetrag (Summe / zu zahlen) als Zahl.
- discount_total: Summe aller Rabatte, die für den GANZEN Bon gelten (Coupon, "5% auf alles", Mitarbeiterrabatt, Treuerabatt), als positive Zahl. 0, wenn keine.
- confidence: deine Gesamt-Sicherheit für den ganzen Bon (0.0 bis 1.0).

POSITIONEN (items), je Artikel:
- name: Text der Artikelzeile GENAU wie auf dem Bon, ohne Preis und ohne nachgestelltes Steuerkennzeichen (einzelnes A/B am Zeilenende).
- product_name: gut lesbarer deutscher Artikelname ohne Kürzel (aus "G&G H-MILCH 3,5%" wird "H-Milch 3,5 %", aus "BIO BAN." wird "Bio-Bananen"). Marke nur, wenn sie zum Erkennen nötig ist.
- category: genau EIN Schlüssel aus der Liste KATEGORIEN. Passt nichts: "sonstiges".
- quantity: Anzahl oder Gewicht/Volumen als Zahl (Standard 1; bei "0,512 kg" also 0.512).
- unit: "stk" für Stück, "kg", "g", "l" oder "ml" bei Abrechnung nach Gewicht oder Volumen. Unklar: "stk".
- unit_price: Preis je Einheit. Ist nur ein Preis erkennbar: gleich total_price.
- total_price: Betrag der Zeile, wie er rechts steht, VOR einem Rabatt dieser Position.
- discount: Rabatt, der nur für DIESE Position gilt (Zeile direkt darunter wie "Rabatt -0,50", "Aktionsnachlass", "Preisvorteil", "-30%"), als positive Zahl. 0, wenn keiner. Eine solche Rabattzeile ist KEINE eigene Position.
- is_deposit: true bei Pfand und Leergut ("PFAND", "EINWEG", "MEHRWEG", "LEERGUT"), sonst false. Pfand immer als eigene Position mit category "pfand"; Leergut-Rückgabe mit negativem total_price.
- confidence: deine Sicherheit für diese Position (0.0 bis 1.0).

KATEGORIEN:
{categories}

REGELN:
- Deutsche Preise nutzen Komma (1,99): gib Zahlen mit Punkt aus (1.99).
- "2 x 1,99" oder "3 Stk x 0,89": quantity = Anzahl, unit = "stk", unit_price = Stückpreis, total_price = Summe.
- "0,512 kg x 2,99 EUR/kg": quantity = 0.512, unit = "kg", unit_price = 2.99, total_price = gedruckter Betrag.
- Keine Positionen sind: Zwischensummen, MwSt-Zeilen, Zahlart, Rückgeld, Kundenkarten- und Punktezeilen, Bon-Nummern.
- Prüfe am Ende: Summe aus (total_price minus discount) aller Positionen, minus discount_total, ergibt ungefähr total_amount. Weicht es stark ab, hast du Positionen oder Rabatte übersehen: lies noch einmal.

BEISPIEL (nur Format):
Die Zeilen "G&G H-MILCH 3,5% 0,95 A", "2 x BANANE 1,98 B", "Rabatt -0,30" und "PFAND 0,25 A" werden zu
[{"name":"G&G H-MILCH 3,5%","product_name":"H-Milch 3,5 %","category":"milch_eier","quantity":1,"unit":"stk","unit_price":0.95,"total_price":0.95,"discount":0,"is_deposit":false,"confidence":0.95},
 {"name":"BANANE","product_name":"Bananen","category":"obst_gemuese","quantity":2,"unit":"stk","unit_price":0.99,"total_price":1.98,"discount":0.30,"is_deposit":false,"confidence":0.9},
 {"name":"PFAND","product_name":"Pfand","category":"pfand","quantity":1,"unit":"stk","unit_price":0.25,"total_price":0.25,"discount":0,"is_deposit":true,"confidence":0.95}]

Antworte mit genau diesem Format, nur JSON, kein Markdown:
{"store_name":"","store_address":"","purchase_date":"YYYY-MM-DD","purchase_time":"HH:MM","total_amount":0.00,"discount_total":0.00,"confidence":0.0,"items":[{"name":"","product_name":"","category":"","quantity":1,"unit":"stk","unit_price":0.00,"total_price":0.00,"discount":0.00,"is_deposit":false,"confidence":0.0}]}
```

### 5.3 Normalisieren der KI-Antwort

`normalize_parsed_receipt(data: dict) -> dict` (rein, ohne Netz) liefert:
`store_name`, `store_address` (Zeilenumbrüche → `", "`, getrimmt, höchstens 200 Zeichen), `purchase_day` (nur wenn
`date.fromisoformat` gelingt, sonst `''`), `purchase_time` (nur wenn `^\d{1,2}:\d{2}` passt und gültig ist, auf `HH:MM`
gebracht, sonst `''`), `purchase_date` (Mitternacht UTC des Tages, sonst 0), `total_amount`,
`discount_total` (`abs`), `confidence` (auf 0–1 begrenzt), `items`.
Je Position: `raw_text` = `name` der KI; `name` = `product_name` der KI, falls nicht leer, sonst `raw_text`; Positionen
ohne `raw_text` und ohne `name` entfallen; `quantity` (0 → 1); `unit` (kleingeschrieben, nur `stk|kg|g|l|ml`, sonst
`'stk'`); `unit_price`/`total_price` mit der bisherigen Ergänzungslogik (`ai.py:574-577`); `discount` (`abs`);
`is_deposit` (bool oder Text `true`/`1` → 1); `category` = `'pfand'` bei `is_deposit`, sonst `categories.coerce(...)`;
`confidence` (0–1). Zahlen laufen durch `_to_float`.

`parse_receipt(image_b64, mime_type, model=None)` gibt dieses Dict zurück (plus `model`). Der bestehende Endpunkt
`POST /api/ai/parse-receipt` bleibt für alte App-Versionen: `ReceiptParseResponse`/`ReceiptParseItem` bekommen die neuen
Felder mit Standardwerten; `ReceiptParseItem.name` trägt dort weiterhin den lesbaren Namen.

### 5.4 Kategorisieren (Phase 3b)

`categorize_items(names)`: System „Du ordnest Artikel eines deutschen Supermarkt-Einkaufs genau einer Kategorie zu.
Antworte NUR mit einem validen JSON-Objekt – kein Markdown, keine Erklärungen." Nutzertext:
`KATEGORIEN:\n{categories ohne pfand}\n\nARTIKEL:\n1. <name>\n2. …\n\nAntworte mit: {"results":[{"index":1,"category":"<schluessel>"}]}`.
Antwort tolerant parsen wie `classify` (`ai.py:184-196`); unbekannte Schlüssel → `sonstiges`.

## 6. Aggregate (Phase 3b)

`AggregateRow`: `period`, `dimension`, `key`, `label`, `amount`, `quantity`, `count`, `avg_unit_price`, `discount`.
Grundlage: Bons mit `deleted = 0`, `status != 'provisional'`, `purchase_day != ''`; Positionen mit `deleted = 0`.

| `dimension` | `period` | `key` / `label` | `amount` | weitere Felder |
|-------------|----------|-----------------|----------|----------------|
| `total` | `YYYY-MM` | `''` / `''` | Σ `total_amount` | `count` = Bons, `discount` = Σ `discount_total` + Σ Positions-`discount` |
| `week` | ISO-Woche `YYYY-Www` (`date.isocalendar`, zweistellig) | `''` / `''` | Σ `total_amount` | `count` = Bons |
| `store` | `YYYY-MM` | `store_key(...)` / häufigster `store_name` der Gruppe (leer → `''`) | Σ `total_amount` | `count` = Bons |
| `category` | `YYYY-MM` | `category` (leer → `sonstiges`) / `''` | Σ (`total_price` − `discount`) | `count` = Positionen |
| `product` | `YYYY-MM` | `normalize_item_key(name)` / häufigster `name` der Gruppe | Σ (`total_price` − `discount`) | `quantity` = Σ `quantity`, `count` = Positionen, `avg_unit_price` = Mittel der `unit_price` |

Pfand-Positionen zählen in `category` unter `pfand` und fehlen in `product`. Beträge auf 2 Nachkommastellen gerundet.
Zeilen sortiert nach `dimension`, `period`, `key`. Es gibt keine gespeicherte Aggregat-Tabelle auf dem Server; die
Antwort wird bei jedem Aufruf aus den Bons berechnet (Datenmenge eines Haushalts).

## 7. Alt-Clients (Umschaltung am Ende von Phase 3a)

`receipts` und `receipt_items` verlassen `SYNC_TABLES` (Pull liefert sie nicht mehr). `SyncPayload` behält beide Felder.
`push_records` nimmt sie weiter an (`LEGACY_PUSH_TABLES`): Datensätze mit `deleted = 0` per LWW upserten, dabei **nur**
die bisherigen Spalten aus `TABLE_COLUMNS` setzen (neue Spalten behalten ihren Wert) und `server_seq` stempeln; bei
Bons mit leerem `purchase_day` den Backfill-Ausdruck anwenden und `source = 'legacy'` setzen, wenn leer. Datensätze mit
`deleted = 1` werden **ignoriert** (Archivschutz gegen die Auto-Löschung alter App-Versionen). Für diese Tabellen gibt es
keine `server_wins`.
