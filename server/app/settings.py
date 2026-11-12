"""Laufzeit-Einstellungen des Servers.

Alle Werte stehen hier zentral und werden aus der Umgebung (bzw. `.env`) gelesen. Die Aufrufer
lesen sie zur Aufrufzeit über das Modul (`settings.API_KEY`), damit Tests sie umstellen können.
"""
import os
from pathlib import Path

from dotenv import load_dotenv

load_dotenv()


def _env_bool(name: str) -> bool:
    return os.getenv(name, "").strip().lower() in ("1", "true", "yes")


# ── Zugang ────────────────────────────────────────────────────────────────────
API_KEY = os.getenv("API_KEY", "")
# Nur für lokale Entwicklung und Tests: Start ohne API_KEY erlauben (sonst verweigert der Server den Start).
ALLOW_NO_AUTH = _env_bool("ALLOW_NO_AUTH")

# ── Pfade ─────────────────────────────────────────────────────────────────────
DB_PATH = os.getenv("DB_PATH", "data/recipes.db")
IMAGES_DIR = Path(os.getenv("IMAGES_DIR", "data/images"))
IMAGES_DIR.mkdir(parents=True, exist_ok=True)

# ── Logging ───────────────────────────────────────────────────────────────────
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO")

# ── KI ────────────────────────────────────────────────────────────────────────
AI_PROVIDER = os.getenv("AI_PROVIDER", "openai")
AI_API_KEY = os.getenv("AI_API_KEY", "")
AI_MODEL = os.getenv("AI_MODEL", "")
AI_API_BASE = os.getenv("AI_API_BASE", "")
# Referenzmodell (stärker, bild-fähig) – nur vom Testset-Skript scripts/receipt_testset.py genutzt.
AI_VISION_MODEL = os.getenv("AI_VISION_MODEL", "")
# Modell für ALLE Bon-Aufrufe (Auslesen, Abgleich, Kategorisieren). Leer = Standard je Provider.
AI_RECEIPT_MODEL = os.getenv("AI_RECEIPT_MODEL", "")
# output_config.effort für Modelle, die es kennen: low | medium | high
AI_RECEIPT_EFFORT = os.getenv("AI_RECEIPT_EFFORT", "medium")

# ── Warteschlange und Uploads ─────────────────────────────────────────────────
# Takt der Re-Parse-Warteschlange für Bons in Sekunden; 0 = Warteschlange nicht starten.
RECEIPT_QUEUE_INTERVAL_SEC = int(os.getenv("RECEIPT_QUEUE_INTERVAL_SEC", "600"))

# Obergrenze für hochgeladene Bon-Fotos.
MAX_RECEIPT_IMAGE_BYTES = 8 * 1024 * 1024
