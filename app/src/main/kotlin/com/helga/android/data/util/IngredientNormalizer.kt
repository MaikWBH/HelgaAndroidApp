package com.helga.android.data.util

/**
 * Einheitliche Normalisierung von Zutatnamen, damit Mapping-Schreiben/-Lesen und der
 * Nährwert-Fallback denselben Schlüssel verwenden. "Butter", "butter", " Butter "
 * werden so identisch behandelt.
 */
object IngredientNormalizer {
    fun normalize(food: String): String = food.trim().lowercase()
}
