# Sync-Patterns & Checklisten

Verbindliche Muster für das LWW-Sync-System der Helga App.
Jede neue Entity oder Feature-Erweiterung muss diese Patterns einhalten.

---

## Entity-Pflichtfelder

Jede syncbare Room-Entity MUSS diese 4 Felder haben:

```kotlin
@Entity(tableName = "my_entities", indices = [
    Index(value = ["updatedAt"]),
    Index(value = ["deleted"]),
])
data class MyEntity(
    @PrimaryKey val id: String,          // UUID, clientseitig generiert
    val updatedAt: Long = 0L,            // Unix-Timestamp ms, bei jeder Änderung auf now()
    val deleted: Int = 0,                // Soft-Delete: 1 = gelöscht
    val dirty: Int = 0,                  // 1 = noch nicht synct
    // ... domänen-spezifische Felder
)
```

**Indices:** `updatedAt` und `deleted` müssen immer indiziert sein (Sync-Query-Performance).

---

## Dirty-Flag-Pflicht

**Regel:** Bei JEDER lokalen Mutation muss `dirty = 1` und `updatedAt = System.currentTimeMillis()` gesetzt werden.

```kotlin
// ✅ RICHTIG – Mutation setzt dirty
suspend fun updateItem(id: String, name: String) {
    val now = System.currentTimeMillis()
    dao.update(id, name = name, updatedAt = now, dirty = 1)
}

// ❌ FALSCH – dirty nicht gesetzt → Änderung wird nie synct
suspend fun updateItem(id: String, name: String) {
    dao.update(id, name = name)
}
```

---

## syncScheduler.triggerOneShot() nach Mutationen

**Regel:** Jede ViewModel-Methode die eine lokale Mutation durchführt, muss danach
`syncScheduler.triggerOneShot()` aufrufen.

```kotlin
// ✅ RICHTIG
fun deleteItem(id: String) {
    viewModelScope.launch {
        repository.deleteItem(id)
        syncScheduler.triggerOneShot()  // Immer danach!
    }
}
```

---

## LWW-Merge-Regel

Die Sync-Engine verwendet Last-Write-Wins:

```
if (serverRecord.updatedAt > localRecord.updatedAt) → Server gewinnt → lokal überschreiben
if (serverRecord.updatedAt <= localRecord.updatedAt) → Client gewinnt → ignorieren
```

Neue Einträge vom Server (keine lokale Version vorhanden): immer übernehmen.

Diese Logik darf **nicht** in ViewModels oder Repositories sein – nur in `SyncEngine`.

---

## Single-Row-Entities (Settings/Constraints)

Für globale Einstellungen die nur einmal existieren (z.B. `WeekplanSettingsEntity`,
`WeekplanConstraintsEntity`):

```kotlin
// Entity
data class WeekplanSettingsEntity(
    @PrimaryKey val id: String = "global",  // Immer "global"
    val planDays: Int = 7,
    // ...
)

// DAO
@Query("SELECT * FROM weekplan_settings WHERE id = 'global' LIMIT 1")
fun observe(): Flow<WeekplanSettingsEntity?>

// Migration – Default-Zeile einfügen
db.execSQL("""
    INSERT OR IGNORE INTO weekplan_settings (id, planDays, updatedAt, deleted, dirty)
    VALUES ('global', 7, 0, 0, 0)
""")
```

`INSERT OR IGNORE` in der Migration stellt sicher dass Upgrade bestehende Daten nicht überschreibt.

---

## Checkliste: Neue syncbare Entity

Beim Hinzufügen einer neuen Entity diese Checkliste vollständig abarbeiten:

