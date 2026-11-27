package com.helga.android.ui.recipes

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.helga.android.R
import com.helga.android.data.util.MarkdownBlockError
import com.helga.android.data.util.MarkdownErrorCode
import com.helga.android.data.util.MarkdownFatal
import com.helga.android.data.util.MarkdownWarning
import com.helga.android.data.util.MarkdownWarningCode
import com.helga.android.data.util.ParsedIngredientLine
import com.helga.android.data.util.ParsedRecipe
import com.helga.android.ui.components.LoadingIndicator
import com.helga.android.ui.components.PreviewSurface
import com.helga.android.ui.components.ThemePreviews

@Composable
fun MarkdownImportScreen(
    onBack: () -> Unit,
    onSaved: (MarkdownSaveTarget) -> Unit,
    viewModel: MarkdownImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var copied by rememberSaveable { mutableStateOf(false) }
    val copiedMessage = stringResource(R.string.md_import_prompt_copied)

    LaunchedEffect(Unit) {
        if (!copied) {
            copyRecipePrompt(context)
            copied = true
            // Ab Android 13 zeigt das System selbst einen Hinweis zum Kopieren.
            if (Build.VERSION.SDK_INT < 33) snackbarHostState.showSnackbar(copiedMessage)
        }
    }

    MarkdownImportContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onInputChange = viewModel::setInput,
        onPaste = { viewModel.setInput(readClipboardText(context)) },
        onCopyAgain = { copyRecipePrompt(context) },
        onEvaluate = viewModel::evaluate,
        onToggle = viewModel::toggle,
        onSave = { viewModel.save(onSaved) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarkdownImportContent(
    state: MarkdownImportState,
    onBack: () -> Unit,
    onInputChange: (String) -> Unit,
    onPaste: () -> Unit,
    onCopyAgain: () -> Unit,
    onEvaluate: () -> Unit,
    onToggle: (Int) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.md_import_title)) },
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
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.md_import_prompt_card),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCopyAgain) { Text(stringResource(R.string.md_import_copy_again)) }
                }
            }
            Text(stringResource(R.string.md_import_step1), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.md_import_step2), style = MaterialTheme.typography.bodyMedium)

            OutlinedTextField(
                value = state.input,
                onValueChange = onInputChange,
                label = { Text(stringResource(R.string.md_import_input_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
                maxLines = 12,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onPaste, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.md_import_paste))
                }
                Button(onClick = onEvaluate, modifier = Modifier.weight(1f), enabled = state.input.isNotBlank()) {
                    Text(stringResource(R.string.md_import_evaluate))
                }
            }

            state.fatal?.let { fatal ->
                Text(
                    text = fatalText(fatal),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            state.preview?.forEachIndexed { index, item ->
                PreviewCard(item = item, onToggle = { onToggle(index) })
            }

            state.errors.forEach { error ->
                Text(
                    text = blockErrorText(error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            val selectedCount = state.preview.orEmpty().count { it.selected }
            if (state.preview != null) {
                if (state.isSaving) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LoadingIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.md_import_saving), style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Button(
                        onClick = onSave,
                        enabled = selectedCount > 0,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(pluralStringResource(R.plurals.md_import_save_count, selectedCount, selectedCount))
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(item: MarkdownPreviewItem, onToggle: () -> Unit) {
    val recipe = item.recipe
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = item.selected, role = Role.Checkbox, onValueChange = { onToggle() }),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(checked = item.selected, onCheckedChange = null)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(recipe.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.md_import_preview_counts, recipe.ingredients.size, recipe.steps.size),
                    style = MaterialTheme.typography.bodySmall,
                )
                durationLine(recipe).takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                if (recipe.tags.isNotEmpty()) {
                    Text(recipe.tags.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                }
                recipe.warnings.forEach { warning ->
                    Text(
                        warningText(warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                if (item.duplicate) {
                    Text(
                        stringResource(R.string.md_import_duplicate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun durationLine(recipe: ParsedRecipe): String {
    val prep = stringResource(R.string.recipe_detail_prep_time)
    val cook = stringResource(R.string.recipe_detail_cook_time)
    val total = stringResource(R.string.recipe_detail_total_time)
    val parts = listOf(
        prep to formatRecipeDuration(recipe.prepTime),
        cook to formatRecipeDuration(recipe.cookTime),
        total to formatRecipeDuration(recipe.totalTime),
    )
    return parts.filter { it.second.isNotBlank() }.joinToString(" · ") { (label, value) -> "$label: $value" }
}

@Composable
private fun warningText(warning: MarkdownWarning): String = when (warning.code) {
    MarkdownWarningCode.INVALID_SERVINGS -> stringResource(R.string.md_import_warn_servings)
    MarkdownWarningCode.INVALID_DURATION -> stringResource(R.string.md_import_warn_duration, warning.detail)
    MarkdownWarningCode.UNKNOWN_VALUE -> stringResource(R.string.md_import_warn_value, warning.detail)
    MarkdownWarningCode.NUTRITION_INCOMPLETE -> stringResource(R.string.md_import_warn_nutrition)
    MarkdownWarningCode.NO_INGREDIENTS -> stringResource(R.string.md_import_warn_no_ingredients)
    MarkdownWarningCode.NO_STEPS -> stringResource(R.string.md_import_warn_no_steps)
    MarkdownWarningCode.UNKNOWN_SECTION -> stringResource(R.string.md_import_warn_section, warning.detail)
}

@Composable
private fun fatalText(fatal: MarkdownFatal): String = stringResource(
    when (fatal) {
        MarkdownFatal.EMPTY_INPUT -> R.string.md_import_fatal_empty
        MarkdownFatal.TOO_LONG -> R.string.md_import_fatal_too_long
        MarkdownFatal.TOO_MANY_RECIPES -> R.string.md_import_fatal_too_many
    },
)

@Composable
private fun blockErrorText(error: MarkdownBlockError): String {
    val reason = stringResource(
        when (error.code) {
            MarkdownErrorCode.MISSING_TITLE -> R.string.md_import_error_missing_title
            MarkdownErrorCode.EMPTY_RECIPE -> R.string.md_import_error_empty
            MarkdownErrorCode.TOO_MANY_LINES -> R.string.md_import_error_too_many_lines
        },
    )
    val title = error.title.ifBlank { stringResource(R.string.md_import_untitled) }
    return stringResource(R.string.md_import_block_error, error.blockIndex, title, reason)
}

@ThemePreviews
@Composable
private fun MarkdownImportContentPreview() {
    PreviewSurface {
        MarkdownImportContent(
            state = MarkdownImportState(
                input = "# Linsensuppe\n## Zutaten\n- 250 g Linsen",
                preview = listOf(
                    MarkdownPreviewItem(
                        recipe = ParsedRecipe(
                            blockIndex = 1,
                            name = "Linsensuppe",
                            prepTime = "PT15M",
                            totalTime = "PT1H",
                            tags = listOf("Suppe", "Eintopf"),
                            ingredients = listOf(ParsedIngredientLine(250.0, "g", "Linsen")),
                            steps = listOf("Kochen."),
                            warnings = listOf(MarkdownWarning(MarkdownWarningCode.NUTRITION_INCOMPLETE)),
                        ),
                        selected = true,
                        duplicate = false,
                    ),
                    MarkdownPreviewItem(ParsedRecipe(blockIndex = 2, name = "Pizza"), selected = false, duplicate = true),
                ),
                errors = listOf(MarkdownBlockError(3, "", MarkdownErrorCode.MISSING_TITLE)),
            ),
            onBack = {},
            onInputChange = {},
            onPaste = {},
            onCopyAgain = {},
            onEvaluate = {},
            onToggle = {},
            onSave = {},
        )
    }
}
