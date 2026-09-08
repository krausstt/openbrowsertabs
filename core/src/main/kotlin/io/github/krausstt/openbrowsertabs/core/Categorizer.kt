package io.github.krausstt.openbrowsertabs.core

/**
 * Domain/slug heuristics, ported 1:1 from pipeline/tabs_pipeline.py.
 * Phase 2 replaces this with content-based LLM enrichment; until then the
 * category ids here are the shared vocabulary between pipeline and app.
 */
object Categorizer {

    private val SHOP_HOSTS = setOf(
        "amazon.de", "amazon.com", "ebay.de", "ebay.com", "aliexpress.com",
        "reichelt.de", "berrybase.de", "welectron.com", "geizhals.de",
        "idealo.de", "mydealz.de", "thomann.de", "kleinanzeigen.de",
        "botland.de", "az-delivery.de", "conrad.de", "pollin.de",
        "eckstein-shop.de", "otto.de", "mediamarkt.de", "saturn.de",
        "alternate.de", "mindfactory.de", "notebooksbilliger.de",
        "temu.com", "banggood.com", "digikey.de", "mouser.de",
        "eu.store.bambulab.com", "seeedstudio.com", "shop.m5stack.com",
        "ikea.com", "lilygo.cc", "kickstarter.com", "bambulab.com",
        "m5stack.com", "waveshare.com", "adafruit.com", "sparkfun.com",
    )
    private val TRAVEL_HOSTS = setOf(
        "airbnb.com", "airbnb.de", "airbnb.co.uk", "skyscanner.de",
        "skyscanner.net", "booking.com", "safaribookings.com",
        "tui.com", "check24.de", "expedia.de",
        "tripadvisor.de", "tripadvisor.com", "komoot.com", "komoot.de",
    )
    private val NEWS_HOSTS = setOf(
        "marktechpost.com", "towardsdatascience.com", "kdnuggets.com",
        "venturebeat.com", "techcrunch.com", "theverge.com",
        "arstechnica.com", "heise.de", "golem.de", "t3n.de",
        "the-decoder.de", "the-decoder.com", "itsfoss.com",
        "tomsguide.com", "tomshardware.com", "techradar.com", "ign.com",
        "hackster.io", "cnx-software.com", "notebookcheck.com",
        "9to5google.com", "androidcentral.com", "androidpolice.com",
        "winfuture.de", "computerbase.de", "chip.de", "stadt-bremerhaven.de",
        "caschys.blog", "infoq.com", "thenewstack.io", "zdnet.com",
        "wired.com", "spiegel.de", "tagesschau.de", "analyticsvidhya.com",
        "machinelearningmastery.com", "unite.ai", "aibase.com", "36kr.com",
        "xda-developers.com", "howtogeek.com", "hackaday.com",
        "geeky-gadgets.com", "3druck.com", "all3dp.com", "musicradar.com",
        "yankodesign.com", "forbes.com", "businessinsider.com",
        "borncity.com", "amazona.de", "notebookcheck.net",
        "pcgameshardware.de", "gamestar.de", "makeuseof.com",
        "androidauthority.com", "sammobile.com", "netzwelt.de",
        "giga.de", "derstandard.de", "futurezone.at", "instructables.com",
    )
    private val BLOG_HOSTS = setOf(
        "dev.to", "levelup.gitconnected.com", "freecodecamp.org",
        "devblogs.microsoft.com", "hashnode.dev", "hackernoon.com",
    )
    private val VIDEO_HOSTS = setOf("youtube.com", "youtu.be", "vimeo.com")
    private val SOCIAL_HOSTS = setOf(
        "reddit.com", "x.com", "twitter.com", "news.ycombinator.com",
        "linkedin.com", "mastodon.social", "instagram.com",
    )
    private val DOCS_HINTS = listOf("docs.", "developer.", "learn.", "wiki")
    private val PAPER_HOSTS = setOf(
        "arxiv.org", "openreview.net", "paperswithcode.com", "aclanthology.org",
    )

