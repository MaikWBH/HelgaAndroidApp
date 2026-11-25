# Einstiegsprompt für die lokale Claude-Sitzung (VSCodium)

Branch `ccr-c53a7201-5hd57f` auschecken, Claude-Sitzung im Repo-Ordner starten und den Text unten einfügen.

```text
Du arbeitest lokal in meinem Checkout von HelgaAndroidApp (Branch ccr-c53a7201-5hd57f). Lies zuerst CLAUDE.md und
server/CLAUDE.md. Deine Aufgabe: den Testplan .claude/phasen/testplan.md mit mir abarbeiten. Der Ergebnisbericht
(.claude/phasen/phase-6-daten/review-bericht.md) liefert den Hintergrund zu den Befunden (V…, B…) und zur Abhakliste G01–G29.

GRUNDSATZ: Alles wird gegen einen LOKALEN Server auf meinem PC getestet, nicht gegen mein Produktiv-Homelab. Das Homelab
wird nur für die Punkte aus Schritt 4 genutzt, und dort nur lesend.

SCHRITT 0 — Vorprüfung (nur lesen, nichts ändern). Prüfe und melde als Tabelle „Voraussetzung | Status | Detail“:
1. git: Branch = ccr-c53a7201-5hd57f, Arbeitsbaum sauber, `git log -1`.
2. Android: `java -version`, ANDROID_HOME/ANDROID_SDK_ROOT gesetzt, `./gradlew --version` läuft, `adb devices`
   (Handy oder Emulator vorhanden?).
3. Server-Werkzeuge: `python3 --version` (3.12 empfohlen), `docker --version` und `docker info` (Daemon läuft?).
   Mindestens eines davon muss vorhanden sein. Port 8000 frei? (`lsof -i :8000` oder Entsprechung für das System.)
4. server/.env: existiert sie? Wenn ja, NUR die Schlüsselnamen prüfen (API_KEY, AI_API_KEY, AI_PROVIDER), nie die Werte
   ausgeben. Wenn nein: anlegen (siehe Schritt 2).
5. KI-Schlüssel: ist AI_API_KEY gesetzt (ja/nein)? Ohne Schlüssel laufen KI-Funktionen nicht; markiere sie dann ➖.
   Echte KI-Aufrufe kosten Geld: vor dem ersten Aufruf fragen.
Fehlt etwas Wichtiges: STOPPEN und mir sagen, was fehlt und wie ich es behebe. Nichts selbst installieren oder umkonfigurieren,
ohne zu fragen.

SCHRITT 1 — Automatische Tests (ohne Server, ohne Handy):
- Server: `cd server && python3 -m pytest -q` (erwartet 180 passed). Ohne installierte Pakete: siehe server/CLAUDE.md („Tests").
- App: `./gradlew :app:testDebugUnitTest :app:compileDebugKotlin --console=plain` (erwartet 287 Tests grün).
Melde Zahlen und jeden Fehlschlag mit Ausgabe.

SCHRITT 2 — Lokalen Server starten (Variante A bevorzugt, wenn Docker läuft; sonst Variante B):

Gemeinsam: `server/.env` nur ANLEGEN, wenn sie fehlt: `cp server/.env.example server/.env`. Eine vorhandene .env NIE
überschreiben. Setze in der .env (lokal) API_KEY auf einen frischen Testwert, z. B. `openssl rand -hex 16`, und notiere ihn
mir für die App-Einstellung. AI_API_KEY nur setzen, wenn ich zugestimmt habe. Die Datenbank liegt dann unter
server/data/ (leer, lokal). Keine Kopie der Produktiv-Daten verwenden, außer ich gebe sie dir ausdrücklich.

Variante A — Docker:
  cd server && docker compose up --build -d
  Logs: docker compose logs -f helga-sync
  Stoppen: docker compose down
Variante B — ohne Docker (venv im server/-Ordner):
  cd server && python3 -m venv .venv && . .venv/bin/activate
  pip install -r requirements-dev.txt
  uvicorn app.main:app --host 0.0.0.0 --port 8000
  (Der Host 0.0.0.0 ist nötig, damit ein Handy im WLAN den Server erreicht.)

Gesundheit prüfen: `curl -s http://localhost:8000/api/health` → {"ok":true,…}.

