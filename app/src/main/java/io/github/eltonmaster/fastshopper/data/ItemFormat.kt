package io.github.eltonmaster.fastshopper.data

import kotlin.math.abs
import kotlin.math.roundToLong

/** Pure formatting helpers for list rows (unit-tested). */
object ItemFormat {
    fun number(value: Double): String {
        if (abs(value - value.roundToLong()) < 1e-9) return value.roundToLong().toString()
        return "%.2f".format(java.util.Locale.ROOT, value).trimEnd('0').trimEnd('.')
    }

    /**
     * Amount shown on the right of a row, e.g. "2 Packung" or "500 g".
     * Plain note items with quantity 1 and no unit show nothing.
     */
    fun amount(
        quantity: Double,
        unitName: String?,
        unitAbbreviation: String?,
        useAbbreviation: Boolean,
    ): String {
        val unit = if (useAbbreviation && !unitAbbreviation.isNullOrBlank()) unitAbbreviation else unitName
        val hasUnit = !unit.isNullOrBlank()
        if (quantity <= 0.0) return unit.orEmpty()
        if (!hasUnit && abs(quantity - 1.0) < 1e-9) return ""
        return if (hasUnit) "${number(quantity)} $unit" else number(quantity)
    }

    /** Main row text: the food name, else the note, else Mealie's display string. */
    fun title(foodName: String?, note: String, display: String): String = when {
        !foodName.isNullOrBlank() -> foodName
        note.isNotBlank() -> note
        else -> display
    }

    /** Secondary text: the note when a food is set. */
    fun subtitle(foodName: String?, note: String): String? =
        if (!foodName.isNullOrBlank() && note.isNotBlank()) note else null
}
