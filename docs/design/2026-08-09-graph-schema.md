# Graph-Schema & harmonisiertes Metadaten-Format

Status: **Schema v6 gebaut, Cloud-Pfad spezifiziert.**
Baut auf `docs/design/2026-07-19-second-brain-architektur.md` auf und macht
dessen `entities`/`edges`-Empfehlung konkret.

## 1. Anforderung (Nutzer, 09.08.)

> „Erst das Framework für die Erstellung des DB-Schemas, da sie sehr dynamisch
> und flexibel sein muss, um alle möglichen Arten von Relationen zu capturen
> inklusive Timestamps vom Save-Datum oder Autoren oder Paradigmen … Es sind
> nicht nur Modellseiten wie CanaryQwen auf HuggingFace, es sind ALLE Arten
> menschenlesbarer Websites, auch Kochrezepte oder lokale oder hochaktuelle
> Infoseiten."

Plus: lokale Anreicherung (beim Teilen) und Cloud-Batch-Anreicherung müssen
**dasselbe Format** sprechen und auf **denselben Pool** von Tags und Kanten
zugreifen. Und: der Gazetteer muss dynamisch erweiterbar sein.

## 2. Warum nicht einfach mehr Spalten

Die bisherige `links`-Tabelle hat feste Spalten. Jedes neue Attribut
(Autor, Kochzeit, Portionen, Veranstaltungsdatum, Preis, Paradigma …) wäre
eine Migration — und für ein Kochrezept sind „Portionen" sinnvoll, für eine
Modellkarte „Parameterzahl", für eine lokale Infoseite „Gültig bis". Ein
festes Schema für „alle menschenlesbaren Websites" gibt es nicht.

Deshalb: **Property-Graph mit offenen Vokabularen.** Neue Attributarten und
neue Relationsarten sind *Daten*, keine Schemaänderung.

## 3. Das Schema (v6)

```sql
entities(id, kind, canonical_key, label, link_id, created_at, updated_at)
    kind: link | person | org | model | standard | topic | place | ingredient | …
    canonical_key: stabile Identität ("https://…", "org:nvidia", "standard:mcp")
    UNIQUE(kind, canonical_key)

entity_attrs(id, entity_id, key, value_text, value_num, value_time,
             source, confidence, observed_at)
    key: author | published_at | paradigm | cuisine | serves | valid_until | …
    UNIQUE(entity_id, key, source)     -- Quellen koexistieren, siehe 4.

edges(id, src_id, dst_id, type, weight, directed, evidence,
      source, confidence, created_at)
    type: similar_to | supersedes | same_organization | discusses_same_standard
        | cites | authored_by | same_series | same_cluster | …
    UNIQUE(src_id, dst_id, type, source)

vocab(id, kind, term, canonical, source, hits, added_at)
    der dynamische Gazetteer — siehe 5.
    UNIQUE(kind, term)

clusters(id, label, method, run_id, size, created_at)
cluster_members(cluster_id, entity_id, score)
```

Drei Spalten für Werte (`value_text`, `value_num`, `value_time`) statt einer:
so bleibt „vor 2024 veröffentlicht" oder „unter 30 Minuten" als
SQL-Vergleich möglich, ohne Strings zu parsen.

## 4. Provenienz: der Kern der Harmonisierung

Jedes Attribut und jede Kante trägt `source` und `confidence`:

| source | wer | Beispiel |
|---|---|---|
| `user` | du, von Hand | eigene Tags, eigene Zusammenfassung |
| `cloud_batch` | Batch-Job in der Cloud | Embedding-Cluster, LLM-Attribute |
| `local_llm` | On-Device-Modell (später) | Kurz-Zusammenfassung |
| `local_rules` | Regeln beim Teilen | Kategorie, Slug-Titel, Gazetteer-Treffer |

**Regel: Quellen überschreiben einander nie, sie koexistieren.** Das
`UNIQUE(entity_id, key, source)` erlaubt denselben Schlüssel aus mehreren
Quellen; beim Lesen gewinnt die Präzedenz `user > cloud_batch > local_llm >
local_rules`. Konsequenzen:

- Ein Cloud-Lauf kann **nie** deine Handarbeit zerstören.
- Ein Cloud-Lauf ist **idempotent** und beliebig wiederholbar.
- Man kann jederzeit fragen „woher weiß die App das?" — und eine schlechte
  Quelle gezielt löschen, ohne alles neu zu rechnen.

