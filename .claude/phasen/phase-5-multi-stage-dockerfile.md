# Phase 5 — multi-stage-dockerfile (`server/Dockerfile`, Compose)

Skill: multi-stage-dockerfile. Bereich: Container-Bau und -Betrieb des Sync-Servers (`server/Dockerfile`,
`server/docker-compose.yml`, `server/docker-compose.prod.yml`, `server/requirements*.txt`, `server/Makefile`).
Feature-Plan mit Server-Bezug: `.claude/features/sync/plan.md`. Produktiv läuft der Server als Submodule im
Homelab-Repo `server_home_lab` (`apps/helga`); dessen Override legt nur Datenpfad und Containername über
`docker-compose.prod.yml`. Dieses Repo ändert nichts im Homelab.

Pfade: `$S` = `server`

## Modelle
- Definition: Sonnet 5.5 · Umsetzung: Haiku 5.5 · Datum: 2026-11-12

## Ziel und Bereich
Das Dockerfile ist einstufig, kennt kein `.dockerignore` (der Build-Kontext enthält `data/recipes.db`, Bilder, `.env`
und Tests), dupliziert beim `chown -R` die App-Dateien in eine Schicht, pinnt `recipe-scrapers` nicht und verdrahtet die
Worker-Zahl fest. Phase 5 macht den Build reproduzierbar und klein im Kontext, trennt Bau- und Laufzeit-Stage, ergänzt
eine Test-Stage, härtet den Betriebs-Container und entfernt das doppelte Healthcheck. **Verhalten und Schnittstellen
bleiben gleich**: Port 8000, Nutzer `helga` mit UID 1000 (damit bestehende Datenverzeichnisse beschreibbar bleiben),
Pfade `/app/data`, Start per `uvicorn app.main:app`.

**Ehrliche Erwartung:** Die Image-Größe sinkt kaum (Prototyp: Baseline 199 MB → 194 MB), denn alle Pakete sind
pure-Python-Quellen oder fertige Wheels; es gibt keinen Compiler, der verschwinden könnte. Der Gewinn liegt in
Reproduzierbarkeit, kleinem Kontext, Tests im Build und Container-Härtung.

## Entscheidungen (aus grilling, 2026-11-12)
- Test-Stage → ja, `docker build --target test .` führt pytest mit der Produktions-Python-Version aus; der
  Standard-Build (letzte Stage = `runtime`) führt sie nicht aus.
- `recipe-scrapers` → auf `==15.13.0` pinnen (heute aufgelöste Version).
- Compose-Härtung → moderat in `docker-compose.prod.yml`: `read_only: true`, `tmpfs: [/tmp]`, `cap_drop: [ALL]`,
  `security_opt: [no-new-privileges:true]`.
- Worker → `ENV WEB_CONCURRENCY=2`, `--workers` fällt aus `CMD`; Uvicorn liest die Variable selbst.
- Von Sonnet 5.5 festgelegt (Prototyp-Ergebnis, im Dateikopf unten begründet): Basis-Image
  `python:3.12-slim-bookworm` über `ARG PYTHON_IMAGE`; Abhängigkeiten per `pip install --prefix=/install` im Builder und
  `COPY --from=builder /install /usr/local` (kein venv: der Prototyp mit venv war 6 MB größer als die Baseline);
  **kein** `--only-binary` (siehe Befund 4); UID 1000 bleibt fest; Healthcheck bleibt im Dockerfile (Python-Einzeiler,
  keine Zusatzpakete) und wird aus dem Prod-Compose entfernt; das Dev-Compose bleibt unverändert; kein Scan mit
  Trivy/Hadolint (nicht installiert).

