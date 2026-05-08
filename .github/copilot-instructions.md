# copilot-instructions.md — Helga Android

## Projektziel

Offline-First Android-App für Rezeptverwaltung, KI-Rezepterstellung, Wochenplanung und Einkaufsliste. Die App ist vollständig ohne Internetverbindung nutzbar – alle Daten liegen lokal auf dem Gerät (Room/SQLite). Ein schlankes Sync-Backend (FastAPI, `server/`) liegt auf dem Homeserver und dient ausschließlich als zentrales Datenlager. Die App synct bidirektional sobald der Server erreichbar ist. **Langfristig ersetzt dieses Setup die Flask-Helga-App vollständig.**

KI-Funktionen laufen ausschließlich auf dem Sync-Server; die App sendet Prompts und empfängt Ergebnisse via Streaming (SSE).

**Feature-Referenz:** `.github/helga_features.md`  
**Entwicklungsplan:** `.github/development_plan.md`

---

## Repo-Struktur

```
HelgaAndroidApp/
├── app/                    # Android-App (Kotlin, Compose) – ab Phase 1
├── server/                 # FastAPI-Sync-Backend (Phase 0 fertig)
│   ├── app/                # Python-Module (main, db, sync, ai, models)
│   ├── scripts/            # migrate_from_helga.py
│   ├── Dockerfile
│   ├── docker-compose.yml          # Lokale Entwicklung
│   ├── docker-compose.prod.yml     # Produktion (CasaOS)
│   ├── Makefile                    # deploy / logs / backup / migrate
│   └── .env.example
└── doku/
    └── casaos-deployment.md        # Deployment-Anleitung für CasaOS
```

---

## Stack

| Bereich | Entscheidung |
|---------|--------------|
| Sprache | Kotlin |
| UI | Jetpack Compose |
| Architektur | MVVM + Repository Pattern (Room als Single Source of Truth) |
| Navigation | Navigation Compose (Single-Activity) |
| Lokale DB | Room (SQLite) |
| Sync / Netzwerk | Retrofit + OkHttp + Moshi |
| Hintergrund-Sync | WorkManager |
| Bilder | Coil (lokaler Cache + Server-Fallback) |
| DI | Hilt |
| State | `StateFlow` / `ViewModel` |
| Async-State | `DataStore<Preferences>` (Sync-Metadaten, Server-URL) |
| Min SDK | 26 (Android 8.0) |

---

## Architektur

```
app/
├── ui/
│   ├── recipes/        # Liste, Detail, Erstellen, Bearbeiten, Kochansicht
│   ├── ai/             # KI-Erstellung, Remix, Vorschau (SSE-Streaming)
│   ├── weekplan/       # Wochenplanung (manuell + KI)
│   ├── shopping/       # Einkaufsliste, Märkte, Gänge
│   └── settings/       # Server-URL, KI-Modell, Wochenplan-Einstellungen
├── data/
│   ├── local/          # Room-Entities, DAOs, AppDatabase
│   ├── remote/         # Retrofit-Interfaces + Sync-DTOs
│   ├── sync/           # SyncEngine, SyncWorker (WorkManager)
│   └── repository/     # Repositories (lesen aus Room, schreiben in Room + dirty-Flag setzen)
└── di/                 # Hilt-Module
```

### Datenfluss

```
UI → ViewModel → Repository
                    ├── liest immer aus Room (offline-fähig)
                    └── schreibt in Room + setzt updated_at / dirty = true

SyncWorker (WorkManager, periodisch + bei Connectivity-Change)
    ├── GET /sync?since=<last_sync_ts>  → Server-Änderungen in Room mergen (LWW)
    └── POST /sync  { lokale dirty-Records }  → Server übernimmt, dirty = false
```

---

## Sync-Protokoll

**Strategie:** Timestamp-basiert, Last-Write-Wins (LWW)

Jede Entity besitzt:
- `id` — UUID, clientseitig generiert
- `updatedAt` — Unix-Timestamp (ms), bei jeder lokalen Änderung auf `now()` gesetzt
- `deleted` — Soft-Delete-Flag (echtes Löschen erst nach erfolgreichem Sync)

**Sync-Ablauf:**
1. `GET /api/sync?since=<lastSyncTs>` → Server gibt alle Datensätze zurück, die nach `lastSyncTs` geändert wurden
2. App merged per LWW: `if (serverRecord.updatedAt > localRecord.updatedAt) → überschreibe lokal`
3. `POST /api/sync` mit allen lokalen Records wo `dirty = true`
4. Server merged per LWW, gibt Konflikte (falls `updatedAt` serverseitig neuer) zurück
5. App speichert neuen `lastSyncTs`, setzt `dirty = false` auf erfolgreich gesyncten Records

**Trigger:** Connectivity-Change (NetworkCallback), App-Foreground, manuell (Pull-to-Refresh in Settings).

---

## Sync-Backend (`server/`)

Docker-Container auf dem Homeserver, Code liegt in `server/`.

| Eigenschaft | Wert |
|-------------|------|
| Framework | FastAPI (Python) |
| Datenbank | SQLite (identisches Schema wie Room) |
| Auth | Shared Secret (Header `X-Api-Key`) im lokalen Netz |
| KI | OpenAI / Anthropic (wie bisher in Helga) |
| Migration | Einmalig: Daten aus Helga-SQLite importieren |
| Deployment | Docker, Port 8000 |

Die Server-URL wird beim ersten App-Start eingegeben und in `DataStore` gespeichert. Emulator-Default: `http://10.0.2.2:8000`.

---

## Konventionen

- **UI-Sprache:** Deutsch – alle Strings in `strings.xml` auf Deutsch
- **Benennung:** Kotlin-Standard (camelCase, PascalCase für Klassen)
- **Single Source of Truth:** ViewModels lesen **immer** aus Room, nie direkt aus Retrofit-Responses
- **Fehlerbehandlung:** `sealed class UiState<T>` in ViewModels; Netzwerkfehler sind nicht-fatal (Snackbar), Room-Fehler sind fatal (Dialog)
- **Soft-Delete:** Nie `DELETE` in Room ohne `deleted = true` + Sync-Bestätigung
- **IDs:** Clientseitig als UUID generieren – kein Server-Roundtrip zum Anlegen nötig
- **Bilder:** Erst lokal im App-Cache speichern, nach Sync zum Server hochladen; Coil liest zuerst aus lokalem Cache
- **Streaming (SSE):** KI-Generierung und Mealie-Import via OkHttp `EventSource`

---

## Entwicklungs-Workflow

```bash
# Sync-Backend lokal starten
cd server && docker compose up -d

# Android-Emulator: Server-URL = http://10.0.2.2:8000
# Physisches Gerät im Heimnetz: Server-URL = http://192.168.x.x:8000

# Helga Flask-App (nur während Migration aktiv, danach abschaltbar)
cd ../Helga && docker compose up -d
```

**CasaOS-Deployment:** Siehe [doku/casaos-deployment.md](doku/casaos-deployment.md)

Vor jedem neuen Feature: prüfen ob Room-Schema + Sync-DTO vollständig sind. Schema-Änderungen erfordern Room-Migration (`@Migration`).
