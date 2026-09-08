# Second-Brain-Architektur: Recommender-Systeme, Knowledge Graph, NotebookLM-Export

Status: **Recherche abgeschlossen, Architektur-Empfehlung — Bau noch nicht freigegeben.**
Baut auf `docs/design/2026-07-05-enrichment-problemraum.md` auf (dort: Modell-Radar,
Fork A „Multimodal", Fork B „AR-Netzwerk-Graph"). Dieses Dokument macht die
„Second Brain"-Vision technisch konkret und verbindet sie mit den dort schon
geloggten Forks. Grundlage: 4 Recherche-Agenten (Recommender-Systeme,
Knowledge-Graph-Architektur, NotebookLM-Export 2026, Faktencheck realer
Screenshot-Inhalte des Nutzers) — ein erster Anlauf scheiterte an einem
sitzungsweiten API-Limit, weil Sub-Agenten unkontrolliert weitere Agenten
spawnten; der zweite Anlauf (mit expliziter Delegationssperre) lief sauber durch.

## 0. Vision (Nutzer, 2026-07-18/19)

> Einmal gesehen und mit OpenBrowserTabs geteilt heißt für immer im Second
> Brain gespeichert und nützlich bei jeder weiteren Sichtung — exportierbar
> in NotebookLM.

Drei technische Konsequenzen:
1. Jede neue Sichtung muss automatisch gegen den **gesamten** Altbestand
   geprüft werden — nicht nur „ähnlich", sondern mit erklärbarem **Warum**
2. Der Bestand muss sich **verdichten** lassen (Themen-Cluster, Digest),
   statt nur zu wachsen
3. Der Bestand muss die App verlassen können, ohne die Datenhoheit zu verlieren

## 1. Was der Faktencheck über die aktuelle Qualität zeigt (validiertes Beispiel)

Konkret an drei echten Items aus der App geprüft:

- `microsoft/agent-framework-go` ist real der offizielle Go-SDK-Ableger des
  **konsolidierten Nachfolgers von Semantic Kernel + AutoGen** (gleiche
  Teams, beide Vorgänger seit Feb. 2026 im Wartungsmodus) — eine echte,
  belegbare `supersedes`-Abstammungslinie.
- Seine „Hängt zusammen mit"-Verknüpfung zum KDnuggets-Artikel „Qwen3.6 +
  MCPs" ist ein **echter Treffer**: Beide drehen sich tatsächlich um **MCP**
  (Model Context Protocol) als verbindenden technischen Standard.
- Seine Verknüpfung zu `microsoft/VibeVoice-ASR` ist ein **bestätigter
  Fehlalarm**: einzige Gemeinsamkeit ist die Marke „Microsoft" — fachlich
  Agenten-Orchestrierung vs. Sprach-ASR, keine echte Beziehung.

Das ist der empirische Beweis für die zentrale Architektur-Anforderung
dieses Dokuments: **„gleiche Organisation" und „gleiches Thema/gleicher
Standard" müssen getrennte, unterschiedlich gewichtete Kantentypen sein.**
(Nebenbefund: Das Kimi-K3-Video „Beats Fable 5 and GPT-5.6" ist uneingelöste
Übertreibung — Kimi K3 landete real auf Rang 3 hinter beiden im
Artificial-Analysis-Ranking, führte nur in einzelnen Agenten-/Coding-
Benchmarks. Interessanter, aber nicht jetzt zu bauender Gedanke: die App
könnte Video-Behauptungen künftig gegen Benchmark-Daten gegenchecken.)

## 2. Recommender-Architektur

**Collaborative Filtering scheidet aus** — es braucht Signale aus vielen
Nutzern; bei genau einem Nutzer ist die Nutzer-Dimension Kardinalität 1.
Content-based (TF-IDF/Embeddings) und Graph-based Ansätze sind die einzigen
Signalquellen, die überhaupt existieren.

**Session-based/Sequential RecSys** (GRU4Rec, SASRec, BERT4Rec) liefert nur
die **Architektur-Idee** „Ereignis triggert Retrieval" (= unser bestehendes
Kontext-on-Sight) — die zugrundeliegende Transformer-Sequenzmodell-Technik
selbst braucht Trainingsdaten aus vielen Sessions und ist für einen
einzelnen Nutzer mit Hunderten Items **nicht sinnvoll trainierbar**.

