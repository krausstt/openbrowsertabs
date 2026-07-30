#!/usr/bin/env python3
"""Build the public demo dataset for the web demo.

PRIVACY: this script contains ONLY well-known public resources (model cards,
repos, papers, articles). The user's personal tab collections never enter the
repository — see .gitignore. The seed below is hand-curated so the demo is
reproducible and shareable.

The seed rows are run through the *real* pipeline logic (categorize/topics
from pipeline/tabs_pipeline.py) plus the typed-edge extraction described in
docs/design/2026-07-19-second-brain-architektur.md, so the demo shows what
the app actually computes — not mock output.

Usage: python3 web/build_demo_data.py > web/data.json
"""
import json
import math
import re
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "pipeline"))
from tabs_pipeline import categorize, normalize, topics  # noqa: E402

# --------------------------------------------------------------- seed corpus
# (url, title, description) — all public, all real.
SEED = [
    # ---- ASR / speech models: the signature association cluster
    ("https://huggingface.co/nvidia/parakeet-tdt-0.6b-v2",
     "nvidia/parakeet-tdt-0.6b-v2",
     "Fast English automatic speech recognition model with token-and-duration transducer, punctuation and timestamps."),
    ("https://huggingface.co/nvidia/canary-1b",
     "nvidia/canary-1b",
     "Multilingual speech recognition and translation model covering English, German, French and Spanish."),
    ("https://huggingface.co/openai/whisper-large-v3",
     "openai/whisper-large-v3",
     "Robust multilingual speech recognition and translation model trained on large-scale weak supervision."),
    ("https://huggingface.co/microsoft/VibeVoice-1.5B",
     "microsoft/VibeVoice-1.5B",
     "Long-form expressive text-to-speech model supporting multi-speaker conversational audio synthesis."),
    ("https://github.com/ggml-org/whisper.cpp",
     "ggml-org/whisper.cpp",
     "Plain C/C++ port of Whisper for on-device inference with GGUF quantization, no runtime dependencies."),
    ("https://arxiv.org/abs/2212.04356",
     "Robust Speech Recognition via Large-Scale Weak Supervision",
     "The Whisper paper: scaling weakly supervised training to 680k hours of multilingual speech data."),

    # ---- Agent frameworks / MCP: the true-positive standard cluster
    ("https://github.com/microsoft/agent-framework",
     "microsoft/agent-framework",
     "Framework for building, orchestrating and deploying AI agents and multi-agent workflows, with MCP support."),
    ("https://github.com/microsoft/autogen",
     "microsoft/autogen",
     "Multi-agent conversation framework for building LLM applications with cooperating agents."),
    ("https://github.com/microsoft/semantic-kernel",
     "microsoft/semantic-kernel",
     "SDK that integrates large language models with conventional programming languages via plugins and planners."),
    ("https://github.com/modelcontextprotocol/servers",
     "modelcontextprotocol/servers",
     "Reference implementations of Model Context Protocol servers for tools, resources and prompts."),
    ("https://modelcontextprotocol.io/docs/getting-started/intro",
     "Model Context Protocol documentation",
     "Open standard for connecting AI assistants to external tools, data sources and prompts over MCP."),
    ("https://github.com/langchain-ai/langgraph",
     "langchain-ai/langgraph",
     "Library for building stateful multi-agent applications as graphs with cycles, checkpoints and human-in-the-loop."),
    ("https://www.anthropic.com/engineering/building-effective-agents",
     "Building effective agents",
     "Engineering guidance on agent patterns: workflows versus autonomous agents, tool design and evaluation."),

    # ---- Local inference runtimes
    ("https://github.com/ollama/ollama",
     "ollama/ollama",
     "Run large language models locally with a simple CLI and REST API, GGUF model library included."),
    ("https://github.com/ggml-org/llama.cpp",
     "ggml-org/llama.cpp",
     "LLM inference in C/C++ with GGUF quantization, running on CPU, GPU and mobile devices."),
    ("https://github.com/vllm-project/vllm",
     "vllm-project/vllm",
     "High-throughput LLM serving engine using PagedAttention for efficient KV cache memory management."),
    ("https://ai.google.dev/edge/litert",
     "LiteRT overview",
     "Google runtime for on-device machine learning inference on Android, formerly TensorFlow Lite."),
    ("https://onnxruntime.ai/docs/get-started/with-java.html",
     "ONNX Runtime documentation",
     "Cross-platform inference engine running ONNX models on mobile, server and edge hardware."),

    # ---- Embedding models: the licence-decision cluster
    ("https://huggingface.co/ibm-granite/granite-embedding-278m-multilingual",
     "ibm-granite/granite-embedding-278m-multilingual",
     "Apache-2.0 multilingual text embedding model with 768 dimensions covering twelve languages."),
    ("https://huggingface.co/google/embeddinggemma-300m",
     "google/embeddinggemma-300m",
     "On-device text embedding model with Matryoshka dimensions and quantization-aware training."),
    ("https://huggingface.co/Qwen/Qwen3-Embedding-0.6B",
     "Qwen/Qwen3-Embedding-0.6B",
     "Multilingual embedding model with configurable output dimensions and strong retrieval benchmarks."),
    ("https://huggingface.co/Snowflake/snowflake-arctic-embed-m-v2.0",
     "Snowflake/snowflake-arctic-embed-m-v2.0",
     "Apache-2.0 multilingual retrieval embedding model with Matryoshka representation learning."),
    ("https://github.com/MinishLab/model2vec",
     "MinishLab/model2vec",
     "Distills sentence transformers into small static embedding models that run without a neural forward pass."),
    ("https://arxiv.org/abs/2509.20354",
     "EmbeddingGemma: Powerful and Lightweight Text Representations",
     "Technical report on a compact embedding model family designed for on-device retrieval."),

    # ---- Retrieval / RAG / recommender theory
    ("https://arxiv.org/abs/2005.11401",
     "Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks",
     "The original RAG paper combining a parametric generator with a non-parametric retrieval index."),
    ("https://dl.acm.org/doi/10.1145/2926720",
     "Novelty and Diversity in Recommender Systems",
     "Survey of evaluation and re-ranking approaches for diversity, novelty and serendipity in recommendation."),
    ("https://peerj.com/articles/cs-178/",
     "Serendipity-biased random walks for recommendation",
     "Graph embedding approach that biases DeepWalk transitions toward structurally near but topically distant nodes."),
    ("https://github.com/facebookresearch/faiss",
     "facebookresearch/faiss",
     "Library for efficient similarity search and clustering of dense vectors at scale."),
    ("https://www.sbert.net/",
     "Sentence Transformers documentation",
     "Framework for computing dense sentence embeddings for semantic search, clustering and retrieval."),

    # ---- Knowledge graph / PKM
    ("https://arxiv.org/abs/2304.09572",
     "An Ecosystem for Personal Knowledge Graphs: A Survey and Research Roadmap",
     "Survey defining personal knowledge graphs and their role in user-centric information systems."),
    ("https://www.wikidata.org/wiki/Property:P1365",
     "Wikidata property: replaces",
     "Ontology property linking an entity to the predecessor it replaced, paired with the inverse replaced-by."),
    ("https://github.com/SkepticMystic/graph-analysis",
     "SkepticMystic/graph-analysis",
     "Obsidian plugin computing note similarity via Jaccard index, co-citation and link prediction."),
    ("https://obsidian.md/",
     "Obsidian",
     "Local-first markdown knowledge base with bidirectional links and a graph view over the vault."),
    ("https://github.com/vasturiano/3d-force-graph",
     "vasturiano/3d-force-graph",
     "Force-directed graph rendering in three dimensions with WebGL, including a WebXR variant."),

    # ---- Android / mobile engineering
    ("https://developer.android.com/topic/libraries/architecture/workmanager",
     "WorkManager documentation",
     "Android API for deferrable background work with constraints, retries and guaranteed execution."),
    ("https://developer.android.com/training/sharing/receive",
     "Receiving simple data from other apps",
     "How to register an Android share target with intent filters and handle incoming send intents."),
    ("https://developer.android.com/develop/ui/compose/documentation",
     "Jetpack Compose documentation",
     "Declarative UI toolkit for Android with composable functions, state hoisting and Material 3."),
    ("https://github.com/ImranR98/Obtainium",
     "ImranR98/Obtainium",
     "Android app that installs and updates apps directly from GitHub releases and other sources."),
    ("https://jsoup.org/",
     "jsoup: Java HTML parser",
     "Library for parsing, traversing and cleaning HTML with a CSS-selector based API."),
    ("https://github.com/mozilla/readability",
     "mozilla/readability",
     "Standalone version of the Firefox Reader View article extraction algorithm."),
    ("https://trafilatura.readthedocs.io/en/latest/",
     "Trafilatura documentation",
     "Python package for web text extraction, metadata parsing and boilerplate removal at scale."),

    # ---- Embedded / maker
    ("https://www.espressif.com/en/products/socs/esp32-s3",
     "ESP32-S3 product page",
     "Microcontroller with Wi-Fi, Bluetooth Low Energy and vector instructions for on-device machine learning."),
    ("https://esphome.io/",
     "ESPHome",
     "System for configuring ESP32 and ESP8266 devices with YAML and integrating them with Home Assistant."),
    ("https://www.home-assistant.io/",
     "Home Assistant",
     "Open source home automation platform running locally with integrations for thousands of devices."),
    ("https://docs.zigbee2mqtt.io/",
     "Zigbee2MQTT documentation",
     "Bridge that exposes Zigbee devices to MQTT without a vendor gateway."),
    ("https://wiki.seeedstudio.com/xiao_esp32s3_getting_started/",
     "XIAO ESP32S3 getting started",
     "Compact ESP32-S3 development board with camera and microphone support for edge AI projects."),
    ("https://github.com/espressif/esp-idf",
     "espressif/esp-idf",
     "Official development framework for ESP32 series chips with FreeRTOS and peripheral drivers."),
    ("https://www.raspberrypi.com/documentation/computers/getting-started.html",
     "Raspberry Pi documentation",
     "Setup and configuration guide for Raspberry Pi single-board computers and their operating system."),

    # ---- 3D printing / hardware
    ("https://github.com/Klipper3d/klipper",
     "Klipper3d/klipper",
     "3D printer firmware that offloads motion planning to a host computer for higher print speeds."),
    ("https://www.printables.com/",
     "Printables",
     "Repository of downloadable 3D models with print profiles and community remixes."),
    ("https://all3dp.com/1/best-3d-printer-slicer-software/",
     "Best 3D printer slicer software",
     "Comparison of slicing tools covering supports, adaptive layers and multi-material printing."),

    # ---- Self-hosting / dev infrastructure
    ("https://tailscale.com/kb/1017/install",
     "Tailscale installation guide",
     "Mesh VPN built on WireGuard that connects devices without opening firewall ports."),
    ("https://docs.docker.com/compose/",
     "Docker Compose documentation",
     "Tool for defining and running multi-container applications from a declarative YAML file."),
    ("https://www.proxmox.com/en/proxmox-virtual-environment/overview",
     "Proxmox Virtual Environment",
     "Open source virtualization platform combining KVM virtual machines and Linux containers."),
    ("https://caddyserver.com/docs/",
     "Caddy documentation",
     "Web server with automatic HTTPS certificate management and a simple configuration format."),
    ("https://sqlite.org/lang_with.html",
     "SQLite recursive common table expressions",
     "Reference for WITH RECURSIVE queries, the basis for graph traversal inside SQLite."),

    # ---- News / analysis articles
    ("https://simonwillison.net/2025/Dec/31/llms-in-2025/",
     "Things we learned about LLMs in 2025",
     "Year in review covering model releases, pricing collapse, agents and local inference progress."),
    ("https://venturebeat.com/ai/what-enterprises-get-wrong-about-ai-agents/",
     "What enterprises get wrong about AI agents",
     "Analysis of evaluation gaps and autonomy risks in production agent deployments."),
    ("https://www.technologyreview.com/2026/07/13/1140343/what-anthropics-latest-ai-discovery-does-and-doesnt-show/",
     "What Anthropic's latest AI discovery does and doesn't show",
     "Critical read of interpretability findings and what they imply about model internals."),
    ("https://www.kdnuggets.com/building-local-ai-systems-qwen3-6-mcps",
     "Building Local AI Systems: Qwen3.6 + MCPs",
     "Tutorial on running a local model and wiring it to tools through the Model Context Protocol."),
    ("https://techcrunch.com/2025/05/27/read-it-later-app-pocket-is-shutting-down-here-are-the-best-alternatives/",
     "Read-it-later app Pocket is shutting down",
     "Coverage of the Pocket shutdown and a comparison of surviving read-later services."),
    ("https://www.theverge.com/tech/rss-is-back",
     "RSS never died",
     "Feature on the resurgence of feed readers as an antidote to algorithmic timelines."),

    # ---- Shopping (category diversity)
    ("https://www.reichelt.de/de/de/shop/produkt/esp32_s3_devkitc_1_n8r8-357289",
     "ESP32-S3 DevKitC development board",
     "Development board with 8 MB flash and 8 MB PSRAM for embedded prototyping."),
    ("https://eu.store.bambulab.com/products/a1-mini",
     "Bambu Lab A1 mini",
     "Compact 3D printer with automatic bed levelling and optional multi-colour filament system."),
    ("https://www.thomann.de/de/adam_audio_t5v.htm",
     "ADAM Audio T5V studio monitor",
     "Two-way active nearfield monitor with a ribbon tweeter for home studio use."),

    # ---- Discussion
    ("https://news.ycombinator.com/item?id=44762913",
     "Hacker News discussion on link shorteners",
     "Thread about tracking redirects, privacy implications and unwrapping shortened URLs."),
    ("https://www.reddit.com/r/LocalLLaMA/comments/local_embedding_models/",
     "r/LocalLLaMA: which embedding model for local RAG",
     "Community comparison of small multilingual embedding models for offline retrieval setups."),
    ("https://www.reddit.com/r/selfhosted/comments/bookmark_managers/",
     "r/selfhosted: bookmark manager recommendations",
     "Discussion of self-hosted read-later and bookmarking tools after the Pocket shutdown."),

    # ---- Video
    ("https://youtube.com/watch?v=demo_local_llm",
     "Running local LLMs on a phone in 2026",
     "Walkthrough of quantized on-device inference, memory limits and battery impact measurements."),
    ("https://youtube.com/watch?v=demo_esp32_audio",
     "ESP32 audio projects: from I2S microphone to wake word",
     "Build log covering microphone wiring, audio buffering and running a small wake-word model."),
]

