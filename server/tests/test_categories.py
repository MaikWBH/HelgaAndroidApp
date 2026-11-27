from app import categories

EXPECTED_KEYS = [
    "obst_gemuese", "brot_backwaren", "milch_eier", "fleisch_wurst", "fisch", "tiefkuehl",
    "trockenware", "wuerzen_backen", "fruehstueck", "getraenke", "alkohol", "suesses_snacks",
    "feinkost", "drogerie", "haushalt", "sonstiges", "pfand",
]


def test_key_list_exact_and_ordered():
    assert [key for key, _ in categories.CATEGORIES] == EXPECTED_KEYS
    assert categories.CATEGORY_KEYS == set(EXPECTED_KEYS)


def test_aisle_categories_are_first_fifteen():
    assert list(categories.AISLE_CATEGORY_KEYS) == EXPECTED_KEYS[:15]
    assert "sonstiges" not in categories.AISLE_CATEGORY_KEYS
    assert "pfand" not in categories.AISLE_CATEGORY_KEYS


def test_coerce():
    assert categories.coerce(" Obst_Gemuese ") == "obst_gemuese"
    assert categories.coerce("xyz") == ""
    assert categories.coerce(None) == ""
    assert categories.coerce("") == ""


def test_prompt_lines():
    lines = categories.prompt_lines()
    assert "- obst_gemuese: Obst & Gemüse" in lines
    assert "- pfand: Pfand" in lines
    assert len(lines.splitlines()) == 17
    assert "pfand" not in categories.prompt_lines(False)
