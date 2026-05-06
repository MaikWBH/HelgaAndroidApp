package com.helga.android.ui.recipes

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.helga.android.R
import com.helga.android.data.local.entity.IngredientEntity
import com.helga.android.data.local.entity.InstructionEntity
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.local.entity.ShoppingListEntity
import com.helga.android.data.local.entity.TagEntity
import com.helga.android.ui.components.CreateFab

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    onBack: () -> Unit,
    onEdit: (id: String) -> Unit,
    onCook: (id: String) -> Unit,
    onNewRecipe: () -> Unit,
    onAiGenerate: () -> Unit,
    onImport: () -> Unit,
    onRemix: (id: String) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    viewModel: RecipeDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val recipe = uiState.recipe
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
    var shoppingTargetDialog by remember { mutableStateOf(false) }
    val shoppingLists by viewModel.shoppingLists.collectAsStateWithLifecycle()
    val servings by viewModel.servings.collectAsStateWithLifecycle()
    val baseServings by viewModel.baseServings.collectAsStateWithLifecycle()
    val scaleFactor by viewModel.scaleFactor.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.classifyError) {
        uiState.classifyError?.let { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { snackbarHostState.showSnackbar(it) }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.recipe_delete_confirm_title)) },
            text = { Text(stringResource(R.string.recipe_delete_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteRecipe(onBack)
                }) {
                    Text(stringResource(R.string.recipe_delete_confirm_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.recipe_delete_confirm_cancel))
                }
            },
        )
    }

    if (shoppingTargetDialog) {
        ShoppingListSelectDialog(
            lists = shoppingLists,
            onDismiss = { shoppingTargetDialog = false },
            onPick = { list ->
                shoppingTargetDialog = false
                viewModel.exportToShoppingList(list.id)
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(recipe?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.recipe_detail_back),
                        )
                    }
                },
                actions = {
                    recipe?.let {
                        IconButton(onClick = { viewModel.toggleFavorite() }) {
                            Icon(
                                imageVector = if (it.isFavorite == 1) Icons.Filled.Star else Icons.Outlined.Star,
                                contentDescription = stringResource(R.string.recipe_favorite),
                                tint = if (it.isFavorite == 1) MaterialTheme.colorScheme.primary
                                       else androidx.compose.ui.graphics.Color.Unspecified,
                            )
                        }
                        if (uiState.instructions.isNotEmpty()) {
                            IconButton(onClick = { onCook(it.id) }) {
                                Icon(Icons.Filled.MenuBook, contentDescription = stringResource(R.string.recipe_cook))
                            }
                        }
                        IconButton(onClick = { onEdit(it.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.recipe_edit))
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.recipe_delete))
                        }
                        Box {
                            IconButton(onClick = { showOverflow = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = showOverflow,
                                onDismissRequest = { showOverflow = false },
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        val defaultList = shoppingLists.firstOrNull { it.isDefaultRecipe == 1 }
                                            ?: shoppingLists.firstOrNull()
                                        Text(
                                            if (defaultList != null)
                                                stringResource(R.string.recipe_add_to_list, defaultList.name)
                                            else
                                                stringResource(R.string.recipe_add_to_shopping)
                                        )
                                    },
                                    leadingIcon = { Icon(Icons.Filled.ShoppingCart, contentDescription = null) },
                                    onClick = {
                                        showOverflow = false
                                        val defaultList = shoppingLists.firstOrNull { it.isDefaultRecipe == 1 }
                                            ?: shoppingLists.firstOrNull()
                                        if (defaultList != null) {
                                            viewModel.addToDefaultShoppingList(defaultList.name)
                                        } else {
                                            shoppingTargetDialog = true
                                        }
                                    },
                                    enabled = shoppingLists.isNotEmpty(),
                                )
                                if (shoppingLists.size > 1) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.recipe_add_to_shopping_pick)) },
                                        leadingIcon = { Icon(Icons.Filled.ShoppingCart, contentDescription = null) },
                                        onClick = {
                                            showOverflow = false
                                            shoppingTargetDialog = true
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = {
                                        if (uiState.isClassifying) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        } else {
                                            Text(stringResource(R.string.recipe_classify))
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Filled.AutoFixHigh, contentDescription = null) },
                                    onClick = {
                                        showOverflow = false
                                        viewModel.classify()
                                    },
                                    enabled = !uiState.isClassifying,
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            recipe?.let {
                CreateFab(
                    onNewRecipe = onNewRecipe,
                    onAiGenerate = onAiGenerate,
                    onUrlImport = onImport,
                    onRemix = { onRemix(it.id) },
                )
            }
        },
    ) { padding ->
        if (recipe == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                item {
                    HeroImage(
                        recipe = recipe,
                        serverUrl = serverUrl,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }
                item {
                    RatingSection(
                        rating = recipe.rating,
                        onRatingChange = viewModel::setRating,
                    )
                }
                if (uiState.tags.isNotEmpty()) {
                    item { TagsSection(tags = uiState.tags) }
                }
                item { MetadataSection(recipe = recipe) }
                if (recipe.description.isNotBlank()) {
                    item { DescriptionSection(description = recipe.description) }
                }
                if (uiState.ingredients.isNotEmpty()) {
                    item {
                        SectionHeader(stringResource(R.string.recipe_detail_ingredients))
                    }
                    if (baseServings > 0) {
                        item {
                            ServingsStepper(
                                servings = servings,
                                onDecrease = { viewModel.setServings(servings - 1) },
                                onIncrease = { viewModel.setServings(servings + 1) },
                            )
                        }
                    }
                    items(uiState.ingredients, key = { it.id }) { ingredient ->
                        IngredientRow(ingredient = ingredient, scaleFactor = scaleFactor)
                    }
                }
                if (uiState.instructions.isNotEmpty()) {
                    item {
                        SectionHeader(stringResource(R.string.recipe_detail_instructions))
                    }
                    items(uiState.instructions, key = { it.id }) { instruction ->
                        InstructionRow(instruction = instruction)
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HeroImage(
    recipe: RecipeEntity,
    serverUrl: String,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val imageUrl = if (recipe.imagePath.isNotBlank() && serverUrl.isNotBlank())
        "${serverUrl.trimEnd('/')}/api/images/${recipe.imagePath}"
    else null

    with(sharedTransitionScope) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .sharedElement(
                    state = rememberSharedContentState(key = "image-${recipe.id}"),
                    animatedVisibilityScope = animatedVisibilityScope,
                ),
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .placeholderMemoryCacheKey("recipe-${recipe.id}")
                        .crossfade(true)
                        .build(),
                    contentDescription = recipe.name,
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
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RatingSection(rating: Int, onRatingChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 1..5) {
            IconButton(
                onClick = { onRatingChange(if (i == rating) 0 else i) },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = if (i <= rating) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = null,
                    tint = if (i <= rating) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsSection(tags: List<TagEntity>) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tags.forEach { tag ->
            AssistChip(
                onClick = {},
                label = { Text(tag.name, style = MaterialTheme.typography.labelMedium) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetadataSection(recipe: RecipeEntity) {
    val items = buildList {
        if (recipe.recipeYield.isNotBlank()) add(stringResource(R.string.recipe_detail_yield) to recipe.recipeYield)
        if (recipe.totalTime.isNotBlank()) add(stringResource(R.string.recipe_detail_total_time) to recipe.totalTime)
        if (recipe.prepTime.isNotBlank()) add(stringResource(R.string.recipe_detail_prep_time) to recipe.prepTime)
        if (recipe.cookTime.isNotBlank()) add(stringResource(R.string.recipe_detail_cook_time) to recipe.cookTime)
        if (recipe.cuisine.isNotBlank()) add(stringResource(R.string.recipe_detail_cuisine) to recipe.cuisine)
        if (recipe.mealType.isNotBlank()) add(stringResource(R.string.recipe_detail_meal_type) to recipe.mealType)
        if (recipe.effort.isNotBlank()) add(stringResource(R.string.recipe_detail_effort) to recipe.effort)
    }
    if (items.isEmpty()) return

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { (label, value) ->
            SuggestionChip(
                onClick = {},
                label = { Text("$label: $value", style = MaterialTheme.typography.bodySmall) },
            )
        }
    }
}

@Composable
private fun DescriptionSection(description: String) {
    Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SectionHeader(title: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
private fun ServingsStepper(servings: Int, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.recipe_detail_yield),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = onDecrease, enabled = servings > 1) {
                    Icon(Icons.Filled.Remove, contentDescription = null)
                }
                Text(
                    text = "$servings",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.widthIn(min = 28.dp),
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = onIncrease, enabled = servings < 99) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun IngredientRow(ingredient: IngredientEntity, scaleFactor: Float = 1f) {
    Text(
        text = ingredient.displayText(scaleFactor),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun InstructionRow(instruction: InstructionEntity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "${instruction.position}.",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 1.dp),
        )
        Text(
            text = instruction.text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun IngredientEntity.displayText(scaleFactor: Float = 1f): String = buildString {
    if (quantity > 0.0) {
        val scaled = quantity * scaleFactor
        val q = if (scaled % 1.0 < 0.01) "${scaled.toInt()}"
                else String.format(java.util.Locale.getDefault(), "%.1f", scaled)
                    .trimEnd('0').trimEnd(',').trimEnd('.')
        append(q)
        if (unit.isNotBlank()) append(" $unit")
        append(" ")
    }
    append(food)
    if (note.isNotBlank()) append(" ($note)")
}

@Composable
private fun ShoppingListSelectDialog(
    lists: List<ShoppingListEntity>,
    onDismiss: () -> Unit,
    onPick: (ShoppingListEntity) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.weekplan_pick_list)) },
        text = {
            if (lists.isEmpty()) {
                Text(stringResource(R.string.weekplan_no_lists))
            } else {
                LazyColumn {
                    items(lists, key = { it.id }) { list ->
                        TextButton(onClick = { onPick(list) }, modifier = Modifier.fillMaxWidth()) {
                            Text(list.name)
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