# Real, documented successor relationships (evidence-backed, see design doc).
SUPERSEDES = [
    ("https://github.com/microsoft/agent-framework", "https://github.com/microsoft/autogen"),
    ("https://github.com/microsoft/agent-framework", "https://github.com/microsoft/semantic-kernel"),
    ("https://huggingface.co/nvidia/canary-1b", "https://huggingface.co/nvidia/parakeet-tdt-0.6b-v2"),
]

# Controlled vocabulary for the "discusses same standard" edge (gazetteer).
STANDARDS = [
    "mcp", "model context protocol", "onnx", "rag", "gguf", "quantization",
    "matryoshka", "webxr", "zigbee", "mqtt", "wireguard", "i2s", "asr",
    "text-to-speech", "embedding", "force-directed", "readability",
]

MIN_SIMILARITY = 0.08
TOP_K = 4


def tokenize(text):
    stop = {
        "der", "die", "das", "und", "oder", "ein", "eine", "mit", "für", "von",
        "the", "and", "for", "with", "that", "this", "from", "have", "has",
        "are", "was", "were", "will", "can", "you", "your", "its", "our",
        "not", "but", "all", "how", "why", "what", "when", "more", "most",
        "into", "than", "then", "them", "they", "there", "here", "about",
        "www", "http", "https", "com", "org",
    }
    return [t for t in re.split(r"[^a-z0-9]+", text.lower()) if len(t) >= 3 and t not in stop]


