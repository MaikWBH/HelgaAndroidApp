# Kotlin Code Quality Guidelines

Projekt-spezifische Kotlin-Qualitätsregeln für die Helga Android App.
Ergänzt die Kotlin Style Guide – fokussiert auf Muster die in diesem Projekt besonders relevant sind.

---

## 1. Kein `!!`-Operator

**Regel:** Not-null assertion (`!!`) ist verboten. Immer explizites Null-Handling.

```kotlin
// ✅ RICHTIG – requireNotNull mit aussagekräftiger Nachricht
val recipeId: String = requireNotNull(savedStateHandle["recipeId"]) {
    "RecipeDetailViewModel ohne recipeId gestartet"
}

// ✅ RICHTIG – ?.let für optionale Ausführung
recipe?.let { showRecipe(it) }

// ✅ RICHTIG – Elvis-Operator mit sinnvollem Fallback
val name = recipe?.name ?: stringResource(R.string.unknown_recipe)

// ❌ FALSCH – !! wirft NullPointerException ohne Kontext
val recipeId = savedStateHandle.get<String>("recipeId")!!
```

---

## 2. `sealed interface` für UiState und Ergebnis-Typen

**Regel:** `sealed interface` statt `sealed class` für Status-Hierarchien.
Interfaces können von mehreren Klassen implementiert werden und erzwingen keine Vererbung.

```kotlin
// ✅ RICHTIG
sealed interface AiGenerateStatus {
    data object Idle : AiGenerateStatus
    data object Loading : AiGenerateStatus
    data class Success(val recipe: ImportedRecipeDto) : AiGenerateStatus
    data class Error(val message: String) : AiGenerateStatus
}

// ❌ EHER VERMEIDEN – sealed class erzwingt Vererbung, `object` braucht keine Klasse
sealed class AiGenerateStatus {
    object Idle : AiGenerateStatus()
    object Loading : AiGenerateStatus()
    data class Success(...) : AiGenerateStatus()
}
```

`data object` statt `object` für Singleton-States (korrekte `toString()`, `equals()`, `hashCode()`).

---

## 3. StateFlow-Kapselung in ViewModels

**Regel:** `MutableStateFlow` ist immer `private`. Nach außen nur `StateFlow` (read-only).

```kotlin
// ✅ RICHTIG
class RecipeListViewModel : ViewModel() {
    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()

    fun selectTag(tag: String?) { _selectedTag.value = tag }
}

// ❌ FALSCH – externe Klassen können State direkt überschreiben
class RecipeListViewModel : ViewModel() {
    val selectedTag = MutableStateFlow<String?>(null)
}
```

---

## 4. Flow aus Repositories – nie suspend für beobachtbare Daten

**Regel:** Repositories geben `Flow<T>` zurück für Daten die sich ändern können.
`suspend fun` nur für einmalige Operationen (Delete, Insert, Update).

```kotlin
// ✅ RICHTIG – Recipes werden beobachtet
interface RecipeRepository {
    fun observeAll(): Flow<List<RecipeEntity>>          // beobachtbar
    fun observeById(id: String): Flow<RecipeEntity?>    // beobachtbar
    suspend fun save(recipe: RecipeEntity)               // Einmal-Op
    suspend fun delete(id: String)                       // Einmal-Op
}

// ❌ FALSCH – suspend gibt Snapshot zurück, keine Live-Updates
suspend fun getRecipes(): List<RecipeEntity>  // beobachtbare Daten müssen Flow sein
```

---

## 5. `@Transaction` für multi-tabellen Operationen

**Regel:** Alle Datenbankoperationen die mehrere Tabellen berühren, müssen mit
`@Transaction` in einem atomaren Block ausgeführt werden.

```kotlin
// ✅ RICHTIG – Recipe + Ingredients + Instructions atomar
@Dao
interface RecipeDao {
    @Transaction
    suspend fun saveRecipeWithRelations(
        recipe: RecipeEntity,
        ingredients: List<IngredientEntity>,
        instructions: List<InstructionEntity>,
    )
}

// ✅ RICHTIG – auch in Repository über database.withTransaction { }
suspend fun saveRecipe(recipe: RecipeEntity, ...) {
    database.withTransaction {
        recipeDao.upsert(recipe)
        recipeDao.upsertIngredients(ingredients)
        recipeDao.upsertInstructions(instructions)
    }
}
```

---

## 6. CoroutineScope-Regeln

**Regel:** Kein `GlobalScope`. Immer `viewModelScope` in ViewModels oder
einen per DI injizierten `CoroutineScope` in Repositories/Engines.

```kotlin
// ✅ RICHTIG – viewModelScope ist an ViewModel-Lifecycle gebunden
class RecipeDetailViewModel : ViewModel() {
    fun deleteRecipe(id: String) {
        viewModelScope.launch {
            repository.delete(id)
        }
    }
}

// ✅ RICHTIG – injizierter Scope in @Singleton Klassen
@Singleton
class SyncEngine @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
) { ... }

// ❌ FALSCH – GlobalScope ignoriert Lifecycle, kann zu Leaks führen
GlobalScope.launch { repository.delete(id) }
```

