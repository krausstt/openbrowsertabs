# GitHub Assets: Einrichtung, Betrieb, Produktisierung

Status: **Entscheidung getroffen (GitHub Assets). Deine Schritte unten, App-Seite noch nicht gebaut.**
Setzt `2026-08-14-sync-entscheidung.md` (Warum) und `2026-08-14-cloud-batch.md` (Pipeline) voraus.

## 1. Deine Schritte — was nur du mit deinem Konto kannst

Ich kann keine Repos in deinem Namen anlegen und keine Tokens erzeugen. Das
sind die vier Dinge, die dein Konto braucht:

### Schritt 1 — privates Daten-Repo
Neues Repo, z. B. `openbrowsertabs-data`, **Visibility: Private**.
Nichts weiter darin anlegen; der Workflow erzeugt die Ordner selbst.

### Schritt 2 — Workflow hineinlegen
Aus diesem Repo `batch/workflow-template.yml` kopieren nach
`.github/workflows/enrich.yml` im **privaten** Repo. Der Workflow zieht den
Pipeline-Code per `curl` aus dem öffentlichen Repo — Code öffentlich, Daten
privat, ohne Duplikat.

### Schritt 3 — Workflow-Schreibrechte erlauben
Im privaten Repo: **Settings → Actions → General → Workflow permissions →
„Read and write permissions"**. Ohne das kann der Job das Ergebnis nicht
zurückschreiben. (Das ist der Fehler, an dem so ein Setup üblicherweise beim
ersten Lauf scheitert.)

### Schritt 4 — Fine-grained PAT für die App
**Settings (Konto) → Developer settings → Personal access tokens →
Fine-grained tokens → Generate new token**
- *Resource owner:* dein Konto
- *Repository access:* **Only select repositories** → nur `openbrowsertabs-data`
- *Permissions → Repository permissions:*
  - **Contents: Read and write** (Assets lesen/schreiben)
  - alles andere auf „No access" lassen
- *Expiration:* 90 Tage (bewusst endlich — siehe 3.)

Den Token einmal kopieren; GitHub zeigt ihn nie wieder. Er kommt später in
die App (verschlüsselt via `EncryptedSharedPreferences`).

**Wichtig:** Dieser Token darf **nur** auf das Daten-Repo zeigen. Ein Token
mit Zugriff auf alle Repos in einer App auf dem Telefon wäre die eigentliche
Sicherheitslücke, nicht die Linksammlung.

### Was ich danach baue
Upload/Download in der App gegen die Releases-API, plus den Import-Parser für
`enrich.jsonl`. Ohne Schritt 1–4 kann ich das nicht gegen die Realität testen.

## 2. Warum Release-Assets und nicht Commits

Ein 3-MB-Blob pro Lauf wäre in der Git-Historie nach einem Jahr ~1 GB — Git
speichert jede Version. Release-Assets liegen **außerhalb** der Historie:
überschreibbar, einzeln löschbar, bis 2 GB pro Datei. Schema:

```
Release "data" (prerelease, im privaten Repo)
  ├── corpus.jsonl.zst      ← App lädt hoch (~1 MB komprimiert)
  └── enrich.jsonl.zst      ← Batch lädt hoch, App holt ab
```

Ein einziges Release, Assets werden ersetzt. Keine Tag-Flut.

## 3. Betriebs-Leitlinien, damit die Batches durchlaufen

**Die drei Dinge, die so ein Setup real killen:**

