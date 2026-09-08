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

object LinkResolution {

    /**
     * If [link] is an opaque shortener (share.google etc.), resolve it over
     * the network and re-run normalization/categorization on the target.
     * Keeps the shared short URL as originalUrl for provenance. On failure
     * the short link is returned unchanged — its pending-enrichment state
     * lets a later background pass retry.
     *
     * Network I/O: callers must invoke this off the main thread.
     */
    fun resolveIfShortened(
        link: ParsedLink,
        fetcher: RedirectResolver.Fetcher = RedirectResolver.defaultFetcher,
    ): ParsedLink {
        if (!RedirectResolver.needsResolution(link.canonicalUrl)) return link
        val target = RedirectResolver.resolve(link.canonicalUrl, fetcher) ?: return link
        val resolved = LinkParser.parse(target).firstOrNull() ?: return link
        return resolved.copy(originalUrl = link.originalUrl)
    }
}
