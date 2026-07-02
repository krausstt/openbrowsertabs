package io.github.krausstt.openbrowsertabs.core

/** A shared/imported link after normalization and heuristic enrichment. */
data class ParsedLink(
    val canonicalUrl: String,
    val originalUrl: String,
    val host: String,
    val category: String,
    val topics: List<String>,
    val label: String?,
)

object LinkParser {

    private val URL_RE = Regex("https?://\\S+")

    /** All URLs contained in a free-text blob (share sheet text, pasted list). */
    fun extractUrls(text: String): List<String> =
        URL_RE.findAll(text).map { it.value.trimEnd('.', ',', ';') }.toList()

    /** Parse, normalize, and enrich every URL in [text]; deduped by canonical URL. */
    fun parse(text: String): List<ParsedLink> =
        extractUrls(text)
            .map { original ->
                val canonical = UrlNormalizer.normalize(original)
                ParsedLink(
                    canonicalUrl = canonical,
                    originalUrl = original,
                    host = urlsplit(canonical).netloc,
                    category = Categorizer.categorize(canonical),
                    topics = Categorizer.topics(canonical),
                    label = Categorizer.searchLabel(canonical),
                )
            }
            .distinctBy { it.canonicalUrl }
}
