package com.helga.android.data.local

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.helga.android.data.local.entity.ReceiptEntity
import com.helga.android.data.local.entity.ReceiptItemEntity
import com.helga.android.data.util.ReceiptImagePreprocessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ergebnis eines Kassenzettel-Scans (vor dem Speichern).
 *
 * [lowConfidenceItemIds] und [needsReview] markieren unsichere Erkennungen, damit
 * die Vorschau den Nutzer gezielt prüfen lässt (Inspiration: Smart Receipts'
 * Pro-Feld-Konfidenz). Defaults leer → der On-Device-Scan bleibt unverändert.
 */
data class ReceiptScanResult(
    val receipt: ReceiptEntity,
    val items: List<ReceiptItemEntity>,
    val lowConfidenceItemIds: Set<String> = emptySet(),
    val needsReview: Boolean = false,
    val source: ScanSource = ScanSource.ON_DEVICE,
)

/** Woher die Bon-Daten stammen – für Transparenz/Debugging in der Vorschau. */
enum class ScanSource { AI, ON_DEVICE }

/** Für das Speichern in [ReceiptEntity.source] (lokales DB-Feld, kein DTO). */
fun ScanSource.toDbValue(): String = when (this) {
    ScanSource.AI -> "ai"
    ScanSource.ON_DEVICE -> "on_device"
}

