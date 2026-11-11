"""Laufzeit-Einstellungen des Servers.

Werden nach `load_dotenv()` einmal aus der Umgebung gelesen, aber von den Aufrufern immer
zur Aufrufzeit über das Modul gelesen (`settings.API_KEY`), damit Tests sie umstellen können.
"""
import os
from pathlib import Path

from dotenv import load_dotenv

load_dotenv()

API_KEY = os.getenv("API_KEY", "")
IMAGES_DIR = Path(os.getenv("IMAGES_DIR", "data/images"))
IMAGES_DIR.mkdir(parents=True, exist_ok=True)

# Takt der Re-Parse-Warteschlange für Bons in Sekunden; 0 = Warteschlange nicht starten.
RECEIPT_QUEUE_INTERVAL_SEC = int(os.getenv("RECEIPT_QUEUE_INTERVAL_SEC", "600"))

# Obergrenze für hochgeladene Bon-Fotos.
MAX_RECEIPT_IMAGE_BYTES = 8 * 1024 * 1024
