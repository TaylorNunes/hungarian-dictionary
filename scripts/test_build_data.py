"""Tests for build_data.py. Run: python3 -m unittest discover -s scripts -p 'test_*.py'"""

import bz2
import json
import shutil
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_data as bd  # noqa: E402

FIXTURES = Path(__file__).resolve().parent / 'fixtures'


def load_entry(word, pos):
    with open(FIXTURES / 'kaikki_sample.jsonl', encoding='utf-8') as fh:
        for line in fh:
            d = json.loads(line)
            if d['word'] == word and d['pos'] == pos:
                return d
    raise KeyError(word)


def conj(word):
    """{form: set of tag strings} for a fixture verb."""
    out = {}
    for form, tags in bd.parse_conjugation(load_entry(word, 'verb')['forms']):
        out.setdefault(form, set()).add(' '.join(tags))
    return out


class FoldTest(unittest.TestCase):
    def test_matches_shared_fixture(self):
        for raw, expected in json.loads((FIXTURES / 'fold_cases.json').read_text(encoding='utf-8')):
            self.assertEqual(bd.fold(raw), expected, raw)

    def test_shard_chars(self):
        self.assertEqual(bd.shard_key_chars('jo reggelt'), 'jo_reggelt')


class CleanFormTest(unittest.TestCase):
    def test_alternatives_and_notes(self):
        self.assertEqual(bd.clean_form_text('láss or'), ['láss'])
        self.assertEqual(bd.clean_form_text('or látnók'), ['látnók'])
        self.assertEqual(bd.clean_form_text('eendem or evendem'), ['eendem', 'evendem'])
        self.assertEqual(bd.clean_form_text('látva (látván)'), ['látva', 'látván'])
        self.assertEqual(bd.clean_form_text('láthatva / láthatván'), ['láthatva', 'láthatván'])
        self.assertEqual(bd.clean_form_text('meneszt (or küld)'), ['meneszt'])
        self.assertEqual(bd.clean_form_text('eendik*'), ['eendik'])
        self.assertIsNone(bd.clean_form_text('Future is expressed with a present-tense verb'))
        self.assertIsNone(bd.clean_form_text('volt.'))
        self.assertIsNone(bd.clean_form_text('-'))
        self.assertIsNone(bd.clean_form_text('{{{192}}}'))


class GlossFormOfTest(unittest.TestCase):
    def test_grammatical_glosses_become_links(self):
        self.assertEqual(bd.gloss_form_of('third-person singular single-possession possessive of monitor'),
                         ('monitor', ('possessed-single', 'possessive', 'singular', 'third-person')))
        self.assertEqual(bd.gloss_form_of('accusative of én (“I”): me'), ('én', ('accusative',)))

    def test_meaningful_glosses_are_kept(self):
        self.assertIsNone(bd.gloss_form_of('alternative form of tiéd'))
        self.assertIsNone(bd.gloss_form_of('synonym of ház'))
        self.assertIsNone(bd.gloss_form_of('house, building'))


class EnglishIndexTest(unittest.TestCase):
    def test_terms_from_glosses(self):
        self.assertEqual(bd.english_terms('to see (to perceive with the eyes)'), ['see'])
        self.assertEqual(bd.english_terms('house, building (closed structure with walls and a roof)'), ['house', 'building'])
        self.assertEqual(bd.english_terms('an apple; the fruit'), ['apple', 'fruit'])
        self.assertEqual(bd.english_terms('good morning!'), ['good morning'])
        self.assertEqual(bd.english_terms('the act of doing something for a very long time'), [])

    def test_ranking_prefers_leading_term_then_first_sense_then_frequency(self):
        lemmas = [
            {'w': 'hodály', 'pos': 'noun', 's': [{'g': 'barn, house'}]},          # 'house' not leading
            {'w': 'ház', 'pos': 'noun', 's': [{'g': 'house, building'}]},
            {'w': 'kamara', 'pos': 'noun', 's': [{'g': 'chamber'}, {'g': 'house (of parliament)'}]},
            {'w': 'lak', 'pos': 'noun', 's': [{'g': 'house'}]},                  # leading, but rarer than ház
        ]
        index = bd.build_english_index(lemmas, {1: 50, 3: 2})
        self.assertEqual([row[0] for row in index['house']], [1, 3, 2, 0])
        self.assertEqual(index['house'][0], [1, 0, 0])
        self.assertEqual(index['barn'], [[0, 0, 0]])


