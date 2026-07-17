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
}
