# Hungarian Dictionary App — Project Plan

*Name: **Szókert** ("word garden"), slogan "Cultivate your knowledge." (Earlier working title: Szóda.)*

## Goal

An offline-capable Hungarian→English dictionary in the spirit of Takoboto. Paste any inflected word and get:

- the base word (lemma)
- its English meaning
- a breakdown of the word (e.g. *házat* = ház + accusative)
- example sentences

It runs as a web app, installs on Android, and costs nothing to host.

## Stack

| Layer | Choice |
|---|---|
| Frontend | Vite + TypeScript (Svelte or plain TS — keep it light) |
| Data build | Python script: Kaikki + Tatoeba → SQLite or sharded JSON |
| In-browser query | sql.js-httpvfs (fetches only needed parts of the DB via HTTP range requests), or prefix-sharded JSON files |
| Hosting | Cloudflare (Worker static assets), deployed by GitHub Actions |
| Android | PWA first, then Bubblewrap (Trusted Web Activity) for the Play Store |

## Data sources

- **Kaikki.org (Wiktextract)** — machine-readable English Wiktionary; Hungarian extract with lemmas, glosses, parts of speech and tagged inflection tables. License: CC BY-SA.
- **Tatoeba** — Hungarian–English sentence pairs. License: CC BY.
- *Optional later:* Hunglish corpus (check terms), Hunspell hu_HU (Magyarispell) for generating extra word forms.

## Data model

- `lemmas` — id, word, part_of_speech
- `senses` — lemma_id, gloss (English)
- `forms` — form, form_folded (accents removed), lemma_id, tags (e.g. `accusative`, `plural`, `possessive 1sg`)
- `sentences` — id, hungarian, english
- `sentence_forms` — sentence_id, form (index of which word forms appear in each sentence)

## Phases

### 1. Setup
- Create the repo, Vite skeleton, and GitHub Pages deploy action.

### 2. Data pipeline
- Download the Kaikki Hungarian JSONL dump.
- Extract lemmas, glosses and inflection tables.
- Build the form → lemma table, including an accent-folded column.
- Check the output size early (files must stay under 100 MB; the site under ~1 GB).
- Run it in a GitHub Action so the data rebuilds automatically.

### 3. MVP search
- Search box → lemma, glosses, breakdown ("*házat* = ház + accusative"), full declension/conjugation table.
- **Accent-tolerant search:** *hazat* still finds *házat* (essential for typing on a phone).

### 4. Suffix-stripper fallback
- Small TypeScript rule set for common endings (-t, -ban/-ben, -ra/-re, -nak/-nek, -val/-vel, possessives, etc.) for words the table misses.

### 5. Example sentences
- Tatoeba pairs, linked through the `sentence_forms` index.

### 6. PWA
- Web manifest and service worker that caches the app and data for offline use, and makes it installable on Android.

### 7. Anki export
- Start with a TSV export (word, meaning, example sentence) that Anki can import.
- Later: generate `.apkg` files directly.

### 8. Play Store
- Wrap the PWA with Bubblewrap.

## Later ideas

- English → Hungarian search
- Starred / saved words
- Frequency ranking of results
- Audio from Wiktionary
- Server-side fallback via emMorph for rare forms (would need hosting beyond GitHub Pages)

## Housekeeping

- Attribution page: Kaikki/Wiktionary (CC BY-SA), Tatoeba (CC BY).
- Choose a code license compatible with the data licenses.
- Check the final name is free on GitHub, the Play Store and as a domain; run it past a native speaker.

## Milestone

Phases 1–3 alone give a genuinely useful tool. Start with the data pipeline, since everything else depends on it.