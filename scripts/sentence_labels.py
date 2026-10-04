#!/usr/bin/env python3
"""Check which word each Tatoeba example sentence is really about, a chunk at a time, with Claude.

The build gives a sentence to every entry that has a matching form, so "…három autót tart" ("…has
three cars", tart = to keep) also lands under tar "bald", whose accusative is tart. This script
turns that into a queue of small text chunks that a Claude Code session reads and answers (see
.claude/skills/label-sentences/SKILL.md). Answers are saved per chunk, so work can stop at any time
and carry on later.

  prepare   build the queue from public/data and data_cache (run after build_data.py)
  next      print the next chunk to do and where its answers go
  save N    check chunk N's answers, add them to data/sentence_labels.jsonl, remove the chunk
  status    progress so far

Only sentences a word could actually show are queued (its best EXAMPLE_POOL candidates), most
common words first. Labels name entries by build_data.lemma_key(), so they survive rebuilds.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_data as bd  # noqa: E402

WORK = bd.ROOT / 'data' / 'sentence-labels'
QUEUE = WORK / 'queue'
ANSWERS = WORK / 'answers'
CHUNK_SIZE = 50
GLOSS_CHARS = 70
LETTERS = 'abcdefghijklmnopqrstuvwxyz'
ANSWER_RE = re.compile(r'^(\d+)((?:\s+\S+=[a-z]+|\s+\S+=-)*)\s*$')


# --------------------------------------------------------------------------- loading the built data

def load_built(data_dir: Path):
    """Lemma records and {lowercased form: {lemma id: [tag set index, …]}} from the built shards."""
    manifest = json.loads((data_dir / 'manifest.json').read_text(encoding='utf-8'))
    root = data_dir / manifest['version']
    lemmas: dict[int, dict] = {}
    for p in (root / 'lemmas').glob('*.json'):
        lemmas.update({int(k): v for k, v in json.loads(p.read_text(encoding='utf-8')).items()})
    forms: dict[str, dict[int, list[int]]] = defaultdict(lambda: defaultdict(list))
    for p in (root / 'forms').glob('*.json'):
        for rows in json.loads(p.read_text(encoding='utf-8')).values():
            for form, lid, t in rows:
                forms[form.lower()][lid].append(t)
    tags = json.loads((root / 'tags.json').read_text(encoding='utf-8'))
    return [lemmas[i] for i in range(len(lemmas))], forms, tags


def describe_entry(lemma: dict, tag_sets: list[int], tags: list[str]) -> str:
    """'tar (adj), accusative singular: bald; shaven'."""
    how = 'dictionary form' if 0 in tag_sets else ' / '.join(sorted({tags[t] for t in tag_sets if tags[t]})[:2])
    glosses = '; '.join(s['g'] for s in lemma['s'][:2])
    if len(glosses) > GLOSS_CHARS:
        glosses = glosses[:GLOSS_CHARS].rsplit(' ', 1)[0] + '…'
    return f"{lemma['w']} ({lemma['pos']}), {how}: {glosses}"


# --------------------------------------------------------------------------- queue

def queued_pairs(candidates: dict[int, list[tuple]], labels) -> dict[int, set[str]]:
    """Sentence id → forms to check: the unlabelled ones among each word's best EXAMPLE_POOL candidates."""
    todo: dict[int, set[str]] = defaultdict(set)
    for cands in candidates.values():
        for _, _, hid, _, _, tok in cands[:bd.EXAMPLE_POOL]:
            if (hid, tok) not in labels:
                todo[hid].add(tok)
    return todo


def prepare(args) -> None:
    lemmas, forms, tags = load_built(Path(args.data))
    labels = bd.read_sentence_labels(Path(args.labels))
    exact = {f: set(ids) for f, ids in forms.items()}
    keys = [bd.lemma_key(l) for l in lemmas]
    sentences = bd.eligible_sentences(Path(args.cache))
    by_id = {hid: (hu, en) for hid, hu, en in sentences}
    candidates = bd.sentence_candidates(sentences, lemmas, exact, labels)
    todo = queued_pairs(candidates, labels)

    # Most common words first: a sentence's priority is its most frequent word that needs it.
    priority: dict[int, int] = {}
    for lid, cands in candidates.items():
        rank = lemmas[lid].get('fr') or 10**6 + lid
        for _, _, hid, _, _, tok in cands[:bd.EXAMPLE_POOL]:
            if tok in todo.get(hid, ()):
                priority[hid] = min(priority.get(hid, rank), rank)
    order = sorted(todo, key=lambda h: (priority[h], h))

    unsaved = sorted(ANSWERS.glob('*.txt')) if ANSWERS.exists() else []
    if unsaved:
        sys.exit(f'answers not saved yet: {", ".join(p.stem for p in unsaved)}. Run save for each first.')
    for d in (QUEUE, ANSWERS):
        d.mkdir(parents=True, exist_ok=True)
        for p in d.iterdir():
            p.unlink()
    def options(tok: str) -> list[int]:
        return sorted(i for i in exact[tok] if lemmas[i]['pos'] not in bd.NO_EXAMPLES_POS)[:len(LETTERS)]

    for n, start in enumerate(range(0, len(order), CHUNK_SIZE), start=1):
        batch = order[start:start + CHUNK_SIZE]
        words = sorted({tok for hid in batch for tok in todo[hid]}, key=lambda t: (fold_sort(t), t))
        lines = [f'# Chunk {n:04d}', '', '## Entries each word could be']
        for tok in words:
            lines.append(f'{tok}:')
            lines += [f'  {LETTERS[i]}) {describe_entry(lemmas[lid], forms[tok][lid], tags)}' for i, lid in enumerate(options(tok))]
        lines += ['', '## Sentences']
        sidecar = {}
        for hid in batch:
            hu, en = by_id[hid]
            toks = sorted(todo[hid], key=lambda t: hu.lower().find(t))
            lines += [f'@{hid} {hu}', f'  en: {en}', f'  check: {", ".join(toks)}']
            sidecar[str(hid)] = {tok: [keys[lid] for lid in options(tok)] for tok in toks}
        (QUEUE / f'{n:04d}.txt').write_text('\n'.join(lines) + '\n', encoding='utf-8')
        (QUEUE / f'{n:04d}.json').write_text(json.dumps(sidecar, ensure_ascii=False), encoding='utf-8')
    pairs = sum(len(v) for v in todo.values())
    print(f'{len(order)} sentences, {pairs} words to check, in {-(-len(order) // CHUNK_SIZE)} chunks of {CHUNK_SIZE}')
    print(f'{len(labels)} words already checked')


