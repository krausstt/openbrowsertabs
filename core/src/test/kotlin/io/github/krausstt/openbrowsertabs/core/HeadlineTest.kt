package io.github.krausstt.openbrowsertabs.core

import kotlin.test.Test
import kotlin.test.assertEquals

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
