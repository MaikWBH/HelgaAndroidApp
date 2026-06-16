package com.helga.android.ui.receipts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
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
    val budget = viewModel.budget.collectAsState().value
    val monthSpending = viewModel.monthSpending.collectAsState().value

    var showBudgetDialog by remember { mutableStateOf(false) }

    if (showBudgetDialog) {
        BudgetEditDialog(
            currentAmount = budget?.amount ?: 0.0,
            onConfirm = { amount ->
                viewModel.saveBudget(amount)
                showBudgetDialog = false
            },
            onDismiss = { showBudgetDialog = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        // Monatsbudget + Warnung
        BudgetCard(
            budgetAmount = budget?.amount ?: 0.0,
            warnThreshold = budget?.warnThreshold ?: 0.8,
            monthSpending = monthSpending,
            formatCurrency = viewModel::formatCurrency,
            onEdit = { showBudgetDialog = true },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
        )

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

/**
 * Monatsbudget-Karte mit farbcodierter Warnung. Vergleicht die Ausgaben des
 * laufenden Kalendermonats mit dem gemeinsamen Budget:
 * grün = im Rahmen, gelb = Warnschwelle überschritten, rot = über Budget.
 */
@Composable
private fun BudgetCard(
    budgetAmount: Double,
    warnThreshold: Double,
    monthSpending: Double,
    formatCurrency: (Double) -> String,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val notSet = budgetAmount <= 0.0
    val ratio = if (notSet) 0.0 else monthSpending / budgetAmount
    val isOver = !notSet && ratio >= 1.0
    val isWarn = !notSet && !isOver && ratio >= warnThreshold

    val container = when {
        notSet -> MaterialTheme.colorScheme.surfaceVariant
        isOver -> MaterialTheme.colorScheme.errorContainer
        isWarn -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val onContainer = when {
        notSet -> MaterialTheme.colorScheme.onSurfaceVariant
        isOver -> MaterialTheme.colorScheme.onErrorContainer
        isWarn -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(color = container, shape = MaterialTheme.shapes.medium)
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Monatsbudget",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = onContainer,
                )
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Budget bearbeiten",
                        tint = onContainer,
                    )
                }
            }

            if (notSet) {
                Text(
                    "Noch kein Budget festgelegt. Tippe auf das Stift-Symbol, um ein gemeinsames Monatsbudget zu setzen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainer,
                    modifier = Modifier.padding(top = 4.dp),
                )
                return@Column
            }

            Text(
                "${formatCurrency(monthSpending)} von ${formatCurrency(budgetAmount)}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = onContainer,
                modifier = Modifier.padding(top = 4.dp),
            )

            LinearProgressIndicator(
                progress = ratio.coerceIn(0.0, 1.0).toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .padding(top = 8.dp),
                color = onContainer,
                trackColor = onContainer.copy(alpha = 0.25f),
            )

            val remaining = budgetAmount - monthSpending
            val statusText = when {
                isOver -> "⚠️ ${formatCurrency(-remaining)} über dem Budget!"
                isWarn -> "Achtung: nur noch ${formatCurrency(remaining)} übrig"
                else -> "Noch ${formatCurrency(remaining)} übrig"
            }
            Text(
                statusText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isOver || isWarn) FontWeight.SemiBold else FontWeight.Normal,
                color = onContainer,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun BudgetEditDialog(
    currentAmount: Double,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    // Vorbelegung ohne Nachkomma-Null, wenn ganzzahlig (z. B. "600" statt "600.0").
    val initial = when {
        currentAmount <= 0.0 -> ""
        currentAmount % 1.0 == 0.0 -> currentAmount.toLong().toString()
        else -> currentAmount.toString()
    }
    var text by remember { mutableStateOf(initial) }
    val parsed = text.replace(',', '.').toDoubleOrNull()
    val valid = text.isBlank() || (parsed != null && parsed >= 0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Monatsbudget") },
        text = {
            Column {
                Text(
                    "Gemeinsames Budget pro Kalendermonat in Euro. Der Wert wird mit dem anderen Gerät synchronisiert. 0 oder leer = kein Budget.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Betrag (€)") },
                    singleLine = true,
                    isError = !valid,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(if (text.isBlank()) 0.0 else (parsed ?: 0.0)) },
                enabled = valid,
            ) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}
