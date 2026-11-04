#!/usr/bin/env python3
"""Fail closed on known private files/content. Reports locations, never matched values."""
from pathlib import Path, PurePosixPath
import argparse
import re
import subprocess
import sys
import io

NOREPLY = '17935459+LachimB@users.noreply.github.com'
PRIVATE_DIRS = {'personal-data', 'private-data', 'private-evidence', 'private-config',
                'private-backups', 'private-exports', 'raw-imports', 'original-pdfs'}
PRIVATE_SUFFIXES = {'.pdf', '.xlsx', '.xls', '.csv', '.db', '.sqlite', '.sqlite3',
                    '.pem', '.p8', '.p12', '.pfx', '.jks', '.keystore', '.key',
                    '.mobileprovision', '.log', '.har', '.hprof', '.dmp', '.dump',
                    '.sql', '.bak', '.backup', '.zip', '.tar', '.tgz', '.gz', '.7z',
                    '.apk', '.aab', '.ipa', '.png', '.jpg', '.jpeg', '.heic', '.mp4'}
EMAIL = re.compile(r'[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}')
RULES = (
    ('absolute private host path', re.compile(r'/(?:Users|home)/[^\s/`"<>]+')),
    ('live Drive link', re.compile(r'https://(?:drive|docs)\.google\.com/(?:file/d/|drive/folders/|document/d/|spreadsheets/d/|presentation/d/)[A-Za-z0-9_-]+')),
    ('probable provider resource ID', re.compile(r'(?<![\w-])1[A-Za-z0-9_-]{24,79}(?![\w-])')),
    ('private key', re.compile(r'-----BEGIN (?:[A-Z]+ )*PRIVATE KEY-----')),
    ('credential token', re.compile(r'(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AIza[0-9A-Za-z_-]{25,}|ya29\.[A-Za-z0-9_-]{20,})')),
    ('personal acceptance field', re.compile(r'owner' + r'Reported[A-Za-z]*\s*[\"\x27]?\s*:')),
    ('private acceptance narrative', re.compile(r'(?i)(?:owner(?:\x27s)?|user(?:\x27s)?|personal|private)\s+(?:workout|template|source).{0,35}\b\d{2}(?:m\d{2}|:\d{2}|[- ]phase)')),
)


def filename_issue(name):
    p = PurePosixPath(name.lower())
    if any(part in PRIVATE_DIRS for part in p.parts):
        return 'private directory'
    if name in {'docs/product/traceability.csv', 'gradle/wrapper/gradle-wrapper.jar'}:
        return None
    if p.suffix in PRIVATE_SUFFIXES or any(name.lower().endswith(x) for x in ('.db-wal', '.db-shm', '.db-journal', '.sqlite-wal', '.sqlite-shm', '.sqlite-journal', '.sqlite3-wal', '.sqlite3-shm', '.sqlite3-journal')):
        return 'private export, capture, credential, or binary file'
    base = p.name
    if (base == '.env' or base.startswith('.env.') and base != '.env.example' or
        base in {'credentials.json', 'secrets.json', 'local.properties', 'android-local.properties', '.npmrc', '.netrc', '.pypirc'} or
        base.startswith(('client_secret', 'service-account')) or
        'token' in base and base.endswith('.json')):
        return 'private configuration or credential export'
    return None


def inspect_content(name, data):
    issues = []
    if b'\0' in data:
        if name != 'gradle/wrapper/gradle-wrapper.jar':
            issues.append((1, 'unexpected binary; review required'))
        return issues
    try:
        text = data.decode('utf-8')
    except UnicodeDecodeError:
        return [(1, 'unreadable content; review required')]
    for number, line in enumerate(text.splitlines(), 1):
        for match in EMAIL.finditer(line):
            address = match.group()
            domain = address.rsplit('@', 1)[1].lower()
            if address != NOREPLY and domain not in {'example.test', 'example.invalid', 'example.com', 'github.com'}:
                issues.append((number, 'non-synthetic contact address'))
        for reason, pattern in RULES:
            if reason == 'probable provider resource ID' and name.endswith(('package-lock.json', 'yarn.lock')) and re.match(r'\s*(?:"integrity"\s*:|integrity\s)', line):
                continue  # Lock integrity hashes are not provider resource identifiers.
            matches = list(pattern.finditer(line))
            if reason == 'probable provider resource ID':
                matches = [m for m in matches if not re.fullmatch('[0-9a-fA-F]+', m.group())]
            if matches:
                issues.append((number, reason))
    return sorted(set(issues))


