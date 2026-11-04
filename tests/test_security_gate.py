"""Synthetic-only integration regressions. No source fixtures contain real records."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
from security_gate import HERE, Scanner, apply_review, digest, event_texts, git, load_policy, scan_range, summary
from security_controller import (GitHub, assess, authorized_review, content_snapshot,
                                 fetch_objects, target_numbers)


def fake_token():
    return 'gh' + 'p_' + hashlib.sha256(b'synthetic-security-test-not-a-credential').hexdigest()[:36]


class FixtureRepo:
    def __init__(self, root):
        self.root = Path(root)
        git(root, 'init', '-q')
        git(root, 'config', 'user.name', 'Synthetic Contributor')
        git(root, 'config', 'user.email', '17935459+LachimB@users.noreply.github.com')
        # Actual ignore policy is part of these end-to-end tests.
        (self.root / '.gitignore').write_bytes((HERE / '.gitignore').read_bytes())
        self.base = self.commit('Synthetic baseline')

    def write(self, name, text):
        p = self.root / name
        p.parent.mkdir(exist_ok=True, parents=True)
        p.write_bytes(text.encode() if isinstance(text, str) else text)

    def commit(self, message='Synthetic change', allow_empty=False):
        git(self.root, 'add', '-A', '-f', '.')
        git(self.root, '-c', 'core.hooksPath=/dev/null', 'commit', '--allow-empty', '-qm', message)
        return git(self.root, 'rev-parse', 'HEAD').decode().strip()


class SecurityTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.repo = FixtureRepo(self.tmp.name)
        self.policy = load_policy(HERE / 'security/policy.json')
        # Real scanner required, not skipped when missing.
        self.scanner = Scanner(self.policy, HERE / '.gitleaks.toml')

    def scan(self, head, metadata=()):
        return scan_range(self.repo.root, self.repo.base, head, self.scanner, metadata)

    def findings(self, report):
        return [f for c in report['commits'] for f in c['findings']] + report['metadata']

    def test_01_fake_api_key(self):
        self.assertTrue(self.scanner.secret(('api_key="' + fake_token() + '"').encode()))

    def test_02_fake_private_key(self):
        self.assertTrue(self.scanner.secret(('-----BEGIN ' + 'PRIVATE KEY-----').encode()))

    def test_03_personal_fixture_requires_review(self):
        self.repo.write('fixtures/generated.txt', 'patient' + '_id: synthetic-example')
        report = self.scan(self.repo.commit())
        self.assertEqual(report['contexts']['privacy'], 'failure')
        self.assertIn('precise-location', {f['rule'] for f in self.findings(report)})
        self.assertEqual(report['contexts']['secrets'], 'success')

    def test_04_private_comment_detected(self):
        self.repo.write('source.py', '# contact: person' + '@mail.invalid')
        report = self.scan(self.repo.commit())
        self.assertEqual(report['contexts']['privacy'], 'failure')
        self.assertNotIn('person' + '@mail.invalid', summary(report))

    def test_05_disguised_sensitive_content(self):
        self.repo.write('innocent.txt', b'SQLite format 3\x00synthetic-only')
        self.assertEqual(self.scan(self.repo.commit())['contexts']['hygiene'], 'failure')

    def test_06_archive_and_oversized(self):
        self.repo.write('notes.zip', b'PK\x03\x04synthetic-only')
        self.repo.write('huge.txt', b'Z' * (self.policy['max_file_bytes'] + 1))
        report = self.scan(self.repo.commit())
        self.assertEqual(report['contexts']['hygiene'], 'failure')
        self.assertEqual(report['contexts']['secrets'], 'failure')

    def test_07_normal_code_passes_and_coverage_is_complete(self):
        self.repo.write('source.py', 'count = 1\n')
        first = self.repo.commit()
        self.repo.write('source.py', 'count = 2\n')
        second = self.repo.commit()
        report = self.scan(second)
        self.assertEqual([c['sha'] for c in report['commits']], [first, second])
        self.assertEqual(report['contexts'], dict.fromkeys(('secrets', 'privacy', 'hygiene'), 'success'))
        self.assertEqual([c['objects'] for c in report['commits']], [1, 2])
        # Repeated Git blobs are deduplicated even though each commit is recorded.
        self.assertLessEqual(report['unique_scans'], 4)

    def test_08_legitimate_public_example(self):
        self.repo.write('public-example.txt', 'Synthetic user <sample@example.test>\ncount = 2')
        self.assertTrue(all(v == 'success' for v in self.scan(self.repo.commit())['contexts'].values()))

    def test_09_added_then_removed_secret_and_message_still_fail(self):
        self.repo.write('source.py', 'token="' + fake_token() + '"\n')
        bad = self.repo.commit('Synthetic regression')
        self.repo.write('source.py', 'count = 2\n')
        head = self.repo.commit()
        report = self.scan(head)
        self.assertEqual(report['contexts']['secrets'], 'failure')
        self.assertEqual(report['commits'][0]['sha'], bad)
        self.assertTrue(all(any(f['rule'] == 'gitleaks' for f in c['findings']) for c in report['commits']))
        self.assertNotIn(fake_token(), summary(report))
        self.repo.write('source.py', 'count = 3\n')
        self.assertEqual(self.scan(self.repo.commit(fake_token()))['contexts']['secrets'], 'failure')

    def test_10_pr_suppression_has_no_authority(self):
        self.repo.write('.gitleaksignore', '*\n')
        self.repo.write('security/policy.json', '{"exceptions": ["everything"]}')
        self.repo.write('.gitleaks.toml', '[allowlist]\npaths=[".*"]')
        self.repo.write('source.py', 'token="' + fake_token() + '" # gitleaks:allow')
        report = self.scan(self.repo.commit())
        self.assertEqual(report['contexts']['secrets'], 'failure')
        self.assertIn('protected-control-change', {f['rule'] for f in self.findings(report)})

    def test_11_missing_crashing_or_wrong_version_scanner_fails_closed(self):
        with self.assertRaises(FileNotFoundError):
            Scanner(self.policy, HERE / '.gitleaks.toml', '/does-not-exist/scanner')
        with patch('security_gate.subprocess.run') as run:
            run.return_value.stdout = b'0.0.0'
            with self.assertRaises(ValueError):
                Scanner(self.policy, HERE / '.gitleaks.toml')
        with patch('security_gate.subprocess.run') as run:
            run.return_value.returncode = 2
            with self.assertRaises(RuntimeError):
                self.scanner.secret(b'synthetic')

    def test_12_fork_content_is_data_and_never_executed(self):
        marker = self.repo.root / 'EXECUTED'
        self.repo.write('build.sh', '#!/bin/sh\ntouch EXECUTED\nexit 13\n')
        self.repo.write('.gitattributes', '* export-ignore\n')
        report = self.scan(self.repo.commit())
        self.assertFalse(marker.exists())
        self.assertEqual(report['contexts']['secrets'], 'success')
        source = (HERE / 'tools/security_controller.py').read_text()
        self.assertNotIn("git(repo, 'checkout'", source)
        with tempfile.TemporaryDirectory() as tmp:
            # A real bare fetch from a local stand-in proves no checkout/hooks.
            target = Path(tmp) / 'objects'
            target.mkdir()
            git(target, 'init', '--bare', '-q')
            git(target, 'fetch', '--no-tags', str(self.repo.root), 'HEAD')
            self.assertFalse((target / 'build.sh').exists())

    def test_13_control_modification_requires_independent_review(self):
        self.repo.write('.github/workflows/security-gate.yml', 'jobs: {}')
        report = self.scan(self.repo.commit())
        self.assertEqual(report['contexts']['privacy'], 'failure')
        self.assertIn('protected-control-change', {f['rule'] for f in self.findings(report)})
        apply_review(report, True)
        self.assertEqual(report['contexts']['privacy'], 'success')

    def test_14_merge_parents_and_rebased_commits(self):
        self.repo.write('base.txt', 'safe\n')
        first = self.repo.commit()
        git(self.repo.root, 'checkout', '-qb', 'side', self.repo.base)
        self.repo.write('side.txt', 'safe\n')
        side = self.repo.commit()
        git(self.repo.root, 'checkout', '-q', '-')
        git(self.repo.root, '-c', 'core.hooksPath=/dev/null', 'merge', '--no-ff', '-qm', 'Synthetic merge', 'side')
        head = git(self.repo.root, 'rev-parse', 'HEAD').decode().strip()
        report = self.scan(head)
        self.assertEqual({c['sha'] for c in report['commits']}, {first, side, head})
        self.assertTrue(all(c['technical'] == 'complete' for c in report['commits']))

    def test_15_metadata_comment_and_links_require_inspection(self):
        self.repo.write('source.py', 'count = 1\n')
        head = self.repo.commit()
        report = self.scan(head, [b'normal PR', ('token="' + fake_token() + '"').encode(), b'https://example.test/screenshot.png'])
        self.assertEqual(report['contexts']['secrets'], 'failure')
        self.assertEqual(report['contexts']['privacy'], 'failure')
        self.assertIn('linked-material-not-fetched', {f['rule'] for f in report['metadata']})
        self.assertNotIn(fake_token(), summary(report))

    def test_tracked_ignore_and_missing_protection(self):
        self.repo.write('debug.log', 'synthetic')
        self.assertEqual(self.scan(self.repo.commit())['contexts']['hygiene'], 'failure')
        self.repo.write('.gitignore', '# protection removed\n')
        self.assertIn('missing-ignore-protection', {f['rule'] for f in self.findings(self.scan(self.repo.commit()))})

    def test_symlink_and_lfs_fail_closed(self):
        os.symlink('source.py', self.repo.root / 'link')
        self.repo.write('source.py', 'safe')
        self.repo.write('data.txt', 'version https://git-lfs.github.com/spec/v1\noid sha256:' + '0' * 64)
        report = self.scan(self.repo.commit())
        self.assertEqual(report['contexts']['hygiene'], 'failure')
        source = b"const pointer = 'version https://git-lfs.github.com/spec/v1';"
        self.assertNotIn('unscanned-lfs-object', {f['rule'] for f in self.scanner.inspect('source.js', source)})

    def test_trusted_exception_exact_and_justified(self):
        data = b'patient_id: synthetic-only'
        self.policy['exceptions'] = [{'rule': 'precise-location', 'object_sha256': digest(data),
            'path_sha256': digest(b'fixture.txt'), 'justification': 'Reviewed synthetic contract field',
            'reviewed_by': 'LachimB', 'expires': '2099-01-01'}]
        scanner = Scanner(self.policy, HERE / '.gitleaks.toml')
        self.assertEqual(scanner.inspect('fixture.txt', data)[0]['status'], 'accepted')
        self.assertEqual(scanner.inspect('other.txt', data)[0]['status'], 'review')
        self.assertEqual(scanner.inspect('fixture.txt', data + b' changed')[0]['status'], 'review')
        self.policy['exceptions'][0]['justification'] = ''
        p = self.repo.root / 'policy.json'
        p.write_text(json.dumps(self.policy))
        with self.assertRaises(ValueError):
            load_policy(p)

    def test_review_never_resolves_secret_or_technical_failures(self):
        self.repo.write('source.py', 'token="' + fake_token() + '"\n# patient_id: synthetic')
        report = self.scan(self.repo.commit())
        apply_review(report, True)
        self.assertEqual(report['contexts']['secrets'], 'failure')

    def test_whole_file_and_preimage_not_only_added_lines(self):
        self.repo.write('source.py', '# person' + '@mail.invalid\ncount = 1')
        base = self.repo.commit()
        self.repo.base = base
        self.repo.write('source.py', '# person' + '@mail.invalid\ncount = 2')
        self.assertEqual(self.scan(self.repo.commit())['contexts']['privacy'], 'failure')
        (self.repo.root / 'source.py').unlink()
        self.assertEqual(self.scan(self.repo.commit())['contexts']['privacy'], 'failure')

    def test_cli_review_can_reach_ci_but_technical_errors_cannot(self):
        self.repo.write('docs/synthetic.txt', 'Synthetic public technical documentation')
        head = self.repo.commit()
        command = [sys.executable, str(HERE / 'tools/security_gate.py'), '--repo', str(self.repo.root),
                   '--base', self.repo.base, '--head', head]
        full = subprocess.run(command, capture_output=True)
        supplemental = subprocess.run(command + ['--automated-only'], capture_output=True)
        self.assertEqual(full.returncode, 1)
        self.assertEqual(supplemental.returncode, 0)
        missing = subprocess.run(command + ['--automated-only', '--scanner', '/no/such/scanner'], capture_output=True)
        self.assertEqual(missing.returncode, 1)
        self.repo.write('source.py', 'password="' + fake_token() + '"')
        head = self.repo.commit()
        command[command.index('--head') + 1] = head
        self.assertEqual(subprocess.run(command + ['--automated-only'], capture_output=True).returncode, 1)

    def test_review_digest_stable_across_processes(self):
        self.repo.write('docs/first.txt', 'safe')
        self.repo.write('docs/second.txt', 'safe')
        self.repo.write('security/new.txt', 'safe')
        head = self.repo.commit()
        command = [sys.executable, str(HERE / 'tools/security_gate.py'), '--repo', str(self.repo.root),
                   '--base', self.repo.base, '--head', head]
        first = subprocess.run(command, capture_output=True, env=dict(os.environ, PYTHONHASHSEED='1'))
        second = subprocess.run(command, capture_output=True, env=dict(os.environ, PYTHONHASHSEED='2'))
        self.assertEqual(first.returncode, 1)
        self.assertEqual(first.stdout, second.stdout)

    def test_unquoted_sensitive_assignment_and_certificate(self):
        value = 'password=' + hashlib.sha256(b'synthetic password').hexdigest()
        self.assertTrue(self.scanner.secret(value.encode()))
        findings = self.scanner.inspect('certificate.txt', ('-----BEGIN ' + 'CERTIFICATE-----').encode())
        self.assertIn('certificate-material', {f['rule'] for f in findings})

    def test_scan_budget_and_invalid_refs_fail(self):
        with self.assertRaises(ValueError):
            scan_range(self.repo.root, 'HEAD', '--all', self.scanner)
        self.scanner.policy['max_objects'] = 0
        with self.assertRaises(RuntimeError):
            self.scanner.inspect('file', b'normal')


class ControllerTests(unittest.TestCase):
    def test_review_is_independent_exact_and_permission_checked(self):
        class API:
            def get(self, path):
                return {'permission': 'write'}
        report = {'head': 'a' * 40, 'review_digest': 'b' * 64}
        pr = {'user': {'login': 'contributor'}}
        policy = {'reviewers': ['LachimB']}
        review = {'id': 1, 'user': {'login': 'LachimB'}, 'state': 'APPROVED',
                  'commit_id': report['head'], 'body': 'Security-Privacy-Review: ' + report['head'] + ' ' + report['review_digest']}
        self.assertTrue(authorized_review(API(), pr, [review], report, policy))
        self.assertFalse(authorized_review(API(), {'user': {'login': 'LachimB'}}, [review], report, policy))
        self.assertFalse(authorized_review(API(), pr, [review], dict(report, head='c'*40), policy))
        self.assertFalse(authorized_review(API(), pr, [review, dict(review, state='DISMISSED')], report, policy))

    def test_metadata_pages_all_sources_and_receipt_is_digest_stable(self):
        class API:
            def pages(self, path):
                body = 'synthetic ' + path
                if path.endswith('/reviews'):
                    body = 'Security-Privacy-Review: ' + 'a'*40 + ' ' + 'b'*64
                return [{'id': 1, 'user': {'login': 'contributor'}, 'body': body}]
        texts, reviews = content_snapshot(API(), 1, {'title': 'safe', 'body': 'synthetic', 'user': {'login': 'contributor'}})
        self.assertEqual(len(texts), 3)
        self.assertEqual(len(reviews), 1)

    def test_webhook_text_includes_accessible_previous_values(self):
        event = {'pull_request': {'title': 'title', 'body': 'body'},
                 'comment': {'body': 'comment'}, 'review': {'body': 'review'},
                 'changes': {'body': {'from': 'previous body'}, 'title': {'from': 'previous title'}}}
        self.assertEqual(event_texts(event), [b'title', b'body', b'comment', b'review', b'previous title', b'previous body'])

    def test_event_routing_and_api_errors(self):
        class API:
            def pages(self, path):
                return [{'number': 1}, {'number': 2}]
        self.assertEqual(target_numbers(API(), 'schedule', {}), [1, 2])
        self.assertEqual(target_numbers(API(), 'issue_comment', {'issue': {'number': 2, 'pull_request': {}}}), [2])
        self.assertEqual(target_numbers(API(), 'issue_comment', {'issue': {'number': 2}}), [])
        for event in ('pull_request_review', 'pull_request_review_comment'):
            self.assertEqual(target_numbers(API(), 'workflow_run', {'workflow_run': {'event': event, 'pull_requests': [{'number': 3}]}}), [3])
        api = GitHub('LachimB/gym-assistant', 'synthetic-token')
        with self.assertRaises(ValueError):
            api.request('/unexpected')
        with patch.object(api, 'get', return_value={'invalid': 'shape'}):
            with self.assertRaises(ValueError):
                api.pages('/pulls')

    def test_controller_publishes_failure_on_scanner_failure(self):
        class API:
            repo = 'LachimB/gym-assistant'
            def __init__(self): self.states = []
            def get(self, path):
                return {'state': 'open', 'head': {'sha': 'a'*40}, 'base': {'sha': 'b'*40}}
            def status(self, sha, context, state, url): self.states.append((context, state))
        api = API()
        with patch('security_controller.content_snapshot', side_effect=RuntimeError('synthetic failure')):
            self.assertFalse(assess(api, 1, 'https://example.test/run'))
        self.assertEqual(api.states[-3:], [(c, 'failure') for c in ('secrets', 'privacy', 'hygiene')])

    def test_controller_success_failure_and_stale_metadata(self):
        class API:
            repo = 'LachimB/gym-assistant'
            def __init__(self): self.states = []
            def get(self, path):
                return {'state': 'open', 'head': {'sha': 'a'*40}, 'base': {'sha': 'b'*40}, 'user': {'login': 'contributor'}}
            def status(self, sha, context, state, url): self.states.append((context, state))
        report = {'head': 'a'*40, 'review_digest': 'c'*64, 'commits': [], 'metadata': [],
                  'contexts': dict.fromkeys(('secrets', 'privacy', 'hygiene'), 'success')}
        with patch('security_controller.content_snapshot', return_value=([b'safe'], [])), \
             patch('security_controller.fetch_objects'), patch('security_controller.Scanner'), \
             patch('security_controller.scan_range', return_value=report), \
             patch('security_controller.authorized_review', return_value=False):
            api = API()
            self.assertTrue(assess(api, 1, 'https://example.test/run'))
            self.assertTrue(all(s == 'success' for _, s in api.states[-3:]))
        with patch('security_controller.content_snapshot', side_effect=[([b'safe'], []), ([b'changed'], [])]), \
             patch('security_controller.fetch_objects'), patch('security_controller.Scanner'), \
             patch('security_controller.scan_range', return_value=report), \
             patch('security_controller.authorized_review', return_value=False):
            api = API()
            self.assertFalse(assess(api, 1, 'https://example.test/run'))
            self.assertTrue(all(s == 'failure' for _, s in api.states[-3:]))

    def test_workflow_structure_pins_and_permissions(self):
        import yaml
        trusted = yaml.safe_load((HERE / '.github/workflows/security-gate.yml').read_text())
        # PyYAML 1.1 treats the unquoted GitHub `on` key as True.
        events = trusted.get('on', trusted.get(True))
        self.assertEqual(set(events), {'pull_request_target', 'issue_comment', 'workflow_run', 'schedule', 'workflow_dispatch'})
        self.assertEqual(trusted['permissions'], {'contents': 'read', 'pull-requests': 'read', 'statuses': 'write'})
        steps = trusted['jobs']['assess']['steps']
        self.assertEqual(steps[0]['with']['ref'], '${{ github.event.repository.default_branch }}')
        self.assertFalse(steps[0]['with']['persist-credentials'])
        self.assertNotIn('paths', events['pull_request_target'])
        read_only = yaml.safe_load((HERE / '.github/workflows/privacy.yml').read_text())
        self.assertTrue(all(p == 'read' for p in read_only['permissions'].values()))
        unpriv_events = read_only.get('on', read_only.get(True))
        self.assertTrue({'pull_request_review', 'pull_request_review_comment'} <= set(unpriv_events))
        for workflow in (trusted, read_only):
            for job in workflow['jobs'].values():
                for step in job['steps']:
                    if 'uses' in step:
                        self.assertRegex(step['uses'], r'@[a-f0-9]{40}$')
        rules = json.loads((HERE / 'security/ruleset.json').read_text())
        self.assertEqual(rules['bypass_actors'], [])
        required = rules['rules'][-1]['parameters']['required_status_checks']
        self.assertTrue({'Security / secrets', 'Security / privacy', 'Security / hygiene'} <= {r['context'] for r in required})


if __name__ == '__main__':
    unittest.main()
