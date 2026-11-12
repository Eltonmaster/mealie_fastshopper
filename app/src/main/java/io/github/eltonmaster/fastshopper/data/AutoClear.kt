package io.github.eltonmaster.fastshopper.data

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** Checked items are deleted this long after they were checked (unit-tested). */
object AutoClear {
    val AFTER: Duration = Duration.ofHours(12)

    /**
     * Mealie has no "checked at" field; a checked item's `updatedAt` is when it was last changed,
     * i.e. when it was checked. Mealie may send it with or without an offset; without one it's UTC.
     */
    fun parse(updatedAt: String?): Instant? {
        if (updatedAt.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(updatedAt).toInstant() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(updatedAt).toInstant(ZoneOffset.UTC) }.getOrNull()
    }

    fun isExpired(checked: Boolean, updatedAt: String?, now: Instant, after: Duration = AFTER): Boolean {
        if (!checked) return false
        val changed = parse(updatedAt) ?: return false
        return Duration.between(changed, now) >= after
    }
}
