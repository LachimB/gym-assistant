import copy
import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from session_history_contract import validate_history


class SessionHistoryContractTests(unittest.TestCase):
    def history(self, kind='partial'):
        return json.loads((ROOT / f'examples/sessions/synthetic-human-{kind}-session.json').read_text())

    def rejects(self, value):
        with self.assertRaises((ValueError, AssertionError, KeyError)):
            validate_history(value)

    def test_actual_kotlin_fixtures_have_only_human_events_and_independently_verifiable_hash(self):
        for kind in ['partial', 'completed']:
            value = self.history(kind)
            before = copy.deepcopy(value)
            self.assertIs(validate_history(value), value)
            self.assertEqual(before, value)
            self.assertNotIn('recording', value)
            self.assertNotIn('tick', [e['type'] for e in value['events']])
            self.assertNotIn('checkpoint', value['sourcePlanSnapshot'])
        self.assertEqual(self.history()['timing']['unknownGapCount'], 2)
        self.assertNotIn('activeDurationMs', self.history()['timing'])

    def test_previous_version_remains_readable_without_inventing_new_timing(self):
        for path in (ROOT / 'examples/sessions').glob('*.json'):
            value = json.loads(path.read_text())
            if value['schemaVersion'] == '1.0.0':
                before = copy.deepcopy(value)
                self.assertIs(validate_history(value), value)
                self.assertEqual(before, value)

    def test_raw_internals_and_tick_events_are_rejected_not_hidden(self):
        for key in ['recording', 'actions', 'checkpoint', 'outbox', 'operationId']:
            value = self.history(); value[key] = {}
            self.rejects(value)
        value = self.history(); value['events'][1]['type'] = 'tick'; self.rejects(value)
        value = self.history(); value['events'][1]['detail'] = {'receipt': {}}; self.rejects(value)
        value = self.history(); value['sourcePlanSnapshot']['checkpoint'] = {}; self.rejects(value)

    def test_unknown_versions_and_corrupt_frozen_source_are_rejected(self):
        for changed in [{'schemaVersion': '2.0.0'}, {'executionPlanHash': 'sha256:' + '0' * 64},
                        {'engineVersion': 'future-engine'}, {'localTrainingDate': '2026-11-01'}]:
            self.rejects({**self.history(), **changed})
        value = self.history(); value['sourcePlanSnapshot']['compiledPlanJson'] += ' '; self.rejects(value)

    def test_session_wide_events_do_not_claim_a_phase(self):
        value = self.history(); value['events'][0]['planStepId'] = 'invented_phase'; self.rejects(value)
        value = self.history(); del value['events'][1]['planStepId']; self.rejects(value)
        value = self.history(); value['events'][1]['planStepId'] = 'unknown_step'; self.rejects(value)

    def test_unknown_gap_stays_unknown(self):
        value = self.history()
        gap = next(e for e in value['events'] if e['type'] == 'interruption')
        self.assertFalse(gap['detail']['gapDurationKnown'])
        self.assertNotIn('gapDurationMs', gap['detail'])
        gap['detail']['gapDurationMs'] = 0; self.rejects(value)
        value = self.history(); gap = next(e for e in value['events'] if e['type'] == 'interruption')
        del gap['detail']; self.rejects(value)

    def test_inconsistent_outcomes_counters_and_duplicate_events_are_rejected(self):
        value = self.history(); value['status'] = 'inProgress'; self.rejects(value)
        value = self.history(); value['completionKind'] = 'plannedEnd'; self.rejects(value)
        value = self.history(); value['events'][-1]['recordedAt'] = '2026-11-03T10:01:00Z'; self.rejects(value)
        value = self.history(); value['events'].insert(1, copy.deepcopy(value['events'][0])); self.rejects(value)
        value = self.history(); value['events'][1]['observedRunMs'] = value['timing']['observedRunMs'] + 1; self.rejects(value)
        value = self.history(); value['timing']['scheduledElapsedMs'] = 50001; self.rejects(value)


if __name__ == '__main__':
    unittest.main()
