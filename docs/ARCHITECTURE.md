# Architektur: Tab-Sammler-App („Pocket, aber mit Hirn")

Stand: Juli 2026. Entscheidungen sind als Empfehlung markiert; alles ist
diskutierbar, bevor Phase 3 startet.

## Zielbild

1. Auf dem Android-Phone einen Link über das Share-Sheet an die App teilen
2. Die App speichert sofort lokal (offline-fähig) und quittiert in <1 s
3. Ein Hintergrund-Worker reichert an: Titel, Beschreibung, Kategorie,
   Topics, Embedding, Dedup-Check gegen Bestand
4. Der Bestand ist als Graph navigierbar (Topics ↔ Links ↔ Hosts ↔
   Ähnlichkeits-Kanten) und hat ein Relevanz-Decay: News altern aus,
   Referenzen bleiben
5. Später: gleiche App auf dem iPad

## Datenmodell (aus Phase 1 validiert)

```
Link:      canonical_url (PK), original_url, host, title?, description?,
           category, topics[], label?, embedding?,
           first_seen, last_seen, n_sightings, status (open|archived|expired),
           relevance_score, decay_class (news|deal|reference|evergreen)
Sighting:  link_id, source (share|pdf_import|browser_export), seen_at
Edge:      src_link, dst_link, type (same_topic|similar|supersedes|same_host), weight
```

Kernerkenntnisse aus der Validierung mit 4 realen Snapshots (2.562 Roh-URLs
→ 1.385 kanonisch):

- ~46 % Duplikat-Quote über Snapshots — Dedup ist Pflicht, funktioniert
  aber nur nach URL-Normalisierung (AMP-Unwrap + Tracking-Param-Strip)
- Implizites Schließen von Tabs zwischen Snapshots ist ein starkes
  Relevanz-Signal (734 von 1.385 URLs „ausgealtert")
- Reine Domain-Heuristik lässt ~25 % in „other" — für gute Kategorien
  braucht es Seiteninhalt + LLM (Phase 2)
- Google-Suchlinks (~3 %) sind ephemer, aber die Query ist ein
  Interessens-Signal → als `search_query` mit Label extrahieren, schnell
  expiren

## Tech-Stack-Empfehlung

### App: Kotlin Multiplatform (KMP) + Compose Multiplatform

- **Android zuerst**, iPad später mit derselben Codebasis (Compose
  Multiplatform für iOS ist seit 1.8, Mai 2025, stabil — vor Phase-4-Start
  aktuellen Stand prüfen)
- Share-Target: `ACTION_SEND`-Intent-Filter (text/plain) → sofort in
  lokale DB, WorkManager-Job für Enrichment einreihen
- Lokale DB: **SQLDelight** (KMP-fähig, SQL-first — passt zum Graph-Schema);
  das MVP nutzt vorerst bewusst plain SQLite ohne Codegen (`LinkStore`),
  der Tausch ist hinter der kleinen Store-API gekapselt
- Hintergrund: WorkManager (Android) mit Constraints (unmetered/charging
  für Batch-Enrichment)

### Enrichment-Compute: Home-Lab zuerst (empfohlen)

| Option | Pro | Contra |
|---|---|---|
| **Home-Lab (empfohlen)** | Daten bleiben privat, kein API-Budget, GPU für Embeddings/LLM | Erreichbarkeit von unterwegs (Tailscale/WireGuard nötig), Wartung |
| Cloud-API (Claude etc.) | beste Qualität der Kategorisierung, kein Betrieb | Kosten, URLs verlassen die eigene Infrastruktur |
| On-Device | offline, privat | nur kleine Modelle; Batterie; Metadaten-Fetch braucht eh Netz |

Pragmatischer Hybrid: App macht nur Fetch von Titel/OpenGraph-Metadaten
(billig, on-device). Volltext-Extraktion, LLM-Kategorisierung, Embeddings
und Graph-Kanten rechnet ein **Home-Lab-Service** (FastAPI + lokales LLM
via Ollama/vLLM oder wahlweise Cloud-API als konfigurierbarem Backend).
Die App synct über eine kleine REST-API (Tailscale macht das von überall
sicher erreichbar, ohne Ports zu öffnen).

### Graph-Navigation

- Kanten: `same_topic` (aus Tags), `similar` (Embedding-Cosine > Schwelle),
  `supersedes` (neuere News zum selben Thema ersetzt ältere → Kandidat
  fürs Aufräumen), `same_host`
- UI: Cluster-Ansicht (Topics als Bubbles) → Drilldown in Listen; volles
  Force-Directed-Layout erst ab Bedarf (1.400+ Knoten auf dem Phone ist
  grenzwertig, Cluster-Aggregation zuerst)
- Export: GraphML/JSON für Obsidian, Gephi o. ä.

### Relevanz-Decay

`relevance = base(category) * exp(-age/halflife(decay_class)) + boost(sightings, pins)`

Halbwertszeiten als Startwerte: news 14 d, deal 7 d, release/announcement
30 d, article/blog 90 d, reference/docs/repo ∞ (kein Decay). Unter
Schwellwert → „Expiry-Inbox": App schlägt Archivierung vor, Nutzer
bestätigt (nie automatisch löschen).

## Sync & Privacy

- Quelle der Wahrheit: SQLite im Home-Lab, App hält lokalen Cache
  (last-write-wins reicht für Single-User)
- Backups verschlüsselt; keine URL-Daten in öffentliche Repos, keine
  Roh-URLs in Logs von Drittdiensten
- PDF-Import (wie in Phase 1) bleibt als Bulk-Import-Pfad in der App
  erhalten (für Alt-Bestände und Browser ohne Share-Export)
