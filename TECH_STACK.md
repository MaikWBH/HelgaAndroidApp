# Tech-Stack — Helga Android

Stand: 2026-08-22 · Quellen: `gradle/libs.versions.toml`, `app/build.gradle.kts`,
`server/requirements.txt`, `server/Dockerfile`, `.github/workflows/android-ci.yml`

---

## Android-App (`app/`)

### Sprache & Build

| | |
|---|---|
| Kotlin | 2.1.0 |
| Android Gradle Plugin | 8.7.3 |
| KSP | 2.1.0-1.0.29 |
| JDK | 17 |
| minSdk / target- & compileSdk | 26 / 35 |

### UI

| | |
|---|---|
| Jetpack Compose | BOM 2024.10.01 |
| Material3 | (Teil der Compose-BOM) |
| Navigation Compose | 2.8.4 |
| Glance (Home-Screen-Widget) | 1.1.1 |
| Wear Compose + Horologist (Wear OS) | 1.2.0 / 0.5.16 |

### Architektur

- MVVM + Repository-Pattern, Room als Single Source of Truth
- Hilt 2.56 (Dependency Injection)

### Daten

| | |
|---|---|
| Room | 2.7.1 |
| DataStore Preferences | 1.1.1 |

### Netzwerk

| | |
|---|---|
| Retrofit | 2.11.0 |
| OkHttp | 4.12.0 |
| Moshi (Kotlin-Codegen) | 1.15.1 |

### Hintergrundarbeit & Medien

| | |
|---|---|
| WorkManager | 2.9.1 |
| Coil (Bilder) | 2.7.0 |
| CameraX | 1.3.4 |
| ML Kit Barcode Scanning | 17.3.0 |
| ML Kit Text Recognition | 16.0.0 |

### Async & Diagnose

| | |
|---|---|
| Kotlin Coroutines | 1.9.0 |
| Timber (Logging) | 5.0.1 |
| LeakCanary (nur Debug) | 2.14 |

### Tests

| | |
|---|---|
| JUnit4 | 4.13.2 |
| MockK | 1.13.12 |
| Turbine | 1.1.0 |
| Macrobenchmark (Baseline Profile) | 1.3.3 |

### Build-Konfiguration

- R8 Full Mode + Resource Shrinking im Release
- `nonTransitiveRClass=true`
- Gradle Configuration Cache + Parallel Build
- `versionCode` aus Git-Commit-Count (`gitCommitCount()` in `app/build.gradle.kts`)
- Fester Debug-Keystore für konsistente APK-Signatur

---

## Sync-Backend (`server/`)

| | |
|---|---|
| Python | 3.12-slim (Docker) |
| FastAPI | 0.115.5 |
| Uvicorn | 0.32.1 |
| aiosqlite | 0.20.0 |
| httpx | 0.28.1 |
| python-multipart | 0.0.20 |
| python-dotenv | 1.0.1 |
| recipe-scrapers | ≥14.0.0 |

**KI-Provider** (umschaltbar über `AI_PROVIDER` in `server/app/ai.py`):

| Anbieter | Standard | Stark |
|---|---|---|
| OpenAI | gpt-4o-mini | gpt-4o |
| Anthropic | claude-haiku-4-5 | claude-sonnet-4-6 |

**Container:** non-root User, Healthcheck gegen `/api/health`.

---

## Deployment & CI

- **Docker:** lokal `server/docker-compose.yml`, Produktion `server/docker-compose.prod.yml`
  auf CasaOS (siehe `doku/casaos-deployment.md`)
- **CI** (`.github/workflows/android-ci.yml`): Lint → Unit-Tests → Debug-APK-Build →
  Artefakt-Upload, bei jedem Push

---

## Datenbank-Stand

Room-Schema Version 30 (~25 Migrationen), 25 Entities.

---

Architektur, Konventionen und Sync-Protokoll: siehe [`CLAUDE.md`](CLAUDE.md).
Funktionaler Ist-Stand je Feature-Bereich: siehe [`.claude/features/README.md`](.claude/features/README.md).