def fold_sort(word: str) -> str:
    return bd.fold(word)


def remaining() -> list[Path]:
    return sorted(QUEUE.glob('*.txt')) if QUEUE.exists() else []


def next_chunk(args) -> None:
    chunks = remaining()
    if not chunks:
        print('Queue is empty. Run `python3 scripts/sentence_labels.py prepare` after the next data build.')
        return
    n = chunks[0].stem
    print(f'chunk {n}: read {chunks[0].relative_to(bd.ROOT)}')
    print(f'write answers to {(ANSWERS / f"{n}.txt").relative_to(bd.ROOT)}, then run: python3 scripts/sentence_labels.py save {n}')
    print(f'{len(chunks)} chunks left')


# --------------------------------------------------------------------------- answers

def parse_answers(text: str, sidecar: dict[str, dict[str, list[str]]]) -> tuple[list[dict], list[str]]:
    """Answer lines like '1234 tart=b autót=a' → label records, plus a list of problems."""
    answers: dict[str, dict[str, str]] = {}
    problems = []
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        m = ANSWER_RE.match(line)
        if not m:
            problems.append(f'unreadable line: {line}')
            continue
        answers[m[1]] = dict(pair.split('=', 1) for pair in m[2].split())
    records = []
    for hid, words in sidecar.items():
        got = answers.pop(hid, None)
        if got is None:
            problems.append(f'sentence {hid} has no answer')
            continue
        for tok, keys in words.items():
            pick = got.pop(tok, None)
            if pick is None:
                problems.append(f'sentence {hid}: no answer for "{tok}"')
                continue
            letters = '' if pick == '-' else pick
            bad = [c for c in letters if LETTERS.index(c) >= len(keys)]
            if bad:
                problems.append(f'sentence {hid}: "{tok}={pick}" names a letter that is not an option')
                continue
            records.append({'s': int(hid), 'f': tok, 'e': [keys[LETTERS.index(c)] for c in dict.fromkeys(letters)]})
        problems += [f'sentence {hid}: "{tok}" was not asked about' for tok in got]
    problems += [f'sentence {hid} is not in this chunk' for hid in answers]
    return records, problems


def save(args) -> None:
    n = f'{int(args.chunk):04d}'
    chunk, sidecar_path, answer = QUEUE / f'{n}.txt', QUEUE / f'{n}.json', ANSWERS / f'{n}.txt'
    if not chunk.exists():
        sys.exit(f'chunk {n} is not in the queue')
    if not answer.exists():
        sys.exit(f'no answers yet: write {answer.relative_to(bd.ROOT)}')
    sidecar = json.loads(sidecar_path.read_text(encoding='utf-8'))
    records, problems = parse_answers(answer.read_text(encoding='utf-8'), sidecar)
    if problems:
        print(f'chunk {n} not saved. Fix the answer file and run save again:')
        print('\n'.join(f'  {p}' for p in problems))
        sys.exit(1)
    labels = Path(args.labels)
    labels.parent.mkdir(parents=True, exist_ok=True)
    with labels.open('a', encoding='utf-8') as fh:
        for r in records:
            fh.write(json.dumps(r, ensure_ascii=False, separators=(',', ':')) + '\n')
    for p in (chunk, sidecar_path, answer):
        p.unlink()
    rejected = sum(1 for r in records if len(r['e']) == 0)
    print(f'chunk {n} saved: {len(records)} words ({rejected} matched no entry). {len(remaining())} chunks left')


def status(args) -> None:
    labels = bd.read_sentence_labels(Path(args.labels))
    left = remaining()
    print(f'{len(labels)} words checked in {len({s for s, _ in labels})} sentences; {len(left)} chunks left')


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--labels', default=str(bd.LABELS))
    sub = ap.add_subparsers(dest='cmd', required=True)
    p = sub.add_parser('prepare')
    p.add_argument('--data', default=str(bd.ROOT / 'public' / 'data'))
    p.add_argument('--cache', default=str(bd.ROOT / 'data_cache'))
    p.set_defaults(fn=prepare)
    sub.add_parser('next').set_defaults(fn=next_chunk)
    p = sub.add_parser('save')
    p.add_argument('chunk')
    p.set_defaults(fn=save)
    sub.add_parser('status').set_defaults(fn=status)
    args = ap.parse_args()
    args.fn(args)


if __name__ == '__main__':
    main()
