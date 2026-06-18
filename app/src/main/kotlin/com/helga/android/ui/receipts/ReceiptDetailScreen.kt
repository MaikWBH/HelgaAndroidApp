package com.helga.android.ui.receipts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.helga.android.data.local.ScanSource
import com.helga.android.data.local.entity.ReceiptEntity
import com.helga.android.data.local.entity.ReceiptItemEntity
import com.helga.android.data.local.toScanSource
import com.helga.android.data.util.ReceiptItemNormalizer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptDetailScreen(
    onBack: () -> Unit,
    onProductClick: (String) -> Unit = {},
    viewModel: ReceiptDetailViewModel = hiltViewModel(),
) {
    val receipt = viewModel.receipt.collectAsState().value
    val items = viewModel.items.collectAsState().value
    val serverUrl = viewModel.serverUrl.collectAsState().value
    val reconcileEnabled = viewModel.reconcileEnabled.collectAsState().value
    val reconcileState = viewModel.reconcileState.collectAsState().value
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Kassenbon löschen?") },
            text = { Text("Der Bon wird lokal gelöscht und beim nächsten Sync entfernt.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.delete(onDone = onBack)
                }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Abbrechen") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(receipt?.storeName?.ifBlank { "Kassenbon" } ?: "Kassenbon") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Löschen")
                    }
                },
            )
        },
    ) { padding ->
        if (receipt == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Bon nicht gefunden")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                item {
                    ReceiptPhoto(receipt = receipt, serverUrl = serverUrl)
                }
                item {
                    SummarySection(receipt = receipt, itemCount = items.size)
                }
                if (reconcileEnabled && receipt.shoppingListId.isNotBlank()) {
                    item {
                        ReconcileSection(
                            state = reconcileState,
                            alreadyReconciled = receipt.status == "reconciled",
                            onReconcile = viewModel::reconcile,
                        )
                    }
                }
                item { HorizontalDivider() }
                items(items, key = { it.id }) { item ->
                    ReceiptItemRow(item = item, onProductClick = onProductClick)
                }
            }
        }
    }
}

@Composable
private fun ReceiptPhoto(receipt: ReceiptEntity, serverUrl: String) {
    val imageModel: Any? = when {
        receipt.localImageUri.isNotBlank() -> receipt.localImageUri
        receipt.imagePath.isNotBlank() && serverUrl.isNotBlank() ->
            "${serverUrl.trimEnd('/')}/api/images/${receipt.imagePath}"
        else -> null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (imageModel != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageModel)
                    .crossfade(true)
                    .build(),
                contentDescription = "Kassenbon-Foto",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.ReceiptLong,
                contentDescription = null,
                modifier = Modifier.height(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummarySection(receipt: ReceiptEntity, itemCount: Int) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            receipt.storeName.ifBlank { "Unbekannter Markt" },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            formatReceiptDate(receipt.purchaseDate),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            when (receipt.source.toScanSource()) {
                ScanSource.AI -> "Gelesen per KI-Vision"
                ScanSource.ON_DEVICE -> "Gelesen per On-Device-OCR"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("$itemCount Artikel", style = MaterialTheme.typography.bodyLarge)
            Text(
                String.format("Summe: €%.2f", receipt.totalAmount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ReconcileSection(
    state: ReconcileState,
    alreadyReconciled: Boolean,
    onReconcile: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Button(
            onClick = onReconcile,
            enabled = state !is ReconcileState.Running,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state is ReconcileState.Running) {
                CircularProgressIndicator(
                    modifier = Modifier.height(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(if (alreadyReconciled) "Erneut mit Einkaufsliste abgleichen" else "Mit Einkaufsliste abgleichen")
            }
        }
        when (state) {
            is ReconcileState.Error -> Text(
                state.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            is ReconcileState.Done -> ReconcileResult(state.outcome)
            else -> Unit
        }
    }
}

@Composable
private fun ReconcileResult(outcome: com.helga.android.data.repository.ReconcileOutcome) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Text(
            "✓ ${outcome.matchedCount} Treffer",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        if (outcome.unexpectedNames.isNotEmpty()) {
            Text(
                "Ungeplant gekauft:",
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
            outcome.unexpectedNames.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
        if (outcome.missingNames.isNotEmpty()) {
            Text(
                "Nicht gefunden (abgehakt, fehlt auf Bon):",
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
            outcome.missingNames.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun ReceiptItemRow(
    item: ReceiptItemEntity,
    onProductClick: (String) -> Unit = {},
) {
    val accent = when (item.matchStatus) {
        "matched" -> MaterialTheme.colorScheme.primary
        "unexpected" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val key = ReceiptItemNormalizer.normalize(item.name.ifBlank { item.rawText })
                if (key.isNotBlank()) onProductClick(key)
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.name.ifBlank { item.rawText },
                style = MaterialTheme.typography.bodyMedium,
                color = accent,
            )
            Text(
                "${formatQuantity(item.quantity)} × ${String.format("€%.2f", item.unitPrice)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            String.format("€%.2f", item.totalPrice),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
