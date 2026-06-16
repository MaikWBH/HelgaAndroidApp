package com.helga.android.ui.receipts

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
    val history = viewModel.history.collectAsState().value
    val isLoading = viewModel.isLoading.collectAsState().value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(history?.displayName ?: "Produkt") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { padding ->
        when {
            isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            history == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text("Keine Daten gefunden") }

            else -> ProductPriceContent(
                history = history,
                formatCurrency = viewModel::formatCurrency,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@Composable
private fun ProductPriceContent(
    history: ProductPriceHistory,
    formatCurrency: (Double) -> String,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        item {
            PriceHeaderCard(history = history, formatCurrency = formatCurrency)
        }
        if (history.points.size >= 2) {
            item {
                PriceSparklineSection(points = history.points, formatCurrency = formatCurrency)
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
                "Kaufverlauf",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        items(history.points) { point ->
            PricePointRow(point = point, formatCurrency = formatCurrency)
        }
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
                    "Günstigster Markt",
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
                PriceStat("Min", formatCurrency(history.minPrice))
                PriceStat("Max", formatCurrency(history.maxPrice))
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
private fun PriceSparklineSection(
    points: List<ProductPricePoint>,
    formatCurrency: (Double) -> String,
) {
    // Oldest first for left→right time direction
    val prices = points.reversed().map { it.unitPrice.toFloat() }
    val minPrice = prices.min()
    val maxPrice = prices.max()
    val range = (maxPrice - minPrice).takeIf { it > 0f } ?: 1f

    val lineColor = MaterialTheme.colorScheme.primary

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            "Preisverlauf",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            val w = size.width
            val h = size.height
            val step = if (prices.size > 1) w / (prices.size - 1) else w

            val path = Path()
            prices.forEachIndexed { i, price ->
                val x = i * step
                val y = h - ((price - minPrice) / range * h)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
            prices.forEachIndexed { i, price ->
                val x = i * step
                val y = h - ((price - minPrice) / range * h)
                drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(x, y))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                formatCurrency(minPrice.toDouble()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatCurrency(maxPrice.toDouble()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
            "Marktvergleich",
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
                point.storeName.ifBlank { "Unbekannter Markt" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            formatCurrency(point.unitPrice),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
