package com.helga.android.ui.receipts

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
    val uiState by viewModel.uiState.collectAsState()

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
                title = { Text("Kassenzettel scannen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
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
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Bon wird ausgelesen…")
                    }
                }

                is ReceiptScanUiState.Preview -> {
                    PreviewContent(
                        state = state,
                        onStoreNameChange = viewModel::updateStoreName,
                        onTotalChange = viewModel::updateTotal,
                        onRemoveItem = viewModel::removeItem,
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
                        Text(state.message)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = viewModel::reset) { Text("Erneut versuchen") }
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
            "Fotografiere deinen Kassenzettel oder wähle ein Bild aus der Galerie.",
            modifier = Modifier.padding(bottom = 24.dp),
        )
        Button(
            onClick = onCamera,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.CameraAlt, contentDescription = null)
            Spacer(Modifier.height(0.dp))
            Text("  Foto aufnehmen")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onGallery,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
            Text("  Aus Galerie wählen")
        }
    }
}

@Composable
private fun PreviewContent(
    state: ReceiptScanUiState.Preview,
    onStoreNameChange: (String) -> Unit,
    onTotalChange: (Double) -> Unit,
    onRemoveItem: (com.helga.android.data.local.entity.ReceiptItemEntity) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
) {
    var totalText by remember(state.totalAmount) {
        mutableStateOf(if (state.totalAmount > 0) String.format("%.2f", state.totalAmount) else "")
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                OutlinedTextField(
                    value = state.storeName,
                    onValueChange = onStoreNameChange,
                    label = { Text("Markt") },
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
                    label = { Text("Gesamtbetrag (€)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Artikel (${state.items.size})",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
            }

            items(state.items, key = { it.id }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        item.name.ifEmpty { item.rawText },
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        String.format("€%.2f", item.totalPrice),
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(onClick = { onRemoveItem(item) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Entfernen")
                    }
                }
            }

            if (state.items.isEmpty()) {
                item {
                    Text(
                        "Keine Artikel erkannt. Du kannst den Bon trotzdem speichern.",
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
            ) { Text("Neu scannen") }
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f),
            ) { Text("Speichern") }
        }
    }
}
