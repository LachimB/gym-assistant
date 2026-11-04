#!/usr/bin/env python3
"""Default-branch-only GitHub controller; untrusted Git objects are NEVER checked out."""
import json
import os
from pathlib import Path
import re
import sys
import tempfile
import urllib.request

from security_gate import HERE, CONTEXTS, SHA, Scanner, apply_review, git, load_policy, scan_range, summary

API_ROOT = 'https://api.github.com'
PREFIX = 'Security / '


class GitHub:
    def __init__(self, repo, token):
        if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repo):
            raise ValueError('invalid repository')
        self.repo, self.token = repo, token

    def request(self, path, payload=None):
        if not path.startswith('/repos/' + self.repo + '/'):
            raise ValueError('unexpected API path')
        request = urllib.request.Request(API_ROOT + path,
            data=None if payload is None else json.dumps(payload).encode(),
            headers={'Authorization': 'Bearer ' + self.token, 'Accept': 'application/vnd.github+json',
                     'X-GitHub-Api-Version': '2022-11-28', 'Content-Type': 'application/json'})
        # No redirects: never forward the credential to another origin.
        opener = urllib.request.build_opener(NoRedirect)
        with opener.open(request, timeout=45) as response:
            return json.load(response)

    def get(self, suffix):
        return self.request('/repos/' + self.repo + suffix)

    def pages(self, suffix):
        records = []
        separator = '&' if '?' in suffix else '?'
        for page in range(1, 102):
            values = self.get(suffix + separator + f'per_page=100&page={page}')
            if not isinstance(values, list):
                raise ValueError('unexpected paginated response')
            records.extend(values)
            if len(values) < 100:
                return records
        raise ValueError('API pagination limit; inspection incomplete')

    def status(self, sha, context, state, run_url):
        if not SHA.fullmatch(sha) or context not in CONTEXTS or state not in {'pending', 'failure', 'success'}:
            raise ValueError('invalid status')
        self.request('/repos/' + self.repo + '/statuses/' + sha,
                     {'state': state, 'context': PREFIX + context,
                      'description': 'Inspection pending' if state == 'pending' else 'See redacted security assessment',
                      'target_url': run_url})


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ValueError('API redirect refused')


def content_snapshot(api, number, pr):
    comments = api.pages(f'/issues/{number}/comments')
    inline = api.pages(f'/pulls/{number}/comments')
    reviews = api.pages(f'/pulls/{number}/reviews')
    texts = [json.dumps({'title': pr['title'], 'body': pr['body'], 'author': pr['user']['login']}, sort_keys=True).encode()]
    # Exclude exact review receipts from the digest: otherwise adding a receipt
    # changes the digest it approves. Receipts carry no free-form private text.
    receipt = re.compile(r'^Security-Privacy-Review: [0-9a-f]{40} [0-9a-f]{64}$')
    for group in (comments, inline, reviews):
        for record in group:
            body = record.get('body') or ''
            if group is reviews and receipt.fullmatch(body.strip()):
                continue
            texts.append(json.dumps({'id': record['id'], 'body': body,
                                     'updated_at': record.get('updated_at', record.get('submitted_at')),
                                     'author': record['user']['login']}, sort_keys=True).encode())
    return texts, reviews


def authorized_review(api, pr, reviews, report, policy):
    receipt = f"Security-Privacy-Review: {report['head']} {report['review_digest']}"
    # A later CHANGES_REQUESTED or DISMISSED state cancels earlier approval.
    latest = {}
    for review in reviews:
        if review['state'] != 'COMMENTED':
            latest[review['user']['login']] = review
    for login, review in latest.items():
        if (login == pr['user']['login'] or login not in policy['reviewers']
                or review['state'] != 'APPROVED' or review.get('commit_id') != report['head']
                or (review.get('body') or '').strip() != receipt):
            continue
        permission = api.get('/collaborators/' + login + '/permission')['permission']
        if permission in {'admin', 'maintain', 'write'}:
            return True
    return False


