# Compose Performance Guidelines

Dieses Dokument definiert verbindliche Regeln für performante Jetpack Compose Implementierungen.
Bei Code-Reviews und Neuimplementierungen aktiv auf Verstöße prüfen und Korrekturen vorschlagen.

---

## 1. Stabilität von Typen

**Regel:** Alle Datenklassen und UiState-Klassen, die direkt in Composables verwendet werden,
müssen stabile Typen sein.

```kotlin
// ✅ RICHTIG
@Immutable
data class RecipeListUiState(
    val recipes: List<RecipeEntity> = emptyList(),
    val selectedTag: String? = null,
)

// ❌ FALSCH – Compose kann Stabilität nicht inferieren, löst unnötige Recompositions aus
data class RecipeListUiState(
    val recipes: List<RecipeEntity> = emptyList(),
)
```

- `@Immutable` für Klassen, deren Felder sich nach Erstellung nie ändern
- `@Stable` für Klassen mit beobachtbarem Änderungs-Contract (z.B. mutableState-Felder)
- `List<T>` ist in Compose instabil → `ImmutableList<T>` (kotlinx.collections.immutable) oder
  `@Immutable`-Wrapper nutzen wenn Listen sich selten ändern

---

## 2. LazyColumn / LazyRow / LazyVerticalGrid

**Regel:** Immer stabilen `key`-Parameter angeben.

```kotlin
// ✅ RICHTIG
LazyColumn {
    items(recipes, key = { it.id }) { recipe ->
        RecipeListItem(recipe = recipe, onClick = { onRecipeClick(recipe.id) })
    }
}

// ❌ FALSCH – Standardmäßiger Index-Key führt zu unnötigem Recompose beim Reorder/Delete
LazyColumn {
    items(recipes) { recipe ->
        RecipeListItem(recipe = recipe)
    }
}
```

- Key muss stabil sein (UUID-String, Long-ID – keine Index-basierten Keys)
- `contentType`-Parameter nutzen wenn verschiedene Item-Typen in einer Liste gemischt werden

---

## 3. `remember` für teure Berechnungen

**Regel:** Berechnungen die von State-Werten abhängen und nicht trivial sind, in `remember`
mit expliziten Abhängigkeiten wrappen.

```kotlin
// ✅ RICHTIG – wird nur neu berechnet wenn sich recipes oder sortOrder ändert
val sortedRecipes = remember(recipes, sortOrder) {
    when (sortOrder) {
        SortOrder.NAME -> recipes.sortedBy { it.name }
        SortOrder.RATING -> recipes.sortedByDescending { it.rating }
    }
}

// ❌ FALSCH – wird bei jeder Recomposition neu berechnet
val sortedRecipes = when (sortOrder) {
    SortOrder.NAME -> recipes.sortedBy { it.name }
    SortOrder.RATING -> recipes.sortedByDescending { it.rating }
}
```

Betrifft insbesondere:
- Sortierung und Filterung von Listen
- String-Formatierung (Datum, Zeit, Zahlen)
- Berechnungen aus mehreren State-Werten
- `LocalDate.parse()`, `DateTimeFormatter`-Aufrufe

---

## 4. `derivedStateOf` für threshold-basierte Sichtbarkeit

**Regel:** Wenn ein Boolean-State von einem anderen State abhängt und nur bei
Schwellenwertüberschreitung wechselt, `derivedStateOf` nutzen.

```kotlin
// ✅ RICHTIG – Button-Sichtbarkeit ändert sich nur wenn scrollPosition Schwellenwert kreuzt
val showScrollToTop by remember {
    derivedStateOf { listState.firstVisibleItemIndex > 0 }
}

// ❌ FALSCH – löst Recomposition bei JEDEM Scroll-Event aus
val showScrollToTop = listState.firstVisibleItemIndex > 0
```

---

## 5. State-Reads so spät wie möglich

