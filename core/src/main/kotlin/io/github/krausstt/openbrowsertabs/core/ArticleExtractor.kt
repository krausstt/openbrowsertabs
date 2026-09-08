package io.github.krausstt.openbrowsertabs.core

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** Extracted page content used for enrichment. */
data class Article(
    val title: String?,
    val description: String?,
    val text: String?,
    val siteName: String?,
    val publishedAt: String?,   // ISO string as found in metadata, unparsed
    /**
     * og:image, stored but not rendered yet: showing it means an image
     * loading dependency and a third-party request per list row, which
     * would leak the reading list to those hosts. Captured now so the
     * decision stays open without a re-crawl.
     */
    val imageUrl: String? = null,
)

/**
 * Lightweight readability: metadata first (OpenGraph/meta), then a
 * largest-text-block heuristic for the article body. Good enough for
 * summaries/embeddings; a full Readability.js pass in a WebView can replace
 * this later for JS-heavy pages.
 */
object ArticleExtractor {

    private const val MAX_TEXT_CHARS = 100_000
    private const val MAX_DESCRIPTION_CHARS = 500

    // containers that typically hold boilerplate, not article text
    private val NOISE_TAGS = listOf(
        "nav", "header", "footer", "aside", "form", "script", "style",
        "noscript", "iframe", "figure", "button", "svg",
    )

    fun extract(html: String, baseUrl: String): Article {
        val doc = Jsoup.parse(html, baseUrl)

        val title = firstNonBlank(
            meta(doc, "og:title"),
            meta(doc, "twitter:title"),
            doc.title(),
        )
        val description = firstNonBlank(
            meta(doc, "og:description"),
            meta(doc, "twitter:description"),
            metaByName(doc, "description"),
        )?.take(MAX_DESCRIPTION_CHARS)
        val siteName = firstNonBlank(meta(doc, "og:site_name"))
        val publishedAt = firstNonBlank(
            meta(doc, "article:published_time"),
            metaByName(doc, "date"),
            doc.selectFirst("time[datetime]")?.attr("datetime"),
        )

        val imageUrl = firstNonBlank(
            meta(doc, "og:image"),
            meta(doc, "twitter:image"),
        )?.let { absolutize(doc, it) }

        NOISE_TAGS.forEach { tag -> doc.select(tag).remove() }
        val text = bestTextBlock(doc)?.take(MAX_TEXT_CHARS)

        return Article(title, description, text, siteName, publishedAt, imageUrl)
    }

    /** Resolve a possibly relative image URL against the page's base URI. */
    private fun absolutize(doc: Document, url: String): String? {
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        val base = doc.baseUri().ifBlank { return null }
        return runCatching { java.net.URI(base).resolve(url).toString() }.getOrNull()
    }

    private fun meta(doc: Document, property: String): String? =
        doc.selectFirst("meta[property=$property]")?.attr("content")

    private fun metaByName(doc: Document, name: String): String? =
        doc.selectFirst("meta[name=$name]")?.attr("content")

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }?.trim()

    /**
     * Pick the container with the most paragraph text: prefer semantic
     * containers (article/main), otherwise score all block candidates.
     */
    private fun bestTextBlock(doc: Document): String? {
        val semantic = doc.selectFirst("article") ?: doc.selectFirst("main")
        val candidate = semantic ?: doc.select("div, section")
            .maxByOrNull { el -> el.select("> p").sumOf { it.text().length } }
        // jsoup always synthesises a body element, so this cannot be null
        val container = candidate ?: doc.body()
        val paragraphs = container.select("p")
            .map { it.text().trim() }
            .filter { it.length > 40 } // drop crumbs, captions, cookie hints
        if (paragraphs.isEmpty()) {
            return container.text().trim().ifBlank { null }
        }
        return paragraphs.joinToString("\n\n")
    }
}
