package io.github.eltonmaster.fastshopper.data

import io.github.eltonmaster.fastshopper.data.db.ItemEntity

/** Pure helpers for filling the edit sheet from an item and turning it back into a note (unit-tested). */
object ItemEdit {
    /** Text for the name field: the food's name, or the note for a plain note item. */
    fun nameText(item: ItemEntity): String =
        if (item.foodId != null) item.foodName?.takeIf { it.isNotBlank() } ?: item.display
        else ItemFormat.title(null, item.note, item.display)

    /** Text for the note field. A plain note item's note is already in the name field. */
    fun noteText(item: ItemEntity): String = if (item.foodId != null) item.note else ""

    fun quantityText(quantity: Double): String = ItemFormat.number(if (quantity > 0) quantity else 1.0)

    /**
     * A plain note item stays a note while its text is left alone, instead of becoming a new
     * food in Mealie just because it was opened for editing.
     */
    fun staysNote(originalFoodId: String?, originalName: String, query: String): Boolean =
        originalFoodId == null && query.trim() == originalName.trim()

    /** The note to save: for a note item the name and the extra note together, else just the note. */
    fun composeNote(staysNote: Boolean, query: String, note: String): String =
        if (staysNote) listOf(query.trim(), note.trim()).filter { it.isNotEmpty() }.joinToString(" ")
        else note.trim()
}
