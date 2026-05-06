# Kotlin für Android – Best Practices & Patterns

## 🔹 Grundlagen
- **Sprache**: Kotlin (offizielle Android-Sprache seit 2017)
- **Features**: Null-Safety (`?`, `!!`, `let`), Data Classes, Extension Functions, Coroutines
- **Tools**: Android Studio (mit Kotlin Plugin), IntelliJ IDEA

## 🔹 Code-Snippets

### Coroutines für asynchrone Operationen
```kotlin
// Beispiel: API-Aufruf mit Retrofit + Coroutines
viewModelScope.launch {
    try {
        val response = apiService.getRecipes()
        _recipes.value = response
    } catch (e: Exception) {
        _error.value = "Fehler: \${e.message}"
    }
}