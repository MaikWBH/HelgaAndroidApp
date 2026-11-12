package com.helga.android.data.receipts

/** Ergebnis eines Bon-Scans. */
sealed interface ReceiptScanOutcome {
    /** Die KI hat ausgelesen; der Bon (Status „review") liegt in Room. */
    data class Parsed(val receiptId: String) : ReceiptScanOutcome

    /**
     * Nur vorläufige Werte (ML Kit) liegen in Room. [queuedOnServer] = true: Der Server hat das Foto und wertet
     * es später aus; false: Das Foto wartet lokal auf den Upload (Server nicht erreichbar).
     */
    data class Provisional(val receiptId: String, val queuedOnServer: Boolean) : ReceiptScanOutcome
}
