package com.helga.android.ui.recipes

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableBooleanStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.helga.android.R
import com.helga.android.data.local.entity.IngredientEntity

@Composable
private fun IngredientCheckRow(
    ingredient: IngredientEntity,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        val label = buildString {
            if (ingredient.quantity > 0) {
                val q = ingredient.quantity
                append(if (q % 1.0 < 0.01) q.toInt().toString() else q.toString())
                append(" ")
            }
            if (ingredient.unit.isNotBlank()) { append(ingredient.unit); append(" ") }
            append(ingredient.food)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
            ),
            color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeCookScreen(
    onBack: () -> Unit,
    viewModel: RecipeCookViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val instructions = uiState.instructions
    val ingredients = uiState.ingredients
    val checkedIds = uiState.checkedIngredientIds
    var stepIndex by remember { mutableIntStateOf(0) }
    var ingredientsExpanded by remember { mutableBooleanStateOf(false) }

    // Bildschirm während der Kochansicht eingeschaltet lassen
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.recipe?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.recipe_detail_back))
                    }
                },
            )
        },
    ) { padding ->
        if (instructions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                if (uiState.recipe == null) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        stringResource(R.string.cook_no_steps),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@Scaffold
        }

        val currentStep = instructions.getOrNull(stepIndex)
        val total = instructions.size
        val progress = (stepIndex + 1).toFloat() / total

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.cook_step_of, stepIndex + 1, total),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (ingredients.isNotEmpty()) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { ingredientsExpanded = !ingredientsExpanded }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.recipe_detail_ingredients),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        val checked = checkedIds.size
                        if (checked > 0) {
                            Text(
                                text = "$checked/${ingredients.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Icon(
                            imageVector = if (ingredientsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                        )
                    }
                    HorizontalDivider()
                    if (ingredientsExpanded) {
                        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                            items(ingredients, key = { it.id }) { ingredient ->
                                IngredientCheckRow(
                                    ingredient = ingredient,
                                    checked = ingredient.id in checkedIds,
                                    onToggle = { viewModel.toggleIngredient(ingredient.id) },
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = currentStep?.text ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.height(8.dp))

                if (stepIndex == total - 1) {
                    Button(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.cook_done))
                    }
                } else {
                    Button(
                        onClick = { stepIndex++ },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.cook_next))
                    }
                }

                if (stepIndex > 0) {
                    FilledTonalButton(
                        onClick = { stepIndex-- },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.cook_prev))
                    }
                }
            }
        }
    }
}
