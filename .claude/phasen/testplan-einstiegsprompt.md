# Einstiegsprompt für die lokale Claude-Sitzung (VSCodium)

Branch `ccr-c53a7201-5hd57f` auschecken, Claude-Sitzung im Repo-Ordner starten und den Text unten einfügen.

```text
Du arbeitest lokal in meinem Checkout von HelgaAndroidApp (Branch ccr-c53a7201-5hd57f). Lies zuerst CLAUDE.md und
server/CLAUDE.md. Deine Aufgabe: den Testplan .claude/phasen/testplan.md mit mir abarbeiten. Der Ergebnisbericht
(.claude/phasen/phase-6-daten/review-bericht.md) liefert den Hintergrund zu den Befunden (V…, B…) und zur Abhakliste G01–G29.

SCHRITT 0 — Vorprüfung (nur lesen, nichts ändern). Prüfe und melde als Tabelle „Voraussetzung | Status | Detail“:
1. git: aktueller Branch = ccr-c53a7201-5hd57f, Arbeitsbaum sauber, `git log -1`.
2. Android: `java -version`, ANDROID_HOME/ANDROID_SDK_ROOT gesetzt, `./gradlew --version` läuft, `adb devices` (Handy oder Emulator vorhanden?).
3. Server-Tests lokal: `python3 --version`, pytest und die Pakete aus server/CLAUDE.md („Tests") installierbar/vorhanden.
4. Docker lokal (optional): `docker --version`, Daemon erreichbar.
5. Homelab per SSH (nur `ssh <host> true` und lesende Befehle): Host erreichbar? Welcher Host/Alias (aus ~/.ssh/config, ohne Schlüssel anzuzeigen)?
   Auf dem Server: `docker compose ps` für den Dienst helga-sync, `curl -s http://localhost:8000/api/health`.
6. API_KEY: nur prüfen, OB er in der Server-.env gesetzt ist (nie den Wert ausgeben, nie in Befehle/Logs/Commits schreiben).
Fehlt etwas Wichtiges oder ist etwas nicht erreichbar: STOPPEN und mir genau sagen, was fehlt und wie ich es behebe. Nichts selbst installieren oder umkonfigurieren, ohne zu fragen.

SCHRITT 1 — Automatische Tests (wenn Schritt 0 ok):
- Server: `cd server && python3 -m pytest -q` (erwartet 180 passed).
- App: `./gradlew :app:testDebugUnitTest :app:compileDebugKotlin --console=plain` (erwartet 287 Tests grün).
Melde Zahlen und jeden Fehlschlag mit Ausgabe.

SCHRITT 2 — Testplan abarbeiten, Abschnitt für Abschnitt (0 → 1 → 2 …), jeweils erst sagen, was du tust:
- Abschnitt 1 (Server) per SSH nur mit lesenden/harmlosen Befehlen (ps, logs, curl, Health). Den Server in Produktion NIE
  neu starten, deployen, `.env` ändern, Dateien löschen oder `restore_purged_receipts.py --apply` ausführen, ohne dass ich
  das im Chat ausdrücklich bestätige; vorher `make backup` vorschlagen. Für Punkte, die die Produktion verändern würden
  (falscher AI_API_KEY, Ohne-API_KEY-Test, Neustart), nur die Schritte beschreiben und auf mein Ja warten.
- Abschnitte 2–5 (Handy) kannst du nicht bedienen: Gib mir pro Abschnitt die Schritte zum Durchklicken, ich melde ✅/❌ (mit
  Kurzbeschreibung). Wo ein Emulator/Handy per adb erreichbar ist, darfst du die App installieren (`./gradlew installDebug`)
  und mit `adb logcat` mitlesen (Abstürze, Exceptions), aber nicht für mich klicken.
- Trage Ergebnisse in .claude/phasen/testplan.md ein (Spalte „Ergebnis“) und committe NICHT ohne meine Freigabe.

REGELN: Keine Secrets (API_KEY, AI_API_KEY, SSH-Schlüssel) ausgeben oder speichern. Keine Änderungen am Code im Rahmen des
Testens; aus ❌-Ergebnissen entstehen später Fix-Aufträge, nicht jetzt. Bei Unklarheit fragen statt raten.
ABSCHLUSS: Fasse alle ❌ und ➖ in einer Liste zusammen (Nummer aus dem Testplan, Symptom, vermutete Ursache laut Review-Bericht).
```
