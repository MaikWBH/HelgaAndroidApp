package com.helga.android.ui.receipts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.helga.android.R
import com.helga.android.data.local.ScanSource
import com.helga.android.data.local.entity.ReceiptItemEntity
import com.helga.android.data.local.entity.StoreEntity
import com.helga.android.data.model.ProductCategory
import com.helga.android.data.receipts.ReceiptDates
import com.helga.android.data.remote.dto.OffProductDto
import com.helga.android.ui.common.formatEuro
import com.helga.android.ui.components.LoadingIndicator
import java.time.ZoneId
import java.util.Locale

/** Einheiten einer Bon-Position, in der Reihenfolge der Auswahl. */
private val UNIT_KEYS = listOf("stk", "kg", "g", "l", "ml")

@Composable
private fun unitLabel(unit: String): String = stringResource(
    when (unit) {
        "kg" -> R.string.receipt_unit_kg
        "g" -> R.string.receipt_unit_g
        "l" -> R.string.receipt_unit_l
        "ml" -> R.string.receipt_unit_ml
        else -> R.string.receipt_unit_stk
    }
)

private fun parseDecimal(value: String): Double? = value.replace(",", ".").toDoubleOrNull()

private fun euroInput(value: Double): String = String.format(Locale.GERMANY, "%.2f", value)

@Composable
internal fun ReviewBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            stringResource(R.string.receipt_scan_review_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

/**
 * Zeigt transparent, wie der Bon gelesen wurde (KI-Vision vs. On-Device-OCR) und
 * macht den rohen OCR-Text bei Bedarf einsehbar.
 */
@Composable
internal fun ScanSourceInfo(source: ScanSource, rawOcrText: String) {
    var showRaw by rememberSaveable { mutableStateOf(false) }
    val label = when (source) {
        ScanSource.AI -> stringResource(R.string.receipt_scan_source_ai)
        ScanSource.ON_DEVICE -> stringResource(R.string.receipt_scan_source_ocr)
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (rawOcrText.isNotBlank()) {
            TextButton(
                onClick = { showRaw = !showRaw },
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
            ) {
                Text(
                    if (showRaw) stringResource(R.string.receipt_scan_raw_hide)
                    else stringResource(R.string.receipt_scan_raw_show)
                )
            }
            if (showRaw) {
                Text(
                    rawOcrText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp),
                )
            }
        }
    }
}

