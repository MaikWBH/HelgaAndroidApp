# Phase 2 — mobile-android-design

Basispfad `$B` = `app/src/main/kotlin/com/helga/android/` · UI `$U` = `$B/ui` · Testpfad `$T` = `app/src/test/kotlin/com/helga/android/` ·
Ressourcen `$R` = `app/src/main/res`

## Modelle
- Definition: Fable 5.2 · Umsetzung: Sonnet 5.5 · Datum: 2026-11-11

## Ziel und Bereich
Die Compose-UI (`$U`, `HelgaNavGraph.kt`, `MainActivity.kt`, `$R/values*`) auf Material-3-Stand des Skills
`mobile-android-design` und der Guidelines `ux-accessibility.md` / `compose-performance.md` bringen: vollständiges
Farbschema mit sauber gesteuerter Dynamic Color, korrekte Insets und adaptive Navigation, alle UI-Texte in `strings.xml`,
Semantik für Screenreader, zustandserhaltende UI und Previews für Bausteine. Querschnittlich über alle Bereiche des
Feature-Index; Status wird in den Bereichs-`plan.md` (rezepte, wochenplan, einkaufsliste, bons-kosten, ki, statistik,
maerkte, einstellungen) nur als Vermerk nachgezogen (S32).

## Entscheidungen (aus grilling, 2026-11-11)
- **F1 Dynamic Color:** eigene Picker-Option „System (Wallpaper)" als erster Eintrag, nur ab Android 12 sichtbar. Index 0
  ist wieder echtes Grün. **Mapping:** Preference-Key `accent_color` fehlt (nie gewählt) → System (`-1`); gespeicherter
  Wert `0` → Grün. Kein Migrationscode. Unter Android 12 wird `-1` als Grün dargestellt.
- **F2 Adaptive Layouts:** nur Navigation (`NavigationRail` ab mittlerer Breite per `WindowSizeClass`) und Begrenzung der
  Inhaltsbreite; genau eine neue Abhängigkeit (`material3-window-size-class`). Rezept-Grid und Zweispalter sind Nicht-Ziel.
- **F3 Strings:** alle Wort-Literale in Screens/Components nach `strings.xml` (inkl. Plurals) und die ViewModel-Meldungen
  über die Hülle `UiText`.
- **F4 Previews:** hell/dunkel für `ui/components` und alle neuen Bausteine; `Screen`/`Content`-Teilung nur für kleine
  Screens außerhalb von `receipts/` (Onboarding, UrlImport, Stats, WeekplanRecipePicker; AiRemix hat bereits `Content`).
- **F5 Zuschnitt:** Querschnittsblöcke plus ein Block „receipts".
- **F6 Navigation:** Predictive Back aktivieren, Onboarding-Aufblitzen beheben; Übergänge bleiben Standard.
- **Abgrenzung zu Phase 3 (Bons):** `ui/receipts/*` wird nur mechanisch angeglichen (Strings, `collectAsStateWithLifecycle`,
  AutoMirrored-Icons, zentraler Währungsformatter, Ladeindikator-Semantik, Scaffold/Zurück für `CostOverviewScreen`).
  Keine neuen Screens, Diagramme, Empty-State-Umbauten, Layoutänderungen oder Datenmodell-Änderungen dort.
- **Rahmen:** keine Änderung an Room, Sync, DTOs, `server/`. `AppPreferences` (DataStore) darf für F1 angepasst werden.
  Phase-1-Regeln und `ArchitectureRulesTest` bleiben grün.
- **Stufe B läuft ohne Emulator.** Verifikation nur per Build, Unit-Tests und grep-Kriterien. Schritte, deren Wirkung so
  nicht prüfbar ist, tragen eine „Prüfliste (Gerät)"; Stufe B hakt sie **nicht** ab, sondern sammelt sie unverändert im
  Abschnitt „Abschluss" für den Nutzer.

## Befunde (Stand 2026-11-11)
| # | Datei:Zeile | Befund | Regel (Skill/Guideline) |
|---|-------------|--------|-------------------------|
| 1 | `$U/theme/Theme.kt:35-60` | 6 Akzent-Schemata setzen nur `primary/secondary/tertiary`; Container-, on- und Surface-Rollen fallen auf lila M3-Baseline | Skill material3-theming „Custom Color Scheme"; ux §7 |
| 2 | `Theme.kt:85-88`, `SettingsScreen.kt:469-490` | Dynamic Color verdeckt an Index 0 gekoppelt, Picker zeigt Grün | Skill Best Practice 2 |
| 3 | `HelgaNavGraph.kt:128-131`, `ReceiptListScreen.kt:60-83` | Root-Padding nicht am `NavHost`; `bottomPadding` nur an 3 von 4 Root-Screens, FAB der Bon-Liste liegt hinter der NavigationBar; sonst doppelter Nav-Inset | Skill android-navigation „Bottom Navigation" |
| 4 | `CostOverviewScreen.kt:48-80` | kein Scaffold, keine TopAppBar, kein Zurück, Inhalt unter der Statusleiste | Edge-to-Edge |
| 5 | `$R/values/themes.xml`, `Theme.kt:94-101` | XML-Theme fest hell, kein `values-night`; `statusBarColor` trotz Edge-to-Edge | Skill Theming/Dark Mode |
| 6 | `$U/**` | mind. 107 hartkodierte UI-Texte (18 Dateien), Schwerpunkt `receipts/*` (ca. 45) und `ShoppingListScreen` (26); 0× `pluralStringResource` | CLAUDE.md; ux §11 |
| 7 | `$U/**/*ViewModel.kt` | 12 Meldungs-Literale in 6 ViewModels | ux §11 |
| 8 | `$U/**` | 0× `rememberSaveable`, 105× `remember…mutableStateOf` | Skill Best Practice 7 |
| 9 | `receipts/*.kt`, `MainActivity.kt:26-27` | 22× `collectAsState()` | compose-performance §8 |
| 10 | `$U/**` | 21 `CircularProgressIndicator` ohne Semantik | ux §4 |
| 11 | `$U/**` | 36 `.clickable` ohne Rolle; Farbkreise 40 dp ohne Beschriftung/Zustand; Slot-Chip zu klein | ux §1, §2, §7 |
| 12 | `$U/**` | 0× `@Preview`; 15 von 20 Screens ohne zustandsloses `Content` | Skill Best Practice 6/8 |
| 13 | `HelgaNavGraph.kt:94-146` | keine `WindowSizeClass`, immer `NavigationBar` | Skill Best Practice 3 |
| 14 | `HelgaNavGraph.kt:99-109, 132` | Start immer Onboarding, Weiterleitung per `LaunchedEffect` (Aufblitzen) | Skill android-navigation „State Management" |
| 15 | `AndroidManifest.xml` | `enableOnBackInvokedCallback` fehlt | Skill android-navigation „Back Handler" |
| 16 | `Theme.kt:103`, `$U/**` | keine `shapes`; 17 ad-hoc `RoundedCornerShape` (8 dp ×8, 6 dp ×4, 4 dp ×3, 12 dp ×1, 50 % ×1) | Skill „Shape System" |
| 17 | `$U/**`, `HelgaNavGraph.kt` | 12 nicht gespiegelte Icons: `ArrowBack` ×4, `ReceiptLong` ×3, `Sort` ×2, `TrendingUp`, `MenuBook`, `FormatListBulleted` | M3 |
| 18 | `$U/**` | Fehler-/Leerzustände uneinheitlich; nur 2 Screens mit `SnackbarHost` | ux §3, §5 — **in Phase 2 nur dokumentiert**, Receipts → Phase 3 |
| 19 | `$U/components/` | kaum gemeinsame Bausteine (Ladeindikator, Zurück-Icon, Farbkreis-Picker fehlen) | Skill „reusable components" |
| 20 | `receipts/*`, `shopping/*` | Währung als `String.format("€%.2f")` ohne Locale, 3 doppelte `formatCurrency` in ViewModels | UI-Sprache Deutsch |
| 21 | `SettingsScreen.kt:469` | 1 `itemsIndexed` ohne `key` | compose-performance §2 |
| 22 | `components/DayMarker.kt:17-33` | Marker-Palette als persistierte Hex-Strings — **bleibt**, nur Kontrast auf Prüfliste | ux §7 (Ausnahme) |
| 23 | 18× `TopAppBar` | kein `scrollBehavior` — **Nicht-Ziel** | M3 optional |
| 24 | Manifest, `$U/**` | 0× `imePadding`, kein `windowSoftInputMode` | Edge-to-Edge/Insets |

