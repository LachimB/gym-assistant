import sys
import tempfile
import unittest
from copy import deepcopy
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / 'tools'))
from template_loader import load_template, TemplateValidationError
from template_review import changed_paths, review_external_edit


class TemplateReviewTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.base = Path(temp.name) / 'base.yaml'
        self.candidate = Path(temp.name) / 'candidate.yaml'
        self.base.write_bytes((ROOT / 'examples/templates/composite-pyramid.yaml').read_bytes())
        self.original = self.base.read_bytes()
        self.exercises = sorted((ROOT / 'examples/exercises').glob('*.yaml'))
        self.document = load_template(self.base, self.exercises)
        self.revision = self.document['revisionId']
        self.edited = deepcopy(self.document)
        self.edited['revisionId'] = 'revision_demo_v2'

    def review(self, document=None, revision=None):
        self.candidate.write_text(yaml.safe_dump(document if document is not None else self.edited))
        before = self.candidate.read_bytes()
        result = review_external_edit(self.base, self.candidate, self.exercises,
                                      revision if revision is not None else self.revision)
        self.assertEqual(self.base.read_bytes(), self.original)
        self.assertEqual(self.candidate.read_bytes(), before)
        return result

    def test_unchanged_ignores_yaml_format_and_key_order(self):
        self.assertEqual(self.review(self.document).status, 'unchanged')

    def test_parameter_preview_keeps_known_good_and_requires_review(self):
        phase = self.edited['sections'][0]['blocks'][0]['occurrences'][0]['sets'][0]['phases'][0]
        phase['durationMs'] = 5000
        review = self.review()
        self.assertEqual(review.status, 'review_required')
        self.assertIn('/sections/0/blocks/0/occurrences/0/sets/0/phases/0/durationMs', review.changed_paths)
        self.assertEqual(review.identity_changes, ())

    def test_stale_base_precedes_candidate_read(self):
        result = review_external_edit(self.base, self.candidate, self.exercises, 'stale_revision')
        self.assertEqual(result.status, 'stale_base')
        self.assertEqual(self.base.read_bytes(), self.original)

    def test_same_revision_different_content_cannot_replace_base(self):
        self.edited['revisionId'] = self.revision
        self.edited['names']['en'] = 'Changed'
        self.assertEqual(self.review().status, 'revision_reused')

    def test_template_identity_change_is_rejected(self):
        self.edited['templateId'] = 'other_template'
        self.assertEqual(self.review().status, 'template_identity_changed')

    def test_set_id_replacement_is_visible_not_inferred_as_rename(self):
        block = self.edited['sections'][0]['blocks'][0]
        block['occurrences'][0]['sets'][0]['setId'] = 'replacement_set'
        block['order'][0]['setId'] = 'replacement_set'
        result = self.review()
        self.assertEqual(result.status, 'identity_review_required')
        self.assertIn('added:set:replacement_set', result.identity_changes)
        self.assertIn('removed:set:set_hip_yellow', result.identity_changes)

    def test_variation_retargeting_requires_identity_review(self):
        self.edited['sections'][0]['blocks'][0]['occurrences'][0]['sets'][0]['variationId'] = 'hip_lift_level_2'
        result = self.review()
        self.assertEqual(result.status, 'identity_review_required')
        self.assertIn('retargeted:set:set_hip_yellow', result.identity_changes)

    def test_reordering_is_visible_but_keeps_identity(self):
        self.edited['sections'][0]['blocks'][0]['occurrences'].reverse()
        result = self.review()
        self.assertEqual(result.status, 'review_required')
        self.assertTrue(result.changed_paths)
        self.assertEqual(result.identity_changes, ())

    def test_invalid_or_missing_candidate_preserves_base(self):
        self.candidate.write_text('names: [')
        result = review_external_edit(self.base, self.candidate, self.exercises, self.revision)
        self.assertEqual(result.status, 'invalid_candidate')
        self.candidate.unlink()
        result = review_external_edit(self.base, self.candidate, self.exercises, self.revision)
        self.assertEqual(result.status, 'unreadable_candidate')
        self.assertEqual(self.base.read_bytes(), self.original)

    def test_bad_base_has_no_invented_fallback(self):
        self.base.write_text('invalid')
        with self.assertRaises(TemplateValidationError):
            review_external_edit(self.base, self.candidate, self.exercises, self.revision)

    def test_json_pointer_escaping_and_type_changes(self):
        self.assertEqual(changed_paths({'a/b~': 1}, {'a/b~': True}), ('/a~1b~0',))


if __name__ == '__main__':
    unittest.main()
