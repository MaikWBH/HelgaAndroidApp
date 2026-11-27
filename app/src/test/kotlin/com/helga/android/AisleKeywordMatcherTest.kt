package com.helga.android

import com.helga.android.data.util.AisleKeywordMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Liste A (Pflicht), Liste B (Stichprobe) und Einzelfälle aus `aisle-testfaelle.md` (Phase 3b). */
class AisleKeywordMatcherTest {

    private fun cases(raw: String): List<Pair<String, String>> =
        raw.lines().map { it.trim() }.filter { it.isNotEmpty() }.map {
            val (name, expected) = it.split(" | ")
            name to if (expected == "-") "" else expected
        }

    @Test
    fun `Liste A - alle 150 Pflichtfaelle stimmen exakt`() {
        val all = cases(LIST_A)
        assertEquals(150, all.size)
        val misses = all.mapNotNull { (name, expected) ->
            val actual = AisleKeywordMatcher.categoryFor(name)
            if (actual != expected) "$name: erwartet '$expected', war '$actual'" else null
        }
        assertTrue("Abweichungen in Liste A:\n" + misses.joinToString("\n"), misses.isEmpty())
    }

    @Test
    fun `Liste B - Stichprobe trifft mindestens 54 von 60`() {
        val all = cases(LIST_B)
        assertEquals(60, all.size)
        val hits = all.count { (name, expected) -> AisleKeywordMatcher.categoryFor(name) == expected }
        println("Stichprobe: $hits/60")
        assertTrue("Stichprobe nur $hits/60", hits >= 54)
    }

    @Test
    fun `bekannte Fehlzuordnungen bleiben wie dokumentiert`() {
        assertEquals("obst_gemuese", AisleKeywordMatcher.categoryFor("Joghurt Erdbeere"))
        assertEquals("obst_gemuese", AisleKeywordMatcher.categoryFor("Apfelmus"))
        assertEquals("obst_gemuese", AisleKeywordMatcher.categoryFor("Gemüsemais"))
        assertEquals("", AisleKeywordMatcher.categoryFor("Spülmaschinentabs"))
        assertEquals("", AisleKeywordMatcher.categoryFor("Leinsamen"))
    }

    @Test
    fun `Einzelfaelle der Regeln`() {
        assertEquals("obst_gemuese", AisleKeywordMatcher.categoryFor("  TOMATEN (klein) "))
        assertEquals("milch_eier", AisleKeywordMatcher.categoryFor("H-Milch"))
        assertEquals("trockenware", AisleKeywordMatcher.categoryFor("Dosentomaten"))
        assertEquals("tiefkuehl", AisleKeywordMatcher.categoryFor("Eis"))
        assertEquals("fruehstueck", AisleKeywordMatcher.categoryFor("Tee"))
        assertEquals("getraenke", AisleKeywordMatcher.categoryFor("Eistee"))
        assertEquals("", AisleKeywordMatcher.categoryFor("Türkei"))
        assertEquals("", AisleKeywordMatcher.categoryFor("Brei"))
        assertEquals("", AisleKeywordMatcher.categoryFor(""))
        assertEquals("", AisleKeywordMatcher.categoryFor("Geschenkpapier"))
    }

    private companion object {
        const val LIST_A = """
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
"""

        const val LIST_B = """
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
"""
    }
}
