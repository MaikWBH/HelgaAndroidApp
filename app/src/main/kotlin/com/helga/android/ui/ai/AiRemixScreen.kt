package com.helga.android.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.helga.android.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiRemixScreen(
    onBack: () -> Unit,
    onSaved: (id: String) -> Unit,
    viewModel: AiRemixViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val source by viewModel.source.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = source.recipe?.name?.let {
                            stringResource(R.string.ai_remix_title_with_name, it)
                        } ?: stringResource(R.string.ai_remix_title),
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        when (val status = state.status) {
            is AiRemixStatus.Preview -> AiPreviewContent(
                recipe = status.recipe,
                isSaving = state.isSaving,
                onSave = { viewModel.save(status.recipe) { id -> onSaved(id) } },
                onNewPrompt = { viewModel.setRemixPrompt(state.remixPrompt) },
                onDiscard = viewModel::discardPreview,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                source.recipe?.let { recipe ->
                    Text(
                        text = stringResource(R.string.ai_remix_original_label, recipe.name),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedTextField(
                    value = state.remixPrompt,
                    onValueChange = viewModel::setRemixPrompt,
                    label = { Text(stringResource(R.string.ai_remix_prompt_label)) },
                    placeholder = { Text(stringResource(R.string.ai_remix_prompt_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8,
                    enabled = status !is AiRemixStatus.Generating,
                )

                if (status is AiRemixStatus.Generating) {
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

                if (status is AiRemixStatus.Error) {
                    Text(
                        text = status.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Button(
                    onClick = viewModel::remix,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.remixPrompt.isNotBlank() && source.recipe != null
                            && status !is AiRemixStatus.Generating,
                ) {
                    Text(stringResource(R.string.ai_remix_button))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiPreviewContent(
    recipe: ParsedAiRecipe,
    isSaving: Boolean,
    onSave: () -> Unit,
    onNewPrompt: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(recipe.name, style = MaterialTheme.typography.titleLarge)
                    if (recipe.description.isNotBlank()) {
                        Text(
                            text = recipe.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                    if (recipe.totalTime.isNotBlank()) {
                        Text(
                            text = "${stringResource(R.string.recipe_detail_total_time)}: ${recipe.totalTime}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        text = stringResource(R.string.import_url_ingredients, recipe.ingredients.size),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = stringResource(R.string.import_url_steps, recipe.instructions.size),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (recipe.tags.isNotEmpty()) {
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
            }
        }
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
            TextButton(onClick = onNewPrompt, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ai_remix_new_prompt))
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
