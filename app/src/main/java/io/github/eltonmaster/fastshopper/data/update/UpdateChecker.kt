package io.github.eltonmaster.fastshopper.data.update

import io.github.eltonmaster.fastshopper.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Looks for a newer signed APK on the repository's GitHub Releases and downloads it. */
class UpdateChecker(
    private val latestReleaseUrl: String = LATEST_RELEASE_URL,
    private val currentVersion: AppVersion? = AppVersion.parse(BuildConfig.VERSION_NAME),
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "FastShopper/${BuildConfig.VERSION_NAME}")
                    .build()
            )
        }
        .build()

    /** The newer release, null when up to date, or a failure when GitHub can't be reached. */
    suspend fun check(): Result<AvailableUpdate?> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(latestReleaseUrl)
                .header("Accept", "application/vnd.github+json")
                .build()
            http.newCall(request).execute().use { response ->
                // 404 means the repository has no published release yet.
                if (response.code == 404) return@runCatching null
                if (!response.isSuccessful) throw IOException("GitHub answered HTTP ${response.code}")
                val release = json.decodeFromString(GithubRelease.serializer(), response.body!!.string())
                pickUpdate(release, currentVersion)
            }
        }
    }

    /**
     * Downloads the APK into [dir] (emptied first) and checks it against the digest GitHub
     * publishes. [onProgress] gets 0..1, or null while the size is unknown.
     */
    suspend fun download(
        update: AvailableUpdate,
        dir: File,
        onProgress: (Float?) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        dir.deleteRecursively()
        if (!dir.mkdirs() && !dir.isDirectory) throw IOException("Can't create $dir")
        val fileName = "fastshopper-${update.version}.apk".replace(Regex("[^A-Za-z0-9._-]"), "_")
        val part = File(dir, "$fileName.part")
        val target = File(dir, fileName)

        val request = Request.Builder().url(update.apkUrl).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download failed with HTTP ${response.code}")
            val body = response.body ?: throw IOException("Download was empty")
            val total = body.contentLength().takeIf { it > 0 } ?: update.sizeBytes.takeIf { it > 0 }
            val digest = MessageDigest.getInstance("SHA-256")
            var read = 0L
            var lastReported = -1
            onProgress(if (total != null) 0f else null)
            body.byteStream().use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        read += n
                        if (total != null) {
                            val percent = (read * 100 / total).toInt().coerceIn(0, 100)
                            if (percent != lastReported) {
                                lastReported = percent
                                onProgress(percent / 100f)
                            }
                        }
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (update.sha256 != null && actual != update.sha256) {
                part.delete()
                throw IOException("The download was damaged. Please try again.")
            }
        }
        if (!part.renameTo(target)) throw IOException("Can't save the download")
        target
    }

    companion object {
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/Eltonmaster/mealie_fastshopper/releases/latest"
        const val RELEASES_PAGE_URL = "https://github.com/Eltonmaster/mealie_fastshopper/releases"
    }
}
