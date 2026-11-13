package io.github.eltonmaster.fastshopper.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class AutoClearDelayTest {
    private val now = Instant.parse("2026-11-12T20:00:00Z")

    private fun checkedAgo(age: Duration): String = now.minus(age).toString()

    @Test fun roundTripsStoredKeys() {
        AutoClearDelay.entries.forEach { assertEquals(it, AutoClearDelay.fromKey(it.key)) }
    }

    @Test fun keysAreUnique() =
        assertEquals(AutoClearDelay.entries.size, AutoClearDelay.entries.map { it.key }.toSet().size)

    @Test fun missingOrUnknownFallsBackTo12Hours() {
        assertEquals(AutoClearDelay.HOURS_12, AutoClearDelay.DEFAULT)
        assertEquals(AutoClearDelay.HOURS_12, AutoClearDelay.fromKey(null))
        assertEquals(AutoClearDelay.HOURS_12, AutoClearDelay.fromKey("2w"))
    }

    @Test fun offersTheOptionsInOrder() = assertEquals(
        listOf("1 hour", "6 hours", "12 hours", "1 day", "3 days", "Never"),
        AutoClearDelay.entries.map { it.label },
    )

    @Test fun durations() {
        assertEquals(Duration.ofHours(1), AutoClearDelay.HOUR_1.duration)
        assertEquals(Duration.ofHours(6), AutoClearDelay.HOURS_6.duration)
        assertEquals(Duration.ofHours(12), AutoClearDelay.HOURS_12.duration)
        assertEquals(Duration.ofHours(24), AutoClearDelay.DAY_1.duration)
        assertEquals(Duration.ofHours(72), AutoClearDelay.DAYS_3.duration)
        assertNull(AutoClearDelay.NEVER.duration)
    }

    @Test fun eachTimedOptionExpiresAtItsDelayAndNotBefore() {
        AutoClearDelay.entries.filter { it.duration != null }.forEach { delay ->
            val after = delay.duration!!
            assertFalse(delay.name, AutoClear.isExpired(true, checkedAgo(after.minusMinutes(1)), now, delay))
            assertTrue(delay.name, AutoClear.isExpired(true, checkedAgo(after), now, delay))
            assertTrue(delay.name, AutoClear.isExpired(true, checkedAgo(after.plusMinutes(1)), now, delay))
        }
    }

    @Test fun shorterDelayClearsWhatALongerOneKeeps() {
        val sevenHoursAgo = checkedAgo(Duration.ofHours(7))
        assertTrue(AutoClear.isExpired(true, sevenHoursAgo, now, AutoClearDelay.HOURS_6))
        assertFalse(AutoClear.isExpired(true, sevenHoursAgo, now, AutoClearDelay.HOURS_12))
    }

    @Test fun neverKeepsEvenVeryOldItems() =
        assertFalse(AutoClear.isExpired(true, checkedAgo(Duration.ofDays(365)), now, AutoClearDelay.NEVER))

    @Test fun uncheckedItemsNeverExpireWhateverTheDelay() {
        AutoClearDelay.entries.forEach { delay ->
            assertFalse(delay.name, AutoClear.isExpired(false, checkedAgo(Duration.ofDays(30)), now, delay))
        }
    }

    @Test fun hintNamesTheDelay() {
        assertEquals("Checked items are removed automatically after 1 hour.", AutoClearDelay.HOUR_1.hint)
        assertEquals("Checked items are removed automatically after 3 days.", AutoClearDelay.DAYS_3.hint)
        assertEquals("Checked items stay until you delete them.", AutoClearDelay.NEVER.hint)
    }
}