## Befunde
| # | Datei:Zeile | Befund | Regel (Skill) |
|---|-------------|--------|---------------|
| 1 | `$S/Dockerfile` | Einstufig: Pip-Aufruf und Laufzeit in einem Image, keine Test-Stage | „Multi-Stage Structure" |
| 2 | `$S/` (fehlt) | Kein `.dockerignore`: `data/recipes.db`, `data/images/`, `.env`, `tests/`, `__pycache__` gehen in den Build-Kontext | „Use `.dockerignore`" |
| 3 | `$S/Dockerfile:13-14` | `RUN mkdir … && chown -R helga:helga /app` nach `COPY` dupliziert alle App-Dateien in eine Extra-Schicht | „COPY --chown" |
| 4 | `$S/requirements.txt:7` | `recipe-scrapers>=14.0.0` ungepinnt; `jstyleson` (über `extruct`, 15.x) gibt es **nur als Quellpaket** — mit `--only-binary` fällt pip stillschweigend auf 14.55.0 zurück | „reproducible builds" |
| 5 | `$S/Dockerfile:5`, `:19` | `adduser` ohne feste UID, Healthcheck mit `localhost` (Uvicorn lauscht auf `0.0.0.0`, `localhost` kann zu `::1` auflösen) | „Security Practices", Healthcheck |
| 6 | `$S/Dockerfile:23` | `--workers 2` fest in `CMD`, nicht per Umgebung änderbar | „build arguments / Konfiguration" |
| 7 | `$S/docker-compose.prod.yml` | Healthcheck doppelt (identisch zum Dockerfile); keine Härtung (`read_only`, `cap_drop`, `no-new-privileges`) | „Security Practices" |
| 8 | `$S/Makefile` | Keine Möglichkeit, die Tests im Container laufen zu lassen | – |

Geprüft und **in Ordnung**: Nicht-Root-Nutzer, Basis-Image-Familie `python:3.12-slim`, `--no-cache-dir`, Datenvolumen
`./data:/app/data`, Port, `restart: unless-stopped`, Log-Rotation im Prod-Compose, Warteschlange mit mehreren Workern.

## Umgebung für die Verifikation (wichtig)
Diese Cloud-Umgebung hat keinen laufenden Docker-Daemon und blockiert Container-Netz über den Proxy. Der Daemon wurde in
Stufe A manuell gestartet und die Basis-Images sind gezogen, **kann aber nicht mehr laufen**, falls die Sitzung
neugestartet wurde. Vorgehen (in S0 erledigen):
1. Läuft `docker info` nicht, Daemon starten:
   `(nohup dockerd --iptables=false --ip6tables=false --bridge=none --storage-driver=vfs > /tmp/dockerd.log 2>&1 &) ; sleep 8; docker info | grep "Server Version"`
2. Basis-Image ziehen: `docker pull python:3.12-slim-bookworm`.
3. Hilfsskript `docker-verify.sh` (außerhalb des Repos, im Scratchpad; **nie committen**) anlegen — es baut `$S` mit
   `--network host`, kopiert das Proxy-CA-Bundle in den Build und setzt `PIP_CERT`:
   ```bash
   #!/bin/bash
   # docker-verify.sh <tag> [target] — baut server/ mit Proxy-/CA-Workaround, ohne das Repo zu verändern
   set -e
   TAG=${1:?tag}; TARGET_ARG=${2:+--target $2}
   CTX=/tmp/claude-0/ctx; rm -rf "$CTX"; mkdir -p "$CTX"
   cp -r /home/user/HelgaAndroidApp/server/. "$CTX/"
   rm -rf "$CTX/.env"
   cp /root/.ccr/ca-bundle.crt "$CTX/ca-bundle.crt"
   printf '!ca-bundle.crt\n' >> "$CTX/.dockerignore"
   sed '/^FROM /a COPY ca-bundle.crt /ca.crt\nENV PIP_CERT=/ca.crt SSL_CERT_FILE=/ca.crt' "$CTX/Dockerfile" > "$CTX/Dockerfile.verify"
   docker build $TARGET_ARG --network host --build-arg HTTPS_PROXY="$HTTPS_PROXY" --build-arg HTTP_PROXY="$HTTP_PROXY" -f "$CTX/Dockerfile.verify" -t "$TAG" "$CTX"
   docker image ls "$TAG" --format '{{.Repository}}:{{.Tag}} {{.Size}}'
   ```
   Eine Kopie liegt in `/tmp/claude-0/-home-user-HelgaAndroidApp/900193cb-5e4c-5bbc-8263-7ddb9a8e3e37/scratchpad/docker-verify.sh`.
   Aufruf: `/tmp/claude-0/docker-verify.sh <tag> [target]` (Skript dorthin kopieren oder den Scratchpad-Pfad nutzen).
   Läuft gar nichts (Daemon startet nicht, Image-Pull scheitert): **stoppen und melden**, nicht raten.
