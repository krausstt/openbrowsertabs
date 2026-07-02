#!/usr/bin/env python3
"""Browser-tab ingestion pipeline: parse -> normalize -> dedupe -> categorize.

Input: one or more PDF exports of open browser tabs (numbered URL lists, as
produced by "share all tabs"-style exports). Each PDF is treated as a
point-in-time snapshot. Deduping across snapshots yields first_seen /
last_seen per canonical URL — the basis for relevance decay: a URL that
disappears from later snapshots was implicitly closed by the user.

Usage:
    python3 tabs_pipeline.py <data_dir> <out.json>

<data_dir> must contain the snapshot PDFs plus a manifest.json:
    { "some_export.pdf": {"id": "s1", "date": "2026-01-15"}, ... }

PRIVACY: <data_dir> and all outputs are gitignored. Never commit URL data.
"""
import json
import re
import sys
from collections import Counter
from pathlib import Path
from urllib.parse import urlsplit, urlunsplit, parse_qsl, urlencode, unquote

from pypdf import PdfReader

ENTRY_SPLIT = re.compile(r"\s(\d+)\.\s+")
URL_RE = re.compile(r"https?://\S+")


# ------------------------------------------------------------------ parsing
def parse_pdf(path: Path):
    """Parse a tab-export PDF into (number|None, url) tuples.

    Handles: hard-wrapped URLs across lines, numbered lists, and trailing
    unnumbered plain URL lists appended after the numbered section.
    """
    reader = PdfReader(str(path))
    full = "".join((p.extract_text() or "") for p in reader.pages)
    # URL fragments wrap without spaces at line breaks, so joining is safe
    full = full.replace("\n", "")
    first = full.find("1.")
    body = " " + full[first:]
    parts = ENTRY_SPLIT.split(body)
    entries = []
    it = iter(parts[1:])
    for num, chunk in zip(it, it):
        urls = URL_RE.findall(chunk.strip())
        if not urls:
            entries.append((int(num), None))
            continue
        entries.append((int(num), urls[0].rstrip(".,;")))
        # extra URLs in a chunk = unnumbered plain list after the numbering
        entries.extend((None, u.rstrip(".,;")) for u in urls[1:])
    return entries


# ------------------------------------------------------------- normalization
# prefix families (utm_*, gad_*) or exact-match names — never bare prefixes,
# otherwise "si" would also strip legitimate params like "size"
TRACKING_PARAMS = re.compile(
    r"^(utm_|gad_"
    r"|(gclid|gbraid|wbraid|fbclid|mc_cid|mc_eid|igshid|si|ref_src"
    r"|PROVID|cmpid|smid|sh|ncid|guccounter|guce_referrer|_hsenc|_hsmi)$)", re.I)
AMP_HOST = re.compile(r"^(www[-.])?([a-z0-9-]+)\.cdn\.ampproject\.org$", re.I)


def unwrap_amp(url: str) -> str:
    """Recover the canonical URL from Google AMP-cache and redirect links."""
    s = urlsplit(url)
    # google.com/url?q=<target> click-tracking wrapper carries the target inline
    if s.netloc.lower().endswith("google.com") and s.path == "/url":
        for k, v in parse_qsl(s.query):
            if k in ("q", "url") and v.startswith("http"):
                return v
    m = re.match(r"^/amp/s/(.+)$", s.path)
    if s.netloc.lower().endswith("google.com") and m:
        rest = m.group(1)
        if s.query:
            rest += "?" + s.query
        return "https://" + re.sub(r"\.amp(?=$|\?)", "", rest)
    if not AMP_HOST.match(s.netloc):
        return url
    for blob in (s.fragment, s.query):
        m = re.search(r"ampshare=([^&]+)", blob)
        if m:
            return unquote(m.group(1))
    m = re.match(r"^/v/s/([^?#]+)", s.path)
    if m:
        return "https://" + re.sub(r"\.amp$", "", m.group(1))
    return url


def normalize(url: str) -> str:
    url = unwrap_amp(url)
    s = urlsplit(url)
    host = s.netloc.lower()
    if host.startswith("www."):
        host = host[4:]
    q = [(k, v) for k, v in parse_qsl(s.query, keep_blank_values=True)
         if not TRACKING_PARAMS.match(k)]
    if "youtube.com" in host:
        q = [(k, v) for k, v in q if k == "v"]
    q = [(k, v) for k, v in q if k not in ("amp", "amp_gsa", "amp_js_v", "usqp")]
    path = re.sub(r"\.amp$", "", s.path).rstrip("/") or "/"
    path = re.sub(r"/amp$", "", path) or "/"
    return urlunsplit(("https", host, path, urlencode(q), ""))


