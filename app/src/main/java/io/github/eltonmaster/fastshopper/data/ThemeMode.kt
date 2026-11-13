package io.github.eltonmaster.fastshopper.data

/** Which colour scheme the app uses. [SYSTEM] follows the phone's dark mode setting. */
enum class ThemeMode(val key: String) {
    LIGHT("light"),
    DARK("dark"),
    SYSTEM("system");

    fun isDark(systemInDarkMode: Boolean): Boolean = when (this) {
        LIGHT -> false
        DARK -> true
        SYSTEM -> systemInDarkMode
    }

    companion object {
        /** Unknown or missing values fall back to following the system. */
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}
