# Phase 3 — Stichwortliste für die Gang-Auto-Zuordnung (Datendatei)

Verwendet von Phase 3b (`AisleKeywords.kt`, `AisleKeywordMatcher.kt`). Die Liste bildet Artikelnamen der Einkaufsliste
offline auf einen Kategorie-Schlüssel aus [kategorien.md](kategorien.md) ab. **605 Stichwörter** in 15 Kategorien
(`sonstiges` und `pfand` haben bewusst keine Stichwörter). Stufe B übernimmt den Datenblock **zeichengenau** als
Raw-String nach `AisleKeywords.RAW`; nichts ergänzen, sortieren oder korrigieren.

## Format des Datenblocks

- Eine Zeile je Kategorie: `<schluessel>: wort, wort, …`. Trennzeichen ist `, `.
- Ein Eintrag mit Leerzeichen ist eine **Phrase** (z. B. `saure sahne`).
- Ein Eintrag mit führendem `!` ist ein **Vorrang-Stichwort** (`!tk`, `!tiefkühl`, `!tiefgekühlt`, `!konserve`, `!dose`); das `!` gehört nicht zum Wort.
- Alle Einträge sind bereits kleingeschrieben und eindeutig (kein Wort steht in zwei Kategorien).

## Algorithmus (verbindlich, in dieser Reihenfolge)

`AisleKeywordMatcher.categoryFor(name: String): String` liefert einen Kategorie-Schlüssel oder `""`.

1. **Normalisieren** (`normalize`): `trim`, `lowercase`; einen Klammerzusatz am Ende entfernen
   (Regex `\s*\([^()]*\)\s*$`); jedes Zeichen, das nicht in `a-z 0-9 ä ö ü ß é è ê à â î ô û ç` oder Leerzeichen liegt,
   durch ein Leerzeichen ersetzen (also auch `-`, `,`, `%`, `&`, `.`); Mehrfach-Leerzeichen zu einem; `trim`.
   Leeres Ergebnis → `""`. Die Stichwörter selbst werden beim Laden mit derselben Funktion normalisiert
   (`h-milch` → `h milch`, wird dadurch zur Phrase).
2. **Tokens** = Normalform an Leerzeichen getrennt. **Formen eines Tokens** = das Token selbst plus, für jedes Suffix aus
   `["en", "er", "n", "e", "s"]` (in dieser Reihenfolge), das Token ohne dieses Suffix, sofern das Token damit endet und
   der Rest mindestens 2 Zeichen lang ist.
3. **Vorrang:** Die Vorrang-Stichwörter in Dateireihenfolge prüfen. Trifft eines zu (ein Token ist gleich dem Wort, oder
   das Wort hat ≥ 4 Zeichen und ein Token **beginnt** damit), sofort dessen Kategorie zurückgeben.
4. **Stufe 1 (genau/Endung/Phrase)** über alle übrigen Stichwörter: Ein Einzelwort trifft ein Token, wenn eine Form des
   Tokens gleich dem Wort ist oder (nur bei Wortlänge ≥ 4) eine Form mit dem Wort **endet**. Eine Phrase trifft, wenn
   `" " + normalform + " "` den Text `" " + phrase + " "` enthält; ihr Token-Index ist der Index ihres letzten Wortes.
   Bei mehreren Treffern gewinnt: größte Wortlänge (Zeichen der Phrase inkl. Leerzeichen) → höchster Token-Index
   (bei Einzelwörtern der höchste Index eines treffenden Tokens) → früherer Eintrag in der Datei.
5. **Stufe 2 (enthält)** nur wenn Stufe 1 nichts fand: Einzelwörter mit Länge ≥ 5, die irgendwo **in** einem Token
   vorkommen (`indexOf`, erstes Vorkommen je Token). Es gewinnt: größte Wortlänge → höchster Token-Index → größte
   Fundposition im Token → früherer Eintrag in der Datei. Phrasen und Vorrang-Stichwörter nehmen an Stufe 2 nicht teil.
6. Kein Treffer → `""` (der Artikel geht an die Server-KI, siehe Phase 3b).

Referenzimplementierung der Stufe A (Python, 60 Zeilen) hat mit genau diesen Regeln die Erwartungswerte in
[aisle-testfaelle.md](aisle-testfaelle.md) erzeugt.

## Prüfzahlen (für den Test `AisleKeywordsTest`)

