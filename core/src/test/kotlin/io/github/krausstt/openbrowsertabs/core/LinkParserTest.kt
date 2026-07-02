package io.github.krausstt.openbrowsertabs.core

import kotlin.test.Test
import kotlin.test.assertEquals

class LinkParserTest {

    @Test
    fun `extracts urls from share-sheet style text`() {
        val text = "Schau mal: https://www.heise.de/news/some-article-123.html, gefunden via Chrome."
        assertEquals(listOf("https://www.heise.de/news/some-article-123.html"), LinkParser.extractUrls(text))
    }

    @Test
    fun `parses multi-line pasted list and dedupes by canonical url`() {
        val text = """
            https://www.example.com/a?utm_source=newsletter
            https://example.com/a
            https://github.com/someorg/somerepo
        """.trimIndent()
        val links = LinkParser.parse(text)
        assertEquals(2, links.size)
        assertEquals("https://example.com/a", links[0].canonicalUrl)
        assertEquals("example.com", links[0].host)
        assertEquals("repo", links[1].category)
    }

    @Test
    fun `text without urls yields empty list`() {
        assertEquals(emptyList(), LinkParser.parse("nur Text, kein Link"))
    }

    @Test
    fun `parses numbered export list with duplicates and tracking params`() {
        val text = """
            URLs 02-07-2026
            1.  https://www.example.com/a?utm_source=newsletter
            2.  https://example.com/a
            3.  https://www.heise.de/news/some-article-123.html
            4.  https://github.com/someorg/somerepo
            5.  https://github.com/someorg/somerepo
        """.trimIndent()
        val links = LinkParser.parse(text)
        // 1+2 collapse after normalization, 4+5 are exact duplicates
        assertEquals(3, links.size)
        assertEquals(
            listOf(
                "https://example.com/a",
                "https://heise.de/news/some-article-123.html",
                "https://github.com/someorg/somerepo",
            ),
            links.map { it.canonicalUrl },
        )
    }
}
