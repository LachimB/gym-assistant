"""Read-only external Training Template edit review for the M1 draft contracts.

No publication, adoption, cloud access or persistence. The known-good local file
is always retained; an approved repository transaction must eventually recheck
its current revision before publishing any reviewed change.
"""
from __future__ import annotations

import argparse
from dataclasses import dataclass
import json
from pathlib import Path

from template_loader import TemplateValidationError, load_template


@dataclass(frozen=True)
class EditReview:
    status: str
    changed_paths: tuple[str, ...] = ()
    identity_changes: tuple[str, ...] = ()
    diagnostic: str = ''


def changed_paths(before, after, path='') -> tuple[str, ...]:
    """JSON Pointer paths only; array ordering stays visible and significant."""
    if type(before) is not type(after):
        return (path or '/',)
    if isinstance(before, dict):
        changes = []
        for key in sorted(before.keys() | after.keys()):
            child = path + '/' + key.replace('~', '~0').replace('/', '~1')
            if key not in before or key not in after:
                changes.append(child)
            else:
                changes.extend(changed_paths(before[key], after[key], child))
        return tuple(changes)
    if isinstance(before, list):
        changes = []
        for i in range(max(len(before), len(after))):
            child = f'{path}/{i}'
            if i >= len(before) or i >= len(after):
                changes.append(child)
            else:
                changes.extend(changed_paths(before[i], after[i], child))
        return tuple(changes)
    return () if before == after else (path or '/',)


def identities(template: dict) -> dict[tuple[str, ...], tuple[str, ...]]:
    """Identity ownership/targets without assuming names or positions are IDs."""
    result = {}
    for section in template['sections']:
        sid = section['sectionId']
        result[('section', sid)] = ()
        for block in section['blocks']:
            bid = block['blockId']
            result[('block', bid)] = (sid,)
            for occurrence in block['occurrences']:
                oid = occurrence['occurrenceId']
                result[('occurrence', oid)] = (bid, occurrence['exerciseId'])
                for item in occurrence['sets']:
                    set_id = item['setId']
                    result[('set', set_id)] = (oid, item['variationId'])
                    for phase in item['phases']:
                        result[('phase', set_id, phase['phaseId'])] = ()
            for step in block['order']:
                result[('step', step['stepId'])] = (
                    bid, step['kind'], step.get('setId', ''), str(step.get('repetitionIndex', '')))
    return result


def review_external_edit(base_path: Path, candidate_path: Path,
                         exercise_paths: list[Path], expected_revision: str) -> EditReview:
    """Compare against a validated base; never modify the known-good artifact.

    A valid preview is not an executable plan or authorization to adopt a new
    default. Identity changes require explicit review; no rename is inferred.
    A bad base raises: without a valid base there is no last-valid fallback.
    """
    base = load_template(base_path, exercise_paths)
    if base['revisionId'] != expected_revision:
        return EditReview('stale_base', diagnostic='Expected base revision is no longer current')
    try:
        candidate = load_template(candidate_path, exercise_paths)
    except TemplateValidationError as exc:
        return EditReview('invalid_candidate', diagnostic=str(exc))
    except OSError:
        return EditReview('unreadable_candidate', diagnostic='Unable to read candidate or exercise file')
    changes = changed_paths(base, candidate)
    if candidate['templateId'] != base['templateId']:
        return EditReview('template_identity_changed', changes)
    if not changes:
        return EditReview('unchanged')
    if candidate['revisionId'] == base['revisionId']:
        return EditReview('revision_reused', changes,
                          diagnostic='Changed content requires a new immutable revision ID')
    before, after = identities(base), identities(candidate)
    identity_changes = []
    for key in sorted(before.keys() | after.keys()):
        if key not in before:
            action = 'added'
        elif key not in after:
            action = 'removed'
        elif before[key] != after[key]:
            action = 'retargeted'
        else:
            continue
        identity_changes.append(action + ':' + ':'.join(key))
    return EditReview('identity_review_required' if identity_changes else 'review_required',
                      changes, tuple(identity_changes))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('base', type=Path)
    parser.add_argument('candidate', type=Path)
    parser.add_argument('--expected-revision', required=True)
    parser.add_argument('--exercise', type=Path, action='append', required=True)
    args = parser.parse_args()
    try:
        review = review_external_edit(args.base, args.candidate, args.exercise, args.expected_revision)
    except (OSError, TemplateValidationError):
        print('FAIL: known-good base or exercise library could not be validated')
        return 1
    # Paths and identity IDs may still be private: this output is for local review only.
    print(json.dumps(review.__dict__, indent=2))
    return 0 if review.status in ('unchanged', 'review_required', 'identity_review_required') else 1


if __name__ == '__main__':
    raise SystemExit(main())
