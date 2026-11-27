# Architektur-Schichten (Helga Android)

Verbindlich für alle `ui/`-ViewModels. Entstanden in Phase 1 (android-clean-architecture).

## Erlaubte Abhängigkeiten

```
ui/ (Compose-Screens, *ViewModel)
  └─► data/repository/  (Repositories, UseCases in data/usecase/, Dienste in data/weekplan/ u. a.)
        └─► data/local/dao/  (Room)     |   data/remote/ (SyncApi, SyncApiFactory via ApiProvider)
              data/receipts/  (Dienste für das Bon-Archiv: ReceiptSyncer, ReceiptUploader, ReceiptImageStore, ReceiptScanFlow)
              data/shopping/  (Dienst der Gang-Zuordnung: AisleAssigner)
```

- **ViewModels** sprechen nur mit Repositories und UseCases. Sie lesen Daten aus Room (Single Source of Truth),
  nie direkt aus Retrofit-Antworten.
- **Verboten in `ui/**/*ViewModel.kt`:** `com.helga.android.data.local.dao.*`, `SyncApi`, `SyncApiFactory`,
  `ApiProvider`, `retrofit2.*`. Geprüft durch `app/src/test/.../ArchitectureRulesTest.kt`.
- **Ausnahme:** `data.remote.SseClient` (SSE-Streaming) darf in `AiGenerateViewModel` und `AiRemixViewModel`
  verwendet werden. Entscheidung Stufe B; im Test als Kommentar dokumentiert. Neue Ausnahmen nur mit Entscheidung hier.
- **`data/receipts/`** ist das Dienst-Paket des Bon-Archivs. Dienste mit Netz oder Room (`ReceiptSyncer`,
  `ReceiptUploader`, `ReceiptImageStore`, `ReceiptScanFlow`) nutzen nur Repositories und Worker.
  **Reine Hilfsobjekte ohne Room/Netz** (`ReceiptStats`, `ReceiptReviewCheck`, `ReceiptDates`, `ReceiptScanOutcome`)
  dürfen ViewModels und Screens direkt verwenden (Entscheidung Phase 6). Regeln: [sync-patterns](sync-patterns.md),
  Abschnitt „Server-Archiv".
- **`data/shopping/`** (`AisleAssigner`) wird nur von `ShoppingRepository` und `AisleCategorizeWorker` genutzt.
- **Datenschicht (`data/`) importiert nie aus `ui/`.** Gemeinsame Wochenplan-Logik liegt in `data/weekplan/`.
  Nur KDoc-Verweise auf `ui/`-Klassen sind erlaubt.

## Hilt

- Netzwerkzugriff über das Interface `ApiProvider` (`data/remote/SyncApiFactory.kt`, gebunden in `di/NetworkModule.kt`).
  Repositories und Dienste injizieren `ApiProvider`, nicht `SyncApiFactory`.
- Repositories sind `@Singleton`; ViewModels sind `@HiltViewModel` und nehmen Repositories als Konstruktorargumente.

## Schreiben

- Jede Mutation im Repository setzt `updatedAt` und `dirty = 1` (siehe [sync-patterns](sync-patterns.md)).
- Nach einer Mutation im ViewModel `syncScheduler.triggerOneShot()` aufrufen.

## Prüfung

- `./gradlew :app:testDebugUnitTest` schlägt fehl, wenn ein ViewModel eine verbotene Abhängigkeit importiert.
