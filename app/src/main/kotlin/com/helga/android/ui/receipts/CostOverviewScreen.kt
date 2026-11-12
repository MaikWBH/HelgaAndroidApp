package com.helga.android.ui.receipts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.helga.android.R
import com.helga.android.data.model.KeyAmount
import com.helga.android.data.model.ProductCategory
import com.helga.android.data.model.StatsRange
import com.helga.android.ui.common.formatEuro
import com.helga.android.ui.components.PreviewSurface
import com.helga.android.ui.components.ThemePreviews
import com.helga.android.ui.receipts.charts.ChartBar
import com.helga.android.ui.receipts.charts.ChartMath
import com.helga.android.ui.receipts.charts.ChartRow
import com.helga.android.ui.receipts.charts.HorizontalBarChart
import com.helga.android.ui.receipts.charts.ShareDonutChart
import com.helga.android.ui.receipts.charts.SpendingBarChart

@Composable
fun CostOverviewScreen(
    onBack: () -> Unit,
    onProductClick: (String) -> Unit = {},
    onAllProductsClick: () -> Unit = {},
    viewModel: CostOverviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CostOverviewContent(
        state = state,
        onBack = onBack,
        onGranularityChange = viewModel::setGranularity,
        onRangeChange = viewModel::setRange,
        onSaveBudget = viewModel::saveBudget,
        onProductClick = onProductClick,
        onAllProductsClick = onAllProductsClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CostOverviewContent(
    state: CostOverviewUiState,
    onBack: () -> Unit,
    onGranularityChange: (Granularity) -> Unit,
    onRangeChange: (StatsRange) -> Unit,
    onSaveBudget: (Double, Double) -> Unit,
    onProductClick: (String) -> Unit,
    onAllProductsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showBudgetDialog by rememberSaveable { mutableStateOf(false) }

    if (showBudgetDialog) {
        BudgetEditDialog(
            currentAmount = state.budget,
            currentWarnThreshold = state.warnThreshold,
            onConfirm = { amount, warnThreshold ->
                onSaveBudget(amount, warnThreshold)
                showBudgetDialog = false
            },
            onDismiss = { showBudgetDialog = false },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cost_overview_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.provisionalCount > 0) {
                    item(key = "hint") {
                        Text(
                            pluralStringResource(R.plurals.cost_provisional_hint, state.provisionalCount, state.provisionalCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item(key = "budget") {
                    BudgetCard(
                        budgetAmount = state.budget,
                        warnThreshold = state.warnThreshold,
                        monthSpending = state.monthSpending,
                        formatCurrency = ::formatEuro,
                        onEdit = { showBudgetDialog = true },
                    )
                }
                if (state.empty) {
                    item(key = "empty") { EmptyStats() }
                } else {
                    item(key = "spending") {
                        SpendingCard(state = state, onGranularityChange = onGranularityChange)
                    }
                    item(key = "range") { RangeChips(selected = state.range, onSelect = onRangeChange) }
                    item(key = "stores") { StoresCard(state = state) }
                    item(key = "categories") { CategoriesCard(state = state) }
                    item(key = "products") {
                        ProductsCard(state = state, onProductClick = onProductClick, onAllProductsClick = onAllProductsClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun EmptyStats() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(R.string.cost_stats_empty),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpendingCard(state: CostOverviewUiState, onGranularityChange: (Granularity) -> Unit) {
    val weekly = state.granularity == Granularity.WEEK
    SectionCard(stringResource(R.string.cost_section_spending)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            Granularity.entries.forEachIndexed { index, granularity ->
                SegmentedButton(
                    selected = state.granularity == granularity,
                    onClick = { onGranularityChange(granularity) },
                    shape = SegmentedButtonDefaults.itemShape(index, Granularity.entries.size),
                ) {
                    Text(
                        stringResource(
                            if (granularity == Granularity.MONTH) R.string.stats_period_month else R.string.stats_period_week
                        )
                    )
                }
            }
        }
        val last = state.bars.lastOrNull()?.amount ?: 0.0
        SpendingBarChart(
            bars = state.bars.mapIndexed { index, bar ->
                ChartBar(ChartMath.periodLabel(bar.period), bar.amount, highlighted = index == state.bars.lastIndex)
            },
            budget = state.barBudget,
            description = stringResource(
                if (weekly) R.string.cost_chart_weeks_description else R.string.cost_chart_months_description,
                formatEuro(last), formatEuro(state.average),
            ),
        )
        Text(
            stringResource(
                if (weekly) R.string.cost_average_week else R.string.cost_average_month,
                formatEuro(state.average),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RangeChips(selected: StatsRange, onSelect: (StatsRange) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        StatsRange.entries.forEach { range ->
            FilterChip(
                selected = selected == range,
                onClick = { onSelect(range) },
                label = {
                    Text(
                        stringResource(
                            when (range) {
                                StatsRange.THIS_MONTH -> R.string.cost_range_this_month
                                StatsRange.THREE_MONTHS -> R.string.cost_range_three_months
                                StatsRange.TWELVE_MONTHS -> R.string.cost_range_twelve_months
                                StatsRange.ALL -> R.string.cost_range_all
                            }
                        )
                    )
                },
            )
        }
    }
}

@Composable
private fun StoresCard(state: CostOverviewUiState) {
    val unknown = stringResource(R.string.receipt_unknown_store)
    val other = stringResource(R.string.cost_other)
    SectionCard(stringResource(R.string.cost_section_stores)) {
        val slices = ChartMath.shareSlices(
            state.stores.map { it.copy(label = it.label.ifBlank { unknown }) }, maxSlices = 5, otherLabel = other,
        )
        if (slices.isEmpty()) {
            NoDataText()
        } else {
            val top = slices.first()
            ShareDonutChart(
                slices = slices,
                centerText = formatEuro(state.stores.sumOf { it.amount }),
                description = stringResource(R.string.cost_chart_stores_description, top.label, formatEuro(top.amount)),
            )
        }
    }
}

@Composable
private fun CategoriesCard(state: CostOverviewUiState) {
    SectionCard(stringResource(R.string.cost_section_categories)) {
        val rows = state.categories.filter { it.amount > 0.0 }.map {
            ChartRow(it.key, stringResource(ProductCategory.fromKey(it.key).labelRes()), it.amount)
        }
        if (rows.isEmpty()) {
            NoDataText()
        } else {
            HorizontalBarChart(
                rows = rows,
                description = stringResource(R.string.cost_chart_categories_description, rows.first().label, formatEuro(rows.first().value)),
            )
            if (state.categoryDiscount > 0.0) {
                Text(
                    stringResource(R.string.cost_category_discount_note, formatEuro(state.categoryDiscount)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProductsCard(state: CostOverviewUiState, onProductClick: (String) -> Unit, onAllProductsClick: () -> Unit) {
    SectionCard(stringResource(R.string.cost_section_top_products)) {
        val rows = state.topProducts.filter { it.amount > 0.0 }.map {
            ChartRow(it.key, it.label.ifBlank { it.key }, it.amount, stringResource(R.string.cost_times_bought, it.count))
        }
        if (rows.isEmpty()) {
            NoDataText()
        } else {
            HorizontalBarChart(
                rows = rows,
                description = stringResource(R.string.cost_chart_products_description, rows.first().label, formatEuro(rows.first().value)),
                onRowClick = onProductClick,
            )
        }
        TextButton(onClick = onAllProductsClick) { Text(stringResource(R.string.cost_all_products)) }
    }
}

@Composable
private fun NoDataText() {
    Text(
        stringResource(R.string.cost_no_receipts_range),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ── Previews ─────────────────────────────────────────────────────────────────

private fun sampleState() = CostOverviewUiState(
    loaded = true, empty = false, provisionalCount = 1,
    bars = (1..12).map { com.helga.android.data.model.PeriodAmount("2026-%02d".format(it), it * 12.0, it) },
    barBudget = 100.0, average = 78.0, monthSpending = 144.0, budget = 150.0,
    stores = listOf(
        KeyAmount("a", "REWE", 120.0, 0.0, 4), KeyAmount("b", "Aldi", 80.0, 0.0, 3), KeyAmount("c", "dm", 25.0, 0.0, 1),
    ),
    categories = listOf(
        KeyAmount("milch_eier", "", 82.4, 0.0, 12), KeyAmount("obst_gemuese", "", 61.0, 0.0, 9),
        KeyAmount("getraenke", "", 23.5, 0.0, 3),
    ),
    categoryDiscount = 4.5,
    topProducts = listOf(KeyAmount("milch", "Milch", 32.4, 18.0, 12), KeyAmount("brot", "Brot", 21.0, 6.0, 6)),
)

@ThemePreviews
@Composable
private fun CostOverviewPreview() = PreviewSurface {
    CostOverviewContent(
        state = sampleState(), onBack = {}, onGranularityChange = {}, onRangeChange = {},
        onSaveBudget = { _, _ -> }, onProductClick = {}, onAllProductsClick = {},
    )
}

@ThemePreviews
@Composable
private fun CostOverviewEmptyPreview() = PreviewSurface {
    CostOverviewContent(
        state = CostOverviewUiState(loaded = true, empty = true), onBack = {}, onGranularityChange = {},
        onRangeChange = {}, onSaveBudget = { _, _ -> }, onProductClick = {}, onAllProductsClick = {},
    )
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
