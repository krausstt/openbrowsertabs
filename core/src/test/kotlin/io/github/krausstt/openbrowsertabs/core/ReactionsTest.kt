package io.github.krausstt.openbrowsertabs.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReactionsTest {

    @Test
    fun `ids are unique and stable`() {
        val ids = Reactions.ALL.map { it.id }
        assertEquals(ids.distinct(), ids)
        // stored in the database, so renaming one silently orphans rows
        assertEquals(listOf("idea", "understand", "build", "reference", "now"), ids)
    }

    @Test
    fun `unknown ids never reach the database`() {
        assertNull(Reactions.sanitize("interessant"))
        assertNull(Reactions.sanitize(null))
        assertEquals("idea", Reactions.sanitize("idea"))
    }

    @Test
    fun `notes are collapsed trimmed and capped`() {
        assertEquals("KI Agents", Reactions.normalizeNote("  KI   \n Agents "))
        assertNull(Reactions.normalizeNote("   "))
        assertNull(Reactions.normalizeNote(null))
        assertEquals(Reactions.MAX_NOTE_CHARS, Reactions.normalizeNote("x".repeat(9_000))!!.length)
    }

    @Test
    fun `every reaction has an emoji and a short label`() {
        Reactions.ALL.forEach {
            assertTrue(it.emoji.isNotBlank(), "${it.id} has no emoji")
            assertTrue(it.label.length <= 12, "${it.id} label too long for a chip")
            assertTrue(it.meaning.isNotBlank())
        }
    }
}
