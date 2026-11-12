package io.github.eltonmaster.fastshopper.data.update

/**
 * A version in the form the release workflow uses for tags: `v1.2.3` or `v1.2.3-beta.1`.
 * Pre-releases sort below the plain release of the same number.
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null,
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        compareValues(major, other.major).let { if (it != 0) return it }
        compareValues(minor, other.minor).let { if (it != 0) return it }
        compareValues(patch, other.patch).let { if (it != 0) return it }
        return when {
            preRelease == other.preRelease -> 0
            preRelease == null -> 1
            other.preRelease == null -> -1
            else -> preRelease.compareTo(other.preRelease)
        }
    }

    override fun toString(): String =
        "$major.$minor.$patch" + (preRelease?.let { "-$it" } ?: "")

    companion object {
        private val PATTERN = Regex("""^[vV]?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?$""")

        fun parse(raw: String?): AppVersion? {
            val match = PATTERN.matchEntire(raw?.trim().orEmpty()) ?: return null
            val (major, minor, patch, pre) = match.destructured
            return AppVersion(
                major = major.toIntOrNull() ?: return null,
                minor = minor.toIntOrNull() ?: return null,
                patch = patch.toIntOrNull() ?: return null,
                preRelease = pre.ifEmpty { null },
            )
        }
    }
}
