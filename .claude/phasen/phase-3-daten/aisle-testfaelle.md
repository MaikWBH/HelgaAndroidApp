# Phase 3 — Testfälle für die Gang-Auto-Zuordnung (Datendatei)

Verwendet von Phase 3b (`AisleKeywordMatcherTest`, `AisleAssignerTest`). Format je Zeile: `Artikel | Erwartung`;
`-` bedeutet „kein Treffer“ (`""`). Stufe B übernimmt die Blöcke **zeichengenau** als Raw-Strings in die Tests.

## Ehrliche Einordnung (Stufe A, 2026-11-11)

- **Liste A (150 Pflichtfälle)** wurde als typischer Wocheneinkauf geschrieben. Der erste Lauf gegen die Stichwortliste
  ergab 145 von 150 richtig (96,7 %). Die fünf Fehler (`Bratwürste`, `Lachsfilet`, `Pizza Salami`, `Vanilleeis`,
  `TK-Erbsen`) wurden durch einzelne zusätzliche Einträge und die Vorrang-Regel behoben; jetzt 150 von 150.
- **Liste B (60 Stichprobe)** wurde **nach** dem Einfrieren der Stichwortliste geschrieben und nicht nachgebessert:
  55 von 60 richtig (91,7 %). Die Fehler stehen unten und bleiben als bekannte Grenzen bestehen.
- Erwartung für den Alltag: gängige Artikel landen zu gut 90 % ohne Server im richtigen Gang; zusammengesetzte Wörter
  mit irreführendem Kopf (`Apfelmus`, `Gemüsemais`), nachgestellte Sorten (`Joghurt Erdbeere`) und seltene Wörter
  brauchen das gelernte Lexikon oder die Server-KI. Eine einmal korrigierte Zuordnung gewinnt immer (Lexikon zuerst).

## Liste A — Pflichtfälle (alle müssen exakt stimmen)

