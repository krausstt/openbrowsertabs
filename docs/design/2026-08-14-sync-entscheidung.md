# Sync statt Dateitausch: Bedrohungsmodell und Backend-Entscheidung

Status: **Empfehlung steht, Umsetzung wartet auf Freigabe.**
Ersetzt den manuellen Import/Export aus `2026-08-14-cloud-batch.md` §4.

## 1. Das Bedrohungsmodell — korrigiert

Meine bisherige Regel („persönliche Sammlungen niemals in die Cloud") war
pauschal. Der Nutzer differenziert richtig: Sensibilität entsteht durch
**Aggregation**, nicht pro Datensatz. Zwei Ergänzungen dazu:

**Der Long Tail ist nicht harmlos.** In der realen Sammlung stehen
`reservierung.ochsenbraterei.de` (Ort + Datum), `immobilienscout24.de/Suche/…`
(Umzugsabsicht), Gesundheitsseiten, `IHK München`. Und der harte Fall: Bei
`search_query`-Einträgen ist das gespeicherte Label **der Suchtext selbst** —
das sind wörtlich die Fragen des Nutzers, nicht bloß besuchte Seiten.

**Der sensible Teil ist der kleine.** Gemessen an der realen Sammlung:

| Anteil | Größe | Sensibilität |
|---|---|---|
| Metadaten (URLs, Titel, Tags, Timestamps) | **0,54 MB** | das eigentliche Profil |
| + extrahierter Artikeltext | 3,3 MB (Cap: 7,9) | öffentlicher Seiteninhalt |
| Embeddings statt Text (int8) | **0,51 MB** | nur Geometrie, nicht lesbar |

Daraus folgt: Es geht nicht um Datenmengen-Schutz, sondern um **wer das
Aggregat lesen kann**. Und 3 MB sind für jede Infrastrukturfrage trivial.

## 2. Braucht es überhaupt eine Backend-DB? — Nein

Bei *einem* Nutzer, *einem* Gerät und einem *periodischen* Batch gibt es keine
Nebenläufigkeit, keine Multi-Device-Konflikte und keine serverseitigen
Queries: Suche, Filter und Joins laufen ohnehin lokal in SQLite. Selbst
pgvector bringt nichts — 1.400 Vektoren brute-force auf dem Gerät sind
Millisekunden.

Eine BaaS-DB zwänge dazu, das Schema (entities/attrs/edges/vocab/clusters) in
einem fremden Datenmodell nachzubauen: Impedance-Mismatch, doppelte
Migrationspflicht, kein funktionaler Gewinn. **Gebraucht wird ein Rohr, keine
Datenbank:** ein Blob hoch, ein Blob runter.

Eine Backend-DB lohnt erst, wenn (a) zwei Geräte gleichzeitig schreiben,
(b) es eine Weboberfläche geben soll, oder (c) semantische Suche
serverseitig ohne Volldownload laufen muss. Dann wäre **Turso** (Embedded
Replicas, SQLite bleibt SQLite) der einzige sinnvolle Kandidat — mit dem
Vorbehalt, dass Turso laut The Register (29.07.2026) Richtung Postgres
pivotiert und das Android-SDK als Preview geführt wird.

## 3. Empfehlung: GitHub (privates Repo, Release-Assets)

**Der Batch-Job läuft ohnehin dort — damit ist die CI-Seite null Aufwand**
(`GITHUB_TOKEN` ist eingebaut). Auf der Android-Seite genügt ein
Fine-grained PAT mit `contents:write` in `EncryptedSharedPreferences` plus
ein `Authorization: Bearer`-Header. **Kein OAuth, kein SDK, kein weiterer
Anbieter.**

- Release-Assets statt Commits, damit die Git-Historie nicht mit Blobs
  zuwächst (Asset bis 2 GB; unser Blob: 3 MB)
- Versionierung über Asset-Namen (`corpus-<gen>.jsonl.zst`)
- 5.000 authentifizierte Requests/h — wir brauchen zwei pro Tag
- Privates Repo: Inhalte „at rest" werden laut GitHub-Privacy-Statement
  (25.03.2026) nicht fürs Training genutzt
- **Aufwand ≈ 1 Personentag**

**Zweitempfehlung: Cloudflare R2 + 30-Zeilen-Worker.** Sauberer als
Objektspeicher gedacht: 10 GB frei, **kein Egress-Preis**, keine
Inaktivitäts-Pausierung, konditionale Writes (`If-Match`) für saubere
Generationswechsel. Der Worker erspart SigV4-Signierung in Kotlin. ≈ 1,5 PT.

### Wovon abzuraten ist

- **Firestore** — die Abrechnung erfolgt **pro Dokument**, unabhängig von der
  Größe. Ein Kantenmodell ist damit die teuerste denkbare Form; ein
  Enrichment-Lauf mit ~8.000 Kanten reißt die 20.000 Writes/Tag schnell.
  (Firebase *Storage* verlangt seit 03.02.2026 ohnehin den Blaze-Plan.)
- **Appwrite Cloud** — Auto-Pause nach 7 Tagen ohne *Entwicklungs*-Aktivität;
  Laufzeit-Traffic zählt ausdrücklich nicht.
- **Supabase als DB** — technisch passend (inkl. pgvector), aber Pause nach
  7 Tagen Inaktivität. Der Keep-Alive müsste per GitHub Actions laufen — und
  GitHub deaktiviert geplante Workflows nach 60 Tagen Repo-Inaktivität.
  **Zwei ineinandergreifende Fallstricke**, die sich gegenseitig auslösen
  können.

## 4. Die saubere Endstufe: die Cloud sieht nur Geometrie

Der Hashing-Gedanke des Nutzers, zu Ende gedacht: Sobald die
On-Device-Embeddings existieren (granite-278m via ONNX), lädt das Phone
**nur Vektoren plus opake IDs** hoch (~0,5 MB). Clustering rechnet auf
Vektoren und braucht keinen Text; die Cluster-Labels entstehen danach wieder
lokal aus den Tags.

Damit verschwindet die Vertrauensfrage, statt einem DPA anvertraut zu werden:
Der Anbieter kann nichts profilieren, weil er nichts Lesbares hält. Das ist
ein starkes Argument, die Embedding-Schicht vorzuziehen.

## 5. Billige Zwischenschritte (unabhängig vom Anbieter)

- **`search_query`-Labels vom Upload ausschließen** — der sensibelste Teil,
  und für Clustering am wenigsten nötig. Ein Filter im Exporter.
- **Pro-Eintrag-Flag „nicht synchronisieren"** — respektiert genau die
  Long-Tail-Ausnahmen, statt alles oder nichts zu entscheiden.
- **Artikeltext optional lassen** — ohne ihn schrumpft der Upload von 3,3 MB
  auf 0,54 MB; die Clusterqualität sinkt, aber die Metadaten bleiben nutzbar.

## 6. Entscheidungslog

| Datum | Entscheidung | Status |
|---|---|---|
| 2026-08-14 | Kein Backend-DB: ein Nutzer, ein Gerät, 3 MB — ein authentifizierter Objektspeicher genügt | empfohlen |
| 2026-08-14 | Primär GitHub Release-Assets im privaten Repo (statischer PAT, kein OAuth, CI-Auth gratis) | wartet auf Freigabe |
| 2026-08-14 | Firestore/Appwrite/Supabase-als-DB verworfen (Abrechnungsmodell bzw. Inaktivitäts-Pausen) | beschlossen |
| offen | „Nur Geometrie hochladen" nach den On-Device-Embeddings | vorgemerkt |
| offen | search_query-Filter, Pro-Eintrag-Sync-Flag | Vorschlag |

**Nicht verifiziert:** ob R2 im Free Tier eine Zahlungsmethode verlangt; ob
Turso-Android noch Preview ist (Storage widersprüchlich 5 vs. 9 GB); ob eine
explizite „kein Training"-Klausel in Cloudflares DPA v6.4 existiert; ob ein
Release-Asset-Upload als Repo-Aktivität gegen die 60-Tage-Regel zählt.

Quellen: docs.github.com (REST Rate Limits, Repository Limits, Contents API),
github.blog (Privacy-Update 25.03.2026), developers.cloudflare.com (R2 &
Workers Pricing), cloudflare.com (Customer DPA v6.4), supabase.com (Terms,
GDPR), firebase.google.com (Firestore Quotas, Storage-Änderung 03.02.2026),
turso.tech (Pricing, Roadmap), docs.turso.tech (Kotlin SDK),
theregister.com (29.07.2026), appwrite.io (Changelog 20.02.2026),
pocketbase.io, docs.nextcloud.com

---
*Generated by AI (Claude Code Session).*
