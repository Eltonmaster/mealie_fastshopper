package io.github.eltonmaster.fastshopper.data

import io.github.eltonmaster.fastshopper.data.FoodSearch.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodSearchTest {
    private fun c(name: String, times: Int = 0, last: Long = 0) = Candidate(name, name, times, last)

    @Test fun umlautsAreIgnored() =
        assertEquals(listOf("Möhre"), FoodSearch.rank("mohre", listOf(c("Möhre"), c("Milch"))).map { it.name })

    @Test fun sharpSMatchesSs() =
        assertEquals(listOf("Weißwurst"), FoodSearch.rank("weissw", listOf(c("Weißwurst"))).map { it.name })

    @Test fun startsWithBeatsContains() =
        assertEquals(
            listOf("Milch", "Buttermilch"),
            FoodSearch.rank("milch", listOf(c("Buttermilch", times = 9), c("Milch"))).map { it.name },
        )

    @Test fun frequentFirstAmongPrefixMatches() =
        assertEquals(
            listOf("Milchreis", "Milch"),
            FoodSearch.rank("mil", listOf(c("Milch", times = 1), c("Milchreis", times = 5))).map { it.name },
        )

    @Test fun wordStartMatches() =
        assertEquals(listOf("Rote Bete"), FoodSearch.rank("bete", listOf(c("Rote Bete"), c("Apfel"))).map { it.name })

    @Test fun emptyQueryGivesNothing() = assertTrue(FoodSearch.rank(" ", listOf(c("Milch"))).isEmpty())

    @Test fun limitIsApplied() =
        assertEquals(2, FoodSearch.rank("a", listOf(c("Apfel"), c("Ananas"), c("Avocado")), limit = 2).size)

    @Test fun exactMatchIgnoresCaseAndAccents() = assertTrue(FoodSearch.isExactMatch(" möhre", "Möhre"))
}
