"""Adversarial checks use generated fake disclosures, never private source values."""
from pathlib import Path
import os
import subprocess
import tempfile
import unittest
from tools.check_repo_hygiene import NOREPLY, filename_issue, inspect_content, inspect_revision, scan


class PrivacyGateTests(unittest.TestCase):
    def test_sensitive_names_include_nested_exports_and_database_sidecars(self):
        for name in ('secrets.json', 'client_secret_demo.json', 'data/export.sql',
                     'data/cache.db-wal', 'data/cache.sqlite3-shm', 'trace.har',
                     'crash.hprof', 'backup.zip', 'export.apk', 'key.key',
                     'nested/private-evidence/evidence.json', 'notes/photo.png'):
            with self.subTest(name=name):
                self.assertIsNotNone(filename_issue(name))
        for name in ('docs/product/traceability.csv', '.env.example', 'examples/sessions/synthetic.json'):
            self.assertIsNone(filename_issue(name))

    def test_contact_resource_host_and_context_disclosures_are_rejected(self):
        samples = [
            'person' + '@' + 'mail.invalid',
            '/' + 'Users' + '/local-user/private-project',
            'https://' + 'drive.google.com/drive/folders/' + '1' + 'Z' * 30,
            'resource=' + '1' + 'Z' * 30,
            '-----BEGIN ' + 'PRIVATE KEY-----',
            'owner' + 'ReportedOutcome: completed',
            'private ' + 'workout plans 45' + 'm50',
        ]
        for value in samples:
            with self.subTest(kind=samples.index(value)):
                self.assertTrue(inspect_content('notes.md', value.encode()))

    def test_reserved_examples_noreply_and_hashes_pass(self):
        text = 'fixture@example.test other@example.invalid\n' + NOREPLY + '\n' + '1a' * 32
        self.assertEqual(inspect_content('examples/synthetic.txt', text.encode()), [])
        self.assertTrue(inspect_content('unknown.bin', b'\x00private'))

    def test_index_and_history_cannot_hide_content_with_clean_worktree(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            def git(*args):
                return subprocess.check_output(['git', '-C', str(root), *args], stderr=subprocess.DEVNULL)
            git('init', '-q')
            git('config', 'user.name', 'LachimB')
            git('config', 'user.email', NOREPLY)
            target = root / 'notes.md'
            target.write_text('person' + '@' + 'mail.invalid')
            git('add', 'notes.md')
            target.write_text('safe synthetic summary')
            self.assertEqual(scan(root), [])
            self.assertTrue(scan(root, staged=True))
            # Commit synthetic forbidden index bytes to prove removed content is
            # still rejected when scanning a newly introduced historical revision.
            git('-c', 'core.hooksPath=/dev/null', 'commit', '-qm', 'Synthetic privacy regression')
            sha = git('rev-parse', 'HEAD').decode().strip()
            self.assertTrue(inspect_revision(root, sha))
            git('add', 'notes.md')
            self.assertEqual(scan(root, staged=True), [])
            self.assertTrue(inspect_revision(root, sha))
            git('-c', 'core.hooksPath=/dev/null', 'commit', '-qm', 'Clean synthetic replacement')
            script = Path(__file__).resolve().parent.parent / 'tools/check_repo_hygiene.py'
            result = subprocess.run([os.sys.executable, str(script), '--commit', 'HEAD'], cwd=root, capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            result = subprocess.run([os.sys.executable, str(script), '--commit', sha], cwd=root, capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertNotIn('person' + '@' + 'mail.invalid', result.stdout + result.stderr)

    def test_gitignore_covers_private_operational_file_families(self):
        root = Path(__file__).resolve().parent.parent
        names = ['client_secret_demo.json', 'cache.sqlite3-wal', 'capture.har',
                 'private-exports/arbitrary.json', 'nested/private-config/settings.yaml',
                 'private-evidence/arbitrary.txt', 'backup.zip', 'crash.hprof']
        result = subprocess.run(['git', 'check-ignore', '--no-index', '--stdin'],
                                cwd=root, input='\n'.join(names)+'\n', text=True, capture_output=True)
        self.assertEqual(set(result.stdout.splitlines()), set(names))


if __name__ == '__main__':
    unittest.main()
