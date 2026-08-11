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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.helga.android.data.local.entity.ReceiptEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

internal fun formatReceiptDate(epochMs: Long): String =
    if (epochMs <= 0) "—"
    else dateFormatter.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

/** Ganze Mengen ohne Nachkommastellen ("2"), sonst mit zwei ("0.5"). */
internal fun formatQuantity(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else String.format("%.2f", value)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptListScreen(
    onBack: () -> Unit,
    onReceiptClick: (String) -> Unit,
    onScanClick: () -> Unit,
    onCostOverviewClick: () -> Unit,
    onProductsClick: () -> Unit,
    viewModel: ReceiptListViewModel = hiltViewModel(),
) {
    val receipts = viewModel.receipts.collectAsState().value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einkäufe") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = onProductsClick) {
                        Icon(Icons.Filled.TrendingUp, contentDescription = "Preise")
                    }
                    IconButton(onClick = onCostOverviewClick) {
                        Icon(Icons.Filled.BarChart, contentDescription = "Kostenübersicht")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScanClick,
                icon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
                text = { Text("Scannen") },
            )
        },
    ) { padding ->
        if (receipts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Noch keine Kassenbons gescannt")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(receipts, key = { it.id }) { receipt ->
                    ReceiptRow(receipt = receipt, onClick = { onReceiptClick(receipt.id) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiptRow(
    receipt: ReceiptEntity,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    receipt.storeName.ifBlank { "Unbekannter Markt" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    formatReceiptDate(receipt.purchaseDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                String.format("€%.2f", receipt.totalAmount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
