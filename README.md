# openbrowsertabs

Finally making sense of all your open tabs.

Ein Data-Science-Projekt zur Sammlung, Bereinigung, Deduplizierung,
Kategorisierung und graphbasierten Navigation offener Browser-Tabs —
perspektivisch als Android-App mit Share-Target (à la Pocket) und
Hintergrund-Anreicherung.

## ⚠️ Datenschutz-Regel Nr. 1

**Persönliche URL-Sammlungen gehören niemals ins Repo.**
Alle Roh-Exporte (PDFs) und abgeleiteten Datensätze liegen in `data/` und
sind per `.gitignore` ausgeschlossen. Vor jedem Commit prüfen:
`git status` darf keine URL-Daten zeigen.

## Pipeline (Phase 1 — validiert)

`pipeline/tabs_pipeline.py` verarbeitet PDF-Exporte offener Tabs
(nummerierte URL-Listen) zu einem kanonischen, angereicherten Datensatz:

1. **Parsing** — robust gegen zeilenumbrochene URLs und unnummerierte
   Anhänge nach der nummerierten Liste; validiert gegen die Listennummerierung
2. **Normalisierung** — AMP-Cache-Wrapper auflösen (`*.cdn.ampproject.org`,
   `google.com/amp/s/…`), Tracking-Parameter entfernen (`utm_*`, `gclid`,
   `gad_*`, `fbclid`, …), Host/Pfad kanonisieren
3. **Snapshot-Dedup** — jede PDF ist ein Zeitpunkt-Snapshot; pro kanonischer
   URL entstehen `first_seen`/`last_seen`/`still_open` → Relevanz-Decay:
   was aus späteren Snapshots verschwindet, wurde implizit geschlossen
4. **Kategorisierung + Tagging** — Heuristiken auf Domain + URL-Slug
   (article, blog, repo, model_or_dataset, shopping, travel, search_query, …);
   in Phase 2 ersetzt/ergänzt durch LLM-Anreicherung mit Seiteninhalt

### Nutzung

```bash
pip install pypdf
# data/manifest.json anlegen: { "<datei>.pdf": {"id": "s1", "date": "YYYY-MM-DD"}, ... }
python3 pipeline/tabs_pipeline.py data/ data/dataset.json
```

Der Report auf stdout enthält Parse-Validierung (Nummerierungs-Check),
Dedup-Statistik und Kategorien-Verteilung.

## Roadmap

- **Phase 1 (dieses Repo, erledigt):** Ingestion-Pipeline + Validierung an
  realen Tab-Exporten, Explorer-Prototyp
- **Phase 2:** Anreicherung — Titel/Metadaten-Fetch, LLM-Kategorisierung,
  Embeddings für Ähnlichkeits-Kanten, Relevanz-Scoring mit Zeit-Decay
- **Phase 3 (MVP in Arbeit):** Android-App mit Share-Target, lokaler
  SQLite-DB und Enrichment-Queue — siehe `app/` + `core/`; Build & APK via
  GitHub Actions (Details: `docs/sessions/2026-07-02-android-core.md`)
- **Phase 4:** iPad-Port via Compose Multiplatform

Details: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
