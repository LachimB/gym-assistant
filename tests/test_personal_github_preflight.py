"""Personal identity safeguards tested with disposable Git repos and fake gh."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parent.parent
SCRIPT = ROOT / 'tools/preflight_personal_github.sh'


class PersonalGithubPreflightTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.repo = Path(temporary.name).resolve()
        self.bin = self.repo / 'bin'
        self.bin.mkdir()
        self.env = os.environ.copy()
        for name in ('GH_TOKEN', 'GITHUB_TOKEN', 'GH_ENTERPRISE_TOKEN', 'GITHUB_ENTERPRISE_TOKEN'):
            self.env.pop(name, None)
        self.env.update(EXPECTED_GITHUB_OWNER='LachimB', EXPECTED_GIT_AUTHOR_NAME='LachimB', EXPECTED_GIT_AUTHOR_EMAIL='<private-contact-redacted>',
                        EXPECTED_LOCAL_REPO_PATH=str(self.repo),
                        TEST_GH_LOGIN='LachimB', TEST_GH_VISIBILITY='PUBLIC')
        self.env['PATH'] = str(self.bin) + os.pathsep + self.env['PATH']
        fake = self.bin / 'gh'
        fake.write_text('''#!/bin/sh
case "$*" in
  'api user --jq .login') printf '%s\\n' "$TEST_GH_LOGIN" ;;
  'repo view LachimB/gym-assistant --json visibility --jq .visibility') printf '%s\\n' "$TEST_GH_VISIBILITY" ;;
  *) exit 99 ;;
esac
''')
        fake.chmod(0o755)
        self.git('init', '-q')
        self.git('config', '--local', 'user.name', 'LachimB')
        self.git('config', '--local', 'user.email', '<private-contact-redacted>')
        self.git('remote', 'add', 'origin', 'https://github.com/LachimB/gym-assistant.git')

    def git(self, *args):
        subprocess.run(['git', *args], cwd=self.repo, env=self.env, check=True, capture_output=True)

    def run_preflight(self, *args):
        return subprocess.run(['bash', str(SCRIPT), *args], cwd=self.repo,
                              env=self.env, capture_output=True, text=True)

    def test_confirmed_owner_public_remote_and_local_author_pass(self):
        result = self.run_preflight()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn('verified gh owner LachimB', result.stdout)

    def test_expected_owner_cannot_be_changed_via_environment(self):
        self.env['EXPECTED_GITHUB_OWNER'] = 'wrong-owner'
        self.assertNotEqual(self.run_preflight('--bootstrap').returncode, 0)

    def test_effective_account_mismatch_stops(self):
        self.env['TEST_GH_LOGIN'] = 'other-account'
        self.assertNotEqual(self.run_preflight().returncode, 0)

    def test_all_token_overrides_stop_without_printing_values(self):
        for name in ('GH_TOKEN', 'GITHUB_TOKEN', 'GH_ENTERPRISE_TOKEN', 'GITHUB_ENTERPRISE_TOKEN'):
            with self.subTest(name=name):
                self.env[name] = 'synthetic-secret-never-print'
                result = self.run_preflight()
                self.env.pop(name)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn(name, result.stderr)
                self.assertNotIn('synthetic-secret-never-print', result.stdout + result.stderr)

    def test_wrong_local_author_name_and_email_stop(self):
        self.git('config', '--local', 'user.name', 'wrong-author')
        self.assertNotEqual(self.run_preflight().returncode, 0)
        self.git('config', '--local', 'user.name', 'LachimB')
        self.git('config', '--local', 'user.email', 'different@example.invalid')
        self.assertNotEqual(self.run_preflight().returncode, 0)

    def test_wrong_owner_remote_and_ssh_stop(self):
        for remote in ('https://github.com/other-owner/gym-assistant.git',
                       'git@github.com:LachimB/gym-assistant.git'):
            self.git('remote', 'set-url', 'origin', remote)
            self.assertNotEqual(self.run_preflight().returncode, 0)
        self.git('remote', 'set-url', 'origin', 'https://github.com/LachimB/gym-assistant.git')

    def test_unconfirmed_visibility_stops(self):
        for visibility in ('PRIVATE', 'INTERNAL', ''):
            with self.subTest(visibility=visibility):
                self.env['TEST_GH_VISIBILITY'] = visibility
                self.assertNotEqual(self.run_preflight().returncode, 0)


if __name__ == '__main__':
    unittest.main()
