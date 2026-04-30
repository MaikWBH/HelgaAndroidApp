# HelgaSyncServer – Deployment auf CasaOS

Schritt-für-Schritt-Anleitung um den HelgaSyncServer auf einem CasaOS-Homeserver zum Laufen zu bringen.

Der Server läuft als Docker-Container und ist über Port 8000 im Heimnetz erreichbar. Die Android-App verbindet sich dann direkt über die lokale IP.

---

## Voraussetzungen

| Was | Wo prüfen |
|-----|-----------|
| CasaOS läuft und ist per SSH erreichbar | CasaOS-Dashboard → IP-Adresse oben links |
| Docker installiert | Per SSH: `docker --version` |
| Git installiert | Per SSH: `git --version` (sonst: `sudo apt install git`) |
| Helga-Flask-App läuft (für Migration) | Optional – nur wenn Daten übernommen werden sollen |

---

## Schritt 1 – SSH-Verbindung zum CasaOS-Server

```bash
ssh dein-user@192.168.x.x
```

Die IP-Adresse steht im CasaOS-Dashboard oben links.

---

## Schritt 2 – Verzeichnis anlegen und Repo klonen

```bash
# CasaOS-Standard-Pfad für App-Daten
sudo mkdir -p /DATA/AppData/helga-sync
sudo chown $USER:$USER /DATA/AppData/helga-sync

# Repo klonen (GitHub-URL oder eigene Git-URL eintragen)
git clone git@github.com:DEIN-USERNAME/HelgaAndroidApp.git /DATA/AppData/helga-sync
cd /DATA/AppData/helga-sync/server
```

**Alternative ohne GitHub** – Dateien direkt vom Entwicklungsrechner übertragen:

```bash
# Vom Entwicklungsrechner ausführen:
rsync -avz --exclude='.git' --exclude='data/recipes.db' --exclude='data/backups' \
    /mnt/games_data/Repo/HelgaAndroidApp/server/ \
    dein-user@192.168.x.x:/DATA/AppData/helga-sync/
```

---

## Schritt 3 – `.env` Datei anlegen

```bash
cd /DATA/AppData/helga-sync
cp .env.example .env
nano .env
```

Folgende Werte eintragen:

```env
# Pflicht: zufälliger langer String – wird in der Android-App als X-Api-Key eingetragen
# Generieren mit: openssl rand -hex 32
API_KEY=hier-ein-langer-zufaelliger-string

# KI-Anbieter: "openai" oder "anthropic"
AI_PROVIDER=openai
AI_API_KEY=sk-dein-openai-api-key

# Optional: Modell-Override (sonst wird der Default-Wert verwendet)
# OpenAI:    gpt-4o, gpt-4o-mini
# Anthropic: claude-sonnet-4-6, claude-haiku-4-5-20251001
AI_MODEL=gpt-4o-mini

# Pfade – nicht ändern, passen zum Docker-Volume
DB_PATH=data/recipes.db
IMAGES_DIR=data/images
```

---

## Schritt 4 – Container bauen und starten

```bash
cd /DATA/AppData/helga-sync
docker compose -f docker-compose.prod.yml up -d --build
```

Beim ersten Start: ca. 2–3 Minuten (Python-Image + Dependencies werden heruntergeladen).

**Status prüfen:**

```bash
# Container-Zustand anzeigen (Erwartung: "healthy")
docker compose -f docker-compose.prod.yml ps

# Health-Endpunkt direkt aufrufen
curl http://localhost:8000/api/health
# Antwort: {"ok":true,"ts":1234567890000}
```

---

## Schritt 5 – Port freigeben (falls UFW aktiv)

CasaOS nutzt meistens UFW als Firewall:

```bash
sudo ufw allow 8000/tcp
sudo ufw status
```

---

## Schritt 6 – Helga-Daten migrieren (einmalig)

Nur nötig wenn Daten aus der bestehenden Helga-Flask-App übernommen werden sollen.

Helga-Datenbankpfad auf dem Server finden:

```bash
find /DATA/AppData -name "recipes.db" 2>/dev/null
```

Migration ausführen (Pfad `--src` anpassen):

```bash
cd /DATA/AppData/helga-sync

docker compose -f docker-compose.prod.yml exec helga-sync \
    python scripts/migrate_from_helga.py \
    --src /DATA/AppData/helga/data/recipes.db \
    --dst data/recipes.db
```

Das Skript ist idempotent – kann mehrfach ausgeführt werden ohne Datenverlust.

**Beispiel-Ausgabe:**

```
Migriere: /DATA/AppData/helga/data/recipes.db → data/recipes.db
Migration abgeschlossen:
  recipes: 142 Einträge
  recipe_ingredients: 1847 Einträge
  recipe_instructions: 763 Einträge
  recipe_tags: 284 Einträge
  ...
```

---

## Schritt 7 – Makefile konfigurieren (Entwicklungsrechner)

In [server/Makefile](../server/Makefile) die ersten zwei Zeilen anpassen:

```makefile
SERVER_HOST = dein-user@192.168.x.x     # IP des CasaOS-Servers
SERVER_PATH = /DATA/AppData/helga-sync
```

Ab jetzt laufen alle Deployments über `make` vom Entwicklungsrechner:

```bash
make status    # Container-Status + Health-Check
make logs      # Live-Logs beobachten
make backup    # DB + Bilder sichern
make deploy    # Nach Code-Änderungen: git pull + rebuild (< 30 Sek.)
```

---

## Schritt 8 – Android-App verbinden

Wenn Phase 1 (Android Foundation) fertig ist, folgende Daten beim Onboarding eingeben:

| Feld | Wert |
|------|------|
| Server-URL | `http://192.168.x.x:8000` |
| API-Key | Wert aus `.env` → `API_KEY` |

Die IP-Adresse ist dieselbe wie für SSH.

---

## Schnelltest vom Entwicklungsrechner

```bash
SERVER_IP=192.168.x.x
API_KEY=dein-api-key

# Health-Check
curl http://$SERVER_IP:8000/api/health

# Sync-Pull (gibt alle Datensätze zurück – nach Migration gefüllt)
curl -H "X-Api-Key: $API_KEY" \
     "http://$SERVER_IP:8000/api/sync?since=0" | python3 -m json.tool
```

---

## Backup und Restore

```bash
# Manuelles Backup (erzeugt data/backups/backup-DATUM.tar.gz auf dem Server)
make backup

# Backup einspielen
make restore FILE=data/backups/backup-20260430-120000.tar.gz
```

Backups liegen auf dem Server unter `/DATA/AppData/helga-sync/data/backups/`.

---

## Fehlerbehebung

**Container startet nicht:**
```bash
make logs
# Typische Ursachen: .env fehlt, Syntaxfehler in .env, Port 8000 belegt
```

**Port 8000 belegt:**
```bash
sudo lsof -i :8000
# Falls eine andere App den Port nutzt: Port in docker-compose.prod.yml ändern, z.B. "8001:8000"
```

**Health-Check schlägt fehl:**
```bash
docker compose -f docker-compose.prod.yml exec helga-sync \
    python -c "import app.db; print('OK')"
```

**Migration schlägt fehl (Quelldatenbank nicht gefunden):**
```bash
# Helga-Container-Namen herausfinden
docker ps

# Datenbankpfad im Helga-Container ermitteln
docker exec helga-container find / -name "recipes.db" 2>/dev/null
```
