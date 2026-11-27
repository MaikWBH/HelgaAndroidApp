package com.helga.android.ui.common

import com.helga.android.R
import com.helga.android.data.repository.AiUnavailableException
import java.io.IOException

/** Verständlicher Text für einen fehlgeschlagenen KI-Aufruf; nie die rohe Fehlermeldung (Phase 8, B9-P1). */
fun Throwable.aiErrorText(fallback: UiText): UiText = when {
    this is AiUnavailableException -> when (kind) {
        AiUnavailableException.Kind.OFFLINE -> UiText.Res(R.string.ai_error_offline)
        AiUnavailableException.Kind.SERVER -> UiText.Res(R.string.ai_error_server)
        AiUnavailableException.Kind.AI_DOWN -> UiText.Res(R.string.ai_error_ai_down)
    }
    this is IOException -> UiText.Res(R.string.ai_error_offline)
    else -> fallback
}
