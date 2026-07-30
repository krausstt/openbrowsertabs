/**
 * Browser port of the enrichment logic in `core/` (Kotlin) and
 * `pipeline/tabs_pipeline.py`. Kept deliberately close to the originals so
 * the demo shows what the app really computes.
 *
 * Parity is guarded on the Kotlin/Python side by unit tests; this file is the
 * third implementation and exists only for the web demo.
 */
const OBT = (() => {
  'use strict';

  // ------------------------------------------------------------ normalizer
  const TRACKING = /^(utm_|gad_|(gclid|gbraid|wbraid|fbclid|mc_cid|mc_eid|igshid|si|ref_src|PROVID|cmpid|smid|sh|ncid|guccounter|guce_referrer|_hsenc|_hsmi)$)/i;
  const AMP_HOST = /^(www[-.])?([a-z0-9-]+)\.cdn\.ampproject\.org$/i;

  function unwrap(rawUrl) {
    let u;
    try { u = new URL(rawUrl); } catch { return rawUrl; }

    // google.com/url?q=<target> carries the destination inline
    if (u.hostname.endsWith('google.com') && u.pathname === '/url') {
      const q = u.searchParams.get('q') || u.searchParams.get('url');
      if (q && q.startsWith('http')) return q;
    }
    // google.com/amp/s/<host>/<path>
    const ampPath = u.pathname.match(/^\/amp\/s\/(.+)$/);
    if (u.hostname.endsWith('google.com') && ampPath) {
      return 'https://' + ampPath[1].replace(/\.amp(?=$|\?)/, '') + u.search;
    }
    // *.cdn.ampproject.org — prefer the ampshare param, else the /v/s/ path
    if (AMP_HOST.test(u.hostname)) {
      for (const blob of [u.hash, u.search]) {
        const m = blob && blob.match(/ampshare=([^&]+)/);
        if (m) return decodeURIComponent(m[1]);
      }
      const v = u.pathname.match(/^\/v\/s\/([^?#]+)/);
      if (v) return 'https://' + v[1].replace(/\.amp$/, '');
    }
    return rawUrl;
  }

  function normalize(rawUrl) {
    let u;
    try { u = new URL(unwrap(rawUrl)); } catch { return rawUrl; }
    let host = u.hostname.toLowerCase().replace(/^www\./, '');

    const kept = [];
    u.searchParams.forEach((value, key) => {
      if (TRACKING.test(key)) return;
      if (['amp', 'amp_gsa', 'amp_js_v', 'usqp'].includes(key)) return;
      if (host.includes('youtube.com') && key !== 'v') return;
      kept.push([key, value]);
    });

    let path = u.pathname.replace(/\.amp$/, '').replace(/\/+$/, '') || '/';
    path = path.replace(/\/amp$/, '') || '/';
    const qs = kept.map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`).join('&');
    return `https://${host}${path}${qs ? '?' + qs : ''}`;
  }

  // ----------------------------------------------------------- categorizer
  const SHOP = new Set(['amazon.de', 'amazon.com', 'ebay.de', 'aliexpress.com', 'reichelt.de',
    'berrybase.de', 'geizhals.de', 'idealo.de', 'mydealz.de', 'thomann.de', 'conrad.de',
    'eu.store.bambulab.com', 'seeedstudio.com', 'ikea.com', 'kickstarter.com', 'adafruit.com',
    'sparkfun.com', 'bambulab.com', 'store.bambulab.com', 'printables.com']);
  const NEWS = new Set(['marktechpost.com', 'towardsdatascience.com', 'kdnuggets.com',
    'venturebeat.com', 'techcrunch.com', 'theverge.com', 'arstechnica.com', 'heise.de',
    'golem.de', 't3n.de', 'itsfoss.com', 'tomsguide.com', 'techradar.com', 'hackster.io',
    'cnx-software.com', 'wired.com', 'xda-developers.com', 'howtogeek.com', 'hackaday.com',
    'all3dp.com', 'technologyreview.com', 'zdnet.com', 'infoq.com', 'thenewstack.io']);
  const BLOG = new Set(['dev.to', 'freecodecamp.org', 'devblogs.microsoft.com', 'hackernoon.com']);
  const VIDEO = new Set(['youtube.com', 'youtu.be', 'vimeo.com']);
  const SOCIAL = new Set(['reddit.com', 'x.com', 'twitter.com', 'news.ycombinator.com',
    'linkedin.com', 'mastodon.social', 'instagram.com']);
  const PAPER = new Set(['arxiv.org', 'openreview.net', 'paperswithcode.com', 'aclanthology.org',
    'dl.acm.org', 'peerj.com']);
  const DOCS_PREFIX = ['docs.', 'developer.', 'learn.', 'wiki'];

  function categorize(url) {
    let u;
    try { u = new URL(url); } catch { return 'other'; }
    const host = u.hostname.replace(/^www\./, '');
    const base = host.split('.').slice(-2).join('.');

    if (host.endsWith('google.com') && u.pathname.startsWith('/search')) return 'search_query';
    if (['github.com', 'codeberg.org', 'gitlab.com'].includes(host) || host.endsWith('.github.io')) return 'repo';
    if (host === 'huggingface.co') return 'model_or_dataset';
    if (PAPER.has(base) || PAPER.has(host)) return 'paper';
    if (SHOP.has(base) || SHOP.has(host)) return 'shopping';
    if (VIDEO.has(base)) return 'video';
    if (SOCIAL.has(base) || SOCIAL.has(host)) return 'discussion';
    if (DOCS_PREFIX.some(p => host.startsWith(p)) || url.includes('/docs/') || url.includes('/documentation')) return 'docs';
    if (NEWS.has(base) || NEWS.has(host)) return 'article';
    if (BLOG.has(base) || BLOG.has(host) || host.includes('medium.com') ||
        host.endsWith('substack.com') || url.includes('/blog/') || host.includes('blog.')) return 'blog';
    return 'other';
  }

  // --------------------------------------------------------------- topics
  const TOPIC_RULES = [
    ['llm_agents', /agent|claude|gpt|llm|openai|anthropic|gemini|mcp|rag|prompt|copilot|langchain|langgraph|llama|mistral|deepseek|qwen|hugging|transformer|fine-?tun|embedding|vector|chatbot|genai|-ai-|\/ai\/|artificial-intelligence|machine-learning|deep-learning|neural|diffusion|comfyui|ollama|vllm|speech|voice|asr/],
    ['coding_devops', /python|rust|golang|typescript|javascript|docker|kubernetes|k8s|git|vscode|neovim|linux|bash|sql|postgres|api|sdk|framework|library|devops|ci-cd|self-?host|proxmox|homelab|server|compose|caddy/],
    ['embedded_iot', /esp32|esp82|raspberry|arduino|microcontroller|zigbee|home-?assistant|smart-?home|iot|sensor|pcb|soldering|3d-?print|cnc|robot|drone|firmware|klipper|mqtt/],
    ['audio_music', /audio|speaker|studiomonitor|synth|midi|dac|amplifier|hifi|hi-fi|headphone|monitor|music|microphone/],
    ['hardware', /cpu|gpu|nvidia|amd|intel|ssd|nas|mini-?pc|laptop|notebook|smartphone|galaxy|pixel|tablet|display|router|wifi|board/],
    ['data_science', /data-?science|pandas|jupyter|dataset|analytics|visualization|statistics|knowledge-?graph|networkx|graph|retrieval|recommend/],
    ['gaming', /pokemon|nintendo|playstation|xbox|steam|gaming|game/],
    ['health', /fitness|sleep|health|garmin|smartwatch|calisthenics/],
    ['travel', /airbnb|skyscanner|safari|booking|flight|hotel|reise|travel/],
  ];

  function topics(text) {
    const low = (text || '').toLowerCase();
    const hits = TOPIC_RULES.filter(([, re]) => re.test(low)).map(([name]) => name);
    return hits.length ? hits : ['untagged'];
  }

  // -------------------------------------------------------------- headline
  const REPO_HOSTS = new Set(['huggingface.co', 'github.com', 'codeberg.org', 'gitlab.com']);

  function shortHeadline(title, url) {
    let u;
    try { u = new URL(url); } catch { return title || url; }
    const host = u.hostname.replace(/^www\./, '');
    if (REPO_HOSTS.has(host)) {
      const last = u.pathname.split('/').filter(Boolean).pop();
      if (last) {
        return last.split(/[-_]/).filter(Boolean).map(seg => {
          if (/\d/.test(seg)) return seg;
          if (seg === seg.toLowerCase()) return seg[0].toUpperCase() + seg.slice(1);
          return seg;
        }).join(' ');
      }
    }
    if (!title) return host;
    const stripped = title.replace(/\s*[·|]\s*[^·|]{2,40}$|\s+-\s+[^-]{2,40}$/, '').trim() || title;
    const words = stripped.split(/\s+/).slice(0, 8).join(' ');
    return words.length <= 60 ? words : words.slice(0, 60).trimEnd() + '…';
  }

  // ------------------------------------------------------------ similarity
  const STOP = new Set(['der', 'die', 'das', 'und', 'oder', 'ein', 'eine', 'mit', 'für', 'von',
    'the', 'and', 'for', 'with', 'that', 'this', 'from', 'have', 'has', 'are', 'was', 'were',
    'will', 'can', 'you', 'your', 'its', 'our', 'not', 'but', 'all', 'how', 'why', 'what',
    'when', 'more', 'most', 'into', 'than', 'then', 'them', 'they', 'there', 'here', 'about',
    'www', 'http', 'https', 'com', 'org']);

  function tokenize(text) {
    return (text || '').toLowerCase().split(/[^a-z0-9]+/)
      .filter(t => t.length >= 3 && !STOP.has(t));
  }

  /** Build a normalised TF-IDF vector using the corpus IDF table. */
  function vectorize(text, idf) {
    const tf = {};
    tokenize(text).forEach(t => { tf[t] = (tf[t] || 0) + 1; });
    const vec = {};
    let norm = 0;
    for (const [t, c] of Object.entries(tf)) {
      const w = c * (idf[t] !== undefined ? idf[t] : Math.log(71) + 1);
      vec[t] = w;
      norm += w * w;
    }
    norm = Math.sqrt(norm);
    if (!norm) return {};
    for (const t of Object.keys(vec)) vec[t] /= norm;
    return vec;
  }

  function cosine(a, b) {
    const [small, large] = Object.keys(a).length <= Object.keys(b).length ? [a, b] : [b, a];
    let sum = 0;
    for (const [t, w] of Object.entries(small)) sum += w * (large[t] || 0);
    return sum;
  }

  // ------------------------------------------------------- typed relations
  const STANDARDS = ['mcp', 'model context protocol', 'onnx', 'rag', 'gguf', 'quantization',
    'matryoshka', 'webxr', 'zigbee', 'mqtt', 'wireguard', 'i2s', 'asr', 'text-to-speech',
    'embedding', 'force-directed', 'readability'];

  function standardsIn(text) {
    const low = (text || '').toLowerCase();
    return STANDARDS.filter(s => low.includes(s));
  }

  function orgOf(url) {
    const m = (url + '/').match(/^https:\/\/(?:huggingface\.co|github\.com)\/([^/]+)\//);
    return m ? m[1].toLowerCase() : null;
  }

  return { normalize, unwrap, categorize, topics, shortHeadline, tokenize, vectorize,
           cosine, standardsIn, orgOf };
})();
