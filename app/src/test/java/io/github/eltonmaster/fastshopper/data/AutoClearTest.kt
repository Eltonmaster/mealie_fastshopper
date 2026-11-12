package io.github.eltonmaster.fastshopper.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AutoClearTest {
    private val now = Instant.parse("2026-11-12T20:00:00Z")

    @Test fun parsesNaiveTimestampAsUtc() =
        assertEquals(Instant.parse("2026-11-12T07:00:00.123456Z"), AutoClear.parse("2026-11-12T07:00:00.123456"))

    @Test fun parsesOffsetTimestamp() =
        assertEquals(Instant.parse("2026-11-12T07:00:00Z"), AutoClear.parse("2026-11-12T08:00:00+01:00"))

    @Test fun garbageIsNull() = assertNull(AutoClear.parse("yesterday"))

    @Test fun checkedOver12HoursIsExpired() =
        assertTrue(AutoClear.isExpired(true, "2026-11-12T07:59:00Z", now))

    @Test fun checkedUnder12HoursStays() =
        assertFalse(AutoClear.isExpired(true, "2026-11-12T08:01:00Z", now))

    @Test fun uncheckedNeverExpires() =
        assertFalse(AutoClear.isExpired(false, "2026-01-01T00:00:00Z", now))

    @Test fun unknownTimeNeverExpires() = assertFalse(AutoClear.isExpired(true, null, now))
}
