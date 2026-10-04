#!/usr/bin/env python3
"""Build the dictionary's static data shards from Kaikki (Wiktionary) and Tatoeba.

Usage:
    python3 scripts/build_data.py                 # download (if needed) and build
    python3 scripts/build_data.py --limit 5000    # quick build from the first N Kaikki lines

Output (under --out, default public/data):
    manifest.json                     current data version, shard keys, counts, sources
    <version>/tags.json               list of tag strings; forms reference them by index
    <version>/forms/<key>.json        {folded form: [[form, lemmaId, tagIdx], ...]}
    <version>/lemmas/<n>.json         {lemmaId: {w, pos, ipa, s: senses, t: table, ex: examples}}

<version> is a hash of the content, so unchanged data keeps its URLs (and offline caches).
Standard library only.
"""

from __future__ import annotations

import argparse
import bz2
import hashlib
import json
import math
import re
import shutil
import sys
import time
import unicodedata
import urllib.request
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

SOURCES = {
    'kaikki-hu.jsonl': 'https://kaikki.org/dictionary/Hungarian/kaikki.org-dictionary-Hungarian.jsonl',
    'hun_sentences.tsv.bz2': 'https://downloads.tatoeba.org/exports/per_language/hun/hun_sentences.tsv.bz2',
    'eng_sentences.tsv.bz2': 'https://downloads.tatoeba.org/exports/per_language/eng/eng_sentences.tsv.bz2',
    'hun-eng_links.tsv.bz2': 'https://downloads.tatoeba.org/exports/per_language/hun/hun-eng_links.tsv.bz2',
    # Word-form counts from OpenSubtitles 2018 (FrequencyWords by Hermit Dave, CC BY-SA 4.0).
    'frequencywords-hu.txt': 'https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/hu/hu_full.txt',
}

FORM_SHARD_MAX_BYTES = 250_000
LEMMAS_PER_SHARD = 128
MAX_FILE_BYTES = 50 * 1024 * 1024
MAX_TOTAL_BYTES = 900 * 1024 * 1024
EXAMPLES_PER_LEMMA = 5
EXAMPLES_PER_SENSE = 2

META_TAGS = {'table-tags', 'inflection-template', 'class', 'romanization'}
NOISE_TAGS = {'error-unrecognized-form', 'form-of', 'alt-of'}


# --------------------------------------------------------------------------- text helpers

def fold(s: str) -> str:
    """Lowercase and strip accents (á→a, ő→o, ü→u). Must match src/lib/fold.ts."""
    decomposed = unicodedata.normalize('NFD', s.lower())
    return unicodedata.normalize('NFC', ''.join(c for c in decomposed if unicodedata.category(c) != 'Mn'))


def shard_key_chars(folded: str) -> str:
    """Filename-safe version of a folded form. Must match shardChars in src/lib/fold.ts."""
    return ''.join(c if ('a' <= c <= 'z' or '0' <= c <= '9') else '_' for c in folded)


def clean_form_text(raw: str) -> list[str] | None:
    """Turn a Kaikki table cell into word forms, or None when it is a prose note.

    Handles 'or' alternatives ('láss or', 'or látnók', 'eendem or evendem'), footnote stars,
    'látva (látván)', 'láthatva / láthatván' and 'meneszt (or küld)'.
    """
    text = raw.strip().rstrip('*').strip()
    text = re.sub(r'\(or [^)]*\)', '', text).strip()          # 'meneszt (or küld)' -> 'meneszt'
    text = re.sub(r'\(([^)]*)\)', r' / \1', text)            # 'látva (látván)' -> 'látva / látván'
    parts = re.split(r'\s+or\s+|\s*/\s*|^or\s+|\s+or$', text)
    forms = [p.strip().rstrip('*').strip() for p in parts if p and p.strip()]
    if not forms or any(f in ('-', '—') or re.search(r'[\s.{}\[\]<>|=]', f) for f in forms):
        return None
    return forms


# Many inflected-form senses lack Kaikki's form_of link and only say so in the gloss:
# "third-person singular single-possession possessive of monitor".
FORM_GLOSS_RE = re.compile(r'^([a-z -]+?) of ([^\s(:“,;]+)')
GLOSS_TAGS = {
    'single-possession': ('possessive', 'possessed-single'),
    'multiple-possession': ('possessive', 'possessed-many'),
    'second-person-object': ('object-second-person',),
    'form': (),
}
GLOSS_WORDS = {
    'first-person', 'second-person', 'third-person', 'singular', 'plural', 'possessive',
    'indicative', 'conditional', 'subjunctive', 'present', 'past', 'definite', 'indefinite',
    'nominative', 'accusative', 'dative', 'instrumental', 'causal-final', 'translative', 'terminative',
    'essive-formal', 'essive-modal', 'inessive', 'superessive', 'adessive', 'illative', 'sublative',
    'allative', 'elative', 'delative', 'ablative', 'temporal',
}


