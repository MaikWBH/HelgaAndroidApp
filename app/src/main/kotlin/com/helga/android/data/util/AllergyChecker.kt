package com.helga.android.data.util

import com.helga.android.data.local.entity.OffProductEntity

object AllergyChecker {
    fun hasAllergens(product: OffProductEntity, userAllergies: List<String>): List<String> {
        if (userAllergies.isEmpty() || product.allergenes.isEmpty()) return emptyList()

        // Parse allergenes JSON-like string
        val productAllergens = product.allergenes
            .trim()
            .removePrefix("[")
            .removeSuffix("]")
            .split(",")
            .map { it.trim().trim('"') }
            .filter { it.isNotBlank() }

        return productAllergens.filter { allergen ->
            userAllergies.any {
                allergen.contains(it, ignoreCase = true) ||
                it.contains(allergen, ignoreCase = true)
            }
        }
    }

    fun hasAnyAllergen(product: OffProductEntity, userAllergies: List<String>): Boolean {
        return hasAllergens(product, userAllergies).isNotEmpty()
    }
}
