package com.helga.android.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.helga.android.R

private val DIET_OPTIONS = listOf("Egal", "Vegan", "Vegetarisch", "Mit Fleisch", "Mit Fisch")
private val COOKTIME_OPTIONS = listOf("Egal", "Schnell (< 30 Min)", "Mittel (30–60 Min)", "Aufwendig (> 60 Min)")
private val EFFORT_OPTIONS = listOf("Egal", "Kindgerecht", "Einfach", "Mittel", "Anspruchsvoll")
private val CUISINE_OPTIONS = listOf(
    "Egal", "Deutsch", "Mediterran", "Asiatisch", "Koreanisch",
    "Japanisch", "Indisch", "Mexikanisch", "Arabisch", "International",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiGenerateScreen(
    onBack: () -> Unit,
    onSaved: (id: String) -> Unit,
    viewModel: AiGenerateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val serverReachable by viewModel.serverReachable.collectAsStateWithLifecycle()

    if (state.feedbackVisible) {
        FeedbackDialog(
            feedback = state.feedback,
            onFeedbackChange = viewModel::setFeedback,
            onConfirm = viewModel::regenerate,
            onDismiss = viewModel::hideFeedback,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ai_generate_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        when (val status = state.status) {
            is AiGenerateStatus.Preview -> AiPreviewContent(
                recipe = status.recipe,
                isSaving = state.isSaving,
                onSave = { viewModel.save(status.recipe) { id -> onSaved(id) } },
                onNewRecipe = viewModel::showFeedback,
                onDiscard = viewModel::discardPreview,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (serverReachable == false) {
                    item { ReachabilityBanner() }
                }
                item {
                    QuestionsCard(
                        dietType = state.dietType,
                        cookTime = state.cookTime,
                        effort = state.effort,
                        cuisine = state.cuisine,
                        special = state.special,
                        enabled = status !is AiGenerateStatus.Generating,
                        onDietType = viewModel::setDietType,
                        onCookTime = viewModel::setCookTime,
                        onEffort = viewModel::setEffort,
                        onCuisine = viewModel::setCuisine,
                        onSpecial = viewModel::setSpecial,
                    )
                }
                item {
                    OutlinedTextField(
                        value = state.prompt,
                        onValueChange = viewModel::setPrompt,
                        label = { Text(stringResource(R.string.ai_generate_prompt_label)) },
                        placeholder = { Text(stringResource(R.string.ai_generate_prompt_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 8,
                        enabled = status !is AiGenerateStatus.Generating,
                    )
                }
                if (status is AiGenerateStatus.Generating) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = stringResource(R.string.ai_generating),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (status is AiGenerateStatus.Error) {
                    item {
                        Text(
                            text = status.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                item {
                    Button(
                        onClick = viewModel::generate,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.prompt.isNotBlank() && status !is AiGenerateStatus.Generating
                                && serverReachable != false,
                    ) {
                        Text(stringResource(R.string.ai_generate_button))
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

/**
 * Persistenter Hinweis statt eines Fehlers erst nach gescheitertem Generieren (ki A3) —
 * gespeist von [com.helga.android.data.sync.ServerReachabilityMonitor].
 */
@Composable
private fun ReachabilityBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = stringResource(R.string.ai_server_unreachable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestionsCard(
    dietType: String,
    cookTime: String,
    effort: String,
    cuisine: String,
    special: String,
    enabled: Boolean,
    onDietType: (String) -> Unit,
    onCookTime: (String) -> Unit,
    onEffort: (String) -> Unit,
    onCuisine: (String) -> Unit,
    onSpecial: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.ai_generate_constraints_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            QuestionDropdown(
                label = stringResource(R.string.ai_question_diet),
                options = DIET_OPTIONS,
                selected = dietType,
                onSelect = onDietType,
                enabled = enabled,
            )
            QuestionDropdown(
                label = stringResource(R.string.ai_question_cooktime),
                options = COOKTIME_OPTIONS,
                selected = cookTime,
                onSelect = onCookTime,
                enabled = enabled,
            )
            QuestionDropdown(
                label = stringResource(R.string.ai_question_effort),
                options = EFFORT_OPTIONS,
                selected = effort,
                onSelect = onEffort,
                enabled = enabled,
            )
            QuestionDropdown(
                label = stringResource(R.string.ai_question_cuisine),
                options = CUISINE_OPTIONS,
                selected = cuisine,
                onSelect = onCuisine,
                enabled = enabled,
            )
            OutlinedTextField(
                value = special,
                onValueChange = onSpecial,
                label = { Text(stringResource(R.string.ai_question_special)) },
                placeholder = { Text(stringResource(R.string.ai_question_special_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestionDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            enabled = enabled,
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelect(option); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun FeedbackDialog(
    feedback: String,
    onFeedbackChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_feedback_title)) },
        text = {
            OutlinedTextField(
                value = feedback,
                onValueChange = onFeedbackChange,
                placeholder = { Text(stringResource(R.string.ai_feedback_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5,
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.ai_regenerate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.recipe_delete_confirm_cancel))
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiPreviewContent(
    recipe: ParsedAiRecipe,
    isSaving: Boolean,
    onSave: () -> Unit,
    onNewRecipe: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header: Name, Beschreibung, Zeitangaben
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(recipe.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (recipe.description.isNotBlank()) {
                        Text(
                            text = recipe.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val timeItems = buildList {
                        if (recipe.prepTime.isNotBlank()) add("Vorbereitung: ${recipe.prepTime}")
                        if (recipe.cookTime.isNotBlank()) add("Kochen: ${recipe.cookTime}")
                        if (recipe.totalTime.isNotBlank()) add("Gesamt: ${recipe.totalTime}")
                        if (recipe.recipeYield.isNotBlank()) add("Portionen: ${recipe.recipeYield}")
                    }
                    if (timeItems.isNotEmpty()) {
                        HorizontalDivider()
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            timeItems.forEach { item ->
                                Text(
                                    text = item,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Zutaten
        if (recipe.ingredients.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.recipe_detail_ingredients),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        recipe.ingredients.forEach { ingredient ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(16.dp),
                                )
                                Text(
                                    text = ingredient,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Zubereitung
        if (recipe.instructions.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.recipe_detail_instructions),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        recipe.instructions.forEachIndexed { idx, step ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = "${idx + 1}.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(24.dp),
                                )
                                Text(
                                    text = step,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (idx < recipe.instructions.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Tags
        if (recipe.tags.isNotEmpty()) {
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    recipe.tags.forEach { tag ->
                        AssistChip(
                            onClick = {},
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        }

        // Aktionen
        item {
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                enabled = recipe.name.isNotBlank() && !isSaving,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.ai_generate_save))
                }
            }
        }
        item {
            OutlinedButton(
                onClick = onNewRecipe,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.ai_new_recipe))
            }
        }
        item {
            TextButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ai_preview_discard))
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}
