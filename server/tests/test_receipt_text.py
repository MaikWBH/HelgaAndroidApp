from app import receipt_text as rt


def test_normalize_item_key_matches_app_cases():
    assert rt.normalize_item_key("MILCH") == "milch"
    assert rt.normalize_item_key("  Brot  ") == "brot"
    assert rt.normalize_item_key("Käse    2,50") == "käse 2,50"
    assert rt.normalize_item_key("Milch 1,29 A") == "milch 1,29"
    assert rt.normalize_item_key("Käse   2,50  EUR") == "käse 2,50"
    assert rt.normalize_item_key("Brot 3,00 €") == "brot 3,00"
    assert rt.normalize_item_key("Bananen 1kg") == "bananen 1kg"
    assert rt.normalize_item_key("Bananen 1,50") == "bananen 1,50"
    assert rt.normalize_item_key("") == ""
    assert rt.normalize_item_key(None) == ""


def test_store_key():
    assert rt.store_key("s1", "REWE") == "s1"
    assert rt.store_key("", "REWE Markt GmbH") == "name:rewe markt gmbh"
    assert rt.store_key("", "") == ""
    assert rt.store_key(None, None) == ""
    assert rt.store_key("", "  Müller-Markt! ") == "name:müller markt"


def test_match_store():
    stores = [{"id": "1", "name": "REWE"}, {"id": "2", "name": "REWE City"}, {"id": "3", "name": "Aldi"}]
    assert rt.match_store("REWE Markt GmbH", stores)["id"] == "1"
    assert rt.match_store("rewe city", stores)["id"] == "2"
    assert rt.match_store("Lidl", stores) is None
    assert rt.match_store("Aldi", stores)["id"] == "3"
    assert rt.match_store("", stores) is None
    # längster Treffer gewinnt
    assert rt.match_store("REWE City Mitte", stores)["id"] == "2"


def test_day_to_ms():
    assert rt.day_to_ms("2026-11-10") == 1794268800000
    assert rt.day_to_ms("x") == 0
    assert rt.day_to_ms("") == 0
    assert rt.day_to_ms(None) == 0
