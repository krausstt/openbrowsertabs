# Session-Notiz 2026-07-02 · Android-App-Core (Phase 3 MVP)

> Kommunikationskanal: Da Artifacts bei dir nicht funktionieren, dokumentiere
> ich Arbeitsstände als Markdown im Repo. Diese Datei enthält **keine
> persönlichen URLs** — nur Code-Doku und aggregierte Zahlen.

## Was in dieser Session entstanden ist

### 1. Android-App-Grundgerüst (`app/` + `core/`)

**`core/`** — reines Kotlin/JVM-Modul, 1:1-Port der validierten Python-Pipeline:

| Datei | Inhalt |
|---|---|
| `Urls.kt` | `urlsplit`, Percent-Decoding, `UrlNormalizer` (AMP-Unwrap, Tracking-Param-Strip, Host/Pfad-Kanonisierung) |
| `Categorizer.kt` | Domain-/Slug-Heuristiken: Kategorien, Themen-Tags, Suchanfragen-Label |
| `LinkParser.kt` | URL-Extraktion aus Share-Text/Paste, Dedup nach kanonischer URL |

**Parität abgesichert:** 18 Unit-Tests, deren Erwartungswerte von der
Python-Referenz (`pipeline/tabs_pipeline.py`) generiert wurden — beide
Implementierungen bleiben im Gleichschritt. Lokal ausgeführt: **18/18 grün.**

**`app/`** — Android (minSdk 26, target 36, Jetpack Compose):

- **Share-Target à la Pocket:** `ShareReceiverActivity` erscheint im
  Android-Share-Sheet („Tab speichern"), speichert sofort lokal (<1 s,
  offline-fähig), zeigt Toast, schließt sich. Duplikate werden als neue
  „Sichtung" gezählt (`n_sightings`, `last_seen`) — das ist das
  Relevanz-Signal aus Phase 1, jetzt live erfasst.
- **Hauptansicht:** Suche, Status-Filter (Offen/Archiv/Alle),
  Kategorie-Chips mit Zählern, Linkliste (Tap = öffnen, Archivieren /
  Wiederherstellen / Löschen).
- **Bulk-Import:** Text mit beliebig vielen URLs einfügen (für deine
  PDF-Altbestände: Text aus der PDF kopieren → einfügen).
- **Persistenz:** SQLite pur (`LinkStore`), Schema wie in
  `docs/ARCHITECTURE.md` — inkl. `pending_enrichment`-Flag: jeder Link ist
  fürs spätere Home-Lab-Enrichment vorgemerkt (Phase 2, bewusst
  zurückgestellt bis dein Linux-Rechner bereit ist).

### 2. Bewusste MVP-Entscheidungen

- **Kein Room/KSP, kein Hilt, kein Netzwerk-Code.** Diese
  Cloud-Umgebung kann keine Android-Builds ausführen (`dl.google.com` ist
  netzwerkseitig gesperrt) — jede Codegen-Abhängigkeit wäre ein
  Blindflug-Risiko. Der `LinkStore` hat eine kleine API-Oberfläche und wird
  beim iPad-Port gegen Room-KMP oder SQLDelight getauscht.
- **Versionen verifiziert statt geraten:** AGP 8.13.2 (bewusst nicht 9.x —
  Breaking Changes ohne lokale Testmöglichkeit), Kotlin 2.3.21,
  Compose BOM 2026.03.01, Gradle 8.14.3.