```text
Milch | milch_eier
Vollmilch 3,5% | milch_eier
Hafermilch | milch_eier
Butter | milch_eier
Eier | milch_eier
Eier (10 Stück) | milch_eier
Gouda | milch_eier
Frischkäse | milch_eier
Naturjoghurt | milch_eier
Erdbeerjoghurt | milch_eier
Schlagsahne | milch_eier
Saure Sahne | milch_eier
Crème fraîche | milch_eier
Parmesan | milch_eier
Mozzarella | milch_eier
Quark | milch_eier
Äpfel | obst_gemuese
Bananen | obst_gemuese
Tomaten | obst_gemuese
Tomaten (klein) | obst_gemuese
Cherrytomaten | obst_gemuese
Gurke | obst_gemuese
Salatgurke | obst_gemuese
Zwiebeln | obst_gemuese
Rote Zwiebeln | obst_gemuese
Knoblauch | obst_gemuese
Kartoffeln | obst_gemuese
Karotten | obst_gemuese
Möhren | obst_gemuese
Paprika rot | obst_gemuese
Zucchini | obst_gemuese
Brokkoli | obst_gemuese
Eisbergsalat | obst_gemuese
Rucola | obst_gemuese
Champignons | obst_gemuese
Zitronen | obst_gemuese
Erdbeeren | obst_gemuese
Weintrauben | obst_gemuese
Petersilie | obst_gemuese
Frühlingszwiebeln | obst_gemuese
Ingwer | obst_gemuese
Avocado | obst_gemuese
Süßkartoffeln | obst_gemuese
Brot | brot_backwaren
Vollkornbrot | brot_backwaren
Brötchen | brot_backwaren
Toastbrot | brot_backwaren
Baguette | brot_backwaren
Croissants | brot_backwaren
Laugenbrezeln | brot_backwaren
Tortilla Wraps | brot_backwaren
Hackfleisch | fleisch_wurst
Rinderhackfleisch | fleisch_wurst
Hähnchenbrustfilet | fleisch_wurst
Putenschnitzel | fleisch_wurst
Bratwürste | fleisch_wurst
Wiener Würstchen | fleisch_wurst
Salami | fleisch_wurst
Kochschinken | fleisch_wurst
Speckwürfel | fleisch_wurst
Bacon | fleisch_wurst
Rindersteak | fleisch_wurst
Lachsfilet | fisch
Räucherlachs | fisch
Thunfisch | fisch
Garnelen | fisch
Fischstäbchen | tiefkuehl
Tiefkühlpizza | tiefkuehl
Pizza Salami | tiefkuehl
Pommes | tiefkuehl
Rahmspinat | tiefkuehl
Vanilleeis | tiefkuehl
TK-Erbsen | tiefkuehl
Spaghetti | trockenware
Nudeln | trockenware
Penne | trockenware
Basmatireis | trockenware
Reis | trockenware
Rote Linsen | trockenware
Kichererbsen | trockenware
Kidneybohnen | trockenware
Passierte Tomaten | trockenware
Tomatenmark | trockenware
Kokosmilch | trockenware
Gemüsebrühe | trockenware
Haferflocken | trockenware
Mehl | wuerzen_backen
Weizenmehl Type 405 | wuerzen_backen
Zucker | wuerzen_backen
Backpulver | wuerzen_backen
Salz | wuerzen_backen
Pfeffer | wuerzen_backen
Olivenöl | wuerzen_backen
Sonnenblumenöl | wuerzen_backen
Essig | wuerzen_backen
Senf | wuerzen_backen
Ketchup | wuerzen_backen
Sojasauce | wuerzen_backen
Honig | wuerzen_backen
Paprikapulver | wuerzen_backen
Hefe | wuerzen_backen
Kaffee | fruehstueck
Kaffeebohnen | fruehstueck
Pfefferminztee | fruehstueck
Müsli | fruehstueck
Erdbeermarmelade | fruehstueck
Nutella | fruehstueck
Erdnussbutter | fruehstueck
Mineralwasser | getraenke
Apfelsaft | getraenke
Orangensaft | getraenke
Cola | getraenke
Apfelschorle | getraenke
Eistee | getraenke
Bier | alkohol
Weißwein | alkohol
Rotwein | alkohol
Sekt | alkohol
Schokolade | suesses_snacks
Zartbitterschokolade | suesses_snacks
Gummibärchen | suesses_snacks
Butterkekse | suesses_snacks
Chips | suesses_snacks
Salzstangen | suesses_snacks
Erdnüsse | suesses_snacks
Müsliriegel | suesses_snacks
Kartoffelsalat | feinkost
Hummus | feinkost
Tofu | feinkost
Blätterteig | feinkost
Maultaschen | feinkost
Shampoo | drogerie
Duschgel | drogerie
Zahnpasta | drogerie
Deo | drogerie
Windeln | drogerie
Taschentücher | drogerie
Toilettenpapier | haushalt
Küchenrolle | haushalt
Spülmittel | haushalt
Waschmittel | haushalt
Müllbeutel | haushalt
Backpapier | haushalt
Alufolie | haushalt
Katzenfutter | haushalt
Batterien | haushalt
Geschenkpapier | -
Blumenstrauß | -
Briefmarken | -
Zeitschrift | -
```

## Liste B — Stichprobe (Test verlangt mindestens 54 von 60 Treffern)

Spalte 2 ist die fachlich richtige Kategorie, nicht das Ist-Ergebnis.

