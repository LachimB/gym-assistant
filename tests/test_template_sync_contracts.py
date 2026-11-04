"""Synthetic contract compatibility and rejection at the cloud publication boundary."""
import copy
import hashlib
import json
from pathlib import Path
import unittest
from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parents[1]
def read(path):
    return json.loads((ROOT / path).read_text())

class TemplateSyncContractTests(unittest.TestCase):
    def setUp(self):
        self.revision = read('examples/template-sync/synthetic-revision.json')
        self.head = read('examples/template-sync/synthetic-head.json')
        self.validators = {kind: Draft202012Validator(read(f'schemas/template-sync-{kind}.v1.schema.json')) for kind in ('revision','head')}
        for validator in self.validators.values(): validator.check_schema(validator.schema)

    def test_manifest_matches_existing_cross_language_frozen_source(self):
        self.validators['revision'].validate(self.revision)
        self.validators['head'].validate(self.head)
        session = read('examples/sessions/synthetic-runtime-v2-session.json')
        snapshot = session['sourcePlanSnapshot']
        source = json.loads(snapshot['sourceSnapshotJson'])
        self.assertEqual(self.revision['templateId'], session['templateId'])
        self.assertEqual(self.revision['revisionId'], session['revisionId'])
        self.assertEqual(self.revision['sourceHash'], hashlib.sha256(snapshot['sourceSnapshotJson'].encode()).hexdigest())
        self.assertEqual(self.revision['compiledPlanHash'], hashlib.sha256(snapshot['compiledPlanJson'].encode()).hexdigest())
        self.assertEqual(self.head['sourceHash'], self.revision['sourceHash'])
        values = [source['templateYaml'], *source['exerciseYamls']]
        self.assertEqual(len(self.revision['files']), len(values))
        for descriptor, text in zip(self.revision['files'], values):
            self.assertEqual(descriptor['byteCount'], len(text.encode()))
            self.assertEqual(descriptor['sha256'], hashlib.sha256(text.encode()).hexdigest())

    def test_contract_rejects_unbounded_external_file_descriptors(self):
        for patch in ({'relativeName':'../source.yaml'}, {'relativeName':'https://example.test/source.yaml'},
                      {'relativeName':'nested/source.yaml'}, {'byteCount':1048577}, {'byteCount':0},
                      {'objectId':'https://example.test/file'}, {'sha256':'bad'}, {'role':'pdf'}):
            value = copy.deepcopy(self.revision); value['files'][0].update(patch)
            self.assertTrue(list(self.validators['revision'].iter_errors(value)))

    def test_unsupported_versions_extra_keys_missing_roles_and_oversized_lists_fail(self):
        variants = []
        for kind in ('revision','head'):
            baseline = self.revision if kind == 'revision' else self.head
            for patch in ({'schemaVersion':'2.0.0'}, {'unexpected':'value'}):
                variants.append((kind, {**baseline, **patch}))
        value = copy.deepcopy(self.revision); value['files'] = value['files'] * 9; variants.append(('revision', value))
        value = copy.deepcopy(self.revision); value['files'][0]['role'] = 'exercise'; variants.append(('revision', value))
        for kind, value in variants:
            self.assertTrue(list(self.validators[kind].iter_errors(value)))

if __name__ == '__main__': unittest.main()
