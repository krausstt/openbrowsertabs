package io.github.krausstt.openbrowsertabs.core

/**
 * The one-tap vocabulary of the save moment.
 *
 * A reaction answers the only question the machine can never reconstruct
 * later: *why did you keep this?* Topics can be scraped, authors can be
 * parsed, publication dates are in the markup — intent is not. It is also
 * the cheapest thing to ask for, which is the point: one tap, no keyboard,
 * no decision about taxonomy.
 *
 * Deliberately **not** a topic list. Topics are what enrichment is for; a
 * second hand-maintained topic taxonomy would only compete with it. These
 * five are stances toward an entry, and they stay stable no matter whether
 * the page is a HuggingFace model card or a lasagne recipe.
 */
object Reactions {

    data class Reaction(
        val id: String,
        val emoji: String,
        /** Short enough to sit under the emoji on a phone. */
        val label: String,
        /** What tapping it says, for the detail view and the export. */
        val meaning: String,
    )

    val ALL: List<Reaction> = listOf(
        Reaction("idea", "💡", "Idee", "hat bei mir eine eigene Idee ausgelöst"),
        Reaction("understand", "🤔", "Verstehen", "will ich richtig verstehen"),
        Reaction("build", "🛠️", "Bauen", "will ich nachbauen oder ausprobieren"),
        Reaction("reference", "🔖", "Merken", "Nachschlagewerk, muss auffindbar bleiben"),
        Reaction("now", "🔥", "Bald lesen", "soll nicht liegen bleiben"),
    )

    private val byId = ALL.associateBy { it.id }

    fun of(id: String?): Reaction? = id?.let { byId[it] }

    fun emojiOf(id: String?): String = of(id)?.emoji ?: ""

    /** Valid ids only — anything else is dropped rather than stored. */
    fun sanitize(id: String?): String? = id?.takeIf { byId.containsKey(it) }

    /**
     * A hand-typed note is context, not a summary: one or two words are the
     * expected case. Normalised so "  KI   Agents " and "KI Agents" are the
     * same note, and capped so a mis-paste cannot become an article body.
     */
    const val MAX_NOTE_CHARS = 280

    fun normalizeNote(raw: String?): String? =
        raw?.trim()?.replace(Regex("\\s+"), " ")?.take(MAX_NOTE_CHARS)?.takeIf { it.isNotEmpty() }
}
