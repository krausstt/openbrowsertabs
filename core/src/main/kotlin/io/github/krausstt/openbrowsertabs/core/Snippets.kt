package io.github.krausstt.openbrowsertabs.core

/** Text snippets for notifications and list previews. */
object Snippets {

    /**
     * Lead of a text: whole sentences while they fit into [maxChars];
     * otherwise a word-boundary cut with ellipsis. Null for blank input.
     */
    fun lead(text: String?, maxChars: Int = 200): String? {
        val clean = text?.replace(Regex("\\s+"), " ")?.trim()
        if (clean.isNullOrBlank()) return null
        if (clean.length <= maxChars) return clean

        val sentenceEnd = Regex("(?<=[.!?])\\s+")
        val out = StringBuilder()
        for (sentence in clean.split(sentenceEnd)) {
            if (out.isEmpty() && sentence.length > maxChars) break
            if (out.length + sentence.length + 1 > maxChars) break
            if (out.isNotEmpty()) out.append(' ')
            out.append(sentence)
        }
        if (out.isNotEmpty()) return out.toString()

        // first sentence alone is too long: cut at a word boundary
        val cut = clean.take(maxChars)
        val lastSpace = cut.lastIndexOf(' ')
        return (if (lastSpace > maxChars / 2) cut.take(lastSpace) else cut).trimEnd() + " …"
    }

    private val PROMO_HINTS = Regex(
        "affiliate|sponsor(ed)?|commission|discount code|promo code|use code|coupon|" +
            "paid partnership|partnered with",
        RegexOption.IGNORE_CASE,
    )
    private val URL_PATTERN = Regex("https?://")

    /**
     * Heuristic for sponsor/affiliate-heavy text (common in YouTube video
     * descriptions) that makes a poor "catchy one-liner" even though it is
     * real, non-boilerplate content.
     */
    fun isPromotional(text: String): Boolean =
        PROMO_HINTS.containsMatchIn(text) || URL_PATTERN.findAll(text).count() >= 2
}
