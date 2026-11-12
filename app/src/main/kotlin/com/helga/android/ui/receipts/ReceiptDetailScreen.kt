package com.helga.android.ui.receipts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.helga.android.R
import com.helga.android.data.local.ScanSource
import com.helga.android.data.local.entity.ReceiptEntity
import com.helga.android.data.local.toScanSource
import com.helga.android.data.receipts.ReceiptDates
import com.helga.android.data.util.ImageUrls
import com.helga.android.data.util.ReceiptItemNormalizer
import com.helga.android.ui.common.asString
import com.helga.android.ui.common.formatEuro
import com.helga.android.ui.components.LoadingIndicator
import java.io.File
import java.time.ZoneId

/** Bons, bei denen der Abgleich mit der Einkaufsliste sinnvoll ist (bestätigt oder Bestand). */
private val RECONCILABLE_STATUS = setOf("confirmed", "scanned", "reconciled")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptDetailScreen(
    onBack: () -> Unit,
    onProductClick: (String) -> Unit = {},
    onReview: (String) -> Unit = {},
    viewModel: ReceiptDetailViewModel = hiltViewModel(),
) {
    val receipt = viewModel.receipt.collectAsStateWithLifecycle().value
    val items = viewModel.items.collectAsStateWithLifecycle().value
    val serverUrl = viewModel.serverUrl.collectAsStateWithLifecycle().value
    val reconcileEnabled = viewModel.reconcileEnabled.collectAsStateWithLifecycle().value
    val reconcileState = viewModel.reconcileState.collectAsStateWithLifecycle().value
    val reparseState = viewModel.reparseState.collectAsStateWithLifecycle().value
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showReparseDialog by rememberSaveable { mutableStateOf(false) }
    var menuExpanded by rememberSaveable { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.receipt_delete_title)) },
            text = { Text(stringResource(R.string.receipt_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.delete(onDone = onBack)
                }) { Text(stringResource(R.string.recipe_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.recipe_delete_confirm_cancel)) }
            },
        )
    }

    if (showReparseDialog) {
        AlertDialog(
            onDismissRequest = { showReparseDialog = false },
            title = { Text(stringResource(R.string.receipt_detail_reparse_title)) },
            text = { Text(stringResource(R.string.receipt_detail_reparse_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showReparseDialog = false
                    viewModel.reparse()
                }) { Text(stringResource(R.string.receipt_detail_reparse)) }
            },
            dismissButton = {
                TextButton(onClick = { showReparseDialog = false }) { Text(stringResource(R.string.recipe_delete_confirm_cancel)) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(receipt?.storeName?.ifBlank { stringResource(R.string.receipt_fallback_title) } ?: stringResource(R.string.receipt_fallback_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.recipe_detail_back))
                    }
                },
                actions = {
                    if (receipt != null) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.receipt_detail_menu))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            if (receipt.status != "provisional") {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.receipt_detail_edit)) },
                                    onClick = { menuExpanded = false; onReview(receipt.id) },
                                )
                            }
                            if (receipt.imagePath.isNotBlank()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.receipt_detail_reparse)) },
                                    onClick = { menuExpanded = false; showReparseDialog = true },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.recipe_delete)) },
                                onClick = { menuExpanded = false; showDeleteDialog = true },
                            )
                        }
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
                Text(stringResource(R.string.receipt_not_found))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                item {
                    StatusBanner(
                        receipt = receipt,
                        reparseState = reparseState,
                        onReview = { onReview(receipt.id) },
                        onDismissError = viewModel::dismissReparseError,
                    )
                }
                receiptPhotoModel(receipt, serverUrl)?.let { model ->
                    item { ReceiptPhoto(model = model) }
                }
                item {
                    SummarySection(receipt = receipt, itemCount = items.size)
                }
                if (reconcileEnabled && receipt.shoppingListId.isNotBlank() && receipt.status in RECONCILABLE_STATUS) {
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
                    ReviewItemRow(
                        item = item,
                        uncertain = false,
                        onEdit = {
                            val key = ReceiptItemNormalizer.normalize(item.name.ifBlank { item.rawText })
                            if (key.isNotBlank()) onProductClick(key)
                        },
                        onRemove = null,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        nameColor = when (item.matchStatus) {
                            "matched" -> MaterialTheme.colorScheme.primary
                            "unexpected" -> MaterialTheme.colorScheme.tertiary
                            else -> androidx.compose.ui.graphics.Color.Unspecified
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBanner(
    receipt: ReceiptEntity,
    reparseState: ReparseState,
    onReview: () -> Unit,
    onDismissError: () -> Unit,
) {
    val provisional = receipt.status == "provisional"
    val needsReview = receipt.status == "review"
    if (!provisional && !needsReview && reparseState is ReparseState.Idle) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (provisional) MaterialTheme.colorScheme.tertiaryContainer
                else MaterialTheme.colorScheme.primaryContainer
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            reparseState is ReparseState.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                LoadingIndicator(modifier = Modifier.height(18.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.receipt_scan_reading))
            }
            reparseState is ReparseState.Error -> {
                Text(reparseState.message.asString(), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onDismissError) { Text(stringResource(R.string.action_retry)) }
            }
            provisional -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CloudOff, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.receipt_detail_banner_provisional), style = MaterialTheme.typography.bodyMedium)
            }
            needsReview -> {
                Text(
                    stringResource(R.string.receipt_detail_banner_review),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Button(onClick = onReview) { Text(stringResource(R.string.receipt_detail_review_now)) }
            }
        }
    }
}

