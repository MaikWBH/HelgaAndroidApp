package com.helga.android.ui.receipts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.helga.android.data.local.dao.CostByDate
import com.helga.android.data.local.dao.CostByStore

@Composable
fun CostOverviewScreen(
    viewModel: CostOverviewViewModel = hiltViewModel(),
) {
    val period = viewModel.period.collectAsState().value
    val costByStore = viewModel.costByStore.collectAsState().value
    val costByDate = viewModel.costByDate.collectAsState().value
    val totalCost = viewModel.totalCost.collectAsState().value
    val receiptCount = viewModel.receiptCount.collectAsState().value
    val isLoading = viewModel.isLoading.collectAsState().value
    val maxCost = viewModel.getMaxCost()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        // Summary Card
        CostSummaryCard(
            totalCost = viewModel.formatCurrency(totalCost),
            receiptCount = receiptCount,
            modifier = Modifier.padding(16.dp)
        )

        // Period Selector
        TabRow(
            selectedTabIndex = period.ordinal,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            TimePeriod.values().forEach { tab ->
                Tab(
                    selected = period == tab,
                    onClick = { viewModel.setPeriod(tab) },
                    text = {
                        Text(
                            when (tab) {
                                TimePeriod.WEEK -> "Woche"
                                TimePeriod.MONTH -> "Monat"
                                TimePeriod.ALL -> "Alle"
                            }
                        )
                    }
                )
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // Cost by Store
            if (costByStore.isNotEmpty()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Ausgaben pro Markt",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    costByStore.forEach { store ->
                        CostBarItem(
                            label = store.storeName.ifBlank { store.storeId.ifBlank { "Unbekannter Markt" } },
                            amount = viewModel.formatCurrency(store.totalAmount),
                            progress = store.totalAmount / maxCost,
                            receiptCount = store.receiptCount,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            }

            // Cost by Date
            if (costByDate.isNotEmpty()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Verlauf nach Datum",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    costByDate.forEach { dateEntry ->
                        CostBarItem(
                            label = viewModel.formatDate(dateEntry.date),
                            amount = viewModel.formatCurrency(dateEntry.totalAmount),
                            progress = dateEntry.totalAmount / maxCost,
                            receiptCount = dateEntry.receiptCount,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            }

            if (costByStore.isEmpty() && costByDate.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Keine Kassenbons vorhanden")
                }
            }
        }
    }
}

@Composable
private fun CostSummaryCard(
    totalCost: String,
    receiptCount: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium
            )
            .padding(16.dp)
    ) {
        Column {
            Text(
                "Gesamtausgaben",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                totalCost,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                "$receiptCount Kassenbons",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun CostBarItem(
    label: String,
    amount: String,
    progress: Double,
    receiptCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    "$receiptCount Bon${if (receiptCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                amount,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        LinearProgressIndicator(
            progress = progress.coerceIn(0.0, 1.0).toFloat(),
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
