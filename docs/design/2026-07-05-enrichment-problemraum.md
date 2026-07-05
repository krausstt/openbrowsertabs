# Design-Thinking: Problemraum Enrichment-Layer (Phase 2+)

Status: **Empathize/Define — in Diskussion.** Nichts hiervon ist beschlossen;
dieses Dokument sammelt Problem-Hypothesen, offene Fragen an den Nutzer und
den Feasibility-Stand. Es enthält keine persönlichen URLs.

## 1. Problem-Statement (Hypothese — bitte korrigieren!)

Die Rohfassung des Nutzers, verdichtet:

> „Ich finde vieles im Google-Feed interessant, komme aber nicht zum Lesen.
> Wenn ich etwas Neues sehe, suche ich ewig nach zusammenhängenden Artikeln/
> Produkten/Threads, die ich früher schon gesehen habe. Die App soll solche
> Assoziationen selbst herstellen, mit mir brainstormen, mir wöchentlich
> zielorientierte Sammelberichte bauen und meine gesamte Sammlung erfragbar
> machen — ADHS-tauglich, als fehlende Schicht über dem News-Konsum."

**Reframing:** Das ist kein Speicher-Problem (Speichern funktioniert — 700
Tabs beweisen es). Es ist ein **Wiederbegegnungs-Problem** mit drei Facetten:

1. **Capture ohne Consumption:** Sammeln gibt den Dopamin-Kick, Lesen nicht.
   Der Berg erzeugt Schuldgefühl → Vermeidung → mehr Feed-Scrollen. (Klassischer
   ADHS-Zyklus; die App darf Lesen niemals zur Schuld machen.)
2. **Kontext-Amnesie:** Das Gehirn weiß „da war was" (Parakeet! Canary!),
   aber der Wiederfinde-Aufwand ist höher als der Nutzen → es unterbleibt
   oder frisst eine Stunde. Die Assoziation muss zur Bringschuld der App
   werden (Kontext-on-Sight), nicht zur Holschuld des Nutzers.
3. **Relevanz ohne Richtung:** Der Feed optimiert auf Klicks, nicht auf die
   eigenen Life/Career-Goals. Ohne Gegengewicht gewinnt immer das Neueste,
   nie das Wichtigste.

**Ziel-Erlebnis (Hypothese):** Sammeln bleibt ein 1-Sekunden-Reflex ohne
Folgekosten. Die App verwandelt den Berg autonom in (a) sofortigen Kontext
beim Speichern, (b) einen konsumierbaren Wochen-Digest entlang der eigenen
Ziele, (c) einen befragbaren Wissensspeicher. Ungelesenes läuft würdevoll
aus, statt anzuklagen.

## 2. Fragen an den Nutzer (Empathize)

*(Interaktive Dialoge funktionieren in dieser Session nicht — bitte formlos
im Chat beantworten, Stichworte reichen.)*

**F1 — MVP-Fokus:** Wenn nur EINE Sache zuerst perfekt sein darf, welche?
  a) *Kontext-on-Sight* — beim Speichern/Ansehen sofort: „hängt zusammen mit
     X, Y, Z aus deiner Sammlung" + Kurzeinordnung
  b) *Wochen-Digest* — zielorientierter Sammelbericht, der das FOMO beendet
  c) *Frag-deine-Tabs* — Chat über den Gesamtbestand mit Quellenangaben
  d) *Guilt-free Triage* — Now/Later/Never + automatisches Auslaufen