- Ein Bug wurde beim Portieren gefunden und in **beiden** Implementierungen
  gefixt: der Tracking-Parameter-Filter matchte per Präfix („si" hätte auch
  „size=10" entfernt). Dedup-Ergebnis unverändert (1.385 kanonische URLs).

### 3. CI: Build + APK ohne lokale Toolchain

`.github/workflows/android.yml` läuft bei jedem Push:

1. Core-Unit-Tests
2. `assembleDebug`
3. **Debug-APK als Download-Artefakt** (`openbrowsertabs-debug-apk`)

**→ So kommst du an die App:** GitHub → Actions → letzter grüner
„Android CI"-Lauf → Artifacts → APK herunterladen → auf dem Phone
installieren (Installation aus unbekannten Quellen einmalig erlauben).
Debug-signiert — für den Eigengebrauch genau richtig.

**Status: CI ist grün ✅** —
[Run #3](https://github.com/krausstt/openbrowsertabs/actions/runs/28577264354)
baut Core-Tests (18/18) + Debug-APK (`openbrowsertabs-debug-apk`, ~10,6 MB,
Artefakt 90 Tage gültig). Zwei Fehlversuche vorher, beide lehrreich:

1. **Run #1/#2:** `material3` ≥ 1.4 liefert `material-icons-core` nicht mehr
   transitiv → explizite Dependency ergänzt.
2. **Run #2:** Die Datenschutz-Regel `data/` in der `.gitignore` (unverankert)
   hat auch das Kotlin-Package `app/**/data/` ignoriert — `LinkStore.kt` war
   nie im Repo. Jetzt als `/data/` auf Root verankert und per
   `git check-ignore` verifiziert.

## Wie du es ausprobierst

1. APK installieren (s. o.) — **das Artefakt wird bei jedem grünen CI-Lauf
   neu erzeugt**; falls eins gelöscht wurde, einfach das vom neuesten Lauf
   nehmen (oder den Workflow über „Run workflow" manuell anstoßen)
2. In Chrome/beliebiger App: Teilen → „Tab speichern"
3. App öffnen: Link ist da, kategorisiert und getaggt
4. Bulk-Import, zwei Wege:
   - **.txt-Datei mit URL-Liste direkt an die App teilen** (Dateimanager →
     Teilen → „Tab speichern") — Nummerierungen („1. ", „2. ") werden
     ignoriert, Duplikate innerhalb der Datei dedupliziert, bereits bekannte
     URLs als Sichtung gezählt statt doppelt angelegt
   - oder Import-Button (＋) in der App → Text mit URLs einfügen

**Grenze des Text-Imports:** URLs müssen im Text *vollständig auf einer
logischen Zeile* stehen. Text, der direkt aus einem PDF kopiert wurde, hat
oft harte Zeilenumbrüche *mitten in* langen URLs — die zerreißt der
Import. Für PDF-Exporte bleibt `pipeline/tabs_pipeline.py` der richtige
Weg (repariert umbrochene URLs anhand der Listennummerierung).

## Offene Punkte (nächste Sessions)

- [ ] **Phase 2 Enrichment** (wartet auf dein Home-Lab): FastAPI-Service,
      Titel/Metadaten-Fetch, LLM-Kategorisierung, Embeddings,
      `pending_enrichment`-Queue abarbeiten via WorkManager + Sync-API
- [ ] Relevanz-Decay-Score + „Expiry-Inbox" in der App (Logik steht in
      `docs/ARCHITECTURE.md`)
- [ ] Graph-Ansicht (Topic-Cluster → Drilldown)
- [ ] Release-Signing + evtl. F-Droid-ähnliche Selbst-Distribution
- [ ] iPad-Port (Compose Multiplatform; `core/` ist bereits plattformneutral)

## Validierungsstand Phase 1 (Referenz)

4 PDF-Snapshots (Jan–Jul 2026): 2.562 Roh-Einträge → 1.385 kanonische URLs,
651 aktuell offen, 734 zwischen Snapshots geschlossen (implizites
Relevanz-Signal), 104 in allen 4 Snapshots (Bookmark-Kandidaten).
Kategorien-Verteilung und Details: siehe README + `pipeline/`.

---
*Generated by AI (Claude Code Session). Quellen: Code in diesem Repo,
validiert durch lokale Testläufe (core) bzw. GitHub Actions CI (app).*
