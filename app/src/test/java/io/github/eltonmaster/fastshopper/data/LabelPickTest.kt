package io.github.eltonmaster.fastshopper.data

import io.github.eltonmaster.fastshopper.data.db.LabelEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelPickTest {
    private val labels = listOf("Getränke", "Obst & Gemüse", "Tiefkühl", "Kühlregal").map { LabelEntity(it, it, null) }

    @Test fun emptyQueryShowsAll() = assertEquals(labels, LabelPick.filter(labels, " "))

    @Test fun filtersByContainsIgnoringCase() =
        assertEquals(listOf("Kühlregal", "Tiefkühl"), LabelPick.filter(labels, "kühl").map { it.name })

    @Test fun prefixMatchesComeFirst() =
        assertEquals("Kühlregal", LabelPick.filter(labels, "KÜHL").first().name)

    @Test fun offersCreateForNewName() = assertTrue(LabelPick.canCreate(labels, "Drogerie"))
    @Test fun noCreateForExistingName() = assertFalse(LabelPick.canCreate(labels, " getränke "))
    @Test fun noCreateWithoutText() = assertFalse(LabelPick.canCreate(labels, "  "))
}
