package io.github.krausstt.openbrowsertabs.core

/**
 * Turns a backlog into a stack you can finish.
 *
 * The inbox failed for one reason: it showed the truth. Several hundred
 * unsorted entries is an accurate number and a useless interface — the pile
 * is the thing that makes you close the app. So the pile is never the unit
 * of work here. [pick] cuts a handful out of it, and the handful is
 * finishable in under a minute.
 *
 * Order is not "oldest first". Oldest first is how a backlog punishes you:
 * it serves the entries you have already failed to care about. The ranking
 * below serves the ones where a single tap still buys something.
 */
object InboxBatch {

    data class Item(
        val id: Long,
        val host: String,
        val savedAt: Long,
        val sightings: Int,
        /** Already carries a reaction or a hand-typed note. */
        val hasContext: Boolean,
        /** Already carries any tag, auto or hand-made. */
        val hasTags: Boolean,
    )

    const val DEFAULT_SIZE = 7
    const val DEFAULT_MAX_PER_HOST = 2

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /**
     * @param maxPerHost keeps one noisy domain from filling the whole stack.
     *   Twelve YouTube tiles in a row read as one item and get skipped as one.
     */
    fun pick(
        items: List<Item>,
        size: Int = DEFAULT_SIZE,
        maxPerHost: Int = DEFAULT_MAX_PER_HOST,
        now: Long = System.currentTimeMillis(),
    ): List<Long> {
        if (size <= 0) return emptyList()
        val ranked = items
            .filter { !it.hasContext || !it.hasTags }
            .sortedWith(compareByDescending<Item> { score(it, now) }.thenByDescending { it.savedAt })

        val perHost = mutableMapOf<String, Int>()
        val out = mutableListOf<Long>()
        // first pass honours the host cap; a second pass fills the rest so a
        // stack of five is never served as a stack of two
        for (item in ranked) {
            if (out.size == size) break
            val used = perHost.getOrDefault(item.host, 0)
            if (used >= maxPerHost) continue
            perHost[item.host] = used + 1
            out += item.id
        }
        if (out.size < size) {
            val taken = out.toSet()
            for (item in ranked) {
                if (out.size == size) break
                if (item.id in taken) continue
                out += item.id
            }
        }
        return out
    }

    /**
     * Higher is served sooner.
     *
     * - **Reacted but untagged** wins outright: you already said this matters,
     *   so a tag here connects something you care about — the best return on
     *   one tap in the whole collection.
     * - **Seen more than once** beats seen once: re-encountering a link is an
     *   interest signal you gave without being asked.
     * - **Recency** decays over a fortnight rather than dropping off, because
     *   you can still say what a link from last week was about, and largely
     *   cannot for one from March.
     */
    internal fun score(item: Item, now: Long): Double {
        var s = 0.0
        if (item.hasContext && !item.hasTags) s += 100.0
        if (item.sightings > 1) s += 20.0 + minOf(item.sightings, 5)
        val ageDays = ((now - item.savedAt).coerceAtLeast(0)).toDouble() / DAY_MS
        s += 40.0 / (1.0 + ageDays / 14.0)
        return s
    }
}
