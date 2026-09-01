package io.github.krausstt.openbrowsertabs.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InboxBatchTest {

    private val now = 1_760_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun item(
        id: Long,
        host: String = "example.com",
        ageDays: Long = 0,
        sightings: Int = 1,
        hasContext: Boolean = false,
        hasTags: Boolean = false,
    ) = InboxBatch.Item(id, host, now - ageDays * day, sightings, hasContext, hasTags)

    @Test
    fun `never returns more than the requested size`() {
        val many = (1L..500L).map { item(it, host = "h$it.com", ageDays = it) }
        assertEquals(7, InboxBatch.pick(many, now = now).size)
    }

    @Test
    fun `reacted but untagged comes first`() {
        val picked = InboxBatch.pick(
            listOf(
                item(1, host = "a.com", ageDays = 0),
                item(2, host = "b.com", ageDays = 40, hasContext = true),
            ),
            size = 2,
            now = now,
        )
        assertEquals(listOf(2L, 1L), picked)
    }

    @Test
    fun `fully curated entries are not offered again`() {
        val picked = InboxBatch.pick(
            listOf(item(1, hasContext = true, hasTags = true), item(2, host = "b.com")),
            now = now,
        )
        assertEquals(listOf(2L), picked)
    }

    @Test
    fun `one host cannot fill the whole stack`() {
        val youtube = (1L..20L).map { item(it, host = "youtube.com", ageDays = it) }
        val others = (21L..30L).map { item(it, host = "h$it.com", ageDays = 50 + it) }
        val picked = InboxBatch.pick(youtube + others, now = now)
        val fromYoutube = picked.count { it <= 20L }
        assertEquals(2, fromYoutube)
        assertEquals(7, picked.size)
    }

    @Test
    fun `host cap does not shrink a small stack`() {
        // five entries, all from one host: the cap must not hand back two
        val picked = InboxBatch.pick((1L..5L).map { item(it, host = "youtube.com") }, now = now)
        assertEquals(5, picked.size)
    }

    @Test
    fun `recent beats old at equal signals`() {
        val picked = InboxBatch.pick(
            listOf(item(1, host = "a.com", ageDays = 300), item(2, host = "b.com", ageDays = 1)),
            size = 2,
            now = now,
        )
        assertEquals(listOf(2L, 1L), picked)
    }

    @Test
    fun `repeat sightings beat a single sighting of similar age`() {
        val picked = InboxBatch.pick(
            listOf(item(1, host = "a.com", ageDays = 5), item(2, host = "b.com", ageDays = 6, sightings = 4)),
            size = 2,
            now = now,
        )
        assertEquals(listOf(2L, 1L), picked)
    }

    @Test
    fun `empty pool and non-positive size are safe`() {
        assertTrue(InboxBatch.pick(emptyList(), now = now).isEmpty())
        assertTrue(InboxBatch.pick(listOf(item(1)), size = 0, now = now).isEmpty())
    }

    @Test
    fun `ids are never duplicated by the fill pass`() {
        val picked = InboxBatch.pick(
            (1L..4L).map { item(it, host = "one.com", ageDays = it) },
            size = 7,
            now = now,
        )
        assertEquals(picked.distinct(), picked)
    }
}
