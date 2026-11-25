# Einstiegsprompt für die lokale Claude-Sitzung (VSCodium)

Branch `ccr-c53a7201-5hd57f` auschecken, Claude-Sitzung im Repo-Ordner starten und den Text unten einfügen.

```text
Du arbeitest lokal in meinem Checkout von HelgaAndroidApp (Branch ccr-c53a7201-5hd57f). Lies zuerst CLAUDE.md und
server/CLAUDE.md. Deine Aufgabe: den Testplan .claude/phasen/testplan.md mit mir abarbeiten. Der Ergebnisbericht
(.claude/phasen/phase-6-daten/review-bericht.md) liefert den Hintergrund zu den Befunden (V…, B…) und zur Abhakliste G01–G29.

GRUNDSATZ: Alles, was geht, wird gegen einen LOKALEN Server auf meinem PC getestet (Docker oder uvicorn), nicht gegen mein
Produktiv-Homelab. Das Homelab wird nur für die wenigen Punkte genutzt, die es wirklich brauchen (siehe Schritt 3) und dann
nur lesend.

SCHRITT 0 — Vorprüfung (nur lesen, nichts ändern). Prüfe und melde als Tabelle „Voraussetzung | Status | Detail“:
1. git: aktueller Branch = ccr-c53a7201-5hd57f, Arbeitsbaum sauber, `git log -1`.
2. Android: `java -version`, ANDROID_HOME/ANDROID_SDK_ROOT gesetzt, `./gradlew --version` läuft, `adb devices` (Handy oder Emulator vorhanden?).
3. Server lokal: `python3 --version`, pytest und Pakete aus server/CLAUDE.md („Tests"); Docker (`docker --version`, Daemon erreichbar)
   für `docker compose` bzw. `make dev`; ist Port 8000 frei?
4. KI-Schlüssel: gibt es einen eigenen AI_API_KEY für lokale Tests (nur prüfen, ob eine Variable/Datei vorhanden ist, nie den
   Wert ausgeben)? Ohne Schlüssel laufen KI-Funktionen (Bon-Scan, Rezept-KI, Auto-Gang per KI) lokal nicht; das markierst du
   dann ➖ statt zu raten. Beachte: Echte KI-Aufrufe kosten Geld, frage vor dem ersten.
5. Homelab per SSH (optional, nur für Schritt 3): `ssh <host> true`, Host/Alias aus ~/.ssh/config (ohne Schlüssel anzuzeigen).
Fehlt etwas Wichtiges oder ist etwas nicht erreichbar: STOPPEN und mir genau sagen, was fehlt und wie ich es behebe. Nichts selbst
installieren oder umkonfigurieren, ohne zu fragen.

SCHRITT 1 — Automatische Tests (wenn Schritt 0 ok):
- Server: `cd server && python3 -m pytest -q` (erwartet 180 passed).
- App: `./gradlew :app:testDebugUnitTest :app:compileDebugKotlin --console=plain` (erwartet 287 Tests grün).
Melde Zahlen und jeden Fehlschlag mit Ausgabe.

SCHRITT 2 — Lokaler Server und App-Tests:
- Server lokal starten: `cd server && cp .env.example .env`, API_KEY auf einen frischen Test-Wert setzen (nicht den Produktiv-Key),
  AI_API_KEY nur wenn ich zugestimmt habe; `make dev` (oder docker compose up --build). Ich nutze nur lokale Daten (leere DB in
  server/data). Für den Upgrade-/Migrationstest kann ich dir eine Kopie meiner Daten bereitstellen: nur auf ausdrückliche Ansage,
  nie direkt das Produktiv-Datenverzeichnis verwenden.
- Server-Prüfungen aus Testplan-Abschnitt 1 lokal: Health, Auth (401 ohne Key), Upload (klein und 5–8 MB), Rezept-Import
  (öffentliche Seite ok; 192.168.x.x und 100.64.x.x abgelehnt), Fehlerverhalten mit absichtlich falschem AI_API_KEY (lokal
  unproblematisch), Start ohne API_KEY (Log „API_KEY fehlt", Container unhealthy), gehärteter Lauf mit
  `docker run --read-only --tmpfs /tmp --cap-drop ALL --security-opt no-new-privileges`, optional `make docker-test`.
- Handy/Emulator: Server-URL in der App auf `http://10.0.2.2:8000` (Emulator) bzw. `http://<PC-IP>:8000` (Handy im selben WLAN).
  Mit `./gradlew installDebug` installieren und per `adb logcat` mitlesen (Abstürze, Exceptions). Die Abschnitte 2–5 des
  Testplans (Klicken) kannst du nicht bedienen: gib mir pro Abschnitt die Schritte, ich melde ✅/❌ mit Kurzbeschreibung.
  Wenn Logcat dabei Fehler zeigt, ordne sie dem Testplan-Punkt zu.

SCHRITT 3 — NUR gegen das Homelab (read-only, nur wenn ich es ausdrücklich sage): `docker compose ps` (healthy?), Container-Logs
lesen, `curl` auf /api/health, UID/Rechte des Datenordners ansehen, prüfen ob das Override read_only/tmpfs/Healthcheck
überschreibt (`docker compose config`), Prod-.env: nur OB API_KEY gesetzt ist. Niemals neu starten, deployen, .env ändern,
Dateien löschen oder `restore_purged_receipts.py --apply` ausführen, ohne dass ich das im Chat ausdrücklich bestätige; vorher
`make backup` vorschlagen.

REGELN: Keine Secrets (API_KEY, AI_API_KEY, SSH-Schlüssel) ausgeben oder speichern, nichts davon committen. Keine Änderungen am
Code im Rahmen des Testens; aus ❌-Ergebnissen entstehen später Fix-Aufträge. Trage Ergebnisse in
.claude/phasen/testplan.md (Spalte „Ergebnis") ein und committe NICHT ohne meine Freigabe. Bei Unklarheit fragen statt raten.
ABSCHLUSS: Fasse alle ❌ und ➖ in einer Liste zusammen (Nummer aus dem Testplan, Symptom, vermutete Ursache laut Review-Bericht).
```