**F2 — Konsum-Momente:** Wann konsumierst du realistisch? (Mehrfachnennung)
  Kurze Mobile-Momente / abends fokussiert (Tablet) / Audio unterwegs
  (TTS-„Podcast" aus dem Digest!) / Wochenend-Deep-Dive

**F3 — Compute-Grenzen:** Streng on-device • Hybrid mit Home-Lab (Empfehlung:
  Phone sammelt + Embeddings, Linux-Rechner crawlt + reichert mit 8-14B-Modell
  an) • Hybrid + punktuell Cloud-Frontier-Modell für Digest/Brainstorming?

**F4 — Goals fürs Gewichtungsprofil:** Welche overarching Life/Career-Goals
  soll der Digest bedienen? (Vermutung aus der Themenverteilung deiner Tabs:
  KI/Agent-Engineering beruflich, Home-Lab/Selfhosting, Maker/Embedded,
  Audio/Musik — bitte präzisieren/gewichten/ergänzen, gern mit 1 Satz pro
  Ziel, was „relevant" für dich dort bedeutet.)

**F5 — Der wunde Punkt (offen):** Beschreib den letzten konkreten Moment, in
  dem dich die Tab-Sammlung geärgert hat. Was hast du gesucht, was hast du
  stattdessen getan, wie viel Zeit hat es gekostet?

## 2b. Research: ADHS-UX (Persona: UX-Researcherin) — ERGEBNIS

*Evidenzlage: überwiegend Praktiker-/Community-Wissen, kognitionspsychologisch
konsistent; keine kontrollierten Studien.*

**Bestätigte Pain Points (Auswahl):**

1. **Tabs sind externalisiertes Arbeitsgedächtnis** — Tab-Hoarding ist eine
   rationale Kompensation für Arbeitsgedächtnis-Defizite; „einfach schließen"
   verlangt genau die Exekutivfunktion, die fehlt.
2. **Collector's Fallacy:** Speichern gibt das Produktivitätsgefühl, Lesen
   nicht; >90 % gespeicherter Links sind laut einem Praxisbericht nach 6
   Monaten tot oder paywalled → Dead-Link-Erkennung ist Pflicht.
3. **Out of sight = out of mind** („Objektpermanenz"): Was im Archiv
   verschwindet, existiert nicht mehr → Resurfacing muss aktiv sein.
4. **Komplexe Taxonomien scheitern strukturell** (Energieschwankungen), nicht
   an Disziplin → Now/Not-Now-Triage, AI übernimmt den Rest.
5. **Markt-Lücke bestätigt:** Nach dem Pocket-Aus (Juli 2025, 20 Mio. Nutzer)
   decken Readwise Reader / Karakeep / Wallabag jeweils Teile ab — **keines
   adressiert die Speichern-ohne-Lesen-Schleife oder verbindet Neues aktiv
   mit Altem.** Auto-Assoziation existiert nur in Nischen-Tools mit ~40 %
   Rausch-Anteil.

**Abgeleitete Design-Prinzipien:** Externalisieren statt disziplinieren ·
Null-Entscheidungs-Erfassung · Now/Not-Now statt Taxonomie · Guilt-free by
design (kein Ungelesen-Zähler!) · Resurfacing als Belohnung (Novelty aus dem
eigenen Bestand) · Konsolidierung mit klarem Ende statt Endlos-Feed.

**Anti-Patterns (nicht bauen):** Ungelesen-Badges, Inbox-Zero-Druck, Streaks
mit Bestrafung, Pflicht-Ordner/Tagging, eigener Endlos-Feed mit
Fremdempfehlungen, 700-Einträge-Listen ohne progressive Disclosure,
Löschzwang-Timer.

Quellen: techcrunch.com (Pocket-Shutdown), differentbrains.org, browser.horse,
dev.to, taskade.medium.com, focuspage.app, simplifyspaceandsoul.com,
psychcentral.com, welcomingweb.com, accessibilitychecker.org, readwise.io,
github.com/karakeep-app, xda-developers.com, burn451.cloud, seroundtable.com,
wikipedia.org/wiki/Doomscrolling, today.ucsd.edu, liminary.io

## 3. Technischer Feasibility-Stand (vorläufig)

*Drei Research-Agenten (Personas: ADHS-UX, On-Device-AI, Ingestion) verifizieren
gerade — dieses Kapitel wird mit deren Ergebnissen aktualisiert. Vorläufige
Einschätzung aus Modellwissen, klar als solche markiert:*

- **„Gemma 4 E2B":** ✅ verifiziert, existiert (siehe 3c) — Nutzer lag
  richtig. Für die Assoziationsschicht ist zusätzlich ein
  On-Device-Embedding-Modell (EmbeddingGemma, ~300M) der eigentliche
  Schlüssel — Ähnlichkeit/Cluster laufen dann in Millisekunden ohne LLM.
- **700 Tabs on-device crawlen:** Grundsätzlich ja, mit menschlichem Pacing
  (2-5 s/Request, Domains interleaved) ≈ 1-2 h einmalig, danach inkrementell
  Sekunden pro Tag. Erwartung: ~80-90 % der Seiten liefern per einfachem
  HTTP-Fetch + Readability sauberen Text; Rest = Paywalls/Cloudflare/Reddit/
  Amazon → WebView-Fallback oder „Metadaten-only". Wichtig: Wir crawlen die
  eigene Leseliste, kein Mass-Scraping — aber einzelne Hosts blocken trotzdem.
- **Screenshots → Vision-LLM:** Möglich, aber als *Fallback*, nicht als
  Standard: Text-Input ist um Größenordnungen schneller/sparsamer und für
  Summaries besser. Screenshot-Pfad nur für Seiten ohne extrahierbaren Text.
- **Supersummary + Tagging on-device (2B-Klasse):** Machbar, aber für 700
  Bestandsartikel eine Frage von Stunden und spürbar Akku — sinnvoll als
  „Neuzugänge nebenbei"-Pfad, nicht für den Bestands-Backfill. Der Backfill
  gehört ins Home-Lab (größeres Modell, bessere Assoziationen, Minuten statt
  Stunden). Genau deshalb Frage F3.
- **YouTube Watch Later:** Die WL-Playlist ist über die offizielle Data API
  seit Jahren **nicht** abrufbar (zu verifizieren, ob unverändert). Legitime
  Wege: Google-Takeout-Export (manuell/periodisch) oder Teilen einzelner
  Videos in die App (funktioniert heute schon!). Transcripts: offizielle Wege
  begrenzt; inoffizielle (yt-dlp etc.) sind ToS-Grauzone → Entscheidung nötig.

## 3a. Research: On-Device-AI (Persona: AI-Engineer) — ERGEBNIS

**Gemma 4 E2B existiert** (Release 02.04.2026, Apache 2.0): ~2,3 Mrd.
effektive Parameter, **128K Kontext**, Input Text+Bild+Video+Audio, explizit
beworben für OCR/Dokument-Parsing/Screen-Verständnis; dank Per-Layer-
Embeddings nur ~2–3 GB Beschleuniger-RAM. (Schwestermodelle: E4B, 12B,
26B-A4B MoE, 31B.)

**Runtime:** MediaPipe LLM Inference API ist **deprecated** — Nachfolger ist
**LiteRT-LM** (Kotlin-API). Gemma 4 E2B: **~52 tok/s Decode auf Android-GPU**
(Referenz S26 Ultra); Qualcomm-NPU via LiteRT-Accelerator >100 tok/s Decode
und >11k tok/s Prefill; für Pixel/Tensor-NPU kein öffentliches Plugin
gefunden (unverifiziert) → dort GPU-Pfad.

**Embeddings:** **EmbeddingGemma (308M)** bleibt Stand der Technik on-device:
<200 MB RAM, 768 Dim (Matryoshka bis 128), ~15–22 ms/Embedding. Für 700
Einträge reicht **Brute-Force-Kosinus — keine Vektor-DB nötig.** Integration
via AI Edge RAG SDK.

**Der entscheidende Überschlag — 700-Artikel-Backfill:**

| | On-device (Gemma 4 E2B, GPU) | Home-Lab (8–14B, RTX-3090-Klasse, vLLM) |
|---|---|---|
| Rechenzeit | ~2–4 h, real mehr (15–40 % Thermal Throttling) | **~15–45 min** (Batching) |
| Energie | ~eine komplette Akkuladung | unkritisch |
| Qualität | 2B-Klasse | deutlich besser (Assoziationen!) |

→ **Empfehlung bestätigt:** On-device = EmbeddingGemma (alle Embeddings) +
Gemma 4 E2B für *inkrementelle* Neuzugänge (wenige/Tag, unproblematisch);
**Backfill + Cross-Artikel-Analysen + Digests = Home-Lab.**

**Screenshots als LLM-Input:** technisch ja (Gemma 4 kann Screen-Verständnis),
**für Artikel nicht sinnvoll** — Viewport statt Volltext, massiv teurer,
fehleranfälliger. Nur Fallback.

Quellen: blog.google (Gemma 4), ai.google.dev/gemma (releases, model card 4,
embeddinggemma, gemma-3n), deepmind.google, huggingface.co/litert-community,
unsloth.ai, ai.google.dev/edge/litert-lm, developers.googleblog.com
(LiteRT-LM, Qualcomm-NPU, EmbeddingGemma), arxiv.org/abs/2509.20354,
arxiv.org/pdf/2410.03613, arxiv.org/html/2603.23640v1

## 3b. Research: Ingestion & YouTube (Persona: Scraping-Engineer) — ERGEBNIS

**700 URLs abrufen: geht mit Einschränkung.** Unser Profil (Residential-/
Mobilfunk-IP, 2–5 s Pacing, gemischte Domains, einmalig ~700 Requests) ist
genau das, was Bot-Detection *nicht* treffen soll. Realer WebView (Chrome-
Engine, JS+Cookies an) besteht JS-Challenges meist; User-Agent nie mitten in
der Session wechseln. **Dauer: 1,5–2,5 h einmalig**, erwartete Ausfallquote
**5–15 %** (Paywalls, Login-Walls, tote Links). Blocker im Detail:
- **Reddit:** 2024/25 massiv verhärtet (`.json`-Trick 2026 weitgehend tot) →
  offizielle Data API mit eigenem OAuth-Account (60 req/min) für gespeicherte
  Threads.
- **Paywalls** (Medium member-only etc.): kein Bot-, sondern Zugriffsproblem
  → Teaser akzeptieren, keine Umgehung.
- **Amazon/Shops:** einzelne Produktseiten aus WebView mit Heim-IP i. d. R.
  ok, gelegentliche CAPTCHAs einplanen.

**Zweistufiger Fetch empfohlen:** OkHttp zuerst (schnell/billig, realer
Chrome-Mobile-UA) → bei Challenge/Leerseite WebView-Fallback → als letzte
Rettung Screenshot (nur visuelles Archiv; Text bräuchte ML-Kit-OCR).

**Extraktion:** Readability.js (im WebView, sieht gerendertes DOM) und
Trafilatura (serverseitig, besser für Batch + Metadaten) sind in Benchmarks
praktisch gleichauf (F1 ~0,9+). → Readability on-device als Primärpfad,
Trafilatura im Home-Lab als Zweitmeinung/Batch.

**YouTube Watch Later:**
- Data API v3: WL-Playlist seit **12.09.2016** abgeschnitten (leere Listen),
  unverändert bis 2026. **Offiziell: geht nicht.**
- Google Takeout: Playlists als CSV, bis zu 6 Exporte/Jahr planbar — **ob WL
  dabei ist, ist unbestätigt/widersprüchlich → mit Test-Export verifizieren
  bevor wir darauf bauen.**
- **Empfohlener Primärweg: Share-Intent** (Video aus YouTube-App in unsere
  App teilen) — ToS-sauber, Echtzeit, funktioniert heute schon.
- Transcripts: offiziell nur für eigene Videos; inoffizielle Wege
  (youtube-transcript-api/yt-dlp) sind ToS-widrig, funktionieren von Heim-IP
  mit Kleinvolumen meist, **nie mit Haupt-Account-Cookies, nie aus der
  Cloud** (Ban-Risiko). → Entscheidung des Nutzers nötig (Entscheidungslog).

**Pacing-/Betriebsregeln:** nur bei WLAN + Laden, randomisiertes Delay,
Domains interleaved, Transcripts ausschließlich vom Home-Lab/Heim-IP.

Quellen: scrapfly.io, zenrows.com, developers.cloudflare.com (Turnstile,
AI-Bot-Defaults), github.com/scrapinghub/article-extraction-benchmark,
trafilatura.readthedocs.io, contextractor.com, developers.google.com/youtube
(Revision History, captions.download), issuetracker.google.com/35172816,
github.com/jdepoix/youtube-transcript-api/issues/511, scrapebadger.com,
portmap.dtinit.org

## 4. Grobe Pipeline-Skizze v0 (wird nach Antworten + Research revidiert)

```
Capture (Phone, <1s)          Enrichment (Ort = F3)             Consumption
─────────────────────         ─────────────────────────         ────────────────────
Share-Sheet / Datei    ──►    Fetch + Readability-Extrakt  ──►  Kontext-on-Sight
  Kurzlink-Resolve            Supersummary + Tags (LLM)         Wochen-Digest (Goals)
  pending_enrichment          Embedding → Assoziations-         Frag-deine-Tabs (RAG)
                              kanten + Cluster                  TTS-Audio-Digest?
                              Relevanz-Decay-Scoring            Expiry-Inbox
```

## 5. Entscheidungslog

| Datum | Entscheidung | Status |
|---|---|---|
| 2026-07-05 | Erst Problemraum & Feasibility, dann Implementierung | beschlossen |
| offen | MVP-Fokus (F1) | wartet auf Nutzer |
| offen | Compute-Topologie (F3) | wartet auf Nutzer |
| offen | YT-Transcript-Weg (ToS-Abwägung: offiziell unmöglich, inoffiziell Grauzone) | wartet auf Nutzer |
| offen | Takeout-Test: enthält der Playlist-Export Watch Later? | Nutzer macht Test-Export |
| 2026-07-05 | YT-Ingestion primär via Share-Intent (ToS-sauber, geht schon) | empfohlen |
| 2026-07-05 | Fetch-Kaskade: OkHttp → WebView+Readability → Screenshot | empfohlen |
| 2026-07-05 | On-device: EmbeddingGemma + Gemma 4 E2B (LiteRT-LM) nur inkrementell; Backfill/Digests im Home-Lab | empfohlen |

---
*Generated by AI (Claude Code Session). Quellen: Nutzerinterview (Chat),
Phase-1-Datenanalyse dieses Repos; Web-Research folgt im nächsten Update.*
