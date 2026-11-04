#!/usr/bin/env python3
"""Trusted, content-only PR scanner. Never execute code or print source content."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
import tempfile

# This module and the legacy privacy policy must come from the trusted checkout.
from check_repo_hygiene import NOREPLY, OWNER_NOREPLY_ALIASES, inspect_content, filename_issue

HERE = Path(__file__).resolve().parent.parent
SHA = re.compile(r'^[0-9a-f]{40}$')
CONTEXTS = ('secrets', 'privacy', 'hygiene')


def digest(data):
    return hashlib.sha256(data).hexdigest()


def git_environment(index=False):
    # Git hooks export GIT_DIR/GIT_INDEX_FILE and push -c config. Never let
    # those redirect scratch operations back into the developer's repository.
    env = {k: v for k, v in os.environ.items() if not k.startswith('GIT_')}
    env.update(GIT_CONFIG_NOSYSTEM='1', GIT_CONFIG_GLOBAL=os.devnull, GIT_TERMINAL_PROMPT='0',
               GIT_NO_REPLACE_OBJECTS='1')
    if index and 'GIT_INDEX_FILE' in os.environ:
        env['GIT_INDEX_FILE'] = os.environ['GIT_INDEX_FILE']
    return env


def git(repo, *args, data=None, index=False):
    return subprocess.run(['git', '-c', 'core.hooksPath=/dev/null', '-C', str(repo), *args],
                          input=data, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                          check=True, timeout=120, env=git_environment(index)).stdout


def load_policy(path):
    policy = json.loads(Path(path).read_text())
    if policy.get('owner_self_review_login') not in {None, 'LachimB'}:
        raise ValueError('invalid owner self-review identity')
    if not isinstance(policy.get('require_pr_review', False), bool):
        raise ValueError('invalid PR review requirement')
    for item in policy['rules']:
        re.compile(item['pattern'])
    for item in policy['exceptions']:
        if (set(item) != {'rule', 'object_sha256', 'path_sha256', 'justification', 'reviewed_by', 'expires'}
                or not item['justification'].strip() or item['reviewed_by'] not in policy['reviewers']
                or not re.fullmatch('[0-9a-f]{64}', item['object_sha256'])
                or not re.fullmatch('[0-9a-f]{64}', item['path_sha256'])):
            raise ValueError('invalid exception')
        datetime.date.fromisoformat(item['expires'])
    return policy


class Scanner:
    def __init__(self, policy, config, executable='gitleaks'):
        self.policy, self.config, self.executable = policy, Path(config).resolve(), executable
        # Ignore user config, inline allow directives, ignore files and PR configs.
        self.environment = {k: v for k, v in os.environ.items()
                            if not k.startswith(('GITLEAKS_', 'GITHUB_', 'GH_', 'GIT_'))}
        self.cache = {}
        self.total = 0
        version = subprocess.run([executable, 'version'], capture_output=True, timeout=15,
                                 env=self.environment, check=True).stdout.decode().strip()
        if version.lstrip('v') != policy['gitleaks_version']:
            raise ValueError('scanner version mismatch')

    def secret(self, data):
        # stdin has no filename/ignore-file suppression; stderr/stdout never escape.
        with tempfile.TemporaryDirectory(prefix='security-scanner-') as tmp:
            result = subprocess.run([self.executable, 'stdin', '--config', str(self.config),
                                     '--redact=100', '--ignore-gitleaks-allow', '--no-banner',
                                     '--exit-code', '42', '--max-decode-depth', '5',
                                     '--max-archive-depth', '2'], input=data, capture_output=True,
                                    env=self.environment, cwd=tmp, timeout=60)
        if result.returncode not in (0, 42):
            raise RuntimeError('scanner failed')
        return result.returncode == 42

    def inspect(self, name, data, mode='100644', metadata=False):
        key = (name, digest(data), mode, metadata)
        if key in self.cache:
            return self.cache[key]
        self.total += len(data)
        if len(self.cache) >= self.policy['max_objects'] or self.total > self.policy['max_total_bytes']:
            raise RuntimeError('scan budget exceeded; split PR or arrange explicit offline review')
        findings = []

        def add(category, rule, level='blocked'):
            # Only exact trusted privacy review findings can be excepted.
            accepted = level == 'review' and any(
                item['rule'] == rule and item['object_sha256'] == digest(data)
                and item['path_sha256'] == digest(name.encode())
                and datetime.date.fromisoformat(item['expires']) >= datetime.date.today()
                for item in self.policy['exceptions'])
            findings.append({'category': category, 'rule': rule,
                             'status': 'accepted' if accepted else level,
                             'id': digest((category + rule + name).encode() + data)})

        if len(data) > self.policy['max_file_bytes']:
            add('hygiene', 'oversized-object')
            # Refuse incomplete secret coverage; never mark a truncated scan clean.
            add('secrets', 'unscanned-oversized-object')
        elif self.secret(data):
            add('secrets', 'gitleaks')
        # Filenames can themselves contain secrets/private facts.
        if not metadata:
            for _, reason in inspect_content('filename', name.encode()):
                add('privacy', 'filename-' + reason)
            if self.secret(name.encode()):
                add('secrets', 'gitleaks-filename')
            if filename_issue(name):
                add('hygiene', 'forbidden-file')
            parts = PurePosixPath(name.lower()).parts
            if any(p in {'.ds_store', 'thumbs.db', '.vscode', '.idea', '__pycache__', 'node_modules'} for p in parts) or name.endswith(('~', '.swp', '.tmp', '.temp', '.crash')):
                add('hygiene', 'machine-or-temporary-file')
            if mode not in {'100644', '100755'}:
                add('hygiene', 'nonregular-git-entry')
        expected_binary = self.policy['binary_sha256'].get(name)
        accepted_binary = expected_binary == digest(data)
        # Existing policy retains strict rules on personal addresses, private
        # paths and real provider identifiers; no blanket email/name heuristic.
        for _, reason in inspect_content(name, data):
            add('hygiene' if 'binary' in reason or 'unreadable' in reason else 'privacy',
                'legacy-' + reason)
        if expected_binary and not accepted_binary:
            add('hygiene', 'binary-digest-changed')
        if not accepted_binary:
            text = data.decode('utf-8', errors='replace')
            for rule in self.policy['rules']:
                if re.search(rule['pattern'], text):
                    add('privacy', rule['id'], 'review')
            if data.startswith((b'SQLite format', b'PK\x03\x04', b'%PDF', b'\x89PNG', b'\xff\xd8', b'\x1f\x8b', b'\x7fELF')):
                add('hygiene', 'sensitive-file-signature')
            if re.search(r'(?im)^\s*(?:CREATE TABLE|INSERT INTO|COPY\s+\w+\s*\()', text):
                add('privacy', 'possible-database-export', 'review')
            if text.lstrip().startswith('version https://git-lfs.github.com/spec/'):
                add('hygiene', 'unscanned-lfs-object')
        if metadata and re.search(r'https?://[^\s<>\[\]()]+', data.decode('utf-8', errors='replace')):
            # Never fetch contributor-controlled URLs: SSRF, redirects, auth and
            # mutable attachments require a human. No OCR coverage is claimed.
            add('privacy', 'linked-material-not-fetched', 'review')
        self.cache[key] = findings
        return findings


def tree(repo, sha):
    entries = {}
    for record in git(repo, 'ls-tree', '-rz', '--full-tree', sha).split(b'\0'):
        if record:
            meta, raw_name = record.split(b'\t', 1)
            mode, kind, oid = meta.decode().split()
            name = raw_name.decode('utf-8')
            if (name.startswith('/') or any(p in {'', '.', '..'} for p in name.split('/'))
                    or '\\' in name or '\0' in name):
                raise ValueError('unsafe Git path')
            entries[name] = (mode, kind, oid)
    return entries


def validate_refs(repo, base, head):
    if git(repo, 'rev-parse', '--is-shallow-repository').strip() != b'false':
        raise ValueError('shallow history cannot establish complete coverage')
    if not SHA.fullmatch(base) or not SHA.fullmatch(head) or base == head:
        raise ValueError('exact distinct commit SHAs required')
    for sha in (base, head):
        if git(repo, 'cat-file', '-t', sha).strip() != b'commit':
            raise ValueError('commit missing')
    commits = git(repo, 'rev-list', '--reverse', '--topo-order', head, '--not', base).decode().splitlines()
    if not commits:
        raise ValueError('empty commit coverage')
    return commits


def scan_range(repo, base, head, scanner, metadata=()):
    commits = validate_refs(repo, base, head)
    if len(commits) > 500:
        raise ValueError('commit limit exceeded')
    trusted_inputs = {name: digest((HERE / name).read_bytes()) for name in
                      ('.gitleaks.toml', 'tools/security_gate.py', 'tools/security_controller.py',
                       'tools/check_repo_hygiene.py')}
    report = {'version': 1, 'trusted_inputs': trusted_inputs, 'base': base, 'head': head, 'commits': [], 'metadata': [],
              'contexts': {c: 'success' for c in CONTEXTS}}
    trees = {}
    # Inspect complete changed files and old/new versions against EVERY parent.
    # Unchanged baseline files and unrelated history are not silently re-audited.
    def get_tree(sha):
        if sha not in trees:
            trees[sha] = tree(repo, sha)
        return trees[sha]
    blob_cache = {}
    for sha in commits:
        findings = []
        commit_data = git(repo, 'cat-file', 'commit', sha)
        findings.extend(scanner.inspect('commit-metadata', commit_data, metadata=True))
        # Retain the existing publication gate's exact public attribution policy.
        identities = git(repo, 'show', '-s', '--format=%ae%x00%ce', sha).decode().strip().split('\0')
        if (len(identities) != 2 or identities[0] not in OWNER_NOREPLY_ALIASES
                or identities[1] not in {NOREPLY, 'noreply@github.com'}):
            findings.append({'category': 'privacy', 'rule': 'existing-public-attribution-policy',
                             'status': 'blocked', 'id': digest((sha + ':attribution').encode())})
        entries = get_tree(sha)
        parents = git(repo, 'show', '-s', '--format=%P', sha).decode().split()
        objects = set()
        changed = set()
        if not parents:
            changed.update(entries)
            objects.update((name, *entry) for name, entry in entries.items())
        for parent in parents:
            previous = get_tree(parent)
            for name in entries.keys() | previous.keys():
                if entries.get(name) != previous.get(name):
                    changed.add(name)
                    if name in entries:
                        objects.add((name, *entries[name]))
                    if name in previous:
                        objects.add((name, *previous[name]))
        for name in sorted(changed):
            if any(name == p or name.startswith(p) for p in scanner.policy['protected_paths']):
                findings.append({'category': 'privacy', 'rule': 'protected-control-change',
                                 'status': 'review', 'id': digest(('protected:' + sha + ':' + name).encode())})
            elif any(name.startswith(p) for p in scanner.policy['review_paths']):
                findings.append({'category': 'privacy', 'rule': 'sensitive-path-change',
                                 'status': 'review', 'id': digest(('review:' + sha + ':' + name).encode())})
        # Read .gitignore from each revision as data only. Git's pattern parser
        # runs in a disposable bare-of-user-hooks scratch repo, never the PR tree.
        with tempfile.TemporaryDirectory(prefix='security-ignore-') as tmp:
            git(tmp, 'init', '-q')
            ignore_files = {n: e for n, e in entries.items() if PurePosixPath(n).name == '.gitignore'}
            for name, (_, kind, oid) in ignore_files.items():
                if kind != 'blob':
                    raise ValueError('invalid ignore object')
                target = Path(tmp) / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(git(repo, 'cat-file', 'blob', oid))
            names = b''.join(n.encode() + b'\0' for n in entries)
            result = subprocess.run(['git', '-C', tmp, '-c', 'core.excludesFile=/dev/null',
                                     'check-ignore', '--no-index', '-z', '--stdin'], input=names,
                                    capture_output=True, timeout=30, env=git_environment())
            if result.returncode not in (0, 1):
                raise RuntimeError('ignore inspection incomplete')
            if result.stdout:
                findings.append({'category': 'hygiene', 'rule': 'tracked-ignored-file',
                                 'status': 'blocked', 'id': digest(result.stdout)})
            # Verify representative accidental-file families remain ignored.
            probes = ['.env', 'nested/.env.production', 'capture.har', 'backup.zip',
                      'data.sqlite3', '.DS_Store', 'node_modules/cache', 'private-data/record.json',
                      'private-config/settings.json', 'crash.dmp', 'debug.log']
            result = subprocess.run(['git', '-C', tmp, '-c', 'core.excludesFile=/dev/null',
                                     'check-ignore', '--no-index', '-z', '--stdin'],
                                    input=b''.join(p.encode() + b'\0' for p in probes), capture_output=True, timeout=30, env=git_environment())
            if result.returncode not in (0, 1):
                raise RuntimeError('ignore coverage unavailable')
            if set(result.stdout.split(b'\0')) - {b''} != {p.encode() for p in probes}:
                findings.append({'category': 'hygiene', 'rule': 'missing-ignore-protection',
                                 'status': 'blocked', 'id': digest(sha.encode() + b'ignore-coverage')})
        for name, mode, kind, oid in sorted(objects):
            if kind != 'blob':
                findings.append({'category': 'hygiene', 'rule': 'unscanned-gitlink',
                                 'status': 'blocked', 'id': digest((sha + name).encode())})
                continue
            if oid not in blob_cache:
                size = int(git(repo, 'cat-file', '-s', oid))
                if size > scanner.policy['max_file_bytes']:
                    # Do not allocate an attacker-controlled huge blob.
                    findings.extend({'category': c, 'rule': 'oversized-object', 'status': 'blocked',
                                     'id': digest((oid + c).encode())} for c in ('hygiene', 'secrets'))
                    continue
                blob_cache[oid] = git(repo, 'cat-file', 'blob', oid)
            findings.extend(scanner.inspect(name, blob_cache[oid], mode))
        unique = {f['id']: f for f in findings}
        technical = 'failed' if any(f['category'] in {'secrets', 'hygiene'} and f['status'] == 'blocked' for f in findings) else 'complete'
        privacy = ('blocked' if any(f['category'] == 'privacy' and f['status'] == 'blocked' for f in findings)
                   else 'review-required' if any(f['status'] == 'review' for f in findings) else 'automated-assessment-complete')
        report['commits'].append({'sha': sha, 'objects': len(objects), 'findings': list(unique.values()),
                                  'technical': technical, 'privacy': privacy})
    for index, data in enumerate(metadata):
        report['metadata'].extend(scanner.inspect('pr-metadata-' + str(index), data, metadata=True))
    findings = [f for c in report['commits'] for f in c['findings']] + report['metadata']
    for f in findings:
        if f['status'] in {'blocked', 'review'}:
            report['contexts'][f['category']] = 'failure'
    # Digest binds ALL inspected metadata, head/base, trusted policy and findings.
    report['review_digest'] = digest(json.dumps({'report': report, 'policy': scanner.policy,
                                                'metadata_hashes': [digest(d) for d in metadata]}, sort_keys=True).encode())
    report['unique_scans'] = len(scanner.cache)
    return report


def apply_review(report, approved):
    if approved:
        # Human review resolves only contextual findings, never technical failures.
        for c in report['commits']:
            for f in c['findings']:
                if f['status'] == 'review':
                    f['status'] = 'reviewed'
            c['privacy'] = 'authorized-reviewed'
        for f in report['metadata']:
            if f['status'] == 'review':
                f['status'] = 'reviewed'
        remaining = [f for c in report['commits'] for f in c['findings']] + report['metadata']
        report['contexts']['privacy'] = 'failure' if any(f['category'] == 'privacy' and f['status'] in {'blocked', 'review'} for f in remaining) else 'success'


def summary(report):
    lines = ['Security assessment (values and filenames withheld)',
             'Head: ' + report['head'], 'Review digest: ' + report['review_digest']]
    if 'review_approval' in report:
        lines.append('Review approval: ' + report['review_approval'])
    for c in report['commits']:
        lines.append(f"{c['sha']}: technical={c['technical']}; privacy={c['privacy']}; objects={c['objects']}; findings={len(c['findings'])}")
        for f in c['findings']:
            lines.append(f"  {f['category']}: {f['rule']} [{f['status']}] id={f['id']}")
    for f in report['metadata']:
        lines.append(f"PR content: {f['category']}: {f['rule']} [{f['status']}] id={f['id']}")
    lines.append('Contexts: ' + json.dumps(report['contexts'], sort_keys=True))
    return '\n'.join(lines) + '\n'


def event_texts(event):
    """Accessible current and previous textual webhook fields, without logging."""
    texts = []
    for key in ('pull_request', 'comment', 'review'):
        obj = event.get(key) or {}
        for field in ('title', 'body'):
            value = obj.get(field)
            if isinstance(value, str):
                texts.append(value.encode())
    for field in ('title', 'body'):
        previous = (event.get('changes', {}).get(field) or {}).get('from')
        if isinstance(previous, str):
            texts.append(previous.encode())
    return texts


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, default=Path('.'))
    parser.add_argument('--base', required=True)
    parser.add_argument('--head', required=True)
    parser.add_argument('--metadata-file', action='append', type=Path, default=[])
    parser.add_argument('--event-file', type=Path, help='also inspect accessible current/previous PR webhook text')
    parser.add_argument('--report', type=Path)
    parser.add_argument('--policy', type=Path, default=HERE / 'security/policy.json')
    parser.add_argument('--scanner', default='gitleaks')
    parser.add_argument('--automated-only', action='store_true',
                        help='supplementary hooks/read-only CI: review findings stay reported but only technical/strict violations set exit code')
    args = parser.parse_args()
    try:
        scanner = Scanner(load_policy(args.policy), HERE / '.gitleaks.toml', args.scanner)
        metadata = [p.read_bytes() for p in args.metadata_file]
        if args.event_file:
            metadata.extend(event_texts(json.loads(args.event_file.read_text())))
        report = scan_range(args.repo, args.base, args.head, scanner, metadata)
        if args.report:
            args.report.write_text(json.dumps(report, indent=2) + '\n')
        print(summary(report), end='')
        if args.automated_only:
            findings = [f for c in report['commits'] for f in c['findings']] + report['metadata']
            return int(any(f['status'] == 'blocked' for f in findings))
        return int(any(v != 'success' for v in report['contexts'].values()))
    except Exception:
        # Includes unavailable scanner, malformed objects, timeout and resource limits.
        print('FAIL: security inspection incomplete; all categories fail closed.', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
