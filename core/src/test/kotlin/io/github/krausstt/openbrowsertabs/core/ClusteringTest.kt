package io.github.krausstt.openbrowsertabs.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClusteringTest {

    private fun item(id: Long, vararg tags: String, category: String = "article") =
        Clustering.Item(id, tags.toList(), category)

    @Test
    fun `items group under their rarest tag`() {
        // llm_agents is common, audio_music is rare — the rare one is the
        // one that actually describes this item
        val items = listOf(
            item(1, "llm_agents", "audio_music"),
            item(2, "llm_agents", "audio_music"),
            item(3, "llm_agents", "audio_music"),
            item(4, "llm_agents"), item(5, "llm_agents"), item(6, "llm_agents"),
            item(7, "llm_agents"), item(8, "llm_agents"),
        )
        val clusters = Clustering.byTag(items)
        val audio = clusters.first { it.label == "audio_music" }
        assertEquals(listOf(1L, 2L, 3L), audio.members)
    }

    @Test
    fun `groups below the minimum are folded into one misc bucket`() {
        val items = listOf(
            item(1, "a"), item(2, "b"), item(3, "c"),
            item(4, "big"), item(5, "big"), item(6, "big"),
        )
        val clusters = Clustering.byTag(items)
        assertEquals(2, clusters.size)
        val misc = clusters.first { it.label == "Verschiedenes" }
        assertEquals(listOf(1L, 2L, 3L), misc.members.sorted())
    }

    @Test
    fun `oversized groups are split into numbered parts under the cap`() {
        val items = (1L..120L).map { item(it, "llm_agents") }
        val clusters = Clustering.byTag(items)
        assertTrue(clusters.all { it.members.size <= Clustering.MAX_MEMBERS })
        assertEquals(listOf("llm_agents (1)", "llm_agents (2)", "llm_agents (3)"),
            clusters.map { it.label })
        assertEquals(120, clusters.sumOf { it.members.size })
    }

    @Test
    fun `untagged items fall back to their category`() {
        val items = listOf(
            item(1, "untagged", category = "shopping"),
            item(2, "untagged", category = "shopping"),
            item(3, "untagged", category = "shopping"),
        )
        val clusters = Clustering.byTag(items)
        assertEquals("shopping", clusters.single().label)
    }

    @Test
    fun `every item lands in exactly one cluster`() {
        val items = (1L..200L).map {
            item(it, listOf("a", "b", "c", "untagged")[(it % 4).toInt()])
        }
        val clusters = Clustering.byTag(items)
        val all = clusters.flatMap { it.members }
        assertEquals(200, all.size)
        assertEquals(200, all.toSet().size)
    }

    @Test
    fun `empty input yields no clusters`() {
        assertEquals(emptyList(), Clustering.byTag(emptyList()))
    }

    @Test
    fun `labels can be humanised without changing the grouping`() {
        val items = List(3) { item(it.toLong(), "llm_agents") }
        val clusters = Clustering.byTag(items, labelFor = { "KI/LLM" })
        assertEquals("KI/LLM", clusters.single().label)
    }
}
