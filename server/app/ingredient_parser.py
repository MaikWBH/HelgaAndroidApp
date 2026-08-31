import re

# Deutsche Koch-Einheiten, kanonisch normalisiert. Spiegelt
# IngredientLineParser.kt (App-seitiges Pendant für KI-Generate/Remix), damit
# Rezepte aus dem URL-Import (import_url in ai.py) dieselbe Menge/Einheit-
# Aufschlüsselung erhalten wie KI-generierte Rezepte.
_UNIT_CANONICAL = {}
for _names, _canon in [
    (("g", "gramm", "gr"), "g"),
    (("kg",), "kg"),
    (("ml", "milliliter"), "ml"),
    (("l", "liter", "ltr"), "l"),
    (("el", "esslöffel", "esslöffeln"), "EL"),
    (("tl", "teelöffel", "teelöffeln"), "TL"),
    (("stück", "stk", "st"), "Stück"),
    (("prise", "prisen"), "Prise"),
    (("bund",), "Bund"),
    (("dose", "dosen"), "Dose"),
    (("päckchen", "pck", "pkt"), "Päckchen"),
    (("scheibe", "scheiben"), "Scheibe"),
    (("tasse", "tassen"), "Tasse"),
    (("glas", "gläser"), "Glas"),
    (("zehe", "zehen"), "Zehe"),
    (("handvoll",), "Handvoll"),
    (("blatt", "blätter"), "Blatt"),
    (("zweig", "zweige"), "Zweig"),
    (("würfel",), "Würfel"),
    (("msp", "messerspitze"), "Msp."),
    (("packung", "packungen", "pack"), "Packung"),
]:
    for _name in _names:
        _UNIT_CANONICAL[_name] = _canon

# Menge am Zeilenanfang: Dezimalzahl (Komma/Punkt), einfacher Bruch ("1/2")
# oder Bereich ("2-3", übernimmt die erste Zahl).
_QUANTITY_RE = re.compile(r"^(\d+[.,]?\d*)\s*(?:/\s*(\d+))?(?:\s*-\s*\d+[.,]?\d*)?\s*")
_TRAILING_NOTE_RE = re.compile(r"\(([^()]*)\)\s*$")
_FIRST_WORD_RE = re.compile(r"^([^\s,]+)\s*(.*)$")

# Unicode-Bruchzeichen ("½ TL Salz", "1½ EL Öl") — viele Rezeptseiten schreiben Mengen so.
_UNICODE_FRACTIONS = {
    "¼": 0.25, "½": 0.5, "¾": 0.75,
    "⅓": 1 / 3, "⅔": 2 / 3,
    "⅕": 0.2, "⅖": 0.4, "⅗": 0.6, "⅘": 0.8,
    "⅙": 1 / 6, "⅚": 5 / 6,
    "⅛": 0.125, "⅜": 0.375, "⅝": 0.625, "⅞": 0.875,
}
_UNICODE_FRACTION_RE = re.compile(r"^(\d+)?\s*([¼½¾⅓⅔⅕⅖⅗⅘⅙⅚⅛⅜⅝⅞])\s*")

# Kopfzeile innerhalb einer Zutatenliste ("Für den Teig:") statt einer echten Zutat — enthält
# per Definition keine Zahl, sonst würde eine mengenlose Zutat ("Salz") fälschlich rausfallen.
_MARKDOWN_HEADER_RE = re.compile(r"^\*{1,2}[^*]+\*{1,2}:?$")


def is_header_line(raw: str) -> bool:
    trimmed = raw.strip()
    if not trimmed or any(ch.isdigit() for ch in trimmed):
        return False
    return trimmed.endswith(":") or bool(_MARKDOWN_HEADER_RE.match(trimmed))


def parse_ingredient_line(raw: str) -> dict:
    """Zerlegt eine Freitext-Zutatenzeile ("200g Mehl") in quantity/unit/food/note.

    Liefert bei nicht erkennbarer Menge/Einheit bewusst die Defaults
    (0.0/""), der Rest der Zeile bleibt aber immer als `food` erhalten –
    kein Informationsverlust gegenüber dem reinen Freitext.
    """
    rest = raw.strip()
    if not rest:
        return {"quantity": 0.0, "unit": "", "food": "", "note": ""}

    note = ""
    note_match = _TRAILING_NOTE_RE.search(rest)
    if note_match:
        note = note_match.group(1).strip()
        rest = (rest[: note_match.start()] + rest[note_match.end():]).strip()

    quantity = 0.0
    unicode_match = _UNICODE_FRACTION_RE.match(rest)
    if unicode_match:
        whole = float(unicode_match.group(1)) if unicode_match.group(1) else 0.0
        quantity = whole + _UNICODE_FRACTIONS[unicode_match.group(2)]
        rest = rest[unicode_match.end():].strip()
    else:
        qty_match = _QUANTITY_RE.match(rest)
        if qty_match and qty_match.group(0).strip():
            whole = float(qty_match.group(1).replace(",", "."))
            fraction_denom = qty_match.group(2)
            if fraction_denom and float(fraction_denom) != 0.0:
                quantity = whole / float(fraction_denom)
            else:
                quantity = whole
            rest = rest[qty_match.end():].strip()

    unit = ""
    if rest:
        word_match = _FIRST_WORD_RE.match(rest)
        if word_match:
            first_word = word_match.group(1)
            canonical = _UNIT_CANONICAL.get(first_word.lower().rstrip("."))
            if canonical:
                unit = canonical
                rest = word_match.group(2).strip()

    return {"quantity": quantity, "unit": unit, "food": rest.strip(), "note": note}
