package io.github.krausstt.openbrowsertabs.core

/**
 * Short, "crispy" display titles. Repo hosts (HuggingFace/GitHub) redundantly
 * repeat the site name in <title> (e.g. "org/repo · Hugging Face") — for
 * those we derive the headline straight from the URL path instead of the
 * page title. For everything else this can only do mechanical cleanup
 * (strip a trailing site-name suffix, cap length); compressing an arbitrary
 * long article title to a truly punchy phrase needs an LLM summarization
 * step, which is a later enrichment tier, not this heuristic.
 */
object Headline {

    private val REPO_HOSTS = setOf("huggingface.co", "github.com", "codeberg.org", "gitlab.com")
    private val SITE_SUFFIX = Regex("\\s*[·|]\\s*[^·|]{2,40}$|\\s+-\\s+[^-]{2,40}$")
    private const val MAX_WORDS = 8
    private const val MAX_CHARS = 60

    fun shortHeadline(rawTitle: String?, url: String): String {
        val s = urlsplit(url)
        if (s.netloc in REPO_HOSTS) {
            repoNameFrom(s.path)?.let { return it }
        }
        val title = rawTitle?.trim().takeUnless { it.isNullOrBlank() } ?: return s.netloc
        return truncate(stripSiteSuffix(title))
    }

    /**
     * Title recovered from the URL slug — the rescue path for pages whose
     * <title> is just the publisher name because a consent wall replaced the
     * article (e.g. golem.de serving "Golem" for every article).
     *
     * "…/news/qwen-perfekt-unperfekte-sprachsynthese-2608-211230.html"
     *   -> "Qwen Perfekt Unperfekte Sprachsynthese"
     */
    fun fromSlug(url: String): String? {
        val s = urlsplit(url)
        val last = s.path.trim('/').split('/').lastOrNull { it.isNotBlank() } ?: return null
        val stem = last.substringBeforeLast('.', last)
        val words = stem.split('-', '_', '+')
            .filter { it.isNotBlank() }
            // article ids and dates carry no meaning for a human headline
            .filterNot { it.all(Char::isDigit) }
            .filterNot { it.length <= 2 && it.any(Char::isDigit) }
        if (words.size < 2) return null
        return words.take(MAX_WORDS).joinToString(" ") { w ->
            if (w.any(Char::isDigit) || w.any(Char::isUpperCase)) w
            else w.replaceFirstChar { it.uppercase() }
        }
    }

    /**
     * Best available display title. Prefers the page title, but falls back to
     * the slug when the title carries no article information — blank, or just
     * the publisher's own name.
     */
    fun best(rawTitle: String?, url: String, siteName: String? = null): String {
        val title = rawTitle?.trim()
        if (!title.isNullOrBlank() && !isPublisherOnly(title, url, siteName)) {
            return shortHeadline(title, url)
        }
        return fromSlug(url) ?: shortHeadline(title, url)
    }

    /** True when the title says only which site it is, not which page. */
    private fun isPublisherOnly(title: String, url: String, siteName: String?): Boolean {
        val t = title.lowercase().trim()
        if (t.split(Regex("\\s+")).size > 3) return false
        val host = urlsplit(url).netloc.removePrefix("www.").lowercase()
        val brand = host.split('.').firstOrNull().orEmpty()
        return t == brand ||
            t == host ||
            t == siteName?.lowercase()?.trim() ||
            t.removeSuffix(".de").removeSuffix(".com") == brand
    }

    private fun repoNameFrom(path: String): String? {
        val last = path.trim('/').split('/').lastOrNull { it.isNotBlank() } ?: return null
        return last.split('-', '_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { formatSegment(it) }
            .ifBlank { null }
    }

    /** Digit-bearing tokens (v1, 7b) and already-cased acronyms (ASR) pass
     *  through unchanged; plain lowercase words get capitalized. */
    private fun formatSegment(segment: String): String = when {
        segment.any { it.isDigit() } -> segment
        segment.all { !it.isLetter() || it.isLowerCase() } -> segment.replaceFirstChar { it.uppercase() }
        else -> segment
    }

    private fun stripSiteSuffix(title: String): String {
        val stripped = SITE_SUFFIX.replace(title, "").trim()
        return stripped.ifBlank { title }
    }

    private fun truncate(text: String): String {
        val words = text.split(Regex("\\s+"))
        val limited = words.take(MAX_WORDS).joinToString(" ")
        return if (limited.length <= MAX_CHARS) limited else limited.take(MAX_CHARS).trimEnd() + "…"
    }
}
