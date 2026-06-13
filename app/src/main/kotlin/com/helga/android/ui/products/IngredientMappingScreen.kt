package com.helga.android.ui.products

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientMappingScreen(
    onBack: () -> Unit,
    viewModel: IngredientMappingViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    var pickerFor by remember { mutableStateOf<IngredientMappingRow?>(null) }

    pickerFor?.let { row ->
        LaunchedEffect(row.normalized) { viewModel.loadSuggestions(row.displayFood) }
        ProductPickerDialog(
            ingredientName = row.displayFood,
            suggestions = suggestions,
            onPick = { product ->
                viewModel.assignProduct(row.normalized, product)
                viewModel.clearSuggestions()
                pickerFor = null
            },
            onDismiss = {
                viewModel.clearSuggestions()
                pickerFor = null
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Zutaten zuordnen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { padding ->
        if (rows.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Keine Zutaten gefunden. Lege zuerst Rezepte an.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rows, key = { it.normalized }) { row ->
                    IngredientRowCard(
                        row = row,
                        onClick = { pickerFor = row },
                        onRemove = { row.mapping?.let { viewModel.removeMapping(it) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun IngredientRowCard(
    row: IngredientMappingRow,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.displayFood,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
                val mapping = row.mapping
                if (mapping != null) {
                    Text(
                        text = "→ ${mapping.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        text = "nicht zugeordnet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (row.mapping != null) {
                TextButton(onClick = onRemove) { Text("Entfernen") }
            }
        }
    }
}

@Composable
private fun ProductPickerDialog(
    ingredientName: String,
    suggestions: List<com.helga.android.data.local.entity.OffProductEntity>,
    onPick: (com.helga.android.data.local.entity.OffProductEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Produkt für \"$ingredientName\"") },
        text = {
            if (suggestions.isEmpty()) {
                Text(
                    text = "Keine passenden Produkte in \"Meine Produkte\". " +
                        "Scanne das Produkt zuerst über \"Meine Produkte\".",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(suggestions, key = { it.id }) { product ->
                        ListItem(
                            modifier = Modifier.clickable { onPick(product) },
                            headlineContent = { Text(product.name.ifBlank { product.barcode }) },
                            supportingContent = {
                                val parts = buildList {
                                    if (product.brand.isNotBlank()) add(product.brand)
                                    if (product.nutriScore.isNotBlank()) add("NutriScore ${product.nutriScore.uppercase()}")
                                    if (product.kcalPerUnit > 0) add("${product.kcalPerUnit.toInt()} kcal")
                                }
                                if (parts.isNotEmpty()) Text(parts.joinToString(" · "))
                            },
                            trailingContent = {
                                Icon(Icons.Filled.Check, contentDescription = "Auswählen")
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Schließen") }
        },
    )
}
