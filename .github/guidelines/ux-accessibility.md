# UX & Accessibility Guidelines

Material Design 3 UX-Vorgaben und Android-Accessibility-Standards für alle Screens der
Helga-App. Gilt für Reviews bestehender Screens und Neuimplementierungen gleichwertig.

---

## 1. Touch-Target-Größe

**Regel:** Jedes interaktive Element muss mindestens 48×48 dp groß sein.

```kotlin
// ✅ RICHTIG – IconButton hat standardmäßig 48dp
IconButton(onClick = onDelete) {
    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
}

// ❌ FALSCH – Icon direkt klickbar mit zu kleinem Target
Icon(
    Icons.Filled.Delete,
    modifier = Modifier
        .size(24.dp)
        .clickable { onDelete() }  // 24dp < 48dp Mindestgröße!
)

// ✅ RICHTIG wenn kleinere Icons benötigt werden
Box(
    modifier = Modifier
        .size(48.dp)
        .clickable { onDelete() },
    contentAlignment = Alignment.Center,
) {
    Icon(Icons.Filled.Delete, contentDescription = ..., modifier = Modifier.size(24.dp))
}
```

Mindestabstand zwischen Touch-Targets: 8 dp.

---

## 2. contentDescription

**Regel:** Alle `Icon`- und `Image`-Composables MÜSSEN eine `contentDescription` haben,
außer sie sind rein dekorativ (dann explizit `null`).

```kotlin
// ✅ RICHTIG – Beschreibung aus strings.xml
Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_recipe))

// ✅ RICHTIG – dekoratives Icon, Screen-Reader ignoriert es
Icon(Icons.Filled.Star, contentDescription = null)

// ❌ FALSCH – leerer String verhindert Screen-Reader nicht zuverlässig
Icon(Icons.Filled.Add, contentDescription = "")

// ✅ RICHTIG – Bild mit Beschreibung
AsyncImage(
    model = imageUrl,
    contentDescription = recipe.name,
)
```

Besonders kritisch: Buttons ohne Text (nur Icon), die eine Aktion auslösen.

---

## 3. Fehlerzustände

**Regel:** Fehler niemals nur durch Farbe kommunizieren. Immer zusätzlich Text anzeigen.

```kotlin
// ✅ RICHTIG – Fehler mit Farbe UND Text
OutlinedTextField(
    value = url,
    onValueChange = onUrlChange,
    isError = urlError != null,
    supportingText = urlError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
    label = { Text(stringResource(R.string.server_url)) },
)

// ❌ FALSCH – nur rote Border ohne Erklärung
OutlinedTextField(
    value = url,
    isError = hasError,  // Sieht rot aus, erklärt aber nichts
)
```

Snackbar-Fehler: immer aussagekräftiger Text, kein "Fehler" allein.

---

## 4. Loading-States

**Regel:** Ladeindikator immer mit `contentDescription` für Screen-Reader.

```kotlin
// ✅ RICHTIG
CircularProgressIndicator(
    modifier = Modifier.semantics { contentDescription = "Lädt..." }
)

// Oder mit Modifier.semantics
Box(modifier = Modifier.semantics { contentDescription = stringResource(R.string.loading) }) {
    CircularProgressIndicator()
}

// ❌ FALSCH – Screen-Reader nennt nichts, User weiß nicht was passiert
CircularProgressIndicator()
```

Loading-States müssen die UI blockieren (kein paralleles Antippen möglich während geladen wird).

---

## 5. Leere Zustände (Empty States)

**Regel:** Jeder Screen der eine Liste zeigt, muss einen aussagekräftigen Leer-Zustand haben.

Mindestanforderung:
1. Erklärenden Text ("Noch keine Rezepte vorhanden")
2. Primäre Aktion anbieten ("Erstes Rezept erstellen")

```kotlin
// ✅ RICHTIG
if (recipes.isEmpty()) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.recipes_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.recipes_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAddRecipe) {
            Text(stringResource(R.string.recipes_add_first))
        }
    }
}
```

---

## 6. Destruktive Aktionen (Delete, Swipe-to-Delete)

**Regel:** Destruktive Aktionen immer mit Bestätigung oder Undo absichern.