App-Verbindung: Emulator → Server-URL `http://10.0.2.2:8000`; Handy im selben WLAN → `http://<PC-IP>:8000`.
Die App braucht denselben API_KEY wie in server/.env (in den Einstellungen bzw. beim Onboarding eintragen).
Die PC-IP ermittle ich dir (z. B. `ip addr` bzw. `ipconfig`), wenn du sie nicht weißt.

SCHRITT 3 — Lokale Server-Prüfungen (Testplan Abschnitt 1, lokal):
- Health; Auth: ohne Key 401, mit Key 200 (`curl -H "X-Api-Key: <KEY>" http://localhost:8000/api/sync?since=0`).
- Upload: ein kleines Bild und eine 5–8 MB große Datei (`POST /api/images/upload`, multipart Feld `file`). Erwartung: 200 bzw. 413
  bei Überschreitung des Limits.
- URL-Import: `POST /api/ai/import-url` mit öffentlicher Seite → ok; mit `http://192.168.1.1/` und `http://100.64.0.1/`
  → 422 (kein Netzzugriff nötig für die Ablehnung).
- Start ohne API_KEY: in einer Test-Kopie der .env mit leerem API_KEY starten → Log „API_KEY fehlt“ (nicht in der echten .env).
- Gehärteter Lauf (Docker): `docker build --target runtime -t helga-local .` und dann
  `docker run --rm -p 8000:8000 --read-only --tmpfs /tmp --cap-drop ALL --security-opt no-new-privileges -e API_KEY=<KEY> -v "$PWD/data:/app/data" helga-local`
  Erwartung: läuft, Health 200, Upload schreibt nach data/images.
- Test-Stage: `make docker-test` (erwartet 177 passed im Container, Stand Phase 5).
- KI-Pfade (Bon-Scan, Rezept-KI, Gang-Zuordnung per KI) NUR mit meiner Zustimmung und einem AI_API_KEY; sonst ➖.

SCHRITT 4 — Nur gegen das Homelab (read-only, nur wenn ich es ausdrücklich sage): `docker compose ps` (healthy?), Logs,
curl auf /api/health, Rechte des Datenordners, `docker compose config` (überschreibt das Override read_only/tmpfs/Healthcheck?),
ob API_KEY in der Prod-.env gesetzt ist (nur ja/nein). Niemals neu starten, deployen, .env ändern, Dateien löschen oder
`restore_purged_receipts.py --apply` ausführen ohne ausdrückliche Bestätigung im Chat; vorher `make backup` vorschlagen.

SCHRITT 5 — Handy/Emulator (Testplan Abschnitte 2–5):
- App bauen und installieren: `./gradlew installDebug` (Gerät per `adb devices` verbunden).
- Während der Tests `adb logcat` mitlesen; Abstürze und Exceptions dem Testplan-Punkt zuordnen.
- Das Klicken am Gerät kannst du nicht übernehmen: gib mir pro Abschnitt die Schritte, ich melde ✅/❌ mit Kurzbeschreibung.
- Für Upgrade-Test (Testplan 2.1): nur mit einer Kopie der Daten, die ich dir ausdrücklich gebe.

REGELN: Keine Secrets (API_KEY, AI_API_KEY, SSH-Schlüssel) ausgeben, speichern oder committen. Keine Änderungen am Code
im Rahmen des Testens; aus ❌-Ergebnissen entstehen später Fix-Aufträge. Ergebnisse in .claude/phasen/testplan.md
(Spalte „Ergebnis“) eintragen; NICHT committen ohne meine Freigabe. Bei Unklarheit fragen statt raten.
ABSCHLUSS: Liste aller ❌ und ➖ (Testplan-Nummer, Symptom, vermutete Ursache laut Review-Bericht).
```
