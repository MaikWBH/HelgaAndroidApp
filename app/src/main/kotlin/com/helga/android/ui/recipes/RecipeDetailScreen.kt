package com.helga.android.ui.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.helga.android.data.util.ImageUrls
import com.helga.android.data.repository.RecipeNutritionWithMappings
import com.helga.android.ui.components.CreateFab
import com.helga.android.ui.components.MealSlots
import com.helga.android.ui.components.mealSlotLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    onBack: () -> Unit,
    onEdit: (id: String) -> Unit,
    onCook: (id: String) -> Unit,
    onNewRecipe: () -> Unit,
    onAiGenerate: () -> Unit,
    onImport: () -> Unit,
    onRemix: (id: String) -> Unit,
    viewModel: RecipeDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val recipe = uiState.recipe
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
    var shoppingTargetDialog by remember { mutableStateOf(false) }
    var showWeekplanPicker by remember { mutableStateOf(false) }
    val shoppingLists by viewModel.shoppingLists.collectAsStateWithLifecycle()
    val weekplanDays by viewModel.weekplanDays.collectAsStateWithLifecycle()
    val servings by viewModel.servings.collectAsStateWithLifecycle()
    val baseServings by viewModel.baseServings.collectAsStateWithLifecycle()
    val scaleFactor by viewModel.scaleFactor.collectAsStateWithLifecycle()
    val nutrition by viewModel.nutrition.collectAsStateWithLifecycle()
    val nutritionWithMappings by viewModel.nutritionWithMappings.collectAsStateWithLifecycle()

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

    if (showWeekplanPicker) {
        WeekplanDayPickerDialog(
            days = weekplanDays,
            onDismiss = { showWeekplanPicker = false },
            onPick = { dayId ->
                showWeekplanPicker = false
                viewModel.addToWeekplanDay(dayId)
            },
        )
    }

    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = recipe?.name ?: "",
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(),
                    )
                },
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
                        Box {
                            IconButton(onClick = { showOverflow = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = showOverflow,
                                onDismissRequest = { showOverflow = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.recipe_edit)) },
                                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                    onClick = {
                                        showOverflow = false
                                        onEdit(it.id)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.recipe_delete)) },
                                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                    onClick = {
                                        showOverflow = false
                                        showDeleteDialog = true
                                    },
                                )
                                HorizontalDivider()
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
                                val alreadyClassified = recipe != null && recipe.mealSlot != MealSlots.OTHER
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
                                    enabled = !uiState.isClassifying && !alreadyClassified,
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.recipe_add_to_weekplan)) },
                                    leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                                    onClick = {
                                        showOverflow = false
                                        viewModel.loadWeekplanDays()
                                        showWeekplanPicker = true
                                    },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.recipe_share)) },
                                    leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                                    onClick = {
                                        showOverflow = false
                                        viewModel.shareRecipe(context)
                                    },
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
                if (nutrition != null) {
                    item { NutritionSection(nutrition = nutrition!!) }
                }
                item {
                    Button(
                        onClick = { viewModel.calculateNutritionWithProducts() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Text("📊 Nährwerte berechnen mit Katalog")
                    }
                }
                if (nutritionWithMappings != null) {
                    item {
                        NutritionSectionWithMappings(
                            data = nutritionWithMappings!!,
                            onScanClick = { }
                        )
                    }
                }
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
                item {
                    PersonalNotesSection(
                        notes = recipe.personalNotes,
                        onSave = { viewModel.savePersonalNotes(it) },
                    )
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
private fun HeroImage(
    recipe: RecipeEntity,
    serverUrl: String,
) {
    val imageUrl = when {
        recipe.localImageUri.isNotBlank() -> recipe.localImageUri
        recipe.imagePath.isNotBlank() && serverUrl.isNotBlank() ->
            ImageUrls.serverImageUrl(serverUrl, recipe.imagePath)
        else -> null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
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
        if (recipe.recipeYield.isNotBlank()) add("🍽️" to recipe.recipeYield)
        if (recipe.totalTime.isNotBlank()) add("⏱️" to recipe.totalTime)
        if (recipe.prepTime.isNotBlank()) add("🔪" to recipe.prepTime)
        if (recipe.cookTime.isNotBlank()) add("🔥" to recipe.cookTime)
        if (recipe.cuisine.isNotBlank()) add("🌍" to recipe.cuisine)
        if (recipe.mealSlot != MealSlots.OTHER) add("🍳" to mealSlotLabel(recipe.mealSlot))
        if (recipe.effort.isNotBlank()) add("💪" to recipe.effort)
    }
    if (items.isEmpty()) return

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { (emoji, value) ->
            SuggestionChip(
                onClick = {},
                label = { Text("$emoji $value", style = MaterialTheme.typography.bodySmall) },
            )
        }
    }
}

@Composable
private fun NutritionSection(nutrition: com.helga.android.data.model.RecipeNutrition) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = "📊 Nährwerte",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            NutritionItem(value = String.format("%.0f", nutrition.kcalPerPortion), unit = "kcal")
            NutritionItem(value = String.format("%.1f", nutrition.protein), unit = "g Protein")
            NutritionItem(value = String.format("%.1f", nutrition.fat), unit = "g Fett")
            NutritionItem(value = String.format("%.1f", nutrition.carbs), unit = "g KH")
        }
        if (nutrition.nutriScore.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Nutri-Score:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = nutrition.nutriScore.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = when (nutrition.nutriScore.lowercase()) {
                        "a" -> androidx.compose.ui.graphics.Color(0xFF22863A)
                        "b" -> androidx.compose.ui.graphics.Color(0xFF28A745)
                        "c" -> androidx.compose.ui.graphics.Color(0xFFFFC107)
                        "d" -> androidx.compose.ui.graphics.Color(0xFFFF9800)
                        else -> androidx.compose.ui.graphics.Color(0xFFD32F2F)
                    },
                )
            }
        }
        Text(
            text = "${nutrition.matchedIngredientsCount}/${nutrition.totalIngredientsCount} Zutaten mit Nährwerten",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun NutritionItem(value: String, unit: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NutritionSectionWithMappings(
    data: RecipeNutritionWithMappings,
    onScanClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("📊 Nährwerte", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NutritionItem(value = String.format("%.0f", data.nutrition.kcalPerPortion), unit = "kcal")
                    NutritionItem(value = String.format("%.1f", data.nutrition.protein), unit = "g Protein")
                    NutritionItem(value = String.format("%.1f", data.nutrition.fat), unit = "g Fett")
                    NutritionItem(value = String.format("%.1f", data.nutrition.carbs), unit = "g KH")
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (data.ingredientMappings.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Verwendet: ${data.ingredientMappings.joinToString(", ") { it.productName }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (data.unmappedIngredients.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    data.unmappedIngredients.forEach { ingredient ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "⚠ $ingredient",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Button(
                                onClick = onScanClick,
                                modifier = Modifier.height(32.dp),
                            ) {
                                Text("Jetzt scannen", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
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
private fun PersonalNotesSection(notes: String, onSave: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(notes) { mutableStateOf(notes) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        SectionHeader(stringResource(R.string.recipe_personal_notes))
        if (editing) {
            androidx.compose.material3.OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                placeholder = { Text(stringResource(R.string.recipe_personal_notes_hint)) },
                minLines = 2,
                maxLines = 6,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { draft = notes; editing = false }) {
                    Text(stringResource(R.string.recipe_delete_confirm_cancel))
                }
                TextButton(onClick = { onSave(draft); editing = false }) {
                    Text(stringResource(R.string.recipe_personal_notes_save))
                }
            }
        } else {
            if (notes.isNotBlank()) {
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editing = true }
                        .padding(vertical = 8.dp),
                )
            } else {
                TextButton(onClick = { editing = true }) {
                    Text(stringResource(R.string.recipe_personal_notes_add))
                }
            }
        }
    }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val quantityText = ingredient.quantityText(scaleFactor)
        Text(
            text = quantityText.ifBlank { "•" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.widthIn(min = 72.dp),
        )
        Text(
            text = buildString {
                append(ingredient.food)
                if (ingredient.note.isNotBlank()) append(" (${ingredient.note})")
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
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

private fun IngredientEntity.quantityText(scaleFactor: Float = 1f): String = buildString {
    if (quantity > 0.0) {
        val scaled = quantity * scaleFactor
        val q = if (scaled % 1.0 < 0.01) "${scaled.toInt()}"
                else String.format(java.util.Locale.getDefault(), "%.1f", scaled)
                    .trimEnd('0').trimEnd(',').trimEnd('.')
        append(q)
        if (unit.isNotBlank()) append(" $unit")
    }
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

@Composable
private fun WeekplanDayPickerDialog(
    days: List<RecipeDetailViewModel.WeekplanDayWithRecipes>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recipe_add_to_weekplan)) },
        text = {
            if (days.isEmpty()) {
                Text(stringResource(R.string.recipe_weekplan_no_days))
            } else {
                LazyColumn {
                    items(days, key = { it.day.id }) { dayWithRecipes ->
                        val date = runCatching {
                            LocalDate.parse(dayWithRecipes.day.planDate, DateTimeFormatter.ISO_LOCAL_DATE)
                        }.getOrNull()
                        val dayLabel = date?.dayOfWeek
                            ?.getDisplayName(TextStyle.FULL, Locale.getDefault())
                            ?.replaceFirstChar { it.uppercase() }
                            ?: dayWithRecipes.day.planDate
                        val dateLabel = date?.format(DateTimeFormatter.ofPattern("dd.MM.")) ?: ""

                        TextButton(
                            onClick = { onPick(dayWithRecipes.day.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row {
                                    Text(
                                        text = dayLabel,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    if (dateLabel.isNotBlank()) {
                                        Spacer(Modifier.widthIn(min = 8.dp))
                                        Text(
                                            text = dateLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (dayWithRecipes.recipeNames.isNotEmpty()) {
                                    Text(
                                        text = dayWithRecipes.recipeNames.joinToString(", "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
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
