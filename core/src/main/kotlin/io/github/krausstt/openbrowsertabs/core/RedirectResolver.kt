package io.github.krausstt.openbrowsertabs.core

import java.net.HttpURLConnection
import java.net.URI

/**
 * Resolves shortener/redirect links (share.google & friends) to their real
 * target by following Location headers. Unlike AMP or google.com/url
 * wrappers, the target is NOT contained in the URL — resolution requires a
 * network round trip, so callers must run this off the main thread and
 * treat failure (offline, interstitial) as "keep the short URL, retry
 * later via the enrichment queue".
 */
object RedirectResolver {

    /** Hosts whose URLs are opaque redirects and worth resolving. */
    private val SHORTENER_HOSTS = setOf(
        "share.google", "goo.gl", "g.co", "bit.ly", "t.co", "tinyurl.com",
        "amzn.to", "amzn.eu", "buff.ly", "ow.ly", "is.gd", "rb.gy",
        "shorturl.at", "lnkd.in", "fb.me", "dlvr.it", "t.ly", "rebrand.ly",
    )

    fun needsResolution(url: String): Boolean {
        val host = urlsplit(url).netloc.lowercase().removePrefix("www.")
        return host in SHORTENER_HOSTS
    }

    /**
     * One HTTP hop: returns (statusCode, locationHeader). Injectable for tests;
     * the default implementation does a real GET without following redirects.
     */
    fun interface Fetcher {
        fun fetch(url: String): Pair<Int, String?>
    }

    val defaultFetcher = Fetcher { url ->
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) OpenBrowserTabs/0.1")
            conn.responseCode to conn.getHeaderField("Location")
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Follow redirects until we leave the shortener hosts.
     * @return the resolved URL, or null if resolution failed (no redirect,
     *         network error, or still on a shortener host after [maxHops]).
     */
    fun resolve(url: String, fetcher: Fetcher = defaultFetcher, maxHops: Int = 5): String? {
        var current = url
        repeat(maxHops) {
            val (code, location) = try {
                fetcher.fetch(current)
            } catch (_: Exception) {
                return null
            }
            if (code !in 300..399 || location.isNullOrBlank()) {
                // landed on a page: success only if we already left the shorteners
                return current.takeIf { it != url && !needsResolution(it) }
            }
            current = if (location.startsWith("http")) {
                location
            } else {
                // resolve relative Location against the current URL
                try {
                    URI(current).resolve(location).toString()
                } catch (_: Exception) {
                    return null
                }
            }
            if (!needsResolution(current)) return current
        }
        return current.takeIf { !needsResolution(it) }
    }
}
