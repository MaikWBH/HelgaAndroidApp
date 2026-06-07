package com.helga.android.data.model

data class ItemCostEstimate(
    val itemId: String,
    val name: String,
    val quantity: Double,
    val unit: String,
    val price: Double? = null, // null if not found
    val totalPrice: Double? = null, // quantity * price if price found
)

data class StoreCost(
    val storeName: String,
    val totalCost: Double,
)

data class ListCostEstimate(
    val listId: String,
    val items: List<ItemCostEstimate>,
    val totalCost: Double,
    val estimatedAccuracy: Double, // percentage of items with prices
    val storeComparison: List<StoreCost>,
)
