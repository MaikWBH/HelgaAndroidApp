package com.helga.android.ui.weekplan

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.helga.android.R
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.util.ImageUrls
import com.helga.android.ui.recipes.SortOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekplanRecipePickerScreen(
    onBack: () -> Unit,
    onRecipePicked: () -> Unit,
    onRecipeClick: (String) -> Unit,
    viewModel: WeekplanRecipePickerViewModel = hiltViewModel(),
) {
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    val allTagNames by viewModel.allTagNames.collectAsStateWithLifecycle()
    val selectedTag by viewModel.selectedTag.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.weekplan_pick_recipe)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
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
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear_search))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            )
            PickerFilterBar(
                allTags = allTagNames,
                selectedTag = selectedTag,
                sortOrder = sortOrder,
                onTagSelect = viewModel::selectTag,
                onSortSelect = viewModel::setSortOrder,
            )
            if (recipes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.recipes_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(recipes, key = { it.id }) { recipe ->
                        val imageUrl = remember(recipe.localImageUri, recipe.imagePath, serverUrl) {
                            when {
                                recipe.localImageUri.isNotBlank() -> recipe.localImageUri
                                recipe.imagePath.isNotBlank() && serverUrl.isNotBlank() ->
                                    ImageUrls.serverImageUrl(serverUrl, recipe.imagePath)
                                else -> null
                            }
                        }
                        PickerRecipeCard(
                            recipe = recipe,
                            imageUrl = imageUrl,
                            onClick = { onRecipeClick(recipe.id) },
                            onPick = {
                                viewModel.addRecipe(recipe.id)
                                onRecipePicked()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerFilterBar(
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
            items(allTags, key = { it }) { tag ->
                FilterChip(
                    selected = tag == selectedTag,
                    onClick = { onTagSelect(if (tag == selectedTag) null else tag) },
                    label = { Text(tag) },
                )
            }
        }
        PickerSortButton(sortOrder = sortOrder, onSortSelect = onSortSelect)
    }
}

@Composable
private fun PickerSortButton(sortOrder: SortOrder, onSortSelect: (SortOrder) -> Unit) {
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
                    text = { Text(sortLabel(order)) },
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
private fun sortLabel(order: SortOrder) = stringResource(
    when (order) {
        SortOrder.NAME -> R.string.recipes_sort_name
        SortOrder.RATING -> R.string.recipes_sort_rating
        SortOrder.UPDATED -> R.string.recipes_sort_updated
    }
)

@Composable
private fun PickerRecipeCard(
    recipe: RecipeEntity,
    imageUrl: String?,
    onClick: () -> Unit,
    onPick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RecipePickerThumbnail(imageUrl = imageUrl)
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
            IconButton(onClick = onPick) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.weekplan_add_recipe),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun RecipePickerThumbnail(imageUrl: String?) {
    Box(
        modifier = Modifier
            .size(64.dp)
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
