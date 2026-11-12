package io.github.eltonmaster.fastshopper.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ItemFormatTest {
    @Test fun wholeNumbersHaveNoDecimals() = assertEquals("2", ItemFormat.number(2.0))
    @Test fun fractionsAreTrimmed() = assertEquals("0.5", ItemFormat.number(0.5))
    @Test fun roundsToTwoDecimals() = assertEquals("0.33", ItemFormat.number(1.0 / 3))

    @Test fun amountWithUnit() = assertEquals("2 Packung", ItemFormat.amount(2.0, "Packung", null, false))
    @Test fun amountUsesAbbreviation() = assertEquals("500 g", ItemFormat.amount(500.0, "Gramm", "g", true))
    @Test fun abbreviationIgnoredWhenDisabled() = assertEquals("500 Gramm", ItemFormat.amount(500.0, "Gramm", "g", false))
    @Test fun singleItemWithoutUnitShowsNothing() = assertEquals("", ItemFormat.amount(1.0, null, null, false))
    @Test fun countWithoutUnit() = assertEquals("6", ItemFormat.amount(6.0, null, null, false))
    @Test fun zeroQuantityShowsUnitOnly() = assertEquals("Bund", ItemFormat.amount(0.0, "Bund", null, false))

    @Test fun titlePrefersFood() = assertEquals("Milch", ItemFormat.title("Milch", "laktosefrei", "2 Milch"))
    @Test fun titleFallsBackToNote() = assertEquals("Spülmittel", ItemFormat.title(null, "Spülmittel", ""))
    @Test fun subtitleIsNoteWhenFoodSet() = assertEquals("laktosefrei", ItemFormat.subtitle("Milch", "laktosefrei"))
    @Test fun noSubtitleForNoteItems() = assertNull(ItemFormat.subtitle(null, "Spülmittel"))
}
