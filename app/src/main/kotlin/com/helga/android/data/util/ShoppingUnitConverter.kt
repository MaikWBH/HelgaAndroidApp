package com.helga.android.data.util

/**
 * Erkennt zusammengehörige Mengeneinheiten (g/kg, ml/l/cl) und rechnet zwischen
 * ihnen um, damit die Einkaufslisten-Zusammenführung "200g Mehl" + "1kg Mehl"
 * korrekt zu "1200g Mehl" mergen kann statt zwei getrennte Positionen anzulegen.
 * Spiegelt server/app/off.py _UNIT_FACTORS. Einheiten ohne bekannte Familie
 * (z. B. "Stück", "Bund" oder leer) werden weiterhin nur exakt verglichen.
 */
object ShoppingUnitConverter {
    private val MASS_FACTORS = mapOf("g" to 1.0, "gr" to 1.0, "gramm" to 1.0, "kg" to 1000.0)
    private val VOLUME_FACTORS = mapOf("ml" to 1.0, "l" to 1000.0, "cl" to 10.0)

    private fun canonical(unit: String) = unit.trim().lowercase()

    private fun familyFactor(unit: String): Double? {
        val u = canonical(unit)
        return MASS_FACTORS[u] ?: VOLUME_FACTORS[u]
    }

    private fun isMass(unit: String) = MASS_FACTORS.containsKey(canonical(unit))
    private fun isVolume(unit: String) = VOLUME_FACTORS.containsKey(canonical(unit))

    /** True, wenn beide Einheiten exakt gleich sind oder zur selben Umrechnungsfamilie gehören. */
    fun isCompatible(unitA: String, unitB: String): Boolean {
        val a = canonical(unitA)
        val b = canonical(unitB)
        if (a == b) return true
        return (isMass(a) && isMass(b)) || (isVolume(a) && isVolume(b))
    }

    /** Rechnet [quantity] von [fromUnit] in [toUnit] um. Gibt [quantity] unverändert zurück, falls inkompatibel. */
    fun convert(quantity: Double, fromUnit: String, toUnit: String): Double {
        if (canonical(fromUnit) == canonical(toUnit)) return quantity
        val fromFactor = familyFactor(fromUnit) ?: return quantity
        val toFactor = familyFactor(toUnit) ?: return quantity
        return quantity * fromFactor / toFactor
    }
}
