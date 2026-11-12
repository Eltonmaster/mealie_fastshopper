package io.github.eltonmaster.fastshopper.data

import java.text.Normalizer

/** Autocomplete matching and ranking for the add sheet (unit-tested). */
object FoodSearch {
    data class Candidate(val id: String, val name: String, val timesAdded: Int, val lastAddedAt: Long)

    private val marks = Regex("\\p{M}+")

    /** Lower-case, no accents/umlauts, ß as ss: "Möhre" and "mohre" match. */
    fun normalize(text: String): String =
        marks.replace(Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD), "")
            .replace("ß", "ss")

    fun rank(query: String, candidates: List<Candidate>, limit: Int = 6): List<Candidate> {
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        return candidates
            .mapNotNull { c ->
                val n = normalize(c.name)
                val score = when {
                    n == q -> 0
                    n.startsWith(q) -> 1
                    n.split(' ', '-').any { it.startsWith(q) } -> 2
                    n.contains(q) -> 3
                    else -> return@mapNotNull null
                }
                score to c
            }
            .sortedWith(
                compareBy<Pair<Int, Candidate>> { it.first.coerceAtLeast(1) } // exact counts as starts-with
                    .thenByDescending { it.second.timesAdded }
                    .thenByDescending { it.second.lastAddedAt }
                    .thenBy { it.first }
                    .thenBy { normalize(it.second.name) }
            )
            .take(limit)
            .map { it.second }
    }

    fun isExactMatch(query: String, name: String): Boolean = normalize(query) == normalize(name)
}
