package com.helga.android.ui.recipes

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.helga.android.R
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.ui.components.CreateFab
import com.helga.android.ui.components.SyncStatusIcon

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    onRecipeClick: (id: String) -> Unit,
    onCreateClick: () -> Unit,
    onImportClick: () -> Unit,
    onAiGenerateClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onShoppingClick: () -> Unit,
    onWeekplanClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    viewModel: RecipeListViewModel = hiltViewModel(),
) {
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val allTagNames by viewModel.allTagNames.collectAsStateWithLifecycle()
    val selectedTag by viewModel.selectedTag.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recipes_title)) },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        SyncStatusIcon(syncStatus)
                    }
                    IconButton(onClick = onWeekplanClick) {
                        Icon(
                            imageVector = Icons.Filled.CalendarMonth,
                            contentDescription = stringResource(R.string.weekplan_title),
                        )
                    }
                    IconButton(onClick = onShoppingClick) {
                        Icon(
                            imageVector = Icons.Filled.ShoppingCart,
                            contentDescription = stringResource(R.string.shopping_title),
                        )
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
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            FilterBar(
                allTags = allTagNames,
                selectedTag = selectedTag,
                sortOrder = sortOrder,
                onTagSelect = viewModel::selectTag,
                onSortSelect = viewModel::setSortOrder,
            )
            if (recipes.isEmpty()) {
                EmptyState(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(recipes, key = { it.id }) { recipe ->
                        RecipeRow(
                            recipe = recipe,
                            serverUrl = serverUrl,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
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
    allTags: List<String>,
    selectedTag: String?,
    sortOrder: SortOrder,
    onTagSelect: (String?) -> Unit,
    onSortSelect: (SortOrder) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = selectedTag == null,
                    onClick = { onTagSelect(null) },
                    label = { Text(stringResource(R.string.recipes_filter_all)) },
                )
            }
            items(allTags) { tag ->
                FilterChip(
                    selected = tag == selectedTag,
                    onClick = { onTagSelect(if (tag == selectedTag) null else tag) },
                    label = { Text(tag) },
                )
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun RecipeRow(
    recipe: RecipeEntity,
    serverUrl: String,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit,
) {
    val imageUrl = remember(recipe.imagePath, serverUrl) {
        if (recipe.imagePath.isNotBlank() && serverUrl.isNotBlank())
            "${serverUrl.trimEnd('/')}/api/images/${recipe.imagePath}"
        else null
    }

    with(sharedTransitionScope) {
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
                    recipeId = recipe.id,
                    imageUrl = imageUrl,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recipe.name.ifBlank { recipe.slug },
                        style = MaterialTheme.typography.titleMedium,
                    )
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
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.RecipeThumbnail(
    recipeId: String,
    imageUrl: String?,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .sharedElement(
                state = rememberSharedContentState(key = "image-$recipeId"),
                animatedVisibilityScope = animatedVisibilityScope,
            ),
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .memoryCacheKey("recipe-$recipeId")
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