## Schritte (Stufe B arbeitet nur diese Liste ab)

**Vorgehen je Schritt:** Änderung → Verifikation → Checkbox abhaken → Commit (Deutsch, ein Satz, Attribution laut
Session-Anweisung). Ein Commit pro Schritt.

**Verifikation `V`** (gilt für jeden Schritt, zusätzlich zum grep-Kriterium des Schritts):
```bash
export ANDROID_HOME=/root/android-sdk
./gradlew :app:testDebugUnitTest :app:compileDebugKotlin --console=plain --no-parallel --max-workers=1 --no-configuration-cache
```
Bei HTTP 429 beim Auflösen von Abhängigkeiten ca. 45 s warten und wiederholen (max. 3 Versuche). Läuft Gradle danach
nicht, gilt der Schritt als **nicht verifiziert**: stoppen und per `grilling` melden.

**Allgemeine Regeln für Stufe B**
- Zeilennummern sind Stand 2026-11-11 und verschieben sich; immer per `grep -n` neu suchen.
- Verhalten und Layout bleiben unverändert, sofern der Schritt nichts anderes sagt. Keine Umbenennung öffentlicher
  Composables, keine Datei-Aufteilung der Groß-Screens.
- **String-Regel (R-S):** Text wörtlich übernehmen (inkl. Emojis, „…", Satzzeichen). Name `snake_case` mit Bereichspräfix
  (`receipt_`, `cost_`, `price_`, `shopping_`, `weekplan_`, `recipe_`, `settings_`, `ai_`, `stats_`, `common_`). Vor dem
  Anlegen `grep -n '>Text<' $R/values/strings.xml`: existiert derselbe Text schon, den vorhandenen Namen verwenden
  (z. B. `action_back` für „Zurück"). Variable Teile als `%1$s`/`%1$d`; Apostroph als `\'`, `%` als `%%`.
  **Bleiben dürfen** Literale ohne Wort: nur Interpolation, Ziffern, Satzzeichen, Emojis (z. B. `"${index + 1}."`,
  `"• $label"`, `"🥩 ${n}"`, `"$checked/${size}"`), außerdem Animations-Label (`"sync-rotate"`, `"angle"`) und
  `ui/widget/` (Bereich Plattform).
- **Prüf-grep `G-S <Datei>`:**
  `grep -nE '(Text\(\s*|(text|label|title|placeholder|contentDescription|message) = )"[^"]*[A-Za-zÄÖÜäöü]{2,}' <Datei>` —
  jeder verbleibende Treffer muss unter „Bleiben dürfen" fallen (Buchstaben nur innerhalb `${…}`/`$name`).
- Unklarheit, unerwarteter Code oder ein Kriterium, das sich nicht erfüllen lässt: stoppen und per `grilling` fragen.

### Block 0 — Basis
- [x] **S0 Baseline** — `V` ausführen; Testanzahl und Ergebnis unter „Abschluss" notieren. Zusätzlich die Zähl-Baseline
  festhalten: `grep -rn 'collectAsState()' $B | wc -l` (erwartet 20; plus 2× `collectAsState(initial` in `MainActivity`),
  `grep -rn 'CircularProgressIndicator(' $U | wc -l` (21), `grep -rn 'rememberSaveable' $U | wc -l` (0),
  `grep -rn '@Preview\|@ThemePreviews' $U | wc -l` (0). Weicht eine Zahl ab: notieren, weitermachen.

### Block A — Theme (Befunde 1, 2, 5, 16, 21)
- [x] **S1 Vollständige Farbschemata** — Dateien: neu `$U/theme/Color.kt`, `$U/theme/Theme.kt`. In `Color.kt`:
  `internal data class AccentTones(val main: Color, val on: Color, val container: Color, val onContainer: Color)` und je
  Farbfamilie ein Hell- und ein Dunkel-Wert nach **Tabelle T1**; zwei Funktionen
  `internal fun helgaLightScheme(p: AccentTones, s: AccentTones, t: AccentTones): ColorScheme` und `helgaDarkScheme(…)`,
  die `lightColorScheme(…)`/`darkColorScheme(…)` mit **allen** Rollen aus T1 (primary/secondary/tertiary jeweils main, on,
  container, onContainer), `surfaceTint = p.main`, `inversePrimary` = main der jeweils anderen Helligkeit (als vierter
  Parameter übergeben) und allen Neutralrollen aus **Tabelle T2** füllen. Fehlerrollen bleiben M3-Standard.
  `accentSchemes` in `Theme.kt` wird aus diesen Funktionen nach **Tabelle T3** gebaut; `accentPrimaryColors` bleibt
  inhaltlich gleich (zieht nach `Color.kt`). Akzeptanz: `grep -c 'Color(0x' $U/theme/Theme.kt` = 0; in `Color.kt` kommt
  jeder Hex-Wert aus T1/T2 vor; `grep -rn 'Color(0x' $U | grep -v theme/` leer. Prüfliste (Gerät): jede der 6 Akzentfarben
  hell und dunkel — FAB, NavigationBar-Indikator, FilterChips und Karten zeigen den Akzent bzw. neutrale Flächen, nirgends
  Lila außer beim Akzent Lila; Text auf Buttons lesbar.
- [x] **S2 Option „System (Wallpaper)" — Logik** — Dateien: `$B/data/preferences/AppPreferences.kt`, `$U/theme/Theme.kt`,
  `$B/MainActivity.kt`, `$U/settings/SettingsViewModel.kt`. In `Color.kt`/`Theme.kt` `const val ACCENT_SYSTEM = -1`.
  `AppPreferences.accentColor`: `it[KEY_ACCENT_COLOR] ?: -1` (Literal mit Kommentar-Verweis auf `ACCENT_SYSTEM`, kein
  Import aus `ui/` in die Datenschicht); `saveAccentColor`: `coerceIn(-1, 5)`. `HelgaTheme`: Parameter-Default
  `accentColorIndex = ACCENT_SYSTEM`; `when`: `accentColorIndex == ACCENT_SYSTEM && SDK_INT >= S` → dynamisches Schema;
  sonst `accentSchemes[accentColorIndex.coerceIn(0, lastIndex)]` (damit `-1` unter Android 12 = Grün, `0` = Grün).
  `MainActivity`: `collectAsState(initial = …)` → `collectAsStateWithLifecycle(initialValue = …)`, Initialwert Akzent
  `ACCENT_SYSTEM`. `SettingsUiState.accentColor` Default `ACCENT_SYSTEM`. Akzeptanz:
  `grep -n 'accentColorIndex == 0' $U/theme/Theme.kt` leer; `grep -c 'collectAsState(' $B/MainActivity.kt` = 0.
  Prüfliste (Gerät, Android 12+): Neuinstallation zeigt Wallpaper-Farben; nach Auswahl Grün ist die App grün.
- [x] **S3 Baustein `ColorSwatchPicker` und Akzent-Picker** — Dateien: neu `$U/components/Previews.kt`, neu
  `$U/components/ColorSwatchPicker.kt`, `$U/settings/SettingsScreen.kt`, `$R/values/strings.xml`. `Previews.kt`:
  Annotation `@ThemePreviews` = `@Preview(name = "Hell", showBackground = true)` +
  `@Preview(name = "Dunkel", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)` und
  `@Composable fun PreviewSurface(content: @Composable () -> Unit)` = `HelgaTheme(accentColorIndex = 0) { Surface { content() } }`.
  `ColorSwatchPicker.kt`: `data class ColorSwatch(val key: String, val label: String, val color: Color?, val icon: ImageVector? = null)`;
  `@Composable fun ColorSwatchPicker(swatches: List<ColorSwatch>, selectedKey: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier)`
  als `LazyRow` (`Arrangement.spacedBy(8.dp)`, `items(swatches, key = { it.key })`), `Modifier.selectableGroup()`; je
  Eintrag eine `Box` 48 dp mit `Modifier.selectable(selected, role = Role.RadioButton, onClick)` und
  `semantics { contentDescription = label }`, darin ein Kreis 40 dp (`CircleShape`, Hintergrund `color` bzw.
  `surfaceVariant` wenn `color == null`, Rand 3 dp `onSurface` bei Auswahl sonst 1 dp `outlineVariant`), bei `icon != null`
  das Icon 20 dp in `onSurfaceVariant`, bei Auswahl zusätzlich `Icons.Filled.Check` 20 dp (Tint: weiß bzw.
  `onSurfaceVariant` beim Icon-Eintrag ersetzt das Icon). Zwei `@ThemePreviews`. `SettingsScreen`: der Block
  `LazyRow { itemsIndexed(accentPrimaryColors) … }` (≈ Z.466–492) wird durch `ColorSwatchPicker` ersetzt; Einträge:
  nur wenn `SDK_INT >= S` zuerst `ColorSwatch("-1", settings_accent_system, null, Icons.Filled.Wallpaper)`, dann Index 0–5
  mit Labels; `selectedKey` = `state.accentColor`, unter Android 12 wird `-1` als `"0"` markiert;
  `onSelect = { viewModel.setAccentColor(it.toInt()) }`. Darunter eine Zeile `Text` (bodySmall, `onSurfaceVariant`) mit dem
  Label der Auswahl. Neue Strings: `settings_accent_system` „System (Wallpaper)", `settings_accent_green` „Grün",
  `settings_accent_blue` „Blau", `settings_accent_purple` „Lila", `settings_accent_orange` „Orange",
  `settings_accent_red` „Rot", `settings_accent_teal` „Petrol". Akzeptanz: `grep -n 'itemsIndexed(accentPrimaryColors' $U` leer;
  `grep -c '@ThemePreviews' $U/components/ColorSwatchPicker.kt` ≥ 2. Prüfliste (Gerät): TalkBack liest Farbnamen und
  „ausgewählt"; Option „System" fehlt unter Android 12.
- [x] **S4 Dark-Start und Systemleisten** — Dateien: neu `$R/values-night/themes.xml`, `$U/theme/Theme.kt`.
  `values-night/themes.xml`: gleicher Style `Theme.HelgaAndroid`, `parent="android:Theme.Material.NoActionBar"`,
  `android:statusBarColor` transparent, `android:windowLightStatusBar` = `false`. In `HelgaTheme` die Zeile
  `window.statusBarColor = …` und den dann unbenutzten Import `toArgb` entfernen; im `SideEffect` ergänzen:
  `isAppearanceLightNavigationBars = !darkTheme` (neben `isAppearanceLightStatusBars`). Akzeptanz:
  `grep -n 'statusBarColor' $U/theme/Theme.kt` leer; Datei `values-night/themes.xml` existiert. Prüfliste (Gerät): Kaltstart
  im System-Dark-Mode ohne weißes Aufblitzen; App-Modus „Hell" bei System dunkel zeigt dunkle Statusleisten-Icons und
  umgekehrt.
- [x] **S5 Shapes** — Dateien: neu `$U/theme/Shape.kt`, `$U/theme/Theme.kt`, alle Dateien mit `RoundedCornerShape(`.
  `val HelgaShapes = Shapes(extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(28.dp))`;
  `MaterialTheme(colorScheme, shapes = HelgaShapes, content)`. Typografie bleibt M3-Standard (keine `Type.kt`). Ersetzen in
  `$U` (ohne `theme/`): `RoundedCornerShape(4.dp)` → `MaterialTheme.shapes.extraSmall`, `6.dp` und `8.dp` →
  `MaterialTheme.shapes.small`, `12.dp` → `MaterialTheme.shapes.medium`, `RoundedCornerShape(50)` → `CircleShape`. Steht
  eine Stelle außerhalb eines `@Composable`-Kontexts: unverändert lassen und unter „Abschluss" nennen. Akzeptanz:
  `grep -rn 'RoundedCornerShape(' $U | grep -v theme/` leer oder nur gemeldete Stellen.

**Tabelle T1 — Akzent-Töne** (main / on / container / onContainer)
| Familie | Hell | Dunkel |
|---------|------|--------|
| Grün | `2E7D32` / `FFFFFF` / `C8E6C9` / `1B5E20` | `81C784` / `0A3D0E` / `1B5E20` / `C8E6C9` |
| Blau | `1565C0` / `FFFFFF` / `BBDEFB` / `0D47A1` | `90CAF9` / `0A3059` / `0D47A1` / `BBDEFB` |
| Lila | `6A1B9A` / `FFFFFF` / `E1BEE7` / `4A148C` | `CE93D8` / `3A0F6B` / `4A148C` / `E1BEE7` |
| Lila-hell (nur Tertiär von Grün) | `8E24AA` / `FFFFFF` / `E1BEE7` / `4A148C` | wie Lila dunkel |
| Orange | `E65100` / `FFFFFF` / `FFE0B2` / `7A2E00` | `FFCC80` / `4A2800` / `7A3E00` / `FFE0B2` |
| Rot | `B71C1C` / `FFFFFF` / `FFCDD2` / `7F1010` | `EF9A9A` / `5F0E0E` / `7F1515` / `FFCDD2` |
| Petrol | `00695C` / `FFFFFF` / `B2DFDB` / `004D40` | `80CBC4` / `00332B` / `004D40` / `B2DFDB` |
| Amber (nur Sekundär von Grün) | `FF8F00` / `000000` / `FFECB3` / `5C3A00` | `FFB74D` / `4A2800` / `7A4A00` / `FFECB3` |
| Cyan (nur Sekundär von Blau) | `00838F` / `FFFFFF` / `B2EBF2` / `006064` | `80DEEA` / `00363B` / `006064` / `B2EBF2` |

Alle Werte als `Color(0xFF……)`. Die main-Werte entsprechen den bisherigen Farben. Kontrast main/on und
container/onContainer rechnerisch ≥ 4,5:1, Ausnahme Orange hell (`E65100`/`FFFFFF` = 3,8:1, bisheriger Wert, bleibt).

**Tabelle T2 — Neutralrollen (für alle Akzente gleich)**
| Rolle | Hell | Dunkel |
|-------|------|--------|
| `background`, `surface` | `FCFCFC` | `121312` |
| `onBackground`, `onSurface` | `1B1C1B` | `E3E3E0` |
| `surfaceVariant` | `E1E3DF` | `444744` |
| `onSurfaceVariant` | `444744` | `C4C7C2` |
| `outline` | `747874` | `8E918D` |
| `outlineVariant` | `C4C7C2` | `444744` |
| `surfaceDim` | `DCDCDA` | `121312` |
| `surfaceBright` | `FCFCFC` | `383938` |
| `surfaceContainerLowest` | `FFFFFF` | `0D0E0D` |
| `surfaceContainerLow` | `F6F6F4` | `1B1C1B` |
| `surfaceContainer` | `F0F0EE` | `1F201F` |
| `surfaceContainerHigh` | `EAEAE8` | `292A29` |
| `surfaceContainerHighest` | `E4E4E2` | `343534` |
| `inverseSurface` | `303130` | `E3E3E0` |
| `inverseOnSurface` | `F2F1EF` | `303130` |
| `scrim` | `000000` | `000000` |

**Tabelle T3 — Zuordnung je Akzent-Index** (wie bisher)
| Index | Name | primary | secondary | tertiary |
|-------|------|---------|-----------|----------|
| 0 | Grün | Grün | Amber | Lila-hell |
| 1 | Blau | Blau | Cyan | Lila |
| 2 | Lila | Lila | Blau | Petrol |
| 3 | Orange | Orange | Grün | Blau |
| 4 | Rot | Rot | Orange | Blau |
| 5 | Petrol | Petrol | Blau | Lila |

### Block B — Navigation und Insets (Befunde 3, 4, 13, 14, 15, 24)
- [x] **S6 Root-Padding am NavHost** — Dateien: `$B/HelgaNavGraph.kt`, `$U/shopping/ShoppingListScreen.kt`,
  `$U/recipes/RecipeListScreen.kt`, `$U/weekplan/WeekplanScreen.kt`. Root-`Scaffold`:
  `contentWindowInsets = WindowInsets(0, 0, 0, 0)`; `NavHost(modifier = Modifier.padding(padding).consumeWindowInsets(padding), …)`.
  Parameter `bottomPadding` aus den drei Screens samt aller Verwendungen entfernen (Shopping ≈ Z.109, 613, 1165, 1254–1255
  inkl. des `Spacer`; RecipeList ≈ Z.88, 179 inkl. Kommentar, 253 — dort den `bottom`-Anteil auf den Wert ohne
  `bottomPadding` zurückführen; Weekplan ≈ Z.121, 292 ebenso) und die drei Aufrufe in `HelgaNavGraph` anpassen.
  Akzeptanz: `grep -rn 'bottomPadding' $B` leer. Prüfliste (Gerät, Gesten- und 3-Tasten-Navigation): auf allen vier Tabs
  liegt nichts hinter der NavigationBar (insb. FAB „Scannen" der Bon-Liste, QuickAdd-Leiste, letzter Listeneintrag);
  keine doppelte Lücke über der Leiste; Detail-Screens reichen bis an die Systemleiste.
- [x] **S7 `CostOverviewScreen` mit Scaffold** — Dateien: `$U/receipts/CostOverviewScreen.kt`, `$B/HelgaNavGraph.kt`,
  `strings.xml`. Signatur `CostOverviewScreen(onBack: () -> Unit, viewModel: … = hiltViewModel())`; bisherigen Inhalt
  unverändert in `Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.cost_overview_title)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } }) }) { padding -> … }`
  setzen, äußere `Column` bekommt `.padding(padding)` vor `verticalScroll`. String `cost_overview_title`
  „Kostenübersicht". NavGraph: `CostOverviewScreen(onBack = { navController.popBackStack() })`. Sonst nichts ändern.
  Akzeptanz: `grep -c 'Scaffold(' $U/receipts/CostOverviewScreen.kt` = 1. Prüfliste (Gerät): Titel und Zurück sichtbar,
  Inhalt beginnt unter der Leiste.
- [x] **S8 Startziel ohne Aufblitzen** — Datei: `$B/HelgaNavGraph.kt`. Am Anfang von `HelgaNavGraph`:
  `var startRoute by rememberSaveable { mutableStateOf<String?>(null) }`;
  `LaunchedEffect(Unit) { if (startRoute == null) startRoute = if (preferences.connection.first().isConfigured) ROUTE_SHOPPING else ROUTE_ONBOARDING }`;
  solange `startRoute == null`: `Surface(Modifier.fillMaxSize()) {}` und `return`. `NavHost(startDestination = startRoute!!…)`
  — ohne `!!`: lokale `val start = startRoute ?: return` nach dem Surface-Zweig verwenden. Den bisherigen
  `LaunchedEffect(Unit)` (≈ Z.99–109) löschen. Share-Intent:
  `var importHandled by rememberSaveable { mutableStateOf(false) }`;
  `LaunchedEffect(start) { if (start == ROUTE_SHOPPING && initialImportUrl != null && !importHandled) { importHandled = true; navController.navigate(ROUTE_RECIPE_URL_IMPORT) } }`.
  Alle übrigen Navigationsaufrufe (`onContinue`, `onLoggedOut`, Bottom-Nav `popUpTo(ROUTE_SHOPPING)`) bleiben.
  `rememberNavController()` muss vor dem frühen `return` stehen. Akzeptanz: `grep -n 'startDestination = ROUTE_ONBOARDING' $B/HelgaNavGraph.kt`
  leer; `grep -c '!!' $B/HelgaNavGraph.kt` = 0. Prüfliste (Gerät): konfigurierte App startet direkt in der Einkaufsliste;
  frische App zeigt Onboarding; Teilen einer URL öffnet den Import, Zurück führt zur Einkaufsliste; Drehen im Import
  öffnet ihn nicht erneut; Abmelden in den Einstellungen führt zum Onboarding.
- [x] **S9 Predictive Back** — Datei: `app/src/main/AndroidManifest.xml`. Am `<application>`-Element
  `android:enableOnBackInvokedCallback="true"`. Vorher prüfen: `grep -rn 'onBackPressed\|OnBackPressedCallback' app/src/main`
  muss leer sein, sonst stoppen. Akzeptanz: Attribut vorhanden, Lint/Build grün. Prüfliste (Gerät, Android 14+ mit
  Gestennavigation): Zurück-Geste zeigt Vorschau und schließt Sheets/Dialoge vor dem Screen.
- [x] **S10 Tastatur-Insets** — Dateien: `AndroidManifest.xml`, `$B/HelgaNavGraph.kt`. `MainActivity`:
  `android:windowSoftInputMode="adjustResize"`. `NavHost`-Modifier aus S6 erweitern zu
  `Modifier.padding(padding).consumeWindowInsets(padding).imePadding()`. Sonst nirgends `imePadding` ergänzen. Akzeptanz:
  `grep -rn 'imePadding' $B | wc -l` = 1. Prüfliste (Gerät): QuickAdd-Feld der Einkaufsliste und unterste Felder in
  Rezeptformular, Einstellungen und Onboarding bleiben bei offener Tastatur sichtbar; keine Lücke zwischen Inhalt und
  Tastatur; Bottom-Sheets mit Textfeld unverändert.
- [x] **S11 NavigationRail und Inhaltsbreite** — Dateien: `gradle/libs.versions.toml`, `app/build.gradle.kts`,
  `$B/MainActivity.kt`, `$B/HelgaNavGraph.kt`. Neue Bibliothek
  `androidx-compose-material3-window-size = { group = "androidx.compose.material3", name = "material3-window-size-class" }`
  (Version über die BOM), `implementation(libs.androidx.compose.material3.window.size)`. `MainActivity`:
  `val widthClass = calculateWindowSizeClass(this).widthSizeClass` (mit `@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)`),
  an `HelgaNavGraph(…, widthSizeClass = widthClass)` übergeben (neuer Parameter, Default `WindowWidthSizeClass.Compact`).
  In `HelgaNavGraph`: Navigationslogik des `onClick` in eine lokale Funktion `navigateToRoot(route: String)` ziehen.
  `useRail = showBottomNav && widthSizeClass != WindowWidthSizeClass.Compact`. `bottomBar` nur wenn
  `showBottomNav && !useRail`. Inhalt des Scaffolds: `Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding())`
  mit — falls `useRail` — `NavigationRail { bottomNavItems.forEach { NavigationRailItem(selected = currentRoute == it.route, onClick = { navigateToRoot(it.route) }, icon = …, label = …) } }`
  (gleiche Icons/Label wie die Leiste), danach `Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter)`
  mit dem `NavHost(modifier = if (widthSizeClass == WindowWidthSizeClass.Expanded) Modifier.widthIn(max = 840.dp) else Modifier, …)`.
  Die Modifier aus S6/S10 wandern vom `NavHost` auf die `Row`. Akzeptanz: `grep -c 'NavigationRail(' $B/HelgaNavGraph.kt` = 1;
  `grep -c 'imePadding' $B/HelgaNavGraph.kt` = 1; nur eine neue Zeile unter `[libraries]`. Prüfliste (Gerät/Emulator
  Tablet oder Querformat ≥ 600 dp): Rail links statt Leiste unten auf den vier Tabs; auf Detail-Screens keine Rail;
  Hochkant-Phone unverändert; auf ≥ 840 dp ist der Inhalt mittig auf 840 dp begrenzt.

### Block C — Bausteine und Previews (Befunde 10, 12, 17, 19)
- [x] **S12 Previews für vorhandene Components** — Dateien: `$U/components/AllergenWarningBanner.kt`, `DayMarker.kt`,
  `SyncStatusIcon.kt`, `CreateFab.kt`. Je Datei mindestens eine private `@ThemePreviews`-Funktion in `PreviewSurface`
  mit festen Beispieldaten (für `SyncStatusIcon` je ein Aufruf pro `SyncStatus`-Variante in einer `Row`).
  `BarcodeScanner.kt` (Kamera) und `MealSlot.kt` (kein UI) bekommen keine. Signaturen nicht ändern. Akzeptanz:
  `grep -c '@ThemePreviews' ` je Datei ≥ 1.
- [x] **S13 `LoadingIndicator` mit Semantik** — Dateien: neu `$U/components/LoadingIndicator.kt`, alle Dateien mit
  `CircularProgressIndicator(` (21 Stellen in 15 Dateien, auch `receipts/`), `strings.xml`.
  `@Composable fun LoadingIndicator(modifier: Modifier = Modifier, strokeWidth: Dp = ProgressIndicatorDefaults.CircularStrokeWidth, color: Color = ProgressIndicatorDefaults.circularColor, label: String = stringResource(R.string.common_loading))`
  = `CircularProgressIndicator(modifier = modifier.semantics { contentDescription = label }, color = color, strokeWidth = strokeWidth)`;
  String `common_loading` „Lädt…" (vorhandenen gleichlautenden String wiederverwenden, falls es einen gibt). Jede
  indeterminierte Stelle 1:1 ersetzen und `modifier`/`strokeWidth`/`color` durchreichen; Stellen mit `progress = …`
  (determiniert) bleiben `CircularProgressIndicator`. Preview hell/dunkel. Akzeptanz:
  `grep -rn 'CircularProgressIndicator(' $U | grep -v 'components/LoadingIndicator.kt'` zeigt nur Stellen mit `progress`.
- [x] **S14 Gespiegelte Icons** — Dateien: Treffer von
  `grep -rnE 'Icons\.(Filled|Default)\.(ArrowBack|ReceiptLong|Sort|TrendingUp|MenuBook|FormatListBulleted)\b' $B`
  (12 Stellen, inkl. `HelgaNavGraph.kt` und `receipts/`). Ersetzen durch `Icons.AutoMirrored.Filled.<Name>` und die
  Imports auf `androidx.compose.material.icons.automirrored.filled.<Name>` umstellen. Akzeptanz: obiger grep leer.

### Block D — Receipts mechanisch angleichen (Befunde 6, 9, 20; Abgrenzung Phase 3 beachten)
In diesem Block nur: Strings (R-S), `collectAsState()` → `collectAsStateWithLifecycle()`, Währungsformat. Keine
Layout-, Struktur- oder Verhaltensänderung, keine neuen Composables.
- [x] **S15 Zentraler Währungsformatter** — Dateien: neu `$U/common/CurrencyFormat.kt`, neu `$T/CurrencyFormatTest.kt`,
  alle Fundstellen von `grep -rn '"€%.2f"\|€%' $U | grep -v widget/`. `fun formatEuro(amount: Double): String =
  NumberFormat.getCurrencyInstance(Locale.GERMANY).format(amount)` (Ergebnis z. B. `12,34 €` mit geschütztem Leerzeichen).
  Test: `0.0`, `12.3`, `1234.5`, `-5.0` — Vergleich nach `replace(' ', ' ')` gegen `"0,00 €"`, `"12,30 €"`,
  `"1.234,50 €"`, `"-5,00 €"`. Alle `String.format("€%.2f", x)` in Screens durch `formatEuro(x)` ersetzen; die drei
  `formatCurrency` in `CostOverviewViewModel`, `ProductPriceListViewModel`, `ProductPriceDetailViewModel` bleiben als
  Methode bestehen und delegieren an `formatEuro`. Andere `format`-Aufrufe (Mengen, Prozente, Datum) nicht anfassen.
  Akzeptanz: `grep -rn '€%' $U | grep -v widget/` leer; neuer Test grün.
- [x] **S16 `ReceiptListScreen` + `ReceiptDetailScreen`** — Strings nach R-S (u. a. „Einkäufe", „Preise",
  „Kostenübersicht" → `cost_overview_title`, „Statistik", „Scannen", „Noch keine Kassenbons gescannt",
  „Unbekannter Markt", Löschdialog, „Bon nicht gefunden", „Kassenbon-Foto"); auch Literale außerhalb des Prüf-greps
  (`ifBlank { "…" }`, Abschnittstitel) per Durchsicht der Datei. `"$itemCount Artikel"` bleibt bis S22. 6×
  `collectAsState()` umstellen. Akzeptanz: `G-S` für beide Dateien regelkonform (Ausnahme S22-Stelle);
  `grep -c 'collectAsState()' ` je Datei = 0.
- [x] **S17 `ReceiptScanScreen`** — Strings nach R-S (ca. 19 Stellen, Z.128–527; die führenden Leerzeichen in
  `"  Foto aufnehmen"`/`"  Aus Galerie wählen"` gehören nicht in den String, sondern als `Spacer(Modifier.width(8.dp))`
  davor); 1× `collectAsState()`. Akzeptanz wie S16.
- [x] **S18 `CostOverviewScreen` + `ProductPriceListScreen` + `ProductPriceDetailScreen`** — Strings nach R-S (ca. 12
  Stellen), 13× `collectAsState()`. Akzeptanz wie S16; zusätzlich `grep -rn 'collectAsState()' $B` leer.

### Block E — Strings im Rest der App (Befunde 6, 7)
- [x] **S19 Einkaufsliste** — Datei: `$U/shopping/ShoppingListScreen.kt` (26 Stellen, v. a. Z.297–305 und der
  Produktkatalog-/Produktdetail-Teil Z.1363–1724) nach R-S. Nährwert-Zeilen: Label als String (`"🔥 Energie"` usw.),
  Werte bleiben interpoliert. Akzeptanz: `G-S` regelkonform.
- [x] **S20 Wochenplan** — Datei: `$U/weekplan/WeekplanScreen.kt` (Z.163 „Wochenplan erstellt", Z.1306–1343:
  „🔥 Nährwert-Budgets", `"Max kcal/Portion: %1$d"`, „🌿 Bio bevorzugt", „🚫 Allergene ausschließen"). Akzeptanz: `G-S`.
- [x] **S21 Rezepte, Einstellungen, Components** — Dateien: `$U/recipes/RecipeListScreen.kt` (Z.155, 554; Z.557 bleibt
  bis S22), `RecipeDetailScreen.kt:556`, `UrlImportScreen.kt:84` (URL-Beispiel als String `url_import_placeholder`),
  `$U/settings/SettingsScreen.kt:927` („Emoji"), `$U/components/AllergenWarningBanner.kt:37`,
  `$U/components/BarcodeScanner.kt:91` (→ vorhandenen Schließen-String suchen, sonst `common_close` „Schließen").
  Akzeptanz: `G-S` über `$U` (ohne `widget/`) zeigt nur noch erlaubte Literale und die zwei S22-Stellen.
- [x] **S22 Plurals** — Dateien: `strings.xml`, `ReceiptDetailScreen.kt` (≈ Z.202), `RecipeListScreen.kt` (≈ Z.557).
  `<plurals name="receipt_item_count">` one „%1$d Artikel" / other „%1$d Artikel";
  `<plurals name="recipes_unclassified_count">` one „%1$d Rezept ist noch nicht kategorisiert." / other
  „%1$d Rezepte sind noch nicht kategorisiert."; Aufruf über `pluralStringResource(id, count, count)`. Akzeptanz:
  `grep -rn 'pluralStringResource' $U | wc -l` = 2.
- [x] **S23 `UiText` für ViewModel-Meldungen** — Dateien: neu `$U/common/UiText.kt`, neu `$T/UiTextTest.kt`, die 6
  ViewModels und ihre Screens, `strings.xml`. `sealed interface UiText { data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText; data class Raw(val value: String) : UiText }`
  plus `@Composable fun UiText.asString(): String` und `fun UiText.asString(context: Context): String`. Umstellen
  (Feldtyp `String` → `UiText`, Screen ruft `.asString()` bzw. vor `showSnackbar` `.asString(context)` auf):
  `RecipeDetailViewModel` — `_classifyState`/`_nutritionCalcState` (`Pair<Boolean, UiText?>`), Z.192/368:
  `e.message?.let(UiText::Raw) ?: UiText.Res(R.string.…)`; `_snackbarMessage: MutableSharedFlow<UiText>`, Z.209
  (`UiText.Res(R.string.recipe_added_to_list, listOf(listName))`, Text „Zur „%1$s” hinzugefügt" zeichengenau wie im
  Code), Z.285. `ReceiptScanUiState.Error(message: UiText)` Z.96/177; Z.79 (`IllegalStateException`-Text, wird nur
  geworfen) bleibt. `ReconcileState.Error(message: UiText)` Z.65. `AiGenerateStatus.Error`/`AiRemixStatus.Error`
  (`message: UiText`) Z.104/115 nach dem `e.message?.let(UiText::Raw) ?: …`-Muster; die geworfenen Exception-Texte
  Z.101/112 werden durch eine private Exception-Klasse ohne Text ersetzt, die im `catch` auf
  `UiText.Res(R.string.ai_error_no_recipe)` abgebildet wird. `ShoppingListViewModel.kt:402` (`name = "Barcode: … (nicht gefunden)"`
  ist ein Entity-Feld): bleibt, im Abschnitt „Abschluss" als dokumentierte Ausnahme nennen. `UiState.Error(message: String)`
  in `ui/common/UiState.kt` bleibt unverändert. Zugriffe auf `uiState.classifyError`/`nutritionError` im
  Screen entsprechend anpassen. Test: `UiText.Raw("x")`-Gleichheit und `Res`-Gleichheit mit Argumenten (reine
  Datenklassen, kein Android nötig). Akzeptanz: `ArchitectureRulesTest` grün;
  `grep -rnE '"[^"]*(fehlgeschlagen|hinzugefügt|Unbekannter Fehler|konnte nicht)[^"]*"' $U --include='*ViewModel.kt'`
  zeigt höchstens die Zeile Z.79 aus `ReceiptScanViewModel`.

### Block F — Semantik und Touch-Targets (Befund 11)
- [x] **S24 Rollen für klickbare Zeilen** — Dateien: Treffer von `grep -rn '\.clickable' $U | grep -v 'receipts/\|widget/'`.
  Zuordnung:
  - **Checkbox-Zeilen → `Modifier.toggleable(value = <checked>, role = Role.Checkbox, onValueChange = { <bisheriger onClick> })`**
    und die enthaltene `Checkbox` auf `onCheckedChange = null`: `RecipeListScreen.kt:509`, `WeekplanScreen.kt:1133`,
    `RecipeCookScreen.kt:118, 397, 644`. Enthält die Zeile keine `Checkbox`/keinen eindeutigen Bool-Zustand: stattdessen
    `clickable(role = Role.Button)`.
  - **Auswahl aus Liste → `Modifier.selectable(selected = …, role = Role.RadioButton, onClick = …)`**, enthaltener
    `RadioButton` auf `onClick = null`: `WeekplanScreen.kt:1086`.
  - **Auf-/Zuklappen → `clickable(role = Role.Button, onClickLabel = stringResource(if (expanded) R.string.common_collapse else R.string.common_expand))`**
    (neue Strings „Einklappen"/„Ausklappen", vorhandene gleichlautende wiederverwenden): `ShoppingListScreen.kt:1343`,
    `RecipeCookScreen.kt:317`, `SettingsScreen.kt:419`, `WeekplanScreen.kt:1199`.
  - **Alle übrigen → `role = Role.Button` ergänzen**, sonst unverändert (auch bei `indication = null`):
    `ShoppingListScreen.kt:237, 455, 756, 927`, `StoreListScreen.kt:463`, `RecipeDetailScreen.kt:738`,
    `WeekplanScreen.kt:849, 891, 975`, `SettingsScreen.kt:677`.
  - **Unverändert:** `WeekplanScreen.kt:445` (Scrim zum Schließen), `SettingsScreen.kt:998` (folgt in S25).
  Akzeptanz: `grep -rn '\.clickable' $U | grep -v 'receipts/\|widget/\|role = \|import '` zeigt nur die zwei unveränderten
  Stellen (mehrzeilige `clickable(`-Aufrufe per Sichtprüfung der Folgezeilen werten). Prüfliste (Gerät, TalkBack):
  Zutaten-/Schritt-Zeilen der Kochansicht melden „Kontrollkästchen, aktiviert/nicht aktiviert".
- [x] **S25 Touch-Targets und Marker-Farbwahl** — Dateien: `$U/weekplan/WeekplanScreen.kt`, `$U/settings/SettingsScreen.kt`.
  Slot-Chip (≈ Z.849): vor `clickable` `Modifier.minimumInteractiveComponentSize()` einfügen (nur im klickbaren Zweig).
  Marker-Farbkreise (≈ Z.980–1000): durch `ColorSwatchPicker` ersetzen — `swatches = dayMarkerColors.mapIndexed { i, hex -> ColorSwatch(hex, stringResource(R.string.settings_marker_color_n, i + 1), parseMarkerColor(hex)) }`,
  `selectedKey = color`, `onSelect = { color = it }`; String `settings_marker_color_n` „Farbe %1$d".
  `dayMarkerColors` und `parseMarkerColor` bleiben unverändert. Akzeptanz: `grep -c 'ColorSwatchPicker(' $U/settings/SettingsScreen.kt` = 2;
  `grep -n 'minimumInteractiveComponentSize' $U/weekplan/WeekplanScreen.kt` ≥ 1 Treffer. Prüfliste (Gerät): Slot-Chip
  lässt sich sicher treffen, Tageszeile wird nicht sichtbar höher als gewünscht (sonst melden); Marker-Chips in hell und
  dunkel lesbar (Befund 22).

### Block G — Zustand erhalten (Befund 8)
Regel für alle drei Schritte: `remember { mutableStateOf(X) }` → `rememberSaveable { mutableStateOf(X) }`, wenn der Typ
`Boolean`, `Int`, `Long`, `Float`, `String` oder deren nullable Variante ist (Dialog-/Sheet-Sichtbarkeit, aufgeklappt,
Eingabetext, gewählte ID). **Nicht umstellen:** Entities/Datenklassen/Listen/Sets/Maps/`Pair`, `TextFieldValue`,
`MutableInteractionSource`, `FocusRequester`, Zustände mit Schlüssel (`remember(key) { … }`), `mutableStateListOf`,
Zustände für Kamera/Bitmap/Uri, sowie Vorschlagslisten. `ui/receipts/` und `ui/widget/` bleiben unberührt (Phase 3).
Akzeptanz je Schritt: für die genannten Dateien listet
`grep -n 'remember { mutableStateOf' <Datei>` nur noch Zeilen der „Nicht umstellen"-Kategorien; die Liste mit je einem
Stichwort zur Begründung in die Commit-Beschreibung.
- [x] **S26** — `$U/shopping/ShoppingListScreen.kt`, `$U/stores/StoreListScreen.kt`.
- [x] **S27** — `$U/weekplan/WeekplanScreen.kt`, `$U/weekplan/WeekplanRecipePickerScreen.kt`, `$U/settings/SettingsScreen.kt`.
- [ ] **S28** — `$U/recipes/*.kt`, `$U/ai/*.kt`, `$U/components/*.kt`. Danach `grep -rn 'rememberSaveable' $U | wc -l`
  unter „Abschluss" notieren. Prüfliste (Gerät, nach S26–S28): offener Dialog/Sheet und getippter Text überleben eine
  Drehung in Einkaufsliste, Wochenplan, Einstellungen, Rezeptdetail.

### Block H — Screen/Content für kleine Screens (Befund 12)
Muster: `XScreen(…, viewModel = hiltViewModel())` sammelt State und reicht ihn an
`@Composable internal fun XContent(state…, <Callbacks>, modifier: Modifier = Modifier)` weiter; `XContent` kennt kein
ViewModel. Der gesamte bisherige UI-Baum wandert unverändert nach `XContent`, `viewModel::foo` wird zu einem
Callback-Parameter. Je `XContent` eine `@ThemePreviews`-Funktion mit festen Beispieldaten in `PreviewSurface`.
- [ ] **S29** — `$U/onboarding/OnboardingScreen.kt`, `$U/recipes/UrlImportScreen.kt`. Akzeptanz je Datei:
  `grep -c 'viewModel' ` nur innerhalb der `XScreen`-Funktion; `grep -c '@ThemePreviews'` ≥ 1.
- [ ] **S30** — `$U/stats/StatsScreen.kt`, `$U/weekplan/WeekplanRecipePickerScreen.kt`; zusätzlich Preview für das
  vorhandene `…Content` in `$U/ai/AiRemixScreen.kt`. Akzeptanz wie S29.

### Block I — Absichern und dokumentieren
- [ ] **S31 `UiConventionsTest`** — Datei neu: `$T/UiConventionsTest.kt` (Aufbau wie `ArchitectureRulesTest`, liest
  `src/main/kotlin/com/helga/android` zeilenweise; `ui/widget/` ausgenommen). Drei Tests: (1) keine Zeile enthält
  `collectAsState(`; (2) keine Zeile enthält `Icons.Filled.ArrowBack` oder `Icons.Default.ArrowBack`; (3) Wort-Literale:
  auf Zeilen, die zu `(Text\(\s*|(text|label|title|placeholder|contentDescription|message) = )"` passen, das erste
  String-Literal nehmen, `\$\{[^}]*\}` und `\$[A-Za-z_][A-Za-z0-9_]*` entfernen; bleiben ≥ 2 aufeinanderfolgende
  Buchstaben (`[A-Za-zÄÖÜäöüß]{2,}`), ist es ein Verstoß — Ausnahmen als Konstante im Test: `"sync-rotate"`, `"angle"`.
  Schlägt (3) auf einer nach R-S erlaubten Zeile fehl (z. B. verschachtelte Anführungszeichen), den Ausdruck im
  Produktivcode in eine lokale `val` ziehen statt die Regel aufzuweichen. Akzeptanz: Test grün.
- [ ] **S32 Guideline, Index, Pläne** — Dateien: `.claude/guidelines/ux-accessibility.md` (neuer Abschnitt „13. Theme,
  Insets, Bausteine", max. 30 Zeilen: Farben nur über Rollen, Schemata in `theme/Color.kt`, `ACCENT_SYSTEM`; Root-Padding
  am NavHost, kein `bottomPadding`-Durchreichen, `imePadding` nur an der Wurzel; `LoadingIndicator`, `ColorSwatchPicker`,
  `@ThemePreviews`, `UiText`, `formatEuro` verwenden; Verweis auf `UiConventionsTest`),
  `.claude/guidelines/compose-performance.md` §8 (Hinweis: per Test abgesichert), `.claude/features/README.md`
  („Bereichsübergreifende Befunde": Zeile `items()` ohne `key` auf 0 korrigieren, neue Zeile „UI-Konventionen — Phase 2
  umgesetzt"), je ein Vermerk „Phase 2 umgesetzt" in den Bereichs-`plan.md` (rezepte, wochenplan, einkaufsliste,
  bons-kosten, ki, statistik, maerkte, einstellungen), `.claude/phasen/README.md` (Status Phase 2 → umgesetzt).
  In `bons-kosten/plan.md` zusätzlich vermerken: Leer-/Fehlerzustände und Layout der Bon-Screens folgen in Phase 3.

## Nicht-Ziele
- Rezept-Grid, Zweispalter Liste/Detail, sonstige adaptive Screen-Layouts (F2).
- `ui/receipts/*` über das Mechanische hinaus: Empty-States, Fehlerdarstellung, Diagramme, neue Screens, Review-Layout,
  `rememberSaveable`, Rollen — alles Phase 3.
- Aufteilen der Groß-Screens (`ShoppingListScreen`, `WeekplanScreen`, `SettingsScreen`, `RecipeDetailScreen`) und
  `Screen`/`Content`-Teilung für sie.
- Eigene Typografie/Schriftart, Spacing-Token-System, `TopAppBar`-Scrollverhalten, eigene Navigationsübergänge,
  `animateItem`.
- Vereinheitlichung von Snackbar/Fehlerzuständen und `UiState<T>` über alle Screens (Befund 18).
- Ersetzen der eigenen Wischgeste `SwipeableShoppingItem`, Landscape-Split der Kochansicht, Meal-Slot-Farben,
  Marker-Palette (persistierte Hex-Werte).
- `ui/widget/` (Glance) und das `:wear`-Modul.
- Room, Sync, DTOs, `server/`.

## Abschluss
- [x] Baseline aus S0 (Testanzahl, Zählwerte): 143 Tests, 0 Fehler, V grün; `collectAsState()` 20, `CircularProgressIndicator(` 21, `rememberSaveable` 0, `@Preview` 0 (alle wie erwartet)
- [ ] Bereichs-`plan.md` und Feature-Index aktualisiert (S32)
- [ ] Verifikation grün (Befehl `V` + Ausgabe der letzten Ausführung festgehalten)
- [ ] Gemeldete Ausnahmen (S5 Shapes außerhalb Composable, S23 Barcode-Dummy, weitere):
- [ ] Gesammelte „Prüfliste (Gerät)" aus S1–S4, S6–S11, S24, S25, S28 für den Nutzer (von Stufe B nicht abgehakt):
