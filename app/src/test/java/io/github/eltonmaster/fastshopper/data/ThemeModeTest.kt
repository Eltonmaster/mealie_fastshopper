package io.github.eltonmaster.fastshopper.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    @Test fun roundTripsStoredKeys() {
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.fromKey(it.key)) }
    }

    @Test fun missingOrUnknownFollowsSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey("sepia"))
    }

    @Test fun lightAndDarkIgnoreTheSystem() {
        assertFalse(ThemeMode.LIGHT.isDark(systemInDarkMode = true))
        assertTrue(ThemeMode.DARK.isDark(systemInDarkMode = false))
    }

    @Test fun systemFollowsTheSystem() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemInDarkMode = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemInDarkMode = false))
    }
}
