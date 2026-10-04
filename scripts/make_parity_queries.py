#!/usr/bin/env python3
"""Write scripts/fixtures/parity_queries.json: the queries the web and Android searches must answer alike.

A mix of common headwords, inflected forms, accentless spellings, suffix-stripper cases, English words
(plain, inflected, irregular, phrases), short partial queries and awkward input. Picked with a fixed
seed from the built data (public/data), so rerunning on the same data gives the same list; the list
itself is committed and only needs regenerating when the mix should change.
"""

from __future__ import annotations

import json
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_data as bd  # noqa: E402

OUT = bd.ROOT / 'scripts' / 'fixtures' / 'parity_queries.json'

FIXED = [
    # from the app's own examples and earlier bug reports
    'házat', 'hazat', 'könyveimben', 'láttalak', 'szeretném', 'house', 'beautiful', 'to see', 'ház', 'haz',
    'houses', 'car', 'cár', 'ment', 'esz', 'autó', 'autós', 'fog', 'tart', 'tar', 'hal', 'orom', 'öröm',
    'megláttam', 'tudod-e', 'házzal', 'busszal', 'almát', 'kefében', 'házzá', 'barátaimmal', 'elmentünk',
    # English: inflected, irregular, phrases, loans
    'went', 'children', 'saw', 'running', 'stopped', 'cities', 'knives', 'bigger', 'happiest', 'quickly',
    "dog's", 'the house', 'a book', 'to go', 'running shoes', 'film', 'taxi', 'internet', 'hotel', 'bank',
    # names competing with ordinary words (name penalty), affixes and letters
    'magyar', 'vas', 'szilárd', 'kertész', 'jó', 'hold', 'nagy', 'kis', 'péter', 'buda', 'ság', 'ség', 'talan', 'b', 'q',
    # partial-only and short queries
    'ha', 'ke', 'me', 'ab', 'ny', 'sz', 'gy', 'ő', 'é', 'a', 'h', 'tűzo', 'kórh', 'könyvesb',
    # multi-word and awkward input
    'Látom a házat', 'jó reggelt', 'köszönöm szépen', '?ház!', '  ház  ', '-ság', 'HÁZ', 'Ház', 'ház.',
    '„alma"', 'x', 'qwrtz', 'zzz', '123', 'é-é', 'ház ház',
]


def main() -> None:
    data = bd.ROOT / 'public' / 'data'
    manifest = json.loads((data / 'manifest.json').read_text(encoding='utf-8'))
    root = data / manifest['version']
    tags = json.loads((root / 'tags.json').read_text(encoding='utf-8'))
    lemmas: dict[int, dict] = {}
    for p in (root / 'lemmas').glob('*.json'):
        lemmas.update({int(k): v for k, v in json.loads(p.read_text(encoding='utf-8')).items()})
    rng = random.Random(1)
    ranked = sorted((l for l in lemmas.values() if l.get('fr')), key=lambda l: l['fr'])

    queries = list(FIXED)
    queries += [l['w'] for l in ranked[:120]]                                  # commonest headwords
    common = [l for l in ranked[:3000] if l.get('t')]
    for l in rng.sample(common, 80):                                           # inflected forms
        queries.append(rng.choice(l['t'])[0])
    accented = [q for q in queries if bd.fold(q) != q.lower()]
    queries += [bd.fold(q) for q in rng.sample(accented, min(40, len(accented)))]  # typed without accents
    nouns = [l for l in common if l['pos'] == 'noun']
    for l in rng.sample(nouns, 40):                                            # possessive + case: stemmer
        poss = [f for f, t in l['t'] if 'possessive' in tags[t] and 'nominative' not in tags[t]
                and not any(c in tags[t] for c in ('inessive', 'dative', 'accusative'))]
        if poss:
            form = rng.choice(poss)
            back = next((c in 'aáoóuú' for c in reversed(form) if c in 'aáeéiíoóöőuúüű'), False)
            queries.append(form + ('ban' if back else 'ben'))
    english_terms = []
    for p in (root / 'en').glob('*.json'):
        english_terms += list(json.loads(p.read_text(encoding='utf-8')))
    english_terms.sort()
    words = [t for t in english_terms if t.isalpha() and len(t) > 2]
    queries += rng.sample(words, 40)                                           # English terms
    queries += [w + 's' for w in rng.sample(words, 10)] + [w + 'ing' for w in rng.sample(words, 5)]
    heads = sorted({l['w'] for l in lemmas.values() if len(l['w']) >= 5})
    queries += [h[:3] for h in rng.sample(heads, 15)]                          # partial prefixes
    queries += [h[-4:] for h in rng.sample(heads, 10)]                         # partial suffixes

    out = list(dict.fromkeys(queries))
    OUT.write_text(json.dumps(out, ensure_ascii=False, indent=0) + '\n', encoding='utf-8')
    print(f'{len(out)} queries written to {OUT.relative_to(bd.ROOT)}')


if __name__ == '__main__':
    main()
