package io.github.eltonmaster.fastshopper.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object ServerUrl {
    /**
     * Turns user input like "mealie.example.de/" into "https://mealie.example.de".
     * Returns null when the input can't be a valid http(s) URL.
     */
    fun normalize(input: String): String? {
        var url = input.trim()
        if (url.isEmpty()) return null
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            url = "https://$url"
        }
        url = url.trimEnd('/')
        val parsed = "$url/".toHttpUrlOrNull() ?: return null
        if (parsed.host.isBlank()) return null
        return url
    }

    /** Retrofit needs a base URL ending in a slash. */
    fun asBaseUrl(normalized: String): String = "$normalized/"
}
