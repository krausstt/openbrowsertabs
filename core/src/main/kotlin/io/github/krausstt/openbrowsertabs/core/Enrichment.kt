package io.github.krausstt.openbrowsertabs.core

/** Fetch + extract for one link; pure JVM, callers handle threading/pacing. */
object Enrichment {

    sealed class Outcome {
        data class Enriched(val finalUrl: String, val article: Article) : Outcome()
        data class Unfetchable(val reason: String) : Outcome()
        /** Retry later (offline, timeout, 5xx, 429). */
        data class Transient(val reason: String) : Outcome()
    }

    fun enrich(url: String): Outcome = when (val f = PageFetcher.fetch(url)) {
        is FetchResult.Success ->
            Outcome.Enriched(f.finalUrl, ArticleExtractor.extract(f.html, f.finalUrl))
        is FetchResult.Unfetchable -> Outcome.Unfetchable(f.reason)
        is FetchResult.TransientError -> Outcome.Transient(f.reason)
    }
}
