package io.github.eltonmaster.fastshopper.data

/** Text of the "someone changed your list" notification (unit-tested). */
object ChangeNotificationText {
    /** How many item names fit in the collapsed notification before "+N more". */
    const val MAX_NAMES = 3

    /** "3 new items", "1 changed item", or "2 new, 1 changed". */
    fun summary(newCount: Int, changedCount: Int): String = when {
        changedCount == 0 -> if (newCount == 1) "1 new item" else "$newCount new items"
        newCount == 0 -> if (changedCount == 1) "1 changed item" else "$changedCount changed items"
        else -> "$newCount new, $changedCount changed"
    }

    /** "Milk, Eggs, Bread" or "Milk, Eggs, Bread +2 more". */
    fun names(names: List<String>, max: Int = MAX_NAMES): String {
        val shown = names.take(max).joinToString(", ")
        val rest = names.size - max
        return if (rest > 0) "$shown +$rest more" else shown
    }

    /** "3 new items (Milk, Eggs, Bread)", shown under the list's name. */
    fun text(newNames: List<String>, changedNames: List<String>): String {
        val all = newNames + changedNames
        val summary = summary(newNames.size, changedNames.size)
        return if (all.isEmpty()) summary else "$summary (${names(all)})"
    }
}
