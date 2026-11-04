#!/usr/bin/env python3
"""Require an explicit third-party review record when dependency inputs change."""
import hashlib
import json
from pathlib import Path
import sys


def dependency_inputs(root):
    paths = set(root.glob('**/*.gradle.kts'))
    paths = {p for p in paths if not any(x in p.parts for x in ('build', 'node_modules', '.gradle'))}
    paths.update(root / name for name in (
        'gradle.properties', 'gradle/wrapper/gradle-wrapper.properties',
        'gradle/wrapper/gradle-wrapper.jar', 'tools/requirements.txt',
        'apps/web/package.json', 'apps/web/package-lock.json',
        'packages/engine-ts/package.json', 'packages/engine-ts/package-lock.json',
        'kotlin-js-store/yarn.lock'))
    return {p.relative_to(root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sorted(paths)}


def npm_components(root):
    result = {}
    for name, scope in [('apps/web/package-lock.json', 'web'), ('packages/engine-ts/package-lock.json', 'engine-ts')]:
        packages = json.loads((root / name).read_text())['packages']
        for path, data in packages.items():
            if not path:
                continue
            license_name = data.get('license')
            if not isinstance(license_name, str) or not license_name.strip():
                raise ValueError('Dependency has undeclared license')
            key = (path.rsplit('node_modules/', 1)[-1], data['version'], license_name)
            result.setdefault(key, []).append(scope)
    return [{'name': n, 'version': v, 'license': lic, 'scopes': sorted(scopes)}
            for (n, v, lic), scopes in sorted(result.items())]


def validate(root, review):
    if dependency_inputs(root) != review.get('dependencyInputs'):
        raise ValueError('Dependency inputs changed: review upstream licenses/notices and update the review record')
    if npm_components(root) != review.get('npmComponents'):
        raise ValueError('Dependency inventory differs from its reviewed record')
    if review.get('binaryReleaseCleared') is not False:
        raise ValueError('Binary distribution clearance requires a separate release review')
    if not (root / 'LICENSE').is_file() or not (root / 'THIRD_PARTY_NOTICES.md').is_file():
        raise ValueError('Project license and third-party notices are required')


def main():
    root = Path(__file__).resolve().parent.parent
    try:
        validate(root, json.loads((root / 'docs/operations/third-party-review.json').read_text()))
    except (OSError, ValueError, KeyError):
        print('FAIL: dependency/license review missing or stale; review upstream terms and update the inventory. No binary release clearance.', file=sys.stderr)
        return 1
    print('PASS: dependency inputs and declared licenses match the reviewed source inventory. Binary release remains uncleared.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
