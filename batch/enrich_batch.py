#!/usr/bin/env python3
"""Cloud batch enrichment: embeddings -> clusters -> edges -> vocabulary.

Reads the interchange JSONL the app exports and writes the enrichment JSONL
the app imports, both defined in docs/design/2026-08-09-graph-schema.md.

Runs on a free GitHub Actions runner (2 vCPU / 7 GB). Embeddings are computed
*on the runner*, so the collection is never sent to a third-party inference
provider — the whole point of the privacy design in the architecture doc.

Usage:
    python3 batch/enrich_batch.py export.jsonl enrich.jsonl
    python3 batch/enrich_batch.py export.jsonl enrich.jsonl --model <name>

Everything this writes is attributed to source="cloud_batch"; the app keeps
user and local_rules assertions alongside and resolves by precedence, so a
run is idempotent and never destroys hand-made curation.
"""
from __future__ import annotations

import argparse
import json
import math
import re
import sys
from collections import Counter
from pathlib import Path

SOURCE = "cloud_batch"
# multilingual: the collection is German and English in roughly equal parts
DEFAULT_MODEL = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
TOP_K_EDGES = 6
MIN_EDGE_WEIGHT = 0.35
MAX_CLUSTER = 50          # mirrors core/Clustering.MAX_MEMBERS
MIN_CLUSTER = 3


