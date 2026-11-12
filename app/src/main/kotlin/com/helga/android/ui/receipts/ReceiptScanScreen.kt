package com.helga.android.ui.receipts

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.helga.android.R
import com.helga.android.data.local.ScanSource
import com.helga.android.data.local.entity.ReceiptItemEntity
import com.helga.android.data.local.entity.StoreEntity
import com.helga.android.data.receipts.ReceiptReviewCheck
import com.helga.android.data.remote.dto.OffProductDto
import com.helga.android.ui.common.asString
import com.helga.android.ui.common.formatEuro
import com.helga.android.ui.components.LoadingIndicator
import com.helga.android.ui.components.PreviewSurface
import com.helga.android.ui.components.ThemePreviews
import java.io.File

@Composable
fun ReceiptScanScreen(
    shoppingListId: String? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onOpenReceipt: (String) -> Unit,
    viewModel: ReceiptScanViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val stores by viewModel.stores.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val suggestionsLoading by viewModel.suggestionsLoading.collectAsStateWithLifecycle()

    LaunchedEffect(shoppingListId) {
        viewModel.setShoppingListId(shoppingListId)
    }

    LaunchedEffect(uiState) {
        if (uiState is ReceiptScanUiState.Saved) {
            onSaved()
        }
    }

    // Temporäre Datei + FileProvider-URI für die Kamera-Aufnahme
    var cameraImageUri by rememberSaveable { mutableStateOf<Uri?>(null) }

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

    ReceiptScanContent(
        state = uiState,
        stores = stores,
        suggestions = suggestions,
        suggestionsLoading = suggestionsLoading,
        onBack = onBack,
        onCamera = ::launchCamera,
        onGallery = {
            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onHeaderConfirm = viewModel::updateHeader,
        onSelectStore = viewModel::selectStore,
        onRemoveItem = viewModel::removeItem,
        onUpdateItem = viewModel::updateItem,
        onSuggest = viewModel::suggestProducts,
        onClearSuggestions = viewModel::clearSuggestions,
        onApplyProduct = viewModel::applyProduct,
        onSave = viewModel::save,
        onReset = viewModel::reset,
        onOpenReceipt = onOpenReceipt,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReceiptScanContent(
    state: ReceiptScanUiState,
    stores: List<StoreEntity>,
    suggestions: List<OffProductDto>,
    suggestionsLoading: Boolean,
    onBack: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onHeaderConfirm: (String, String, String, String, Double, Double) -> Unit,
    onSelectStore: (StoreEntity) -> Unit,
    onRemoveItem: (ReceiptItemEntity) -> Unit,
    onUpdateItem: (ReceiptItemEntity) -> Unit,
    onSuggest: (ReceiptItemEntity) -> Unit,
    onClearSuggestions: () -> Unit,
    onApplyProduct: (String, OffProductDto) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onOpenReceipt: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
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
            when (state) {
                is ReceiptScanUiState.Idle -> CaptureButtons(onCamera = onCamera, onGallery = onGallery)

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

                is ReceiptScanUiState.Preview -> PreviewContent(
                    state = state,
                    stores = stores,
                    suggestions = suggestions,
                    suggestionsLoading = suggestionsLoading,
                    onHeaderConfirm = onHeaderConfirm,
                    onSelectStore = onSelectStore,
                    onRemoveItem = onRemoveItem,
                    onUpdateItem = onUpdateItem,
                    onSuggest = onSuggest,
                    onClearSuggestions = onClearSuggestions,
                    onApplyProduct = onApplyProduct,
                    onSave = onSave,
                    onRetry = onReset,
                )

                is ReceiptScanUiState.Provisional -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        ProvisionalCard(
                            receiptId = state.receiptId,
                            queuedOnServer = state.queuedOnServer,
                            onOpenReceipt = onOpenReceipt,
                        )
                    }
                }

                is ReceiptScanUiState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(state.message.asString())
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onReset) { Text(stringResource(R.string.action_retry)) }
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
private fun PreviewContent(
    state: ReceiptScanUiState.Preview,
    stores: List<StoreEntity>,
    suggestions: List<OffProductDto>,
    suggestionsLoading: Boolean,
    onHeaderConfirm: (String, String, String, String, Double, Double) -> Unit,
    onSelectStore: (StoreEntity) -> Unit,
    onRemoveItem: (ReceiptItemEntity) -> Unit,
    onUpdateItem: (ReceiptItemEntity) -> Unit,
    onSuggest: (ReceiptItemEntity) -> Unit,
    onClearSuggestions: () -> Unit,
    onApplyProduct: (String, OffProductDto) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
) {
    var showHeaderDialog by rememberSaveable { mutableStateOf(false) }
    // Aktuell zur Korrektur geöffnete Position (nur die id überlebt Drehen/Prozess-Tod).
    var editingItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var showSuggestions by rememberSaveable { mutableStateOf(false) }
    val editingItem = state.items.firstOrNull { it.id == editingItemId }

    if (showHeaderDialog) {
        HeaderEditDialog(
            storeName = state.storeName,
            storeAddress = state.storeAddress,
            purchaseDay = state.purchaseDay,
            purchaseTime = state.purchaseTime,
            totalAmount = state.totalAmount,
            discountTotal = state.discountTotal,
            stores = stores,
            onSelectStore = onSelectStore,
            onConfirm = { name, address, day, time, total, discount ->
                onHeaderConfirm(name, address, day, time, total, discount)
                showHeaderDialog = false
            },
            onDismiss = { showHeaderDialog = false },
        )
    }

    editingItem?.let { item ->
        EditItemDialog(
            item = item,
            onDismiss = { editingItemId = null },
            onConfirm = {
                onUpdateItem(it)
                editingItemId = null
            },
            onAssignProduct = { edited ->
                onUpdateItem(edited)
                onSuggest(edited)
                showSuggestions = true
            },
        )
    }

    if (showSuggestions && editingItem != null) {
        ProductSuggestionSheet(
            loading = suggestionsLoading,
            suggestions = suggestions,
            onPick = { product ->
                onApplyProduct(editingItem.id, product)
                showSuggestions = false
                editingItemId = null
            },
            onDismiss = {
                showSuggestions = false
                onClearSuggestions()
            },
        )
    }

    val itemsSum = ReceiptReviewCheck.expectedTotal(state.items, state.discountTotal)
    val mismatch = ReceiptReviewCheck.sumMismatch(state.totalAmount, state.items, state.discountTotal)

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
                ReviewHeaderCard(
                    storeName = state.storeName,
                    storeAddress = state.storeAddress,
                    purchaseDay = state.purchaseDay,
                    purchaseTime = state.purchaseTime,
                    totalAmount = state.totalAmount,
                    discountTotal = state.discountTotal,
                    onEdit = { showHeaderDialog = true },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(
                        R.string.receipt_review_sum_line, formatEuro(itemsSum), formatEuro(state.totalAmount)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (mismatch) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.receipt_scan_items, state.items.size),
                    style = MaterialTheme.typography.titleMedium,
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
                ReviewItemRow(
                    item = item,
                    uncertain = item.id in state.lowConfidenceItemIds,
                    onEdit = { editingItemId = item.id },
                    onRemove = { onRemoveItem(item) },
                )
            }

            if (state.items.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.receipt_scan_no_items),
                        style = MaterialTheme.typography.bodySmall,
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

// ── Previews ─────────────────────────────────────────────────────────────────

private fun sampleReview() = ReceiptScanUiState.Preview(
    receiptId = "r1",
    storeName = "REWE",
    storeId = "",
    storeAddress = "Hauptstr. 12, 12345 Musterstadt",
    purchaseDay = "2026-11-10",
    purchaseTime = "17:42",
    totalAmount = 4.2,
    discountTotal = 0.3,
    purchaseDate = 1_794_268_800_000L,
    rawOcrText = "",
    items = listOf(
        ReceiptItemEntity(
            id = "i1", receiptId = "r1", rawText = "G&G H-MILCH 3,5%", name = "H-Milch 3,5 %",
            quantity = 2.0, unit = "stk", unitPrice = 0.95, totalPrice = 1.9, category = "milch_eier",
        ),
        ReceiptItemEntity(
            id = "i2", receiptId = "r1", rawText = "BANANE", name = "Bananen", quantity = 0.512, unit = "kg",
            unitPrice = 1.99, totalPrice = 1.02, discount = 0.3, category = "obst_gemuese", confidence = 0.5,
        ),
        ReceiptItemEntity(
            id = "i3", receiptId = "r1", rawText = "PFAND", name = "Pfand", unitPrice = 0.25, totalPrice = 0.25,
            isDeposit = 1, category = "pfand",
        ),
    ),
    lowConfidenceItemIds = setOf("i2"),
    needsReview = true,
    source = ScanSource.AI,
)

@ThemePreviews
@Composable
private fun ReceiptReviewPreview() = PreviewSurface {
    ReceiptScanContent(
        state = sampleReview(), stores = emptyList(), suggestions = emptyList(), suggestionsLoading = false,
        onBack = {}, onCamera = {}, onGallery = {}, onHeaderConfirm = { _, _, _, _, _, _ -> },
        onSelectStore = {}, onRemoveItem = {}, onUpdateItem = {}, onSuggest = {}, onClearSuggestions = {},
        onApplyProduct = { _, _ -> }, onSave = {}, onReset = {}, onOpenReceipt = {},
    )
}

@ThemePreviews
@Composable
private fun ReceiptProvisionalPreview() = PreviewSurface {
    ReceiptScanContent(
        state = ReceiptScanUiState.Provisional(receiptId = "r1", queuedOnServer = false),
        stores = emptyList(), suggestions = emptyList(), suggestionsLoading = false,
        onBack = {}, onCamera = {}, onGallery = {}, onHeaderConfirm = { _, _, _, _, _, _ -> },
        onSelectStore = {}, onRemoveItem = {}, onUpdateItem = {}, onSuggest = {}, onClearSuggestions = {},
        onApplyProduct = { _, _ -> }, onSave = {}, onReset = {}, onOpenReceipt = {},
    )
}