def gloss_form_of(gloss: str) -> tuple[str, tuple[str, ...]] | None:
    """(target word, tags) when a gloss only describes an inflected form of another word."""
    m = FORM_GLOSS_RE.match(gloss)
    if not m:
        return None
    words = m.group(1).split()
    if not words or not all(w in GLOSS_WORDS or w in GLOSS_TAGS for w in words):
        return None
    tags = []
    for w in words:
        tags.extend(GLOSS_TAGS.get(w, (w,)))
    return m.group(2), tuple(sorted(set(tags)))


ARTICLE_RE = re.compile(r'^(?:to|a|an|the) ')
MAX_TERM_WORDS = 4
MAX_LEMMAS_PER_TERM = 30


def english_terms(gloss: str) -> list[str]:
    """English headword terms of a gloss: 'to see (to perceive…)' → ['see'].

    Only text outside parentheses counts; it is split on commas and semicolons.
    """
    text = re.sub(r'\([^()]*\)', '', gloss)
    terms = []
    for piece in re.split(r'[,;]', text):
        t = re.sub(r'\s+', ' ', piece).strip().strip('.!?"“”‘’').strip().lower()
        t = ARTICLE_RE.sub('', t)
        if t and len(t.split()) <= MAX_TERM_WORDS and not re.search(r'[:()\[\]=]', t) and t not in terms:
            terms.append(t)
    return terms


def tag_key(tags) -> str:
    return ' '.join(sorted(set(tags)))


# --------------------------------------------------------------------------- verb tables
#
# Kaikki's parse of the Hungarian conjugation tables is misaligned in most templates: each
# person's form carries the *next* column's tags (látok → "second-person", látnak → none),
# and moods are unlabelled. The row order is fixed, though, so we rebuild the tags:
#   * split each table into person blocks (at notes, definiteness/past changes, or when the
#     person sequence goes backwards);
#   * a block starting with a 2sg-informal row and without any 1sg row is shifted by one;
#   * group blocks (indefinite + definite + the -lak form) into tenses, then label them by
#     order: first = present indicative, flagged past = past, last two = conditional and
#     subjunctive; middle groups are archaic tenses (dropped) or, for 'van', the future.

PERSONS = ['1sg', '2sg', '3sg', '1pl', '2pl', '3pl']
PERSON_TAGS = {
    '1sg': ('first-person', 'singular'), '2sg': ('second-person', 'singular'),
    '3sg': ('third-person', 'singular'), '1pl': ('first-person', 'plural'),
    '2pl': ('second-person', 'plural'), '3pl': ('third-person', 'plural'),
}


def person_col(tags: set) -> int | None:
    # Formal 'you' (Ön) takes third-person forms; some templates tag it second-person + formal.
    if 'first-person' in tags:
        p = 1
    elif 'formal' in tags or 'third-person' in tags:
        p = 3
    elif 'second-person' in tags:
        p = 2
    else:
        return None
    if 'singular' in tags:
        return p - 1
    if 'plural' in tags:
        return p + 2
    return None


class Block:
    def __init__(self, definite, past):
        self.definite = definite      # True / False / None
        self.past = past
        self.rows: list[tuple[list[str], int | None, set]] = []
        self.last_raw_col = -1
        self.lak: list[str] = []      # 1sg subject → 2nd-person object form (látlak)

    def raw_col(self, col):
        if col is not None:
            return col
        # A person-less row after person rows is the shifted 3pl; before them it is the 1sg.
        return 6 if any(c is not None for _, c, _ in self.rows) else -1

    def resolve(self) -> list[tuple[str, str]]:
        """Return (form, person) pairs with the column shift undone."""
        if not self.rows:
            return []
        first_col = self.rows[0][1]
        shifted = first_col is not None and not any(c == 0 for _, c, _ in self.rows)
        out = []
        for forms, col, _ in self.rows:
            if shifted:
                true = 5 if col is None else col - 1
            else:
                true = 0 if col is None else col
            if 0 <= true <= 5:
                out.extend((f, PERSONS[true]) for f in forms)
        return out


def parse_conjugation(entry_forms: list[dict]) -> list[tuple[str, tuple[str, ...]]]:
    sections: list[list[tuple[list[str] | None, set]]] = []
    for f in entry_forms:
        if f.get('source') != 'conjugation':
            continue
        tags = set(f.get('tags', [])) - NOISE_TAGS
        if 'table-tags' in tags:
            sections.append([])
            continue
        if tags & META_TAGS:
            continue
        if not sections:
            sections.append([])
        sections[-1].append((clean_form_text(f.get('form', '')), tags))

    result: list[tuple[str, tuple[str, ...]]] = []
    for si, rows in enumerate(sections):
        potential = si > 0 and any(fs and re.search(r'h[ae]t', fs[0]) for fs, _ in rows[:3])
        result.extend(_parse_section(rows, potential))
    return result


