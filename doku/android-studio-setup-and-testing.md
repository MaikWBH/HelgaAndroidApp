# Android Studio Setup & Testing Guide

Detaillierte Anleitung zum Starten der Helga Android-App mit Android Studio und Verbindung zum lokalen Server.

---

## 1. Server-Vorbereitung (Docker)

### 1.1 Docker Container starten

```bash
cd /mnt/games_data/Repo/HelgaAndroidApp/server
docker compose up -d
```

### 1.2 Container-Status prüfen

```bash
docker compose ps
```

Erwartetes Output:
```
NAME          STATUS         PORTS
helga-server  Up ...         0.0.0.0:8000->8000/tcp
```

### 1.3 Server-Logs prüfen

```bash
docker compose logs -f app
```

Erwartete Meldung:
```
Uvicorn running on http://0.0.0.0:8000
```

Keine Fehlermeldungen = ✅ Server läuft korrekt.

### 1.4 Server-Erreichbarkeit testen (vom Host)

```bash
curl -s http://localhost:8000/api/health
```

Falls vorhanden, gibt es eine Health-Check-Response zurück.

### 1.5 Deine Server-IP im Heimnetz ermitteln

```bash
hostname -I
# oder detaillierter:
ip addr | grep "inet " | grep -v 127.0.0.1
```

**Notiere dir deine IP** (z.B. `192.168.1.100`). Du brauchst sie später für die App bei physischen Geräten.

---

## 2. Android Studio Setup

### 2.1 Projekt öffnen

1. Android Studio starten
2. **File** → **Open**
3. Navigiere zu `/mnt/games_data/Repo/HelgaAndroidApp`
4. Klick **Open**

### 2.2 Gradle Sync durchführen

Android Studio sollte automatisch einen Sync vorschlagen. Falls nicht manuell anstoßen:

- **File** → **Sync Now**
- Warte auf die Meldung „Gradle build finished" (ca. 1–2 Minuten beim ersten Mal)

**Falls Fehler auftreten:**

```bash
cd /mnt/games_data/Repo/HelgaAndroidApp
./gradlew clean
./gradlew build
```

### 2.3 local.properties prüfen (optional)

Die Datei wird normalerweise automatisch erzeugt. Falls du sie selbst erstellen musst:

```bash
cd /mnt/games_data/Repo/HelgaAndroidApp
cat > local.properties << 'EOF'
sdk.dir=/path/to/your/Android/sdk
EOF
```

Bei Installation über die Distro ist der SDK normalerweise bereits konfiguriert.

---

## 3. Emulator starten (empfohlen für Anfang)

### 3.1 Android Virtual Device Manager öffnen

In Android Studio:
- **Tools** → **Device Manager** (oder Telefon-Icon oben rechts)

### 3.2 Emulator erstellen oder auswählen

**Falls noch kein Emulator vorhanden:**

1. Klick **Create Device**
2. Wähle **Pixel 7** (oder ein modernes Gerät)
3. Wähle **Android 14** oder höher (API 34+, min SDK ist 26)
4. Klick **Finish**

**Falls Emulator bereits vorhanden:**

- Überspringe diesen Schritt und gehe zu 3.3.

### 3.3 Emulator starten

Im Device Manager:
1. Klick den **Play-Button** neben deinem Emulator
2. Warte ca. 30 Sekunden, bis der Emulator vollständig geladen ist

Der Emulator-Bildschirm sollte sich öffnen und den Android Home-Screen zeigen.

---

## 4. App-Build & Run

### 4.1 Run-Konfiguration vorbereiten (einmalig)

1. Oben in Android Studio: Dropdown neben dem grünen Play-Button
2. Falls nicht vorhanden: Klick auf **Edit Configurations**
3. Klick **+** → **Android App**
4. Name: z.B. `app`
5. Module: `app`
6. Klick **OK**

### 4.2 App starten

1. Stelle sicher, dass dein Emulator läuft (aus Schritt 3.3)
2. Stelle sicher, dass die Run-Konfiguration `app` selected ist
3. Klick den grünen **Play-Button** (oder `Shift+F10`)
4. Warte, bis der Build fertig ist (ca. 1–2 Minuten beim ersten Mal)

Die App sollte im Emulator starten und den **Onboarding-Screen** anzeigen.

---

## 5. Erste App-Tests: Onboarding & Server-Verbindung

### 5.1 Onboarding-Screen

Die App zeigt einen Willkommens-Screen. Folge den Anweisungen auf dem Bildschirm (z.B. Klick **Weiter** oder **Fortfahren**).