Das ist genau die Eigenschaft, die lokale und Cloud-Anreicherung auf einen
gemeinsamen Pool schreiben lässt, ohne sich gegenseitig zu zerstören.

## 5. Dynamischer Gazetteer

Die feste Begriffsliste („MCP", „ONNX", …) wandert aus dem Code in die
`vocab`-Tabelle. Damit:

- **Seed** aus dem Code beim ersten Start (bekannte Standards/Topics)
- **Erweiterung durch den Cloud-Batch**: häufige, aussagekräftige Terme aus
  den Clustern werden als Vorschlag mit `source='cloud_batch'` eingetragen
- **Erweiterung durch dich**: jeder von Hand vergebene Tag landet als
  `source='user'` im Vokabular und wird ab dann automatisch mitgematcht
- `hits` zählt Treffer → nie getroffene Begriffe können aufgeräumt werden

Für Kochrezepte heißt das konkret: Der Batch entdeckt „sous-vide",
„meal-prep", „vegan" als wiederkehrende Terme und trägt sie ein — ohne dass
jemand vorher eine Kochvokabelliste geschrieben hat.

## 6. Interchange-Format (lokal ⇄ Cloud)

Eine JSON-Datei, die beide Seiten lesen und schreiben. Bewusst
zeilenorientiert (JSONL) für die großen Teile, damit der Cloud-Job streamen
kann.

**Export (Phone → Cloud), `export.jsonl`:**
```json
{"type":"meta","schema":6,"exported_at":1786000000,"count":1385}
{"type":"node","id":42,"kind":"link","key":"https://…","label":"Qwen …",
 "host":"golem.de","category":"article","tags":["llm_agents"],
 "text":"…extrahierter Text, gekürzt…","summary":"…","saved_at":1785000000,
 "attrs":[{"key":"published_at","time":1784000000,"source":"local_rules"}]}
```

**Import (Cloud → Phone), `enrich.jsonl`:**
```json
{"type":"meta","run_id":"2026-08-09T12:00Z","source":"cloud_batch"}
{"type":"attr","node":42,"key":"paradigm","text":"speech-synthesis","conf":0.8}
{"type":"edge","src":42,"dst":77,"etype":"similar_to","weight":0.83,
 "evidence":"cosine 0.83"}
{"type":"cluster","cid":3,"label":"Sprachmodelle & TTS","members":[42,77,91]}
{"type":"vocab","kind":"standard","term":"sous-vide","canonical":"sous_vide"}
```

Regeln: Die Cloud sieht **nur** `id`, nie Geräte-Identifikatoren. Die App
akzeptiert ausschließlich die fünf Satzarten oben und ignoriert Unbekanntes —
so kann die Cloud-Seite weiterentwickelt werden, ohne die App zu brechen.

## 7. Wie die beiden Anreicherungspfade zusammenspielen

```
Teilen (sofort, offline-fähig)          Batch (periodisch, Cloud)
────────────────────────────────        ─────────────────────────────────
Fetch + Extraktion                      Embeddings über alle Texte
Kategorie + Slug-Titel                  Clustering (HDBSCAN)
Gazetteer-Treffer aus vocab             Cluster-Labels
TF-IDF-Nachbarn                         semantische Kanten (Kosinus)
   ↓ source=local_rules                 neue Vokabeln entdecken
                                           ↓ source=cloud_batch
        ╰──────────► entities / entity_attrs / edges / vocab ◄──────────╯
                     (gemeinsamer Pool, Präzedenz beim Lesen)
```

Der lokale Pfad bleibt vollständig funktionsfähig ohne Cloud — die Cloud
verbessert, ersetzt aber nichts.

## 8. Offene Punkte

- Welche Cloud (Recherche läuft) und wie die privaten Daten dorthin kommen,
  ohne öffentlich zu werden
- Ob `local_llm` je gebraucht wird oder der Batch reicht
- Aufräumstrategie für `edges` (bei N² Kanten wächst die Tabelle schnell —
  aktuell Top-K pro Knoten)

---
*Generated by AI (Claude Code Session). Quellen: Nutzeranforderung (Chat),
`docs/design/2026-07-19-second-brain-architektur.md`, Code in diesem Commit.*
