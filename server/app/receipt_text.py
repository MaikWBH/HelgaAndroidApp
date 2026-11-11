"""Schlüssel- und Datumshilfen für Bons (reine Funktionen, ohne Datenbankzugriff).

`normalize_item_key` entspricht 1:1 `ReceiptItemNormalizer.normalize` der App.
"""
import calendar
import re
from datetime import datetime

_WS = re.compile(r"\s+")
_TRAILING_SUFFIX = re.compile(r"\s+(?:[a-z]|€|eur)$")
_NON_NAME_CHARS = re.compile(r"[^a-z0-9äöüß]+")


def normalize_item_key(text: str | None) -> str:
    """Trim, klein, Mehrfach-Leerraum zu einem Leerzeichen, angehängte Steuerklasse/€/EUR entfernen."""
    cleaned = _WS.sub(" ", (text or "").strip().lower())
    return _TRAILING_SUFFIX.sub("", cleaned)


def _clean_store_name(name: str | None) -> str:
    return _WS.sub(" ", _NON_NAME_CHARS.sub(" ", (name or "").lower())).strip()


def store_key(store_id: str | None, store_name: str | None) -> str:
    """Markt-Schlüssel: `store_id`, sonst `name:<bereinigter Name>`, beides leer → ''."""
    if store_id:
        return store_id
    cleaned = _clean_store_name(store_name)
    return f"name:{cleaned}" if cleaned else ""


def match_store(store_name: str | None, stores: list[dict]) -> dict | None:
    """Findet den bekannten Markt zu einem Bon-Markennamen.

    Treffer, wenn der bereinigte Marktname `m` nicht leer ist und (`n == m` oder `n` beginnt mit
    `m + " "` oder `m` beginnt mit `n + " "`); bei mehreren Treffern gewinnt der längste `m`.
    """
    n = _clean_store_name(store_name)
    if not n:
        return None
    best = None
    best_len = -1
    for store in stores:
        m = _clean_store_name(store.get("name"))
        if not m:
            continue
        if n == m or n.startswith(m + " ") or m.startswith(n + " "):
            if len(m) > best_len:
                best, best_len = store, len(m)
    return best


def day_to_ms(day: str | None) -> int:
    """Mitternacht UTC des Tages `YYYY-MM-DD` in Unix-ms; ungültig → 0."""
    try:
        parsed = datetime.strptime((day or "").strip(), "%Y-%m-%d")
    except ValueError:
        return 0
    return calendar.timegm(parsed.timetuple()) * 1000
