package com.helga.android.ui.weekplan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.helga.android.R
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.local.entity.ShoppingListEntity
import com.helga.android.data.local.entity.WeekplanConstraintsEntity
import com.helga.android.data.local.entity.WeekplanDayEntity
import com.helga.android.data.local.entity.WeekplanExtraEntity
import com.helga.android.data.local.entity.WeekplanRecipeEntity
import com.helga.android.data.remote.dto.WeekplanAssignmentDto
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekplanScreen(
    bottomPadding: Dp = 0.dp,
    onAddRecipeForDay: (dayId: String) -> Unit,
    onNavigateToRecipe: (recipeId: String) -> Unit,
    viewModel: WeekplanViewModel = hiltViewModel(),
) {
    val days by viewModel.days.collectAsStateWithLifecycle()
    val weekplanRecipes by viewModel.weekplanRecipes.collectAsStateWithLifecycle()
    val weekplanExtras by viewModel.weekplanExtras.collectAsStateWithLifecycle()
    val allRecipes by viewModel.allRecipes.collectAsStateWithLifecycle()
    val shoppingLists by viewModel.shoppingLists.collectAsStateWithLifecycle()
    val selectedDayId by viewModel.selectedDayId.collectAsStateWithLifecycle()
    val daySummaries by viewModel.daySummaries.collectAsStateWithLifecycle()
    val constraints by viewModel.constraints.collectAsStateWithLifecycle()
    val generateStatus by viewModel.generateStatus.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()

    var exportPicker by remember { mutableStateOf<String?>(null) }
    var constraintsEditorVisible by remember { mutableStateOf(false) }

    val recipeById: (String) -> RecipeEntity? = { id -> allRecipes.find { it.id == id } }

    val startDate = remember(days) {
        days.firstOrNull()?.planDate
            ?: LocalDate.now().with(DayOfWeek.MONDAY).format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    LaunchedEffect(Unit) { viewModel.ensureWeek() }

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

    if (constraintsEditorVisible) {
        ConstraintsEditorSheet(
            constraints = constraints,
            onSave = { maxMeat, minVeg, maxRepeat ->
                viewModel.saveConstraints(maxMeat, minVeg, maxRepeat)
                constraintsEditorVisible = false
            },
            onDismiss = { constraintsEditorVisible = false },
        )
    }

    when (val status = generateStatus) {
        is WeekplanGenerateStatus.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        is WeekplanGenerateStatus.Proposal -> {
            ProposalSheet(
                assignments = status.assignments,
                recipeNameById = { id -> allRecipes.find { it.id == id }?.name ?: id },
                onAccept = { viewModel.applyProposal(status.assignments) },
                onDiscard = { viewModel.discardProposal() },
            )
        }
        is WeekplanGenerateStatus.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.discardProposal() },
                title = { Text(stringResource(R.string.weekplan_ai_generate)) },
                text = { Text(status.message) },
                confirmButton = {
                    TextButton(onClick = { viewModel.discardProposal() }) {
                        Text(stringResource(R.string.recipe_delete_confirm_cancel))
                    }
                },
            )
        }
        else -> Unit
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.weekplan_title)) },
                actions = {
                    IconButton(onClick = { constraintsEditorVisible = true }) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = stringResource(R.string.weekplan_constraints_title),
                        )
                    }
                    IconButton(
                        onClick = { viewModel.generateWeekplan(startDate) },
                        enabled = generateStatus !is WeekplanGenerateStatus.Loading,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = stringResource(R.string.weekplan_ai_generate),
                        )
                    }
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
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp + bottomPadding),
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
                    recipeById = recipeById,
                    serverUrl = serverUrl,
                    onSelect = { viewModel.selectDay(day.id) },
                    onNoteChange = { note -> viewModel.updateNote(day.id, note) },
                    onAddRecipe = { onAddRecipeForDay(day.id) },
                    onRemoveRecipe = viewModel::removeRecipe,
                    onAddExtra = { text -> viewModel.addExtra(day.id, text) },
                    onRemoveExtra = viewModel::removeExtra,
                    onExport = { exportPicker = day.id },
                    onNavigateToRecipe = onNavigateToRecipe,
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
    recipeById: (String) -> RecipeEntity?,
    serverUrl: String,
    onSelect: () -> Unit,
    onNoteChange: (String) -> Unit,
    onAddRecipe: () -> Unit,
    onRemoveRecipe: (WeekplanRecipeEntity) -> Unit,
    onAddExtra: (String) -> Unit,
    onRemoveExtra: (WeekplanExtraEntity) -> Unit,
    onExport: () -> Unit,
    onNavigateToRecipe: (String) -> Unit,
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

                weekplanRecipes.forEach { entry ->
                    val recipe = recipeById(entry.recipeId)
                    val imageUrl = remember(recipe?.imagePath, serverUrl) {
                        val path = recipe?.imagePath.orEmpty()
                        if (path.isNotBlank() && serverUrl.isNotBlank())
                            "${serverUrl.trimEnd('/')}/api/images/$path"
                        else null
                    }
                    RecipeItemRow(
                        name = recipe?.name?.ifBlank { recipe.slug } ?: entry.recipeId,
                        imageUrl = imageUrl,
                        onNavigate = { onNavigateToRecipe(entry.recipeId) },
                        onRemove = { onRemoveRecipe(entry) },
                    )
                }

                weekplanExtras.forEach { extra ->
                    ExtraChip(
                        text = extra.itemText,
                        onRemove = { onRemoveExtra(extra) },
                    )
                }

                if (weekplanRecipes.isNotEmpty() || weekplanExtras.isNotEmpty()) {
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
private fun RecipeItemRow(
    name: String,
    imageUrl: String?,
    onNavigate: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onNavigate),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Restaurant,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            text = name,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConstraintsEditorSheet(
    constraints: WeekplanConstraintsEntity,
    onSave: (maxMeat: Int, minVeg: Int, maxRepeat: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var maxMeat by remember(constraints.maxMeatPerWeek) { mutableFloatStateOf(constraints.maxMeatPerWeek.toFloat()) }
    var minVeg by remember(constraints.minVegetarianPerWeek) { mutableFloatStateOf(constraints.minVegetarianPerWeek.toFloat()) }
    var maxRepeat by remember(constraints.maxRepeatDays) { mutableFloatStateOf(constraints.maxRepeatDays.toFloat()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.weekplan_constraints_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(16.dp))

            Text(
                text = "${stringResource(R.string.weekplan_max_meat)}: ${maxMeat.toInt()}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = maxMeat,
                onValueChange = { maxMeat = it },
                valueRange = 0f..7f,
                steps = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            Text(
                text = "${stringResource(R.string.weekplan_min_veg)}: ${minVeg.toInt()}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = minVeg,
                onValueChange = { minVeg = it },
                valueRange = 0f..7f,
                steps = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            Text(
                text = "${stringResource(R.string.weekplan_max_repeat)}: ${maxRepeat.toInt()}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = maxRepeat,
                onValueChange = { maxRepeat = it },
                valueRange = 7f..28f,
                steps = 20,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { onSave(maxMeat.toInt(), minVeg.toInt(), maxRepeat.toInt()) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.weekplan_constraints_save))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProposalSheet(
    assignments: List<WeekplanAssignmentDto>,
    recipeNameById: (String) -> String,
    onAccept: () -> Unit,
    onDiscard: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDiscard,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.weekplan_proposal_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(assignments, key = { it.date }) { assignment ->
                    ListItem(
                        headlineContent = {
                            val name = assignment.recipeName.ifBlank { recipeNameById(assignment.recipeId) }
                            Text(name)
                        },
                        supportingContent = { Text(assignment.date) },
                    )
                    HorizontalDivider()
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onDiscard,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.weekplan_proposal_discard))
                }
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.weekplan_proposal_accept))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
