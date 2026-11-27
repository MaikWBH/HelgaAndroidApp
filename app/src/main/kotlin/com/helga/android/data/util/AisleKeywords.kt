package com.helga.android.data.util

/**
 * Offline-Stichwortliste für die automatische Gang-Zuordnung der Einkaufsliste (Phase 3b).
 * Quelle: `.claude/phasen/phase-3-daten/aisle-keywords.md` (Datenblock zeichengenau übernommen, 605 Stichwörter).
 * Format: eine Zeile je Kategorie `<schluessel>: wort, wort, …`; ein Eintrag mit Leerzeichen ist eine Phrase,
 * ein führendes `!` kennzeichnet ein Vorrang-Stichwort (das `!` gehört nicht zum Wort).
 */
object AisleKeywords {

    data class Entry(val word: String, val category: String, val priority: Boolean)

    /** Alle Einträge in Dateireihenfolge, Wörter bereits mit [normalize] normalisiert. */
    val entries: List<Entry> by lazy { parse(RAW) }

    internal fun parse(raw: String): List<Entry> =
        raw.lines().map { it.trim() }.filter { it.isNotEmpty() }.flatMap { line ->
            val category = line.substringBefore(':').trim()
            line.substringAfter(':').split(", ").map { it.trim() }.filter { it.isNotEmpty() }.map { token ->
                val priority = token.startsWith("!")
                Entry(normalize(token.removePrefix("!")), category, priority)
            }
        }

    private val trailingBracket = Regex("""\s*\([^()]*\)\s*$""")
    private val disallowed = Regex("[^a-z0-9äöüßéèêàâîôûç ]")
    private val manySpaces = Regex(" +")

    /**
     * Normalform für Artikelnamen und Stichwörter: kleingeschrieben, ein Klammerzusatz am Ende entfernt, alle
     * Zeichen außer Buchstaben/Ziffern/Leerzeichen zu Leerzeichen, Mehrfach-Leerzeichen zusammengezogen.
     */
    internal fun normalize(raw: String): String =
        raw.trim().lowercase()
            .replace(trailingBracket, "")
            .replace(disallowed, " ")
            .replace(manySpaces, " ")
            .trim()

    internal const val RAW = """
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
"""
}
