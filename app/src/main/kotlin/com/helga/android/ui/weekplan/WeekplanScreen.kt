package com.helga.android.ui.weekplan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.helga.android.R
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.remote.dto.WeekplanAssignmentDto
import com.helga.android.data.util.ImageUrls
import com.helga.android.data.local.entity.ShoppingListEntity
import com.helga.android.data.local.entity.WeekplanConstraintsEntity
import com.helga.android.data.local.entity.WeekplanDayEntity
import com.helga.android.data.local.entity.WeekplanExtraEntity
import com.helga.android.data.local.entity.WeekplanRecipeEntity
import com.helga.android.data.local.entity.WeekplanTemplateEntity
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
    val weekRecipes by viewModel.weekRecipes.collectAsStateWithLifecycle()
    val weekExtras by viewModel.weekExtras.collectAsStateWithLifecycle()
    val constraints by viewModel.constraints.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val weekOffset by viewModel.weekOffset.collectAsStateWithLifecycle()
    val weekLabel by viewModel.weekLabel.collectAsStateWithLifecycle()
    val generateStatus by viewModel.generateStatus.collectAsStateWithLifecycle()
    val feedbackMap by viewModel.feedbackForSelectedDay.collectAsStateWithLifecycle()
    val weekBalance by viewModel.weekBalance.collectAsStateWithLifecycle()
    var exportPicker by remember { mutableStateOf<String?>(null) }
    var constraintsEditorVisible by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val recipeById: (String) -> RecipeEntity? = { id -> allRecipes[id] }

    LaunchedEffect(weekOffset) { viewModel.ensureWeek() }

    LaunchedEffect(Unit) {
        viewModel.exportEvent.collect {
            val result = snackbarHostState.showSnackbar(
                message = "Wochenplan erstellt",
                actionLabel = "Zur Einkaufsliste",
            )
            if (result == SnackbarResult.ActionPerformed) {
                exportPicker = "all"
            }
        }
    }

    if (exportPicker != null) {
        ShoppingListPickerDialog(
            lists = shoppingLists,
            onPick = { listId, servings ->
                val dayId = exportPicker!!
                if (dayId == "all") viewModel.exportWeekToShoppingList(listId, servings)
                else viewModel.exportToShoppingList(dayId, listId, servings)
                exportPicker = null
            },
            onDismiss = { exportPicker = null },
        )
    }

    if (constraintsEditorVisible) {
        ConstraintsEditorSheet(
            constraints = constraints,
            allergies = emptyList(), // TODO: load from preferences
            onSave = { maxMeat, maxFish, minVeg, maxRepeat, maxKcal, minScore, prefOrganic, excludeAllergies ->
                viewModel.saveConstraints(maxMeat, maxFish, minVeg, maxRepeat, maxKcal, minScore, prefOrganic, excludeAllergies)
                constraintsEditorVisible = false
            },
            onDismiss = { constraintsEditorVisible = false },
        )
    }

    val proposal = generateStatus as? WeekplanGenerateStatus.Proposal
    if (proposal != null) {
        ProposalSheet(
            assignments = proposal.assignments,
            warnings = proposal.warnings,
            allRecipes = allRecipes,
            serverUrl = serverUrl,
            onAccept = { viewModel.applyProposal(proposal.assignments) },
            onDismiss = viewModel::discardProposal,
            onRegenerateDay = { index -> viewModel.regenerateProposalDay(index) },
        )
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
                    IconButton(onClick = { exportPicker = "all" }) {
                        Icon(
                            imageVector = Icons.Filled.ShoppingCart,
                            contentDescription = stringResource(R.string.weekplan_export_week),
                        )
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.weekplan_more),
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.weekplan_repeat_last_week)) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.repeatLastWeek()
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp + bottomPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "week_nav") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = viewModel::prevWeek) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.weekplan_prev_week),
                            )
                        }
                        Text(
                            text = weekLabel,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center,
                        )
                        if (weekOffset != 0) {
                            TextButton(onClick = viewModel::goToCurrentWeek) {
                                Text(stringResource(R.string.weekplan_today))
                            }
                        }
                        IconButton(onClick = viewModel::nextWeek) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = stringResource(R.string.weekplan_next_week),
                            )
                        }
                    }
                }
                item(key = "week_balance") {
                    val total = weekBalance.meat + weekBalance.fish + weekBalance.veg + weekBalance.other
                    if (total > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("🥩 ${weekBalance.meat}", style = MaterialTheme.typography.labelMedium)
                            Text("🐟 ${weekBalance.fish}", style = MaterialTheme.typography.labelMedium)
                            Text("🥬 ${weekBalance.veg}", style = MaterialTheme.typography.labelMedium)
                            if (weekBalance.other > 0) {
                                Text(
                                    text = "❓ ${weekBalance.other}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (weekBalance.other > 2)
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
                items(days, key = { it.id }) { day ->
                    val isSelected = day.id == selectedDayId
                    // Im ausgewählten Zustand die live editierbaren Listen, sonst die Wochen-Übersicht
                    val dayRecipes = if (isSelected) weekplanRecipes else (weekRecipes[day.id] ?: emptyList())
                    val dayExtras = if (isSelected) weekplanExtras else (weekExtras[day.id] ?: emptyList())
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
                        onSuggestExtra = viewModel::suggestItems,
                        onExport = { exportPicker = day.id },
                        onNavigateToRecipe = onNavigateToRecipe,
                        onToggleQuick = { viewModel.toggleQuickDay(day) },
                        onToggleGuest = { viewModel.toggleGuestDay(day) },
                        onRegenerateDay = { viewModel.regenerateDay(day.id) },
                        feedbackMap = feedbackMap,
                        onFeedback = { recipeId, liked -> viewModel.setFeedback(recipeId, day.planDate, liked) },
                    )
                }
            }

            if (generateStatus is WeekplanGenerateStatus.Loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.weekplan_ai_generating),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
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
    onSuggestExtra: suspend (String) -> List<String>,
    onExport: () -> Unit,
    onNavigateToRecipe: (String) -> Unit,
    onToggleQuick: () -> Unit,
    onToggleGuest: () -> Unit,
    onRegenerateDay: () -> Unit,
    feedbackMap: Map<String, Int>,
    onFeedback: (recipeId: String, liked: Int) -> Unit,
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(text = dayLabel, style = MaterialTheme.typography.titleMedium)
                        if (day.isQuickDay == 1) {
                            Text("⚡", style = MaterialTheme.typography.bodySmall)
                        }
                        if (day.isGuestDay == 1) {
                            Text("👥", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (dateLabel.isNotBlank()) {
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (isSelected) {
                    IconButton(onClick = onToggleQuick) {
                        Text(
                            text = "⚡",
                            color = if (day.isQuickDay == 1) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                    }
                    IconButton(onClick = onToggleGuest) {
                        Text(
                            text = "👥",
                            color = if (day.isGuestDay == 1) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                    }
                    IconButton(onClick = onExport) {
                        Icon(
                            imageVector = Icons.Filled.ShoppingCart,
                            contentDescription = stringResource(R.string.weekplan_export_day),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = onRegenerateDay) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.weekplan_regenerate_day),
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
                    val imageUrl = remember(recipe?.localImageUri, recipe?.imagePath, serverUrl) {
                        val localUri = recipe?.localImageUri.orEmpty()
                        val path = recipe?.imagePath.orEmpty()
                        when {
                            localUri.isNotBlank() -> localUri
                            path.isNotBlank() && serverUrl.isNotBlank() ->
                                ImageUrls.serverImageUrl(serverUrl, path)
                            else -> null
                        }
                    }
                    RecipeItemRow(
                        name = recipe?.name?.ifBlank { recipe.slug } ?: entry.recipeId,
                        imageUrl = imageUrl,
                        liked = feedbackMap[entry.recipeId] ?: 0,
                        onNavigate = { onNavigateToRecipe(entry.recipeId) },
                        onRemove = { onRemoveRecipe(entry) },
                        onThumbUp = { onFeedback(entry.recipeId, 1) },
                        onThumbDown = { onFeedback(entry.recipeId, -1) },
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

                DisposableEffect(day.id) {
                    onDispose { onNoteChange(noteText) }
                }

                var extraInput by remember { mutableStateOf("") }
                var extraSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }

                LaunchedEffect(extraInput) {
                    if (extraInput.length >= 2) {
                        kotlinx.coroutines.delay(300)
                        extraSuggestions = onSuggestExtra(extraInput)
                    } else {
                        extraSuggestions = emptyList()
                    }
                }

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

                if (extraSuggestions.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(extraSuggestions) { suggestion ->
                            SuggestionChip(
                                onClick = {
                                    onAddExtra(suggestion)
                                    extraInput = ""
                                    extraSuggestions = emptyList()
                                },
                                label = { Text(suggestion) },
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }

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
                            extraSuggestions = emptyList()
                        }),
                    )
                    IconButton(
                        onClick = {
                            onAddExtra(extraInput)
                            extraInput = ""
                            extraSuggestions = emptyList()
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
            } else if (weekplanRecipes.isNotEmpty() || weekplanExtras.isNotEmpty() || day.note.isNotBlank()) {
                Spacer(Modifier.height(6.dp))

                weekplanRecipes.forEach { entry ->
                    val recipe = recipeById(entry.recipeId)
                    val imageUrl = remember(recipe?.localImageUri, recipe?.imagePath, serverUrl) {
                        val localUri = recipe?.localImageUri.orEmpty()
                        val path = recipe?.imagePath.orEmpty()
                        when {
                            localUri.isNotBlank() -> localUri
                            path.isNotBlank() && serverUrl.isNotBlank() ->
                                ImageUrls.serverImageUrl(serverUrl, path)
                            else -> null
                        }
                    }
                    DayPreviewRecipeRow(
                        name = recipe?.name?.ifBlank { recipe.slug } ?: entry.recipeId,
                        imageUrl = imageUrl,
                        onNavigate = { onNavigateToRecipe(entry.recipeId) },
                    )
                }

                weekplanExtras.forEach { extra ->
                    Text(
                        text = "– ${extra.itemText}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }

                if (day.note.isNotBlank()) {
                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("📝", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = day.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipeItemRow(
    name: String,
    imageUrl: String?,
    liked: Int,
    onNavigate: () -> Unit,
    onRemove: () -> Unit,
    onThumbUp: () -> Unit,
    onThumbDown: () -> Unit,
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
                .size(80.dp)
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onThumbUp,
                ) {
                    Text(
                        text = "👍",
                        color = if (liked == 1) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                }
                IconButton(
                    onClick = onThumbDown,
                ) {
                    Text(
                        text = "👎",
                        color = if (liked == -1) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                }
            }
        }
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
private fun DayPreviewRecipeRow(
    name: String,
    imageUrl: String?,
    onNavigate: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onNavigate),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp)),
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
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
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
    onPick: (String, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var servings by remember { mutableIntStateOf(2) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.weekplan_pick_list)) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.weekplan_servings), style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (servings > 1) servings-- }) {
                            Icon(Icons.Filled.Remove, contentDescription = null)
                        }
                        Text("$servings", style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { if (servings < 12) servings++ }) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (lists.isEmpty()) {
                    Text(stringResource(R.string.weekplan_no_lists))
                } else {
                    lists.forEach { list ->
                        TextButton(
                            onClick = { onPick(list.id, servings) },
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
    allergies: List<String>,
    onSave: (maxMeat: Int, maxFish: Int, minVeg: Int, maxRepeat: Int, maxKcal: Int, minScore: String, prefOrganic: Boolean, excludeAllergies: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var maxMeat by remember(constraints.maxMeatPerWeek) { mutableFloatStateOf(constraints.maxMeatPerWeek.toFloat()) }
    var maxFish by remember(constraints.maxFishPerWeek) { mutableFloatStateOf(constraints.maxFishPerWeek.toFloat()) }
    var minVeg by remember(constraints.minVegetarianPerWeek) { mutableFloatStateOf(constraints.minVegetarianPerWeek.toFloat()) }
    var maxRepeat by remember(constraints.maxRepeatDays) { mutableFloatStateOf(constraints.maxRepeatDays.toFloat()) }
    var maxKcal by remember(constraints.maxKcalPerPortion) { mutableFloatStateOf(constraints.maxKcalPerPortion.toFloat()) }
    var minNutriScore by remember(constraints.minNutriScore) { mutableStateOf(constraints.minNutriScore) }
    var preferOrganic by remember(constraints.preferOrganic) { mutableStateOf(constraints.preferOrganic == 1) }
    var selectedAllergens by remember(constraints.excludeAllergens) { mutableStateOf(emptyList<String>()) }

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
                text = "${stringResource(R.string.weekplan_max_fish)}: ${maxFish.toInt()}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = maxFish,
                onValueChange = { maxFish = it },
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

            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text(
                text = "🔥 Nährwert-Budgets",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))

            Text(
                text = "Max kcal/Portion: ${maxKcal.toInt()}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = maxKcal,
                onValueChange = { maxKcal = it },
                valueRange = 300f..1200f,
                steps = 18,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            Text(
                text = "Min Nutri-Score",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("c", "b", "a").forEach { score ->
                    Button(
                        onClick = { minNutriScore = score },
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = if (minNutriScore == score) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Text(score.uppercase())
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "🌿 Bio bevorzugt",
                    style = MaterialTheme.typography.bodyMedium,
                )
                androidx.compose.material3.Switch(
                    checked = preferOrganic,
                    onCheckedChange = { preferOrganic = it },
                )
            }
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { onSave(maxMeat.toInt(), maxFish.toInt(), minVeg.toInt(), maxRepeat.toInt(), maxKcal.toInt(), minNutriScore, preferOrganic, selectedAllergens) },
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
    warnings: List<String> = emptyList(),
    allRecipes: Map<String, RecipeEntity>,
    serverUrl: String,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    onRegenerateDay: (Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                text = stringResource(R.string.weekplan_proposal_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            warnings.forEach { warning ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }

            assignments.forEachIndexed { index, assignment ->
                val recipe = allRecipes[assignment.recipeId]
                val imageUrl = remember(recipe?.localImageUri, recipe?.imagePath, serverUrl) {
                    val localUri = recipe?.localImageUri.orEmpty()
                    val path = recipe?.imagePath.orEmpty()
                    when {
                        localUri.isNotBlank() -> localUri
                        path.isNotBlank() && serverUrl.isNotBlank() ->
                            ImageUrls.serverImageUrl(serverUrl, path)
                        else -> null
                    }
                }
                val proteinEmoji = when (recipe?.proteinType?.lowercase()) {
                    in listOf("fleisch", "meat", "geflügel", "poultry", "rind", "schwein") -> "🥩"
                    in listOf("fisch", "fish", "meeresfrüchte", "seafood") -> "🐟"
                    in listOf("vegetarisch", "vegetarian", "vegan") -> "🥬"
                    else -> ""
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (imageUrl != null) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = assignment.recipeName,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = assignment.date,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (proteinEmoji.isNotBlank()) {
                                Text(proteinEmoji, style = MaterialTheme.typography.labelSmall)
                            }
                            if (recipe != null && recipe.totalTime.isNotBlank()) {
                                Text(
                                    text = recipe.totalTime,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    IconButton(onClick = { onRegenerateDay(index) }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.weekplan_regenerate_day),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.weekplan_proposal_discard))
                }
                Button(onClick = onAccept) {
                    Text(stringResource(R.string.weekplan_proposal_accept))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateSheet(
    templates: List<WeekplanTemplateEntity>,
    onApply: (String) -> Unit,
    onSave: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var newTemplateName by remember { mutableStateOf("") }

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
                text = stringResource(R.string.weekplan_templates),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))

            // Vorhandene Templates
            if (templates.isEmpty()) {
                Text(
                    text = stringResource(R.string.weekplan_no_templates),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                templates.forEach { template ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onApply(template.id) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = template.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onDelete(template.id) }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // Neue Vorlage speichern
            Text(
                text = stringResource(R.string.weekplan_save_template),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newTemplateName,
                    onValueChange = { newTemplateName = it },
                    label = { Text(stringResource(R.string.weekplan_template_name)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (newTemplateName.isNotBlank()) {
                                onSave(newTemplateName.trim())
                                newTemplateName = ""
                            }
                        },
                    ),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (newTemplateName.isNotBlank()) {
                            onSave(newTemplateName.trim())
                            newTemplateName = ""
                        }
                    },
                    enabled = newTemplateName.isNotBlank(),
                ) {
                    Text(stringResource(R.string.weekplan_proposal_accept))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
