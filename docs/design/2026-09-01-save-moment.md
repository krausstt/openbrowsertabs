# Der Save-Moment und das Ende des Posteingangs

Status: **Gebaut** (App-Schema v7, Interchange-Schema 7).
Reaktion auf das Nutzerfeedback vom 01.09.2026: „Posteingang 791 / Ohne Tag 454 …
das überfordert und erschlägt mich."

## 1. Warum der Posteingang gescheitert ist

Die beiden Zahlen auf dem Startbildschirm waren korrekt. Genau das war das
Problem. Drei Eigenschaften machten sie unbrauchbar:

1. **Sie wachsen von allein.** Jedes Teilen erhöht sie. Die Oberfläche hat
   damit exakt das Verhalten bestraft, von dem die App lebt.
2. **Sie sind kein Auftrag, sondern ein Urteil.** Auf 791 gibt es keine
   Handlung, die den Zustand sichtbar verbessert — 790 sieht aus wie 791.
3. **Sie erzwingen eine Taxonomie-Entscheidung.** „Tag vergeben" heißt: Welches
   Vokabular? Passt es zu den anderen? Das ist die teuerste Frage im ganzen
   System und stand am Anfang.

Die Recherche zu ADHS-Informationsverhalten (Design-Doc vom 05.07.) sagt
dasselbe in einem Satz: Ein Rückstand, der ohne eigenes Zutun wächst, wird
nicht abgearbeitet, sondern gemieden.

## 2. Was stattdessen gefragt wird

Nicht *„zu welchem Thema gehört das?"* — sondern **„warum hebst du das auf?"**

Fünf Haltungen, ein Tipp, keine Tastatur:

| | id | bedeutet |
|---|---|---|
| 💡 | `idea` | hat eine eigene Idee ausgelöst |
| 🤔 | `understand` | will ich richtig verstehen |
| 🛠️ | `build` | will ich nachbauen |
| 🔖 | `reference` | Nachschlagewerk, muss auffindbar bleiben |
| 🔥 | `now` | soll nicht liegen bleiben |

Bewusst **keine** Themenliste. Themen kann Anreicherung ableiten, Absicht
nicht — sie steht in keinem Markup. Und die Liste bleibt stabil, egal ob die
Seite eine HuggingFace-Modellkarte oder ein Lasagne-Rezept ist.

Daneben steht ein einzeiliges Feld für **ein Wort**. Das ist die zweite
Antwortform, für die Fälle, in denen man genau weiß, was man meinte.

## 3. Die entscheidende Reihenfolge: erst speichern, dann fragen

`ShareReceiverActivity` schreibt den Link in die Datenbank, **bevor** das
Overlay existiert. Damit kann die Frage den Save nie kosten: Wegwischen,
Zurück-Taste, App-Kill — der Eintrag ist in jedem Fall drin, nur ohne Kontext.

Jede Variante, in der die Kontextfrage ein Tor ist, endet gleich: Man teilt
nicht mehr. Der Nutzerwunsch war „mehr oder weniger gezwungen" — umgesetzt als
sozialer Zwang (die Frage steht da, sie ist in einer Sekunde beantwortet),
nicht als technischer.

Der Schreibvorgang läuft auf `writeScope`, nicht auf `lifecycleScope`: Die
Activity ist `noHistory` und beendet sich im selben Atemzug, was
`lifecycleScope` abbricht und den Tipp verschluckt hätte.

**Mehrfach-Shares (URL-Listen) überspringen das Overlay.** Eine Reaktion kann
über vierzig Links nichts aussagen, und vierzig Mal fragen ist das Gegenteil
von niedrigschwellig. Die laufen wie bisher in die Hintergrund-Warteschlange.

## 4. Der Stapel ersetzt den Posteingang

`core/InboxBatch.kt` schneidet sieben Einträge aus dem Rückstand. Sieben ist in
unter einer Minute erledigt.