    fun categorize(url: String): String {
        val s = urlsplit(url)
        val host = s.netloc
        val base = host.split(".").takeLast(2).joinToString(".")
        return when {
            host.endsWith("google.com") && s.path.startsWith("/search") -> "search_query"
            host in setOf("github.com", "codeberg.org", "gitlab.com") ||
                host.endsWith(".github.io") -> "repo"
            host == "huggingface.co" -> "model_or_dataset"
            base in PAPER_HOSTS || host in PAPER_HOSTS -> "paper"
            base in SHOP_HOSTS || host in SHOP_HOSTS -> "shopping"
            base in TRAVEL_HOSTS || host in TRAVEL_HOSTS -> "travel"
            base in VIDEO_HOSTS -> "video"
            base in SOCIAL_HOSTS || host in SOCIAL_HOSTS -> "discussion"
            DOCS_HINTS.any { host.startsWith(it) } ||
                url.contains("/docs/") || url.contains("/documentation") -> "docs"
            base in NEWS_HOSTS || host in NEWS_HOSTS -> "article"
            base in BLOG_HOSTS || host in BLOG_HOSTS || host.contains("medium.com") ||
                host.endsWith("substack.com") || url.contains("/blog/") ||
                host.contains("blog.") -> "blog"
            else -> "other"
        }
    }

    /** Extract the query text from a Google search URL. */
    fun searchLabel(url: String): String? {
        val s = urlsplit(url)
        if (!(s.netloc.endsWith("google.com") && s.path.startsWith("/search"))) return null
        return parseQsl(s.query).firstOrNull { it.first == "q" }?.second
    }

    private val TOPIC_RULES = listOf(
        "llm_agents" to Regex("agent|claude|gpt|llm|openai|anthropic|gemini|mcp|rag|prompt|copilot|langchain|langgraph|llama|mistral|deepseek|qwen|hugging|transformer|fine-?tun|embedding|vector|chatbot|genai|-ai-|^ai-|/ai/|artificial-intelligence|machine-learning|deep-learning|neural|diffusion|stable-diffusion|comfyui|ollama|vllm"),
        "coding_devops" to Regex("python|rust|golang|typescript|javascript|docker|kubernetes|k8s|git|vscode|neovim|linux|bash|sql|postgres|api|sdk|framework|library|devops|ci-cd|self-?host|proxmox|homelab|server"),
        "embedded_iot" to Regex("esp32|esp82|raspberry|arduino|microcontroller|zigbee|home-?assistant|smart-?home|iot|sensor|pcb|soldering|3d-?print|cnc|robot|drone|din-rail"),
        "audio_music" to Regex("audio|speaker|studiomonitor|synth|midi|dac|amplifier|hifi|hi-fi|headphone|squeezelite|music"),
        "hardware" to Regex("cpu|gpu|nvidia|amd|intel|ssd|nas|mini-?pc|laptop|notebook|smartphone|galaxy|pixel|tablet|monitor|display|router|wifi"),
        "data_science" to Regex("data-?science|pandas|jupyter|notebook|dataset|analytics|visualization|statistics|knowledge-?graph|networkx|graph"),
        "gaming" to Regex("pokemon|nintendo|playstation|xbox|steam|gaming|game"),
        // NOT bare "watch": every YouTube URL contains /watch?v=, which made
        // every video false-positive match "health" via this rule
        "health" to Regex("fitness|sleep|health|garmin|smartwatch|calisthenics"),
        "travel" to Regex("airbnb|skyscanner|safari|booking|flight|hotel|reise|namibia|travel"),
    )

    /**
     * Matches the topic keyword rules against arbitrary text. Originally
     * URL-only (slug keywords); works on any lowercase-able string, so
     * callers with real page content (title/description/body) get far more
     * accurate tags than opaque URLs (e.g. youtube.com/watch?v=<id>) can
     * ever provide — video/opaque-URL hosts are exactly where this matters.
     */
    fun topics(text: String): List<String> {
        val u = text.lowercase()
        val hits = TOPIC_RULES.filter { (_, re) -> re.containsMatchIn(u) }.map { it.first }
        return hits.ifEmpty { listOf("untagged") }
    }
}
