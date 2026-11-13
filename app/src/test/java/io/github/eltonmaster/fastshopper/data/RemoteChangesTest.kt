package io.github.eltonmaster.fastshopper.data

import io.github.eltonmaster.fastshopper.data.RemoteChanges.CHANGED
import io.github.eltonmaster.fastshopper.data.RemoteChanges.NEW
import io.github.eltonmaster.fastshopper.data.db.ItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteChangesTest {
    private fun item(
        id: String = "a",
        quantity: Double = 1.0,
        note: String = "",
        checked: Boolean = false,
        foodId: String? = "milk",
        unitId: String? = null,
        labelId: String? = null,
        updatedAt: String? = "2026-11-13T08:00:00",
        remoteChange: String? = null,
        notified: Boolean = false,
    ) = ItemEntity(
        id = id, listId = "l", quantity = quantity, note = note, display = "", checked = checked,
        position = 0, foodId = foodId, foodName = foodId, unitId = unitId, unitName = null,
        unitAbbreviation = null, unitUseAbbreviation = false, labelId = labelId, labelName = null,
        updatedAt = updatedAt, remoteChange = remoteChange, remoteChangeNotified = notified,
    )

    private val later = "2026-11-13T09:00:00"

    private fun markOne(
        cached: ItemEntity?,
        incoming: ItemEntity,
        own: Set<String> = emptySet(),
        firstSync: Boolean = false,
    ): ItemEntity = RemoteChanges.mark(listOfNotNull(cached), listOf(incoming), own, firstSync).single()

    // ---- New items ----

    @Test fun unknownItemIsNew() = assertEquals(NEW, markOne(null, item()).remoteChange)

    @Test fun nothingIsMarkedOnFirstSync() =
        assertNull(markOne(null, item(), firstSync = true).remoteChange)

    @Test fun newCheckedItemIsNotMarked() = assertNull(markOne(null, item(checked = true)).remoteChange)

    @Test fun ownAddComingBackFromServerIsNotMarked() {
        // The app caches its own adds (without updatedAt) before the server has them.
        val local = item(updatedAt = null)
        assertNull(markOne(local, item(note = "normalised by server", updatedAt = later)).remoteChange)
    }

    // ---- Changed items ----

    @Test fun changedAmountIsChanged() =
        assertEquals(CHANGED, markOne(item(), item(quantity = 2.0, updatedAt = later)).remoteChange)

    @Test fun changedNoteIsChanged() =
        assertEquals(CHANGED, markOne(item(), item(note = "lactose free", updatedAt = later)).remoteChange)

    @Test fun changedFoodIsChanged() =
        assertEquals(CHANGED, markOne(item(), item(foodId = "oat milk", updatedAt = later)).remoteChange)

    @Test fun changedUnitIsChanged() =
        assertEquals(CHANGED, markOne(item(), item(unitId = "bottle", updatedAt = later)).remoteChange)

    @Test fun changedLabelIsChanged() =
        assertEquals(CHANGED, markOne(item(), item(labelId = "dairy", updatedAt = later)).remoteChange)

    @Test fun checkOnlyIsNotAChange() =
        assertNull(markOne(item(), item(checked = true, updatedAt = later)).remoteChange)

    @Test fun uncheckOnlyIsNotAChange() =
        assertNull(markOne(item(checked = true), item(checked = false, updatedAt = later)).remoteChange)

    @Test fun sameUpdatedAtIsNotAChange() =
        assertNull(markOne(item(), item(quantity = 2.0)).remoteChange)

    @Test fun whitespaceInNoteIsNotAChange() =
        assertNull(markOne(item(note = "ripe"), item(note = "ripe ", updatedAt = later)).remoteChange)

    @Test fun ownChangeIsNotMarked() =
        assertNull(markOne(item(), item(quantity = 2.0, updatedAt = later), own = setOf("a")).remoteChange)

    // ---- Keeping and dropping markers ----

    @Test fun existingMarkerIsKept() =
        assertEquals(CHANGED, markOne(item(remoteChange = CHANGED), item()).remoteChange)

    @Test fun newStaysNewWhenChangedAgain() =
        assertEquals(NEW, markOne(item(remoteChange = NEW), item(quantity = 3.0, updatedAt = later)).remoteChange)

    @Test fun markerIsKeptForOwnPendingItem() =
        assertEquals(NEW, markOne(item(remoteChange = NEW), item(), own = setOf("a")).remoteChange)

    @Test fun markerIsKeptOnFirstSyncAfterUpgrade() =
        assertEquals(NEW, markOne(item(remoteChange = NEW), item(), firstSync = true).remoteChange)

    @Test fun checkedElsewhereDropsMarker() =
        assertNull(markOne(item(remoteChange = NEW), item(checked = true, updatedAt = later)).remoteChange)

    // ---- Notified flag ----

    @Test fun freshMarkerIsNotNotified() =
        assertFalse(markOne(null, item(notified = true)).remoteChangeNotified)

    @Test fun keptMarkerKeepsNotified() =
        assertTrue(markOne(item(remoteChange = NEW, notified = true), item()).remoteChangeNotified)

    @Test fun freshChangeResetsNotified() {
        val seenAndNotified = item(notified = true)
        val marked = markOne(seenAndNotified, item(quantity = 2.0, updatedAt = later))
        assertEquals(CHANGED, marked.remoteChange)
        assertFalse(marked.remoteChangeNotified)
    }

    // ---- Whole lists ----

    @Test fun marksOnlyWhatChangedInAList() {
        val cached = listOf(item("a"), item("b"), item("c"))
        val incoming = listOf(
            item("a"),
            item("b", quantity = 2.0, updatedAt = later),
            item("c", checked = true, updatedAt = later),
            item("d"),
        )
        val marks = RemoteChanges.mark(cached, incoming, emptySet(), firstSync = false)
            .associate { it.id to it.remoteChange }
        assertEquals(mapOf("a" to null, "b" to CHANGED, "c" to null, "d" to NEW), marks)
    }
}
