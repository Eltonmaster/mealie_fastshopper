package io.github.eltonmaster.fastshopper.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerUrlTest {
    @Test fun addsHttpsAndStripsSlash() =
        assertEquals("https://mealie.example.de", ServerUrl.normalize(" mealie.example.de/ "))

    @Test fun keepsExplicitScheme() =
        assertEquals("http://192.168.1.10:9000", ServerUrl.normalize("http://192.168.1.10:9000"))

    @Test fun keepsSubPath() =
        assertEquals("https://example.de/mealie", ServerUrl.normalize("https://example.de/mealie/"))

    @Test fun rejectsBlank() = assertNull(ServerUrl.normalize("   "))

    @Test fun rejectsGarbage() = assertNull(ServerUrl.normalize("https://"))

    @Test fun baseUrlEndsWithSlash() =
        assertEquals("https://example.de/mealie/", ServerUrl.asBaseUrl("https://example.de/mealie"))
}
