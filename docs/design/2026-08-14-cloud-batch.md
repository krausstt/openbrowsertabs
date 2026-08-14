# Cloud-Batch: Embeddings & Clustering im kostenlosen Free-Tier

Status: **Pipeline gebaut, Workflow-Vorlage bereit, Einrichtung offen (2 Schritte für dich).**
Setzt `docs/design/2026-08-09-graph-schema.md` (Interchange-Format) voraus.

## 1. Empfehlung: GitHub Actions in einem **privaten Daten-Repo**

Verifizierte Zahlen (Stand 14.08.2026):

| | Wert |
|---|---|
| Freie Minuten | **2.000 Linux-Min/Monat** (GitHub Free), 3.000 (Pro) |
| Runner (privat) | 2 vCPU / 7 GB RAM / 14 GB SSD |
| Job-Limit | 6 h · Cache 10 GB pro Repo |
| Kosten dieses Jobs | **0 €** |
| Training auf Repo-Inhalten | **Nein** (GitHub Privacy Statement, 25.03.2026) |

Geschätzter Verbrauch (Ableitung aus MiniLM-CPU-Benchmarks, **nicht gemessen**):
~5–10 min pro Lauf über 1.400 Links inkl. Setup → **wöchentlich ≈ 40 min/Monat**,
täglich ≈ 150–300 min. Beides weit unter dem Kontingent.

**Der entscheidende Punkt: Die Embeddings laufen auf dem Runner selbst**
(`sentence-transformers`, mehrsprachiges MiniLM wegen der deutsch-englischen
Mischung). Die Sammlung geht damit an **keinen** Inferenz-Anbieter.

### Warum nicht die Alternativen

- **Hugging Face Spaces:** cpu-basic wäre passend, aber seit ~Juni 2026 häufen
  sich Berichte über `403 quota limit`; ob Policy oder Bug ist offiziell nicht
  dokumentiert. Zu unsicher für einen periodischen Job.
- **Google Colab:** Scheduling gibt es nur in Colab Enterprise (kostenpflichtig).
  Für unbeaufsichtigte Cron-Jobs ungeeignet.
- **Modal:** technisch die eleganteste Lösung (echter `modal.Cron`,
  Image-Caching) und die **klarste Anti-Training-Klausel im DPA**. $30
  Credits/Monat, dieser Job kostet ~$0,03/Lauf. **Klare Zweitempfehlung**,
  falls die 7 GB RAM des Runners je knapp werden.
- **Cloudflare Workers AI:** 10.000 Neurons/Tag gratis ohne Kreditkarte,
  `bge-m3` mehrsprachig; unser Job bräuchte nur ~8 % des Tageskontingents, und
  Cloudflare sagt Training auf Kundeninhalten explizit ab. **Aber:** Workers
  Free hat 10 ms CPU-Limit → Clustering kann dort nicht laufen. Taugt als
  optionaler Embedding-Baustein, nicht als Gesamtlösung.
- **Oracle Always Free:** seit 15.06.2026 von 4 auf 2 OCPU halbiert, Instanzen
  über Limit werden ab 18.08.2026 terminiert. Zu viel Betriebslast.
- **Fly.io / Replicate:** keine nutzbaren Gratiskontingente mehr.

## 2. Privacy-Weg konkret

```
öffentliches Repo (Code)          privates Repo (Daten)
─────────────────────────         ──────────────────────────────
batch/enrich_batch.py     ──curl──►  .github/workflows/enrich.yml
batch/requirements.txt               data/export.jsonl   ← Phone pusht
                                     data/enrich.jsonl   → Phone holt
```

1. Privates Repo anlegen (GitHub Free erlaubt unbegrenzt viele).
2. `batch/workflow-template.yml` dorthin als `.github/workflows/enrich.yml`.
3. Der Workflow lädt den Code aus dem öffentlichen Repo — **Code öffentlich,
   Daten privat**, ohne den Code zu duplizieren.