### 5.2 Server-URL eingeben

Der nächste Screen fragt nach der **Server-URL** und optional einem **API-Key**.

**Unterschiedlich je nach Szenario:**

#### Szenario A: Emulator auf dem gleichen Host

```
http://10.0.2.2:8000
```

Das ist die Standard-IP des Host-Systems, wenn du vom Android-Emulator aus darauf zugreifst.

#### Szenario B: Physisches Android-Gerät im Heimnetz

```
http://192.168.1.100:8000
```

Ersetze `192.168.1.100` durch deine Server-IP aus Schritt 1.5.

**Eingabe in der App:**

1. Gib die URL ein (z.B. `http://10.0.2.2:8000`)
2. Gib deinen API-Key ein (falls konfiguriert; sonst leer lassen oder Dummy-Wert)
3. Klick **Speichern** oder **Verbinden**

### 5.3 Rezeptliste anschauen

Nach erfolgreicher Verbindung solltest du die **Recipe List Screen** sehen:

- **Leer:** Falls noch keine Rezepte in der Datenbank vorhanden sind, zeigt die App „Noch keine Rezepte"
- **Mit Rezepten:** Falls der Server bereits Rezepte enthält, sollten diese aufgelistet sein

**Oben rechts:** Der **Sync-Status-Icon**
- ✅ Grüner Check = zuletzt erfolgreich synced
- ⏳ Spinner/Grau = Syncing läuft gerade

Klick darauf, um einen **manuellen Sync** zu triggern.

---

## 6. Netzwerk-Debugging & Fehlerbehandlung

### 6.1 Test der Emulator-Netzwerk-Verbindung

**Via Emulator-Shell:**

```bash
adb shell ping 10.0.2.2
```

Sollte Responses zeigen (keine Timeouts).

```bash
adb shell curl -v http://10.0.2.2:8000/api/health
```

Sollte HTTP 200 oder ähnlich zurückgeben.

### 6.2 Problem: Emulator kann Server nicht erreichen

**Checkliste:**

1. **Server läuft noch?**
   ```bash
   docker compose ps
   # oder
   docker compose logs app | tail -20
   ```

2. **Firewall blockiert Port 8000?**
   ```bash
   sudo ufw allow 8000
   # oder Status prüfen:
   sudo ufw status
   ```

3. **Falsche URL in der App?**
   - Emulator: immer `http://10.0.2.2:8000`
   - Physisches Gerät: `http://192.168.x.x:8000` (deine Heimnetz-IP)

### 6.3 Problem: "Server-URL ungültig" oder "Verbindung fehlgeschlagen"

Schaue in die **Logcat**-Ausgabe (siehe Schritt 6.5).

### 6.4 Problem: Netzwerk im Emulator ist aus

Emulator-Fenster:
1. Klick **More** (drei Punkte oben rechts)
2. Wähle **Settings**
3. Tab **Network**
4. Stelle sicher, dass Netzwerk **eingeschaltet** ist

### 6.5 Logcat für Debugging nutzen

Im Android Studio **Logcat**-Tab (unten):

1. Wähle dein Gerät aus dem Dropdown
2. Wähle **Verbose** oder **Debug** für ausführlichere Logs
3. Nutze Filter, um nur bestimmte Logs zu sehen:

```
# Nur Helga-Logs
com.helga.android

# Nur Netzwerk (Retrofit/OkHttp)
Retrofit
OkHttp

# Nur Datenbank (Room)
database

# Nur Fehler
Error
```

**Beispiel-Fehler:**
```
E/Retrofit: failed to connect to 10.0.2.2/10.0.2.2:8000
```

→ Server läuft nicht oder falsche IP eingegeben.

---

## 7. App-Features testen (Phase 3)

### 7.1 Rezeptliste anschauen

- ✅ Klick **Refresh-Icon** (Sync-Status) oben rechts
- ✅ Warte auf Sync (Spinner sollte drehen)
- ✅ Sollte „Noch keine Rezepte" anzeigen (oder Rezepte vom Server, falls vorhanden)

### 7.2 Filter & Sortierung testen

- ✅ Klick auf die **FilterChips** oben (z.B. „Alle", „Vegetarisch")
- ✅ Rezeptliste sollte sich entsprechend filtern
- ✅ Klick auf **Sort-Button** (Icon mit drei Linien) → Dropdown öffnet sich
- ✅ Wähle „Name", „Bewertung" oder „Zuletzt geändert"
- ✅ Rezeptliste sollte sich neu sortieren

