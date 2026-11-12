package io.github.eltonmaster.fastshopper.data

import io.github.eltonmaster.fastshopper.data.db.ItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemEditTest {
    private fun item(foodId: String? = null, foodName: String? = null, note: String = "", display: String = "") =
        ItemEntity(
            id = "i", listId = "l", quantity = 1.0, note = note, display = display, checked = false,
            position = 0, foodId = foodId, foodName = foodName, unitId = null, unitName = null,
            unitAbbreviation = null, unitUseAbbreviation = false, labelId = null, labelName = null,
            updatedAt = null,
        )

    @Test fun foodItemNameIsFood() = assertEquals("Milch", ItemEdit.nameText(item("f", "Milch", "laktosefrei")))
    @Test fun foodItemKeepsNoteSeparate() = assertEquals("laktosefrei", ItemEdit.noteText(item("f", "Milch", "laktosefrei")))
    @Test fun noteItemNameIsNote() = assertEquals("Spülmittel", ItemEdit.nameText(item(note = "Spülmittel")))
    @Test fun noteItemHasNoExtraNote() = assertEquals("", ItemEdit.noteText(item(note = "Spülmittel")))
    @Test fun noteItemFallsBackToDisplay() = assertEquals("2 Brot", ItemEdit.nameText(item(display = "2 Brot")))

    @Test fun zeroQuantityStartsAtOne() = assertEquals("1", ItemEdit.quantityText(0.0))
    @Test fun fractionalQuantityKept() = assertEquals("0.5", ItemEdit.quantityText(0.5))

    @Test fun untouchedNoteItemStaysNote() = assertTrue(ItemEdit.staysNote(null, "Spülmittel", " Spülmittel "))
    @Test fun renamedNoteItemDoesNotStayNote() = assertFalse(ItemEdit.staysNote(null, "Spülmittel", "Spüli"))
    @Test fun foodItemNeverStaysNote() = assertFalse(ItemEdit.staysNote("f", "Milch", "Milch"))

    @Test fun noteItemJoinsNameAndNote() =
        assertEquals("Spülmittel Zitrone", ItemEdit.composeNote(true, "Spülmittel", " Zitrone"))
    @Test fun foodItemNoteIsJustTheNote() = assertEquals("laktosefrei", ItemEdit.composeNote(false, "Milch", "laktosefrei "))
}
