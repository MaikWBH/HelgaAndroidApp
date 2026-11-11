package com.helga.android.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.remote.ConnectionResult
import com.helga.android.data.remote.ServerConnectionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val connectionChecker: ServerConnectionChecker,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun setServerUrl(url: String) = _state.update { it.copy(serverUrl = url, validation = Validation.Idle) }
    fun setApiKey(key: String) = _state.update { it.copy(apiKey = key, validation = Validation.Idle) }

    fun testConnection(onSuccess: () -> Unit) {
        val url = _state.value.serverUrl.trim()
        val key = _state.value.apiKey.trim()
        if (url.isBlank() || !url.startsWith("http")) {
            _state.update { it.copy(validation = Validation.InvalidUrl) }
            return
        }
        _state.update { it.copy(validation = Validation.Testing) }
        viewModelScope.launch {
            when (val result = connectionChecker.test(url, key)) {
                is ConnectionResult.Success -> {
                    _state.update { it.copy(validation = Validation.Success) }
                    onSuccess()
                }
                is ConnectionResult.HttpError -> _state.update {
                    it.copy(
                        validation = if (result.code == 401 || result.code == 403)
                            Validation.Unauthorized else Validation.Unreachable
                    )
                }
                is ConnectionResult.Unreachable, is ConnectionResult.Other -> _state.update {
                    it.copy(validation = Validation.Unreachable)
                }
            }
        }
    }
}

data class OnboardingState(
    val serverUrl: String = "",
    val apiKey: String = "",
    val validation: Validation = Validation.Idle,
)

sealed interface Validation {
    data object Idle : Validation
    data object Testing : Validation
    data object Success : Validation
    data object InvalidUrl : Validation
    data object Unreachable : Validation
    data object Unauthorized : Validation
}