Die Reihenfolge ist ausdrücklich **nicht** „ältestes zuerst" — so serviert ein
Rückstand genau die Einträge, an denen man schon einmal gescheitert ist.
Stattdessen:

- **Reagiert, aber ohne Tag** zuerst (+100). Hier hat der Mensch bereits gesagt,
  dass es zählt; ein Tag verbindet also etwas, das ihm wichtig ist.
- **Mehrfach gesehen** vor einmalig (+20). Ein wiederholtes Antreffen ist ein
  Interessensignal, das ungefragt gegeben wurde.
- **Aktualität** zerfällt über zwei Wochen statt abzureißen — was letzte Woche
  gespeichert wurde, kann man noch einordnen, was vom März ist, meist nicht.
- **Höchstens zwei pro Host**, damit zwölf YouTube-Kacheln nicht als ein
  einziger Eintrag gelesen und als einer übersprungen werden. Ein zweiter
  Durchlauf füllt auf, damit ein Stapel von fünf nicht als zwei ankommt.

Der Stapel **füllt sich nicht von selbst nach.** Er kann null erreichen und
dort bleiben, bis man „Noch sieben" tippt. Ein automatisch nachlaufender Stapel
wäre der Rückstand mit Zwischenschritten.

Die 791 sind nicht verschwunden, sie stehen eine Zeile tiefer in Grau, neben
„kuratiert: n" und einem Knopf für die ganze Liste. Als Tatsache über die
Sammlung, nicht als Forderung. Das Badge in der Navigation zeigt jetzt die
Stapelgröße (0–7) statt des Rückstands.

## 5. Was der Kontext im Graph tut

`reaction` und `user_note` gehen als eigene Felder in den Interchange
(Schema 7) und landen in `batch/enrich_batch.py` im Embedding-Text:

- Die Notiz wird **doppelt** eingesetzt — ein Wort unter 2.000 Zeichen
  Fließtext trägt sonst praktisch nichts zum Vektor bei.
- Die Reaktion wird zu Sprache aufgelöst (`build` → „nachbauen ausprobieren
  Projekt"), statt als opakes Token zu erscheinen, das kein Modell kennt.

Beide sind `source = user` und stehen damit in der Provenienz-Präzedenz über
allem, was ein Cloud-Lauf schreibt. Ein Batch kann sie nicht überschreiben.

## 6. Geprüft

`:core:test` grün, 12 neue Fälle in `InboxBatchTest` und `ReactionsTest` —
unter anderem: Stapel nie größer als angefordert, Host-Deckel begrenzt aber
verkleinert keinen kleinen Stapel, fertig kuratierte Einträge tauchen nicht
wieder auf, unbekannte Reaktions-IDs erreichen die Datenbank nicht.

**Nicht geprüft:** Alles, was einen Emulator braucht — die Migration v6→v7 auf
einer echten Datenbank, das Overlay im echten Share-Sheet, das Verhalten der
Tastatur über dem Sheet. Die App-Module lassen sich in dieser Umgebung nicht
bauen (Android-Gradle-Plugin nicht erreichbar); dafür läuft der CI-Build.

## 7. Offen

- **Flow-Modus** (TikTok-artiger Snap-Feed mit Hintergrund-Mikrofon) — braucht
  eine eigene Entscheidung: Laufzeit-Berechtigung `RECORD_AUDIO` und eine
  Transkriptionsabhängigkeit. Der Stapel hier ist die Vorstufe: gleiche
  Reaktionen, gleiches Ein-Tipp-Prinzip, ohne neue Berechtigung.
- **Import-Pfad** `enrich.jsonl` → App (unverändert offen).
- Reaktionen bisher ohne eigene Space-Zuordnung — „🛠️ Bauen" wäre ein
  naheliegender automatischer Space.

---
*Generated by AI (Claude Code Session). Quellen: eigenes Nutzerfeedback
01.09.2026 (Screenshot), `docs/design/2026-07-05-enrichment-problemraum.md`,
`docs/design/2026-08-09-graph-schema.md`.*
