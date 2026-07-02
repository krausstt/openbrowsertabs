package io.github.krausstt.openbrowsertabs.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cross-validation against the Python reference pipeline: every expected
 * value below was produced by pipeline/tabs_pipeline.py `normalize()` on the
 * same (synthetic) input. Keep both implementations in lockstep.
 */
class UrlNormalizerTest {

    private fun check(input: String, expected: String) =
        assertEquals(expected, UrlNormalizer.normalize(input), "input: $input")

    @Test
    fun `strips tracking params, lowercases host, drops www and trailing slash`() =
        check(
            "http://www.Example.COM/Some/Path/?utm_source=x&utm_medium=y&id=42",
            "https://example.com/Some/Path?id=42",
        )

    @Test
    fun `unwraps ampproject cache via ampshare fragment param`() =
        check(
            "https://www-example-org.cdn.ampproject.org/v/s/www.example.org/news/story.amp" +
                "?amp_gsa=1&usqp=x#amp_tf=a&ampshare=https%3A%2F%2Fwww.example.org%2Fnews%2Fstory",
            "https://example.org/news/story",
        )

    @Test
    fun `unwraps ampproject cache via v-s path fallback`() =
        check(
            "https://www-foo-io.cdn.ampproject.org/v/s/www.foo.io/post/abc.amp?amp_js_v=a9",
            "https://foo.io/post/abc",
        )

    @Test
    fun `unwraps google amp-s wrapper`() =
        check(
            "https://www.google.com/amp/s/www.example.net/2026/01/article/",
            "https://example.net/2026/01/article",
        )

    @Test
    fun `youtube keeps only the v param`() =
        check(
            "https://www.youtube.com/watch?v=abc123&si=trackme&t=42s",
            "https://youtube.com/watch?v=abc123",
        )

    @Test
    fun `youtu-be short link drops si tracking param`() =
        check("https://youtu.be/abc123?si=xyz", "https://youtu.be/abc123")

    @Test
    fun `exact-name tracking params are stripped but size survives`() =
        check(
            "https://shop.example.de/product?size=10&sh=1&gclid=g123&PROVID=99",
            "https://shop.example.de/product?size=10",
        )

    @Test
    fun `google search query is re-encoded stably`() =
        check(
            "https://www.google.com/search?q=esp32+audio+board&client=ms-android&oq=esp32",
            "https://google.com/search?q=esp32+audio+board&client=ms-android&oq=esp32",
        )

    @Test
    fun `multiple trailing slashes collapse`() =
        check("https://example.com/a/b///", "https://example.com/a/b")

    @Test
    fun `already-canonical urls pass through unchanged`() =
        check("https://github.com/someorg/somerepo", "https://github.com/someorg/somerepo")

    @Test
    fun `path case is preserved while host is lowercased`() =
        check("https://Medium.COM/@author/some-post-1a2b3c", "https://medium.com/@author/some-post-1a2b3c")

    @Test
    fun `bare host normalizes to root path`() =
        check("https://www.example.com", "https://example.com/")

    @Test
    fun `unwraps google url click-tracking wrapper with encoded target`() =
        check(
            "https://www.google.com/url?q=https%3A%2F%2Fwww.example.org%2Fnews%2Fstory%3Fid%3D7&sa=D&source=editors",
            "https://example.org/news/story?id=7",
        )

    @Test
    fun `unwraps google url click-tracking wrapper with plain target`() =
        check(
            "https://www.google.com/url?q=https://example.net/a/b&usg=xyz",
            "https://example.net/a/b",
        )
}
