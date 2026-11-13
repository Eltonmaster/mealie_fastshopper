package io.github.eltonmaster.fastshopper.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Pure helpers for creating, renaming and deleting shopping lists. */
object ListEdit {
    /** The name to send to Mealie, or null when nothing usable was typed. */
    fun cleanName(input: String): String? = input.trim().takeIf { it.isNotEmpty() }

    /**
     * The body for Mealie's list PUT, built from the list exactly as Mealie returned it.
     *
     * Mealie replaces the whole list with what it receives, and a missing `listItems` means
     * "no items", which would wipe the list. So everything goes back as received and only the
     * name changes. Returns null when the fetched list has no `listItems` array, because sending
     * that back could delete the items.
     */
    fun renamed(list: JsonObject, name: String): JsonObject? {
        if (list["listItems"] !is JsonArray) return null
        return JsonObject(list + ("name" to JsonPrimitive(name)))
    }

    /** The question asked before deleting a list. [itemCount] is null when it couldn't be loaded. */
    fun deletePrompt(name: String, itemCount: Int?): String {
        val question = when (itemCount) {
            null -> "Delete $name and all its items?"
            0 -> "Delete $name?"
            1 -> "Delete $name and its 1 item?"
            else -> "Delete $name and its $itemCount items?"
        }
        return "$question This removes it for everyone in your household."
    }
}
