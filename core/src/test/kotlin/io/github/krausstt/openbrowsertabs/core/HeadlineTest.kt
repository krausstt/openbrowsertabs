package io.github.krausstt.openbrowsertabs.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HeadlineTest {

    @Test
    fun `huggingface repo name replaces the redundant page title`() {
        assertEquals(
            "Personaplex 7b v1",
            Headline.shortHeadline(
                "nvidia/personaplex-7b-v1 · Hugging Face",
                "https://huggingface.co/nvidia/personaplex-7b-v1",
            ),
        )
    }

    @Test
    fun `mixed-case acronym segments are preserved as-is`() {
        assertEquals(
            "VibeVoice ASR",
            Headline.shortHeadline(
                "microsoft/VibeVoice-ASR · Hugging Face",
                "https://huggingface.co/microsoft/VibeVoice-ASR",
            ),
        )
    }

    @Test
    fun `github repo name is derived the same way`() {
        assertEquals(
            "Openbrowsertabs",
            Headline.shortHeadline("krausstt/openbrowsertabs", "https://github.com/krausstt/openbrowsertabs"),
        )
    }

    @Test
    fun `generic article title passes through when short enough`() {
        assertEquals(
            "Claude + Mobbin = Design GENIUS (Finally)",
            Headline.shortHeadline(
                "Claude + Mobbin = Design GENIUS (Finally)",
                "https://example.com/blog/post",
            ),
        )
    }

    @Test
    fun `long generic title is truncated at a word boundary`() {
        val result = Headline.shortHeadline(
            "I Built a Working LEGO James Bond Gun Barrel Scene With Real Motors",
            "https://youtube.com/watch?v=abc123",
        )
        assertEquals("I Built a Working LEGO James Bond Gun", result)
    }

    @Test
    fun `site suffix separated by middle dot is stripped`() {
        assertEquals(
            "Some Article Title",
            Headline.shortHeadline("Some Article Title · Some Site", "https://example.com/a"),
        )
    }

    @Test
    fun `blank title falls back to host`() {
        assertEquals("example.com", Headline.shortHeadline(null, "https://example.com/a"))
    }
}

class SlugTitleTest {

    private val golem =
        "https://golem.de/news/qwen-perfekt-unperfekte-sprachsynthese-2608-211230.html"

    @Test
    fun `recovers a headline from the url slug, dropping ids and dates`() {
        assertEquals("Qwen Perfekt Unperfekte Sprachsynthese", Headline.fromSlug(golem))
    }

    @Test
    fun `publisher-only title falls back to the slug`() {
        // golem.de serves "Golem" as <title> behind its consent wall
        assertEquals("Qwen Perfekt Unperfekte Sprachsynthese", Headline.best("Golem", golem))
        assertEquals("Qwen Perfekt Unperfekte Sprachsynthese", Headline.best("golem.de", golem))
        assertEquals("Qwen Perfekt Unperfekte Sprachsynthese", Headline.best(null, golem))
    }

    @Test
    fun `a real article title is kept`() {
        assertEquals(
            "Qwen: Perfekt unperfekte Sprachsynthese",
            Headline.best("Qwen: Perfekt unperfekte Sprachsynthese", golem),
        )
    }

    @Test
    fun `slugless urls return null rather than nonsense`() {
        assertNull(Headline.fromSlug("https://example.com/"))
        assertNull(Headline.fromSlug("https://example.com/123456"))
    }

    @Test
    fun `repo hosts still win over the slug path`() {
        assertEquals(
            "Personaplex 7b v1",
            Headline.best("nvidia/personaplex-7b-v1 · Hugging Face",
                "https://huggingface.co/nvidia/personaplex-7b-v1"),
        )
    }
}

class ConsentWallTest {

    @Test
    fun `golem consent text is detected`() {
        assertTrue(Snippets.isConsentWall(
            "Für die Nutzung mit Werbung: Wir erheben personenbezogene Daten und " +
            "übermitteln diese auch an bis zu 160 Drittanbieter, die uns helfen, " +
            "unsere Webseite und Angebote zu verbessern und zu finanzieren."))
    }

    @Test
    fun `english consent banners are detected`() {
        assertTrue(Snippets.isConsentWall(
            "We and our partners store and access information on a device, such as " +
            "cookies, and process personal data. Accept all cookies to continue."))
    }

    @Test
    fun `an article about privacy is not mistaken for a banner`() {
        val article = ("Der Datenschutz bei vernetzten Geräten wirft Fragen auf. " +
            "Forscher untersuchten die Verarbeitung von Telemetrie in Haushaltsgeräten " +
            "und fanden erhebliche Unterschiede zwischen den Herstellern. ").repeat(12)
        assertTrue(!Snippets.isConsentWall(article))
    }

    @Test
    fun `ordinary text and blanks are not consent walls`() {
        assertTrue(!Snippets.isConsentWall("Ein neues Sprachmodell wurde vorgestellt."))
        assertTrue(!Snippets.isConsentWall(null))
        assertTrue(!Snippets.isConsentWall(""))
    }
}
