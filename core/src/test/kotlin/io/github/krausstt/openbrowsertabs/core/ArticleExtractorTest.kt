package io.github.krausstt.openbrowsertabs.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArticleExtractorTest {

    private val articleHtml = """
        <html><head>
          <title>Fallback Title | Site</title>
          <meta property="og:title" content="Ein Testartikel über ESP32-Audio">
          <meta property="og:description" content="Kurzbeschreibung des Artikels.">
          <meta property="og:site_name" content="Beispiel-Blog">
          <meta property="article:published_time" content="2026-07-01T10:00:00Z">
        </head><body>
          <nav><p>Navigation Link Eins Zwei Drei Vier Fünf Sechs Sieben Acht</p></nav>
          <article>
            <p>Der erste Absatz des eigentlichen Artikels ist lang genug, um als Inhalt erkannt zu werden.</p>
            <p>kurz</p>
            <p>Der zweite Absatz enthält weitere Details und ist ebenfalls deutlich länger als vierzig Zeichen.</p>
          </article>
          <footer><p>Impressum Datenschutz Kontakt und noch mehr Boilerplate Text hier</p></footer>
        </body></html>
    """.trimIndent()

    @Test
    fun `prefers opengraph metadata over title tag`() {
        val a = ArticleExtractor.extract(articleHtml, "https://example.org/post")
        assertEquals("Ein Testartikel über ESP32-Audio", a.title)
        assertEquals("Kurzbeschreibung des Artikels.", a.description)
        assertEquals("Beispiel-Blog", a.siteName)
        assertEquals("2026-07-01T10:00:00Z", a.publishedAt)
    }

    @Test
    fun `extracts article paragraphs and drops nav footer and crumbs`() {
        val a = ArticleExtractor.extract(articleHtml, "https://example.org/post")
        val text = a.text.orEmpty()
        assertTrue(text.contains("erste Absatz"))
        assertTrue(text.contains("zweite Absatz"))
        assertTrue(!text.contains("Navigation"))
        assertTrue(!text.contains("Impressum"))
        assertTrue(!text.contains("kurz\n"))
    }

    @Test
    fun `falls back to title tag when no metadata present`() {
        val a = ArticleExtractor.extract(
            "<html><head><title>Nur Titel</title></head><body><p>Hallo Welt, dieser Text hat mehr als vierzig Zeichen Länge.</p></body></html>",
            "https://example.org/",
        )
        assertEquals("Nur Titel", a.title)
        assertNull(a.description)
        assertTrue(a.text.orEmpty().contains("Hallo Welt"))
    }
}
