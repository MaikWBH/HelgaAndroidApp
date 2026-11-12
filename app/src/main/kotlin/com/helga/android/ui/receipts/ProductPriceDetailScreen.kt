package com.helga.android.ui.receipts

import com.helga.android.R
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.helga.android.ui.components.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import com.helga.android.data.model.TrendPoint
import com.helga.android.ui.receipts.charts.ChartMath
import com.helga.android.ui.receipts.charts.ChartPoint
import com.helga.android.ui.receipts.charts.TrendLineChart
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.helga.android.data.repository.ProductPriceHistory
import com.helga.android.data.repository.ProductPricePoint
import com.helga.android.data.repository.StoreBestPrice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductPriceDetailScreen(
    onBack: () -> Unit,
    viewModel: ProductPriceDetailViewModel = hiltViewModel(),
) {
    val history = viewModel.history.collectAsStateWithLifecycle().value
    val trend = viewModel.trend.collectAsStateWithLifecycle().value
    val isLoading = viewModel.isLoading.collectAsStateWithLifecycle().value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(history?.displayName ?: stringResource(R.string.price_product_fallback)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.recipe_detail_back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { LoadingIndicator() }

            history == null && trend.size < 2 -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.price_no_data)) }

            else -> ProductPriceContent(
                history = history,
                trend = trend,
                formatCurrency = viewModel::formatCurrency,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@Composable
private fun ProductPriceContent(
    history: ProductPriceHistory?,
    trend: List<TrendPoint>,
    formatCurrency: (Double) -> String,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        if (trend.size >= 2) {
            item(key = "trend") {
                MonthlyTrendSection(
                    displayName = history?.displayName ?: stringResource(R.string.price_product_fallback),
                    trend = trend,
                    formatCurrency = formatCurrency,
                )
            }
        }
        if (history == null) {
            item(key = "only_trend_note") {
                Text(
                    stringResource(R.string.price_single_purchases_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            return@LazyColumn
        }
        item {
            PriceHeaderCard(history = history, formatCurrency = formatCurrency)
        }
        if (history.points.size >= 2) {
            item {
                PriceTrendSection(history = history, formatCurrency = formatCurrency)
            }
        }
        if (history.storeComparison.isNotEmpty()) {
            item {
                StoreComparisonSection(
                    stores = history.storeComparison,
                    maxPrice = history.maxPrice,
                    formatCurrency = formatCurrency,
                )
            }
        }
        item {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Text(
                stringResource(R.string.price_single_purchases_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        items(history.points, key = { "${it.purchaseDate}_${it.storeId}" }) { point ->
            PricePointRow(point = point, formatCurrency = formatCurrency)
        }
    }
}

@Composable
private fun MonthlyTrendSection(
    displayName: String,
    trend: List<TrendPoint>,
    formatCurrency: (Double) -> String,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            stringResource(R.string.price_trend_all_months_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        TrendLineChart(
            points = trend.map { ChartPoint(label = ChartMath.periodLabel(it.period), value = it.avgUnitPrice) },
            description = stringResource(
                R.string.price_trend_all_months_description,
                displayName,
                trend.size,
                formatCurrency(trend.last().avgUnitPrice),
            ),
        )
    }
}

@Composable
private fun PriceHeaderCard(
    history: ProductPriceHistory,
    formatCurrency: (Double) -> String,
) {
    val cheapestStore = history.storeComparison.firstOrNull { it.isCheapest }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium,
            )
            .padding(16.dp),
    ) {
        Column {
            if (cheapestStore != null) {
                Text(
                    stringResource(R.string.product_prices_cheapest_store),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "${cheapestStore.storeName} · ${formatCurrency(cheapestStore.bestPrice)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                PriceStat("Ø", formatCurrency(history.avgPrice))
                PriceStat(stringResource(R.string.price_stat_min), formatCurrency(history.minPrice))
                PriceStat(stringResource(R.string.price_stat_max), formatCurrency(history.maxPrice))
            }
        }
    }
}

@Composable
private fun PriceStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun PriceTrendSection(
    history: ProductPriceHistory,
    formatCurrency: (Double) -> String,
) {
    val zone = ZoneId.systemDefault()
    val labelFormat = DateTimeFormatter.ofPattern("dd.MM.")
    // history.points ist neueste zuerst; das Diagramm läuft von links (alt) nach rechts (neu).
    val chartPoints = history.points.reversed().map {
        ChartPoint(
            label = Instant.ofEpochMilli(it.purchaseDate).atZone(zone).format(labelFormat),
            value = it.unitPrice,
        )
    }
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            stringResource(R.string.product_prices_sparkline_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        TrendLineChart(
            points = chartPoints,
            description = stringResource(
                R.string.product_prices_chart_description,
                history.displayName,
                formatCurrency(history.points.firstOrNull()?.unitPrice ?: 0.0),
                formatCurrency(history.minPrice),
                formatCurrency(history.maxPrice),
            ),
        )
    }
}

@Composable
private fun StoreComparisonSection(
    stores: List<StoreBestPrice>,
    maxPrice: Double,
    formatCurrency: (Double) -> String,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            stringResource(R.string.product_prices_store_comparison),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        stores.forEach { store ->
            val isCheapest = store.isCheapest
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${store.storeName}${if (isCheapest) " ★" else ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCheapest) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCheapest) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        formatCurrency(store.bestPrice),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isCheapest) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
                LinearProgressIndicator(
                    progress = { (store.bestPrice / maxPrice.coerceAtLeast(0.01)).coerceIn(0.0, 1.0).toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = if (isCheapest) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PricePointRow(
    point: ProductPricePoint,
    formatCurrency: (Double) -> String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                formatReceiptDate(point.purchaseDate),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "${point.storeName.ifBlank { stringResource(R.string.receipt_unknown_store) }} · ${formatQuantity(point.quantity)}× " +
                    "à ${formatCurrency(point.unitPrice)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            formatCurrency(point.totalPrice),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
