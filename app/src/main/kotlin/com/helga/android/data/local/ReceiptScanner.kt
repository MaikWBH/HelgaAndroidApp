package com.helga.android.data.local

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.helga.android.data.local.entity.ReceiptEntity
import com.helga.android.data.local.entity.ReceiptItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Ergebnis eines lokalen Kassenzettel-Scans (vor dem Speichern). */
data class ReceiptScanResult(
    val receipt: ReceiptEntity,
    val items: List<ReceiptItemEntity>,
)

@Singleton
class ReceiptScanner @Inject constructor(
    private val context: Context,
) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Scans a receipt image locally using ML Kit Text Recognition.
     * Parses the OCR text to extract store name, items, and total amount.
     * Returns the parsed receipt + items (no DB write, no server call).
     */
    suspend fun scanReceiptImage(bitmap: Bitmap): ReceiptScanResult = withContext(Dispatchers.Default) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(inputImage).await()

        val fullText = result.text
        val (storeName, totalAmount) = parseReceiptHeader(fullText)

        val now = System.currentTimeMillis()
        val receiptId = UUID.randomUUID().toString()

        val receipt = ReceiptEntity(
            id = receiptId,
            storeId = "",
            storeName = storeName,
            shoppingListId = "",
            purchaseDate = LocalDate.now().toEpochDay() * 86400 * 1000,
            totalAmount = totalAmount,
            currency = "EUR",
            imagePath = "",
            localImageUri = "",
            rawOcrText = fullText,
            status = "scanned",
            updatedAt = now,
            deleted = 0,
            dirty = 1,
        )

        // Items mit der frisch erzeugten receiptId verknüpfen
        val items = parseReceiptItems(fullText).map { it.copy(receiptId = receiptId) }

        return@withContext ReceiptScanResult(receipt = receipt, items = items)
    }

    /**
     * Parses OCR text to extract items (name, price).
     * Returns list of ReceiptItemEntity with parsed data.
     */
    private fun parseReceiptItems(text: String): List<ReceiptItemEntity> {
        val items = mutableListOf<ReceiptItemEntity>()
        var position = 0

        val lines = text.split("\n").map { it.trim() }.filter { it.isNotBlank() }

        for (line in lines) {
            // Skip headers, footers, metadata
            if (isMetadataLine(line)) continue

            val parsed = parseItemLine(line)
            if (parsed != null) {
                val (name, price) = parsed
                items.add(
                    ReceiptItemEntity(
                        id = UUID.randomUUID().toString(),
                        receiptId = "", // Will be set after receipt is created
                        position = position++,
                        rawText = line,
                        name = name,
                        quantity = 1.0,
                        unitPrice = price,
                        totalPrice = price,
                        matchedShoppingItemId = "",
                        matchStatus = "",
                        updatedAt = System.currentTimeMillis(),
                        deleted = 0,
                        dirty = 1,
                    )
                )
            }
        }

        return items
    }

    /**
     * Parses a single receipt line to extract item name and price.
     * Erlaubt am Zeilenende eine optionale Steuerklassen-Markierung (z. B. "3,49 A")
     * oder ein Währungskürzel ("3,49 EUR").
     */
    private fun parseItemLine(line: String): Pair<String, Double>? {
        val match = ITEM_PRICE_REGEX.find(line) ?: return null

        val price = match.groupValues[1].replace(",", ".").toDoubleOrNull() ?: return null
        // Unplausible Beträge (z. B. Mengen wie "3,5%") aussortieren wäre möglich;
        // hier reicht: der Name vor dem Preis darf nicht leer sein.
        val name = line.substring(0, match.range.first).trim()
        if (name.isEmpty()) return null

        return name to price
    }

    /**
     * Identifies metadata lines (store info, date, payment method, totals, etc.).
     * Nutzt Wortgrenzen, damit Produktnamen wie "Rhabarber" (enthält "bar") oder
     * "Becher" (enthält "ec") nicht fälschlich verworfen werden.
     */
    private fun isMetadataLine(line: String): Boolean {
        val lower = line.lowercase()
        return METADATA_REGEXES.any { it.containsMatchIn(lower) }
    }

    /**
     * Extracts store name and grand total from receipt text.
     * Der Gesamtbetrag wird aus echten Summen-Zeilen (summe/total) gewählt und
     * gegen Zwischensummen sowie MwSt-Zeilen abgegrenzt; bei mehreren Kandidaten
     * gewinnt der größte Betrag (= Gesamtsumme).
     */
    private fun parseReceiptHeader(text: String): Pair<String, Double> {
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotBlank() }

        var storeName = ""
        val totalCandidates = mutableListOf<Double>()

        for (line in lines) {
            val lower = line.lowercase()

            // Erste plausible Kopfzeile (überwiegend Buchstaben) ist meist der Markt.
            if (storeName.isEmpty() && !isMetadataLine(line) &&
                line.length in 3..50 && line.any { it.isLetter() } &&
                line.count { it.isDigit() } <= line.length / 2
            ) {
                storeName = line
            }

            val isGrandTotal = (lower.contains("summe") || lower.contains("total")) &&
                !lower.contains("zwischen") && !lower.contains("mwst") && !lower.contains("steuer")
            if (isGrandTotal) {
                lastPriceInLine(line)?.let { totalCandidates.add(it) }
            }
        }

        return storeName to (totalCandidates.maxOrNull() ?: 0.0)
    }

    /** Letzter preisartiger Token einer Zeile (Gesamtbetrag steht meist am Ende). */
    private fun lastPriceInLine(line: String): Double? =
        PRICE_REGEX.findAll(line).lastOrNull()?.value?.replace(",", ".")?.toDoubleOrNull()

    private companion object {
        val PRICE_REGEX = Regex("""\d+[.,]\d{2}""")
        val ITEM_PRICE_REGEX = Regex("""(\d+[.,]\d{2})\s*(?:[A-Za-z]|€|EUR)?\s*$""")

        // Schlüsselwörter, die eine Zeile als Metadaten (kein Artikel) kennzeichnen.
        // Wortgrenzen verhindern Substring-Fehltreffer.
        val METADATA_REGEXES: List<Regex> = listOf(
            "summe", "zwischensumme", "total", "betrag", "mwst", "steuer", "ust",
            "netto", "brutto", "rückgeld", "ruckgeld", "gegeben", "rückgabe",
            "kartenzahlung", "karte", "bar", "kasse", "kassierer", "bon", "beleg",
            "rechnung", "datum", "uhrzeit", "zeit", "danke", "wiedersehen",
            "rabatt", "eur", "euro",
        ).map { Regex("""\b""" + Regex.escape(it) + """\b""") }
    }
}
