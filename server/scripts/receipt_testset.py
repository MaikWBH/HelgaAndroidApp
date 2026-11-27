#!/usr/bin/env python3
"""Qualitätstest für das Bon-Modell: vergleicht Modelle auf einem Satz Bon-Fotos.

Aufruf (vom server/-Verzeichnis aus, mit echtem KI-Schlüssel in der Umgebung / .env):

    python scripts/receipt_testset.py --dir /pfad/zu/bon-fotos [--models a,b] [--out bericht.md] [--dry-run]

Fotos und Berichte werden NIE ins Repo geschrieben; `--out` ist standardmäßig die Standardausgabe.
Pro Foto kann eine Datei `<name>.expected.json` die Wahrheit festlegen:
    {"store_name": "REWE", "purchase_date": "2026-11-10", "total_amount": 12.34, "item_count": 9}
Fehlt sie, gilt das letzte Modell der Liste (Referenzmodell) als Maßstab.
"""
import argparse
import asyncio
import base64
import json
import os
import re
import sys
import time
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app import ai, settings  # noqa: E402

PHOTO_SUFFIXES = {".jpg", ".jpeg", ".png", ".webp"}
MIME = {".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".png": "image/png", ".webp": "image/webp"}


def _clean(name) -> str:
    return re.sub(r"\s+", " ", re.sub(r"[^a-z0-9äöüß]+", " ", str(name or "").lower())).strip()


def _day(value) -> str:
    """Normalisiert ein Datum (YYYY-MM-DD oder Unix-ms) zu YYYY-MM-DD."""
    if isinstance(value, (int, float)) and value > 0:
        return datetime.utcfromtimestamp(value / 1000).strftime("%Y-%m-%d")
    return str(value or "")[:10]


def compare(result: dict, truth: dict) -> dict:
    """Vergleicht ein Ergebnis mit der Wahrheit. Wert je Kriterium: True/False, None = nicht prüfbar."""
    out = {"store": None, "date": None, "total": None, "items": None}
    if "store_name" in truth:
        a, b = _clean(result.get("store_name")), _clean(truth["store_name"])
        out["store"] = bool(a and b and (a == b or a.startswith(b) or b.startswith(a)))
    if "purchase_date" in truth:
        got = result.get("purchase_day") or _day(result.get("purchase_date"))
        out["date"] = bool(got) and got == _day(truth["purchase_date"])
    if "total_amount" in truth:
        out["total"] = abs(float(result.get("total_amount") or 0) - float(truth["total_amount"])) <= 0.01
    if "item_count" in truth:
        out["items"] = abs(len(result.get("items") or []) - int(truth["item_count"])) <= 1
    return out


def item_sum(result: dict) -> float:
    """Σ (total_price − discount) aller Positionen minus Bon-Rabatt."""
    total = sum((i.get("total_price") or 0) - (i.get("discount") or 0) for i in result.get("items") or [])
    return round(total - (result.get("discount_total") or 0), 2)


def render_report(rows: list[dict]) -> str:
    """Markdown-Bericht: Tabelle je Foto und Modell, darunter Trefferquoten je Modell."""
    lines = ["# Bon-Testset", "",
             "| Foto | Modell | Markt | Datum | Summe | Positionen | Σ Positionen | Abweichung | Dauer s |",
             "|------|--------|-------|-------|-------|------------|--------------|------------|---------|"]
    tallies: dict[str, dict[str, list]] = {}
    for row in rows:
        model = row["model"]
        bucket = tallies.setdefault(model, {"store": [], "date": [], "total": [], "items": []})
        if row.get("error"):
            lines.append(f"| {row['photo']} | {model} | Fehler: {row['error']} | | | | | | {row.get('seconds', 0):.1f} |")
            for key in bucket:
                bucket[key].append(False)
            continue
        result = row["result"]
        cmp = row.get("cmp") or {}
        for key in bucket:
            if cmp.get(key) is not None:
                bucket[key].append(bool(cmp[key]))
        total_sum = item_sum(result)
        deviation = round(total_sum - float(result.get("total_amount") or 0), 2)
        lines.append(
            f"| {row['photo']} | {model} | {result.get('store_name') or '–'} | "
            f"{result.get('purchase_day') or '–'} | {float(result.get('total_amount') or 0):.2f} | "
            f"{len(result.get('items') or [])} | {total_sum:.2f} | {deviation:+.2f} | {row.get('seconds', 0):.1f} |"
        )
    lines += ["", "## Trefferquoten je Modell", "",
              "| Modell | Datum | Markt | Summe | Positionen |", "|--------|-------|-------|-------|------------|"]
    for model, bucket in tallies.items():
        def rate(key):
            values = bucket[key]
            return f"{sum(values)}/{len(values)} ({100 * sum(values) // len(values)} %)" if values else "–"
        lines.append(f"| {model} | {rate('date')} | {rate('store')} | {rate('total')} | {rate('items')} |")
    return "\n".join(lines) + "\n"


def list_photos(directory: Path) -> list[Path]:
    return sorted(p for p in directory.iterdir() if p.is_file() and p.suffix.lower() in PHOTO_SUFFIXES)


async def _run_models(photos: list[Path], models: list[str]) -> list[dict]:
    rows: list[dict] = []
    for photo in photos:
        b64 = base64.b64encode(photo.read_bytes()).decode("ascii")
        expected_file = photo.with_suffix(".expected.json")
        expected = json.loads(expected_file.read_text(encoding="utf-8")) if expected_file.exists() else None
        per_photo: list[dict] = []
        for model in models:
            started = time.monotonic()
            row = {"photo": photo.name, "model": model, "seconds": 0.0}
            try:
                row["result"] = await ai.parse_receipt(b64, MIME[photo.suffix.lower()], model=model)
            except Exception as exc:  # einzelne Fehler stoppen den Lauf nicht
                row["error"] = str(exc)[:120]
            row["seconds"] = time.monotonic() - started
            per_photo.append(row)
        reference = per_photo[-1].get("result") if per_photo else None
        truth = expected
        if truth is None and reference:
            truth = {"store_name": reference.get("store_name"), "purchase_date": reference.get("purchase_day"),
                     "total_amount": reference.get("total_amount"), "item_count": len(reference.get("items") or [])}
        for row in per_photo:
            if truth and row.get("result"):
                row["cmp"] = compare(row["result"], truth)
        rows.extend(per_photo)
    return rows


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="Bon-Modelle auf einem Foto-Satz vergleichen")
    parser.add_argument("--dir", default=os.getenv("RECEIPT_TESTSET_DIR", ""))
    parser.add_argument("--models", default="")
    parser.add_argument("--out", default="")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args(argv)

    if not args.dir or not Path(args.dir).is_dir():
        print("Fehler: Foto-Verzeichnis fehlt. --dir <pfad> oder RECEIPT_TESTSET_DIR setzen "
              "(Fotos gehören nie ins Repo).", file=sys.stderr)
        return 2
    photos = list_photos(Path(args.dir))
    if args.dry_run:
        for photo in photos:
            print(photo.name)
        return 0

    models = [m.strip() for m in args.models.split(",") if m.strip()] or [
        ai._receipt_model(), settings.AI_VISION_MODEL or "claude-sonnet-4-6"]
    report = render_report(asyncio.run(_run_models(photos, models)))
    if args.out:
        Path(args.out).write_text(report, encoding="utf-8")
        print(f"Bericht geschrieben: {args.out}")
    else:
        print(report)
    return 0


if __name__ == "__main__":
    sys.exit(main())