fun String.toScanSource(): ScanSource = if (this == "ai") ScanSource.AI else ScanSource.ON_DEVICE

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
        // Kontrast/Graustufen vor der OCR verbessern die Texterkennung deutlich.
        val enhanced = ReceiptImagePreprocessor.enhanceForOcr(bitmap)
        val inputImage = InputImage.fromBitmap(enhanced, 0)
        val result = try {
            recognizer.process(inputImage).await()
        } finally {
            if (enhanced !== bitmap) enhanced.recycle()
        }

        // Roher OCR-Text wird unverändert gespeichert (Debug/Nachvollziehbarkeit).
        val fullText = result.text
        // Für die Auswertung visuelle Zeilen aus den Bounding-Boxes rekonstruieren:
        // ML Kit liefert Name (linke Spalte) und Preis (rechte Spalte) oft getrennt.
        val rows = reconstructRows(result)
        val parseText = rows.joinToString("\n")

        val (storeName, totalAmount) = parseReceiptHeader(rows, fullText)
        val purchaseDate = parseReceiptDate(parseText) ?: LocalDate.now()

        val now = System.currentTimeMillis()
        val receiptId = UUID.randomUUID().toString()

        val receipt = ReceiptEntity(
            id = receiptId,
            storeId = "",
            storeName = storeName,
            shoppingListId = "",
            purchaseDate = purchaseDate.toEpochDay() * 86400 * 1000,
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

        // Items aus rekonstruierten Zeilen; Fallback auf Adjacent-Line-Pairing im Roh-Text.
        val reconstructedItems = parseReceiptItems(rows)
        val items = if (reconstructedItems.isNotEmpty()) {
            reconstructedItems
        } else {
            val rawLines = fullText.split("\n").map { it.trim() }.filter { it.isNotBlank() }
            parseReceiptItemsAdjacentLines(rawLines)
        }.map { it.copy(receiptId = receiptId) }

        return@withContext ReceiptScanResult(receipt = receipt, items = items)
    }

    /**
     * Rekonstruiert visuelle Bon-Zeilen aus den Bounding-Boxes der OCR-Zeilen.
     * ML Kit gruppiert Artikelname (linke Spalte) und Preis (rechte Spalte) häufig
     * in getrennte Blöcke; ohne Rekonstruktion stünden Name und Preis auf
     * verschiedenen Text-Zeilen und ließen sich nicht paaren. OCR-Zeilen mit
     * ähnlicher vertikaler Position werden zu einer Zeile zusammengeführt und
     * links → rechts sortiert.
     *
     * Robustheit:
     * - Zeilen ohne eigene BoundingBox erhalten die Box des übergeordneten Blocks
     *   (geschätzte Position innerhalb des Blocks).
     * - Toleranz = max(Zeilenhöhe × 0.9, 12 px), damit Name + Preis auch bei
     *   leicht versetzter vertikaler Ausrichtung noch zusammengeführt werden.
     */
    private fun reconstructRows(visionText: Text): List<String> {
        val ocrLines = mutableListOf<OcrRowLine>()
        for (block in visionText.textBlocks) {
            val blockBox = block.boundingBox
            val blockLineCount = block.lines.size.coerceAtLeast(1)
            for ((lineIdx, line) in block.lines.withIndex()) {
                val box = line.boundingBox
                val (centerY, height, left) = when {
                    box != null -> Triple(
                        (box.top + box.bottom) / 2,
                        (box.bottom - box.top).coerceAtLeast(1),
                        box.left,
                    )
                    blockBox != null -> {
                        // Schätzung: Block gleichmäßig auf Zeilen aufteilen.
                        val blockH = (blockBox.bottom - blockBox.top).coerceAtLeast(1)
                        val estH = blockH / blockLineCount
                        val estTop = blockBox.top + lineIdx * estH
                        Triple(estTop + estH / 2, estH, blockBox.left)
                    }
                    else -> continue
                }
                ocrLines.add(OcrRowLine(text = line.text, centerY = centerY, height = height, left = left))
            }
        }

        // Letzter Ausweg: rohen Text zeilenweise zurückgeben.
        if (ocrLines.isEmpty()) {
            return visionText.text.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        }

        val sorted = ocrLines.sortedBy { it.centerY }
        val rows = mutableListOf<MutableList<OcrRowLine>>()
        for (l in sorted) {
            val row = rows.lastOrNull()
            if (row != null) {
                val rowCenter = row.sumOf { it.centerY } / row.size
                // Großzügige Toleranz (0.9× Höhe, mind. 12 px) damit Name (links)
                // und Preis (rechts) trotz leichtem vertikalem Versatz gepaart werden.
                val tolerance = (row.first().height * 0.9).toInt().coerceAtLeast(12)
                if (kotlin.math.abs(l.centerY - rowCenter) <= tolerance) {
                    row.add(l)
                    continue
                }
            }
            rows.add(mutableListOf(l))
        }
        return rows
            .map { row -> row.sortedBy { it.left }.joinToString(" ") { it.text.trim() }.trim() }
            .filter { it.isNotBlank() }
    }

    /**
     * Parses OCR text to extract items (name, price).
     * Returns list of ReceiptItemEntity with parsed data.
     */
    private fun parseReceiptItems(lines: List<String>): List<ReceiptItemEntity> {
        val items = mutableListOf<ReceiptItemEntity>()
        var position = 0

        for (line in lines) {
            // Skip headers, footers, metadata
            if (isMetadataLine(line)) continue

            val parsed = parseItemLine(line)
            if (parsed != null) {
                val (name, price) = parsed
                items.add(makeItem(position++, line, name, price))
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
    /**
     * Sucht Marktname und Gesamtsumme. [rows] sind die rekonstruierten Zeilen,
     * [rawText] ist der unverarbeitete ML-Kit-Text der als Fallback für den
     * Marktnamen dient, falls die rekonstruierten Zeilen ihn nicht enthalten
     * (z. B. weil die Kopfzeile keine BoundingBox hatte und herausgefiltert wurde).
     */
    private fun parseReceiptHeader(rows: List<String>, rawText: String = ""): Pair<String, Double> {
        var storeName = ""
        val totalCandidates = mutableListOf<Double>()
        val allPrices = mutableListOf<Double>()

        // Alle Zeilen prüfen (rekonstruierte Zeilen + rohe Zeilen für Marktname-Suche).
        val candidateLines = if (rawText.isNotBlank()) {
            val rawLines = rawText.split("\n").map { it.trim() }.filter { it.isNotBlank() }
            // Rohe Zeilen zuerst für den Marktnamen, dann rekonstruierte für Summen.
            (rawLines + rows).distinctBy { it }
        } else rows

        for (line in candidateLines) {
            val lower = line.lowercase()

            if (storeName.isEmpty() && !isMetadataLine(line) &&
                line.length in 3..50 && line.any { it.isLetter() } &&
                line.count { it.isDigit() } <= line.length / 2
            ) {
                storeName = line
            }

            // Alle Preise für den Fallback auf die größte Zahl sammeln.
            PRICE_REGEX.findAll(line).forEach { m ->
                m.value.replace(",", ".").toDoubleOrNull()?.let { allPrices.add(it) }
            }

            val isGrandTotal = TOTAL_KEYWORDS.any { lower.contains(it) } &&
                !lower.contains("zwischen") && !lower.contains("mwst") &&
                !lower.contains("steuer") && !lower.contains("netto")
            if (isGrandTotal) {
                lastPriceInLine(line)?.let { totalCandidates.add(it) }
            }
        }

        val total = totalCandidates.maxOrNull() ?: allPrices.maxOrNull() ?: 0.0
        return storeName to total
    }

    /**
     * Fallback-Parser: paart benachbarte Zeilen, wenn Name (Zeile N) und Preis
     * (Zeile N+1) getrennt vorliegen – typisch wenn die Bounding-Box-Rekonstruktion
     * die Spalten nicht zusammenführen konnte.
     */
    private fun parseReceiptItemsAdjacentLines(rawLines: List<String>): List<ReceiptItemEntity> {
        val items = mutableListOf<ReceiptItemEntity>()
        var position = 0
        var i = 0
        while (i < rawLines.size) {
            val line = rawLines[i]
            if (isMetadataLine(line)) { i++; continue }

            // Versuche, diese Zeile direkt zu parsen (Name + Preis zusammen).
            val direct = parseItemLine(line)
            if (direct != null) {
                val (name, price) = direct
                items.add(makeItem(position++, line, name, price))
                i++
                continue
            }

            // Name-only Zeile? Dann prüfen ob die nächste Zeile ein reiner Preis ist.
            val nextLine = rawLines.getOrNull(i + 1)?.trim() ?: ""
            val nextPrice = if (nextLine.isNotBlank() && !isMetadataLine(nextLine)) {
                // Nächste Zeile ist ein reiner Preis, wenn sie fast nur aus einer Preis-Angabe besteht.
                ITEM_PRICE_REGEX.find(nextLine)?.let { m ->
                    val before = nextLine.substring(0, m.range.first).trim()
                    if (before.isEmpty()) m.groupValues[1].replace(",", ".").toDoubleOrNull() else null
                }
            } else null

            if (nextPrice != null && line.any { it.isLetter() } && !isMetadataLine(line)) {
                val name = line.trim()
                items.add(makeItem(position++, "$line $nextLine", name, nextPrice))
                i += 2 // beide Zeilen verbraucht
            } else {
                i++
            }
        }
        return items
    }

    private fun makeItem(position: Int, rawText: String, name: String, price: Double) =
        ReceiptItemEntity(
            id = UUID.randomUUID().toString(),
            receiptId = "",
            position = position,
            rawText = rawText,
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

    /** Letzter preisartiger Token einer Zeile (Gesamtbetrag steht meist am Ende). */
    private fun lastPriceInLine(line: String): Double? =
        PRICE_REGEX.findAll(line).lastOrNull()?.value?.replace(",", ".")?.toDoubleOrNull()

    /**
     * Versucht das Kaufdatum aus dem Bon-Text zu lesen. Erkennt deutsche Formate
     * (TT.MM.JJJJ / TT.MM.JJ, auch mit "-" oder "/" als Trenner) sowie ISO (JJJJ-MM-TT).
     * Liefert den ersten plausiblen Treffer (nicht in der Zukunft, nicht vor 2000) –
     * sonst `null`, damit der Aufrufer auf "heute" zurückfallen kann.
     */
    private fun parseReceiptDate(text: String): LocalDate? {
        val today = LocalDate.now()

        // Deutsche Formate zuerst (häufiger auf DE-Bons), dann ISO als Fallback.
        val dmyCandidates = DATE_DMY_REGEX.findAll(text).mapNotNull { m ->
            val day = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val month = m.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            val year = normalizeYear(m.groupValues[3].toIntOrNull() ?: return@mapNotNull null)
            safeDate(year, month, day)
        }
        val isoCandidates = DATE_ISO_REGEX.findAll(text).mapNotNull { m ->
            val year = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val month = m.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            val day = m.groupValues[3].toIntOrNull() ?: return@mapNotNull null
            safeDate(year, month, day)
        }

        return (dmyCandidates + isoCandidates)
            .firstOrNull { !it.isBefore(MIN_PLAUSIBLE_DATE) && !it.isAfter(today) }
    }

    /** Zweistellige Jahre als 20xx interpretieren (Kassenbons sind aktuell). */
    private fun normalizeYear(year: Int): Int = if (year < 100) 2000 + year else year

    /** LocalDate.of mit Bereichsprüfung; ungültige Werte (z. B. 31.02.) ergeben null. */
    private fun safeDate(year: Int, month: Int, day: Int): LocalDate? =
        try {
            LocalDate.of(year, month, day)
        } catch (e: java.time.DateTimeException) {
            null
        }

    /** Eine OCR-Zeile mit Lage-Info zur Rekonstruktion visueller Bon-Zeilen. */
    private data class OcrRowLine(
        val text: String,
        val centerY: Int,
        val height: Int,
        val left: Int,
    )

    private companion object {
        val PRICE_REGEX = Regex("""\d+[.,]\d{2}""")
        val ITEM_PRICE_REGEX = Regex("""(\d+[.,]\d{2})\s*(?:[A-Za-z]|€|EUR)?\s*$""")

        // Schlüsselwörter für die Gesamtsummen-Zeile (deutsche Bon-Varianten).
        val TOTAL_KEYWORDS = listOf("summe", "gesamt", "total", "zu zahlen", "zahlbetrag")

        // Kaufdatum: TT.MM.JJJJ / TT.MM.JJ (Trenner . - /) und ISO JJJJ-MM-TT.
        val DATE_DMY_REGEX = Regex("""\b(\d{1,2})[.\-/](\d{1,2})[.\-/](\d{2,4})\b""")
        val DATE_ISO_REGEX = Regex("""\b(\d{4})-(\d{1,2})-(\d{1,2})\b""")

        // Bons älter als 2000 sind unplausibel (meist Fehl-Treffer aus Artikelnummern).
        val MIN_PLAUSIBLE_DATE: LocalDate = LocalDate.of(2000, 1, 1)

        // Schlüsselwörter, die eine Zeile als Metadaten (kein Artikel) kennzeichnen.
        // Wortgrenzen verhindern Substring-Fehltreffer. "eur"/"euro" sind bewusst
        // NICHT enthalten, da Preisspalten oft "EUR" tragen (z. B. "1,99 EUR/kg").
        val METADATA_REGEXES: List<Regex> = listOf(
            "summe", "zwischensumme", "total", "gesamt", "zahlen", "zahlbetrag",
            "betrag", "mwst", "steuer", "ust",
            "netto", "brutto", "rückgeld", "ruckgeld", "gegeben", "rückgabe",
            "kartenzahlung", "karte", "bar", "kasse", "kassierer", "bon", "beleg",
            "rechnung", "datum", "uhrzeit", "zeit", "danke", "wiedersehen",
            "rabatt",
        ).map { Regex("""\b""" + Regex.escape(it) + """\b""") }
    }
}