# ------------------------------------------------------------ categorization
SHOP_HOSTS = {"amazon.de", "amazon.com", "ebay.de", "ebay.com", "aliexpress.com",
              "reichelt.de", "berrybase.de", "welectron.com", "geizhals.de",
              "idealo.de", "mydealz.de", "thomann.de", "kleinanzeigen.de",
              "botland.de", "az-delivery.de", "conrad.de", "pollin.de",
              "eckstein-shop.de", "otto.de", "mediamarkt.de", "saturn.de",
              "alternate.de", "mindfactory.de", "notebooksbilliger.de",
              "temu.com", "banggood.com", "digikey.de", "mouser.de",
              "eu.store.bambulab.com", "seeedstudio.com", "shop.m5stack.com",
              "ikea.com", "lilygo.cc", "kickstarter.com", "bambulab.com",
              "m5stack.com", "waveshare.com", "adafruit.com", "sparkfun.com"}
TRAVEL_HOSTS = {"airbnb.com", "airbnb.de", "airbnb.co.uk", "skyscanner.de",
                "skyscanner.net", "booking.com", "safaribookings.com",
                "tui.com", "check24.de", "expedia.de",
                "tripadvisor.de", "tripadvisor.com", "komoot.com", "komoot.de"}
NEWS_HOSTS = {"marktechpost.com", "towardsdatascience.com", "kdnuggets.com",
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
              "giga.de", "derstandard.de", "futurezone.at", "instructables.com"}
BLOG_HOSTS = {"dev.to", "levelup.gitconnected.com", "freecodecamp.org",
              "devblogs.microsoft.com", "hashnode.dev", "hackernoon.com"}
VIDEO_HOSTS = {"youtube.com", "youtu.be", "vimeo.com"}
SOCIAL_HOSTS = {"reddit.com", "x.com", "twitter.com", "news.ycombinator.com",
                "linkedin.com", "mastodon.social", "instagram.com"}
DOCS_HINTS = ("docs.", "developer.", "learn.", "wiki")
PAPER_HOSTS = {"arxiv.org", "openreview.net", "paperswithcode.com", "aclanthology.org"}


def categorize(url: str) -> str:
    s = urlsplit(url)
    host = s.netloc
    base = ".".join(host.split(".")[-2:])
    if host.endswith("google.com") and s.path.startswith("/search"):
        return "search_query"
    if host in ("github.com", "codeberg.org", "gitlab.com") or host.endswith(".github.io"):
        return "repo"
    if host == "huggingface.co":
        return "model_or_dataset"
    if base in PAPER_HOSTS or host in PAPER_HOSTS:
        return "paper"
    if base in SHOP_HOSTS or host in SHOP_HOSTS:
        return "shopping"
    if base in TRAVEL_HOSTS or host in TRAVEL_HOSTS:
        return "travel"
    if base in VIDEO_HOSTS:
        return "video"
    if base in SOCIAL_HOSTS or host in SOCIAL_HOSTS:
        return "discussion"
    if any(host.startswith(h) for h in DOCS_HINTS) or "/docs/" in url or "/documentation" in url:
        return "docs"
    if base in NEWS_HOSTS or host in NEWS_HOSTS:
        return "article"
    if (base in BLOG_HOSTS or host in BLOG_HOSTS or "medium.com" in host
            or host.endswith("substack.com") or "/blog/" in url or "blog." in host):
        return "blog"
    return "other"


def search_label(url: str):
    """Extract the query text from a Google search URL."""
    s = urlsplit(url)
    if not (s.netloc.endswith("google.com") and s.path.startswith("/search")):
        return None
    for k, v in parse_qsl(s.query):
        if k == "q":
            return v
    return None


