package com.helga.android.ui.receipts

import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import com.helga.android.ui.components.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.helga.android.R
import com.helga.android.data.model.CostByDate
import com.helga.android.data.model.CostByStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CostOverviewScreen(
    onBack: () -> Unit,
    viewModel: CostOverviewViewModel = hiltViewModel(),
) {
    val period = viewModel.period.collectAsStateWithLifecycle().value
    val costByStore = viewModel.costByStore.collectAsStateWithLifecycle().value
    val costByDate = viewModel.costByDate.collectAsStateWithLifecycle().value
    val totalCost = viewModel.totalCost.collectAsStateWithLifecycle().value
    val receiptCount = viewModel.receiptCount.collectAsStateWithLifecycle().value
    val isLoading = viewModel.isLoading.collectAsStateWithLifecycle().value
    val maxCost = viewModel.getMaxCost()
    val budget = viewModel.budget.collectAsStateWithLifecycle().value
    val monthSpending = viewModel.monthSpending.collectAsStateWithLifecycle().value

    var showBudgetDialog by remember { mutableStateOf(false) }

    if (showBudgetDialog) {
        BudgetEditDialog(
            currentAmount = budget?.amount ?: 0.0,
            currentWarnThreshold = budget?.warnThreshold ?: 0.8,
            onConfirm = { amount, warnThreshold ->
                viewModel.saveBudget(amount, warnThreshold)
                showBudgetDialog = false
            },
            onDismiss = { showBudgetDialog = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cost_overview_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
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
                                    TimePeriod.WEEK -> stringResource(R.string.stats_period_week)
                                    TimePeriod.MONTH -> stringResource(R.string.stats_period_month)
                                    TimePeriod.ALL -> stringResource(R.string.recipes_filter_all)
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
                    LoadingIndicator()
                }
            } else {
                // Cost by Store
                if (costByStore.isNotEmpty()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.cost_overview_by_store),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        costByStore.forEach { store ->
                            CostBarItem(
                                label = store.storeName.ifBlank { store.storeId.ifBlank { stringResource(R.string.receipt_unknown_store) } },
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
                            stringResource(R.string.cost_overview_by_date),
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
                        Text(stringResource(R.string.cost_overview_empty))
                    }
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
                stringResource(R.string.cost_overview_total),
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
                stringResource(R.string.cost_receipts_summary, receiptCount),
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
                    if (receiptCount != 1) stringResource(R.string.cost_bon_other, receiptCount) else stringResource(R.string.cost_bon_one, receiptCount),
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
                    stringResource(R.string.cost_budget_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = onContainer,
                )
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.cost_budget_edit),
                        tint = onContainer,
                    )
                }
            }

            if (notSet) {
                Text(
                    stringResource(R.string.cost_budget_not_set),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainer,
                    modifier = Modifier.padding(top = 4.dp),
                )
                return@Column
            }

            Text(
                stringResource(R.string.cost_budget_of, formatCurrency(monthSpending), formatCurrency(budgetAmount)),
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
                isOver -> stringResource(R.string.cost_budget_over, formatCurrency(-remaining))
                isWarn -> stringResource(R.string.cost_budget_warn, formatCurrency(remaining))
                else -> stringResource(R.string.cost_budget_left, formatCurrency(remaining))
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
    currentWarnThreshold: Double,
    onConfirm: (amount: Double, warnThreshold: Double) -> Unit,
    onDismiss: () -> Unit,
) {
    // Vorbelegung ohne Nachkomma-Null, wenn ganzzahlig (z. B. "600" statt "600.0").
    val initial = when {
        currentAmount <= 0.0 -> ""
        currentAmount % 1.0 == 0.0 -> currentAmount.toLong().toString()
        else -> currentAmount.toString()
    }
    var text by remember { mutableStateOf(initial) }
    var warnThreshold by remember { mutableStateOf(currentWarnThreshold.toFloat()) }
    val parsed = text.replace(',', '.').toDoubleOrNull()
    val valid = text.isBlank() || (parsed != null && parsed >= 0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cost_budget_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.cost_budget_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.cost_budget_amount)) },
                    singleLine = true,
                    isError = !valid,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.cost_budget_threshold, (warnThreshold * 100).toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = warnThreshold,
                    onValueChange = { warnThreshold = it },
                    valueRange = 0.5f..1.0f,
                    steps = 9,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(if (text.isBlank()) 0.0 else (parsed ?: 0.0), warnThreshold.toDouble()) },
                enabled = valid,
            ) { Text(stringResource(R.string.recipe_form_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.recipe_delete_confirm_cancel)) }
        },
    )
}
