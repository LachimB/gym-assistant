"""Provider-neutral M1 storage contract experiment; volatile test adapter only.

Not a production sync implementation, session store, durable outbox or Drive
adapter. Domain IDs are supplied separately from opaque adapter object handles.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Optional, Protocol


class StorageConflict(ValueError):
    """Preserve the local attempted content; remote content was not changed."""


class ObjectMissing(KeyError):
    pass


@dataclass(frozen=True)
class StoredObject:
    object_id: str
    revision: str
    content: bytes


@dataclass(frozen=True)
class ChangesPage:
    objects: tuple[StoredObject, ...]
    cursor: str
    has_more: bool


class StorageAdapter(Protocol):
    def get_object(self, object_id: str) -> StoredObject: ...

    def create_object(self, unique_key: str, content: bytes,
                      operation_id: str) -> StoredObject: ...

    def put_if_revision(self, object_id: str, expected_revision: str,
                        content: bytes, operation_id: str) -> StoredObject: ...

    def list_changes(self, cursor: Optional[str] = None,
                     limit: int = 100) -> ChangesPage: ...


class MemoryStorageAdapter:
    """Single-threaded reference fake. All state is lost on process exit.

    Replaying an operation returns its original receipt even if the object has
    advanced. Reusing an operation ID with different intent fails. Stable create
    keys deduplicate equal content, but never overwrite differing content.
    """
    def __init__(self):
        self._objects = {}
        self._keys = {}
        self._operations = {}
        self._changes = []

    def get_object(self, object_id: str) -> StoredObject:
        try:
            return self._objects[object_id]
        except KeyError:
            raise ObjectMissing(object_id) from None

    @staticmethod
    def _validate(content, operation_id):
        if not isinstance(content, bytes):
            raise TypeError('Storage content must be immutable bytes')
        if not isinstance(operation_id, str) or not operation_id:
            raise ValueError('A nonempty operation ID is required')

    def _replay(self, operation_id, intent):
        previous = self._operations.get(operation_id)
        if previous is None:
            return None
        if previous[0] != intent:
            raise StorageConflict('Operation ID was already used for a different intent')
        return previous[1]

    def _commit(self, object_id, content, operation_id, intent):
        receipt = StoredObject(object_id, f'revision_{len(self._changes) + 1}', content)
        self._objects[object_id] = receipt
        self._changes.append(receipt)
        self._operations[operation_id] = (intent, receipt)
        return receipt

    def create_object(self, unique_key: str, content: bytes,
                      operation_id: str) -> StoredObject:
        self._validate(content, operation_id)
        if not isinstance(unique_key, str) or not unique_key:
            raise ValueError('A nonempty unique key is required')
        intent = ('create', unique_key, content)
        replay = self._replay(operation_id, intent)
        if replay is not None:
            return replay
        if unique_key in self._keys:
            existing = self.get_object(self._keys[unique_key])
            if existing.content != content:
                raise StorageConflict('Unique key already exists with different content')
            self._operations[operation_id] = (intent, existing)
            return existing
        object_id = f'object_{len(self._objects) + 1}'
        receipt = self._commit(object_id, content, operation_id, intent)
        self._keys[unique_key] = object_id
        return receipt

    def put_if_revision(self, object_id: str, expected_revision: str,
                        content: bytes, operation_id: str) -> StoredObject:
        self._validate(content, operation_id)
        intent = ('put', object_id, expected_revision, content)
        replay = self._replay(operation_id, intent)
        if replay is not None:
            return replay
        existing = self.get_object(object_id)
        if existing.revision != expected_revision:
            raise StorageConflict('Remote revision changed; preserve the attempted branch')
        return self._commit(object_id, content, operation_id, intent)

    def list_changes(self, cursor: Optional[str] = None, limit: int = 100) -> ChangesPage:
        if type(limit) is not int or not 1 <= limit <= 1000:
            raise ValueError('Page limit must be an integer from 1 to 1000')
        if cursor is None:
            position = 0
        elif not isinstance(cursor, str) or not cursor.startswith('cursor_'):
            raise ValueError('Invalid change cursor')
        else:
            suffix = cursor[len('cursor_'):]
            if not suffix.isascii() or not suffix.isdigit():
                raise ValueError('Invalid change cursor')
            position = int(suffix)
        if position > len(self._changes):
            raise ValueError('Change cursor is beyond available changes')
        end = min(position + limit, len(self._changes))
        return ChangesPage(tuple(self._changes[position:end]), f'cursor_{end}',
                           end < len(self._changes))