TOPIC_RULES = [
    ("llm_agents",   r"agent|claude|gpt|llm|openai|anthropic|gemini|mcp|rag|prompt|copilot|langchain|langgraph|llama|mistral|deepseek|qwen|hugging|transformer|fine-?tun|embedding|vector|chatbot|genai|-ai-|^ai-|/ai/|artificial-intelligence|machine-learning|deep-learning|neural|diffusion|stable-diffusion|comfyui|ollama|vllm"),
    ("coding_devops", r"python|rust|golang|typescript|javascript|docker|kubernetes|k8s|git|vscode|neovim|linux|bash|sql|postgres|api|sdk|framework|library|devops|ci-cd|self-?host|proxmox|homelab|server"),
    ("embedded_iot", r"esp32|esp82|raspberry|arduino|microcontroller|zigbee|home-?assistant|smart-?home|iot|sensor|pcb|soldering|3d-?print|cnc|robot|drone|din-rail"),
    ("audio_music",  r"audio|speaker|studiomonitor|synth|midi|dac|amplifier|hifi|hi-fi|headphone|squeezelite|music"),
    ("hardware",     r"cpu|gpu|nvidia|amd|intel|ssd|nas|mini-?pc|laptop|notebook|smartphone|galaxy|pixel|tablet|monitor|display|router|wifi"),
    ("data_science", r"data-?science|pandas|jupyter|notebook|dataset|analytics|visualization|statistics|knowledge-?graph|networkx|graph"),
    ("gaming",       r"pokemon|nintendo|playstation|xbox|steam|gaming|game"),
    ("health",       r"fitness|sleep|health|garmin|watch|calisthenics"),
    ("travel",       r"airbnb|skyscanner|safari|booking|flight|hotel|reise|namibia|travel"),
]


def topics(url: str) -> list:
    u = url.lower()
    return [name for name, pat in TOPIC_RULES if re.search(pat, u)] or ["untagged"]


# ------------------------------------------------------------------- main
def main(data_dir: str, out_path: str):
    data = Path(data_dir)
    manifest = json.loads((data / "manifest.json").read_text())
    snap_order = [v["id"] for v in sorted(manifest.values(), key=lambda v: v["date"])]

    canon = {}
    parse_stats = {}
    for fname, meta in sorted(manifest.items(), key=lambda kv: kv[1]["date"]):
        sid, date = meta["id"], meta["date"]
        entries = parse_pdf(data / fname)
        nums = [n for n, _ in entries if n is not None]
        parse_stats[sid] = {
            "date": date, "entries": len(entries), "numbered": len(nums),
            "unnumbered": sum(1 for n, _ in entries if n is None),
            "numbering_ok": nums == list(range(1, len(nums) + 1)),
            "no_url": sum(1 for _, u in entries if u is None),
        }
        for num, url in entries:
            if not url:
                continue
            cu = normalize(url)
            rec = canon.setdefault(cu, {
                "url": cu, "original": url, "snapshots": [], "positions": {}})
            if sid not in rec["snapshots"]:
                rec["snapshots"].append(sid)
            rec["positions"][sid] = num

    latest = snap_order[-1]
    for rec in canon.values():
        rec["category"] = categorize(rec["url"])
        label = search_label(rec["url"])
        if label:
            rec["label"] = label
        rec["topics"] = topics(rec["url"])
        rec["host"] = urlsplit(rec["url"]).netloc
        snaps = rec["snapshots"]
        rec["first_seen"] = snaps[0]
        rec["last_seen"] = snaps[-1]
        rec["n_snapshots"] = len(snaps)
        rec["still_open"] = latest in snaps
        idx = [snap_order.index(s) for s in snaps]
        rec["reopened"] = any(b - a > 1 for a, b in zip(idx, idx[1:]))

    records = sorted(canon.values(), key=lambda r: (-r["n_snapshots"], r["url"]))
    Path(out_path).write_text(json.dumps(
        {"parse_stats": parse_stats, "snap_order": snap_order,
         "n_canonical": len(records), "records": records},
        ensure_ascii=False, indent=1))

    print("== parse validation ==")
    for sid, st in parse_stats.items():
        print(f"  {sid} ({st['date']}): {st['entries']} entries, "
              f"numbering_ok={st['numbering_ok']}, no_url={st['no_url']}")
    print(f"\ncanonical URLs: {len(records)}")
    print("still open:", sum(r["still_open"] for r in records),
          "/ closed:", sum(not r["still_open"] for r in records))
    print("\ncategories:")
    for k, v in Counter(r["category"] for r in records).most_common():
        print(f"  {k:18} {v}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
