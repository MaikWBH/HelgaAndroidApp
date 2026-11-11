"""Gemeinsamer Kategorien-Katalog (Bons, Auswertung, Gänge).

Quelle der Wahrheit: .claude/phasen/phase-3-daten/kategorien.md. Schlüssel sind stabil;
die deutschen Namen sind nur Anzeige. Änderungen am Katalog brauchen eine neue Entscheidung.
"""

CATEGORIES: list[tuple[str, str]] = [
    ("obst_gemuese", "Obst & Gemüse"),
    ("brot_backwaren", "Brot & Backwaren"),
    ("milch_eier", "Milchprodukte & Eier"),
    ("fleisch_wurst", "Fleisch & Wurst"),
    ("fisch", "Fisch"),
    ("tiefkuehl", "Tiefkühl"),
    ("trockenware", "Konserven & Trockenware"),
    ("wuerzen_backen", "Backen, Gewürze & Saucen"),
    ("fruehstueck", "Frühstück, Kaffee & Tee"),
    ("getraenke", "Getränke"),
    ("alkohol", "Bier, Wein & Spirituosen"),
    ("suesses_snacks", "Süßes & Snacks"),
    ("feinkost", "Feinkost & Fertiggerichte"),
    ("drogerie", "Drogerie & Baby"),
    ("haushalt", "Haushalt & Tierbedarf"),
    ("sonstiges", "Sonstiges"),
    ("pfand", "Pfand"),
]

CATEGORY_KEYS: frozenset[str] = frozenset(key for key, _ in CATEGORIES)

# Einträge 1–15: Gang-Kategorien (Standard-Gangsatz neuer Märkte).
AISLE_CATEGORY_KEYS: tuple[str, ...] = tuple(key for key, _ in CATEGORIES[:15])


def coerce(key: str | None) -> str:
    """Normalisiert einen Kategorie-Schlüssel; Unbekanntes wird zu '' (= nicht kategorisiert)."""
    cleaned = (key or "").strip().lower()
    return cleaned if cleaned in CATEGORY_KEYS else ""


def prompt_lines(include_deposit: bool = True) -> str:
    """`- <schluessel>: <Name>` je Zeile, für den Bon-Prompt."""
    return "\n".join(
        f"- {key}: {name}"
        for key, name in CATEGORIES
        if include_deposit or key != "pfand"
    )
