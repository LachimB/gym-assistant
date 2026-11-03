"""Read/validate portable session history, independently of Android receipt JSON."""
from __future__ import annotations

import hashlib
import json
from datetime import datetime
from zoneinfo import ZoneInfo

from validate_starter import check_schema


def validate_history(data: dict) -> dict:
    """Read known versions; reject unsupported/inconsistent human facts, never repair."""
    check_schema('session', data)
    if data['schemaVersion'] == '1.0.0':
        return data  # Preserve the earlier contract; do not invent newer timing facts.
    source = data['sourcePlanSnapshot']
    raw = source['compiledPlanJson']
    if data['executionPlanHash'] != 'sha256:' + hashlib.sha256(raw.encode('utf-8')).hexdigest():
        raise ValueError('Frozen plan hash differs')
    plan = json.loads(raw)
    if (plan['templateId'], plan['revisionId'], plan['planFormatVersion']) != (
        data['templateId'], data['revisionId'], source['planFormatVersion']
    ):
        raise ValueError('Frozen plan identity differs')
    if source['hashFormat'] != 'sha256-kotlin-timed-1-json-utf8' or data['engineVersion'] != 'kotlin-timed-1':
        raise ValueError('Unsupported frozen plan format/engine')
    instant = datetime.fromisoformat(data['startedAt'].replace('Z', '+00:00'))
    if instant.astimezone(ZoneInfo(data['timeZone'])).date().isoformat() != data['localTrainingDate']:
        raise ValueError('Captured local date differs')
    timing = data['timing']
    if timing['plannedDurationMs'] != plan['expectedDurationMs']:
        raise ValueError('Planned duration differs')
    if timing['scheduledElapsedMs'] > timing['plannedDurationMs']:
        raise ValueError('Scheduled position outside plan')
    # Do not cap observed running time by planned duration: rewound work may be repeated.
    phases = plan['phases']
    steps = {phase['stepId'] for phase in phases}
    seen = set()
    counts = {}
    previous = {key: 0 for key in ['observedRunMs', 'pauseMs', 'unobservedGapMs', 'unknownGapCount']}
    for event in data['events']:
        identity = event['eventId']
        if identity in seen:
            raise ValueError('Repeated human event ID')
        seen.add(identity)
        counts[event['type']] = counts.get(event['type'], 0) + 1
        if event['scheduledElapsedMs'] > timing['plannedDurationMs']:
            raise ValueError('Event position outside plan')
        for key in previous:
            if not previous[key] <= event[key] <= timing[key]:
                raise ValueError('Event timing is inconsistent')
            previous[key] = event[key]
        if 'planStepId' in event:
            matches = [p for p in phases if p['stepId'] == event['planStepId']
                       and ('phaseId' not in event or p.get('phaseId') == event['phaseId'])
                       and ('setId' not in event or p.get('setId') == event['setId'])]
            if event['planStepId'] not in steps or not matches:
                raise ValueError('Unknown event source reference')
            edge = 'endMs' if event['type'] == 'phaseCompleted' else 'startMs'
            if not any(p[edge] == event['scheduledElapsedMs'] for p in matches):
                raise ValueError('Event position differs from its phase')
        if event['type'] == 'planEndReached' and event['scheduledElapsedMs'] != timing['plannedDurationMs']:
            raise ValueError('Plan end position differs')
        detail = event.get('detail', {})
        if event['type'] == 'interruption':
            if not isinstance(detail.get('gapDurationKnown'), bool):
                raise ValueError('Missing interruption uncertainty')
            if detail['gapDurationKnown'] != ('gapDurationMs' in detail):
                raise ValueError('Unknown gap must not invent a duration')
    if data['events'][0]['type'] != 'sessionStarted' or counts.get('sessionStarted') != 1:
        raise ValueError('Missing/duplicate session start')
    kind = data['completionKind']
    last = data['events'][-1]
    if kind == 'unfinished':
        if counts.get('sessionEnded', 0) + counts.get('sessionDiscarded', 0):
            raise ValueError('Unfinished history has a finalization')
        if data['status'] == 'awaitingCompletion' and not counts.get('planEndReached'):
            raise ValueError('Missing observed plan end')
    else:
        expected = 'sessionDiscarded' if kind == 'discarded' else 'sessionEnded'
        if last['type'] != expected or last.get('detail', {}).get('completionKind') != kind:
            raise ValueError('Final outcome differs')
        if counts.get('sessionEnded', 0) + counts.get('sessionDiscarded', 0) != 1:
            raise ValueError('Repeated finalization')
        if last['recordedAt'] != data['finishedAt']:
            raise ValueError('Finalization timestamp differs')
        if any(last[key] != timing[key] for key in ['scheduledElapsedMs', *previous]):
            raise ValueError('Final timing differs from final event')
        if kind == 'plannedEnd' and not counts.get('planEndReached'):
            raise ValueError('Planned end was not observed')
    return data