def _parse_section(rows, potential: bool):
    items: list[tuple[str, object]] = []   # ('block', Block) | ('inf', [forms]) | ('pinf', Block) | ('nonfinite', (forms, tags))
    block: Block | None = None
    lak_block: Block | None = None   # block that just received a -lak form (alternatives may follow)
    mode = 'finite'

    def close():
        nonlocal block
        if block and (block.rows or block.lak):
            items.append(('pinf' if mode == 'pinf' else 'block', block))
        block = None

    for forms, tags in rows:
        if forms is None:
            close()
            continue
        col = person_col(tags)
        if 'infinitive' in tags:
            if col is None and mode == 'finite':
                close()
                items.append(('inf', forms))
                mode = 'pinf'
                continue
            if mode == 'pinf':
                if col is None and (block is None or not block.rows):
                    items.append(('inf', forms))   # plain infinitive repeated by some templates
                    continue
                if block is None:
                    block = Block(None, False)
                block.rows.append((forms, col, tags))
                continue
        if mode == 'pinf' and 'infinitive' not in tags:
            close()
            mode = 'nonfinite'
        if mode == 'nonfinite':
            items.append(('nonfinite', (forms, tags)))
            continue

        definite = True if 'definite' in tags else False if 'indefinite' in tags else None
        past = 'past' in tags
        if 'definite' not in tags and col in (None, 1) and forms[0].endswith(('lak', 'lek')):
            target = block if (block is not None and block.definite) else lak_block
            if target is not None:
                target.lak.extend(forms)
                close()
                lak_block = target
                continue
        lak_block = None
        new = (block is None
               or (definite is not None and block.definite is not None and definite != block.definite)
               or past != block.past
               or (block.rows and block.raw_col(col) < block.last_raw_col))
        if new:
            close()
            block = Block(definite, past)
        if block.definite is None:
            block.definite = definite
        block.last_raw_col = block.raw_col(col)
        block.rows.append((forms, col, tags))
    close()

    # Group finite blocks into tenses and label them.
    # A new tense group starts when the past flag changes or the group already holds a
    # block of the same definiteness (indefinite, definite).
    groups: list[dict] = []      # {'past': bool, 'blocks': [Block]}
    for kind, obj in items:
        if kind != 'block':
            continue
        b: Block = obj
        last = groups[-1] if groups else None
        if last is None or last['past'] != b.past or any(bool(x.definite) == bool(b.definite) for x in last['blocks']):
            last = {'past': b.past, 'blocks': []}
            groups.append(last)
        last['blocks'].append(b)

    nonpast = [g for g in groups if not g['past']]
    labels: dict[int, tuple[str, ...] | None] = {}
    for i, g in enumerate(nonpast):
        if i == 0:
            label = ('indicative', 'present')
        elif len(nonpast) >= 3 and i == len(nonpast) - 2:
            label = ('conditional', 'present')
        elif len(nonpast) >= 3 and i == len(nonpast) - 1:
            label = ('subjunctive', 'present')
        elif len(nonpast) == 2:
            label = ('conditional', 'present')
        elif any('future' in t and c is not None for b in g['blocks'] for _, c, t in b.rows):
            label = ('indicative', 'future')
        else:
            label = None   # archaic tense: dropped
        labels[id(g)] = label
    past_groups = [g for g in groups if g['past']]
    for i, g in enumerate(past_groups):
        labels[id(g)] = ('indicative', 'past') if i == 0 else None

    extra = ('potential',) if potential else ()
    out: list[tuple[str, tuple[str, ...]]] = []
    for g in groups:
        label = labels[id(g)]
        if label is None:
            continue
        for b in g['blocks']:
            defn = ('definite',) if b.definite else ('indefinite',)
            for form, person in b.resolve():
                out.append((form, label + defn + PERSON_TAGS[person] + extra))
            for form in b.lak:
                out.append((form, label + ('first-person', 'singular', 'object-second-person') + extra))

    # Wiktionary omits subjunctive definite cells that match the indicative (tudja, mondjuk…);
    # fill the missing persons from the indicative present definite.
    def persons_of(prefix):
        found = defaultdict(list)
        for form, tags in out:
            if tags[:3] == prefix and len(tags) > 4 and tags[4] in ('singular', 'plural'):
                found[tags[3:5]].append(form)
        return found
    subj_def = persons_of(('subjunctive', 'present', 'definite'))
    if subj_def:
        ind_def = persons_of(('indicative', 'present', 'definite'))
        for person, forms in ind_def.items():
            if person not in subj_def:
                out.extend((f, ('subjunctive', 'present', 'definite') + person + extra) for f in forms)

    for kind, obj in items:
        if kind == 'inf':
            out.extend((f, ('infinitive',) + extra) for f in obj)
        elif kind == 'pinf':
            for form, person in obj.resolve():
                out.append((form, ('infinitive', 'personal') + PERSON_TAGS[person] + extra))
        elif kind == 'nonfinite':
            forms, tags = obj
            for f in forms:
                t = _nonfinite_tags(f, tags)
                if t:
                    out.append((f, t + extra))
    return out