Der Build-Kontext des Skripts ist eine Kopie von `$S` und wendet `.dockerignore` an; die Ausgabe `transferring context`
zeigt die Kontextgröße.

**Verifikation `V`:** `/tmp/claude-0/docker-verify.sh helga-verify:new` baut erfolgreich (Default-Target).
`VT`: `/tmp/claude-0/docker-verify.sh helga-test:new test` endet mit `… passed` (Erwartung: 177 Tests). `VS`:
`cd server && python3 -m pytest -q` bleibt grün (177), weil sich Python-Code nicht ändert.

## Schritte (Stufe B arbeitet nur diese Liste ab)

### Block A — Image
- [x] **S0 Daemon, Hilfsskript, Baseline** — keine Repo-Dateien. Abschnitt „Umgebung für die Verifikation" ausführen.
  Dann die **unveränderte** Baseline bauen und messen: `/tmp/claude-0/docker-verify.sh helga-verify:old` und notiere
  die Ausgabezeile (Größe; erwartet ≈ 199 MB). `docker run --rm helga-verify:old id helga` → `uid=1000`. `VS` ausführen
  (erwartet 177). Werte unter „Abschluss" notieren. Akzeptanz: Baseline-Größe und UID notiert.
- [x] **S1 `.dockerignore`** — Datei: neu `$S/.dockerignore` mit genau diesem Inhalt:
  ```
  **
  !requirements.txt
  !requirements-dev.txt
  !app/
  !scripts/
  !tests/
  **/__pycache__
  **/*.pyc
  ```
  Verifikation: lege eine 20-MB-Testdatei an (`head -c 20000000 /dev/zero > $S/data/dummy.bin`, liegt in einem durch
  `.gitignore` ausgeschlossenen Ordner, danach löschen) und baue mit dem Hilfsskript: die Zeile `transferring context`
  zeigt weniger als 1 MB. Akzeptanz: Kontext < 1 MB trotz Testdatei; Build der **alten** Dockerfile bleibt grün
  (`/tmp/claude-0/docker-verify.sh helga-verify:ignore`).
- [x] **S2 Abhängigkeit pinnen** — Datei: `$S/requirements.txt`. Zeile `recipe-scrapers>=14.0.0` →
  `recipe-scrapers==15.13.0`. Alle anderen Zeilen unverändert. Akzeptanz: Zeile geändert; Build in S3 löst auf 15.13.0 auf.
- [x] **S3 Multi-Stage-Dockerfile** — Datei: `$S/Dockerfile` **komplett ersetzen** durch genau diesen Inhalt (im Prototyp
  gebaut und geprüft, Quelle: Scratchpad `Dockerfile.final`):
  ```dockerfile
  ARG PYTHON_IMAGE=python:3.12-slim-bookworm

  # ── builder: Abhängigkeiten installieren (jstyleson liegt nur als Quellpaket vor) ──
  FROM ${PYTHON_IMAGE} AS builder
  ENV PIP_NO_CACHE_DIR=1 PIP_DISABLE_PIP_VERSION_CHECK=1
  WORKDIR /build
  COPY requirements.txt .
  RUN pip install --prefix=/install -r requirements.txt

  # ── test: nur mit `docker build --target test .` ─────────────────────────────
  FROM ${PYTHON_IMAGE} AS test
  ENV PIP_NO_CACHE_DIR=1 PIP_DISABLE_PIP_VERSION_CHECK=1 PYTHONDONTWRITEBYTECODE=1
  WORKDIR /app
  COPY --from=builder /install /usr/local
  COPY requirements-dev.txt requirements.txt ./
  RUN pip install -r requirements-dev.txt
  COPY app/ ./app/
  COPY scripts/ ./scripts/
  COPY tests/ ./tests/
  RUN python -c "from recipe_scrapers import scrape_html; from recipe_scrapers._exceptions import NoSchemaFoundInWildMode, WebsiteNotImplementedError" \
   && python -m pytest -q

  # ── runtime ──────────────────────────────────────────────────────────────────
  FROM ${PYTHON_IMAGE} AS runtime
  ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 WEB_CONCURRENCY=2
  RUN adduser --disabled-password --gecos "" --uid 1000 helga
  WORKDIR /app
  COPY --from=builder /install /usr/local
  COPY --chown=helga:helga app/ ./app/
  COPY --chown=helga:helga scripts/ ./scripts/
  RUN mkdir -p data/images && chown helga:helga data data/images
  USER helga
  EXPOSE 8000
  HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/api/health')"
  CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
  ```
  Verifikation: `V` (Größe notieren, erwartet ≈ 194 MB, jedenfalls **nicht größer als die Baseline**);
  `docker run --rm helga-verify:new sh -c 'id helga; pip list 2>/dev/null | grep -i "recipe\|uvicorn"; ls -ld /app /app/data'`
  → `uid=1000(helga)`, `recipe_scrapers 15.13.0`, `/app` gehört `root`, `/app/data` gehört `helga`. Akzeptanz: alles erfüllt.