**Serendipity ist formal definiert als Relevanz × Unerwartetheit** (Kotkov
et al.). Zentrale, sofort umsetzbare Technik: **MMR-Reranking** (Carbonell/
Goldstein 1998) — `argmax[λ·Relevanz − (1−λ)·max Ähnlichkeit zu bereits
gewählten Items]`. Wichtig: MMR wird in der Literatur **immer als
zusätzlicher Schritt nach** einer Relevanz-Vorauswahl eingesetzt, nie als
Ersatz — bestätigt unser Zwei-Modi-Bedürfnis direkt. Für „strukturell nah,
aber anderes Themencluster" (unser Nemotron→Parakeet/Canary-Muster) liefert
**Seren2vec** (serendipity-gewichteter DeepWalk) das graphbasierte Rezept:
Übergangswahrscheinlichkeiten im Random Walk so verzerren, dass Knoten mit
hoher struktureller Nähe, aber unterschiedlichem Cluster bevorzugt werden.

**Zwei-Modi-Empfehlung:**
- **Kontext-on-Sight** (sofort, bestehendes Verhalten beibehalten): reine
  Relevanz über `similar_to`/`discusses_same_standard`-Kanten, hoher
  Schwellwert, kein Diversity-Boost — Präzision vor Überraschung.
- **Wochen-Digest/Brainstorming-Modus** (neu): MMR-Reranking + gezieltes
  Sampling von Kandidaten jenseits von 1 Hop mit hoher struktureller, aber
  thematisch unterschiedlicher Nähe — hier darf und soll es überraschen.

**Bestehende Tools als Warnbeispiel:** Obsidians native Graph View zeigt nur
explizite Links; echte „Related Notes" kommen aus dem Community-Plugin
*Graph Analysis* via Jaccard-Similarity — **rein strukturell, ohne
Content-Verständnis** — spiegelt unser Problem exakt umgekehrt (Struktur
ohne Inhalt statt Inhalt ohne typisierte Struktur). Recall.ai/Readwise
Ghostreader: konkrete technische Umsetzung nicht aus öffentlicher Doku
verifizierbar — bewusst nicht geraten.

## 3. Knowledge-Graph-Schema