WorkManager-Workers: `CoroutineWorker` nutzen (bereits im Projekt so implementiert).

---

## 7. Kein `lateinit` außer Hilt-Injection

**Regel:** `lateinit var` nur für `@Inject`-Felder in Android-Komponenten (Activity,
Fragment, Application). In allen anderen Klassen `val` mit direkter Initialisierung.

```kotlin
// ✅ RICHTIG – Hilt-Injection in Application-Klasse
@HiltAndroidApp
class HelgaApp : Application() {
    @Inject lateinit var workerFactory: HiltWorkerFactory
}

// ✅ RICHTIG – stattdessen Konstruktor-Injektion
@HiltViewModel
class RecipeListViewModel @Inject constructor(
    private val repository: RecipeRepository,  // kein lateinit nötig
) : ViewModel()

// ❌ FALSCH – lateinit in ViewModel ohne Hilt-Notwendigkeit
class RecipeListViewModel : ViewModel() {
    lateinit var repository: RecipeRepository  // Warum nicht Konstruktor-Injektion?
}
```

---

## 8. Extension Functions: nur für genuinen Receiver-Kontext

**Regel:** Extension Functions nur schreiben wenn die Funktion semantisch zum Receiver-Typ
gehört. Utility-Buckets als Objekte oder Top-Level-Funktionen.

```kotlin
// ✅ RICHTIG – gehört semantisch zu String
fun String.isValidServerUrl(): Boolean =
    isNotBlank() && (startsWith("http://") || startsWith("https://"))

// ✅ RICHTIG – Mapping-Extension für Dto→Entity
fun RecipeDto.toEntity(): RecipeEntity = RecipeEntity(id = id, name = name, ...)

// ❌ FALSCH – kein semantischer Bezug zum Receiver
fun Context.showDeleteConfirmation(onConfirm: () -> Unit) { ... }
// Besser als eigenständige Composable-Funktion
```

---

## 9. DTO-Felder mit Default-Werten

**Regel:** Alle Felder in Sync-DTOs (`@JsonClass`) müssen Default-Werte haben.
Server kann neue Felder hinzufügen, App muss rückwärtskompatibel bleiben.

```kotlin
// ✅ RICHTIG – alle Felder haben Defaults, robuster gegen Server-Änderungen
@JsonClass(generateAdapter = true)
data class RecipeDto(
    val id: String,
    @Json(name = "updated_at") val updatedAt: Long,
    val deleted: Int = 0,
    val name: String = "",
    val description: String = "",
    val rating: Int = 0,
    // Neue Server-Felder werden als Default ignoriert statt Exception zu werfen
)

// ❌ FALSCH – fehlendes Feld = JsonDataException zur Laufzeit
@JsonClass(generateAdapter = true)
data class RecipeDto(
    val id: String,
    val updatedAt: Long,
    val name: String,  // kein Default → Crash wenn Server Feld wegfällt
)
```

---

## 10. `const val` für Route- und Key-Konstanten

**Regel:** Alle String-Konstanten für Navigation-Routen, SharedPreferences-Keys,
Intent-Extras etc. als `const val` in einem Companion Object oder Top-Level definieren.

```kotlin
// ✅ RICHTIG – in NavGraph oder Routes-Objekt
object Routes {
    const val RECIPE_LIST = "recipes"
    const val RECIPE_DETAIL = "recipes/{recipeId}"
    const val RECIPE_DETAIL_ARG = "recipeId"
    const val RECIPE_COOK = "recipes/{recipeId}/cook"
    const val URL_IMPORT = "recipes/url-import"
}

// ✅ RICHTIG – in DAO oder Repository
private const val GLOBAL_SETTINGS_ID = "global"

// ❌ FALSCH – Magic Strings in NavGraph
composable("recipes/{recipeId}") { ... }  // Tipp-Fehler kompiliert problemlos
```

---

## 11. Immutable State in Composables

**Regel:** Lokaler Composable-State für Eingaben mit `remember { mutableStateOf() }`,
nie direkte `var`-Felder ohne `remember`.

```kotlin
// ✅ RICHTIG
@Composable
fun SearchBar(onSearch: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    OutlinedTextField(value = query, onValueChange = { query = it })
}

// ❌ FALSCH – var ohne remember: Wert wird bei jeder Recomposition zurückgesetzt
@Composable
fun SearchBar(onSearch: (String) -> Unit) {
    var query = ""  // Wird bei Recomposition auf "" zurückgesetzt!
}
```

`mutableIntStateOf`, `mutableLongStateOf`, `mutableFloatStateOf` für primitive Typen
(performance-optimiert gegenüber `mutableStateOf<Int>`).
