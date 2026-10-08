package com.flaviocecca.ondalunga.update

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(val version: String, val apkUrl: String, val bytes: Long)

/** Talks to the project's GitHub releases. Plain JVM code: no Android involved. */
object Releases {
    private const val REPO = "carellice/ondalunga"
    private const val LATEST = "https://api.github.com/repos/$REPO/releases/latest"

    /** Only ever download from the project's own release page. */
    private const val DOWNLOAD_PREFIX = "https://github.com/$REPO/releases/download/"

    /** True if [candidate] (e.g. "1.0.10") is a later version than [installed] (e.g. "1.0.9"). */
    fun isNewer(candidate: String, installed: String): Boolean {
        val a = candidate.split('.').map { it.toIntOrNull() ?: 0 }
        val b = installed.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val difference = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (difference != 0) return difference > 0
        }
        return false
    }

    /** Reads the GitHub "latest release" answer; null if it carries no usable APK. */
    fun parse(json: String): ReleaseInfo? {
        val release = JSONObject(json)
        val version = release.optString("tag_name").removePrefix("v")
        val assets = release.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val url = asset.optString("browser_download_url")
            if (asset.optString("name").endsWith(".apk") && url.startsWith(DOWNLOAD_PREFIX)) {
                return ReleaseInfo(version, url, asset.optLong("size"))
            }
        }
        return null
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "OndaLunga")
        }

    /** The newest published release, or null if there is none yet. */
    fun fetchLatest(): ReleaseInfo? {
        val connection = open(LATEST).apply { setRequestProperty("Accept", "application/vnd.github+json") }
        try {
            return when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> parse(connection.inputStream.bufferedReader().use { it.readText() })
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> throw IOException("GitHub ha risposto ${connection.responseCode}")
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Saves the release's APK to [target], reporting progress as 0..1. */
    fun download(release: ReleaseInfo, target: File, onProgress: (Float) -> Unit) {
        if (!release.apkUrl.startsWith(DOWNLOAD_PREFIX)) throw IOException("Indirizzo di download inatteso")
        val connection = open(release.apkUrl)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Download non riuscito: ${connection.responseCode}")
            }
            val total = release.bytes.takeIf { it > 0 } ?: connection.contentLengthLong
            val partial = File(target.parentFile, target.name + ".part")
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) onProgress((done.toFloat() / total).coerceAtMost(1f))
                    }
                    if (total > 0 && done != total) throw IOException("Download incompleto")
                }
            }
            if (!partial.renameTo(target)) throw IOException("Impossibile salvare l'aggiornamento")
        } finally {
            connection.disconnect()
        }
    }
}
