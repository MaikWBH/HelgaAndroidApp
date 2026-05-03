package com.helga.android.ui.recipes

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.helga.android.R
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeFormScreen(
    onBack: () -> Unit,
    viewModel: RecipeFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        uri?.let { viewModel.setImage(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (viewModel.isEditing) stringResource(R.string.recipe_form_edit_title)
                        else stringResource(R.string.recipe_form_new_title),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.recipe_detail_back))
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ImagePicker(
                localImageUri = state.localImageUri,
                onPickImage = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )

            FormField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = stringResource(R.string.recipe_form_name),
                required = true,
            )
            FormField(
                value = state.description,
                onValueChange = viewModel::setDescription,
                label = stringResource(R.string.recipe_form_description),
                singleLine = false,
                minLines = 3,
            )

            SectionHeader(stringResource(R.string.recipe_form_times))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(
                    value = state.prepTime,
                    onValueChange = viewModel::setPrepTime,
                    label = stringResource(R.string.recipe_detail_prep_time),
                    modifier = Modifier.weight(1f),
                )
                FormField(
                    value = state.cookTime,
                    onValueChange = viewModel::setCookTime,
                    label = stringResource(R.string.recipe_detail_cook_time),
                    modifier = Modifier.weight(1f),
                )
                FormField(
                    value = state.totalTime,
                    onValueChange = viewModel::setTotalTime,
                    label = stringResource(R.string.recipe_detail_total_time),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(
                    value = state.recipeYield,
                    onValueChange = viewModel::setRecipeYield,
                    label = stringResource(R.string.recipe_detail_yield),
                    modifier = Modifier.weight(1f),
                )
                FormField(
                    value = state.cuisine,
                    onValueChange = viewModel::setCuisine,
                    label = stringResource(R.string.recipe_detail_cuisine),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(
                    value = state.mealType,
                    onValueChange = viewModel::setMealType,
                    label = stringResource(R.string.recipe_detail_meal_type),
                    modifier = Modifier.weight(1f),
                )
                FormField(
                    value = state.effort,
                    onValueChange = viewModel::setEffort,
                    label = stringResource(R.string.recipe_detail_effort),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(
                    value = state.proteinType,
                    onValueChange = viewModel::setProteinType,
                    label = stringResource(R.string.recipe_form_protein_type),
                    modifier = Modifier.weight(1f),
                )
                FormField(
                    value = state.seasonFit,
                    onValueChange = viewModel::setSeasonFit,
                    label = stringResource(R.string.recipe_form_season_fit),
                    modifier = Modifier.weight(1f),
                )
            }
            FormField(
                value = state.sourceUrl,
                onValueChange = viewModel::setSourceUrl,
                label = stringResource(R.string.recipe_form_source_url),
                keyboardType = KeyboardType.Uri,
            )

            SectionHeader(stringResource(R.string.recipe_form_tags))
            TagsEditor(
                tags = state.tags,
                onAddTag = viewModel::addTag,
                onRemoveTag = viewModel::removeTag,
            )

            SectionHeader(stringResource(R.string.recipe_detail_ingredients))
            IngredientsEditor(
                items = state.ingredients,
                onAdd = viewModel::addIngredient,
                onUpdate = viewModel::updateIngredient,
                onRemove = viewModel::removeIngredient,
            )

            SectionHeader(stringResource(R.string.recipe_detail_instructions))
            InstructionsEditor(
                items = state.instructions,
                onAdd = viewModel::addInstruction,
                onUpdate = viewModel::updateInstruction,
                onRemove = viewModel::removeInstruction,
            )

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.save(onBack) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.name.isNotBlank() && !state.isSaving,
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.recipe_form_save))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ImagePicker(localImageUri: String, onPickImage: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (localImageUri.isNotBlank()) {
            AsyncImage(
                model = File(localImageUri),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Restaurant,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalIconButton(
            onClick = onPickImage,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
        ) {
            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = stringResource(R.string.recipe_form_pick_image))
        }
    }
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    required: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(if (required) "$label *" else label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
        ),
    )
}

@Composable
private fun SectionHeader(title: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsEditor(
    tags: List<String>,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tags.forEach { tag ->
                InputChip(
                    selected = false,
                    onClick = { onRemoveTag(tag) },
                    label = { Text(tag) },
                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(stringResource(R.string.recipe_form_tag_input)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
            FilledTonalIconButton(onClick = { onAddTag(input); input = "" }) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
        }
    }
}

@Composable
private fun IngredientsEditor(
    items: List<IngredientFormItem>,
    onAdd: () -> Unit,
    onUpdate: (Int, IngredientFormItem) -> Unit,
    onRemove: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { idx, item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedTextField(
                    value = item.quantity,
                    onValueChange = { onUpdate(idx, item.copy(quantity = it)) },
                    label = { Text(stringResource(R.string.recipe_form_quantity)) },
                    modifier = Modifier.width(72.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = item.unit,
                    onValueChange = { onUpdate(idx, item.copy(unit = it)) },
                    label = { Text(stringResource(R.string.recipe_form_unit)) },
                    modifier = Modifier.width(80.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = item.food,
                    onValueChange = { onUpdate(idx, item.copy(food = it)) },
                    label = { Text(stringResource(R.string.recipe_form_food)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                IconButton(onClick = { onRemove(idx) }) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        FilledTonalIconButton(onClick = onAdd, modifier = Modifier.align(Alignment.End)) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.recipe_form_add_ingredient))
        }
    }
}

@Composable
private fun InstructionsEditor(
    items: List<InstructionFormItem>,
    onAdd: () -> Unit,
    onUpdate: (Int, InstructionFormItem) -> Unit,
    onRemove: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { idx, item ->
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "${idx + 1}.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp),
                )
                OutlinedTextField(
                    value = item.text,
                    onValueChange = { onUpdate(idx, item.copy(text = it)) },
                    label = { Text(stringResource(R.string.recipe_form_step)) },
                    modifier = Modifier.weight(1f),
                    singleLine = false,
                    minLines = 2,
                )
                IconButton(onClick = { onRemove(idx) }, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        FilledTonalIconButton(onClick = onAdd, modifier = Modifier.align(Alignment.End)) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.recipe_form_add_step))
        }
    }
}