4. Artefakte und Commits privater Repos sind nicht öffentlich lesbar.
5. **Optionale Härtung:** `export.jsonl` mit `age`/SOPS verschlüsselt ablegen,
   Schlüssel als Repo-Secret; GitHub sieht dann nur Ciphertext, entschlüsselt
   wird flüchtig im Runner.

Nebenvorteil: Geplante Workflows werden nur in **öffentlichen** Repos nach 60
Tagen Inaktivität deaktiviert — im privaten Repo läuft der Zeitplan weiter.

## 3. Was die Pipeline tut

`batch/enrich_batch.py` liest `export.jsonl` und schreibt `enrich.jsonl`:

1. **Textauswahl** — eine handgeschriebene Zusammenfassung schlägt den
   gescrapten Text; sonst Beschreibung + erste 2.000 Zeichen.
2. **Embeddings** — normalisiert, damit Kosinus ein Skalarprodukt ist.
3. **Clustering** — HDBSCAN (findet die Clusterzahl selbst und darf „Rauschen"
   sagen), Fallback agglomerativ mit **Distanzschwelle statt fester
   Clusterzahl** — die Zahl der Themen einer persönlichen Sammlung ist
   unbekannt und wächst mit.
4. **Cluster-Labels** aus den unterscheidenden Begriffen.
5. **Kanten** — Top-6 pro Knoten über Schwellwert 0.35, ungerichtet einmal.
6. **Vokabel-Vorschläge** — häufige, aussagekräftige Terme je Cluster gehen als
   neue Gazetteer-Einträge zurück. **So lernt die App Kochvokabular, ohne dass
   je jemand eine Kochvokabelliste geschrieben hat.**

Alles trägt `source="cloud_batch"`. Die App legt es neben `user` und
`local_rules` ab und löst beim Lesen über die Präzedenz auf — ein Lauf ist
**idempotent und kann Handarbeit nie zerstören**.

### Geprüft
Die Cluster-, Label- und Kantenlogik ist mit synthetischen Embeddings über drei
Themen (ASR-Modelle / Kochrezepte / ESP32) getestet: drei themenreine Cluster,
**0 themenfremde Kanten von 30**. Der Embedding-Schritt selbst ist ungetestet
(kein torch in dieser Umgebung) — läuft erstmals im CI.

## 4. Was du tun musst

1. Privates Repo anlegen, `batch/workflow-template.yml` als
   `.github/workflows/enrich.yml` hineinkopieren.
2. In der App: **Export → Cloud-Interchange (JSONL)** und die Datei als
   `data/export.jsonl` in das private Repo legen.

Danach läuft der Job wöchentlich. Der Rückweg (`enrich.jsonl` → App) ist **noch
nicht gebaut** — aktuell ist der Import ein offener Punkt, siehe 5.

## 5. Offene Punkte

- **Import-Pfad in die App**: `enrich.jsonl` einlesen und in
  `entity_attrs`/`edges`/`clusters`/`vocab` schreiben. Der Schreibcode
  (`GraphStore`) existiert, der Parser fehlt.
- **Transport**: aktuell manuell (Datei rein/raus). Automatik ginge über einen
  fine-grained PAT in der App — sicherheitstechnisch bewusst noch nicht gebaut.
- **Kanten-Aufräumen**: Top-6 pro Knoten bei 1.400 Knoten ≈ 8.400 Kanten pro
  Lauf; alte `cloud_batch`-Kanten sollten vor dem Import gelöscht werden.
- Laufzeit- und Speicherverbrauch sind Schätzungen, keine Messwerte.

Quellen: docs.github.com (Actions Billing, Limits, Runners), github.blog
(Privacy-Update 25.03.2026), discuss.huggingface.co (cpu-basic-Quota),
research.google.com/colaboratory/faq, modal.com (Pricing, DPA),
developers.cloudflare.com (Workers AI Pricing, Data Usage, bge-m3),
docs.oracle.com + infoq.com + heise.de (Oracle-Free-Tier-Kürzung),
community.fly.io, replicate.com/docs/topics/billing

---
*Generated by AI (Claude Code Session).*