def _nonfinite_tags(form: str, tags: set) -> tuple[str, ...] | None:
    if 'noun-from-verb' in tags:
        return ('noun-from-verb',)
    if 'causative' in tags:
        return ('causative',)
    if 'adverbial' in tags:
        return ('adverbial', 'participle')
    if 'potential' in tags:
        return ('potential',)
    if form.endswith(('atlan', 'etlen')):
        return ('privative', 'participle')
    if form.endswith(('andó', 'endő')):
        return ('future', 'participle')
    if form.endswith(('ó', 'ő')):
        return ('present', 'participle')
    if form.endswith('t'):
        return ('past', 'participle')
    return None


def parse_other_forms(entry_forms: list[dict]) -> list[tuple[str, tuple[str, ...]]]:
    out = []
    for f in entry_forms:
        if f.get('source') == 'conjugation':
            continue
        tags = set(f.get('tags', [])) - NOISE_TAGS
        if tags & META_TAGS:
            continue
        forms = clean_form_text(f.get('form', ''))
        if not forms:
            continue
        out.extend((x, tuple(sorted(tags))) for x in forms)
    return out


# --------------------------------------------------------------------------- download

def download(cache: Path) -> None:
    cache.mkdir(parents=True, exist_ok=True)
    for name, url in SOURCES.items():
        dest = cache / name
        if dest.exists():
            continue
        part = dest.with_suffix(dest.suffix + '.part')
        for attempt in range(6):
            have = part.stat().st_size if part.exists() else 0
            req = urllib.request.Request(url, headers={'User-Agent': 'szokert-build/1.0'})
            if have:
                req.add_header('Range', f'bytes={have}-')
            try:
                with urllib.request.urlopen(req, timeout=60) as resp:
                    if have and resp.status != 206:
                        have = 0
                    with open(part, 'ab' if have else 'wb') as out:
                        shutil.copyfileobj(resp, out, 1 << 20)
                part.rename(dest)
                print(f'  downloaded {name} ({dest.stat().st_size / 1e6:.1f} MB)')
                break
            except OSError as e:
                print(f'  {name}: {e}; retrying ({attempt + 1})', file=sys.stderr)
                time.sleep(3 * (attempt + 1))
        else:
            sys.exit(f'failed to download {url}')


# --------------------------------------------------------------------------- Kaikki

def read_kaikki(path: Path, limit: int | None):
    """Return (lemmas, form_of_links, stats).

    lemmas: list of dicts {w, pos, ipa, s, forms: [(form, tags)]}
    form_of_links: list of (form, target word, pos, tags)
    """
    lemmas = []
    links = []
    with open(path, encoding='utf-8') as fh:
        for i, line in enumerate(fh):
            if limit and i >= limit:
                break
            d = json.loads(line)
            if d.get('lang_code', 'hu') != 'hu':
                continue
            word, pos = d.get('word'), d.get('pos')
            if not word or not pos:
                continue
            senses = []
            for s in d.get('senses', []):
                stags = set(s.get('tags', [])) - NOISE_TAGS
                if s.get('form_of'):
                    for fo in s['form_of']:
                        if fo.get('word'):
                            links.append((word, fo['word'], pos, tuple(sorted(stags))))
                    continue
                glosses = s.get('glosses') or []
                if not glosses or 'no-gloss' in stags:
                    continue
                implied = gloss_form_of(glosses[-1])
                if implied:
                    links.append((word, implied[0], pos, tuple(sorted(stags | set(implied[1])))))
                    continue
                sense = {'g': glosses[-1]}
                if len(glosses) > 1:
                    sense['p'] = glosses[0]
                if stags:
                    sense['t'] = sorted(stags)
                exs = []
                for ex in s.get('examples', []):
                    text = (ex.get('text') or '').strip()
                    eng = (ex.get('english') or ex.get('translation') or '').strip()
                    if text and eng and len(text) <= 200 and ex.get('type') != 'quotation':
                        exs.append([text, eng])
                    if len(exs) >= EXAMPLES_PER_SENSE:
                        break
                if exs:
                    sense['ex'] = exs
                senses.append(sense)
            if not senses:
                continue
            forms = d.get('forms', [])
            table = parse_conjugation(forms) + parse_other_forms(forms)
            ipa = next((x['ipa'] for x in d.get('sounds', []) if x.get('ipa')), None)
            lemmas.append({'w': word, 'pos': pos, 'ipa': ipa, 's': senses, 'forms': table})
    return lemmas, links


