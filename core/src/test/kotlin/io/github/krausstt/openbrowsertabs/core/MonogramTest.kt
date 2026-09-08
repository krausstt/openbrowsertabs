package io.github.krausstt.openbrowsertabs.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MonogramTest {

    @Test
    fun `well-known hosts get curated initials`() {
        assertEquals("GH", Monogram.initials("github.com"))
        assertEquals("HF", Monogram.initials("huggingface.co"))
        assertEquals("HN", Monogram.initials("news.ycombinator.com"))
        assertEquals("YT", Monogram.initials("youtube.com"))
    }

    @Test
    fun `hyphenated domains use one letter per part`() {
        assertEquals("HA", Monogram.initials("home-assistant.io"))
        assertEquals("XD", Monogram.initials("xda-developers.com"))
    }

    @Test
    fun `plain domains use the first two letters, www is ignored`() {
        assertEquals("HE", Monogram.initials("heise.de"))
        assertEquals("HE", Monogram.initials("www.heise.de"))
        assertEquals("ES", Monogram.initials("esphome.io"))
    }

    @Test
    fun `subdomains resolve to the registrable name`() {
        assertEquals("DO", Monogram.initials("docs.docker.com"))
        assertEquals("SE", Monogram.initials("wiki.seeedstudio.com"))
    }

    @Test
    fun `colour is stable per host and inside the palette`() {
        val a = Monogram.colorIndex("heise.de")
        val b = Monogram.colorIndex("www.heise.de")
        assertEquals(a, b, "www prefix must not change the colour")
        assertTrue(a in Monogram.PALETTE.indices)
        assertTrue(Monogram.color("github.com") in Monogram.PALETTE)
    }

    @Test
    fun `different hosts generally get different colours`() {
        val hosts = listOf("github.com", "huggingface.co", "heise.de", "arxiv.org",
                           "youtube.com", "reddit.com", "esphome.io", "docker.com")
        // not a guarantee (8 colours, pigeonhole) but the spread must not collapse
        assertTrue(hosts.map { Monogram.colorIndex(it) }.toSet().size >= 4)
    }
}

class ReadingTimeTest {

    @Test
    fun `word count ignores surrounding whitespace`() {
        assertEquals(3, Snippets.wordCount("  one two   three \n"))
        assertEquals(0, Snippets.wordCount(""))
        assertEquals(0, Snippets.wordCount(null))
    }

    @Test
    fun `very short text yields no estimate rather than a fake minute`() {
        assertNull(Snippets.readingMinutes(0))
        assertNull(Snippets.readingMinutes(29))
    }

    @Test
    fun `estimate rounds to whole minutes at 200 wpm`() {
        assertEquals(1, Snippets.readingMinutes(200))
        assertEquals(3, Snippets.readingMinutes(600))
        assertEquals(1, Snippets.readingMinutes(60))   // rounds up from 0.3
    }
}
