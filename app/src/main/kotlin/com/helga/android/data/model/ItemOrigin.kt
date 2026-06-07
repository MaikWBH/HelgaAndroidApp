package com.helga.android.data.model

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

/**
 * Eine einzelne Herkunft eines Einkaufsartikels. Wenn z. B. Knoblauch aus zwei
 * Rezepten zusammengefasst wird, hat der Artikel zwei [ItemOrigin]-Einträge.
 *
 * [recipe] ist der Rezeptname; ein leerer Wert bedeutet "manuell hinzugefügt".
 */
@JsonClass(generateAdapter = true)
data class ItemOrigin(
    val recipe: String = "",
    val quantity: Double = 1.0,
    val unit: String = "",
)

/**
 * (De)serialisiert die Herkunftsliste eines Einkaufsartikels als JSON-String,
 * der in [com.helga.android.data.local.entity.ShoppingItemEntity.origins] liegt.
 *
 * Eigene Moshi-Instanz, damit weder Repository noch UI Moshi injizieren müssen.
 */
object ItemOrigins {
    private val moshi = Moshi.Builder().build()
    private val type = Types.newParameterizedType(List::class.java, ItemOrigin::class.java)
    private val adapter = moshi.adapter<List<ItemOrigin>>(type)

    fun decode(json: String): List<ItemOrigin> {
        if (json.isBlank()) return emptyList()
        return runCatching { adapter.fromJson(json) }.getOrNull().orEmpty()
    }

    fun encode(origins: List<ItemOrigin>): String = adapter.toJson(origins)

    /**
     * Fasst Herkünfte mit demselben Rezeptnamen zusammen und summiert die Mengen
     * (nur bei gleicher Einheit). Für die aufgeklappte Aufschlüsselung.
     */
    fun aggregateByRecipe(origins: List<ItemOrigin>): List<ItemOrigin> =
        origins
            .groupBy { it.recipe to it.unit }
            .map { (key, group) ->
                ItemOrigin(
                    recipe = key.first,
                    quantity = group.sumOf { it.quantity },
                    unit = key.second,
                )
            }

    /** Anzahl unterschiedlicher, benannter Rezepte (leere = manuell zählen nicht). */
    fun distinctRecipes(origins: List<ItemOrigin>): List<String> =
        origins.map { it.recipe }.filter { it.isNotBlank() }.distinct()
}
