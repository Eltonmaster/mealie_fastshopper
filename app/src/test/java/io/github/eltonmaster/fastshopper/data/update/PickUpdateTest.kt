package io.github.eltonmaster.fastshopper.data.update

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PickUpdateTest {
    private val json = Json { ignoreUnknownKeys = true }

    // Trimmed copy of what GitHub returns for /releases/latest.
    private val releaseJson = """
        {
          "tag_name": "v0.2.0",
          "html_url": "https://github.com/Eltonmaster/mealie_fastshopper/releases/tag/v0.2.0",
          "draft": false,
          "prerelease": false,
          "body": "## What's Changed\r\n* Add item editing by @Eltonmaster in https://github.com/Eltonmaster/mealie_fastshopper/pull/14\r\n* Add in-app update check by @Eltonmaster in https://github.com/Eltonmaster/mealie_fastshopper/pull/13\r\n\r\n## New Contributors\r\n* @someone made their first contribution in https://github.com/Eltonmaster/mealie_fastshopper/pull/9\r\n\r\n**Full Changelog**: https://github.com/Eltonmaster/mealie_fastshopper/compare/v0.1.0...v0.2.0",
          "assets": [
            {
              "name": "fastshopper-0.2.0.apk.sha256",
              "browser_download_url": "https://github.com/Eltonmaster/mealie_fastshopper/releases/download/v0.2.0/fastshopper-0.2.0.apk.sha256",
              "size": 88,
              "digest": "sha256:5a7bd88a61bbf25819ae96983cec0fd2b46fa01acac12b2b0a1702755cd07ae5"
            },
            {
              "name": "fastshopper-0.2.0.apk",
              "browser_download_url": "https://github.com/Eltonmaster/mealie_fastshopper/releases/download/v0.2.0/fastshopper-0.2.0.apk",
              "size": 12352810,
              "digest": "sha256:1BAE9F447C113839B2D82BB1173F62BF0C9E5B802D173CDF7C65D9967A3F39A0"
            }
          ]
        }
    """.trimIndent()

    private val release = json.decodeFromString(GithubRelease.serializer(), releaseJson)

    @Test fun offersNewerRelease() {
        val update = pickUpdate(release, AppVersion(0, 1, 0))
        assertNotNull(update)
        update!!
        assertEquals(AppVersion(0, 2, 0), update.version)
        assertEquals("fastshopper-0.2.0.apk", update.apkName)
        assertEquals(
            "https://github.com/Eltonmaster/mealie_fastshopper/releases/download/v0.2.0/fastshopper-0.2.0.apk",
            update.apkUrl,
        )
        assertEquals(12352810L, update.sizeBytes)
        assertEquals("1bae9f447c113839b2d82bb1173f62bf0c9e5b802d173cdf7c65d9967a3f39a0", update.sha256)
        assertEquals(listOf("Add item editing", "Add in-app update check"), update.highlights)
    }

    @Test fun skipsSameVersion() = assertNull(pickUpdate(release, AppVersion(0, 2, 0)))

    @Test fun skipsOlderVersion() = assertNull(pickUpdate(release, AppVersion(0, 3, 0)))

    @Test fun skipsPreRelease() = assertNull(pickUpdate(release.copy(prerelease = true), AppVersion(0, 1, 0)))

    @Test fun skipsReleaseWithoutApk() =
        assertNull(pickUpdate(release.copy(assets = release.assets.filterNot { it.name.endsWith(".apk") }), AppVersion(0, 1, 0)))

    @Test fun ignoresMalformedDigest() {
        val assets = release.assets.map { it.copy(digest = "md5:abc") }
        assertNull(pickUpdate(release.copy(assets = assets), AppVersion(0, 1, 0))!!.sha256)
    }

    @Test fun highlightsHandleEmptyBody() = assertEquals(emptyList<String>(), releaseHighlights(null))
}