def git(root, *args):
    return subprocess.check_output(['git', '-C', str(root), *args], stderr=subprocess.DEVNULL)


def scan(root, staged=False):
    args = ['ls-files', '-z'] if staged else ['ls-files', '-z', '--cached', '--others', '--exclude-standard']
    names = sorted(set(x.decode() for x in git(root, *args).split(b'\0') if x))
    failures = []
    for name in names:
        reason = filename_issue(name)
        if reason:
            failures.append((name, 1, reason))
        path = root / name
        if path.is_symlink():
            failures.append((name, 1, 'symlink requires explicit review'))
            continue
        if not staged and not path.exists():
            continue  # A deleted tracked file will not be published.
        try:
            data = git(root, 'show', ':' + name) if staged else path.read_bytes()
        except (OSError, subprocess.CalledProcessError):
            failures.append((name, 1, 'cannot inspect file'))
            continue
        failures.extend((name, line, reason) for line, reason in inspect_content(name, data))
    return failures



def inspect_revision(root, sha):
    # Read the Git tree/objects directly: export-ignore/export-subst attributes
    # must not hide or rewrite historical bytes during a privacy inspection.
    failures = []
    blobs = []
    for record in git(root, 'ls-tree', '-rz', '--full-tree', sha).split(b'\0'):
        if not record:
            continue
        metadata, raw_name = record.split(b'\t', 1)
        mode, kind, object_sha = metadata.split()
        name = raw_name.decode('utf-8')
        if kind != b'blob' or mode not in {b'100644', b'100755'}:
            failures.append((name, 1, 'non-regular Git entry requires review'))
            continue
        blobs.append((name, object_sha))
    request = b''.join(object_sha + b'\n' for _, object_sha in blobs)
    output = subprocess.check_output(['git', '-C', str(root), 'cat-file', '--batch'], input=request, stderr=subprocess.DEVNULL)
    stream = io.BytesIO(output)
    for name, expected in blobs:
        actual, kind, size = stream.readline().split()
        if actual != expected or kind != b'blob':
            raise ValueError('Unexpected Git object')
        data = stream.read(int(size))
        if len(data) != int(size) or stream.read(1) != b'\n':
            raise ValueError('Incomplete Git object')
        reason = filename_issue(name)
        if reason:
            failures.append((name, 1, reason))
        failures.extend((name, line, reason) for line, reason in inspect_content(name, data))
    return [('commit ' + sha[:12] + '/' + name, line, reason) for name, line, reason in failures]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--staged', action='store_true', help='inspect index bytes, including partially staged files')
    parser.add_argument('--message-file', type=Path, help='inspect the proposed commit message without printing content')
    parser.add_argument('--commit', help='inspect exactly one new commit without rescanning merged baseline ancestors')
    parser.add_argument('--commits', help='also check author/committer emails for commits in this revision range')
    args = parser.parse_args()
    try:
        root = Path(subprocess.check_output(['git', 'rev-parse', '--show-toplevel'], text=True).strip())
        failures = scan(root, args.staged)
        if args.message_file:
            failures.extend(('commit-message', line, reason) for line, reason in inspect_content('commit-message', args.message_file.read_bytes()))
        if args.commits or args.commit:
            options = ['-1'] if args.commit else []
            records = git(root, 'log', '--format=%H%x09%ae%x09%ce', *options, args.commit or args.commits).decode().splitlines()
            for record in records:
                sha, author, committer = record.split('\t')
                if author != NOREPLY or committer not in {NOREPLY, 'noreply@github.com'}:
                    failures.append(('commit ' + sha[:12], 1, 'new commit must use confirmed noreply author and committer'))
                failures.extend(inspect_revision(root, sha))
                failures.extend(('commit ' + sha[:12] + '/message', line, reason) for line, reason in inspect_content('commit-message', git(root, 'log', '-1', '--format=%B', sha)))
    except (OSError, subprocess.CalledProcessError, ValueError):
        print('FAIL: repository privacy inspection unavailable', file=sys.stderr)
        return 1
    for name, line, reason in failures:
        print(f'{name}:{line}: {reason}', file=sys.stderr)
    if failures:
        print('FAIL: privacy gate rejected content; matched values withheld.', file=sys.stderr)
        return 1
    print('PASS: known private filename/content checks. Manual review and secret scanning remain required.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
