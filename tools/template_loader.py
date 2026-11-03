"""Local M1 contract loader; does not compile plans or publish revisions."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import yaml
from jsonschema import Draft202012Validator, FormatChecker
from yaml.events import AliasEvent

ROOT = Path(__file__).resolve().parent.parent
MAX_BYTES = 1_048_576
MAX_DEPTH = 64


class TemplateValidationError(ValueError):
    """Input cannot be used as a Training Template contract."""


class StrictLoader(yaml.SafeLoader):
    def compose_node(self, parent, index):
        if self.check_event(AliasEvent):
            raise TemplateValidationError('YAML aliases are unsupported; use explicit values')
        self.depth = getattr(self, 'depth', 0) + 1
        if self.depth > MAX_DEPTH:
            raise TemplateValidationError('YAML nesting limit exceeded')
        try:
            return super().compose_node(parent, index)
        finally:
            self.depth -= 1

    def construct_mapping(self, node, deep=False):
        result = {}
        for key_node, value_node in node.value:
            key = self.construct_object(key_node, deep=deep)
            if not isinstance(key, str):
                raise TemplateValidationError('YAML mapping keys must be strings')
            if key in result:
                raise TemplateValidationError('Duplicate YAML mapping key')
            result[key] = self.construct_object(value_node, deep=deep)
        return result


def read_document(path: Path) -> dict:
    """Read one bounded, safe YAML document without silently overwriting keys."""
    try:
        with path.open('rb') as stream:
            raw = stream.read(MAX_BYTES + 1)
        if len(raw) > MAX_BYTES:
            raise TemplateValidationError('YAML byte limit exceeded')
        data = yaml.load(raw.decode('utf-8'), Loader=StrictLoader)
    except (UnicodeError, yaml.YAMLError) as exc:
        # Parser messages may contain private source text. Keep diagnostics structural.
        mark = getattr(exc, 'problem_mark', None)
        location = f' at line {mark.line + 1}, column {mark.column + 1}' if mark else ''
        raise TemplateValidationError(f'Invalid YAML{location}') from None
    if not isinstance(data, dict):
        raise TemplateValidationError('Expected a YAML object')
    try:
        json.dumps(data, allow_nan=False)
    except (TypeError, ValueError):
        raise TemplateValidationError('YAML values must be JSON-compatible and finite') from None
    return data


def validate_schema(kind: str, data: dict) -> None:
    version = 'v1.1' if kind == 'template' and data.get('schemaVersion') == '1.1.0' else 'v1'
    schema = json.loads((ROOT / f'schemas/{kind}.{version}.schema.json').read_text())
    validator = Draft202012Validator(schema, format_checker=FormatChecker())
    for error in validator.iter_errors(data):
        location = '/' + '/'.join(str(part) for part in error.absolute_path)
        raise TemplateValidationError(f'{kind}: {error.validator} validation failed at {location}')


def load_template(template_path: Path, exercise_paths: list[Path]) -> dict:
    """Validate current draft schema, stable IDs, references and explicit rep coverage.

    Returns a fresh document. No repository, last-valid cache, session or cloud
    state is changed. Program overrides and version selection are separate work.
    """
    template = read_document(template_path)
    validate_schema('template', template)
    families = {}
    for path in exercise_paths:
        exercise = read_document(path)
        validate_schema('exercise', exercise)
        family = exercise['familyId']
        if family in families:
            raise TemplateValidationError('Duplicate exercise family ID')
        variations = [item['variationId'] for item in exercise['variations']]
        if len(variations) != len(set(variations)):
            raise TemplateValidationError('Duplicate variation ID within exercise family')
        families[family] = set(variations)

    structural_ids = set()

    def claim(identity):
        if identity in structural_ids:
            raise TemplateValidationError('Duplicate structural ID in Training Template')
        structural_ids.add(identity)

    for section in template['sections']:
        claim(section['sectionId'])
        for block in section['blocks']:
            claim(block['blockId'])
            sets = {}
            for occurrence in block['occurrences']:
                claim(occurrence['occurrenceId'])
                variations = families.get(occurrence['exerciseId'])
                if variations is None:
                    raise TemplateValidationError('Unresolved exercise family reference')
                for item in occurrence['sets']:
                    claim(item['setId'])
                    sets[item['setId']] = item
                    if item['variationId'] not in variations:
                        raise TemplateValidationError('Unresolved variation within exercise family')
                    phases = [phase['phaseId'] for phase in item['phases']]
                    if len(phases) != len(set(phases)):
                        raise TemplateValidationError('Duplicate phase ID within set')
            seen = set()
            for step in block['order']:
                claim(step['stepId'])
                if step['kind'] == 'rest':
                    continue
                item = sets.get(step['setId'])
                if item is None:
                    raise TemplateValidationError('Unresolved set reference within block')
                rep = step['repetitionIndex']
                if rep > item['repetitions']:
                    raise TemplateValidationError('Repetition index exceeds set repetitions')
                key = (step['setId'], rep)
                if key in seen:
                    raise TemplateValidationError('Duplicate planned repetition')
                seen.add(key)
            if len(seen) != sum(item['repetitions'] for item in sets.values()):
                raise TemplateValidationError('Missing planned repetitions')
    return template


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('template', type=Path)
    parser.add_argument('--exercise', type=Path, action='append', required=True)
    args = parser.parse_args()
    try:
        load_template(args.template, args.exercise)
    except (TemplateValidationError, OSError) as exc:
        print(f'FAIL: {exc}' if isinstance(exc, TemplateValidationError) else 'FAIL: unable to read local file')
        return 1
    print('PASS: Training Template draft contract and local exercise references validated')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