### Android-Seite
- [ ] `data/local/entity/MyEntity.kt` — mit id, updatedAt, deleted, dirty + Indices
- [ ] `data/local/dao/MyDao.kt` — observe(), get(), upsert(), dirty(), clearDirty()
- [ ] `AppDatabase.kt` — Entity zu `entities = [...]` hinzufügen, Version +1
- [ ] `AppDatabase.kt` — `MIGRATION_X_Y` implementieren (CREATE TABLE + Indices + ggf. DEFAULT-Row)
- [ ] `AppDatabase.kt` — `MIGRATION_X_Y` zu `addMigrations(...)` hinzufügen
- [ ] `di/DatabaseModule.kt` — `@Provides fun provideMyDao(db: AppDatabase): MyDao`
- [ ] `data/remote/dto/SyncDto.kt` — `MyDto` Klasse mit `@JsonClass(generateAdapter = true)`
- [ ] `data/remote/dto/SyncDto.kt` — Feld zu `SyncPullResponse` hinzufügen
- [ ] `data/remote/dto/SyncDto.kt` — Feld zu `SyncPushRequest` hinzufügen
- [ ] `data/local/dao/SyncDao.kt` — `myTimestamps()` Query hinzufügen
- [ ] `data/sync/SyncEngine.kt` — Mapper-Extensions `MyDto.toEntity()` + `MyEntity.toDto()`
- [ ] `data/sync/SyncEngine.kt` — filterServerWins in `applyServerChanges()`
- [ ] `data/sync/SyncEngine.kt` — dirty() in `buildPushBody()`
- [ ] `data/sync/SyncEngine.kt` — clearDirty() in `clearDirtyFlagsExcept()`

### Server-Seite
- [ ] `server/app/models.py` — `MyRecord` Pydantic-Modell + SyncPayload-Felder
- [ ] `server/app/db.py` — CREATE TABLE in SCHEMA + zu SYNC_TABLES + Indices
- [ ] `server/app/sync.py` — TABLE_COLUMNS + PAYLOAD_FIELD Einträge

### Überprüfung
- [ ] Room-Schema exportiert (schemas/X.json vorhanden)
- [ ] Bidirektionaler Sync-Test: Lokal ändern → Sync → Server prüfen → Vom Server ändern → Sync → App prüfen

---

## Room-Migration-Vorlage

```kotlin
private val MIGRATION_X_Y = object : Migration(X, Y) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS my_entities (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL DEFAULT '',
                updatedAt INTEGER NOT NULL DEFAULT 0,
                deleted INTEGER NOT NULL DEFAULT 0,
                dirty INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_my_entities_updatedAt ON my_entities(updatedAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_my_entities_deleted ON my_entities(deleted)")
        // Für Single-Row-Entities:
        // db.execSQL("INSERT OR IGNORE INTO my_entities (id, ...) VALUES ('global', ...)")
    }
}
```

**Nie `fallbackToDestructiveMigration()` in Produktion.** Jede Schema-Änderung braucht eine explizite Migration.

---

## SyncDto-Vorlage

```kotlin
@JsonClass(generateAdapter = true)
data class MyDto(
    val id: String,
    @Json(name = "updated_at") val updatedAt: Long,
    val deleted: Int = 0,
    val name: String = "",
    // Alle weiteren Felder mit Default-Werten!
)
```

SyncPullResponse und SyncPushRequest:
```kotlin
data class SyncPullResponse(
    // ... bestehende Felder
    @Json(name = "my_entities") val myEntities: List<MyDto> = emptyList(),
)

data class SyncPushRequest(
    // ... bestehende Felder
    @Json(name = "my_entities") val myEntities: List<MyDto> = emptyList(),
)
```

---

## SyncEngine-Mapper-Vorlage

```kotlin
private fun MyDto.toEntity(): MyEntity = MyEntity(
    id = id,
    name = name,
    updatedAt = updatedAt,
    deleted = deleted,
    dirty = 0,  // Frisch vom Server → kein dirty
)

private fun MyEntity.toDto(): MyDto = MyDto(
    id = id,
    name = name,
    updatedAt = updatedAt,
    deleted = deleted,
)
```

---

## Room ist immer Source of Truth

**Regel:** Retrofit-Responses niemals direkt an die UI geben. Immer:
1. Retrofit-Response in Room schreiben
2. UI beobachtet Room via `Flow`

```kotlin
// ✅ RICHTIG – UI beobachtet immer Room
val recipes: StateFlow<List<RecipeEntity>> = recipeDao.observeAll()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

// ❌ FALSCH – Retrofit-Response direkt im ViewModel gehalten
private val _recipes = MutableStateFlow<List<RecipeDto>>(emptyList())
// ... api.getRecipes() → _recipes.value = response
```