/** Kopfkarte: Markt, Adresse, Datum + Uhrzeit, Summe, Bon-Rabatt — Stift öffnet den Bearbeiten-Dialog. */
@Composable
internal fun ReviewHeaderCard(
    storeName: String,
    storeAddress: String,
    purchaseDay: String,
    purchaseTime: String,
    totalAmount: Double,
    discountTotal: Double,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val date = ReceiptDates.displayDate(purchaseDay, 0L, ZoneId.systemDefault())
        .ifEmpty { stringResource(R.string.receipt_review_date_unknown) }
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    storeName.ifBlank { stringResource(R.string.receipt_scan_store) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (storeAddress.isNotBlank()) {
                    Text(
                        storeAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (purchaseTime.isNotBlank()) "$date · $purchaseTime" else date,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    formatEuro(totalAmount),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (discountTotal > 0.0) {
                    Text(
                        stringResource(R.string.receipt_review_discount_receipt, formatEuro(discountTotal)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.receipt_review_edit_header))
            }
        }
    }
}

/** Dialog für die Kopfdaten. „Speichern" nur bei gültigem Datum (TT.MM.JJJJ) und gültiger Uhrzeit (HH:MM). */
@Composable
internal fun HeaderEditDialog(
    storeName: String,
    storeAddress: String,
    purchaseDay: String,
    purchaseTime: String,
    totalAmount: Double,
    discountTotal: Double,
    stores: List<StoreEntity>,
    onSelectStore: (StoreEntity) -> Unit,
    onConfirm: (storeName: String, storeAddress: String, purchaseDay: String, purchaseTime: String, total: Double, discountTotal: Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(storeName) }
    var address by rememberSaveable { mutableStateOf(storeAddress) }
    var dateText by rememberSaveable {
        mutableStateOf(ReceiptDates.displayDate(purchaseDay, 0L, ZoneId.systemDefault()))
    }
    var timeText by rememberSaveable { mutableStateOf(purchaseTime) }
    var totalText by rememberSaveable { mutableStateOf(if (totalAmount > 0) euroInput(totalAmount) else "") }
    var discountText by rememberSaveable { mutableStateOf(if (discountTotal > 0) euroInput(discountTotal) else "") }

    val dateError = ReceiptDates.parseDisplayDay(dateText) == null
    val timeError = timeText.isNotEmpty() && !ReceiptDates.isValidTime(timeText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.receipt_review_header_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.receipt_scan_store)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (stores.isNotEmpty()) {
                    Text(
                        stringResource(R.string.receipt_review_choose_store),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(stores, key = { it.id }) { store ->
                            FilterChip(
                                selected = name == store.name,
                                onClick = {
                                    name = store.name
                                    onSelectStore(store)
                                },
                                label = { Text(store.name) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(stringResource(R.string.receipt_review_address)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text(stringResource(R.string.receipt_review_date)) },
                    isError = dateError,
                    supportingText = if (dateError) {
                        { Text(stringResource(R.string.receipt_review_date_invalid)) }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = timeText,
                    onValueChange = { timeText = it },
                    label = { Text(stringResource(R.string.receipt_review_time)) },
                    isError = timeError,
                    supportingText = if (timeError) {
                        { Text(stringResource(R.string.receipt_review_time_invalid)) }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = totalText,
                    onValueChange = { totalText = it },
                    label = { Text(stringResource(R.string.receipt_scan_total)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = discountText,
                    onValueChange = { discountText = it },
                    label = { Text(stringResource(R.string.receipt_review_discount_total)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !dateError && !timeError,
                onClick = {
                    onConfirm(
                        name.trim(),
                        address.trim(),
                        ReceiptDates.parseDisplayDay(dateText).orEmpty(),
                        timeText.trim(),
                        parseDecimal(totalText) ?: 0.0,
                        parseDecimal(discountText) ?: 0.0,
                    )
                },
            ) { Text(stringResource(R.string.recipe_form_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.recipe_delete_confirm_cancel)) }
        },
    )
}

/** Eine Position im Review: Name, Bon-Text (wenn abweichend), Menge + Einheit, Rabatt, Pfand- und Kategorie-Chip. */
@Composable
internal fun ReviewItemRow(
    item: ReceiptItemEntity,
    uncertain: Boolean,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayName = item.name.ifEmpty { item.rawText }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(
                if (uncertain) Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                else Modifier
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (uncertain) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = stringResource(R.string.receipt_scan_check),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClick = onEdit),
        ) {
            Text(displayName)
            if (item.rawText.isNotBlank() && !item.rawText.equals(displayName, ignoreCase = true)) {
                Text(
                    item.rawText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "${formatQuantity(item.quantity)} ${unitLabel(item.unit)} × ${formatEuro(item.unitPrice)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.discount > 0.0) {
                Text(
                    stringResource(R.string.receipt_review_discount_item, formatEuro(item.discount)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.isDeposit == 1) {
                    AssistChip(onClick = onEdit, label = { Text(stringResource(R.string.receipt_review_deposit)) })
                } else {
                    AssistChip(
                        onClick = onEdit,
                        label = { Text(stringResource(ProductCategory.fromKey(item.category).labelRes())) },
                    )
                }
            }
        }
        Text(
            formatEuro(item.totalPrice),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(role = Role.Button, onClick = onEdit),
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.weekplan_remove_recipe))
        }
    }
}

/**
 * Dialog zum manuellen Korrigieren einer erkannten Position. Der Gesamtpreis folgt Menge × Einzelpreis,
 * solange er nicht von Hand überschrieben wurde. „Pfand" setzt die Kategorie auf Pfand und sperrt die Auswahl.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditItemDialog(
    item: ReceiptItemEntity,
    onDismiss: () -> Unit,
    onConfirm: (ReceiptItemEntity) -> Unit,
    onAssignProduct: (edited: ReceiptItemEntity) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(item.name.ifEmpty { item.rawText }) }
    var quantityText by rememberSaveable { mutableStateOf(formatQuantity(item.quantity)) }
    var unitPriceText by rememberSaveable { mutableStateOf(euroInput(item.unitPrice)) }
    var totalText by rememberSaveable { mutableStateOf(euroInput(item.totalPrice)) }
    var discountText by rememberSaveable { mutableStateOf(if (item.discount > 0) euroInput(item.discount) else "") }
    var unit by rememberSaveable { mutableStateOf(item.unit.ifEmpty { "stk" }) }
    var deposit by rememberSaveable { mutableStateOf(item.isDeposit == 1) }
    var categoryKey by rememberSaveable { mutableStateOf(if (item.isDeposit == 1) "" else item.category) }
    var unitExpanded by rememberSaveable { mutableStateOf(false) }
    var categoryExpanded by rememberSaveable { mutableStateOf(false) }
    // Solange true, folgt der Gesamtpreis automatisch Menge × Einzelpreis.
    var autoTotal by rememberSaveable { mutableStateOf(true) }

    fun recomputeTotal() {
        if (!autoTotal) return
        val q = parseDecimal(quantityText)
        val u = parseDecimal(unitPriceText)
        if (q != null && u != null) totalText = euroInput(q * u)
    }

    fun edited(): ReceiptItemEntity {
        val q = parseDecimal(quantityText) ?: item.quantity
        val u = parseDecimal(unitPriceText) ?: item.unitPrice
        return item.copy(
            name = name.trim(),
            quantity = q,
            unit = unit,
            unitPrice = u,
            totalPrice = parseDecimal(totalText) ?: (q * u),
            discount = (parseDecimal(discountText) ?: 0.0).coerceAtLeast(0.0),
            isDeposit = if (deposit) 1 else 0,
            category = if (deposit) ProductCategory.PFAND.key else categoryKey,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.receipt_scan_edit_item)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.receipt_scan_item_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { onAssignProduct(edited()) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.receipt_review_assign_product))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it; recomputeTotal() },
                        label = { Text(stringResource(R.string.recipe_form_quantity)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    ExposedDropdownMenuBox(
                        expanded = unitExpanded,
                        onExpandedChange = { unitExpanded = it },
                        modifier = Modifier.weight(1f),
                    ) {
                        OutlinedTextField(
                            value = unitLabel(unit),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.receipt_review_unit)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                        )
                        ExposedDropdownMenu(expanded = unitExpanded, onDismissRequest = { unitExpanded = false }) {
                            UNIT_KEYS.forEach { key ->
                                DropdownMenuItem(
                                    text = { Text(unitLabel(key)) },
                                    onClick = { unit = key; unitExpanded = false },
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = unitPriceText,
                    onValueChange = { unitPriceText = it; recomputeTotal() },
                    label = { Text(stringResource(R.string.receipt_scan_unit_price)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = totalText,
                    onValueChange = { totalText = it; autoTotal = false },
                    label = { Text(stringResource(R.string.receipt_scan_total_price)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = discountText,
                    onValueChange = { discountText = it },
                    label = { Text(stringResource(R.string.receipt_review_discount)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.receipt_review_deposit), modifier = Modifier.weight(1f))
                    Switch(checked = deposit, onCheckedChange = { deposit = it })
                }
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded && !deposit,
                    onExpandedChange = { if (!deposit) categoryExpanded = it },
                ) {
                    OutlinedTextField(
                        value = stringResource(
                            (if (deposit) ProductCategory.PFAND else ProductCategory.fromKey(categoryKey)).labelRes()
                        ),
                        onValueChange = {},
                        readOnly = true,
                        enabled = !deposit,
                        label = { Text(stringResource(R.string.receipt_review_category)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded && !deposit) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded && !deposit,
                        onDismissRequest = { categoryExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.category_none)) },
                            onClick = { categoryKey = ""; categoryExpanded = false },
                        )
                        ProductCategory.entries.filter { it != ProductCategory.PFAND }.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(stringResource(category.labelRes())) },
                                onClick = { categoryKey = category.key; categoryExpanded = false },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(edited()) }) {
                Text(stringResource(R.string.weekplan_export_preview_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.recipe_delete_confirm_cancel)) }
        },
    )
}

/** Blatt mit Open-Food-Facts-Treffern; Ladeindikator, Trefferliste (Name, Marke) oder Leertext. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProductSuggestionSheet(
    loading: Boolean,
    suggestions: List<OffProductDto>,
    onPick: (OffProductDto) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.receipt_review_assign_product),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            when {
                loading -> Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) { LoadingIndicator() }
                suggestions.isEmpty() -> Text(
                    stringResource(R.string.receipt_review_no_matches),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> suggestions.forEachIndexed { index, product ->
                    if (index > 0) HorizontalDivider()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { onPick(product) }
                            .padding(vertical = 10.dp),
                    ) {
                        Text(product.name)
                        if (product.brand.isNotBlank()) {
                            Text(
                                product.brand,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Hinweis nach dem Scan, wenn nur vorläufige Werte vorliegen. */
@Composable
internal fun ProvisionalCard(
    receiptId: String,
    queuedOnServer: Boolean,
    onOpenReceipt: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (queuedOnServer) Icons.Filled.HourglassTop else Icons.Filled.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(
                        if (queuedOnServer) R.string.receipt_scan_provisional_queued
                        else R.string.receipt_scan_provisional_offline
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(onClick = { onOpenReceipt(receiptId) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.receipt_scan_open_receipt))
            }
        }
    }
}
