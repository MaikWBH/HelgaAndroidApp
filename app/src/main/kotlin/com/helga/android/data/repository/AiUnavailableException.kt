package com.helga.android.data.repository

/** KI-Aufruf nicht möglich; die Art entscheidet über den Text in der UI (Phase 8, B9-P1). */
class AiUnavailableException(val kind: Kind, cause: Throwable? = null) : Exception(kind.name, cause) {
    enum class Kind { OFFLINE, SERVER, AI_DOWN }
}
