# Szókert: Hungarian ⇄ English dictionary

*Cultivate your knowledge.* Live at https://szokert.org/. The name and slogan are set in `src/config.ts` and `vite.config.ts`; the logo and app icons are drawn by `scripts/make_icons.py`.

An offline-capable Hungarian→English dictionary in the spirit of Takoboto. Type or paste any form of a word and get:

- the dictionary form: *házat* → **ház**
- its English meanings
- a breakdown: *házat* = ház + accusative; *szeretném* = szeret + conditional + I + definite
- the full declension or conjugation table
- example sentences, where every word can be tapped to look it up

It also works the other way: type an English word (*house*, *beautiful*, *to see*, *went*) to find the Hungarian. As in Takoboto, there's one search box and no language switch. Words with Hungarian accents are looked up as Hungarian; anything else is looked up in both languages, and the stronger match is shown first.

Accents are optional (*hazat* finds *házat*). When a form isn't in the tables, a suffix stripper makes a labelled guess (*könyveimben* = könyv + my (several) + inessive). Starred words can be exported for Anki. The site is a static PWA that installs on Android and works offline.

A **Guide** page explains the parts of speech, the frequency badges and how they're calculated, and every Wiktionary usage label. Its text lives in `src/lib/glossary.json`, and the data build warns if Wiktionary introduces a label the glossary doesn't describe.

The plan and roadmap are in [app.md](app.md).

## Development

Requirements: Node 22+ and Python 3.12+ (standard library only).

```sh
npm install
npm run data          # download sources (~620 MB, cached in data_cache/) and build public/data/
npm run dev           # http://localhost:5173
```

For a quicker data build while working on the UI: `python3 scripts/build_data.py --limit 5000`.

Tests and checks:

```sh
python3 -m unittest discover -s scripts -p 'test_*.py'   # data pipeline
npm test                                                  # search, stemmer, tags, Anki export
npm run check                                             # svelte-check / TypeScript
npm run build && npm run preview                          # production build with service worker
```

## How it works

**Data build** (`scripts/build_data.py`):

- Reads the [Kaikki.org](https://kaikki.org/dictionary/Hungarian/) Hungarian extract of English Wiktionary and the [Tatoeba](https://tatoeba.org/) Hungarian–English sentence pairs.
- Kaikki's Hungarian verb tables are parsed with every person shifted one column (*látok* is tagged "second person"), and mood labels are missing. The build rebuilds the tags from the table layout, then checks them against Kaikki's separate form-of entries. It prints the agreement rate (currently 99.4%).
- The English index reverses the Hungarian entries' glosses: "to see (to perceive…)" indexes *see* → lát. Ranking favours a term that leads its gloss, the first sense and everyday labels, plus how often the Hungarian word appears in Tatoeba.
- Word frequency comes from Hungarian subtitle counts (FrequencyWords). The subtitle list counts word forms, so each form's count is shared among the dictionary words that have it. The split is weighted by the number of everyday (non-rare) senses and by how common the headword itself is, so *eszik* outranks the rare *esz*, and *fog* "will/hold" outranks *fog* "tooth". Each entry stores its rank (`fr`). The rank drives the "very common" (top 1,000) and "common" (top 5,000) labels, search ranking in both directions, and `top1000`/`top5000` tags in the Anki export.
- Senses that only describe an inflected form ("third-person singular possessive of *monitor*") become links to the headword instead of separate entries.

Output lives in `public/data/`:

| File | Contents |
|---|---|
| `manifest.json` | current data version, shard list, counts |
| `<version>/forms/<prefix>.json` | accent-folded form → `[form, lemma id, tag set]`; sharded by prefix, split until each is ≤ 250 KB |
| `<version>/lemmas/<n>.json` | 128 entries per file: senses, IPA, inflection table, example sentences |
| `<version>/en/<prefix>.json` | English gloss term → `[lemma id, sense, leading?]`, best first |
| `<version>/starts/<prefix>.json`, `<version>/ends/<prefix>.json` | folded headword (reversed for `ends`) → `[headword, lemma id, pos, frequency rank, short gloss]`, for "starts or ends with" matches |
| `<version>/tags.json` | the tag sets that forms and tables refer to |

`<version>` is a hash of the content, so a rebuild that changes nothing keeps the same URLs and offline caches stay valid. The whole dataset is about 125 MB uncompressed, in about 3,250 files.

**App** (Vite + Svelte 5 + TypeScript):

- `src/lib/search.ts` tries an exact form, then an accent-folded form, then `src/lib/stemmer.ts`. In parallel, `searchEnglish` looks the query up in the English index, falling back to simple de-inflection from `src/lib/english.ts` (*houses → house*, *went → go*). `rankSections` decides which section comes first.
- `src/lib/tags.ts` turns tag sets into readable breakdowns and inflection grids.
- The service worker (`vite-plugin-pwa`) precaches the app shell and caches data shards as they're used. About → "Download for offline use" fetches every shard.

## Deploying (Cloudflare)

`.github/workflows/deploy.yml` runs on every push to `main` and weekly, to pick up new Wiktionary data. It tests the pipeline, builds the data, runs the app tests, builds, and deploys `dist/` to a Cloudflare Worker as static assets (`wrangler.jsonc`).

1. In Cloudflare, create an API token with **Account → Workers Scripts → Edit** (plus read-only Account Settings, User Details and Memberships).
2. In the GitHub repository's **Settings → Secrets and variables → Actions**, add the repository secrets `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID`.
3. The first deploy creates the Worker `szokert`. Attach the domain in its **Settings → Domains & Routes**.

Cloudflare rejects any single file over 25 MiB; `szokert.db.xz` is the largest (about 22 MB).

## Android / Play Store (phase 8)

The site is already an installable PWA (manifest, icons including a maskable one, and a service worker). To publish it on the Play Store as a Trusted Web Activity:

1. `npm i -g @bubblewrap/cli`, then `bubblewrap init --manifest https://<your-site>/manifest.webmanifest`.
2. `bubblewrap build` produces a signed `.aab` and the SHA-256 fingerprint of the signing key.
3. Publish `public/.well-known/assetlinks.json` with that fingerprint, so the app opens without the browser bar. Add `.well-known` to the site.
4. Upload the `.aab` in the Play Console (this needs a developer account).

## Licences

- App code: MIT (see `LICENSE`).
- Definitions and inflection tables: Wiktionary contributors via Kaikki.org, [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).
- Example sentences: Tatoeba contributors, [CC BY 2.0 FR](https://creativecommons.org/licenses/by/2.0/fr/).
- Word frequencies: [FrequencyWords](https://github.com/hermitdave/FrequencyWords) by Hermit Dave, from OpenSubtitles 2018, [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).

The in-app About page carries the same attribution.