# --------------------------------------------------------------------------- Tatoeba

def read_tsv_bz2(path: Path):
    with bz2.open(path, 'rt', encoding='utf-8', errors='replace') as fh:
        for line in fh:
            yield line.rstrip('\n').split('\t')


TOKEN_RE = re.compile(r"[^\W\d_]+(?:-[^\W\d_]+)*")


def attach_sentences(cache: Path, lemmas: list[dict], exact_index: dict[str, set[int]]) -> dict[int, int]:
    """Attach example sentences; return how many sentences use each lemma."""
    hun = {int(r[0]): r[2] for r in read_tsv_bz2(cache / 'hun_sentences.tsv.bz2') if len(r) >= 3}
    links: dict[int, int] = {}
    for r in read_tsv_bz2(cache / 'hun-eng_links.tsv.bz2'):
        if len(r) >= 2:
            links.setdefault(int(r[0]), int(r[1]))
    wanted = set(links.values())
    eng = {int(r[0]): r[2] for r in read_tsv_bz2(cache / 'eng_sentences.tsv.bz2') if len(r) >= 3 and int(r[0]) in wanted}

    candidates: dict[int, list[tuple[int, int, str, str]]] = defaultdict(list)
    for hid, eid in links.items():
        hu_text, en_text = hun.get(hid), eng.get(eid)
        if not hu_text or not en_text:
            continue
        tokens = TOKEN_RE.findall(hu_text.lower())
        if not 2 <= len(tokens) <= 14:
            continue
        # Prefer 4–9 words; very short sentences carry little context.
        score = abs(len(tokens) - 6)
        seen = set()
        for tok in tokens:
            for lid in exact_index.get(tok, ()):
                if lid in seen:
                    continue
                seen.add(lid)
                bonus = 0 if tok == lemmas[lid]['w'].lower() else 1
                candidates[lid].append((score + bonus, hid, hu_text, en_text))

    for lid, cands in candidates.items():
        cands.sort()
        lemmas[lid]['ex'] = [[h, e] for _, _, h, e in cands[:EXAMPLES_PER_LEMMA]]
    return {lid: len(c) for lid, c in candidates.items()}


# --------------------------------------------------------------------------- build

# --------------------------------------------------------------------------- frequency

# Usage labels that mark a sense as unlikely to be what a learner wants.
UNCOMMON_LABELS = {
    'archaic', 'obsolete', 'dated', 'rare', 'dialectal', 'regional', 'nonstandard', 'proscribed',
    'literary', 'poetic', 'historical', 'uncommon', 'misspelling', 'eye-dialect',
}

MIN_FORM_COUNT = 2   # forms seen once in the subtitles are mostly typos and names
MINOR_POS = {'character', 'name', 'suffix', 'prefix', 'infix', 'interfix'}


def read_frequency(path: Path) -> dict[str, int]:
    """Lowercased word form → count, from a 'word count' per line file."""
    counts: dict[str, int] = defaultdict(int)
    with open(path, encoding='utf-8', errors='replace') as fh:
        for line in fh:
            parts = line.split()
            if len(parts) == 2 and parts[1].isdigit() and int(parts[1]) >= MIN_FORM_COUNT:
                counts[parts[0].lower()] += int(parts[1])
    return counts


def lemma_weight(lemma: dict, headword_count: int) -> float:
    """How much of a shared form's count a lemma should get, relative to the other lemmas with that form.

    Everyday senses count fully and rare/archaic ones a little; letters, names and affixes get little;
    and a lemma whose dictionary form is itself common in the corpus (eszik, not esz) gets more.
    """
    w = sum(0.2 if UNCOMMON_LABELS & set(s.get('t') or ()) else 1.0 for s in lemma['s'])
    if lemma['pos'] in MINOR_POS:
        w *= 0.1
    return w * (1 + math.log10(1 + headword_count))