1. **Token läuft ab.** Bei 90 Tagen Laufzeit: Kalendereintrag. Die App muss
   den 401 sauber melden („Sync-Token abgelaufen"), nicht still scheitern —
   das baue ich so.
2. **Geplanter Workflow wird deaktiviert.** GitHub schaltet `schedule`-Trigger
   nach **60 Tagen ohne Repo-Aktivität** ab. Bei uns schreibt der Job selbst
   jede Woche ins Repo — damit ist die Uhr immer zurückgesetzt. Nur wenn der
   Job längere Zeit *fehlschlägt*, kann die Kette reißen.
3. **Kontingent.** 2.000 Minuten/Monat, ein Lauf 5–10 min. Wöchentlich ≈ 40
   min. Selbst täglich (~300) bleibt weit darunter. **Nicht** stündlich planen.

**Weitere Leitlinien:**
- `concurrency: group: enrich, cancel-in-progress: true` — verhindert, dass
  zwei Läufe gleichzeitig dasselbe Asset überschreiben.
- **Generationszähler** im Asset (`"gen": n` in der Meta-Zeile): Die App
  verwirft ein `enrich.jsonl`, das zu einem älteren `corpus` gehört, statt
  veraltete Kanten zu importieren.
- Alte `cloud_batch`-Kanten **vor** dem Import löschen, sonst wachsen sie
  monoton (8.400 Kanten pro Lauf).
- **Geplante Läufe verspäten sich real um 5–30 min** — nie auf punktgenaue
  Zeiten verlassen.
- Modell-Cache über `actions/cache` — sonst lädt jeder Lauf ~450 MB.
- Der Job soll bei leerem/ungültigem Input **hart fehlschlagen**, nicht ein
  leeres Ergebnis schreiben; ein leeres `enrich.jsonl` würde sonst alle
  Cluster löschen.

## 4. Wo das langfristig läuft — Produktisierung

Der GitHub-Actions-Weg ist bewusst *dein* Werkzeug, kein Produkt: Er setzt
ein GitHub-Konto, ein privates Repo und einen PAT voraus. Für ein Produkt
scheidet er aus. Die realistische Staffelung:

**Stufe 1 — heute: Actions + privates Repo.** Null Kosten, null Betrieb,
funktioniert für genau einen technisch versierten Nutzer.

**Stufe 2 — Tinkerer-Repo (das, was du beschreibst).** Zwei Komponenten:
Mobile App + Docker-Container fürs Batch-Enrichment, dazu ein
`docker-compose.yml`. Läuft auf deinem Linux-Server, ist für andere
Selfhoster reproduzierbar und macht das Repo als Portfolio-Stück stärker.
**Aufwand: klein**, weil die Pipeline schon ein reines Python-Skript mit
`requirements.txt` ist — es fehlt praktisch nur ein Dockerfile und ein Cron.
Ich würde das machen, *weil* es billig ist, nicht als Endzustand.

**Stufe 3 — das eigentlich elegante Ziel: der Batch verschwindet.** Der
skalierbarste Ort für die Anreicherung ist **kein Server, sondern das
Gerät**. Sobald die On-Device-Embeddings laufen (granite-278m via ONNX,
Apache-2.0):
- Embeddings entstehen beim Speichern, nebenbei, ohne Batch
- Clustering über 1.400 Vektoren ist auf dem Phone Millisekunden
- **Es gibt keinen Sync-Pfad mehr, weil es keinen zweiten Rechner gibt**

Damit fällt der gesamte Themenkomplex weg: kein Token, kein Kontingent, kein
DPA, keine Ablauffristen, keine Kosten pro Nutzer. Ein Produkt, das pro
Nutzer 0 € Infrastruktur kostet, skaliert per Definition.

**Wofür dann noch Cloud?** Nur für das, was on-device wirklich nicht geht:
- der **narrative** Wochen-Digest (Frontier-Modell, BYOK — der Nutzer bringt
  seinen Schlüssel, wir hosten nichts)
- optional Seiten-Thumbnails, wenn wir sie je wollen — das ist ein
  Fetch-Problem, kein Rechenproblem

**Konsequenz für die Priorisierung:** Der Sync ist Infrastruktur für einen
Zwischenzustand. Die On-Device-Embeddings sind der Ausweg *aus* dem
Zwischenzustand. Wenn eines von beiden zuerst kommen soll, spricht die
Produktperspektive für die Embeddings — der Sync wäre dann nur noch für
Bestands-Backfill und Experimente nötig, nicht für den Normalbetrieb.

## 5. Entscheidungslog

| Datum | Entscheidung | Status |
|---|---|---|
| 2026-08-21 | GitHub Release-Assets im privaten Repo, ein Release „data", zwei Assets | beschlossen |
| 2026-08-21 | Fine-grained PAT, nur `Contents: RW` auf genau dieses Repo | wartet auf Nutzer |
| 2026-08-21 | Generationszähler gegen Import veralteter Anreicherungen | geplant |
| 2026-08-21 | Docker-Container als Stufe 2 (Tinkerer-Repo) — billig, deshalb ja | vorgemerkt |
| 2026-08-21 | Zielbild: Anreicherung on-device, Sync entfällt; Cloud nur für BYOK-Digest | Richtung |

---
*Generated by AI (Claude Code Session). Quellen: docs.github.com
(Fine-grained PATs, Actions Limits, Releases-API, Repository Limits),
github.blog (Privacy-Update 25.03.2026); Zahlen aus der Recherche in
`2026-08-14-cloud-batch.md`.*
