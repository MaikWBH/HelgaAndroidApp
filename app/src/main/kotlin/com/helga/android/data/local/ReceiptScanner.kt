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
     * Expects format like: "Productname                      9,99"
     */
    private fun parseItemLine(line: String): Pair<String, Double>? {
        // Pattern: text followed by price (e.g., "Milch 3,5%              3,49")
        // Match: any text ending with digits and comma/dot
        val pricePattern = Regex("""(\d+)[.,](\d{2})\s*$""")
        val match = pricePattern.find(line) ?: return null

        val priceStr = match.value.trim().replace(",", ".")
        val price = priceStr.toDoubleOrNull() ?: return null

        val name = line.substring(0, match.range.first).trim()
        if (name.isEmpty()) return null

        return name to price
    }

    /**
     * Identifies metadata lines (store info, date, payment method, etc.)
     */
    private fun isMetadataLine(line: String): Boolean {
        val lowerLine = line.lowercase()

        // Skip common receipt headers/footers
        val skipPatterns = listOf(
            "store", "markt", "supermarkt", "shop", "einkauf",
            "date:", "zeit:", "time:", "uhrzeit:", "kasse:",
            "kassierer:", "bon nr", "ticket nr", "receipt",
            "subtotal", "total:", "summe:", "betrag:",
            "danke", "thank you", "wiedersehen", "goodbye",
            "zahlart", "payment", "bar", "card", "ec",
            "mwst", "steuer", "tax", "euro", "eur",
            "rabatt", "discount", "aktion", "angebot"
        )

        return skipPatterns.any { lowerLine.contains(it) }
    }

    /**
     * Extracts store name and total amount from receipt text.
     */
    private fun parseReceiptHeader(text: String): Pair<String, Double> {
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotBlank() }

        var storeName = ""
        var totalAmount = 0.0

        for (i in lines.indices) {
            val line = lines[i]
            val lowerLine = line.lowercase()

            // First non-metadata line is likely store name
            if (storeName.isEmpty() && !isMetadataLine(line) && line.length in 3..50) {
                storeName = line
            }

            // Look for total/sum line
            if (lowerLine.contains("total") || lowerLine.contains("summe") ||
                lowerLine.contains("betrag")) {
                val amount = extractPriceFromLine(line)
                if (amount > 0) {
                    totalAmount = amount
                }
            }
        }

        return storeName to totalAmount
    }

    /**
     * Extracts price value from a text line.
     */
    private fun extractPriceFromLine(line: String): Double {
        val pricePattern = Regex("""(\d+)[.,](\d{2})""")
        val match = pricePattern.find(line)
        return if (match != null) {
            match.value.replace(",", ".").toDoubleOrNull() ?: 0.0
        } else {
            0.0
        }
    }
}