/** Lokale Datei, sonst Server-Bild; ohne beides kein Foto (kein Platzhalter). */
private fun receiptPhotoModel(receipt: ReceiptEntity, serverUrl: String): Any? = when {
    receipt.localImageUri.isNotBlank() && File(receipt.localImageUri).exists() -> File(receipt.localImageUri)
    receipt.imagePath.isNotBlank() && serverUrl.isNotBlank() -> ImageUrls.serverImageUrl(serverUrl, receipt.imagePath)
    else -> null
}

@Composable
private fun ReceiptPhoto(model: Any) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(model)
                .crossfade(true)
                .build(),
            contentDescription = stringResource(R.string.receipt_photo),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun SummarySection(receipt: ReceiptEntity, itemCount: Int) {
    val date = ReceiptDates.displayDate(receipt.purchaseDay, receipt.purchaseDate, ZoneId.systemDefault())
        .ifEmpty { "—" }
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            receipt.storeName.ifBlank { stringResource(R.string.receipt_unknown_store) },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        if (receipt.storeAddress.isNotBlank()) {
            Text(
                receipt.storeAddress,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            if (receipt.purchaseTime.isNotBlank()) "$date · ${receipt.purchaseTime}" else date,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            when (receipt.source.toScanSource()) {
                ScanSource.AI -> stringResource(R.string.receipt_source_ai)
                ScanSource.ON_DEVICE -> stringResource(R.string.receipt_source_ocr)
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(pluralStringResource(R.plurals.receipt_item_count, itemCount, itemCount), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.receipt_sum, formatEuro(receipt.totalAmount)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        if (receipt.discountTotal > 0.0) {
            Text(
                stringResource(R.string.receipt_review_discount_receipt, formatEuro(receipt.discountTotal)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (receipt.depositTotal != 0.0) {
            Text(
                stringResource(R.string.receipt_detail_deposit_sum, formatEuro(receipt.depositTotal)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                LoadingIndicator(
                    modifier = Modifier.height(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(if (alreadyReconciled) stringResource(R.string.receipt_reconcile_again) else stringResource(R.string.receipt_reconcile))
            }
        }
        when (state) {
            is ReconcileState.Error -> Text(
                state.message.asString(),
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
            stringResource(R.string.receipt_reconcile_matches, outcome.matchedCount),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        if (outcome.unexpectedNames.isNotEmpty()) {
            Text(
                stringResource(R.string.receipt_reconcile_unexpected),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
            outcome.unexpectedNames.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
        if (outcome.missingNames.isNotEmpty()) {
            Text(
                stringResource(R.string.receipt_reconcile_missing),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
            outcome.missingNames.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
