package io.github.krausstt.openbrowsertabs.core

/**
 * Deterministic visual identity for a link, derived from its host.
 *
 * The list is dominated by (thumbnail, title, link, metadata) tuples, and a
 * grid of identical grey rectangles is unscannable. Real page images would
 * mean an image-loading dependency plus a network request to a third party
 * every time the list is drawn — which leaks the reading list to those hosts.
 * A monogram derived from the host is instant, offline, private and stable:
 * the same site always looks the same, so the eye learns it.
 */
object Monogram {

    /** Validated categorical palette, also used by the web demo. */
    val PALETTE = listOf(
        0xFF2A78D6, // blue
        0xFF1BAF7A, // aqua
        0xFFEDA100, // amber
        0xFF008300, // green
        0xFF4A3AA7, // violet
        0xFFE34948, // red
        0xFFE87BA4, // magenta
        0xFFEB6834, // orange
    )

    private val KNOWN = mapOf(
        "github.com" to "GH", "huggingface.co" to "HF", "youtube.com" to "YT",
        "youtu.be" to "YT", "reddit.com" to "RD", "arxiv.org" to "AR",
        "news.ycombinator.com" to "HN", "stackoverflow.com" to "SO",
        "google.com" to "GO", "amazon.de" to "AZ", "amazon.com" to "AZ",
    )

    /** Two-character label: curated for well-known hosts, derived otherwise. */
    fun initials(host: String): String {
        val clean = host.removePrefix("www.").lowercase()
        KNOWN[clean]?.let { return it }

        val labels = clean.split('.').filter { it.isNotBlank() }
        // drop the public suffix: "home-assistant.io" -> "home-assistant"
        val base = when {
            labels.size >= 3 && labels[labels.size - 2].length <= 3 ->
                labels[labels.size - 3] // co.uk style
            labels.size >= 2 -> labels[labels.size - 2]
            else -> labels.firstOrNull() ?: return "?"
        }
        val parts = base.split('-', '_').filter { it.isNotBlank() }
        return when {
            parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
            base.length >= 2 -> base.take(2).uppercase()
            base.isNotEmpty() -> base.uppercase()
            else -> "?"
        }
    }

    /** Stable palette index — the same host keeps its colour across restarts. */
    fun colorIndex(host: String): Int {
        val clean = host.removePrefix("www.").lowercase()
        var hash = 0
        for (ch in clean) hash = (hash * 31 + ch.code) and 0x7FFFFFFF
        return hash % PALETTE.size
    }

    fun color(host: String): Long = PALETTE[colorIndex(host)]
}
