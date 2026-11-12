package io.github.eltonmaster.fastshopper.data

import io.github.eltonmaster.fastshopper.data.db.LabelEntity

/** Pure helpers for the label picker (unit-tested). */
object LabelPick {
    /** Labels whose name contains the typed text, those starting with it first; all labels for no text. */
    fun filter(labels: List<LabelEntity>, query: String): List<LabelEntity> {
        val q = query.trim()
        if (q.isEmpty()) return labels
        return labels
            .filter { it.name.contains(q, ignoreCase = true) }
            .sortedBy { if (it.name.startsWith(q, ignoreCase = true)) 0 else 1 }
    }

    /** Offer to create a label when text is typed and no label has exactly that name. */
    fun canCreate(labels: List<LabelEntity>, query: String): Boolean {
        val q = query.trim()
        return q.isNotEmpty() && labels.none { it.name.trim().equals(q, ignoreCase = true) }
    }
}