# --------------------------------------------------------------------- io
def read_nodes(path: Path):
    meta, nodes = {}, []
    with path.open(encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            rec = json.loads(line)
            if rec.get("type") == "meta":
                meta = rec
            elif rec.get("type") == "node":
                nodes.append(rec)
    return meta, nodes


# what a one-tap reaction means in words, so it lands in the embedding as
# language rather than as an opaque token the model has never seen
REACTION_TEXT = {
    "idea": "eigene Idee Inspiration",
    "understand": "verstehen lernen Grundlagen",
    "build": "nachbauen ausprobieren Projekt",
    "reference": "Nachschlagewerk Referenz",
    "now": "bald lesen aktuell",
}


def text_of(node: dict) -> str:
    """What the model sees.

    Hand-written signal outranks scraped text throughout: the note is a word
    the human chose for this page, the summary is a paragraph they wrote, and
    both say more about where an entry belongs than 2,000 characters of body
    copy ever will. The note is repeated deliberately — one word among 2,000
    otherwise contributes almost nothing to the vector."""
    parts = [node.get("label", ""), " ".join(node.get("tags", []))]
    note = (node.get("note") or "").strip()
    if note:
        parts.append(f"{note} {note}")
    reaction = REACTION_TEXT.get(node.get("reaction") or "")
    if reaction:
        parts.append(reaction)
    if node.get("summary"):
        parts.append(node["summary"])
    else:
        parts.append(node.get("description") or "")
        parts.append((node.get("text") or "")[:2000])
    return " ".join(p for p in parts if p).strip()


# ------------------------------------------------------------- embeddings
def embed(texts: list[str], model_name: str):
    from sentence_transformers import SentenceTransformer

    model = SentenceTransformer(model_name)
    return model.encode(
        texts,
        batch_size=32,
        convert_to_numpy=True,
        normalize_embeddings=True,   # cosine becomes a dot product
        show_progress_bar=True,
    )


# -------------------------------------------------------------- clusters
def cluster(vectors, ids):
    """HDBSCAN when available (finds its own cluster count and admits noise),
    agglomerative fallback so the job still runs on a minimal image."""
    labels = None
    try:
        import hdbscan

        labels = hdbscan.HDBSCAN(
            min_cluster_size=max(MIN_CLUSTER, len(ids) // 60),
            metric="euclidean",          # valid on normalised vectors
            cluster_selection_method="leaf",
        ).fit_predict(vectors)
    except ImportError:
        from sklearn.cluster import AgglomerativeClustering

        # a distance threshold, not a fixed cluster count: the number of
        # themes in a personal collection is unknown and changes as it grows
        labels = AgglomerativeClustering(
            n_clusters=None,
            distance_threshold=0.45,     # merge while cosine similarity > 0.55
            metric="cosine",
            linkage="average",
        ).fit_predict(vectors)

    grouped: dict[int, list] = {}
    for idx, label in enumerate(labels):
        grouped.setdefault(int(label), []).append(ids[idx])
    # -1 is HDBSCAN's noise label: keep the members, drop the pretence
    noise = grouped.pop(-1, [])
    return grouped, noise


STOP = set("""der die das und oder ein eine mit für von ist sind wird werden nicht auch als bei sich den dem des aus nach über wie was wir ich sie aber noch schon the and for with that this from have has are was were will can you your its our not but all how why what when more most into than then them they there here about www http https com org""".split())
WORD = re.compile(r"[\wÀ-ɏ]{3,}", re.UNICODE)


def label_for(members: list[int], by_id: dict[int, dict]) -> tuple[str, list[str]]:
    """Cluster label from the terms that distinguish it, plus the terms worth
    proposing as new vocabulary."""
    tags = Counter()
    words = Counter()
    for mid in members:
        node = by_id[mid]
        tags.update(node.get("tags", []))
        for w in WORD.findall((node.get("label") or "").lower()):
            if w not in STOP:
                words[w] += 1

    top_tags = [t for t, _ in tags.most_common(2) if t != "untagged"]
    top_words = [w for w, c in words.most_common(4) if c >= max(2, len(members) // 6)]
    label = " · ".join(top_tags) if top_tags else (top_words[0] if top_words else "Cluster")
    if top_words and not top_tags:
        label = " ".join(top_words[:2]).title()
    return label, top_words


# ----------------------------------------------------------------- edges
def top_edges(vectors, ids):
    import numpy as np

    sims = vectors @ vectors.T
    np.fill_diagonal(sims, -1.0)
    out = []
    for i, src in enumerate(ids):
        order = np.argsort(-sims[i])[:TOP_K_EDGES]
        for j in order:
            weight = float(sims[i][j])
            if weight < MIN_EDGE_WEIGHT:
                continue
            dst = ids[j]
            if src < dst:                      # undirected: emit once
                out.append((src, dst, weight))
    return out


# ------------------------------------------------------------------ main
def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("infile", type=Path)
    ap.add_argument("outfile", type=Path)
    ap.add_argument("--model", default=DEFAULT_MODEL)
    ap.add_argument("--run-id", default=None)
    args = ap.parse_args()

    meta, nodes = read_nodes(args.infile)
    if not nodes:
        print("no nodes in input", file=sys.stderr)
        return 1
    print(f"{len(nodes)} nodes (schema {meta.get('schema')})", file=sys.stderr)

    ids = [n["id"] for n in nodes]
    by_id = {n["id"]: n for n in nodes}
    vectors = embed([text_of(n) for n in nodes], args.model)

    grouped, noise = cluster(vectors, ids)
    edges = top_edges(vectors, ids)
    run_id = args.run_id or f"batch-{len(nodes)}"

    written = Counter()
    with args.outfile.open("w", encoding="utf-8") as out:
        def emit(rec):
            out.write(json.dumps(rec, ensure_ascii=False) + "\n")
            written[rec["type"]] += 1

        emit({"type": "meta", "run_id": run_id, "source": SOURCE,
              "model": args.model, "nodes": len(nodes)})

        for src, dst, weight in edges:
            emit({"type": "edge", "src": src, "dst": dst, "etype": "similar_to",
                  "weight": round(weight, 4), "evidence": f"cosine {weight:.2f}"})

        cid = 0
        for _, members in sorted(grouped.items(), key=lambda kv: -len(kv[1])):
            label, terms = label_for(members, by_id)
            # same cap as the on-device grouping, so exports stay comparable
            for part, chunk in enumerate(
                [members[i:i + MAX_CLUSTER] for i in range(0, len(members), MAX_CLUSTER)]
            ):
                suffix = f" ({part + 1})" if len(members) > MAX_CLUSTER else ""
                emit({"type": "cluster", "cid": cid, "label": label + suffix,
                      "members": chunk})
                cid += 1
            for term in terms:
                emit({"type": "vocab", "kind": "topic", "term": term,
                      "canonical": term.replace(" ", "_").replace("-", "_")})

        if noise:
            emit({"type": "cluster", "cid": cid, "label": "Ohne Cluster",
                  "members": noise[:MAX_CLUSTER]})

    print(f"wrote {dict(written)} -> {args.outfile}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
