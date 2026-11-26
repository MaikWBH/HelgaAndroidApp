package com.helga.android.data.model

import java.time.YearMonth

/** Ergebnis von „Ältere Bons laden“ (Phase 7, F11). */
sealed interface OlderReceiptsResult {
    /** Im Monat [month] wurden [count] Bons nachgeladen. */
    data class Loaded(val count: Int, val month: YearMonth) : OlderReceiptsResult

    /** In den zwölf Monaten vor [before] gab es keine Bons auf dem Server. */
    data class NothingFound(val before: YearMonth) : OlderReceiptsResult

    data object Failed : OlderReceiptsResult
}
