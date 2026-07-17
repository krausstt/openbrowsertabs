package io.github.krausstt.openbrowsertabs.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextSimilarityTest {

    private val docs = listOf(
        TextSimilarity.Doc(1, "NVIDIA Parakeet ASR speech recognition model transcription automatic speech english audio"),
        TextSimilarity.Doc(2, "NVIDIA Canary multilingual speech recognition ASR model transcription translation audio"),
        TextSimilarity.Doc(3, "Nemotron Voice speech to speech model NVIDIA audio conversation real time ASR"),
        TextSimilarity.Doc(4, "Schokoladenkuchen Rezept backen Ofen Zucker Mehl Butter Eier vanille"),
        TextSimilarity.Doc(5, "ESP32 microcontroller sensor zigbee smart home automation firmware arduino"),
    )

    @Test
    fun `finds thematically related docs and ranks them above unrelated ones`() {
        val related = TextSimilarity.topRelated(docs, targetId = 3, k = 3)
        val ids = related.map { it.id }
        assertTrue(1L in ids, "Parakeet sollte verwandt sein: $related")
        assertTrue(2L in ids, "Canary sollte verwandt sein: $related")
        assertTrue(4L !in ids, "Kuchenrezept darf nicht verwandt sein: $related")
    }

    @Test
    fun `unrelated doc yields no fake associations`() {
        val related = TextSimilarity.topRelated(docs, targetId = 4, k = 3)
        assertEquals(emptyList(), related.map { it.id })
    }

    @Test
    fun `single doc corpus yields empty result`() {
        assertEquals(emptyList(), TextSimilarity.topRelated(docs.take(1), targetId = 1))
    }
}

class SnippetsTest {

    @Test
    fun `short text passes through`() {
        assertEquals("Kurzer Text.", Snippets.lead("Kurzer Text.", 200))
    }

    @Test
    fun `cuts at sentence boundary within budget`() {
        val text = "Erster Satz über ein Modell. Zweiter Satz mit Details. " +
            "Dritter Satz, der nicht mehr in das Budget passt und lang ist."
        val lead = Snippets.lead(text, 60)
        assertEquals("Erster Satz über ein Modell. Zweiter Satz mit Details.", lead)
    }

    @Test
    fun `overlong first sentence gets word-boundary cut with ellipsis`() {
        val text = "Dies ist ein einziger extrem langer Satz ohne Punkt der einfach " +
            "immer weiterläuft und niemals ein Satzzeichen benutzt"
        val lead = Snippets.lead(text, 50)!!
        assertTrue(lead.endsWith(" …"), lead)
        assertTrue(lead.length <= 53, lead)
    }

    @Test
    fun `blank input yields null`() {
        assertEquals(null, Snippets.lead("   ", 100))
        assertEquals(null, Snippets.lead(null, 100))
    }
}
