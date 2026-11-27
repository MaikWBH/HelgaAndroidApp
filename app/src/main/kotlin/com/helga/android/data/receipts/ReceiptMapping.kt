package com.helga.android.data.receipts

import com.helga.android.data.local.entity.ReceiptAggregateEntity
import com.helga.android.data.local.entity.ReceiptEntity
import com.helga.android.data.local.entity.ReceiptItemEntity
import com.helga.android.data.remote.dto.ReceiptAggregateDto
import com.helga.android.data.remote.dto.ReceiptRemoteDto
import com.helga.android.data.remote.dto.ReceiptRemoteItemDto
import com.helga.android.data.remote.dto.ReceiptUpsertDto

/**
 * Abbildung Server-Bon ↔ Room (Bon-Archiv, Phase 3a). Jede Server-Antwort wird zuerst in Room geschrieben
 * (`dirty = 0`); gerätelokale Felder (`localImageUri`, `pendingUpload`, `cachedUntil`, `rawOcrText`) bleiben erhalten.
 */
fun ReceiptRemoteDto.toEntity(local: ReceiptEntity?): ReceiptEntity = ReceiptEntity(
    id = id,
    storeId = storeId,
    storeName = storeName,
    shoppingListId = shoppingListId,
    purchaseDate = purchaseDate,
    totalAmount = totalAmount,
    currency = currency,
    imagePath = imagePath,
    localImageUri = local?.localImageUri.orEmpty(),
    rawOcrText = local?.rawOcrText.orEmpty(),
    status = status,
    source = source.ifBlank { local?.source ?: "ai" },
    storeAddress = storeAddress,
    purchaseDay = purchaseDay,
    purchaseTime = purchaseTime,
    discountTotal = discountTotal,
    depositTotal = depositTotal,
    confidence = confidence,
    parseStatus = parseStatus,
    pendingUpload = local?.pendingUpload ?: 0,
    cachedUntil = local?.cachedUntil ?: 0L,
    updatedAt = updatedAt,
    deleted = deleted,
    dirty = 0,
)

fun ReceiptRemoteItemDto.toEntity(): ReceiptItemEntity = ReceiptItemEntity(
    id = id,
    receiptId = receiptId,
    position = position,
    rawText = rawText,
    name = name,
    quantity = quantity,
    unitPrice = unitPrice,
    totalPrice = totalPrice,
    unit = unit,
    discount = discount,
    isDeposit = isDeposit,
    category = category,
    offBarcode = offBarcode,
    confidence = confidence,
    matchedShoppingItemId = matchedShoppingItemId,
    matchStatus = matchStatus,
    updatedAt = updatedAt,
    deleted = deleted,
    dirty = 0,
)

fun ReceiptItemEntity.toRemoteDto(): ReceiptRemoteItemDto = ReceiptRemoteItemDto(
    id = id,
    receiptId = receiptId,
    position = position,
    rawText = rawText,
    name = name,
    quantity = quantity,
    unit = unit,
    unitPrice = unitPrice,
    totalPrice = totalPrice,
    discount = discount,
    isDeposit = isDeposit,
    category = category,
    offBarcode = offBarcode,
    confidence = confidence,
    matchedShoppingItemId = matchedShoppingItemId,
    matchStatus = matchStatus,
    updatedAt = updatedAt,
    deleted = deleted,
)

/** `items` muss die vollständige Positionsliste des Bons sein (der Server ersetzt damit seine Positionen). */
fun ReceiptEntity.toUpsertDto(items: List<ReceiptItemEntity>): ReceiptUpsertDto = ReceiptUpsertDto(
    id = id,
    storeId = storeId,
    storeName = storeName,
    storeAddress = storeAddress,
    shoppingListId = shoppingListId,
    purchaseDate = purchaseDate,
    purchaseDay = purchaseDay,
    purchaseTime = purchaseTime,
    totalAmount = totalAmount,
    discountTotal = discountTotal,
    depositTotal = depositTotal,
    currency = currency,
    status = status,
    source = source,
    updatedAt = updatedAt,
    deleted = deleted,
    items = items.map { it.toRemoteDto() },
)

fun ReceiptAggregateDto.toEntity(): ReceiptAggregateEntity = ReceiptAggregateEntity(
    id = "$dimension|$period|$key",
    period = period,
    dimension = dimension,
    key = key,
    label = label,
    amount = amount,
    quantity = quantity,
    avgUnitPrice = avgUnitPrice,
    discount = discount,
    count = count,
)
