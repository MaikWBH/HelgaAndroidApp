package com.helga.android.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.helga.android.R
import com.helga.android.data.remote.dto.ImportedRecipeDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrlImportScreen(
    onBack: () -> Unit,
    initialUrl: String? = null,
    viewModel: UrlImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(initialUrl) {
        if (initialUrl != null) viewModel.prefillUrl(initialUrl)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_url_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.recipe_detail_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.url,
                onValueChange = viewModel::setUrl,
                label = { Text(stringResource(R.string.import_url_label)) },
                placeholder = { Text("https://www.chefkoch.de/rezepte/...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { viewModel.import() }),
                leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
            )

            Button(
                onClick = { viewModel.import() },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.url.isNotBlank() && state.status !is ImportStatus.Loading,
            ) {
                if (state.status is ImportStatus.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.import_url_action))
                }
            }

            when (val status = state.status) {
                is ImportStatus.Error -> {
                    Text(
                        text = status.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                is ImportStatus.Success -> {
                    ImportPreview(
                        recipe = status.recipe,
                        isSaving = state.isSaving,
                        onSave = { viewModel.save(status.recipe, onBack) },
                    )
                }

                else -> {}
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ImportPreview(
    recipe: ImportedRecipeDto,
    isSaving: Boolean,
    onSave: () -> Unit,
) {
    HorizontalDivider()
    Spacer(Modifier.height(4.dp))
    Text(stringResource(R.string.import_url_preview), style = MaterialTheme.typography.titleMedium)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(recipe.name.ifBlank { "—" }, style = MaterialTheme.typography.titleMedium)

            if (recipe.description.isNotBlank()) {
                Text(
                    recipe.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (recipe.ingredients.isNotEmpty()) {
                    MetaChip(stringResource(R.string.import_url_ingredients, recipe.ingredients.size))
                }
                if (recipe.instructions.isNotEmpty()) {
                    MetaChip(stringResource(R.string.import_url_steps, recipe.instructions.size))
                }
                if (recipe.totalTime.isNotBlank()) {
                    MetaChip(recipe.totalTime)
                }
            }

            if (recipe.tags.isNotEmpty()) {
                Text(
                    recipe.tags.joinToString(", "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    Button(
        onClick = onSave,
        modifier = Modifier.fillMaxWidth(),
        enabled = recipe.name.isNotBlank() && !isSaving,
    ) {
        if (isSaving) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Text(stringResource(R.string.recipe_form_save))
        }
    }
}

@Composable
private fun MetaChip(text: String) {
    Box(
        modifier = Modifier.padding(horizontal = 0.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
