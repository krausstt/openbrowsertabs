package io.github.krausstt.openbrowsertabs.core

/**
 * Tag-based grouping that works today, without embeddings and without the
 * cloud. The cloud batch will later replace the assignment with semantic
 * clusters, but the *shape* it produces is identical — same size cap, same
 * splitting rule — so the export paths downstream do not change.
 */
object Clustering {

    data class Item(val id: Long, val tags: List<String>, val category: String)
    data class Cluster(val label: String, val members: List<Long>)

    /** NotebookLM starts to lose citation quality well before 100 sources. */
    const val MAX_MEMBERS = 50
    private const val MIN_MEMBERS = 3
    private const val MISC = "Verschiedenes"

    /**
     * Assign every item to exactly one cluster.
     *
     * An item lands under its *rarest* tag: with `[llm_agents, hardware]` the
     * rarer of the two says more about this item than the common one. Groups
     * smaller than [MIN_MEMBERS] are folded into a misc bucket so the result
     * is not a hundred singletons, and groups above [MAX_MEMBERS] are split
     * into numbered parts.
     */
    fun byTag(
        items: List<Item>,
        labelFor: (String) -> String = { it },
        maxMembers: Int = MAX_MEMBERS,
    ): List<Cluster> {
        if (items.isEmpty()) return emptyList()

        val frequency = HashMap<String, Int>()
        items.forEach { item ->
            item.tags.filter { it != "untagged" }.distinct()
                .forEach { frequency[it] = (frequency[it] ?: 0) + 1 }
        }

        val grouped = LinkedHashMap<String, MutableList<Long>>()
        items.forEach { item ->
            val usable = item.tags.filter { it != "untagged" && frequency.containsKey(it) }
            val key = usable.minByOrNull { frequency[it] ?: Int.MAX_VALUE }
                ?: item.category.takeIf { it.isNotBlank() }
                ?: MISC
            grouped.getOrPut(key) { mutableListOf() }.add(item.id)
        }

        val misc = mutableListOf<Long>()
        val kept = LinkedHashMap<String, MutableList<Long>>()
        grouped.forEach { (key, members) ->
            if (members.size < MIN_MEMBERS) misc += members else kept[key] = members
        }

        val out = mutableListOf<Cluster>()
        kept.entries
            .sortedByDescending { it.value.size }
            .forEach { (key, members) ->
                val label = labelFor(key)
                members.chunked(maxMembers).forEachIndexed { index, chunk ->
                    val suffix = if (members.size > maxMembers) " (${index + 1})" else ""
                    out += Cluster(label + suffix, chunk)
                }
            }
        misc.chunked(maxMembers).forEachIndexed { index, chunk ->
            val suffix = if (misc.size > maxMembers) " (${index + 1})" else ""
            out += Cluster(MISC + suffix, chunk)
        }
        return out
    }
}
