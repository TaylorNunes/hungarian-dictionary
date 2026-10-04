#!/usr/bin/env python3
"""Check that the SQLite database holds exactly the data in the JSON shards, in the same order.

The web app reads the JSON shards and the Android app reads szokert.db.xz; search ranking depends on
row order, so every key must return the same rows in the same order from both. Also checks the
hashes and sizes in manifest.json.

  python3 scripts/check_db.py [public/data]
"""

from __future__ import annotations

import json
import lzma
import shutil
import sqlite3
import sys
import tempfile
import zlib
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_data as bd  # noqa: E402


def shards(directory: Path):
    for p in sorted(directory.glob('*.json')):
        yield from json.loads(p.read_text(encoding='utf-8')).items()


def check(data_root: Path) -> list[str]:
    """Problems found (empty when the database matches the JSON)."""
    manifest = json.loads((data_root / 'manifest.json').read_text(encoding='utf-8'))
    root = data_root / manifest['version']
    databases = manifest.get('databases') or []
    if not databases:
        return ['manifest.json lists no databases']
    info = databases[0]
    packed = data_root / info['path']
    problems: list[str] = []
    if packed.stat().st_size != info['bytes']:
        problems.append(f'{packed.name}: size {packed.stat().st_size} != manifest {info["bytes"]}')
    if bd.sha256_file(packed) != info['sha256']:
        problems.append(f'{packed.name}: sha256 differs from manifest')

    with tempfile.TemporaryDirectory() as tmp:
        raw = Path(tmp) / 'szokert.db'
        with lzma.open(packed, 'rb') as src, raw.open('wb') as dst:
            shutil.copyfileobj(src, dst, 1 << 20)
        if raw.stat().st_size != info['rawBytes'] or bd.sha256_file(raw) != info['rawSha256']:
            problems.append('uncompressed database differs from manifest rawBytes/rawSha256')
        db = sqlite3.connect(raw)
        problems += compare(db, root, manifest, info['schema'])
        db.close()
    return problems


def compare(db: sqlite3.Connection, root: Path, manifest: dict, schema: int) -> list[str]:
    problems: list[str] = []

    def expect(label: str, got, want) -> None:
        if got != want and len(problems) < 50:
            problems.append(f'{label}: database {str(got)[:200]} != json {str(want)[:200]}')

    expect('user_version', db.execute('PRAGMA user_version').fetchone()[0], schema)
    meta = dict(db.execute('SELECT key, value FROM meta'))
    expect('meta.version', meta.get('version'), manifest['version'])

    tags = json.loads((root / 'tags.json').read_text(encoding='utf-8'))
    expect('tags', [t for (t,) in db.execute('SELECT tags FROM tags ORDER BY idx')], tags)

    count = 0
    for key, rows in shards(root / 'forms'):
        count += len(rows)
        got = [list(r) for r in db.execute('SELECT form, lemma_id, tag_idx FROM forms WHERE folded = ? ORDER BY ord', (key,))]
        expect(f'forms[{key}]', got, rows)
    expect('forms row count', db.execute('SELECT count(*) FROM forms').fetchone()[0], count)

    count = 0
    for term, rows in shards(root / 'en'):
        count += len(rows)
        got = [list(r) for r in db.execute('SELECT lemma_id, sense, position FROM en WHERE term = ? ORDER BY ord', (term,))]
        expect(f'en[{term}]', got, rows)
    expect('en row count', db.execute('SELECT count(*) FROM en').fetchone()[0], count)

    head_sql = 'SELECT word, lemma_id, pos, rank, gloss FROM heads WHERE {} = ? ORDER BY lemma_id'
    count = 0
    for key, rows in shards(root / 'starts'):
        count += len(rows)
        expect(f'starts[{key}]', [list(r) for r in db.execute(head_sql.format('key'), (key,))], rows)
    expect('heads row count', db.execute('SELECT count(*) FROM heads').fetchone()[0], count)
    for rkey, rows in shards(root / 'ends'):
        expect(f'ends[{rkey}]', [list(r) for r in db.execute(head_sql.format('rkey'), (rkey,))], rows)

    count = 0
    for p in sorted((root / 'lemmas').glob('*.json')):
        for lid, rec in json.loads(p.read_text(encoding='utf-8')).items():
            count += 1
            row = db.execute('SELECT w, pos, fr, data, tbl FROM lemmas WHERE id = ?', (int(lid),)).fetchone()
            if row is None:
                expect(f'lemma {lid}', None, rec)
                continue
            w, pos, fr, data, tbl = row
            got = json.loads(data)
            if tbl is not None:
                got['t'] = json.loads(zlib.decompress(tbl))
            expect(f'lemma {lid}', got, rec)
            expect(f'lemma {lid} columns', (w, pos, fr), (rec['w'], rec['pos'], rec.get('fr')))
    expect('lemma count', db.execute('SELECT count(*) FROM lemmas').fetchone()[0], count)
    return problems


def main() -> None:
    data_root = Path(sys.argv[1]) if len(sys.argv) > 1 else bd.ROOT / 'public' / 'data'
    problems = check(data_root)
    if problems:
        print('\n'.join(problems))
        sys.exit(f'{len(problems)} problem(s): the database does not match the JSON shards')
    print('database matches the JSON shards')


if __name__ == '__main__':
    main()