| Kategorie | Anzahl |
|-----------|-------:|
| `alkohol` | 24 |
| `brot_backwaren` | 33 |
| `drogerie` | 38 |
| `feinkost` | 34 |
| `fisch` | 24 |
| `fleisch_wurst` | 49 |
| `fruehstueck` | 29 |
| `getraenke` | 27 |
| `haushalt` | 41 |
| `milch_eier` | 51 |
| `obst_gemuese` | 96 |
| `suesses_snacks` | 38 |
| `tiefkuehl` | 23 |
| `trockenware` | 44 |
| `wuerzen_backen` | 54 |
| **Summe** | **605** |

Vorrang-Stichwörter: 5. Phrasen (nach Normalisierung mit Leerzeichen): 26.

## Datenblock

```text
obst_gemuese: apfel, äpfel, birne, banane, orange, mandarine, clementine, zitrone, limette, grapefruit, traube, weintraube, erdbeere, himbeere, heidelbeere, blaubeere, brombeere, johannisbeere, kirsche, pflaume, zwetschge, pfirsich, nektarine, aprikose, melone, ananas, mango, kiwi, avocado, feige, dattel, granatapfel, rhabarber, tomate, gurke, paprika, zucchini, aubergine, karotte, möhre, kartoffel, süßkartoffel, zwiebel, lauchzwiebel, frühlingszwiebel, schalotte, knoblauch, ingwer, lauch, porree, sellerie, fenchel, brokkoli, blumenkohl, rosenkohl, rotkohl, weißkohl, wirsing, spitzkohl, grünkohl, kohlrabi, chinakohl, pak choi, spinat, mangold, salat, rucola, feldsalat, radieschen, rettich, rote bete, kürbis, spargel, champignon, pilz, mais, maiskolben, bohne, erbse, zuckerschote, sprossen, petersilie, schnittlauch, basilikum, dill, koriander, minze, rosmarin, thymian, kresse, chili, peperoni, obst, gemüse, suppengrün, kräuter
brot_backwaren: brot, brötchen, baguette, ciabatta, toast, toastbrot, vollkornbrot, knäckebrot, zwieback, croissant, brezel, laugenbrezel, laugenstange, semmel, fladenbrot, pita, wrap, tortilla, kuchen, torte, muffin, donut, berliner, hefezopf, stollen, waffel, pumpernickel, aufbackbrötchen, burgerbrötchen, hotdogbrötchen, sandwichbrot, naan, bagel
milch_eier: milch, vollmilch, hmilch, h-milch, hafermilch, sojamilch, mandelmilch, haferdrink, sojadrink, buttermilch, kefir, sahne, schlagsahne, kochsahne, saure sahne, schmand, creme fraiche, crème fraîche, quark, speisequark, joghurt, naturjoghurt, skyr, butter, margarine, käse, frischkäse, hüttenkäse, mozzarella, feta, schafskäse, gouda, emmentaler, parmesan, camembert, brie, bergkäse, reibekäse, schnittkäse, halloumi, mascarpone, ricotta, ei, eier, pudding, milchreis, grießbrei, kondensmilch, kaffeesahne, kakaomilch, trinkjoghurt
fleisch_wurst: fleisch, hackfleisch, rinderhack, gehacktes, hähnchen, hühnchen, hähnchenbrust, hähnchenschenkel, pute, putenbrust, schnitzel, steak, gulasch, braten, rind, rindfleisch, schwein, schweinefleisch, lamm, kotelett, rinderfilet, schweinefilet, bratwurst, wurst, würste, würstchen, wiener, bockwurst, salami, schinken, kochschinken, speck, bacon, leberwurst, mettwurst, mett, lyoner, fleischwurst, aufschnitt, frikadelle, geflügel, ente, gans, kasseler, rippchen, leberkäse, mortadella, cabanossi, geschnetzeltes
fisch: fisch, lachs, räucherlachs, forelle, thunfisch, hering, matjes, makrele, kabeljau, seelachs, garnele, shrimps, krabben, scampi, tintenfisch, muscheln, sardine, sardelle, fischfilet, lachsfilet, zander, dorade, rollmops, bismarckhering
tiefkuehl: !tk, !tiefkühl, !tiefgekühlt, pizza, pizza salami, pizza schinken, pizza thunfisch, fischstäbchen, pommes, kroketten, rösti, eis, speiseeis, eiscreme, wassereis, stieleis, vanilleeis, schokoladeneis, erdbeereis, rahmspinat, eiswürfel, backofenpommes, kartoffelpuffer
trockenware: nudel, nudeln, spaghetti, penne, fusilli, tagliatelle, lasagneplatten, makkaroni, spätzle, gnocchi, tortellini, reis, basmatireis, risottoreis, couscous, bulgur, quinoa, linse, kichererbse, kidneybohne, !konserve, !dose, passierte tomaten, tomatenmark, kokosmilch, brühe, gemüsebrühe, suppe, tütensuppe, kartoffelpüree, knödel, polenta, grieß, haferflocken, sauerkraut, rotkohl glas, gewürzgurke, olive, kapern, pesto, tomatensauce, nudelsauce, ravioli, artischocke
wuerzen_backen: mehl, zucker, puderzucker, vanillezucker, backpulver, hefe, natron, speisestärke, stärke, salz, pfeffer, paprikapulver, curry, zimt, muskat, kümmel, oregano, gewürz, öl, olivenöl, sonnenblumenöl, rapsöl, essig, balsamico, senf, ketchup, mayonnaise, mayo, sojasauce, sauce, soße, grillsauce, dressing, tomatenketchup, honig, sirup, ahornsirup, kakao, backkakao, kuvertüre, schokostreusel, gelatine, tortenguss, rosinen, mandeln gemahlen, kokosraspel, paniermehl, semmelbrösel, zitronensaft, worcestersauce, tabasco, sambal, brühwürfel, vanille
fruehstueck: kaffee, kaffeebohnen, espresso, kaffeepads, kaffeekapseln, tee, schwarztee, grüntee, kräutertee, früchtetee, pfefferminztee, kamillentee, teebeutel, müsli, cornflakes, cerealien, granola, porridge, marmelade, konfitüre, gelee, nutella, nussnougatcreme, erdnussbutter, aufstrich, brotaufstrich, kakaopulver, trinkschokolade, kaba
getraenke: wasser, mineralwasser, sprudel, saft, apfelsaft, orangensaft, multivitaminsaft, schorle, apfelschorle, limonade, limo, cola, fanta, sprite, eistee, energydrink, smoothie, nektar, tonic, malzbier, alkoholfreies bier, sirup getränk, zitronenlimonade, spezi, bitter lemon, kokoswasser, isodrink
alkohol: bier, pils, weizenbier, weißbier, radler, wein, rotwein, weißwein, rosé, sekt, prosecco, champagner, schnaps, wodka, whisky, rum, gin, likör, aperol, glühwein, cidre, weinbrand, korn, tequila
suesses_snacks: schokolade, schokoriegel, riegel, praline, bonbon, gummibärchen, fruchtgummi, lakritz, keks, kekse, butterkeks, plätzchen, waffeln, chips, kartoffelchips, flips, erdnussflips, salzstangen, salzbrezeln, cracker, nüsse, erdnüsse, studentenfutter, popcorn, kaugummi, müsliriegel, lebkuchen, marzipan, tortilla chips, nachos, reiswaffeln, cashew, pistazien, walnüsse, haselnüsse, mandeln, trockenobst, schokolinsen
feinkost: kartoffelsalat, nudelsalat, fleischsalat, krautsalat, eiersalat, heringssalat, antipasti, hummus, tzatziki, guacamole, kräuterbutter, fertiggericht, fertigsalat, sushi, maultaschen, frische pasta, frische pizza, pizzateig, blätterteig, tofu, räuchertofu, tempeh, seitan, veggie burger, vegane wurst, falafel, frikadellen fertig, suppe frisch, dip, oliven frisch, sandwich, wrap fertig, baguette belegt, flammkuchen
drogerie: shampoo, duschgel, seife, handseife, zahnpasta, zahnbürste, mundspülung, zahnseide, deo, deodorant, rasierer, rasierschaum, rasierklingen, creme, handcreme, bodylotion, sonnencreme, taschentücher, tempo, wattepads, wattestäbchen, binden, tampons, slipeinlagen, windeln, feuchttücher, babynahrung, babybrei, pflaster, haargel, haarspray, spülung, lippenpflege, nagellackentferner, kondome, kontaktlinsenmittel, gesichtscreme, abschminktücher
haushalt: toilettenpapier, klopapier, küchenrolle, küchenpapier, spülmittel, spültabs, geschirrspültabs, waschmittel, weichspüler, reiniger, allzweckreiniger, badreiniger, glasreiniger, wc reiniger, entkalker, schwamm, schwämme, spültuch, putzlappen, müllbeutel, müllsäcke, gefrierbeutel, frischhaltefolie, alufolie, backpapier, kerze, teelichter, batterien, glühbirne, streichhölzer, feuerzeug, servietten, staubsaugerbeutel, katzenfutter, hundefutter, katzenstreu, tierfutter, vogelfutter, grillkohle, klarspüler, spülmaschinensalz
```
