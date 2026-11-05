"""Offline regressions for content-free status publication diagnostics."""
import contextlib
import io
import json
from pathlib import Path
import sys
import unittest
from unittest.mock import patch
import urllib.error

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
from security_controller import GitHub


class PublicationDiagnosticTests(unittest.TestCase):
    def setUp(self):
        self.api = GitHub('LachimB/gym-assistant', 'synthetic-token')
        self.sha = 'a' * 40
        self.url = 'https://example.test/synthetic-run'

    def test_errors_are_content_free_and_propagate_without_retry(self):
        canary = 'synthetic-' + 'withheld-response-content'
        errors = [
            (urllib.error.HTTPError('https://example.test/' + canary, 403, canary,
                                    {'X-Synthetic': canary}, io.BytesIO(canary.encode())), 'http-403'),
            (urllib.error.HTTPError(canary, canary, canary, {}, None), 'http'),
            (json.JSONDecodeError(canary, canary, 0), 'invalid-json'),
            (TimeoutError(canary), 'timeout'),
            (urllib.error.URLError(canary), 'transport'),
            (RuntimeError(canary), 'unexpected'),
        ]
        for error, category in errors:
            with self.subTest(category=category), patch.object(self.api, 'request', side_effect=error) as request:
                output = io.StringIO()
                with contextlib.redirect_stdout(output), self.assertRaises(type(error)) as raised:
                    self.api.status(self.sha, 'privacy', 'success', self.url)
                self.assertIs(raised.exception, error)
                request.assert_called_once()
                self.assertEqual(output.getvalue(),
                    f'STATUS PUBLICATION FAILED: context=privacy state=success category={category}\n')
                self.assertNotIn(canary, output.getvalue())

    def test_success_preserves_payload_without_diagnostic(self):
        with patch.object(self.api, 'request', return_value={}) as request:
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                self.api.status(self.sha, 'privacy', 'success', self.url)
            self.assertEqual(output.getvalue(), '')
            request.assert_called_once_with('/repos/LachimB/gym-assistant/statuses/' + self.sha,
                {'state': 'success', 'context': 'Security / privacy',
                 'description': 'See redacted security assessment', 'target_url': self.url})

    def test_invalid_status_is_rejected_before_request_or_diagnostic(self):
        for sha, context, state in [('bad', 'privacy', 'success'),
                                    (self.sha, 'unknown', 'success'),
                                    (self.sha, 'privacy', 'unknown')]:
            with patch.object(self.api, 'request') as request:
                output = io.StringIO()
                with contextlib.redirect_stdout(output), self.assertRaises(ValueError):
                    self.api.status(sha, context, state, self.url)
                request.assert_not_called()
                self.assertEqual(output.getvalue(), '')


if __name__ == '__main__':
    unittest.main()
