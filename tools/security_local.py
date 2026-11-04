#!/usr/bin/env python3
"""Supplementary full-content index check; CI remains authoritative."""
from pathlib import Path
import sys
from security_gate import HERE, Scanner, git, load_policy


def main():
    try:
        repo = Path(git('.', 'rev-parse', '--show-toplevel').decode().strip())
        scanner = Scanner(load_policy(HERE / 'security/policy.json'), HERE / '.gitleaks.toml')
        names = git(repo, 'diff', '--cached', '--name-only', '--diff-filter=ACMRT', '-z').split(b'\0')
        blocked = review = 0
        for raw_name in filter(None, names):
            name = raw_name.decode()
            data = git(repo, 'show', ':' + name)
            # Inspect actual staged bytes, never worktree replacements.
            findings = scanner.inspect(name, data)
            blocked += sum(f['status'] == 'blocked' for f in findings)
            review += sum(f['status'] == 'review' for f in findings)
        print(f'Staged security inspection: {blocked} blocking findings; {review} contextual findings (independent CI review required). Values withheld.')
        return int(blocked > 0)
    except Exception:
        print('FAIL: staged security scan incomplete; commit blocked.', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
