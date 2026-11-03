#!/usr/bin/env python3
"""Validate synthetic starter artifacts. This is NOT a production workout engine."""
from __future__ import annotations
import csv
import json
import re
from collections import Counter
from pathlib import Path
from typing import Any
import yaml
from jsonschema import Draft202012Validator, FormatChecker

ROOT = Path(__file__).resolve().parent.parent


def read_yaml(path: str) -> dict[str, Any]:
    with (ROOT / path).open(encoding='utf-8') as f:
        data = yaml.safe_load(f)
    assert isinstance(data, dict)
    return data


def read_json(path: str) -> dict[str, Any]:
    return json.loads((ROOT / path).read_text(encoding='utf-8'))


def check_schema(schema_name: str, data: dict[str, Any]) -> None:
    version = 'v1.2' if schema_name == 'session' and data.get('schemaVersion') == '1.2.0' else ('v2' if schema_name == 'compiled-plan' else ('v1.1' if schema_name in ('session', 'template') and data.get('schemaVersion') == '1.1.0' else 'v1'))
    schema = read_json(f'schemas/{schema_name}.{version}.schema.json')
    Draft202012Validator.check_schema(schema)
    errors = sorted(Draft202012Validator(schema, format_checker=FormatChecker()).iter_errors(data), key=lambda e: list(map(str, e.path)))
    if errors:
        raise AssertionError(f'{schema_name}: ' + '; '.join(f'{list(e.path)}: {e.message}' for e in errors))


def load_fixture() -> dict[str, Any]:
    obj = read_yaml('examples/templates/composite-pyramid.yaml')
    check_schema('template', obj)
    return obj


def compile_synthetic_timeline(template: dict[str, Any]) -> dict[str, Any]:
    """Simple fixture reference compiler; NOT general block/engine semantics."""
    phases = []
    allids: list[str] = []
    cursor = 0
    for section in template['sections']:
        allids.append(section['sectionId'])
        for block in section['blocks']:
            allids.append(block['blockId'])
            sets: dict[str, dict] = {}
            exercise_by_id = {x['familyId']: x for x in (
                read_yaml('examples/exercises/bridge-band-family.yaml'),
                read_yaml('examples/exercises/plank-family.yaml'))}
            for occurrence in block['occurrences']:
                allids.append(occurrence['occurrenceId'])
                exercise = exercise_by_id[occurrence['exerciseId']]
                variations = {v['variationId'] for v in exercise['variations']}
                for setobj in occurrence['sets']:
                    assert setobj['setId'] not in sets, f'duplicate set ID {setobj["setId"]}'
                    assert setobj['variationId'] in variations, f'unknown variation {setobj["variationId"]}'
                    assert len({p['phaseId'] for p in setobj['phases']}) == len(setobj['phases'])
                    for phase in setobj['phases']:
                        assert phase['mode'] == 'timed', 'reference compiler only accepts timed phases'
                    sets[setobj['setId']] = setobj
                    allids.append(setobj['setId'])
            rep_coverage: list[tuple[str,int]] = []
            for step in block['order']:
                allids.append(step['stepId'])
                if step['kind'] == 'rest':
                    duration = step['durationMs']
                    phases.append({'stepId':step['stepId'],'startMs':cursor,'endMs':cursor+duration,'name':'rest'})
                    cursor += duration
                else:
                    sid = step['setId']
                    assert sid in sets, f'unknown set: {sid}'
                    rep = step['repetitionIndex']
                    assert 1 <= rep <= sets[sid]['repetitions']
                    rep_coverage.append((sid,rep))
                    for phase in sets[sid]['phases']:
                        duration = phase['durationMs']
                        phases.append({'stepId':step['stepId'],'phaseId':phase['phaseId'],'setId':sid,
                                       'repetitionIndex':rep,'startMs':cursor,'endMs':cursor+duration,'name':phase['name']})
                        cursor += duration
            expected_reps = {(sid,rep) for sid,s in sets.items() for rep in range(1,s['repetitions']+1)}
            assert set(rep_coverage) == expected_reps, f'missing/extra repetitions: {expected_reps ^ set(rep_coverage)}'
            assert len(rep_coverage) == len(set(rep_coverage)), 'duplicate planned repetitions'
    assert len(allids) == len(set(allids)), 'duplicate structural ID in fixture'
    return {'schemaVersion':template['schemaVersion'], 'templateId':template['templateId'],
            'revisionId':template['revisionId'], 'planFormatVersion':'1.0.0',
            'expectedDurationMs':cursor, 'phases':phases}


