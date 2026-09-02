# HelgaSyncServer — Betrieb & Neubauen

Praktische Anleitung für den laufenden Betrieb: Server nach Code-Änderungen neu bauen,
konfigurieren, prüfen, sichern. Für die **einmalige Ersteinrichtung** auf einem neuen
CasaOS-Host siehe [`doku/casaos-deployment.md`](../doku/casaos-deployment.md) — dort stehen
SSH-Zugriff, Verzeichnis anlegen, erstes `.env` und Datenmigration aus der alten Helga-Flask-App
Schritt für Schritt. Architektur/Endpunkte/Konventionen stehen in [`CLAUDE.md`](CLAUDE.md).

---

## Server neu bauen — Schnellstart

Der Server ist bereits eingerichtet unter `/DATA/AppData/helga-sync` auf dem CasaOS-Host. Neu
bauen heißt: neuester Code vom Git-Branch + `docker compose ... up -d --build`.

### Variante A — vom Entwicklungsrechner (empfohlen)

`SERVER_HOST` in [`Makefile`](Makefile) muss auf den Server zeigen (`user@ip`), Standard ist
aktuell `maik@192.168.178.68`.

```bash
cd server
make deploy      # ssh + git pull + docker compose -f docker-compose.prod.yml up -d --build
make status      # Container-Status + Health-Check
make logs        # Live-Logs, Strg+C zum Beenden
```

`make deploy` zieht per `git pull` **den auf dem Server ausgecheckten Branch** — normalerweise
`main`. Wenn Änderungen auf einem anderen Branch liegen (z. B. einem Feature-Branch aus einer
Claude-Code-Session), muss der Server-Checkout erst auf den richtigen Branch/Commit gebracht
werden, siehe [Branch-Stand prüfen](#branch-stand-prüfen-vor-dem-deploy) unten.

### Variante B — direkt auf dem Server per SSH

```bash
ssh maik@192.168.178.68
cd /DATA/AppData/helga-sync
git pull
docker compose -f docker-compose.prod.yml up -d --build
```

Kurzform als Einzeiler (identisch zu `doku/serverBuild.md`, ohne den `git pull`-Schritt):

```bash
docker compose -f docker-compose.prod.yml build && docker compose -f docker-compose.prod.yml up -d
```

### Variante C — lokal zur Entwicklung (kein SSH, kein CasaOS)

```bash
cd server
cp .env.example .env   # einmalig, falls noch nicht vorhanden — Werte eintragen, siehe unten
make dev                # = docker compose up --build (nutzt docker-compose.yml, Port 8000)
```

Android-Emulator erreicht das lokal so über `http://10.0.2.2:8000`, ein physisches Gerät im
selben Netz über die lokale IP des Entwicklungsrechners.

---

## Branch-Stand prüfen (vor dem Deploy)

`make deploy`/`git pull` holt immer den Branch, der auf dem Server bereits ausgecheckt ist.
Auf einem anderen Branch entwickelter Code kommt so **nicht** automatisch an. Prüfen:

```bash
ssh maik@192.168.178.68 "cd /DATA/AppData/helga-sync && git branch --show-current"
```

Steht das gewünschte Ergebnis auf einem anderen Branch (z. B. einem noch offenen Feature-Branch,
der noch nicht in `main` gemerged ist), einmalig umstellen:

```bash
ssh maik@192.168.178.68 "cd /DATA/AppData/helga-sync && git fetch origin && git checkout <branch-name> && git pull"
```

Ab dann zieht `make deploy` wieder normal auf diesem Branch nach. Zurück auf `main`:
`git checkout main && git pull`.

---

## Konfiguration (`.env`)

Liegt auf dem Server unter `/DATA/AppData/helga-sync/.env`, ist gitignored (enthält Secrets).
Vorlage mit allen Variablen: [`.env.example`](.env.example).

| Variable | Pflicht | Bedeutung |
|----------|---------|-----------|
| `API_KEY` | ja | Shared Secret zwischen App und Server (Header `X-Api-Key`). Erzeugen: `openssl rand -hex 32` |
| `AI_PROVIDER` | ja | `openai` oder `anthropic` |
| `AI_API_KEY` | ja | API-Key des gewählten Anbieters |
| `AI_MODEL` | nein | Modell-Override für generate/remix/classify (sonst günstiger Provider-Default) |
| `AI_VISION_MODEL` | nein | Eigenes, stärkeres Modell nur für Kassenbon-Scans — sonst Fallback auf `AI_MODEL`, dann auf einen starken Default (siehe `CLAUDE.md` → KI-Endpunkte) |
| `AI_API_BASE` | nein | Alternative API-Basis-URL, z. B. für einen OpenAI-kompatiblen Proxy |
| `DB_PATH` | ja | Pfad zur SQLite-Datei im Container, Standard `data/recipes.db` — an Volume-Mount gebunden, nicht ändern |
| `IMAGES_DIR` | ja | Bild-Ablage im Container, Standard `data/images` — dito |

Änderung an `.env` erfordert einen Neustart des Containers (`docker compose ... up -d`, kein
`--build` nötig, wenn sich nur Umgebungsvariablen ändern).

---

## Nach dem Deploy prüfen

```bash
make status
# oder direkt:
curl http://192.168.178.68:8000/api/health
# erwartete Antwort: {"ok":true,"ts":<unix-ms>}
```

`docker compose -f docker-compose.prod.yml ps` sollte den Container als `healthy` zeigen (der
Healthcheck im `Dockerfile` ruft denselben `/api/health`-Endpunkt alle 30s auf).

---

## Backup vor größeren Updates

Besonders vor einem Deploy mit Schemaänderung sinnvoll (Schemaänderungen selbst sind laut
Konvention nicht-destruktiv, siehe `CLAUDE.md`, aber ein Backup kostet nichts):

```bash
make backup      # data/backups/backup-<datum>.tar.gz auf dem Server
make restore FILE=data/backups/backup-20260901-120000.tar.gz
```

---

## Fehlerbehebung

**Container startet nicht / crasht sofort:**
```bash
make logs
```
Typische Ursachen: `.env` fehlt oder hat einen Syntaxfehler, Port 8000 bereits belegt.

**Port 8000 belegt:**
```bash
ssh maik@192.168.178.68 "sudo lsof -i :8000"
```
Falls dauerhaft ein anderer Dienst den Port braucht: in `docker-compose.prod.yml` z. B. auf
`"8001:8000"` ändern (App-seitig dann die Server-URL entsprechend anpassen).

**Health-Check schlägt fehl, Container läuft aber:**
```bash
docker compose -f docker-compose.prod.yml exec helga-sync python -c "import app.db; print('OK')"
```
Wirft das einen Fehler, liegt es meist an einem defekten Schema-Update — Logs (`make logs`)
zeigen die genaue Exception.

**"unhealthy" trotz erreichbarem Endpunkt:** ein `--build` ohne vorheriges `down` behält oft
noch den alten Healthcheck-Zustand — einmal `docker compose -f docker-compose.prod.yml restart`
reicht meist.

Ausführlichere Fehlerbehebung (inkl. Migrations-Fehlern) in
[`doku/casaos-deployment.md`](../doku/casaos-deployment.md#fehlerbehebung).

---

## Alle Makefile-Befehle

```bash
make help
```

```
make deploy           – Server aktualisieren (git pull + rebuild)
make logs              – Live-Logs vom Server
make status            – Container-Status + Health-Check
make backup             – DB + Bilder sichern
make restore FILE=...  – Backup einspielen
make migrate            – Helga-Daten importieren (einmalig)
make dev                – Lokal starten (kein SSH)
```
