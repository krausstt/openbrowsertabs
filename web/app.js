/* Demo application: live enrichment, typed-edge case study, force graph, browser. */
(() => {
  'use strict';

  const EDGE_TYPES = {
    supersedes:              { label: 'supersedes',        color: '#4a3aa7', dark: '#9085e9', w: 2.2 },
    discusses_same_standard: { label: 'same standard',     color: '#1baf7a', dark: '#199e70', w: 1.8 },
    similar_to:              { label: 'content similarity', color: '#2a78d6', dark: '#3987e5', w: 1.2 },
    same_organization:       { label: 'same organisation', color: '#9a9a92', dark: '#6d6d66', w: 0.6 },
  };

  const CATEGORY_LABEL = {
    article: 'Article', blog: 'Blog', repo: 'Repo', model_or_dataset: 'Model',
    shopping: 'Shopping', discussion: 'Discussion', travel: 'Travel',
    search_query: 'Search', docs: 'Docs', video: 'Video', paper: 'Paper', other: 'Other',
  };

  const TOPIC_LABEL = {
    llm_agents: 'AI/LLM', coding_devops: 'Coding', embedded_iot: 'Embedded',
    hardware: 'Hardware', data_science: 'Data', audio_music: 'Audio',
    gaming: 'Gaming', health: 'Health', travel: 'Travel', untagged: 'untagged',
  };

  const PRESETS = {
    mcp: {
      url: 'https://www.kdnuggets.com/wiring-local-tools-into-agents-with-mcp',
      title: 'Wiring local tools into agents with MCP',
      text: 'Walkthrough of exposing local scripts as Model Context Protocol servers so an agent framework can call them as tools.',
    },
    esp: {
      url: 'https://www.hackster.io/news/esp32-s3-wake-word-badge',
      title: 'An ESP32-S3 badge that listens for a wake word',
      text: 'Build log wiring an I2S microphone to an ESP32-S3, running a tiny on-device model and reporting to Home Assistant over MQTT.',
    },
    shop: {
      url: 'https://www.reichelt.de/de/de/shop/produkt/xiao_esp32s3_sense-402198?utm_source=deal',
      title: 'Seeed XIAO ESP32S3 Sense with camera',
      text: 'Compact development board bundling camera and microphone for edge machine learning prototypes.',
    },
    noise: {
      url: 'https://www.bbcgoodfood.com/recipes/classic-lasagne',
      title: 'Classic lasagne recipe',
      text: 'Layered pasta bake with slow-cooked beef ragu, béchamel sauce and plenty of parmesan.',
    },
  };

  let DATA = null;
  let typedEdges = true;

  const $ = sel => document.querySelector(sel);
  const dark = () => window.matchMedia('(prefers-color-scheme: dark)').matches;
  const edgeColor = type => dark() ? EDGE_TYPES[type].dark : EDGE_TYPES[type].color;

  // ============================================================ live demo
  function enrich(url, title, text) {
    const canonical = OBT.normalize(url);
    const blob = `${title} ${text} ${canonical}`;
    const category = OBT.categorize(canonical);
    const tags = OBT.topics(blob);
    const headline = OBT.shortHeadline(title, canonical);
    const standards = OBT.standardsIn(blob);
    const org = OBT.orgOf(canonical);

    // score against the corpus with the shipped IDF table
    const vec = OBT.vectorize(`${title} ${text} ${tags.join(' ')}`, DATA.idf);
    const scored = DATA.nodes.map((node, i) => {
      const sim = OBT.cosine(vec, DATA.doc_vectors[i]);
      const shared = standards.filter(s => node.standards.includes(s));
      const sameOrg = org && node.org === org;
      let type = 'similar_to';
      let weight = sim;
      let evidence = `cosine ${sim.toFixed(2)}`;
      if (shared.length) {
        type = 'discusses_same_standard';
        weight = Math.max(sim, Math.min(1, 0.45 + 0.2 * shared.length));
        evidence = 'shared: ' + shared.join(', ');
      } else if (sameOrg && sim < 0.25) {
        type = 'same_organization';
        weight = Math.max(sim, 0.2);
        evidence = `both ${org}`;
      }
      return { node, type, weight, evidence, sim };
    });

    const related = scored
      .filter(r => (typedEdges && r.type !== 'similar_to') ? r.weight >= 0.3 : r.sim >= 0.08)
      .sort((a, b) => b.weight - a.weight)
      .slice(0, 3);

    return { canonical, category, tags, headline, standards, org, related, url };
  }

  function renderDemo() {
    const res = enrich($('#url-in').value, $('#title-in').value, $('#text-in').value);

    $('#n-title').textContent = res.headline;
    const tagLine = [CATEGORY_LABEL[res.category] || res.category]
      .concat(res.tags.filter(t => t !== 'untagged').slice(0, 2).map(t => TOPIC_LABEL[t] || t))
      .join(' · ');
    const body = [`🏷 ${tagLine}`];
    if (res.related.length) {
      body.push('🔗 Related: ' + res.related.map(r => OBT.shortHeadline(r.node.title, r.node.url)).join(' · '));
    } else {
      body.push('🔗 Nothing related in your collection yet');
    }
    $('#n-body').innerHTML = body.map(l => `<div>${escapeHtml(l)}</div>`).join('');

    const steps = [
      ['Canonicalised', res.canonical !== res.url
        ? `<s>${escapeHtml(trim(res.url, 60))}</s> → <code>${escapeHtml(trim(res.canonical, 60))}</code>`
        : `<code>${escapeHtml(trim(res.canonical, 70))}</code>`],
      ['Category', `<span class="pill">${CATEGORY_LABEL[res.category] || res.category}</span>`],
      ['Topics', res.tags.map(t => `<span class="pill">${TOPIC_LABEL[t] || t}</span>`).join(' ')],
      ['Standards detected', res.standards.length
        ? res.standards.map(s => `<span class="pill strong">${escapeHtml(s)}</span>`).join(' ')
        : '<span class="muted">none</span>'],
      ['Associations', res.related.length
        ? res.related.map(r => `
            <div class="assoc">
              <span class="edge-dot" style="background:${edgeColor(r.type)}"></span>
              <a href="${escapeHtml(r.node.url)}" target="_blank" rel="noopener">${escapeHtml(r.node.title)}</a>
              <span class="assoc-meta">${EDGE_TYPES[r.type].label} · ${escapeHtml(r.evidence)}</span>
            </div>`).join('')
        : '<span class="muted">below threshold — deliberately shows nothing rather than guessing</span>'],
    ];
    $('#pipeline').innerHTML = steps.map(([k, v]) =>
      `<div class="step"><div class="step-k">${k}</div><div class="step-v">${v}</div></div>`).join('');
  }

  // ======================================================= case study
  function renderCase() {
    const byTitle = t => DATA.nodes.find(n => n.title === t);
    const af = byTitle('microsoft/agent-framework');
    const vibe = byTitle('microsoft/VibeVoice-1.5B');
    const mcp = byTitle('Model Context Protocol documentation');
    if (!af || !vibe || !mcp) return;

    const find = (a, b) => DATA.edges.find(e =>
      (e.source === a.id && e.target === b.id) || (e.source === b.id && e.target === a.id));

    const rows = [
      { other: vibe, edge: find(af, vibe), verdict: 'false positive' },
      { other: mcp, edge: find(af, mcp), verdict: 'genuine' },
    ];

    $('#case-study').innerHTML = `
      <p class="case-anchor">Starting from <code>${escapeHtml(af.title)}</code>:</p>
      ${rows.map(r => {
        const type = r.edge ? r.edge.type : 'similar_to';
        const shown = typedEdges ? EDGE_TYPES[type].label : 'related';
        const color = typedEdges ? edgeColor(type) : edgeColor('similar_to');
        const strength = typedEdges
          ? (type === 'same_organization' ? 'weak' : 'strong')
          : 'equal weight';
        return `
        <div class="case-row ${typedEdges && type === 'same_organization' ? 'demoted' : ''}">
          <span class="edge-dot" style="background:${color}"></span>
          <div>
            <div class="case-title">${escapeHtml(r.other.title)}</div>
            <div class="case-meta">
              <span class="pill" style="border-color:${color}">${escapeHtml(shown)}</span>
              <span class="muted">${escapeHtml(r.edge ? r.edge.evidence : 'content overlap')} · ${strength}</span>
            </div>
          </div>
          <span class="verdict ${r.verdict === 'genuine' ? 'good' : 'bad'}">${r.verdict}</span>
        </div>`;
      }).join('')}
      <p class="fine">${typedEdges
        ? 'With typed edges the organisation match still exists, but it can never outrank a shared-standard link.'
        : 'Without types both look identical to the ranker — the false positive competes with the real insight.'}</p>`;
  }

  // ============================================================== graph
  const canvas = $('#graph');
  const ctx = canvas.getContext('2d');
  let sim = { nodes: [], edges: [] };
  let hidden = new Set();
  let selected = null;
  let drag = null;
  let raf = null;

  function initGraph() {
    const w = canvas.clientWidth, h = canvas.clientHeight;
    sim.nodes = DATA.nodes.map(n => ({
      ...n,
      x: w / 2 + (Math.random() - 0.5) * w * 0.7,
      y: h / 2 + (Math.random() - 0.5) * h * 0.7,
      vx: 0, vy: 0,
      deg: 0,
    }));
    sim.edges = DATA.edges.map(e => ({ ...e }));
    sim.edges.forEach(e => { sim.nodes[e.source].deg++; sim.nodes[e.target].deg++; });
    tick(260);
  }

  function step() {
    const w = canvas.clientWidth, h = canvas.clientHeight;
    const nodes = sim.nodes;
    // repulsion (O(n²) is fine at 70 nodes); tuned to spread the hairball
    for (let i = 0; i < nodes.length; i++) {
      for (let j = i + 1; j < nodes.length; j++) {
        const a = nodes[i], b = nodes[j];
        let dx = b.x - a.x, dy = b.y - a.y;
        let d2 = dx * dx + dy * dy || 0.01;
        if (d2 > 62500) continue;
        const d = Math.sqrt(d2);
        const f = 1250 / Math.max(d2, 400);
        const fx = f * dx / d, fy = f * dy / d;
        a.vx -= fx; a.vy -= fy; b.vx += fx; b.vy += fy;
      }
    }
    // springs — weak similarity edges pull much less than typed ones
    for (const e of sim.edges) {
      if (hidden.has(e.type)) continue;
      const a = nodes[e.source], b = nodes[e.target];
      const dx = b.x - a.x, dy = b.y - a.y;
      const d = Math.hypot(dx, dy) || 0.01;
      const typedBoost = e.type === 'similar_to' ? 0.55 : 1.4;
      const rest = 95 + (1 - e.weight) * 110;
      const f = (d - rest) * 0.0013 * (0.3 + e.weight) * typedBoost;
      const fx = f * dx / d, fy = f * dy / d;
      a.vx += fx; a.vy += fy; b.vx -= fx; b.vy -= fy;
    }
    // centring + integration (elliptical pull keeps the cloud off the walls)
    for (const n of nodes) {
      n.vx += (w / 2 - n.x) * 0.0042;
      n.vy += (h / 2 - n.y) * 0.0060;
      n.vx *= 0.85; n.vy *= 0.85;
      if (drag !== n) { n.x += n.vx; n.y += n.vy; }
      n.x = Math.max(26, Math.min(w - 26, n.x));
      n.y = Math.max(26, Math.min(h - 26, n.y));
    }
  }

  function draw() {
    const w = canvas.clientWidth, h = canvas.clientHeight;
    const dpr = window.devicePixelRatio || 1;
    if (canvas.width !== w * dpr || canvas.height !== h * dpr) {
      canvas.width = w * dpr; canvas.height = h * dpr;
    }
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, w, h);

    for (const e of sim.edges) {
      if (hidden.has(e.type)) continue;
      const a = sim.nodes[e.source], b = sim.nodes[e.target];
      const focus = selected !== null && (e.source === selected || e.target === selected);
      ctx.globalAlpha = selected === null ? (e.type === 'same_organization' ? 0.35 : 0.6) : (focus ? 0.95 : 0.07);
      ctx.strokeStyle = edgeColor(e.type);
      ctx.lineWidth = EDGE_TYPES[e.type].w * (focus ? 1.6 : 1) * (0.5 + e.weight * 0.7);
      ctx.beginPath();
      ctx.moveTo(a.x, a.y);
      ctx.lineTo(b.x, b.y);
      ctx.stroke();
    }
    ctx.globalAlpha = 1;

    // draw nodes, then labels with collision avoidance so the centre stays legible
    const labels = [];
    for (const n of sim.nodes) {
      const r = 4 + Math.min(7, n.deg * 0.45);
      const isSel = selected === n.id;
      const near = selected !== null && sim.edges.some(e => !hidden.has(e.type) &&
        ((e.source === selected && e.target === n.id) || (e.target === selected && e.source === n.id)));
      ctx.globalAlpha = selected === null || isSel || near ? 1 : 0.2;
      ctx.beginPath();
      ctx.arc(n.x, n.y, isSel ? r + 3 : r, 0, Math.PI * 2);
      ctx.fillStyle = dark() ? '#e8e8e2' : '#2b2b28';
      ctx.fill();
      if (isSel) {
        ctx.lineWidth = 2.5;
        ctx.strokeStyle = edgeColor('similar_to');
        ctx.stroke();
      }
      if (isSel || near || (selected === null && n.deg >= 12)) {
        labels.push({ n, r, priority: isSel ? 3 : near ? 2 : 1 });
      }
    }
    ctx.globalAlpha = 1;

    ctx.font = '600 11px ui-sans-serif, system-ui, sans-serif';
    ctx.textAlign = 'center';
    const placed = [];
    labels.sort((a, b) => b.priority - a.priority || b.n.deg - a.n.deg);
    for (const { n, r } of labels) {
      const text = trim(OBT.shortHeadline(n.title, n.url), 24);
      const width = ctx.measureText(text).width;
      const box = { x1: n.x - width / 2 - 3, x2: n.x + width / 2 + 3,
                    y1: n.y - r - 16, y2: n.y - r - 3 };
      if (placed.some(p => !(box.x2 < p.x1 || box.x1 > p.x2 || box.y2 < p.y1 || box.y1 > p.y2))) continue;
      placed.push(box);
      ctx.fillStyle = dark() ? 'rgba(19,19,22,.82)' : 'rgba(251,251,250,.85)';
      ctx.fillRect(box.x1, box.y1, box.x2 - box.x1, box.y2 - box.y1);
      ctx.fillStyle = dark() ? '#d6d6cf' : '#3d3d3a';
      ctx.fillText(text, n.x, n.y - r - 6);
    }
  }

  function tick(frames) {
    cancelAnimationFrame(raf);
    let left = frames;
    const loop = () => {
      step(); draw();
      if (--left > 0) raf = requestAnimationFrame(loop);
    };
    loop();
  }

  function nodeAt(x, y) {
    let best = null, bestD = 18;
    for (const n of sim.nodes) {
      const d = Math.hypot(n.x - x, n.y - y);
      if (d < bestD) { bestD = d; best = n; }
    }
    return best;
  }

  function pos(evt) {
    const r = canvas.getBoundingClientRect();
    const p = evt.touches ? evt.touches[0] : evt;
    return { x: p.clientX - r.left, y: p.clientY - r.top };
  }

  function showNodeCard(n) {
    const card = $('#node-card');
    if (!n) { card.hidden = true; return; }
    const rel = sim.edges
      .filter(e => !hidden.has(e.type) && (e.source === n.id || e.target === n.id))
      .sort((a, b) => b.weight - a.weight).slice(0, 6);
    card.hidden = false;
    card.innerHTML = `
      <button class="close" aria-label="Close">×</button>
      <h4>${escapeHtml(n.title)}</h4>
      <p class="node-desc">${escapeHtml(n.description)}</p>
      <p class="node-meta">
        <span class="pill">${CATEGORY_LABEL[n.category] || n.category}</span>
        ${n.topics.filter(t => t !== 'untagged').map(t => `<span class="pill">${TOPIC_LABEL[t] || t}</span>`).join(' ')}
      </p>
      <p class="node-links">${rel.map(e => {
        const other = sim.nodes[e.source === n.id ? e.target : e.source];
        return `<span class="assoc"><span class="edge-dot" style="background:${edgeColor(e.type)}"></span>
          ${escapeHtml(trim(other.title, 40))}
          <span class="assoc-meta">${EDGE_TYPES[e.type].label}</span></span>`;
      }).join('')}</p>
      <a class="btn small" href="${escapeHtml(n.url)}" target="_blank" rel="noopener">Open source ↗</a>`;
    card.querySelector('.close').onclick = () => { selected = null; card.hidden = true; tick(1); };
  }

  function renderLegend() {
    $('#legend').innerHTML = Object.entries(EDGE_TYPES).map(([key, v]) => {
      const count = DATA.edges.filter(e => e.type === key).length;
      return `<button class="legend-item" data-type="${key}">
        <span class="edge-line" style="background:${edgeColor(key)};height:${v.w}px"></span>
        ${v.label} <span class="legend-count">${count}</span>
      </button>`;
    }).join('');
    $('#legend').querySelectorAll('.legend-item').forEach(btn => {
      btn.onclick = () => {
        const t = btn.dataset.type;
        if (hidden.has(t)) { hidden.delete(t); btn.classList.remove('off'); }
        else { hidden.add(t); btn.classList.add('off'); }
        tick(90);
      };
    });
  }

  // ============================================================= browser
  function renderList() {
    const q = $('#search').value.trim().toLowerCase();
    const cat = document.querySelector('#cat-chips .chip.on');
    const catKey = cat ? cat.dataset.cat : null;
    const rows = DATA.nodes.filter(n => {
      if (catKey && n.category !== catKey) return false;
      if (!q) return true;
      return (n.title + ' ' + n.host + ' ' + n.description + ' ' + n.topics.join(' ')).toLowerCase().includes(q);
    });
    $('#count').textContent = `${rows.length} of ${DATA.nodes.length} links`;
    $('#link-list').innerHTML = rows.map(n => `
      <li>
        <a href="${escapeHtml(n.url)}" target="_blank" rel="noopener">${escapeHtml(n.title)}</a>
        <p class="li-desc">${escapeHtml(trim(n.description, 140))}</p>
        <p class="li-meta">
          <span class="pill">${CATEGORY_LABEL[n.category] || n.category}</span>
          ${n.topics.filter(t => t !== 'untagged').map(t => `<span class="pill ghost">${TOPIC_LABEL[t] || t}</span>`).join(' ')}
          <span class="muted">${escapeHtml(n.host)}</span>
        </p>
      </li>`).join('');
  }

  function renderCatChips() {
    const counts = {};
    DATA.nodes.forEach(n => { counts[n.category] = (counts[n.category] || 0) + 1; });
    $('#cat-chips').innerHTML = Object.entries(counts)
      .sort((a, b) => b[1] - a[1])
      .map(([c, n]) => `<button class="chip" data-cat="${c}">${CATEGORY_LABEL[c] || c} <span class="muted">${n}</span></button>`)
      .join('');
    $('#cat-chips').querySelectorAll('.chip').forEach(chip => {
      chip.onclick = () => {
        const was = chip.classList.contains('on');
        $('#cat-chips').querySelectorAll('.chip').forEach(c => c.classList.remove('on'));
        if (!was) chip.classList.add('on');
        renderList();
      };
    });
  }

  // ============================================================== helpers
  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, c =>
      ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }
  function trim(s, n) { return s.length > n ? s.slice(0, n - 1) + '…' : s; }

  // ================================================================ wire-up
  fetch('data.json').then(r => r.json()).then(data => {
    DATA = data;

    $('#run-btn').onclick = renderDemo;
    ['#url-in', '#title-in', '#text-in'].forEach(sel => {
      $(sel).addEventListener('input', renderDemo);
    });
    document.querySelectorAll('[data-preset]').forEach(btn => {
      btn.onclick = () => {
        const p = PRESETS[btn.dataset.preset];
        $('#url-in').value = p.url;
        $('#title-in').value = p.title;
        $('#text-in').value = p.text;
        renderDemo();
      };
    });
    renderDemo();

    $('#typed-toggle').onclick = () => {
      typedEdges = !typedEdges;
      $('#typed-toggle').innerHTML = `Typed edges: <strong>${typedEdges ? 'on' : 'off'}</strong>`;
      $('#toggle-hint').textContent = typedEdges
        ? 'Organisation matches are demoted to a weak, grey edge.'
        : 'Every connection is one undifferentiated "related" score.';
      renderCase();
      renderDemo();
    };
    renderCase();

    renderLegend();
    initGraph();
    renderCatChips();
    renderList();
    $('#search').addEventListener('input', renderList);

    canvas.addEventListener('pointerdown', e => {
      const p = pos(e);
      const n = nodeAt(p.x, p.y);
      if (n) { drag = n; selected = n.id; showNodeCard(n); canvas.setPointerCapture(e.pointerId); }
      else { selected = null; showNodeCard(null); }
      tick(120);
    });
    canvas.addEventListener('pointermove', e => {
      if (!drag) return;
      const p = pos(e);
      drag.x = p.x; drag.y = p.y; drag.vx = 0; drag.vy = 0;
      tick(2);
    });
    canvas.addEventListener('pointerup', () => { drag = null; tick(120); });
    window.addEventListener('resize', () => tick(120));
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
      renderLegend(); renderCase(); renderDemo(); tick(2);
    });
  }).catch(err => {
    $('#pipeline').innerHTML = `<p class="muted">Demo data could not be loaded: ${escapeHtml(err.message)}</p>`;
  });
})();
