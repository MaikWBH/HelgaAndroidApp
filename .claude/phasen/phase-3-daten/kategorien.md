# Phase 3 — Kategorien-Katalog (Datendatei)

Eine feste gemeinsame Liste für (a) die KI beim Auslesen der Bons, (b) die Auswertung „Ausgaben je Kategorie",
(c) die Gänge der Märkte. Schlüssel sind stabil (klein, ASCII, Unterstrich) und werden gespeichert; die deutschen Namen
sind nur Anzeige. Änderungen am Katalog brauchen eine neue Entscheidung.

## Katalog (Reihenfolge = Anzeige- und Standard-Gangreihenfolge)

| # | Schlüssel | Name (Deutsch) | Typische Artikel | Gang |
|---|-----------|----------------|------------------|------|
| 1 | `obst_gemuese` | Obst & Gemüse | Äpfel, Tomaten, Salat, frische Kräuter | ja |
| 2 | `brot_backwaren` | Brot & Backwaren | Brot, Brötchen, Toast, Kuchen | ja |
| 3 | `milch_eier` | Milchprodukte & Eier | Milch, Joghurt, Käse, Butter, Eier, Pflanzendrinks | ja |
| 4 | `fleisch_wurst` | Fleisch & Wurst | Hackfleisch, Hähnchen, Schinken, Salami | ja |
| 5 | `fisch` | Fisch | Lachs, Thunfisch, Garnelen | ja |
| 6 | `tiefkuehl` | Tiefkühl | TK-Pizza, Eis, TK-Gemüse, Pommes | ja |
| 7 | `trockenware` | Konserven & Trockenware | Nudeln, Reis, Hülsenfrüchte, Dosen, Brühe | ja |
| 8 | `wuerzen_backen` | Backen, Gewürze & Saucen | Mehl, Zucker, Öl, Essig, Senf, Gewürze | ja |
| 9 | `fruehstueck` | Frühstück, Kaffee & Tee | Kaffee, Tee, Müsli, Marmelade, Aufstriche | ja |
| 10 | `getraenke` | Getränke | Wasser, Saft, Limonade (alkoholfrei) | ja |
| 11 | `alkohol` | Bier, Wein & Spirituosen | Bier, Wein, Sekt | ja |
| 12 | `suesses_snacks` | Süßes & Snacks | Schokolade, Kekse, Chips, Nüsse | ja |
| 13 | `feinkost` | Feinkost & Fertiggerichte | Fertigsalate, Hummus, Tofu, frische Pasta | ja |
| 14 | `drogerie` | Drogerie & Baby | Shampoo, Zahnpasta, Windeln | ja |
| 15 | `haushalt` | Haushalt & Tierbedarf | Toilettenpapier, Spülmittel, Müllbeutel, Tierfutter | ja |
| 16 | `sonstiges` | Sonstiges | alles andere, Non-Food ohne Zuordnung | nein |
| 17 | `pfand` | Pfand | Pfand, Leergut-Rückgabe (nur Bons) | nein |

- Einträge 1–15 sind die **Gang-Kategorien** und bilden den Standard-Gangsatz neuer Märkte (Gangname = Name aus der
  Tabelle, Reihenfolge wie oben).
- `sonstiges` ist die Auffangkategorie der Auswertung und wird nie einem Gang zugeordnet. Ein leerer Schlüssel (`""`)
  bedeutet „noch nicht kategorisiert" und zählt in der Auswertung zu `sonstiges`.
- `pfand` gibt es nur bei Bon-Positionen (`is_deposit = 1`), nie als Gang und nie in der Einkaufsliste.
- Ungültige Schlüssel (von der KI erfunden, Tippfehler) werden serverseitig und in der App zu `""`.

## Ausweichkette (Kategorie → Ersatz, wenn der Markt keinen Gang mit diesem Schlüssel hat)

| Kategorie | Ersatz in dieser Reihenfolge |
|-----------|------------------------------|
| `wuerzen_backen` | `trockenware` |
| `fruehstueck` | `trockenware` |
| `feinkost` | `milch_eier` |
| `alkohol` | `getraenke` |
| `fisch` | `fleisch_wurst` |
| `haushalt` | `drogerie` |
| `drogerie` | `haushalt` |
| alle anderen | kein Ersatz |

Die Kette wird genau eine Stufe tief ausgewertet (kein Ersatz des Ersatzes). Haben mehrere Gänge eines Markts denselben
Schlüssel, gewinnt der mit der kleinsten `sortOrder`.

## Zuordnung der bisherigen Standardgänge (Room-Migration 37 → 38, Phase 3b)

Nur Gänge, deren Name **exakt** einem alten Standardnamen entspricht, bekommen automatisch einen Schlüssel. Alle anderen
Gänge behalten `""` und werden vom Nutzer im Gang-Editor zugeordnet.

| Alter Gangname (`StoreRepository.kt:21-25`) | Schlüssel |
|---------------------------------------------|-----------|
| Obst & Gemüse | `obst_gemuese` |
| Brot & Backwaren | `brot_backwaren` |
| Milchprodukte & Eier | `milch_eier` |
| Fleisch & Wurst | `fleisch_wurst` |
| Fisch | `fisch` |
| Tiefkühl | `tiefkuehl` |
| Konserven & Trockenware | `trockenware` |
| Getränke | `getraenke` |
| Süßes & Snacks | `suesses_snacks` |
| Drogerie & Haushalt | `drogerie` |

## String-Ressourcen (App)

Name `category_<schluessel>` mit dem deutschen Namen aus der Tabelle, also `category_obst_gemuese` = „Obst &amp; Gemüse"
bis `category_pfand` = „Pfand"; zusätzlich `category_none` = „Ohne Kategorie". `&` wird in `strings.xml` als `&amp;`
geschrieben.
