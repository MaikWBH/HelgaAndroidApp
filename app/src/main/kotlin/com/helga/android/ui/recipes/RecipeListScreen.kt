package com.helga.android.ui.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
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
import com.helga.android.ui.components.CreateFab
import com.helga.android.ui.components.MealSlots
import com.helga.android.ui.components.SyncStatusIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    onRecipeClick: (id: String) -> Unit,
    onCreateClick: () -> Unit,
    onImportClick: () -> Unit,
    onAiGenerateClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onCookClick: (recipeId: String) -> Unit,
    bottomPadding: Dp = 0.dp,
    viewModel: RecipeListViewModel = hiltViewModel(),
) {
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val allTagNames by viewModel.allTagNames.collectAsStateWithLifecycle()
    val selectedTags by viewModel.selectedTags.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val showFavoritesOnly by viewModel.showFavoritesOnly.collectAsStateWithLifecycle()
    val todayRecipe by viewModel.todayRecipe.collectAsStateWithLifecycle()
    val unclassifiedCount by viewModel.unclassifiedCount.collectAsStateWithLifecycle()
    val unclassifiedRecipes by viewModel.unclassifiedRecipes.collectAsStateWithLifecycle()

    var showTagFilter by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showBulkClassifyDialog by remember { mutableStateOf(false) }
    var classifyProgress by remember { mutableStateOf(0 to 0) }

    if (showTagFilter) {
        TagFilterDialog(
            allTags = allTagNames,
            selectedTags = selectedTags,
            onToggleTag = viewModel::toggleTag,
            onClearAll = viewModel::clearTags,
            onDismiss = { showTagFilter = false },
        )
    }

    if (showBulkClassifyDialog) {
        BulkClassifyDialog(
            unclassifiedRecipes = unclassifiedRecipes,
            isRunning = classifyProgress.first > 0,
            progress = classifyProgress,
            onConfirm = { selected ->
                viewModel.classifyBatch(selected) { current, total ->
                    classifyProgress = current to total
                }
                if (classifyProgress.first == classifyProgress.second) {
                    showBulkClassifyDialog = false
                    classifyProgress = 0 to 0
                }
            },
            onDismiss = {
                showBulkClassifyDialog = false
                classifyProgress = 0 to 0
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recipes_title)) },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        SyncStatusIcon(syncStatus)
                    }
                    if (unclassifiedCount > 0) {
                        Box {
                            IconButton(onClick = { showOverflowMenu = !showOverflowMenu }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.recipe_actions))
                                Badge(modifier = Modifier.align(Alignment.TopEnd)) { Text(unclassifiedCount.toString()) }
                            }
                            DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("⚡ Alle klassifizieren ($unclassifiedCount)") },
                                    onClick = {
                                        showBulkClassifyDialog = true
                                        showOverflowMenu = false
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.action_settings),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            CreateFab(
                onNewRecipe = onCreateClick,
                onAiGenerate = onAiGenerateClick,
                onUrlImport = onImportClick,
                // FAB über der Bottom-Navigation halten, sonst liegt er dahinter
                modifier = Modifier.padding(bottom = bottomPadding),
            )
        },
    ) { padding ->
        val focusManager = LocalFocusManager.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::setSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                placeholder = { Text(stringResource(R.string.recipes_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            )
            FilterBar(
                selectedTags = selectedTags,
                tagCount = allTagNames.size,
                sortOrder = sortOrder,
                showFavoritesOnly = showFavoritesOnly,
                onOpenTagFilter = { showTagFilter = true },
                onSortSelect = viewModel::setSortOrder,
                onToggleFavorites = viewModel::toggleFavoritesFilter,
                onClearFilter = { viewModel.clearTags(); if (showFavoritesOnly) viewModel.toggleFavoritesFilter() },
            )
            if (todayRecipe != null) {
                Card(
                    onClick = { onCookClick(todayRecipe!!.recipeId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("🍳", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.recipes_today_cook),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = todayRecipe!!.recipeName,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            if (recipes.isEmpty()) {
                EmptyState(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + bottomPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(recipes, key = { it.id }) { recipe ->
                        RecipeRow(
                            recipe = recipe,
                            serverUrl = serverUrl,
                            onClick = { onRecipeClick(recipe.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterBar(
    selectedTags: Set<String>,
    tagCount: Int,
    sortOrder: SortOrder,
    showFavoritesOnly: Boolean,
    onOpenTagFilter: () -> Unit,
    onSortSelect: (SortOrder) -> Unit,
    onToggleFavorites: () -> Unit,
    onClearFilter: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = selectedTags.isEmpty() && !showFavoritesOnly,
                onClick = onClearFilter,
                label = { Text(stringResource(R.string.recipes_filter_all)) },
            )
            FilterChip(
                selected = showFavoritesOnly,
                onClick = onToggleFavorites,
                label = { Text(stringResource(R.string.recipes_filter_favorites)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
            if (tagCount > 0) {
                BadgedBox(
                    badge = {
                        if (selectedTags.isNotEmpty()) {
                            Badge { Text(selectedTags.size.toString()) }
                        }
                    },
                ) {
                    FilterChip(
                        selected = selectedTags.isNotEmpty(),
                        onClick = onOpenTagFilter,
                        label = { Text(stringResource(R.string.recipes_filter_tags)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.FilterList,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }
        }
        SortButton(sortOrder = sortOrder, onSortSelect = onSortSelect)
    }
}

@Composable
private fun SortButton(sortOrder: SortOrder, onSortSelect: (SortOrder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.Sort,
                contentDescription = stringResource(R.string.recipes_sort),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(order.label()) },
                    onClick = { onSortSelect(order); expanded = false },
                    leadingIcon = if (order == sortOrder) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else null,
                )
            }
        }
    }
}

@Composable
private fun SortOrder.label() = stringResource(
    when (this) {
        SortOrder.NAME -> R.string.recipes_sort_name
        SortOrder.RATING -> R.string.recipes_sort_rating
        SortOrder.UPDATED -> R.string.recipes_sort_updated
    }
)

@Composable
private fun RecipeRow(
    recipe: RecipeEntity,
    serverUrl: String,
    onClick: () -> Unit,
) {
    val imageUrl = remember(recipe.localImageUri, recipe.imagePath, serverUrl) {
        when {
            recipe.localImageUri.isNotBlank() -> recipe.localImageUri
            recipe.imagePath.isNotBlank() && serverUrl.isNotBlank() ->
                "${serverUrl.trimEnd('/')}/api/images/${recipe.imagePath}"
            else -> null
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RecipeThumbnail(
                imageUrl = imageUrl,
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = recipe.name.ifBlank { recipe.slug },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f).basicMarquee(),
                    )
                    if (recipe.mealSlot == MealSlots.OTHER) {
                        Text("⚠️", modifier = Modifier.padding(start = 4.dp))
                    }
                }
                if (recipe.totalTime.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = recipe.totalTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (recipe.rating > 0) {
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(recipe.rating) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipeThumbnail(
    imageUrl: String?,
) {
    Box(
        modifier = Modifier
            .size(72.dp)
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.recipes_empty),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.recipes_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TagFilterDialog(
    allTags: List<String>,
    selectedTags: Set<String>,
    onToggleTag: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recipes_filter_tags_title)) },
        text = {
            if (allTags.isEmpty()) {
                Text(stringResource(R.string.recipes_filter_no_tags))
            } else {
                LazyColumn {
                    items(allTags) { tag ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleTag(tag) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = tag in selectedTags,
                                onCheckedChange = { onToggleTag(tag) },
                            )
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.recipes_filter_done))
            }
        },
        dismissButton = {
            if (selectedTags.isNotEmpty()) {
                TextButton(onClick = onClearAll) {
                    Text(stringResource(R.string.recipes_filter_clear))
                }
            }
        },
    )
}

@Composable
private fun BulkClassifyDialog(
    unclassifiedRecipes: List<RecipeListViewModel.UnclassifiedRecipe>,
    isRunning: Boolean,
    progress: Pair<Int, Int>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedRecipes by remember { mutableStateOf(unclassifiedRecipes.map { it.id }.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rezepte klassifizieren") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("${unclassifiedRecipes.size} Rezepte sind noch nicht kategorisiert.",
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                if (isRunning) {
                    LinearProgressIndicator(
                        progress = { if (progress.second > 0) progress.first.toFloat() / progress.second else 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("${progress.first}/${progress.second}",
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        style = MaterialTheme.typography.labelSmall)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)) {
                        items(unclassifiedRecipes) { recipe ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = recipe.id in selectedRecipes,
                                    onCheckedChange = { checked ->
                                        selectedRecipes = if (checked) {
                                            selectedRecipes + recipe.id
                                        } else {
                                            selectedRecipes - recipe.id
                                        }
                                    },
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                Text(recipe.name, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedRecipes.toList()) },
                enabled = selectedRecipes.isNotEmpty() && !isRunning,
            ) {
                Text(if (isRunning) "Lädt..." else "Klassifizieren")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isRunning) {
                Text(android.R.string.cancel)
            }
        },
    )
}
