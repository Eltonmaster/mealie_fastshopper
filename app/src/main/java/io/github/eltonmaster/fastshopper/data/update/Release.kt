package io.github.eltonmaster.fastshopper.data.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The parts of GitHub's release JSON that the update check needs. */
@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String = "",
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
data class GithubAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = 0,
    /** GitHub fills this with `sha256:<hex>` for uploaded assets. */
    val digest: String? = null,
)

data class AvailableUpdate(
    val version: AppVersion,
    val apkUrl: String,
    val apkName: String,
    val sizeBytes: Long,
    val sha256: String?,
    val releaseUrl: String,
    val highlights: List<String>,
)

/**
 * Returns the update offered by [release], or null when it is not newer than [current],
 * is a draft or pre-release, or has no APK attached.
 */
fun pickUpdate(release: GithubRelease, current: AppVersion?): AvailableUpdate? {
    if (release.draft || release.prerelease) return null
    val version = AppVersion.parse(release.tagName) ?: return null
    if (current != null && version <= current) return null
    val apk = release.assets
        .filter { it.name.endsWith(".apk", ignoreCase = true) }
        .minByOrNull { it.name.length } // fastshopper-x.y.z.apk over any longer variant
        ?: return null
    return AvailableUpdate(
        version = version,
        apkUrl = apk.downloadUrl,
        apkName = apk.name,
        sizeBytes = apk.size,
        sha256 = apk.digest
            ?.takeIf { it.startsWith("sha256:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.lowercase()
            ?.takeIf { it.length == 64 },
        releaseUrl = release.htmlUrl,
        highlights = releaseHighlights(release.body),
    )
}

/**
 * Turns GitHub's generated release notes into short bullet lines:
 * `* Add item editing by @someone in https://…/pull/7` becomes `Add item editing`.
 */
fun releaseHighlights(body: String?, max: Int = 6): List<String> =
    body.orEmpty()
        .lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("* ") || it.startsWith("- ") }
        .map { it.drop(2).substringBefore(" by @").substringBefore(" in https://").trim() }
        .filter { it.isNotEmpty() && !it.startsWith("@") }
        .distinct()
        .take(max)
        .toList()