def check_requirements_traceability() -> int:
    requirements = (ROOT/'docs/product/requirements.md').read_text()
    ids = re.findall(r'\| (GA-(?:ORG|DOM|EXE|WEB|DATA|SEC|SHARE|OPS)-\d{3}) \|', requirements)
    assert ids and len(ids) == len(set(ids)), 'duplicate/missing requirements IDs'
    with (ROOT/'docs/product/traceability.csv').open(newline='') as f:
        records = list(csv.DictReader(f))
    tr_ids = [x['requirement_id'] for x in records]
    assert len(tr_ids) == len(set(tr_ids)), 'duplicate traceability IDs'
    assert set(ids) == set(tr_ids), f'traceability mismatch: {set(ids)^set(tr_ids)}'
    for r in records:
        assert r['milestone'] and r['evidence_status'] and r['test_reference']
    return len(ids)


def validate_all() -> tuple[int, int]:
    files = [
        ('session','examples/sessions/synthetic-runtime-v2-session.json','json'),
        ('session','examples/sessions/synthetic-runtime-v2-completed-session.json','json'),
        ('exercise','examples/exercises/bridge-band-family.yaml','yaml'),
        ('exercise','examples/exercises/plank-family.yaml','yaml'),
        ('template','examples/templates/composite-pyramid.yaml','yaml'),
        ('template','examples/templates/timed-runtime-v2.yaml','yaml'),
        ('compiled-plan','examples/expected/timed-runtime-v2.plan.json','json'),
        ('session','examples/sessions/synthetic-completed-session.json','json'),
        ('execution-plan','examples/expected/composite-pyramid.timeline.json','json'),
        ('storage-manifest','examples/storage/manifest.json','json'),
        ('journal-job','examples/storage/journal-job.json','json'),
        ('import-record','examples/storage/import-record.json','json'),
        ('training-season','examples/seasons/season-2026-2027.yaml','yaml'),
        ('training-season','examples/seasons/season-2027-2028.yaml','yaml'),
        ('training-program','examples/programs/corrective-training.yaml','yaml'),
        ('training-program','examples/programs/standalone-mobility.yaml','yaml'),
        ('season-participation','examples/participations/corrective-2026.yaml','yaml'),
        ('season-participation','examples/participations/corrective-2027.yaml','yaml'),
        ('promotion-proposal','examples/promotions/selective-promotion.json','json'),
        ('session','examples/sessions/synthetic-standalone-session.json','json'),
        ('session','examples/sessions/synthetic-ungrouped-session.json','json'),
        ('session','examples/sessions/synthetic-human-partial-session.json','json'),
        ('session','examples/sessions/synthetic-human-completed-session.json','json'),
    ]
    for schema, path, typ in files:
        obj = read_yaml(path) if typ=='yaml' else read_json(path)
        check_schema(schema,obj)
    from session_history_contract import validate_history
    for path in ('synthetic-human-partial-session.json', 'synthetic-human-completed-session.json'):
        validate_history(read_json('examples/sessions/' + path))
    from organization_contracts import validate_records, validate_start_context
    seasons = [read_yaml('examples/seasons/season-2026-2027.yaml'), read_yaml('examples/seasons/season-2027-2028.yaml')]
    programs = [read_yaml('examples/programs/corrective-training.yaml'), read_yaml('examples/programs/standalone-mobility.yaml')]
    participations = [read_yaml('examples/participations/corrective-2026.yaml'), read_yaml('examples/participations/corrective-2027.yaml')]
    validate_records(seasons, programs, participations, [load_fixture()])
    actual = read_json('examples/sessions/synthetic-completed-session.json')
    assert actual['effectiveConfigurationSnapshot']['templateRevisionId'] == actual['revisionId']
    validate_start_context(actual.get('organizationContext'),seasons,programs,participations)
    validate_start_context(read_json('examples/sessions/synthetic-standalone-session.json').get('organizationContext'),seasons,programs,participations)
    validate_start_context(read_json('examples/sessions/synthetic-ungrouped-session.json').get('organizationContext'),seasons,programs,participations)
    computed = compile_synthetic_timeline(load_fixture())
    expected = read_json('examples/expected/composite-pyramid.timeline.json')
    assert computed == expected, f'golden plan mismatch: expected={expected}, got={computed}'
    assert computed['expectedDurationMs'] == 50000
    assert sum(p['endMs']-p['startMs'] for p in computed['phases']) == 50000
    reqcount=check_requirements_traceability()
    return len(files),reqcount


if __name__=='__main__':
    file_count, req_count=validate_all()
    print(f'PASS: {file_count} schema-validated example files, deterministic 50000ms golden trace, {req_count} mapped requirements.')