**Entities** (statt nur „Link"): `Link` (bestehend), `Model` (HF-Repo-Pfad
als kanonische ID), `Repo` (GitHub org/repo), `Organization` (schwach,
reines URL-Pfadsegment), `Standard/Topic` (kontrolliertes Vokabular: MCP,
ONNX, RAG, LoRA, WebXR, …), optional `Series` (Modellfamilie, z. B. „Qwen").

**Relations** (typisiert, mit Gewicht + Richtung):

| Kante | Richtung | Zweck |
|---|---|---|
| `similar_to` | ungerichtet | heutige TF-IDF/Embedding-Basis |
| `supersedes`/`successor_of` | gerichtet | Modell-Nachfolge (Wikidata-Vorbild: P1365/P1366) |
| `same_series` | ungerichtet | Gruppierung ohne explizite Nachfolge |
| `cites`/`references` | gerichtet | Artikel verlinkt auf gespeichertes Modell/Paper |
| `same_organization` | ungerichtet, **schwach** | z. B. „beide Microsoft" — NIE mit Themen-Ähnlichkeit vermischen |
| `discusses_same_standard` | ungerichtet, **stark** | z. B. „beide erwähnen MCP" — trägt den echten Qwen3.6-Treffer |

Akademischer Kontext: Es gibt keine direkt übertragbare Standard-Ontologie
für Link-Sammlungen speziell (Personal-Knowledge-Graph-Literatur zielt eher
auf Konversations-/Recommender-Zwecke). Am nächsten dran: „Automated
Extraction of Personal Knowledge from Smartphone Push Notifications"
(arXiv 1808.02013) — nutzt 11 Relationstypen, **regelbasierte Extraktion**
statt schwerer NLP, bestätigt den hier gewählten pragmatischen Weg.

**Extraktion — regelbasiert vor NER/LLM:**
1. **URL-Pfad-Parsing** (kostenlos, deterministisch): `huggingface.co/<org>/<model>`
   bzw. `github.com/<org>/<repo>` → Organization + kanonische Entity, ohne NLP
2. **`supersedes` fast gratis über HF-Metadaten:** Model-Cards tragen bereits
   die Felder `new_version` und `base_model_relation` — da das geplante
   Modell-Radar ohnehin die HF-API abfragt, lässt sich die `supersedes`-Kante
   **ohne zusätzliches Modell** direkt daraus schreiben. Für Nachfolge
   außerhalb von HF (z. B. Nemotron→Parakeet) bleibt nur eine schwächere
   Heuristik (gleiche Task-Kategorie + Namens-Tokenähnlichkeit + neueres
   Datum) — als Vorschlag markiert, nicht als Fakt behandelt.
3. **Gazetteer-Matching für `discusses_same_standard`:** feste Liste von
   ~100–300 Fachbegriffen (MCP, ONNX, RAG, …), Aho-Corasick-Suche über
   Titel/Tags/Content — Sub-Millisekunde pro Link, genau das Muster hinter
   dem korrekten Qwen3.6-Treffer.
4. **`cites/references`:** String-Match, ob im gecachten Artikeltext eine
   URL vorkommt, die bereits als eigener Link in der DB existiert.
5. **On-device NER (GLiNER, ~20–200 MB ONNX) oder Mini-LLM:** nur als
   optionale Stufe 2 für offene Entitäten (Personen, beliebige
   Produktnamen). Für 1.000–5.000 Links deckt Stufe 1 bereits die konkret
   geplanten Features ab — **zurückstellen**, bis ein Feature das wirklich
   braucht.

**Speicherung:** KùzuDB wurde im Oktober 2025 nach der Apple-Übernahme
archiviert (nur Community-Forks, keine bestätigte Android-Unterstützung) —
**nicht empfohlen**. GraphLite zu neu/unbelegt für Produktiveinsatz —
beobachten, nicht adoptieren. Stattdessen bleibt **SQLite** das Backend;
`related_ids` wird durch generische Tabellen ersetzt:

```
entities(id, type, canonical_key, meta_json)
edges(src_id, dst_id, edge_type, weight, directed, evidence, meta_json)
```

Recursive CTEs reichen für Multi-Hop-Traversal bis weit über unseren
1–5k-Knoten-Zielbereich hinaus. Selbst reife PKM-Tools bestätigen den
leichten Ansatz: Obsidian nutzt intern nur einen Link-Cache, keine formale
Graph-DB; Roam/Logseq setzen auf Datomic/Datalog (echtes Triple-Modell) —
beide Extreme meiden wir zugunsten von typisierten Tabellen.

**Ohne Doppelbau für Modell-Radar + AR-Graph:** Modell-Radar schreibt beim
HF-API-Abruf `supersedes`-Kanten in dieselbe `edges`-Tabelle (Evidence-Feld
= `hf:new_version`/`hf:base_model_relation`). Der AR-Netzwerk-Graph liest
dieselbe Tabelle, gruppiert nach `edge_type` für Farbcodierung (schwach/grau
= `same_organization`, kräftig = `supersedes`/`discusses_same_standard`) und
nutzt `weight` als Federkonstante im Force-Layout — **eine
Ingestion-Pipeline, ein Schema, zwei Features.**

## 4. NotebookLM-Export

**Keine Consumer-API.** Seit September 2025 existiert eine offizielle
NotebookLM-Enterprise-API (`google.cloud.notebooklm.v1alpha`,
`notebooks.create`/`notebooks.sources.batchCreate`) — aber ausschließlich
gebunden an **Gemini Enterprise** (Google-Cloud-Business-Lizenz). Für ein
normales privates Google-Konto gibt es keinen programmatischen Push.

**Akzeptierte Quellen/Limits:** PDF, Google Docs/Sheets/Slides, Web-URLs
(nur Text-Scrape), YouTube-Links (nur Untertitel), eingefügter Text, Audio,
Bilder, EPUB (neu März 2026), .docx. Quellen/Notebook: Free 50, Plus 100,
Pro 300, Ultra 500–600; Einzelquelle max. ~500.000 Wörter/200 MB
unabhängig vom Plan.

**Empfohlener Weg (kein API-Feature versuchen):**
1. **„NotebookLM-Export"-Button:** pro Themen-Cluster/Wochen-Digest ein
   sauber strukturiertes Markdown-/PDF-Bundle erzeugen (klare
   H2/H3-Hierarchie, Executive Summary zuerst, Quellenliste am Ende,
   <100 Seiten/Datei für gute Zitierbarkeit) — Übergabe per
   Android-Share-Intent, Upload bleibt ein manueller 10-Sekunden-Schritt.
2. **Für ein „lebendes", automatisch aktuelles Notebook:** Digest über die
   **offizielle Google-Docs-API** (stabil, kein NotebookLM-spezifisches
   Risiko) in ein Drive-Dokument schreiben. NotebookLMs seit Mai 2026
   existierender automatischer Drive-Sync übernimmt Updates von selbst —
   kein zusätzlicher Baustein nötig außer der Digest-Schreib-Schnittstelle,
   die wir ohnehin brauchen.
3. Viele **kleine themenclusterbasierte Notebooks** statt eines
   Mega-Notebooks passt strukturell zu unserem Themen-Cluster-Ansatz und
   umgeht die Quellen-Limits pro Notebook.
4. Öffentliches Notebook-Sharing existiert seit Aug. 2025 (bis 50
   Betrachter, nur Chat/Audio-Interaktion) — für später relevant, falls
   Digests geteilt werden sollen.

**Nicht verifizierbar:** ob Drive-Auto-Sync auch Audio-Overviews
automatisch neu generiert (vermutlich nur Text-Update); exakte
Enterprise-API-Buchbarkeit für Einzelpersonen.

## 5. Zusammengeführte Architektur

```
Capture (bestehend)     Enrichment (bestehend + neu)         Second Brain
────────────────────    ──────────────────────────────       ─────────────────────────
Share-Sheet       ──►   Fetch+Extrakt (bestehend)      ──►   Kontext-on-Sight:
                        Regelbasierte Entity-Extraktion       reine Relevanz, hoher
                        (URL-Pfad, HF-Metadaten,               Schwellwert (bestehend)
                        Gazetteer für Standards)
                        → entities + edges (typisiert)   ──►  Wochen-Digest/Brainstorm:
                                                                MMR-Reranking + Graph-
                                                                Sampling >1 Hop (neu)
                                                          ──►  Modell-Radar: liest
                                                                supersedes-Kanten
                                                          ──►  AR-Netzwerk-Graph: liest
                                                                edge_type für Farbe/Gewicht
                                                          ──►  NotebookLM-Export: Digest-
                                                                Text → Markdown-Bundle
                                                                oder Drive-Doc (Auto-Sync)
```

Der Kern-Umbau ist eine einzige DB-Migration (`related_ids` →
`entities`/`edges`-Tabellen), die **vier** bereits designte/gewünschte
Features gleichzeitig freischaltet, statt für jedes einzeln Sonderlogik zu
bauen.

## 6. Offene Entscheidungen (vor dem Bau)

| Frage | Optionen |
|---|---|
| Reihenfolge | Erst DB-Schema (entities/edges) + regelbasierte Extraktion, dann MMR/Digest, dann Modell-Radar/AR-Graph obendrauf? |
| Gazetteer-Liste | Wer pflegt die ~100–300 Standard-Begriffe (MCP, ONNX, …) — statisch im Code oder nachträglich erweiterbar? |
| NotebookLM-Trigger | Export nur auf Nutzer-Tap, oder automatisch bei jedem Wochen-Digest mitgeneriert? |
| Digest-Ziel | Markdown-Datei (einfach) zuerst, Google-Docs-API-Pfad (für Auto-Sync) später? |

## Quellen

**Recommender-Systeme:** arxiv.org/html/2502.10157, arxiv.org/pdf/2407.13699,
dl.acm.org/doi/10.1145/2926720, researchgate.net (Serendipity-Survey,
Seren2vec via peerj.com/articles/cs-178, SMMR-Reranking), arxiv.org/pdf/2001.05324,
github.com/SkepticMystic/graph-analysis, obsidian.md/help/plugins/graph,
getrecall.ai/blog, docs.readwise.io/reader

**Knowledge Graph:** arxiv.org/abs/2304.09572, wires.onlinelibrary.wiley.com
(PKG-Survey), arxiv.org/pdf/2402.07540, arxiv.org/pdf/1808.02013,
microsoft.com/research (MyLifeBits), wikidata.org (P1365/P1366),
huggingface.co/docs/hub/model-cards, github.com/urchade/GLiNER,
github.com/kuzudb/kuzu (archiviert), dev.to (SQLite-as-Graph-DB),
vasturiano.github.io/3d-force-graph-ar

**NotebookLM:** docs.cloud.google.com/gemini/enterprise/notebooklm-enterprise,
support.google.com/gemininotebook, elephas.app, notebooklm-guide.com,
blog.google, workspaceupdates.googleblog.com (Drive-Auto-Sync, 26.05.2026),
felloai.com, googally.com

**Faktencheck:** bloomberg.com, marktechpost.com, simonwillison.net,
openai.com, techcrunch.com, anthropic.com, venturebeat.com,
technologyreview.com, github.com/microsoft/agent-framework-go,
devblogs.microsoft.com, huggingface.co/microsoft/VibeVoice-ASR,
github.com/QwenLM/Qwen3.6, kdnuggets.com

---
*Generated by AI (Claude Code Session, 4 Recherche-Agenten). Quellen wie
oben je Abschnitt gelistet.*