- [x] **S4 Test-Stage und Makefile** — Dateien: `$S/Makefile`. Verifikation `VT` (erwartet `177 passed`). Im Makefile unter
  `.PHONY` und in der Hilfe-Ausgabe ein Ziel `docker-test` ergänzen (`docker build --target test -t helga-test .`,
  Hilfetext `make docker-test       – Server-Tests im Container (Python 3.12)`). Das Makefile-Ziel selbst wird hier nicht
  ausgeführt (Proxy-Workaround nötig). Akzeptanz: `VT` grün; `make -n docker-test` zeigt den Befehl. Danach Block A pushen.

### Block B — Compose, Doku
- [x] **S5 Prod-Compose härten** — Datei: `$S/docker-compose.prod.yml`. Den kompletten `healthcheck:`-Block entfernen
  (steht im Dockerfile). Unter `helga-sync:` ergänzen: `read_only: true`, `tmpfs:` mit `- /tmp`, `cap_drop:` mit `- ALL`,
  `security_opt:` mit `- no-new-privileges:true`. `build`, `restart`, `ports`, `volumes`, `env_file`, `logging` unverändert.
  Verifikation: (a) `cd $S && docker compose -f docker-compose.prod.yml config > /dev/null` (Syntax) und
  `docker compose -f docker-compose.yml config > /dev/null`; (b) Härtungs-Lauf mit dem in S3 gebauten Image
  (Daten-Ordner anlegen, `chown 1000:1000`, Host-Netz weil keine Bridge):
  ```bash
  rm -rf /tmp/hdata && mkdir /tmp/hdata && chown 1000:1000 /tmp/hdata
  docker run -d --name hv --network host --read-only --tmpfs /tmp --cap-drop ALL --security-opt no-new-privileges \
    -v /tmp/hdata:/app/data -e API_KEY=testkey helga-verify:new
  sleep 6
  curl -s http://127.0.0.1:8000/api/health                              # {"ok":true,…}
  curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8000/api/sync   # 401
  printf '\xff\xd8\xff\xe0jpegdata' > /tmp/t.jpg
  curl -s -H "X-Api-Key: testkey" -F "file=@/tmp/t.jpg" http://127.0.0.1:8000/api/images/upload   # uuid + filename
  ls /tmp/hdata /tmp/hdata/images                                      # recipes.db, Bilddatei
  docker exec hv python -c "import urllib.request;print(urllib.request.urlopen('http://127.0.0.1:8000/api/health').status)"  # 200
  docker rm -f hv
  ```
  Außerdem ohne `API_KEY` starten (Korrektur nach Rückmeldung von Stufe B: mit mehreren Uvicorn-Workern **beendet** ein
  Startfehler den Container nicht, der Supervisor startet die Worker immer wieder neu — das war schon vor Phase 5 so und
  bleibt; kein neuer Code): `docker run -d --name hn --network host --health-interval=5s --health-start-period=1s
  --health-retries=2 helga-verify:new`, nach etwa 30 Sekunden: `docker logs hn 2>&1 | grep -c "API_KEY fehlt"` ist größer
  als 0 und `docker inspect -f '{{.State.Health.Status}}' hn` ist `unhealthy`; danach `docker rm -f hn`. Akzeptanz: alle
  Antworten wie angegeben.
