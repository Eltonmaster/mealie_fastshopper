package io.github.eltonmaster.fastshopper.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemOrderTest {
    @Test fun moveDown() = assertEquals(listOf("b", "c", "a", "d"), ItemOrder.move(listOf("a", "b", "c", "d"), 0, 2))
    @Test fun moveUp() = assertEquals(listOf("d", "a", "b", "c"), ItemOrder.move(listOf("a", "b", "c", "d"), 3, 0))
    @Test fun moveToSamePlace() = assertEquals(listOf("a", "b"), ItemOrder.move(listOf("a", "b"), 1, 1))
    @Test fun moveOutOfRangeIsIgnored() = assertEquals(listOf("a", "b"), ItemOrder.move(listOf("a", "b"), 0, 5))

    @Test fun renumbersInDisplayOrder() = assertEquals(
        mapOf("c" to 0, "a" to 2),
        ItemOrder.renumber(listOf("c", "b", "a"), mapOf("a" to 0, "b" to 1, "c" to 2)),
    )

    @Test fun unchangedOrderNeedsNoUpdates() =
        assertTrue(ItemOrder.renumber(listOf("a", "b", "c"), mapOf("a" to 0, "b" to 1, "c" to 2)).isEmpty())

    @Test fun gapsAndDuplicatesAreClosed() = assertEquals(
        mapOf("b" to 1, "c" to 2),
        ItemOrder.renumber(listOf("a", "b", "c"), mapOf("a" to 0, "b" to 0, "c" to 7)),
    )

    @Test fun goneItemsAreSkipped() = assertEquals(
        mapOf("c" to 2),
        ItemOrder.renumber(listOf("a", "gone", "c"), mapOf("a" to 0, "c" to 1)),
    )
}
