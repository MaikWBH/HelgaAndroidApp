package com.helga.android.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Bon-Archiv (Phase 3a): bon-weises Protokoll `/api/receipts…`, getrennt vom Voll-Sync.
 * Feldnamen und Regeln: `.claude/phasen/phase-3-daten/server-vertrag.md`.
 */
@JsonClass(generateAdapter = true)
data class ReceiptRemoteItemDto(
    val id: String,
    @Json(name = "receipt_id") val receiptId: String = "",
    val position: Int = 0,
    @Json(name = "raw_text") val rawText: String = "",
    val name: String = "",
    val quantity: Double = 1.0,
    val unit: String = "",
    @Json(name = "unit_price") val unitPrice: Double = 0.0,
    @Json(name = "total_price") val totalPrice: Double = 0.0,
    val discount: Double = 0.0,
    @Json(name = "is_deposit") val isDeposit: Int = 0,
    val category: String = "",
    @Json(name = "off_barcode") val offBarcode: String = "",
    val confidence: Double = 1.0,
    @Json(name = "matched_shopping_item_id") val matchedShoppingItemId: String = "",
    @Json(name = "match_status") val matchStatus: String = "",
    @Json(name = "updated_at") val updatedAt: Long = 0,
    val deleted: Int = 0,
)

/** Antwort des Servers (`ReceiptOut`). `items` enthält nur nicht gelöschte Positionen, nach `position`. */
@JsonClass(generateAdapter = true)
data class ReceiptRemoteDto(
    val id: String,
    @Json(name = "store_id") val storeId: String = "",
    @Json(name = "store_name") val storeName: String = "",
    @Json(name = "store_address") val storeAddress: String = "",
    @Json(name = "shopping_list_id") val shoppingListId: String = "",
    @Json(name = "purchase_date") val purchaseDate: Long = 0,
    @Json(name = "purchase_day") val purchaseDay: String = "",
    @Json(name = "purchase_time") val purchaseTime: String = "",
    @Json(name = "total_amount") val totalAmount: Double = 0.0,
    @Json(name = "discount_total") val discountTotal: Double = 0.0,
    @Json(name = "deposit_total") val depositTotal: Double = 0.0,
    val currency: String = "EUR",
    @Json(name = "image_path") val imagePath: String = "",
    val status: String = "scanned",
    val source: String = "",
    val confidence: Double = 1.0,
    @Json(name = "parse_status") val parseStatus: String = "none",
    @Json(name = "updated_at") val updatedAt: Long = 0,
    val deleted: Int = 0,
    @Json(name = "server_seq") val serverSeq: Long = 0,
    val items: List<ReceiptRemoteItemDto> = emptyList(),
)

/** Anfrage an den Server (`ReceiptIn`): `items` ist immer die vollständige Positionsliste. */
@JsonClass(generateAdapter = true)
data class ReceiptUpsertDto(
    val id: String,
    @Json(name = "store_id") val storeId: String = "",
    @Json(name = "store_name") val storeName: String = "",
    @Json(name = "store_address") val storeAddress: String = "",
    @Json(name = "shopping_list_id") val shoppingListId: String = "",
    @Json(name = "purchase_date") val purchaseDate: Long = 0,
    @Json(name = "purchase_day") val purchaseDay: String = "",
    @Json(name = "purchase_time") val purchaseTime: String = "",
    @Json(name = "total_amount") val totalAmount: Double = 0.0,
    @Json(name = "discount_total") val discountTotal: Double = 0.0,
    @Json(name = "deposit_total") val depositTotal: Double = 0.0,
    val currency: String = "EUR",
    val status: String = "scanned",
    val source: String = "",
    @Json(name = "updated_at") val updatedAt: Long,
    val deleted: Int = 0,
    val items: List<ReceiptRemoteItemDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class ReceiptListResponse(
    @Json(name = "server_seq") val serverSeq: Long = 0,
    val receipts: List<ReceiptRemoteDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class ReceiptPutResponse(
    val applied: Boolean = false,
    val receipt: ReceiptRemoteDto,
)

@JsonClass(generateAdapter = true)
data class ReceiptScanResponse(
    val parsed: Boolean = false,
    val receipt: ReceiptRemoteDto,
)

@JsonClass(generateAdapter = true)
data class ReceiptImageResponse(
    @Json(name = "image_path") val imagePath: String = "",
)

@JsonClass(generateAdapter = true)
data class LexiconEntryDto(
    val id: String,
    @Json(name = "store_key") val storeKey: String = "",
    @Json(name = "store_name") val storeName: String = "",
    @Json(name = "raw_text") val rawText: String = "",
    val name: String = "",
    val category: String = "",
    @Json(name = "off_barcode") val offBarcode: String = "",
    val hits: Int = 0,
    @Json(name = "updated_at") val updatedAt: Long = 0,
)

@JsonClass(generateAdapter = true)
data class LexiconListResponse(
    val entries: List<LexiconEntryDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class OkResponse(
    val ok: Boolean = true,
)

@JsonClass(generateAdapter = true)
data class OffSearchRequest(
    val query: String,
    val limit: Int = 5,
)

@JsonClass(generateAdapter = true)
data class OffSearchResponse(
    val products: List<OffProductDto> = emptyList(),
)

/** Eine Zeile der Server-Auswertung (`GET /api/receipts/aggregates`). */
@JsonClass(generateAdapter = true)
data class ReceiptAggregateDto(
    val period: String,
    val dimension: String,
    val key: String = "",
    val label: String = "",
    val amount: Double = 0.0,
    val quantity: Double = 0.0,
    val count: Int = 0,
    @Json(name = "avg_unit_price") val avgUnitPrice: Double = 0.0,
    val discount: Double = 0.0,
)

@JsonClass(generateAdapter = true)
data class ReceiptAggregatesResponse(
    @Json(name = "generated_at") val generatedAt: Long = 0,
    val rows: List<ReceiptAggregateDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class CategorizeRequest(
    val names: List<String>,
)

@JsonClass(generateAdapter = true)
data class CategorizeResultDto(
    val name: String = "",
    val category: String = "",
)

@JsonClass(generateAdapter = true)
data class CategorizeResponse(
    val results: List<CategorizeResultDto> = emptyList(),
)
