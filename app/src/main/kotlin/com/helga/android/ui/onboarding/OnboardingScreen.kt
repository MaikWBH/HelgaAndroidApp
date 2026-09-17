package com.helga.android.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.common.Barcode
import com.helga.android.R
import com.helga.android.ui.components.BarcodeScanner

@Composable
fun OnboardingScreen(
    onContinue: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showScanner by remember { mutableStateOf(false) }
    var cameraDenied by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraDenied = !granted
        showScanner = granted
    }

    fun startScan() {
        cameraDenied = false
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) showScanner = true else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (showScanner) {
        BarcodeScanner(
            onBarcodeDetected = { raw ->
                showScanner = false
                viewModel.onScanned(raw, onSuccess = onContinue)
            },
            onDismiss = { showScanner = false },
            modifier = Modifier.fillMaxSize(),
            formats = Barcode.FORMAT_QR_CODE,
        )
        return
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.onboarding_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { startScan() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.onboarding_scan_qr))
            }
            Text(
                text = stringResource(R.string.onboarding_scan_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (cameraDenied) {
                Text(
                    text = stringResource(R.string.onboarding_camera_denied),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Text(
                text = stringResource(R.string.onboarding_or_manual),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            OutlinedTextField(
                value = state.serverUrl,
                onValueChange = viewModel::setServerUrl,
                label = { Text(stringResource(R.string.onboarding_server_url)) },
                placeholder = { Text(stringResource(R.string.onboarding_server_url_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.apiKey,
                onValueChange = viewModel::setApiKey,
                label = { Text(stringResource(R.string.onboarding_api_key)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            ValidationFeedback(state.validation)

            Button(
                onClick = { viewModel.testConnection(onSuccess = onContinue) },
                enabled = state.validation != Validation.Testing &&
                    state.serverUrl.isNotBlank() && state.apiKey.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.validation == Validation.Testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.onboarding_test_connection))
                }
            }
        }
    }
}

@Composable
private fun ValidationFeedback(validation: Validation) {
    val (text, color) = when (validation) {
        Validation.Idle -> return
        Validation.Testing -> stringResource(R.string.onboarding_testing) to
            MaterialTheme.colorScheme.onSurfaceVariant
        Validation.Success -> stringResource(R.string.onboarding_success) to
            MaterialTheme.colorScheme.primary
        Validation.InvalidUrl -> stringResource(R.string.onboarding_error_invalid_url) to
            MaterialTheme.colorScheme.error
        Validation.Unreachable -> stringResource(R.string.onboarding_error_unreachable) to
            MaterialTheme.colorScheme.error
        Validation.Unauthorized -> stringResource(R.string.onboarding_error_unauthorized) to
            MaterialTheme.colorScheme.error
        Validation.InvalidQr -> stringResource(R.string.onboarding_error_invalid_qr) to
            MaterialTheme.colorScheme.error
    }
    Text(text = text, color = color, style = MaterialTheme.typography.bodyMedium)
}
