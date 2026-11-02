"""M1-01: local YAML boundary and referential-integrity acceptance evidence."""
import subprocess
import sys
import tempfile
import unittest
from copy import deepcopy
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / 'tools'))
from template_loader import MAX_BYTES, TemplateValidationError, load_template, read_document


class TemplateLoaderTests(unittest.TestCase):
    def setUp(self):
        self.fixture = ROOT / 'examples/templates/composite-pyramid.yaml'
        self.exercises = sorted((ROOT / 'examples/exercises').glob('*.yaml'))
        self.template = load_template(self.fixture, self.exercises)
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name) / 'template.yaml'

    def write(self, value):
        self.path.write_text(yaml.safe_dump(value), encoding='utf-8')

    def test_load_ungrouped_template_and_preserve_source(self):
        before = self.fixture.read_bytes()
        self.assertEqual(self.template['revisionId'], 'revision_demo_v1')
        self.assertNotIn('organizationContext', self.template)
        self.template['names']['en'] = 'Changed only in memory'
        self.assertNotEqual(load_template(self.fixture, self.exercises), self.template)
        self.assertEqual(self.fixture.read_bytes(), before)

    def test_reject_unsafe_ambiguous_or_malformed_yaml(self):
        for source in ('a: 1\na: 2', 'a: &a [*a]', 'a: &a 1\nb: *a',
                       '!!python/object/apply:os.system [echo unexpected]',
                       '---\na: 1\n---\nb: 2', '[]', '', 'a: [',
                       '1: value', 'a: {<<: {b: 1}}', '[' * 70 + ']' * 70,
                       'a: .nan', 'a: .inf', 'a: 2026-11-02', 'a: !!binary YQ=='):
            with self.subTest(source=source):
                self.path.write_text(source)
                with self.assertRaises(TemplateValidationError):
                    read_document(self.path)

    def test_reject_invalid_encoding_and_oversized_input(self):
        for raw in (b'\xff', b' ' * (MAX_BYTES + 1)):
            self.path.write_bytes(raw)
            with self.assertRaises(TemplateValidationError):
                read_document(self.path)

    def test_reject_schema_errors(self):
        for key, value in [('schemaVersion', '999'), ('names', {'en': ''}),
                           ('sections', []), ('unknown', 'private marker')]:
            changed = deepcopy(self.template)
            changed[key] = value
            self.write(changed)
            with self.assertRaises(TemplateValidationError) as caught:
                load_template(self.path, self.exercises)
            self.assertNotIn('private marker', str(caught.exception))

    def test_reject_semantic_errors(self):
        for case in ('duplicate_set', 'duplicate_occurrence', 'duplicate_phase',
                     'duplicate_step', 'missing_rep', 'duplicate_rep', 'unknown_set',
                     'out_of_range', 'unknown_family', 'wrong_family_variation'):
            with self.subTest(case=case):
                changed = deepcopy(self.template)
                block = changed['sections'][0]['blocks'][0]
                occurrence = block['occurrences'][0]
                if case == 'duplicate_set':
                    occurrence['sets'].append(deepcopy(occurrence['sets'][0]))
                elif case == 'duplicate_occurrence':
                    block['occurrences'].append(deepcopy(occurrence))
                elif case == 'duplicate_phase':
                    occurrence['sets'][0]['phases'].append(deepcopy(occurrence['sets'][0]['phases'][0]))
                elif case == 'duplicate_step':
                    block['order'][1]['stepId'] = block['order'][0]['stepId']
                elif case == 'missing_rep':
                    block['order'].pop()
                elif case == 'duplicate_rep':
                    block['order'][-1]['repetitionIndex'] = 1
                elif case == 'unknown_set':
                    block['order'][0]['setId'] = 'missing_set'
                elif case == 'out_of_range':
                    block['order'][0]['repetitionIndex'] = 2
                elif case == 'unknown_family':
                    occurrence['exerciseId'] = 'missing_family'
                else:
                    occurrence['sets'][0]['variationId'] = 'plank_level_1'
                self.write(changed)
                with self.assertRaises(TemplateValidationError):
                    load_template(self.path, self.exercises)

    def test_require_unique_families_and_variations(self):
        with self.assertRaises(TemplateValidationError):
            load_template(self.fixture, self.exercises * 2)
        exercise = yaml.safe_load(self.exercises[0].read_text())
        exercise['variations'].append(deepcopy(exercise['variations'][0]))
        self.write(exercise)
        with self.assertRaises(TemplateValidationError):
            load_template(self.fixture, [self.path])

    def test_cli_exit_status(self):
        command = [sys.executable, str(ROOT / 'tools/template_loader.py'), str(self.fixture)]
        for path in self.exercises:
            command.extend(['--exercise', str(path)])
        result = subprocess.run(command, capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.path.write_text('names: [')
        command[2] = str(self.path)
        result = subprocess.run(command, capture_output=True, text=True)
        self.assertEqual(result.returncode, 1)
        self.assertNotIn('Traceback', result.stderr)


if __name__ == '__main__':
    unittest.main()
