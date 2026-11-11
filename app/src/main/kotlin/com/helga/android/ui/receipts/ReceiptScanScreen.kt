package com.helga.android.ui.receipts

import com.helga.android.ui.common.asString
import com.helga.android.R
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.helga.android.ui.common.formatEuro
import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.background
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Button
import com.helga.android.ui.components.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import android.content.pm.PackageManager
import com.helga.android.data.local.ScanSource
import com.helga.android.data.local.entity.ReceiptItemEntity
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptScanScreen(
    shoppingListId: String? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ReceiptScanViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(shoppingListId) {
        viewModel.setShoppingListId(shoppingListId)
    }

    LaunchedEffect(uiState) {
        if (uiState is ReceiptScanUiState.Saved) {
            onSaved()
        }
    }

    // Temporäre Datei + FileProvider-URI für die Kamera-Aufnahme
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) cameraImageUri?.let { viewModel.scanImage(it) }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.scanImage(it) }
    }

    // Erstellt die temporäre Datei und startet den Kamera-Intent.
    // Wird erst nach Permission-Grant aufgerufen.
    fun doLaunchCamera() {
        val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        cameraImageUri = uri
        cameraLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) doLaunchCamera()
    }

    fun launchCamera() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) doLaunchCamera() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.receipt_scan_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.recipe_detail_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            when (val state = uiState) {
                is ReceiptScanUiState.Idle -> {
                    CaptureButtons(
                        onCamera = { launchCamera() },
                        onGallery = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    )
                }

                is ReceiptScanUiState.Scanning -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        LoadingIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.receipt_scan_reading))
                    }
                }

                is ReceiptScanUiState.Preview -> {
                    PreviewContent(
                        state = state,
                        onStoreNameChange = viewModel::updateStoreName,
                        onTotalChange = viewModel::updateTotal,
                        onRemoveItem = viewModel::removeItem,
                        onUpdateItem = viewModel::updateItem,
                        onSave = viewModel::save,
                        onRetry = viewModel::reset,
                    )
                }

                is ReceiptScanUiState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(state.message.asString())
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = viewModel::reset) { Text(stringResource(R.string.action_retry)) }
                    }
                }

                is ReceiptScanUiState.Saved -> Unit
            }
        }
    }
}

@Composable
private fun CaptureButtons(
    onCamera: () -> Unit,
    onGallery: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.receipt_scan_intro),
            modifier = Modifier.padding(bottom = 24.dp),
        )
        Button(
            onClick = onCamera,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.CameraAlt, contentDescription = null)
            Spacer(Modifier.height(0.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.receipt_scan_take_photo))
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onGallery,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.receipt_scan_gallery))
        }
    }
}

