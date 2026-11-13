package io.github.eltonmaster.fastshopper.data

import java.time.Duration

/**
 * How long checked items stay before they're deleted, chosen in Settings. A phone setting: each
 * phone deletes on the server, so with several phones the shortest delay wins. [NEVER] keeps
 * checked items until someone deletes them.
 */
enum class AutoClearDelay(val key: String, val duration: Duration?, val label: String) {
    HOUR_1("1h", Duration.ofHours(1), "1 hour"),
    HOURS_6("6h", Duration.ofHours(6), "6 hours"),
    HOURS_12("12h", Duration.ofHours(12), "12 hours"),
    DAY_1("1d", Duration.ofDays(1), "1 day"),
    DAYS_3("3d", Duration.ofDays(3), "3 days"),
    NEVER("never", null, "Never");

    /** The line shown above the checked items on a list. */
    val hint: String
        get() = if (duration == null) {
            "Checked items stay until you delete them."
        } else {
            "Checked items are removed automatically after $label."
        }

    companion object {
        val DEFAULT = HOURS_12

        /** Unknown or missing values fall back to [DEFAULT]. */
        fun fromKey(key: String?): AutoClearDelay = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
