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

## 3. Technischer Feasibility-Stand (vorläufig)

*Drei Research-Agenten (Personas: ADHS-UX, On-Device-AI, Ingestion) verifizieren
gerade — dieses Kapitel wird mit deren Ergebnissen aktualisiert. Vorläufige
Einschätzung aus Modellwissen, klar als solche markiert:*

- **„Gemma 4 E2B":** Zu verifizieren, ob es Stand Juli 2026 ein „Gemma 4"
  im Effective-2B-Format gibt — die etablierte On-Device-Linie ist Gemma 3n
  E2B/E4B (MatFormer, multimodal, läuft via Google AI Edge / MediaPipe LLM
  Inference API). Für die Assoziationsschicht ist zusätzlich ein
  On-Device-Embedding-Modell (EmbeddingGemma-Klasse, ~300M) der eigentliche
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
| offen | YT-Transcript-Weg (ToS-Abwägung) | wartet auf Research + Nutzer |

---
*Generated by AI (Claude Code Session). Quellen: Nutzerinterview (Chat),
Phase-1-Datenanalyse dieses Repos; Web-Research folgt im nächsten Update.*