**Regel:** State-Werte erst in der Phase lesen, in der sie benötigt werden. Lambda-Modifier
statt direkter Wert-Übergabe nutzen für Positionen/Offsets.

```kotlin
// ✅ RICHTIG – scrollValue wird erst in der Draw-Phase gelesen (kein Layout-Recompose)
Modifier.graphicsLayer {
    translationY = scrollProvider()
}

// ❌ FALSCH – erzwingt Recomposition der gesamten Funktion bei jedem Scroll
Modifier.offset(y = scrollState.value.dp)
```

---

## 6. Composable-Lambdas in `items()` nicht inline

**Regel:** Item-Composables als benannte private Funktionen extrahieren, nicht als anonyme
Lambdas inline in `items()` definieren.

```kotlin
// ✅ RICHTIG
LazyColumn {
    items(recipes, key = { it.id }) { recipe ->
        RecipeListItem(recipe = recipe, onClick = { onRecipeClick(recipe.id) })
    }
}

@Composable
private fun RecipeListItem(recipe: RecipeEntity, onClick: () -> Unit) { ... }

// ❌ FALSCH – anonymes Lambda verhindert Compose-Optimierungen
LazyColumn {
    items(recipes, key = { it.id }) { recipe ->
        Card(onClick = { onRecipeClick(recipe.id) }) {
            Text(recipe.name)
            // ... komplexer Inhalt inline
        }
    }
}
```

---

## 7. ViewModel-Referenzen nicht durchreichen

**Regel:** ViewModels nur in Top-Level Screen-Composables verwenden. Callbacks und State
als primitive Props weitergeben.

```kotlin
// ✅ RICHTIG
@Composable
fun RecipeListScreen(viewModel: RecipeListViewModel = hiltViewModel()) {
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    RecipeList(
        recipes = recipes,
        onRecipeClick = viewModel::navigateToDetail,
    )
}

@Composable
private fun RecipeList(recipes: List<RecipeEntity>, onRecipeClick: (String) -> Unit) { ... }

// ❌ FALSCH – ViewModel-Referenz tief im Baum macht Composables nicht testbar/previewbar
@Composable
private fun RecipeList(viewModel: RecipeListViewModel) { ... }
```

---

## 8. State-Collection

**Regel:** Immer `collectAsStateWithLifecycle()` verwenden, niemals `collectAsState()`.

```kotlin
// ✅ RICHTIG – stoppt Collection wenn App im Hintergrund ist
val recipes by viewModel.recipes.collectAsStateWithLifecycle()

// ❌ FALSCH – sammelt weiter wenn App im Hintergrund ist (Battery-Drain)
val recipes by viewModel.recipes.collectAsState()
```

Abhängigkeit: `androidx.lifecycle:lifecycle-runtime-compose` (bereits im Projekt vorhanden)

---

## 9. Keine backwards-writes

**Regel:** Niemals in derselben Kompositions-Phase in State schreiben, der zuvor bereits
gelesen wurde. Das verursacht infinite recomposition loops.

```kotlin
// ❌ FALSCH – count wird gelesen (Text), dann sofort geschrieben
var count by remember { mutableIntStateOf(0) }
Text("$count")
count++ // CRASH: infinite loop

// ✅ RICHTIG – Schreiben nur in Event-Handlern
Button(onClick = { count++ }) { Text("$count") }
```

---

## 10. Compose Compiler Metriken (Diagnose)

Um instabile Typen zu finden, Compiler-Metriken aktivieren:

```kotlin
// app/build.gradle.kts
kotlinOptions {
    freeCompilerArgs += listOf(
        "-P", "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=$buildDir/compose_metrics",
        "-P", "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=$buildDir/compose_metrics",
    )
}
```

Danach `./gradlew assembleRelease` und `build/compose_metrics/` analysieren.
Klassen mit `unstable` markieren → `@Immutable` oder `@Stable` hinzufügen.
