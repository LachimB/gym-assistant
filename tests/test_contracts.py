import sys
import unittest
from pathlib import Path
ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / 'tools'))
from validate_starter import (validate_all, load_fixture, compile_synthetic_timeline, check_schema, read_json)
from copy import deepcopy


class StarterContractsTest(unittest.TestCase):
    def test_all_fixtures_traceability(self):
        n, requirements = validate_all()
        self.assertEqual(n, 23)
        self.assertGreaterEqual(requirements, 80)

    def test_expected_timing_and_order(self):
        trace = compile_synthetic_timeline(load_fixture())
        self.assertEqual(trace['expectedDurationMs'], 50000)
        self.assertEqual([p['name'] for p in trace['phases']],
                         ['prepare', 'hold', 'prepare', 'hold', 'prepare', 'hold', 'rest', 'prepare', 'hold'])
        self.assertEqual(trace['phases'][6]['startMs'], 32000)
        self.assertEqual(trace['phases'][6]['endMs'], 42000)
        self.assertEqual(trace['phases'][-1]['endMs'], 50000)

    def test_reject_unresolved_set_reference(self):
        invalid=deepcopy(load_fixture())
        invalid['sections'][0]['blocks'][0]['order'][0]['setId']='does_not_exist'
        with self.assertRaises(AssertionError):
            compile_synthetic_timeline(invalid)

    def test_reject_duplicate_repetition_coverage(self):
        invalid=deepcopy(load_fixture())
        invalid['sections'][0]['blocks'][0]['order'][1]['repetitionIndex']=2
        with self.assertRaises(AssertionError):
            compile_synthetic_timeline(invalid)

    def test_reject_invalid_schema_version(self):
        invalid=deepcopy(load_fixture())
        invalid['schemaVersion']='999.0'
        with self.assertRaises(AssertionError):
            check_schema('template',invalid)

    def test_reject_negative_duration(self):
        invalid=deepcopy(load_fixture())
        invalid['sections'][0]['blocks'][0]['occurrences'][0]['sets'][0]['phases'][0]['durationMs']=-1
        with self.assertRaises(AssertionError):
            check_schema('template',invalid)

    def test_synthetic_session_separates_effort_and_quality(self):
        session=read_json('examples/sessions/synthetic-completed-session.json')
        self.assertIn('overallRpe',session)
        self.assertIn('personalQualityRating',session)
        self.assertIn('journalSummary',session)

if __name__=='__main__':unittest.main()
