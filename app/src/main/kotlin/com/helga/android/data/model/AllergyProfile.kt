package com.helga.android.data.model

data class AllergyProfile(
    val name: String, // e.g., "Kind 1", "Mutter"
    val allergenes: List<String>, // e.g., ["Gluten", "Nussallergien", "Laktose"]
)