def tfidf_vectors(docs):
    """docs: list of token lists -> list of normalised {term: weight}."""
    n = len(docs)
    df = Counter()
    for tokens in docs:
        df.update(set(tokens))
    vectors = []
    for tokens in docs:
        tf = Counter(tokens)
        vec = {t: c * (math.log((n + 1) / (df[t] + 1)) + 1) for t, c in tf.items()}
        norm = math.sqrt(sum(v * v for v in vec.values()))
        vectors.append({t: v / norm for t, v in vec.items()} if norm else {})
    return vectors


def cosine(a, b):
    small, large = (a, b) if len(a) <= len(b) else (b, a)
    return sum(w * large.get(t, 0.0) for t, w in small.items())


def org_of(url):
    """Organisation entity from repo-host URL paths (weak signal)."""
    m = re.match(r"^https://(?:huggingface\.co|github\.com)/([^/]+)/", url + "/")
    return m.group(1).lower() if m else None


def standards_in(text):
    low = text.lower()
    return sorted({s for s in STANDARDS if s in low})


def main():
    nodes = []
    for idx, (url, title, description) in enumerate(SEED):
        canonical = normalize(url)
        blob = f"{title} {description} {canonical}"
        nodes.append({
            "id": idx,
            "url": canonical,
            "host": canonical.split("/")[2],
            "title": title,
            "description": description,
            "category": categorize(canonical),
            "topics": topics(blob),
            "org": org_of(canonical),
            "standards": standards_in(blob),
        })

    corpus = [tokenize(f"{n['title']} {n['description']} {' '.join(n['topics'])}") for n in nodes]
    vectors = tfidf_vectors(corpus)

    url_to_id = {n["url"]: n["id"] for n in nodes}
    edges = []
    seen = set()

    def add_edge(src, dst, edge_type, weight, evidence):
        key = (min(src, dst), max(src, dst), edge_type)
        if key in seen or src == dst:
            return
        seen.add(key)
        edges.append({
            "source": src, "target": dst, "type": edge_type,
            "weight": round(weight, 4), "evidence": evidence,
        })

    # strong: documented successor relationships
    for newer, older in SUPERSEDES:
        s, t = url_to_id.get(normalize(newer)), url_to_id.get(normalize(older))
        if s is not None and t is not None:
            add_edge(s, t, "supersedes", 1.0, "documented successor")

    # strong: shared technical standard (gazetteer match)
    for i, a in enumerate(nodes):
        for b in nodes[i + 1:]:
            shared = set(a["standards"]) & set(b["standards"])
            if shared:
                add_edge(a["id"], b["id"], "discusses_same_standard",
                         min(1.0, 0.45 + 0.2 * len(shared)), "shared: " + ", ".join(sorted(shared)))

    # weak: same organisation — the documented false-positive source
    for i, a in enumerate(nodes):
        for b in nodes[i + 1:]:
            if a["org"] and a["org"] == b["org"]:
                add_edge(a["id"], b["id"], "same_organization", 0.2, f"both {a['org']}")

    # baseline: content similarity
    for i, a in enumerate(nodes):
        scored = sorted(
            ((cosine(vectors[i], vectors[j]), j) for j in range(len(nodes)) if j != i),
            reverse=True,
        )[:TOP_K]
        for score, j in scored:
            if score >= MIN_SIMILARITY:
                add_edge(a["id"], j, "similar_to", score, f"cosine {score:.2f}")

    payload = {
        "generated_by": "web/build_demo_data.py (real pipeline logic)",
        "note": "Public demo corpus. No personal data.",
        "nodes": nodes,
        "edges": edges,
        "idf": {},
    }
    # ship the IDF table so the browser can score newly pasted links live
    n = len(corpus)
    df = Counter()
    for tokens in corpus:
        df.update(set(tokens))
    payload["idf"] = {t: round(math.log((n + 1) / (c + 1)) + 1, 4) for t, c in df.items()}
    payload["doc_vectors"] = [
        {t: round(w, 4) for t, w in v.items() if w > 0.02} for v in vectors
    ]
    json.dump(payload, sys.stdout, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
