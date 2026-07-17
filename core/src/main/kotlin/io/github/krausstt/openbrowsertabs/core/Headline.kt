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
