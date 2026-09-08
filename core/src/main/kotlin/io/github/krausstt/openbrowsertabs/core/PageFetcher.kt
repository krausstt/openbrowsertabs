package io.github.krausstt.openbrowsertabs.core

import java.net.HttpURLConnection
import java.net.URI

sealed class FetchResult {
    data class Success(val finalUrl: String, val html: String) : FetchResult()
    /** Reached the server but got no usable HTML (paywall/challenge/non-HTML). */
    data class Unfetchable(val statusCode: Int, val reason: String) : FetchResult()
    /** Transient problem (offline, timeout) — worth retrying later. */
    data class TransientError(val reason: String) : FetchResult()
}

/**
 * Plain-HTTP page fetch with redirects, size cap, and a real mobile UA.
 * First stage of the fetch cascade; a WebView fallback (JS rendering) can
 * be layered on top in the app for challenge/empty responses.
 */
object PageFetcher {

    private const val MAX_BYTES = 2_000_000
    private const val TIMEOUT_MS = 10_000
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    fun fetch(url: String, maxRedirects: Int = 8): FetchResult {
        var current = url
        repeat(maxRedirects) {
            val conn = try {
                (URI(current).toURL().openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
                    setRequestProperty("Accept-Language", "de-DE,de;q=0.9,en;q=0.7")
                }
            } catch (e: Exception) {
                return FetchResult.TransientError("connect: ${e.message}")
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location")
                        ?: return FetchResult.Unfetchable(code, "redirect without location")
                    current = URI(current).resolve(loc).toString()
                    return@repeat
                }
                if (code !in 200..299) {
                    return if (code in 500..599 || code == 429) {
                        FetchResult.TransientError("http $code")
                    } else {
                        FetchResult.Unfetchable(code, "http $code")
                    }
                }
                val type = conn.contentType.orEmpty()
                if (!type.contains("html", ignoreCase = true) && type.isNotEmpty()) {
                    return FetchResult.Unfetchable(code, "content-type: $type")
                }
                val charsetName = Regex("charset=([A-Za-z0-9_-]+)")
                    .find(type)?.groupValues?.get(1)?.uppercase()
                    ?.let { if (it == "UTF8") "UTF-8" else it } ?: "UTF-8"
                // manual capped read: InputStream.readNBytes needs Android 13+
                val buffer = java.io.ByteArrayOutputStream()
                conn.inputStream.use { input ->
                    val chunk = ByteArray(16_384)
                    while (buffer.size() < MAX_BYTES) {
                        val n = input.read(chunk)
                        if (n < 0) break
                        buffer.write(chunk, 0, minOf(n, MAX_BYTES - buffer.size()))
                    }
                }
                val html = String(buffer.toByteArray(), charset(charsetName))
                return FetchResult.Success(current, html)
            } catch (e: Exception) {
                return FetchResult.TransientError("read: ${e.message}")
            } finally {
                conn.disconnect()
            }
        }
        return FetchResult.Unfetchable(0, "too many redirects")
    }
}
