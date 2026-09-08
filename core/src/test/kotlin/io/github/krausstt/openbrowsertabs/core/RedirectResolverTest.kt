package io.github.krausstt.openbrowsertabs.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RedirectResolverTest {

    @Test
    fun `detects shortener hosts`() {
        assertTrue(RedirectResolver.needsResolution("https://share.google/AbCdEf123"))
        assertTrue(RedirectResolver.needsResolution("https://goo.gl/xyz"))
        assertFalse(RedirectResolver.needsResolution("https://example.com/share.google"))
        assertFalse(RedirectResolver.needsResolution("https://heise.de/news/article.html"))
    }

    private fun fakeFetcher(hops: Map<String, Pair<Int, String?>>) =
        RedirectResolver.Fetcher { url -> hops[url] ?: (404 to null) }

    @Test
    fun `follows redirect chain to the real target`() {
        val fetcher = fakeFetcher(
            mapOf(
                "https://share.google/abc" to (302 to "https://g.co/kgs/xyz"),
                "https://g.co/kgs/xyz" to (301 to "https://example.org/news/story?id=7"),
            ),
        )
        assertEquals(
            "https://example.org/news/story?id=7",
            RedirectResolver.resolve("https://share.google/abc", fetcher),
        )
    }

    @Test
    fun `resolves relative Location headers`() {
        val fetcher = fakeFetcher(
            mapOf(
                "https://share.google/abc" to (302 to "/interstitial/def"),
                "https://share.google/interstitial/def" to (302 to "https://example.net/a"),
            ),
        )
        assertEquals("https://example.net/a", RedirectResolver.resolve("https://share.google/abc", fetcher))
    }

    @Test
    fun `gives up when the shortener serves a page instead of a redirect`() {
        val fetcher = fakeFetcher(mapOf("https://share.google/abc" to (200 to null)))
        assertNull(RedirectResolver.resolve("https://share.google/abc", fetcher))
    }

    @Test
    fun `gives up on network errors`() {
        val fetcher = RedirectResolver.Fetcher { throw java.io.IOException("offline") }
        assertNull(RedirectResolver.resolve("https://share.google/abc", fetcher))
    }

    @Test
    fun `gives up after max hops inside shortener domains`() {
        val fetcher = fakeFetcher(
            mapOf(
                "https://share.google/a" to (302 to "https://goo.gl/b"),
                "https://goo.gl/b" to (302 to "https://share.google/a"),
            ),
        )
        assertNull(RedirectResolver.resolve("https://share.google/a", fetcher))
    }
}
