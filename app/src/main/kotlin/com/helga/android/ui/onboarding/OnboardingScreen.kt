package com.helga.android.ui.onboarding

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
import com.helga.android.ui.components.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.helga.android.R
import com.helga.android.ui.components.PreviewSurface
import com.helga.android.ui.components.ThemePreviews

@Composable
fun OnboardingScreen(
    onContinue: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    OnboardingContent(
        state = state,
        onServerUrlChange = viewModel::setServerUrl,
        onApiKeyChange = viewModel::setApiKey,
        onTestConnection = { viewModel.testConnection(onSuccess = onContinue) },
    )
}

@Composable
internal fun OnboardingContent(
    state: OnboardingState,
    onServerUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier) { padding ->
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

            OutlinedTextField(
                value = state.serverUrl,
                onValueChange = onServerUrlChange,
                label = { Text(stringResource(R.string.onboarding_server_url)) },
                placeholder = { Text(stringResource(R.string.onboarding_server_url_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.apiKey,
                onValueChange = onApiKeyChange,
                label = { Text(stringResource(R.string.onboarding_api_key)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            ValidationFeedback(state.validation)

            Button(
                onClick = onTestConnection,
                enabled = state.validation != Validation.Testing &&
                    state.serverUrl.isNotBlank() && state.apiKey.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.validation == Validation.Testing) {
                    LoadingIndicator(
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
    }
    Text(text = text, color = color, style = MaterialTheme.typography.bodyMedium)
}

@ThemePreviews
@Composable
private fun OnboardingContentPreview() {
    PreviewSurface {
        OnboardingContent(
            state = OnboardingState(
                serverUrl = "http://192.168.1.10:8000",
                apiKey = "geheim",
                validation = Validation.Success,
            ),
            onServerUrlChange = {},
            onApiKeyChange = {},
            onTestConnection = {},
        )
    }
}
