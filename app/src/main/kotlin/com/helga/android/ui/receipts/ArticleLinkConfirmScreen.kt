package com.helga.android.ui.receipts

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
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.helga.android.data.local.entity.OffProductEntity
import com.helga.android.data.repository.SuggestedMatch
import com.helga.android.data.util.AllergyChecker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleLinkConfirmScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: ArticleLinkConfirmViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsState().value
    val confirmedProduct = viewModel.confirmedProduct.collectAsState().value
    val linkSaved = viewModel.linkSaved.collectAsState().value
    val searchQuery = viewModel.searchQuery.collectAsState().value
    val searchResults = viewModel.searchResults.collectAsState().value
    val allergies = viewModel.preferences.allergies.collectAsState(initial = emptyList()).value

    LaunchedEffect(linkSaved) {
        if (linkSaved) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.displayName.ifBlank { "Artikel" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { padding ->
        val contentModifier = Modifier.fillMaxSize().padding(padding)
        val product = confirmedProduct
        when {
            product != null -> PackageGramsFallback(
                productName = product.name,
                onSave = viewModel::saveManualPackageGrams,
                onSkip = viewModel::skipManualPackageGrams,
                modifier = contentModifier,
            )
            uiState is ArticleLinkUiState.Loading -> Box(contentModifier, Alignment.Center) {
                CircularProgressIndicator()
            }
            uiState is ArticleLinkUiState.AlreadyLinked -> Box(contentModifier, Alignment.Center) {
                Text("Bereits zugeordnet zu „${uiState.linkedDisplayName}“")
            }
            uiState is ArticleLinkUiState.Error -> Box(contentModifier, Alignment.Center) {
                Text("KI-Vorschlag fehlgeschlagen. Bitte später erneut versuchen.")
            }
            uiState is ArticleLinkUiState.Suggestion -> SuggestionContent(
                match = uiState.match,
                allergies = allergies,
                searchQuery = searchQuery,
                searchResults = searchResults,
                onSearch = viewModel::search,
                onConfirm = viewModel::confirm,
                modifier = contentModifier,
            )
        }
    }
}

@Composable
private fun SuggestionContent(
    match: SuggestedMatch,
    allergies: List<String>,
    searchQuery: String,
    searchResults: List<OffProductEntity>,
    onSearch: (String) -> Unit,
    onConfirm: (OffProductEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        if (match.cleanedName.isNotBlank()) {
            item {
                Text(
                    "KI-Vorschlag für: „${match.cleanedName}“",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        if (match.products.isEmpty()) {
            item {
                Text(
                    "Keine Treffer gefunden",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        items(match.products) { product ->
            CandidateCard(
                product = product,
                allergenWarnings = AllergyChecker.hasAllergens(product, allergies),
                onConfirm = { onConfirm(product) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        item {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            Text(
                "Anderes Produkt suchen",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearch,
                placeholder = { Text("Produkt suchen…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
        items(searchResults) { product ->
            CandidateCard(
                product = product,
                allergenWarnings = AllergyChecker.hasAllergens(product, allergies),
                onConfirm = { onConfirm(product) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun CandidateCard(
    product: OffProductEntity,
    allergenWarnings: List<String>,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                product.name.ifBlank { "Unbekanntes Produkt" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (product.brand.isNotBlank()) {
                Text(
                    product.brand,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    if (product.kcalPerUnit > 0) "${product.kcalPerUnit.toInt()} kcal/100g" else "Kcal unbekannt",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    if (product.packageGrams > 0) "${formatQuantity(product.packageGrams)} g Packung" else "Packungsgröße unbekannt",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (allergenWarnings.isNotEmpty()) {
                Text(
                    "⚠️ Allergene: ${allergenWarnings.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("Bestätigen")
            }
        }
    }
}

@Composable
private fun PackageGramsFallback(
    productName: String,
    onSave: (Double) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }
    val parsed = input.replace(',', '.').toDoubleOrNull()

    Column(modifier = modifier.padding(16.dp)) {
        Text(
            "Packungsgröße für „$productName“ konnte nicht automatisch ermittelt werden.",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Ohne Packungsgröße zählt der Artikel nur „pro 100 g“ und fließt nicht in die Gesamtsumme ein.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Packungsgröße (g)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onSkip) { Text("Überspringen") }
            Button(onClick = { parsed?.let(onSave) }, enabled = parsed != null && parsed > 0.0) {
                Text("Speichern")
            }
        }
    }
}