### 7.3 Recipe Detail Screen testen (falls Rezepte vorhanden)

Falls der Server bereits Rezepte enthält:

1. Klick auf eine **Rezeptkarte** in der Liste
2. **Detail-Screen** sollte öffnen mit:
   - 📷 Bild oben (mit Animations-Übergang vom List-Image)
   - ⭐ Sterne-Bewertung (1–5 Sterne zum Klicken)
   - 🥕 Zutaten-Liste
   - 👨‍🍳 Zubereitung (Schritte)
   - 🏷️ Tags
   - ℹ️ Metadaten (Kochzeit, Garzeit, Küche, etc.)

### 7.4 Bewertung ändern testen

1. Im **Detail-Screen:** Klick auf Sterne (z.B. 4 von 5)
2. Sterne-Anzeige sollte sich sofort ändern
3. Klick **Zurück** (oder Back-Button)
4. Zurück in der Liste sollten die **aktualisierten Sterne** sichtbar sein (sofern Sync erfolgreich war)

---

## 8. Offline-Modus testen (optional)

### 8.1 Netzwerk im Emulator deaktivieren

1. Im Emulator-Fenster: **More** (drei Punkte) → **Settings**
2. Tab **Network**
3. **Schalte Netzwerk aus**

### 8.2 App-Verhalten testen

- ✅ App sollte weiterhin funktionieren (nur ohne Sync)
- ✅ Rezepte sind noch sichtbar (aus lokalem Cache)
- ✅ Änderungen sind möglich (werden lokal gespeichert)

### 8.3 Netzwerk wieder einschalten

1. Emulator-Einstellungen → **Network**
2. **Netzwerk an**
3. ✅ Sync sollte automatisch triggern
4. ✅ Lokale Änderungen sollten mit Server synchronisiert werden

---

## 9. APK-Export für echtes Gerät (optional)

Falls du die App auf einem echten Android-Handy testen möchtest:

### 9.1 APK bauen

1. **Build** → **Build Bundle(s) / APK(s)** → **Build APK(s)**
2. Warte auf Completion
3. Im Popup: Klick **locate** um zum Build-Ordner zu navigieren

### 9.2 APK auf Gerät installieren

```bash
# Gerät über USB verbinden und USB-Debugging aktivieren

# APK via adb installieren:
adb install -r path/to/app-debug.apk
```

---

## 10. Schnell-Checkliste

- [ ] Docker-Server läuft: `docker compose ps` im `server/`-Verzeichnis
- [ ] Server erreichbar: `curl http://localhost:8000/api/health`
- [ ] Projekt in Android Studio geöffnet
- [ ] Gradle Sync erfolgreich
- [ ] Emulator oder Gerät verbunden
- [ ] App gestartet (grüner Play-Button)
- [ ] Onboarding durchlaufen
- [ ] Server-URL eingegeben (`http://10.0.2.2:8000` für Emulator, `http://192.168.x.x:8000` für physisches Gerät)
- [ ] Sync-Button getestet und erfolgreich
- [ ] Filter/Sortierung getestet
- [ ] Detail-Screen getestet (falls Rezepte vorhanden)
- [ ] Bewertung geändert und verifiziert
- [ ] Offline-Modus getestet (optional)

---

## 11. Häufig gestellte Fragen

### Q: Die App zeigt immer "Noch keine Rezepte", obwohl der Server läuft

**A:** Das ist normal! Der Server ist zu Anfang leer. Du kannst:
- Manuell Rezepte via REST-API in die Server-DB eingeben
- Oder warte auf Phase 4 (Rezept-Erstellung in der App)

### Q: Emulator ist sehr langsam

**A:** Das ist normal. Tipps zur Beschleunigung:
- Hardware-Beschleunigung aktivieren (Settings → Performance)
- Weniger RAM dem Emulator zuweisen (aber mindestens 2 GB)
- Schnellere CPU nutzen

### Q: App stürzt beim Starten ab

**A:** Schaue in den Logcat-Logs nach. Häufige Gründe:
- Datenbankmigrationen fehlgeschlagen
- Fehler bei Hilt-Injection
- Server-Verbindung fehlgeschlagen

---

## 12. Nächste Schritte

Nach erfolgreicher Phase 3:

- **Phase 4:** Rezept-Verwaltung (Erstellen, Bearbeiten, Löschen)
- **Phase 5:** KI-Rezept-Erstellung (SSE-Streaming)
- **Phase 6:** Wochenplanung (Planung + Auto-Planung)

Siehe `.claude/development_plan.md` für vollständigen Plan.