def lemma_frequency(lemmas: list[dict], form_entries: dict[str, set], counts: dict[str, int]) -> dict[int, float]:
    """Estimated corpus count per lemma: each form's count shared among the lemmas that have it."""
    lemmas_of: dict[str, set[int]] = defaultdict(set)
    for form, entries in form_entries.items():
        lemmas_of[form.lower()].update(lid for lid, _ in entries)
    weights = [lemma_weight(l, counts.get(l['w'].lower(), 0)) for l in lemmas]
    freq: dict[int, float] = defaultdict(float)
    for form, count in counts.items():
        ids = lemmas_of.get(form)
        if not ids:
            continue
        total = sum(weights[i] for i in ids)
        for lid in ids:
            freq[lid] += count * weights[lid] / total
    return dict(freq)


def build(args) -> None:
    cache = Path(args.cache)
    out_root = Path(args.out)
    if not args.skip_download:
        print('Downloading sources…')
        download(cache)

    print('Reading Kaikki…')
    t0 = time.time()
    raw_lemmas, links = read_kaikki(cache / 'kaikki-hu.jsonl', args.limit)
    raw_lemmas.sort(key=lambda l: (fold(l['w']), l['w'], l['pos'], l['s'][0]['g']))
    lemmas = raw_lemmas
    print(f'  {len(lemmas)} lemmas, {len(links)} form-of links ({time.time() - t0:.0f}s)')

    by_word: dict[str, list[int]] = defaultdict(list)
    for lid, l in enumerate(lemmas):
        by_word[l['w']].append(lid)

    tag_index: dict[str, int] = {'': 0}
    tag_list = ['']

    def tid(tags) -> int:
        k = tag_key(tags)
        if k not in tag_index:
            tag_index[k] = len(tag_list)
            tag_list.append(k)
        return tag_index[k]

    # form -> {(lemmaId, tagIdx)}
    form_entries: dict[str, set[tuple[int, int]]] = defaultdict(set)
    for lid, l in enumerate(lemmas):
        form_entries[l['w']].add((lid, 0))
        seen = set()
        table = []
        for form, tags in l.pop('forms'):
            t = tid(tags)
            if (form, t) in seen:
                continue
            seen.add((form, t))
            table.append([form, t])
            form_entries[form].add((lid, t))
        if table:
            l['t'] = table

    unresolved = 0
    for form, target, pos, tags in links:
        ids = by_word.get(target, [])
        same_pos = [i for i in ids if lemmas[i]['pos'] == pos]
        chosen = same_pos or ids
        if not chosen:
            unresolved += 1
            continue
        t = tid(tags)
        for lid in chosen:
            if not any(l == lid for l, _ in form_entries.get(form, ())):
                form_entries[form].add((lid, t))
    print(f'  {len(form_entries)} distinct forms, {len(tag_list)} tag sets, {unresolved} unresolved form-of links')

    report_verb_accuracy(lemmas, links, tag_list, by_word)

    if not args.skip_sentences:
        print('Linking Tatoeba sentences…')
        exact: dict[str, set[int]] = defaultdict(set)
        for form, entries in form_entries.items():
            exact[form.lower()].update(lid for lid, _ in entries)
        linked = attach_sentences(cache, lemmas, exact)
        print(f'  {len(linked)} lemmas have example sentences')

    print('Ranking by subtitle frequency…')
    freq = lemma_frequency(lemmas, form_entries, read_frequency(cache / 'frequencywords-hu.txt'))
    for rank, lid in enumerate(sorted(freq, key=lambda i: (-freq[i], i)), start=1):
        lemmas[lid]['fr'] = rank
    print(f'  {len(freq)} lemmas ranked')

    report_missing_glossary(lemmas)
    english = build_english_index(lemmas, freq)
    print(f'  {len(english)} English terms')

    starts, ends = headword_indexes(lemmas)
    write_output(out_root, lemmas, form_entries, tag_list, english, (starts, ends), args)




def english_rank(position: int, sense_index: int, labels, freq: float) -> float:
    """Lower is better: a leading term in the first everyday sense of a common word wins."""
    return (0.6 * position + 0.5 * (sense_index > 0) + 1.5 * bool(UNCOMMON_LABELS & set(labels or ()))
            - 0.6 * math.log10(freq + 1))


def build_english_index(lemmas: list[dict], freq: dict[int, float]) -> dict[str, list[list[int]]]:
    """Folded English term → [[lemmaId, senseIndex, position], …], best first.

    position is 0 when the term leads its gloss ('house' in 'house, building'), else 1.
    """
    best: dict[str, dict[int, tuple[float, int, int]]] = defaultdict(dict)
    for lid, l in enumerate(lemmas):
        if l['pos'] == 'character':
            continue
        for si, sense in enumerate(l['s']):
            for i, term in enumerate(english_terms(sense['g'])):
                key = fold(term)
                position = min(i, 1)
                cand = (english_rank(position, si, sense.get('t'), freq.get(lid, 0)), si, position)
                if lid not in best[key] or cand < best[key][lid]:
                    best[key][lid] = cand
    index = {}
    for key, by_lemma in best.items():
        rows = sorted(by_lemma.items(), key=lambda kv: (kv[1][0], kv[0]))
        index[key] = [[lid, si, position] for lid, (_, si, position) in rows[:MAX_LEMMAS_PER_TERM]]
    return index


