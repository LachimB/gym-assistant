"""Offline validation of the proposed manual workflow's target guards."""
from copy import deepcopy
import contextlib
import io
import os
from pathlib import Path
import sys
import unittest
from unittest.mock import patch
import yaml

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
import security_controller


class PinnedWorkflowTests(unittest.TestCase):
    def setUp(self):
        self.workflow = yaml.safe_load((Path(__file__).resolve().parents[1]
            / '.github/workflows/security-gate.yml').read_text())
        script = self.workflow['jobs']['assess']['steps'][-1]['run']
        self.code = compile(script.split("<<'PYTHON'\n", 1)[1].rsplit('\nPYTHON', 1)[0],
                            '<synthetic-manual-workflow>', 'exec')
        self.env = {'GITHUB_ACTOR': 'LachimB', 'GITHUB_REPOSITORY': 'LachimB/gym-assistant',
                    'GITHUB_EVENT_NAME': 'workflow_dispatch', 'GH_TOKEN': 'synthetic-token',
                    'GITHUB_RUN_ID': '123'}
        self.target = {'state': 'open', 'user': {'login': 'LachimB'},
            'head': {'sha': 'a97faab93190ccaeb7af3e9053a3b841ec4fc75b'},
            'base': {'sha': 'e66847fb68147e07c912799b35068d9caed4daaa'}}

    def run_code(self, env, target):
        with patch.dict(os.environ, env, clear=True), \
             patch.object(security_controller, 'GitHub') as client, \
             patch.object(security_controller, 'assess', return_value=False) as assess:
            client.return_value.get.return_value = target
            with contextlib.redirect_stdout(io.StringIO()), self.assertRaises(SystemExit) as result:
                exec(self.code, {})
            return result.exception.code, client, assess

    def test_invalid_execution_context_never_creates_client(self):
        for field in ['GITHUB_ACTOR', 'GITHUB_REPOSITORY', 'GITHUB_EVENT_NAME']:
            env = dict(self.env, **{field: 'synthetic-unapproved'})
            status, client, assess = self.run_code(env, self.target)
            self.assertEqual(status, 1)
            client.assert_not_called()
            assess.assert_not_called()

    def test_changed_target_never_publishes(self):
        for field in ['state', 'user', 'head', 'base']:
            target = deepcopy(self.target)
            target[field] = 'closed' if field == 'state' else {}
            status, client, assess = self.run_code(self.env, target)
            self.assertEqual(status, 1)
            client.return_value.get.assert_called_once_with('/pulls/51')
            client.return_value.status.assert_not_called()
            assess.assert_not_called()

    def test_exact_target_runs_existing_assessment_and_preserves_failure(self):
        status, client, assess = self.run_code(self.env, self.target)
        self.assertEqual(status, 1)
        assess.assert_called_once_with(client.return_value, 51,
            'https://github.com/LachimB/gym-assistant/actions/runs/123')
        client.return_value.status.assert_not_called()

    def test_workflow_is_manual_pinned_and_minimally_privileged(self):
        self.assertEqual(self.workflow.get('on', self.workflow.get(True)), {'workflow_dispatch': None})
        self.assertEqual(self.workflow['permissions'],
            {'contents': 'read', 'pull-requests': 'read', 'statuses': 'write'})
        self.assertEqual(self.workflow['concurrency'],
            {'group': 'trusted-security-controller', 'cancel-in-progress': False})
        checkout = self.workflow['jobs']['assess']['steps'][0]['with']
        self.assertEqual(checkout['ref'], '16ce6048afe1d73cb7feb4e23bdd8caa85814421')
        self.assertIs(checkout['persist-credentials'], False)


if __name__ == '__main__':
    unittest.main()