def fetch_objects(repo, repository, base, head, number):
    # Repo URL and refspecs are validated/generated, never PR-controlled shell.
    if not SHA.fullmatch(base) or not SHA.fullmatch(head) or not isinstance(number, int) or number < 1:
        raise ValueError('invalid refs')
    git(repo, 'init', '--bare', '-q')
    url = 'https://github.com/' + repository + '.git'
    git(repo, 'fetch', '--no-tags', '--no-recurse-submodules', url,
        base, f'+refs/pull/{number}/head:refs/security/head')
    if git(repo, 'rev-parse', 'refs/security/head').decode().strip() != head:
        raise ValueError('PR changed during fetch')


def target_numbers(api, event_name, event):
    if event_name == 'workflow_run':
        run = event['workflow_run']
        # Never download or execute trigger-workflow artifacts/code.
        if run.get('event') not in {'pull_request', 'pull_request_review', 'pull_request_review_comment'}:
            return []
        # Fork review events can omit pull_requests; bounded polling is fallback.
        return sorted({p['number'] for p in run.get('pull_requests', [])})
    if 'pull_request' in event:
        return [event['pull_request']['number']]
    if 'issue' in event:
        return [event['issue']['number']] if 'pull_request' in event['issue'] else []
    if event_name in {'schedule', 'workflow_dispatch'}:
        return [p['number'] for p in api.pages('/pulls?state=open')]
    raise ValueError('unsupported event')


def assess(api, number, run_url):
    pr = api.get(f'/pulls/{number}')
    if pr['state'] != 'open':
        return True
    head, base = pr['head']['sha'], pr['base']['sha']
    # Immediately invalidate previously green statuses before API/scanner work.
    for context in CONTEXTS:
        api.status(head, context, 'pending', run_url)
    try:
        policy = load_policy(HERE / 'security/policy.json')
        metadata, reviews = content_snapshot(api, number, pr)
        with tempfile.TemporaryDirectory(prefix='security-objects-') as tmp:
            fetch_objects(tmp, api.repo, base, head, number)
            scanner = Scanner(policy, HERE / '.gitleaks.toml')
            report = scan_range(tmp, base, head, scanner, metadata)
        approved = authorized_review(api, pr, reviews, report, policy)
        apply_review(report, approved)
        # Guard concurrent edits/pushes; no stale green result gets published.
        current = api.get(f'/pulls/{number}')
        current_metadata, current_reviews = content_snapshot(api, number, current)
        if (current['head']['sha'] != head or current['base']['sha'] != base or current_metadata != metadata
                or authorized_review(api, current, current_reviews, report, policy) != approved):
            raise ValueError('PR changed during assessment; rerun required')
        # Logs and summary contain rule names, opaque IDs and SHAs only.
        safe = summary(report)
        print(safe, end='')
        if os.environ.get('GITHUB_STEP_SUMMARY'):
            with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as output:
                output.write('```text\n' + safe + '```\n')
        for context, state in report['contexts'].items():
            api.status(head, context, state, run_url)
        return all(state == 'success' for state in report['contexts'].values())
    except Exception:
        for context in CONTEXTS:
            api.status(head, context, 'failure', run_url)
        print('FAIL: PR inspection incomplete; all categories blocked; content withheld.')
        return False


def main():
    # Auth only the API client; never pass token to git or Gitleaks subprocesses.
    try:
        api = GitHub(os.environ['GITHUB_REPOSITORY'], os.environ.pop('GH_TOKEN'))
        event = json.loads(Path(os.environ['GITHUB_EVENT_PATH']).read_text())
        run_url = 'https://github.com/' + api.repo + '/actions/runs/' + os.environ['GITHUB_RUN_ID']
        numbers = target_numbers(api, os.environ['GITHUB_EVENT_NAME'], event)
        outcomes = [assess(api, number, run_url) for number in numbers]
        return int(not all(outcomes))
    except Exception:
        print('FAIL: trusted security controller unavailable; required statuses must not be bypassed.')
        return 1


if __name__ == '__main__':
    sys.exit(main())
