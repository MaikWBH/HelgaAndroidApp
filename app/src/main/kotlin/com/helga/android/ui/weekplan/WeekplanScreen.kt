package com.helga.android.ui.weekplan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.helga.android.R
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.local.entity.ShoppingListEntity
import com.helga.android.data.local.entity.WeekplanDayEntity
import com.helga.android.data.local.entity.WeekplanExtraEntity
import com.helga.android.data.local.entity.WeekplanRecipeEntity
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekplanScreen(
    onBack: () -> Unit,
    viewModel: WeekplanViewModel = hiltViewModel(),
) {
    val days by viewModel.days.collectAsStateWithLifecycle()
    val weekplanRecipes by viewModel.weekplanRecipes.collectAsStateWithLifecycle()
    val weekplanExtras by viewModel.weekplanExtras.collectAsStateWithLifecycle()
    val allRecipes by viewModel.allRecipes.collectAsStateWithLifecycle()
    val shoppingLists by viewModel.shoppingLists.collectAsStateWithLifecycle()
    val selectedDayId by viewModel.selectedDayId.collectAsStateWithLifecycle()
    val daySummaries by viewModel.daySummaries.collectAsStateWithLifecycle()

    var recipePicker by remember { mutableStateOf<String?>(null) }  // dayId being picked for
    var exportPicker by remember { mutableStateOf<String?>(null) }  // dayId or "all"
    var weekExportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.ensureWeek() }

    if (recipePicker != null) {
        RecipePickerSheet(
            dayId = recipePicker!!,
            recipes = allRecipes,
            onPick = { recipeId ->
                viewModel.addRecipe(recipePicker!!, recipeId)
                recipePicker = null
            },
            onDismiss = { recipePicker = null },
        )
    }

    if (exportPicker != null) {
        ShoppingListPickerDialog(
            lists = shoppingLists,
            onPick = { listId ->
                val dayId = exportPicker!!
                if (dayId == "all") viewModel.exportWeekToShoppingList(listId)
                else viewModel.exportToShoppingList(dayId, listId)
                exportPicker = null
            },
            onDismiss = { exportPicker = null },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.weekplan_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.recipe_detail_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { exportPicker = "all" }) {
                        Icon(
                            imageVector = Icons.Filled.ShoppingCart,
                            contentDescription = stringResource(R.string.weekplan_export_week),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(days, key = { it.id }) { day ->
                val isSelected = day.id == selectedDayId
                val dayRecipes = if (isSelected) weekplanRecipes else emptyList()
                val dayExtras = if (isSelected) weekplanExtras else emptyList()
                val summary = daySummaries[day.id]

                DayCard(
                    day = day,
                    isSelected = isSelected,
                    weekplanRecipes = dayRecipes,
                    weekplanExtras = dayExtras,
                    recipeCount = summary?.recipeCount ?: 0,
                    extraCount = summary?.extraCount ?: 0,
                    recipeNameById = { id -> allRecipes.find { it.id == id }?.name ?: id },
                    onSelect = { viewModel.selectDay(day.id) },
                    onNoteChange = { note -> viewModel.updateNote(day.id, note) },
                    onAddRecipe = { recipePicker = day.id },
                    onRemoveRecipe = viewModel::removeRecipe,
                    onAddExtra = { text -> viewModel.addExtra(day.id, text) },
                    onRemoveExtra = viewModel::removeExtra,
                    onExport = { exportPicker = day.id },
                )
            }
        }
    }
}

@Composable
private fun DayCard(
    day: WeekplanDayEntity,
    isSelected: Boolean,
    weekplanRecipes: List<WeekplanRecipeEntity>,
    weekplanExtras: List<WeekplanExtraEntity>,
    recipeCount: Int,
    extraCount: Int,
    recipeNameById: (String) -> String,
    onSelect: () -> Unit,
    onNoteChange: (String) -> Unit,
    onAddRecipe: () -> Unit,
    onRemoveRecipe: (WeekplanRecipeEntity) -> Unit,
    onAddExtra: (String) -> Unit,
    onRemoveExtra: (WeekplanExtraEntity) -> Unit,
    onExport: () -> Unit,
) {
    val date = remember(day.planDate) {
        runCatching { LocalDate.parse(day.planDate, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
    }
    val dayLabel = remember(date) {
        date?.dayOfWeek?.getDisplayName(TextStyle.FULL, Locale.getDefault())
            ?.replaceFirstChar { it.uppercase() } ?: day.planDate
    }
    val dateLabel = remember(date) {
        date?.format(DateTimeFormatter.ofPattern("dd.MM.")) ?: ""
    }

    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = if (isSelected) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ) else CardDefaults.cardColors(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = dayLabel, style = MaterialTheme.typography.titleMedium)
                    if (dateLabel.isNotBlank()) {
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (isSelected) {
                    IconButton(onClick = onExport) {
                        Icon(
                            imageVector = Icons.Filled.ShoppingCart,
                            contentDescription = stringResource(R.string.weekplan_export_day),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (isSelected) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                if (weekplanRecipes.isNotEmpty() || weekplanExtras.isNotEmpty()) {
                    weekplanRecipes.forEach { entry ->
                        RecipeChip(
                            name = recipeNameById(entry.recipeId),
                            onRemove = { onRemoveRecipe(entry) },
                        )
                    }
                    weekplanExtras.forEach { extra ->
                        ExtraChip(
                            text = extra.itemText,
                            onRemove = { onRemoveExtra(extra) },
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }

                var noteText by remember(day.id) { mutableStateOf(day.note) }
                var extraInput by remember { mutableStateOf("") }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text(stringResource(R.string.weekplan_note_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { onNoteChange(noteText) }
                    ),
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = extraInput,
                        onValueChange = { extraInput = it },
                        label = { Text(stringResource(R.string.weekplan_extra_hint)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            onAddExtra(extraInput)
                            extraInput = ""
                        }),
                    )
                    IconButton(
                        onClick = {
                            onAddExtra(extraInput)
                            extraInput = ""
                        },
                        enabled = extraInput.isNotBlank(),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                    }
                }

                Spacer(Modifier.height(4.dp))

                TextButton(
                    onClick = onAddRecipe,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.weekplan_add_recipe))
                }
            } else if (recipeCount > 0 || extraCount > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        if (recipeCount > 0) append("$recipeCount Rezepte")
                        if (extraCount > 0) {
                            if (isNotEmpty()) append(", ")
                            append("$extraCount Extras")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RecipeChip(name: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "• $name",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.weekplan_remove_recipe),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExtraChip(text: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "– $text",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipePickerSheet(
    dayId: String,
    recipes: List<RecipeEntity>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Text(
            text = stringResource(R.string.weekplan_pick_recipe),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        HorizontalDivider()
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            items(recipes, key = { it.id }) { recipe ->
                ListItem(
                    headlineContent = { Text(recipe.name.ifBlank { recipe.slug }) },
                    modifier = Modifier.padding(horizontal = 4.dp),
                    trailingContent = {
                        IconButton(onClick = {
                            scope.launch { sheetState.hide() }.invokeOnCompletion { onPick(recipe.id) }
                        }) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ShoppingListPickerDialog(
    lists: List<ShoppingListEntity>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.weekplan_pick_list)) },
        text = {
            if (lists.isEmpty()) {
                Text(stringResource(R.string.weekplan_no_lists))
            } else {
                Column {
                    lists.forEach { list ->
                        TextButton(
                            onClick = { onPick(list.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(list.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.recipe_delete_confirm_cancel))
            }
        },
    )
}