```kotlin
// ✅ OPTION A – Bestätigungs-Dialog
var deleteCandidate by remember { mutableStateOf<String?>(null) }

if (deleteCandidate != null) {
    AlertDialog(
        onDismissRequest = { deleteCandidate = null },
        title = { Text(stringResource(R.string.delete_confirm_title)) },
        text = { Text(stringResource(R.string.delete_confirm_body)) },
        confirmButton = {
            TextButton(onClick = { viewModel.delete(deleteCandidate!!); deleteCandidate = null }) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = { deleteCandidate = null }) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

// ✅ OPTION B – Snackbar mit Undo (Optimistic UI)
// Lokal sofort entfernen, Snackbar mit "Rückgängig"-Aktion für 5 Sekunden anzeigen
```

---

## 7. Farben

**Regel:** Niemals hardcodierte Farben. Immer aus `MaterialTheme.colorScheme`.

```kotlin
// ✅ RICHTIG
Text(text = "Fehler", color = MaterialTheme.colorScheme.error)
Icon(tint = MaterialTheme.colorScheme.primary)
Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant))

// ❌ FALSCH – bricht Dark Mode und Dynamic Color
Text(text = "Fehler", color = Color.Red)
Icon(tint = Color(0xFF1565C0))
```

Ausnahmen: Rating-Sterne mit spezifischer Amber-Farbe sind akzeptabel wenn semantisch sinnvoll
und auch im Dark Mode kontrastreich genug.

---

## 8. Trennlinien und visuelle Gliederung

**Regel:** `HorizontalDivider` zwischen logisch getrennten Sektionen in Listen und Sheets.

```kotlin
// ✅ RICHTIG – klare visuelle Trennung
Column {
    SectionHeader("Einstellungen")
    HorizontalDivider()
    SettingsItem(...)
    HorizontalDivider()
    SettingsItem(...)
}
```

Kein `Spacer(Modifier.height(1.dp))` als Divider-Ersatz – semantisch bedeutungslos.

---

## 9. Modal Bottom Sheets

**Regel:** Bottom Sheets für Formulare immer mit `skipPartiallyExpanded = true`.

```kotlin
// ✅ RICHTIG – Formular-Sheet springt direkt in vollständigen Zustand
val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
) {
    // Formular-Inhalt
}
```

Bottom Sheets für einfache Auswahl (Picker) können `skipPartiallyExpanded = false` nutzen.

---

## 10. Keyboard-Handling

**Regel:** Alle `OutlinedTextField`/`TextField`-Komponenten müssen `imeAction` und
`KeyboardActions` korrekt konfigurieren.

```kotlin
// ✅ RICHTIG – letzte Eingabe schließt Keyboard und löst Aktion aus
OutlinedTextField(
    value = name,
    onValueChange = onNameChange,
    label = { Text(stringResource(R.string.recipe_name)) },
    singleLine = true,
    keyboardOptions = KeyboardOptions(
        capitalization = KeyboardCapitalization.Words,
        imeAction = ImeAction.Next,  // oder ImeAction.Done beim letzten Feld
    ),
    keyboardActions = KeyboardActions(
        onNext = { focusManager.moveFocus(FocusDirection.Down) },
        onDone = { focusManager.clearFocus(); onSubmit() },
    ),
)
```

Formulare: Felder in logischer Reihenfolge mit `ImeAction.Next`, letztes Feld mit `ImeAction.Done`.

---

## 11. Strings

**Regel:** Kein hardcodierter UI-Text in Kotlin/Compose-Code. Ausnahmslos `strings.xml`.

```kotlin
// ✅ RICHTIG
Text(stringResource(R.string.recipe_name_label))
contentDescription = stringResource(R.string.add_recipe)

// ❌ FALSCH
Text("Rezeptname")
contentDescription = "Rezept hinzufügen"
```

Auch Format-Strings mit Platzhaltern: `stringResource(R.string.sync_error_detail, errorMsg)`.

---

## 12. Scrollbare Screens

**Regel:** Screens mit variablem Inhalt müssen scrollbar sein.

```kotlin
// ✅ RICHTIG – für kurze, bekannte Inhalte
Column(modifier = Modifier.verticalScroll(rememberScrollState())) { ... }

// ✅ RICHTIG – für lange/dynamische Listen
LazyColumn { ... }

// ❌ FALSCH – Column ohne Scroll auf kleinen Displays abgeschnitten
Column(modifier = Modifier.fillMaxSize()) {
    // Viel Inhalt der nicht scrollt
}
```

Screens mit `LazyColumn` dürfen keine `Column(verticalScroll(...))` als Parent haben
(Scroll-Konflikt / IllegalStateException).
