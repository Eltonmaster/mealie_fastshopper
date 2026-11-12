package io.github.eltonmaster.fastshopper.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test fun parsesTag() = assertEquals(AppVersion(0, 2, 0), AppVersion.parse("v0.2.0"))

    @Test fun parsesVersionName() = assertEquals(AppVersion(1, 12, 3), AppVersion.parse("1.12.3"))

    @Test fun parsesPreRelease() =
        assertEquals(AppVersion(0, 2, 0, "beta.1"), AppVersion.parse("v0.2.0-beta.1"))

    @Test fun rejectsGarbage() {
        assertNull(AppVersion.parse("latest"))
        assertNull(AppVersion.parse("v1.2"))
        assertNull(AppVersion.parse(null))
    }

    @Test fun comparesNumerically() {
        assertTrue(AppVersion.parse("0.10.0")!! > AppVersion.parse("0.9.9")!!)
        assertTrue(AppVersion.parse("1.0.0")!! > AppVersion.parse("0.99.99")!!)
        assertTrue(AppVersion.parse("0.2.1")!! > AppVersion.parse("0.2.0")!!)
    }

    @Test fun preReleaseSortsBelowRelease() {
        assertTrue(AppVersion.parse("0.2.0-beta.1")!! < AppVersion.parse("0.2.0")!!)
        assertTrue(AppVersion.parse("0.2.0-beta.1")!! > AppVersion.parse("0.1.0")!!)
    }

    @Test fun printsWithoutPrefix() = assertEquals("0.2.0", AppVersion.parse("v0.2.0").toString())
}