```text
Schmand | milch_eier
Gewürzgurken | trockenware
Lauch | obst_gemuese
Blumenkohl | obst_gemuese
Heidelbeeren | obst_gemuese
Bio-Bananen | obst_gemuese
Körnerbrötchen | brot_backwaren
Schweinegeschnetzeltes | fleisch_wurst
Putenbrustaufschnitt | fleisch_wurst
Leberwurst fein | fleisch_wurst
Matjesfilet | fisch
Backofen Pommes | tiefkuehl
Dosenmais | trockenware
Vollkornnudeln | trockenware
Tortellini | trockenware
Puderzucker | wuerzen_backen
Balsamico Essig | wuerzen_backen
Grillsauce | wuerzen_backen
Früchtemüsli | fruehstueck
Kamillentee | fruehstueck
Filterkaffee | fruehstueck
Multivitaminsaft | getraenke
Zitronenlimonade | getraenke
Sprudelwasser | getraenke
Weizenbier | alkohol
Glühwein | alkohol
Vollmilchschokolade | suesses_snacks
Paprikachips | suesses_snacks
Studentenfutter | suesses_snacks
Nudelsalat | feinkost
Räuchertofu | feinkost
Handseife | drogerie
Sonnencreme | drogerie
Feuchttücher | drogerie
Geschirrspültabs | haushalt
Gefrierbeutel | haushalt
Hundefutter | haushalt
Teelichter | haushalt
Joghurt Erdbeere | milch_eier
Kräuterquark | milch_eier
Schokopudding | milch_eier
Zwiebelmettwurst | fleisch_wurst
Apfelmus | trockenware
Gemüsemais | trockenware
Sahnejoghurt | milch_eier
Kinderriegel | suesses_snacks
Tomatenketchup | wuerzen_backen
Kaffeesahne | milch_eier
Spülmaschinentabs | haushalt
Laugenstangen | brot_backwaren
Rinderbraten | fleisch_wurst
Seelachsfilet | fisch
Kirschtomaten | obst_gemuese
Babyspinat | obst_gemuese
Zahnbürsten | drogerie
Orangen | obst_gemuese
Leinsamen | wuerzen_backen
Reibekäse | milch_eier
Tafelwasser | getraenke
Erdnussflips | suesses_snacks
```

Bekannte Fehlzuordnungen in Liste B (Ist-Ergebnis des Algorithmus; im Test als eigene Zusicherung festgehalten, damit
eine spätere Verbesserung auffällt):

| Artikel | richtig | Ist | Ursache |
|---------|---------|-----|---------|
| Joghurt Erdbeere | `milch_eier` | `obst_gemuese` | längstes Stichwort gewinnt |
| Apfelmus | `trockenware` | `obst_gemuese` | längstes Stichwort gewinnt |
| Gemüsemais | `trockenware` | `obst_gemuese` | längstes Stichwort gewinnt |
| Spülmaschinentabs | `haushalt` | `-` | kein Stichwort |
| Leinsamen | `wuerzen_backen` | `-` | kein Stichwort |

## Liste C — Reihenfolge der Quellen (`AisleAssignerTest`)

Aufbau: aktiver Markt `M` mit den Gängen `Obst & Gemüse` (`obst_gemuese`), `Kühlregal` (`milch_eier`),
`Getränke` (`getraenke`), `Drogerie & Haushalt` (`drogerie`), `Kasse` (ohne Schlüssel). Gelerntes Lexikon für `M`:
`Hafermilch` → `Getränke`, `Tofu` → `Kühlregal`.

| # | Eingabe | erwarteter Gang | Begründung |
|---|---------|-----------------|------------|
| 1 | `Hafermilch` | `Getränke` | Lexikon schlägt Stichwort (`milch_eier`) |
| 2 | `Hafermilch (Barista)` | `Getränke` | `AisleProductKey.normalize` entfernt den Klammerzusatz |
| 3 | `Tofu` | `Kühlregal` | Lexikon; ohne Lexikon wäre es `feinkost` → Ausweichkette → `Kühlregal` |
| 4 | `Tomaten` | `Obst & Gemüse` | Stichwort |
| 5 | `Bier` | `Getränke` | `alkohol` hat keinen Gang → Ausweichkette `getraenke` |
| 6 | `Spülmittel` | `Drogerie & Haushalt` | `haushalt` → Ausweichkette `drogerie` |
| 7 | `Hackfleisch` | `` (leer) | `fleisch_wurst` hat keinen Gang und keine Ausweichkategorie → Server-KI |
| 8 | `Geschenkpapier` | `` (leer) | kein Stichwort → Server-KI |
| 9 | `Tomaten`, kein aktiver Markt | `` (leer) | ohne Markt keine Zuordnung, kein Server-Aufruf |
| 10 | `Hackfleisch`, Server meldet `fleisch_wurst` | `` (leer) | Kategorie bekannt, aber kein Gang → nichts lernen |
| 11 | `Geschenkpapier`, Server meldet `haushalt` | `Drogerie & Haushalt` | Server-KI + Ausweichkette; Ergebnis wird als `AisleProduct` gelernt |
| 12 | `Geschenkpapier`, Server nicht erreichbar | `` (leer) | Fehler wird geschluckt, Artikel bleibt ohne Gang |