class FrequencyTest(unittest.TestCase):
    def lemma(self, word, pos='noun', senses=('meaning',), labels=None):
        return {'w': word, 'pos': pos, 's': [{'g': g, **({'t': labels} if labels else {})} for g in senses]}

    def test_weight_favours_everyday_senses_and_common_headwords(self):
        everyday = self.lemma('eszik', 'verb', ['to eat'])
        rare = self.lemma('esz', 'verb', ['to eat'], labels=['rare'])
        self.assertGreater(bd.lemma_weight(everyday, 0), bd.lemma_weight(rare, 0))
        self.assertGreater(bd.lemma_weight(everyday, 1000), bd.lemma_weight(everyday, 0))
        letter = self.lemma('a', 'character')
        self.assertLess(bd.lemma_weight(letter, 0), bd.lemma_weight(self.lemma('a', 'article'), 0))

    def test_shared_form_counts_are_split_by_weight(self):
        lemmas = [self.lemma('fog', 'noun', ['tooth']), self.lemma('fog', 'verb', ['to hold', 'to catch', 'will'])]
        forms = {'fog': {(0, 0), (1, 0)}, 'fogok': {(1, 1)}}
        freq = bd.lemma_frequency(lemmas, forms, {'fog': 400, 'fogok': 100, 'unknown': 50})
        self.assertAlmostEqual(freq[0] + freq[1], 500)
        self.assertAlmostEqual(freq[0], 100)    # 1 of 4 sense weight
        self.assertAlmostEqual(freq[1], 400)

    def test_read_frequency_drops_singletons_and_lowercases(self):
        with tempfile.NamedTemporaryFile('w', suffix='.txt', delete=False, encoding='utf-8') as fh:
            fh.write('Ház 3\nház 4\nhapax 1\nbad line\n')
        try:
            self.assertEqual(bd.read_frequency(Path(fh.name)), {'ház': 7})
        finally:
            Path(fh.name).unlink()


class ConjugationTest(unittest.TestCase):
    """Kaikki shifts person tags by one column; the parser must undo it."""

    def assertTagged(self, forms, form, tags):
        self.assertIn(tags, forms.get(form, set()), f'{form}: {forms.get(form)}')

    def test_present_indefinite_and_definite(self):
        f = conj('lát')
        self.assertTagged(f, 'látok', 'indicative present indefinite first-person singular')
        self.assertTagged(f, 'látsz', 'indicative present indefinite second-person singular')
        self.assertTagged(f, 'lát', 'indicative present indefinite third-person singular')
        self.assertTagged(f, 'látnak', 'indicative present indefinite third-person plural')
        self.assertTagged(f, 'látom', 'indicative present definite first-person singular')
        self.assertTagged(f, 'látják', 'indicative present definite third-person plural')
        self.assertTagged(f, 'látlak', 'indicative present first-person singular object-second-person')

    def test_past_conditional_subjunctive(self):
        f = conj('lát')
        self.assertTagged(f, 'láttam', 'indicative past indefinite first-person singular')
        self.assertTagged(f, 'láttam', 'indicative past definite first-person singular')
        self.assertTagged(f, 'látnék', 'conditional present indefinite first-person singular')
        self.assertTagged(f, 'látnám', 'conditional present definite first-person singular')
        self.assertTagged(f, 'látnók', 'conditional present definite first-person plural')
        self.assertTagged(f, 'lássak', 'subjunctive present indefinite first-person singular')
        self.assertTagged(f, 'lásd', 'subjunctive present definite second-person singular')
        self.assertTagged(f, 'lássák', 'subjunctive present definite third-person plural')

    def test_nonfinite_and_potential(self):
        f = conj('lát')
        self.assertTagged(f, 'látni', 'infinitive')
        self.assertTagged(f, 'látnom', 'infinitive personal first-person singular')
        self.assertTagged(f, 'látás', 'noun-from-verb')
        self.assertTagged(f, 'látva', 'adverbial participle')
        self.assertTagged(f, 'láthatok', 'indicative present indefinite first-person singular potential')

    def test_archaic_tenses_are_dropped(self):
        f = conj('lát')
        self.assertNotIn('láték', f)
        self.assertNotIn('látandok', f)

    def test_aligned_template_van(self):
        f = conj('van')
        self.assertTagged(f, 'vagyok', 'indicative present indefinite first-person singular')
        self.assertTagged(f, 'van', 'indicative present indefinite third-person singular')
        self.assertTagged(f, 'lesz', 'indicative future indefinite third-person singular')
        self.assertTagged(f, 'volna', 'conditional present indefinite third-person singular')
        self.assertTagged(f, 'legyen', 'subjunctive present indefinite third-person singular')
        self.assertNotIn('infinitive personal first-person singular', f.get('lenni', set()))

    def test_subjunctive_definite_filled_from_indicative(self):
        f = conj('tud')
        self.assertTagged(f, 'tudja', 'subjunctive present definite third-person singular')
        self.assertTagged(f, 'tudja', 'indicative present definite third-person singular')

    def test_irregular_alternatives(self):
        f = conj('jön')
        self.assertTagged(f, 'gyere', 'subjunctive present indefinite second-person singular')
        self.assertTagged(f, 'jövök', 'indicative present indefinite first-person singular')


class BuildTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        cache = self.tmp / 'cache'
        cache.mkdir()
        shutil.copy(FIXTURES / 'kaikki_sample.jsonl', cache / 'kaikki-hu.jsonl')

        def tsv(name, rows):
            with bz2.open(cache / name, 'wt', encoding='utf-8') as fh:
                fh.write(''.join('\t'.join(map(str, r)) + '\n' for r in rows))

        tsv('hun_sentences.tsv.bz2', [(1, 'hun', 'Látom a házat.'), (2, 'hun', 'Ez egy nagyon nagyon nagyon nagyon hosszú mondat a házról és az almáról meg sok minden másról.')])
        tsv('eng_sentences.tsv.bz2', [(10, 'eng', 'I see the house.'), (20, 'eng', 'A long sentence.')])
        tsv('hun-eng_links.tsv.bz2', [(1, 10), (2, 20)])
        (cache / 'frequencywords-hu.txt').write_text('látom 90\nlát 30\nházat 20\nház 15\nalma 4\nritka 1\n', encoding='utf-8')
        self.out = self.tmp / 'out'
        self.out.mkdir()
        self.args = SimpleNamespace(cache=str(cache), out=str(self.out), limit=None, skip_download=True, skip_sentences=False)

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def build(self, shard_max=None):
        old = bd.FORM_SHARD_MAX_BYTES
        if shard_max:
            bd.FORM_SHARD_MAX_BYTES = shard_max
        try:
            bd.build(self.args)
        finally:
            bd.FORM_SHARD_MAX_BYTES = old
        manifest = json.loads((self.out / 'manifest.json').read_text(encoding='utf-8'))
        return manifest, self.out / manifest['version']

    def lookup(self, manifest, root, folded):
        keys = set(manifest['formShards'])
        chars = bd.shard_key_chars(folded)
        for n in range(len(chars), 0, -1):
            if chars[:n] in keys:
                shard = json.loads((root / 'forms' / f'{chars[:n]}.json').read_text(encoding='utf-8'))
                return shard.get(folded, [])
        return []

    def lemma(self, manifest, root, lid):
        shard = json.loads((root / 'lemmas' / f'{lid // manifest["lemmasPerShard"]}.json').read_text(encoding='utf-8'))
        return shard[str(lid)]

    def test_accent_folded_lookup_finds_accusative(self):
        manifest, root = self.build()
        tags = json.loads((root / 'tags.json').read_text(encoding='utf-8'))
        rows = self.lookup(manifest, root, 'hazat')
        hits = [(form, self.lemma(manifest, root, lid)['w'], tags[t]) for form, lid, t in rows]
        self.assertIn(('házat', 'ház', 'accusative singular'), hits)

    def test_form_of_entries_are_not_lemmas(self):
        manifest, root = self.build()
        words = set()
        for p in (root / 'lemmas').glob('*.json'):
            words.update(v['w'] for v in json.loads(p.read_text(encoding='utf-8')).values())
        self.assertIn('ház', words)
        self.assertNotIn('házat', words)

    def test_sentences_linked_through_forms(self):
        manifest, root = self.build()
        ids = {self.lemma(manifest, root, lid)['w']: lid for _, lid, _ in self.lookup(manifest, root, 'lat')}
        lat = self.lemma(manifest, root, ids['lát'])
        self.assertIn(['Látom a házat.', 'I see the house.'], lat['ex'])

    def test_small_shards_split_and_stay_findable(self):
        manifest, root = self.build(shard_max=2000)
        self.assertTrue(any(len(k) > 1 for k in manifest['formShards']))
        for p in (root / 'forms').glob('*.json'):
            for folded in json.loads(p.read_text(encoding='utf-8')):
                self.assertTrue(self.lookup(manifest, root, folded), folded)

    def test_english_index_shards(self):
        manifest, root = self.build()
        self.assertTrue(manifest['enShards'])
        self.assertGreater(manifest['englishTermCount'], 0)
        keys = set(manifest['enShards'])

        def english(term):
            chars = bd.shard_key_chars(term)
            for n in range(len(chars), 0, -1):
                if chars[:n] in keys:
                    shard = json.loads((root / 'en' / f'{chars[:n]}.json').read_text(encoding='utf-8'))
                    return [self.lemma(manifest, root, lid)['w'] for lid, _, _ in shard.get(term, [])]
            return []

        self.assertEqual(english('house')[0], 'ház')
        self.assertEqual(english('see')[0], 'lát')
        self.assertIn('alma', english('apple'))

    def test_lemmas_carry_frequency_rank(self):
        manifest, root = self.build()
        ranks = {}
        for p in (root / 'lemmas').glob('*.json'):
            for rec in json.loads(p.read_text(encoding='utf-8')).values():
                if 'fr' in rec:
                    ranks[(rec['w'], rec['pos'])] = rec['fr']
        self.assertEqual(ranks[('lát', 'verb')], 1)
        self.assertLess(ranks[('ház', 'noun')], ranks[('alma', 'noun')])
        self.assertNotIn(('jön', 'verb'), ranks)   # not in the frequency fixture

    def test_version_is_content_addressed(self):
        first, _ = self.build()
        second, _ = self.build()
        self.assertEqual(first['version'], second['version'])
        self.assertEqual([p.name for p in self.out.iterdir() if p.is_dir()], [first['version']])


if __name__ == '__main__':
    unittest.main()