@Composable
private fun ReviewBanner() {
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
 * macht den rohen OCR-Text bei Bedarf einsehbar – hilft beim Nachvollziehen,
 * warum Positionen evtl. nicht erkannt wurden.
 */
@Composable
private fun ScanSourceInfo(source: ScanSource, rawOcrText: String) {
    var showRaw by remember { mutableStateOf(false) }
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
                Text(if (showRaw) stringResource(R.string.receipt_scan_raw_hide) else stringResource(R.string.receipt_scan_raw_show))
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

@Composable
private fun PreviewContent(
    state: ReceiptScanUiState.Preview,
    onStoreNameChange: (String) -> Unit,
    onTotalChange: (Double) -> Unit,
    onRemoveItem: (ReceiptItemEntity) -> Unit,
    onUpdateItem: (ReceiptItemEntity) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
) {
    var totalText by remember(state.totalAmount) {
        mutableStateOf(if (state.totalAmount > 0) String.format("%.2f", state.totalAmount) else "")
    }
    // Aktuell zur Korrektur geöffnete Position (null = kein Dialog offen).
    var editingItem by remember { mutableStateOf<ReceiptItemEntity?>(null) }

    editingItem?.let { item ->
        EditItemDialog(
            item = item,
            onDismiss = { editingItem = null },
            onConfirm = {
                onUpdateItem(it)
                editingItem = null
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                ScanSourceInfo(source = state.source, rawOcrText = state.rawOcrText)
                Spacer(Modifier.height(12.dp))
            }
            if (state.needsReview) {
                item {
                    ReviewBanner()
                    Spacer(Modifier.height(12.dp))
                }
            }
            item {
                OutlinedTextField(
                    value = state.storeName,
                    onValueChange = onStoreNameChange,
                    label = { Text(stringResource(R.string.receipt_scan_store)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = totalText,
                    onValueChange = {
                        totalText = it
                        it.replace(",", ".").toDoubleOrNull()?.let(onTotalChange)
                    },
                    label = { Text(stringResource(R.string.receipt_scan_total)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.receipt_scan_items, state.items.size),
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.receipt_scan_edit_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }

            items(state.items, key = { it.id }) { item ->
                val uncertain = item.id in state.lowConfidenceItemIds
                Row(
                    modifier = Modifier
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
                            .clickable { editingItem = item },
                    ) {
                        Text(item.name.ifEmpty { item.rawText })
                        Text(
                            "${formatQuantity(item.quantity)} × ${formatEuro(item.unitPrice)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        formatEuro(item.totalPrice),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { editingItem = item },
                    )
                    IconButton(onClick = { onRemoveItem(item) }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.weekplan_remove_recipe))
                    }
                }
            }

            if (state.items.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.receipt_scan_no_items),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.receipt_scan_rescan)) }
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.recipe_form_save)) }
        }
    }
}

/**
 * Dialog zum manuellen Korrigieren einer erkannten Position. Der Gesamtpreis wird
 * automatisch aus Menge × Einzelpreis berechnet, solange er nicht von Hand
 * überschrieben wurde (z. B. bei Rabatten oder Pfand auf dem Bon).
 */
@Composable
private fun EditItemDialog(
    item: ReceiptItemEntity,
    onDismiss: () -> Unit,
    onConfirm: (ReceiptItemEntity) -> Unit,
) {
    var name by remember { mutableStateOf(item.name.ifEmpty { item.rawText }) }
    var quantityText by remember { mutableStateOf(formatQuantity(item.quantity)) }
    var unitPriceText by remember { mutableStateOf(String.format("%.2f", item.unitPrice)) }
    var totalText by remember { mutableStateOf(String.format("%.2f", item.totalPrice)) }
    // Solange true, folgt der Gesamtpreis automatisch Menge × Einzelpreis.
    var autoTotal by remember { mutableStateOf(true) }

    fun parse(value: String): Double? = value.replace(",", ".").toDoubleOrNull()

    fun recomputeTotal() {
        if (!autoTotal) return
        val q = parse(quantityText)
        val u = parse(unitPriceText)
        if (q != null && u != null) totalText = String.format("%.2f", q * u)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.receipt_scan_edit_item)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.receipt_scan_item_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it; recomputeTotal() },
                    label = { Text(stringResource(R.string.recipe_form_quantity)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = unitPriceText,
                    onValueChange = { unitPriceText = it; recomputeTotal() },
                    label = { Text(stringResource(R.string.receipt_scan_unit_price)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = totalText,
                    onValueChange = { totalText = it; autoTotal = false },
                    label = { Text(stringResource(R.string.receipt_scan_total_price)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val q = parse(quantityText) ?: item.quantity
                val u = parse(unitPriceText) ?: item.unitPrice
                val t = parse(totalText) ?: (q * u)
                onConfirm(
                    item.copy(
                        name = name.trim(),
                        quantity = q,
                        unitPrice = u,
                        totalPrice = t,
                    )
                )
            }) { Text(stringResource(R.string.weekplan_export_preview_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.recipe_delete_confirm_cancel)) }
        },
    )
}