- [ ] **S6 Dokumentation** — Dateien: `$S/CLAUDE.md` (Abschnitt „Deployment": Mehrstufiges Image, `--target test`,
  `make docker-test`, `WEB_CONCURRENCY`, gehärteter Prod-Container: schreibbar sind nur `/app/data` und `/tmp`; neue
  Schreibpfade im Code müssen unter `/app/data` liegen; Pin von `recipe-scrapers` und Hinweis zu `jstyleson` →
  kein `--only-binary`; außerdem der Hinweis: Fehlt `API_KEY`, beendet sich der Container **nicht** — die Worker brechen
  mit „API_KEY fehlt …" ab, Uvicorn startet sie neu, der Container wird `unhealthy`; Ursache im Log suchen),
  `$S/.env.example` (Zeile `# WEB_CONCURRENCY=2` mit Kommentar „Anzahl Uvicorn-Worker, Standard 2"),
  `doku/casaos-deployment.md` (kurzer Hinweis nahe der Compose-Befehle: Datenordner muss UID 1000 gehören; Container ist
  `read_only`), `.claude/features/sync/plan.md` (Abschnitt „Phase 5"), `.claude/phasen/README.md` (Phase 5 → „umgesetzt
  (Stufe B)", Umsetzungsmodell Haiku 5.5, Definition Sonnet 5.5). Akzeptanz: Dateien geändert; `VS` grün. Danach Block B pushen.

## Nicht-Ziele
- Mehrarchitektur-Builds (`buildx`), Image-Scan (Trivy/Hadolint), Distroless/Alpine, signierte Images oder Registry-Push.
- Änderungen am Python-Code, an Tests oder am Android-Client.
- Das Homelab-Repo (`server_home_lab`), dessen Override und `deploy.sh`.
- Das Dev-Compose (`docker-compose.yml`) außer der Syntaxprüfung.
- Healthcheck-Logik der API (Prüfung der Datenbank) und Uvicorn-Tuning über `WEB_CONCURRENCY` hinaus.

## Abschluss
- [ ] Baseline (S0): Größe 199 627 092 Bytes (Docker: 200 MB), UID 1000 (`uid=1000(helga)`), `VS` 177 passed; Endstand: Größe …, `VT` …, `VS` …
- [ ] Kontextgröße vorher/nachher (S1)
- [ ] Gemeldete Abweichungen und Ausnahmen (je Schritt eine Zeile)
- [ ] Bereichs-`plan.md` (sync), Phasen-README, `server/CLAUDE.md` aktualisiert (S6)
- [ ] **Hinweis für den Nutzer (Rollout):** Das Image muss im Homelab neu gebaut werden (neuer Tag, `scripts/app.sh bump helga`,
      dann `scripts/deploy.sh`). Der Daten-Ordner muss UID 1000 gehören (wie bisher). Prüfen, dass das Homelab-Override
      weder `read_only`/`tmpfs` noch den Healthcheck überschreibt (`docker compose config` im Homelab ansehen).
- [ ] **Prüfliste (Server)** für den Nutzer, von Stufe B nicht abgehakt: nach dem Deploy zeigt `docker compose ps` den Status
      `healthy`; App-Sync, Foto-Upload (auch ein großes Bon-Foto um 5–8 MB) und Bon-Scan funktionieren; Rezept-Import per
      URL funktioniert (neuer `recipe-scrapers` 15.13.0); `docker stats` zeigt zwei Uvicorn-Worker.
- [ ] **Nicht geprüft** (Stufe B nur ausfüllen): echte ARM-/Multi-Arch-Builds (Homelab-CPU unbekannt; alle Abhängigkeiten außer
      dem reinen Python-Paket `jstyleson` sind Wheels für x86_64 geprüft), Verhalten hinter dem Cloudflare-Tunnel, Image-Scan.
