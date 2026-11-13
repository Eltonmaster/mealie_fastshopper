package io.github.eltonmaster.fastshopper.data

/** Pure helpers for dragging items into a new order (unit-tested). */
object ItemOrder {
    /** [list] with the element at [from] moved to [to]; out-of-range indexes leave it unchanged. */
    fun <T> move(list: List<T>, from: Int, to: Int): List<T> {
        if (from == to || from !in list.indices || to !in list.indices) return list
        return list.toMutableList().apply { add(to, removeAt(from)) }
    }

    /**
     * Numbers the open items 0 to n−1 in display order ([orderedIds]: sections in order, then
     * the items in each) and returns only the items whose position changed, so only those get
     * an UPDATE. Ids that aren't in [currentPositions] (gone meanwhile) are skipped.
     */
    fun renumber(orderedIds: List<String>, currentPositions: Map<String, Int>): Map<String, Int> =
        orderedIds.withIndex()
            .filter { (index, id) -> id in currentPositions && currentPositions[id] != index }
            .associate { (index, id) -> id to index }
}
