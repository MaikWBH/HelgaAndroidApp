#!/usr/bin/env python3
"""Listet Bons, die die frühere 3-Monats-Löschung der App auf dem Server als gelöscht markiert hat,
und stellt sie mit --apply wieder her (Soft-Delete rückgängig).

    python scripts/restore_purged_receipts.py --db data/recipes.db [--min-days 25] [--apply]

Kandidaten: deleted = 1, purchase_date > 0 und updated_at - purchase_date >= min-days Tage
(am Kauftag von Hand gelöschte Bons bleiben dadurch gelöscht). Ohne --apply wird nichts geändert.
Mit --apply bekommen Bon und seine gemeinsam gelöschten Positionen deleted = 0, updated_at = jetzt
und eine neue server_seq, damit alle Geräte sie beim nächsten Abgleich erhalten.
Vorher ein Backup der Datenbank ziehen (make backup).
"""
import argparse
import sqlite3
import sys
import time
from datetime import datetime

DAY_MS = 86_400_000


def find_candidates(con: sqlite3.Connection, min_days: int) -> list[tuple]:
    return con.execute(
        "SELECT id, store_name, total_amount, purchase_date, updated_at FROM receipts "
        "WHERE deleted = 1 AND purchase_date > 0 AND updated_at - purchase_date >= ? "
        "ORDER BY purchase_date",
        (min_days * DAY_MS,),
    ).fetchall()


def restore(con: sqlite3.Connection, candidates: list[tuple]) -> int:
    if not candidates:
        return 0
    now = int(time.time() * 1000)
    con.execute("UPDATE sync_state SET seq = seq + 1 WHERE id = 0")
    seq = con.execute("SELECT seq FROM sync_state WHERE id = 0").fetchone()[0]
    for receipt_id, _store, _total, _purchase, receipt_updated in candidates:
        # Nur Positionen, die gemeinsam mit dem Bon gelöscht wurden (gleicher updated_at)
        con.execute(
            "UPDATE receipt_items SET deleted = 0, updated_at = ?, server_seq = ? "
            "WHERE receipt_id = ? AND deleted = 1 AND updated_at = ?",
            (now, seq, receipt_id, receipt_updated),
        )
        con.execute(
            "UPDATE receipts SET deleted = 0, updated_at = ?, server_seq = ? WHERE id = ?",
            (now, seq, receipt_id),
        )
    con.commit()
    return len(candidates)


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="Automatisch gelöschte Bons wiederherstellen")
    parser.add_argument("--db", required=True, help="Pfad zur SQLite-Datenbank des Servers")
    parser.add_argument("--min-days", type=int, default=25)
    parser.add_argument("--apply", action="store_true", help="Wirklich wiederherstellen")
    args = parser.parse_args(argv)

    con = sqlite3.connect(args.db)
    try:
        candidates = find_candidates(con, args.min_days)
        for receipt_id, store, total, purchase, updated in candidates:
            day = datetime.utcfromtimestamp(purchase / 1000).strftime("%Y-%m-%d")
            days = (updated - purchase) // DAY_MS
            print(f"{day}  {store or '–':<20} {total:>8.2f} €  gelöscht nach {days} Tagen  [{receipt_id}]")
        if not args.apply:
            print(f"{len(candidates)} Kandidat(en). Mit --apply wiederherstellen.")
            return 0
        print(f"{restore(con, candidates)} Bon(s) wiederhergestellt.")
        return 0
    finally:
        con.close()


if __name__ == "__main__":
    sys.exit(main())
