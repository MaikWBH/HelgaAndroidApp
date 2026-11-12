package com.helga.android.ui.receipts

import com.helga.android.R
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.helga.android.ui.common.formatEuro
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.helga.android.data.local.entity.ReceiptEntity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import com.helga.android.data.receipts.ReceiptDates
import com.helga.android.ui.common.asString
import com.helga.android.ui.components.LoadingIndicator
import com.helga.android.ui.components.PreviewSurface
import com.helga.android.ui.components.ThemePreviews
import java.time.YearMonth
import java.util.Locale
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

private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)

@Composable
private fun monthLabel(key: String): String =
    if (key.length == 7) {
        runCatching { YearMonth.parse(key).format(monthFormatter) }.getOrDefault(key)
    } else {
        stringResource(R.string.receipt_review_date_unknown)
    }

@Composable
fun ReceiptListScreen(
    onReceiptClick: (String) -> Unit,
    onScanClick: () -> Unit,
    onCostOverviewClick: () -> Unit,
    onProductsClick: () -> Unit,
    onStatsClick: () -> Unit = {},
    viewModel: ReceiptListViewModel = hiltViewModel(),
) {
    val receipts by viewModel.receipts.collectAsStateWithLifecycle()
    val loadingOlder by viewModel.loadingOlder.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.olderMessage.collect { snackbarHostState.showSnackbar(it.asString(context)) }
    }

    ReceiptListContent(
        receipts = receipts,
        loadingOlder = loadingOlder,
        snackbarHostState = snackbarHostState,
        onReceiptClick = onReceiptClick,
        onScanClick = onScanClick,
        onCostOverviewClick = onCostOverviewClick,
        onProductsClick = onProductsClick,
        onStatsClick = onStatsClick,
        onLoadOlder = viewModel::loadOlder,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ReceiptListContent(
    receipts: List<ReceiptEntity>,
    loadingOlder: Boolean,
    snackbarHostState: SnackbarHostState,
    onReceiptClick: (String) -> Unit,
    onScanClick: () -> Unit,
    onCostOverviewClick: () -> Unit,
    onProductsClick: () -> Unit,
    onStatsClick: () -> Unit,
    onLoadOlder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.receipt_list_title)) },
                actions = {
                    IconButton(onClick = onProductsClick) {
                        Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = stringResource(R.string.product_prices_title))
                    }
                    IconButton(onClick = onCostOverviewClick) {
                        Icon(Icons.Filled.BarChart, contentDescription = stringResource(R.string.cost_overview_title))
                    }
                    IconButton(onClick = onStatsClick) {
                        Icon(Icons.Filled.Insights, contentDescription = stringResource(R.string.receipt_stats))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScanClick,
                icon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
                text = { Text(stringResource(R.string.receipt_scan)) },
            )
        },
    ) { padding ->
        if (receipts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.ReceiptLong,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.receipt_list_empty_hint),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onScanClick) { Text(stringResource(R.string.receipt_list_scan_button)) }
            }
        } else {
            val groups = remember(receipts) { groupReceiptsByMonth(receipts) }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                groups.forEach { (monthKey, monthReceipts) ->
                    stickyHeader(key = "header-$monthKey") {
                        Text(
                            monthLabel(monthKey),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(vertical = 4.dp),
                        )
                    }
                    items(monthReceipts, key = { it.id }) { receipt ->
                        ReceiptRow(receipt = receipt, onClick = { onReceiptClick(receipt.id) })
                    }
                }
                item(key = "footer") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 72.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        TextButton(onClick = onLoadOlder, enabled = !loadingOlder) {
                            if (loadingOlder) {
                                LoadingIndicator()
                                Spacer(Modifier.size(8.dp))
                            }
                            Text(stringResource(R.string.receipt_list_load_older))
                        }
                        Text(
                            stringResource(R.string.receipt_list_older_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
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
    val status = receiptStatusKind(receipt)
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
                    receipt.storeName.ifBlank { stringResource(R.string.receipt_unknown_store) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    ReceiptDates.displayDate(receipt.purchaseDay, receipt.purchaseDate, ZoneId.systemDefault())
                        .ifEmpty { "—" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (status != null) {
                    Spacer(Modifier.height(4.dp))
                    ReceiptStatusChip(status)
                }
            }
            Text(
                formatEuro(receipt.totalAmount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────

private fun sampleReceipts() = listOf(
    ReceiptEntity(id = "1", storeName = "REWE", purchaseDay = "2026-11-10", purchaseDate = 1_794_268_800_000L,
        totalAmount = 23.45, status = "review"),
    ReceiptEntity(id = "2", storeName = "Lidl", purchaseDay = "2026-11-02", purchaseDate = 1_793_577_600_000L,
        totalAmount = 8.9, status = "provisional"),
    ReceiptEntity(id = "3", storeName = "Aldi", purchaseDay = "2026-10-28", purchaseDate = 1_793_145_600_000L,
        totalAmount = 41.2, status = "confirmed"),
)

@ThemePreviews
@Composable
private fun ReceiptListPreview() = PreviewSurface {
    ReceiptListContent(
        receipts = sampleReceipts(), loadingOlder = false, snackbarHostState = remember { SnackbarHostState() },
        onReceiptClick = {}, onScanClick = {}, onCostOverviewClick = {}, onProductsClick = {},
        onStatsClick = {}, onLoadOlder = {},
    )
}

@ThemePreviews
@Composable
private fun ReceiptListEmptyPreview() = PreviewSurface {
    ReceiptListContent(
        receipts = emptyList(), loadingOlder = false, snackbarHostState = remember { SnackbarHostState() },
        onReceiptClick = {}, onScanClick = {}, onCostOverviewClick = {}, onProductsClick = {},
        onStatsClick = {}, onLoadOlder = {},
    )
}
