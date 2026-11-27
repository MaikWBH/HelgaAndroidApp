package com.helga.android.ui.common

/** Einheitlicher Zustand für ViewModels: Laden, Erfolg mit Daten oder Fehler mit Meldung. */
sealed class UiState<out T> {
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}