HEAD_SKIP_POS = {'character', 'punct', 'symbol'}
HEAD_GLOSS_CHARS = 60


def short_gloss(gloss: str, limit: int = HEAD_GLOSS_CHARS) -> str:
    """The gloss cut at a word boundary to about `limit` characters."""
    if len(gloss) <= limit:
        return gloss
    cut = gloss[:limit].rsplit(' ', 1)[0].rstrip(' ,;:(')
    return (cut or gloss[:limit]) + '…'


def headword_indexes(lemmas: list[dict]) -> tuple[dict[str, list], dict[str, list]]:
    """Headwords for partial search: folded headword → rows, and reversed folded headword → the same rows.

    A row is [word, lemmaId, pos, frequency rank or 0, short first gloss], enough to list a match
    without loading its lemma record (ends-with matches are spread over every lemma shard).
    """
    starts: dict[str, list] = defaultdict(list)
    ends: dict[str, list] = defaultdict(list)
    for lid, l in enumerate(lemmas):
        if l['pos'] in HEAD_SKIP_POS:
            continue
        row = [l['w'], lid, l['pos'], l.get('fr', 0), short_gloss(l['s'][0]['g'])]
        key = fold(l['w'])
        starts[key].append(row)
        ends[key[::-1]].append(row)
    return starts, ends


GLOSSARY = ROOT / 'src' / 'lib' / 'glossary.json'


def missing_glossary(lemmas: list[dict], glossary: dict) -> tuple[list[str], list[str]]:
    """Usage labels and parts of speech in the data that the Guide page (glossary.json) doesn't describe."""
    labels = {t for l in lemmas for s in l['s'] for t in s.get('t', ())}
    pos = {l['pos'] for l in lemmas}
    return sorted(labels - glossary['labels'].keys()), sorted(pos - glossary['pos'].keys())


def report_missing_glossary(lemmas: list[dict]) -> None:
    labels, pos = missing_glossary(lemmas, json.loads(GLOSSARY.read_text(encoding='utf-8')))
    if labels or pos:
        print(f'  warning: not described in src/lib/glossary.json: labels {labels}, parts of speech {pos}')
    else:
        print('  glossary covers every label and part of speech')


def report_verb_accuracy(lemmas, links, tag_list, by_word) -> None:
    """Compare rebuilt verb tags with Kaikki's (correct) form-of entries."""
    table_tags: dict[tuple[str, str], set[str]] = defaultdict(set)
    for l in lemmas:
        if l['pos'] != 'verb':
            continue
        for form, t in l.get('t', []):
            table_tags[(form, l['w'])].add(tag_list[t])
    keys = ('indicative', 'conditional', 'subjunctive', 'present', 'past', 'definite', 'indefinite',
            'first-person', 'second-person', 'third-person', 'singular', 'plural')
    checked = agree = 0
    for form, target, pos, tags in links:
        if pos != 'verb' or not {'first-person', 'second-person', 'third-person'} & set(tags) or 'potential' in tags:
            continue
        have = table_tags.get((form, target))
        if not have:
            continue
        want = {k for k in keys if k in tags}
        checked += 1
        if any({k for k in keys if k in h.split()} >= want for h in have):
            agree += 1
    if checked:
        print(f'  verb tag check: {agree}/{checked} form-of entries agree ({100 * agree / checked:.1f}%)')


def write_sharded(directory: Path, mapping: dict[str, list], dump) -> list[str]:
    """Write {folded key: rows} as prefix shards, splitting any shard over the size limit.

    A key lives in the shard named by the longest listed prefix of shard_key_chars(key);
    src/lib/data.ts resolves keys the same way. Returns the shard keys.
    """
    directory.mkdir(parents=True, exist_ok=True)
    shard_keys: list[str] = []

    def emit(prefix: str, group: list) -> None:
        size = sum(len(k) * 2 + len(json.dumps(v, ensure_ascii=False)) for k, v in group)
        depth = len(prefix)
        if size <= FORM_SHARD_MAX_BYTES or all(len(shard_key_chars(k)) <= depth for k, _ in group):
            dump(directory / f'{prefix}.json', dict(group))
            shard_keys.append(prefix)
            return
        here = [(k, v) for k, v in group if len(shard_key_chars(k)) <= depth]
        if here:
            dump(directory / f'{prefix}.json', dict(here))
            shard_keys.append(prefix)
        children: dict[str, list] = defaultdict(list)
        for k, v in group:
            sk = shard_key_chars(k)
            if len(sk) > depth:
                children[sk[:depth + 1]].append((k, v))
        for child, g in sorted(children.items()):
            emit(child, g)

    top: dict[str, list] = defaultdict(list)
    for k, v in sorted(mapping.items()):
        sk = shard_key_chars(k)
        if sk:
            top[sk[:1]].append((k, v))
    for prefix, g in sorted(top.items()):
        emit(prefix, g)
    return sorted(shard_keys)


