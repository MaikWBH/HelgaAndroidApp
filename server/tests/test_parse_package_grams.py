"""Tests für die Packungsgrößen-Ableitung aus OFF-Rohdaten.

Ausführen (vom server/-Verzeichnis aus, damit `app` importierbar ist):

    cd server && python -m pytest tests/test_parse_package_grams.py
"""
import os
import sys

import pytest

# server/ in den Pfad legen, damit `from app.off import ...` funktioniert,
# unabhängig davon, von wo pytest gestartet wird.
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from app.off import parse_package_grams


@pytest.mark.parametrize(
    "quantity,expected",
    [
        ("500 g", 500.0),
        ("1 l", 1000.0),
        ("1.5kg", 1500.0),
        ("6x125g", 750.0),
        ("2 x 200 ml", 400.0),
        ("330ml", 330.0),
        ("", 0.0),
        ("1 Packung", 0.0),
    ],
)
def test_parse_from_quantity_text(quantity, expected):
    assert parse_package_grams({"quantity": quantity}) == pytest.approx(expected)


def test_product_quantity_takes_precedence():
    # Strukturiertes product_quantity hat Vorrang vor dem Freitext.
    data = {"product_quantity": 750, "quantity": "500 g"}
    assert parse_package_grams(data) == pytest.approx(750.0)


def test_comma_decimal():
    assert parse_package_grams({"quantity": "1,5 kg"}) == pytest.approx(1500.0)


def test_empty_dict():
    assert parse_package_grams({}) == 0.0
