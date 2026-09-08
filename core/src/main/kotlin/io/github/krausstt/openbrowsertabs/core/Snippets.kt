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

    // Cookie/consent walls are served *instead of* the article, so their text
    // extracts cleanly and looks like real content. Two independent signals
    // are required so that genuine articles about privacy are not discarded.
    private val CONSENT_STRONG = Regex(
        "bis zu \\d+ (drittanbieter|partner)|wir und unsere partner|" +
            "berechtigtes interesse|einwilligung jederzeit widerrufen|" +
            "we and our partners|iab (tcf|europe)|" +
            "accept all cookies|alle akzeptieren|zustimmen und weiter",
        RegexOption.IGNORE_CASE,
    )
    private val CONSENT_WEAK = Regex(
        "cookies?|einwilligung|consent|datenschutz|personenbezogene daten|" +
            "verarbeitung|tracking|werbung|privacy policy|gerätemerkmale",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Heuristic for consent-wall text captured instead of the article.
     * Strong phrase alone is enough; otherwise several weak markers must
     * appear in a short text (long articles naturally mention these words).
     */
    fun isConsentWall(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        if (CONSENT_STRONG.containsMatchIn(text)) return true
        val hits = CONSENT_WEAK.findAll(text).map { it.value.lowercase() }.toSet().size
        return hits >= 3 && wordCount(text) < 200
    }

    /** Word count of extracted article text, used for the reading estimate. */
    fun wordCount(text: String?): Int =
        text?.trim()?.takeIf { it.isNotEmpty() }
            ?.split(Regex("\\s+"))?.size ?: 0

    /**
     * Reading time in whole minutes at 200 wpm, the common estimate for adult
     * silent reading of non-technical prose. Null when there is no body text
     * to measure — the UI shows the page type instead of a made-up number.
     */
    fun readingMinutes(wordCount: Int): Int? =
        if (wordCount < 30) null else maxOf(1, Math.round(wordCount / 200.0).toInt())
}