def write_output(out_root: Path, lemmas, form_entries, tag_list, english, heads, args) -> None:
    print('Writing shards…')
    staging = out_root / '_staging'
    if staging.exists():
        shutil.rmtree(staging)
    (staging / 'forms').mkdir(parents=True)
    (staging / 'lemmas').mkdir(parents=True)

    def dump(path: Path, obj) -> int:
        data = json.dumps(obj, ensure_ascii=False, separators=(',', ':')).encode('utf-8')
        path.write_bytes(data)
        return len(data)

    # Forms grouped by folded spelling; both indexes are sharded by folded prefix.
    folded: dict[str, list] = defaultdict(list)
    for form, entries in form_entries.items():
        for lid, t in sorted(entries):
            folded[fold(form)].append([form, lid, t])
    form_keys = write_sharded(staging / 'forms', folded, dump)
    en_keys = write_sharded(staging / 'en', english, dump)
    start_keys = write_sharded(staging / 'starts', heads[0], dump)
    end_keys = write_sharded(staging / 'ends', heads[1], dump)

    shards: dict[int, dict] = defaultdict(dict)
    for lid, l in enumerate(lemmas):
        rec = {k: v for k, v in l.items() if v}
        shards[lid // LEMMAS_PER_SHARD][str(lid)] = rec
    for n, rec in shards.items():
        dump(staging / 'lemmas' / f'{n}.json', rec)
    dump(staging / 'tags.json', tag_list)

    # Content-addressed version so unchanged data keeps its cache.
    h = hashlib.sha256()
    for p in sorted(staging.rglob('*.json')):
        h.update(p.relative_to(staging).as_posix().encode())
        h.update(p.read_bytes())
    version = h.hexdigest()[:12]

    for p in out_root.iterdir():
        if p.is_dir() and p.name != '_staging':
            shutil.rmtree(p)
    staging.rename(out_root / version)

    files = list((out_root / version).rglob('*.json'))
    total = sum(p.stat().st_size for p in files)
    largest = max(files, key=lambda p: p.stat().st_size)
    manifest = {
        'version': version,
        'built': datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ'),
        'lemmaCount': len(lemmas),
        'posCounts': dict(sorted(Counter(l['pos'] for l in lemmas).items(), key=lambda kv: -kv[1])),
        'formCount': len(form_entries),
        'lemmasPerShard': LEMMAS_PER_SHARD,
        'lemmaShards': len(shards),
        'formShards': form_keys,
        'englishTermCount': len(english),
        'enShards': en_keys,
        'startShards': start_keys,
        'endShards': end_keys,
        'bytes': total,
        'sources': {
            'kaikki': SOURCES['kaikki-hu.jsonl'],
            'tatoeba': 'https://tatoeba.org/en/downloads',
        },
        'partial': bool(args.limit),
    }
    dump(out_root / 'manifest.json', manifest)

    print(f'  version {version}: {len(files)} files, {total / 1e6:.1f} MB total, '
          f'largest {largest.relative_to(out_root)} {largest.stat().st_size / 1e3:.0f} KB')
    for name in ('forms', 'en', 'starts', 'ends', 'lemmas'):
        size = sum(p.stat().st_size for p in (out_root / version / name).glob('*.json'))
        print(f'    {name}/: {size / 1e6:.1f} MB')
    if largest.stat().st_size > MAX_FILE_BYTES:
        sys.exit(f'{largest} exceeds {MAX_FILE_BYTES} bytes')
    if total > MAX_TOTAL_BYTES:
        sys.exit(f'data is {total} bytes, over the {MAX_TOTAL_BYTES} byte budget')


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--cache', default=str(ROOT / 'data_cache'))
    ap.add_argument('--out', default=str(ROOT / 'public' / 'data'))
    ap.add_argument('--limit', type=int, help='only read the first N Kaikki lines (for quick dev builds)')
    ap.add_argument('--skip-download', action='store_true')
    ap.add_argument('--skip-sentences', action='store_true')
    args = ap.parse_args()
    Path(args.out).mkdir(parents=True, exist_ok=True)
    build(args)


if __name__ == '__main__':
    main()
