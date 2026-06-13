package com.helga.android.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.helga.android.data.local.entity.OffProductEntity
import com.helga.android.ui.components.BarcodeScanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyProductsScreen(
    onBack: () -> Unit,
    viewModel: MyProductsViewModel = hiltViewModel(),
) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val scannedProduct by viewModel.scannedProduct.collectAsStateWithLifecycle()
    var showScanner by remember { mutableStateOf(false) }

    if (showScanner) {
        BarcodeScanner(
            onBarcodeDetected = { barcode ->
                showScanner = false
                viewModel.onBarcodeScanned(barcode)
            },
            onDismiss = { showScanner = false },
            modifier = Modifier.fillMaxSize(),
        )
    }

    scannedProduct?.let { product ->
        AddToCatalogDialog(
            product = product,
            onConfirm = { viewModel.confirmAddToCatalog() },
            onDismiss = { viewModel.dismissScanned() },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meine Produkte") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showScanner = true },
                icon = { Icon(Icons.Filled.QrCodeScanner, contentDescription = null) },
                text = { Text("Produkt scannen") },
            )
        },
    ) { padding ->
        if (products.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Noch keine Produkte. Scanne deine regelmäßig gekauften Artikel, " +
                        "um sie hier mit Nährwerten zu sammeln.",
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
                items(products, key = { it.id }) { product ->
                    MyProductCard(
                        product = product,
                        onRemove = { viewModel.removeFromCatalog(product) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MyProductCard(
    product: OffProductEntity,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (product.nutriScore.isNotBlank()) {
                NutriScoreBadge(product.nutriScore)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = product.name.ifBlank { product.barcode },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (product.brand.isNotBlank()) {
                    Text(
                        text = product.brand,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = buildString {
                        if (product.kcalPerUnit > 0) append("${product.kcalPerUnit.toInt()} kcal · ")
                        append("Eiweiß ${product.proteins.toInt()}g · ")
                        append("Fett ${product.fats.toInt()}g · ")
                        append("KH ${product.carbs.toInt()}g")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = "Entfernen")
            }
        }
    }
}

@Composable
private fun NutriScoreBadge(score: String) {
    Surface(
        modifier = Modifier.size(36.dp),
        shape = RoundedCornerShape(4.dp),
        color = when (score.uppercase()) {
            "A" -> Color(0xFF4CAF50)
            "B" -> Color(0xFF8BC34A)
            "C" -> Color(0xFFFFC107)
            "D" -> Color(0xFFFF9800)
            "E" -> Color(0xFFF44336)
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = score.uppercase(),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun AddToCatalogDialog(
    product: OffProductEntity,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📦 ${product.name.ifBlank { product.barcode }}") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (product.brand.isNotBlank()) {
                    Text("Marke: ${product.brand}", style = MaterialTheme.typography.bodySmall)
                }
                if (product.nutriScore.isNotBlank()) {
                    Text("NutriScore: ${product.nutriScore.uppercase()}", style = MaterialTheme.typography.bodySmall)
                }
                if (product.kcalPerUnit > 0) {
                    Text("${product.kcalPerUnit.toInt()} kcal / 100 g", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Eiweiß ${product.proteins.toInt()}g · Fett ${product.fats.toInt()}g · KH ${product.carbs.toInt()}g",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("⭐ Zu meinen Produkten") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}
