import sys
import unittest
from dataclasses import FrozenInstanceError
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / 'tools'))
from storage_contracts import MemoryStorageAdapter, ObjectMissing, StorageConflict


class StorageContractsTests(unittest.TestCase):
    def setUp(self):
        self.store = MemoryStorageAdapter()
        self.first = self.store.create_object('session_fixture', b'original', 'create_1')

    def test_duplicate_create_retry_returns_same_object_without_extra_change(self):
        self.assertEqual(self.store.create_object('session_fixture', b'original', 'create_1'), self.first)
        self.assertEqual(self.store.create_object('session_fixture', b'original', 'create_2'), self.first)
        self.assertEqual(len(self.store.list_changes().objects), 1)

    def test_create_key_cannot_overwrite_other_content(self):
        with self.assertRaises(StorageConflict):
            self.store.create_object('session_fixture', b'other_branch', 'create_2')
        self.assertEqual(self.store.get_object(self.first.object_id), self.first)

    def test_stale_update_preserves_remote_and_callers_conflicting_branch(self):
        updated = self.store.put_if_revision(self.first.object_id, self.first.revision, b'new', 'put_1')
        branch = b'offline_other_device'
        with self.assertRaises(StorageConflict):
            self.store.put_if_revision(self.first.object_id, self.first.revision, branch, 'put_2')
        self.assertEqual(self.store.get_object(self.first.object_id), updated)
        self.assertEqual(branch, b'offline_other_device')
        self.assertEqual(len(self.store.list_changes().objects), 2)

    def test_lost_acknowledgement_replay_returns_original_receipt_after_later_update(self):
        updated = self.store.put_if_revision(self.first.object_id, self.first.revision, b'new', 'put_1')
        latest = self.store.put_if_revision(updated.object_id, updated.revision, b'latest', 'put_2')
        receipt = self.store.put_if_revision(self.first.object_id, self.first.revision, b'new', 'put_1')
        self.assertEqual(receipt, updated)
        self.assertEqual(self.store.get_object(self.first.object_id), latest)
        self.assertEqual(len(self.store.list_changes().objects), 3)

    def test_reused_operation_id_with_changed_intent_is_rejected(self):
        with self.assertRaises(StorageConflict):
            self.store.create_object('another_key', b'original', 'create_1')
        with self.assertRaises(StorageConflict):
            self.store.put_if_revision(self.first.object_id, self.first.revision, b'new', 'create_1')

    def test_paginated_changes_and_incremental_polling(self):
        second = self.store.create_object('another_key', b'new', 'create_2')
        page = self.store.list_changes(limit=1)
        self.assertEqual(page.objects, (self.first,))
        self.assertTrue(page.has_more)
        next_page = self.store.list_changes(page.cursor, limit=1)
        self.assertEqual(next_page.objects, (second,))
        self.assertFalse(next_page.has_more)
        empty = self.store.list_changes(next_page.cursor)
        self.assertEqual(empty.objects, ())
        third = self.store.create_object('third_key', b'third', 'create_3')
        self.assertEqual(self.store.list_changes(empty.cursor).objects, (third,))

    def test_invalid_cursor_limit_missing_object_and_mutable_content(self):
        for cursor in ('bad', 'cursor_-1', 'cursor_999', 'cursor_', 1):
            with self.subTest(cursor=cursor), self.assertRaises(ValueError):
                self.store.list_changes(cursor)
        for limit in (0, -1, 1001, True, 1.5):
            with self.subTest(limit=limit), self.assertRaises(ValueError):
                self.store.list_changes(limit=limit)
        with self.assertRaises(ObjectMissing):
            self.store.get_object('missing')
        with self.assertRaises(TypeError):
            self.store.create_object('mutable', bytearray(b'value'), 'mutable_op')
        with self.assertRaises(FrozenInstanceError):
            self.first.content = b'changed'

    def test_mock_is_explicitly_not_durable(self):
        with self.assertRaises(ObjectMissing):
            MemoryStorageAdapter().get_object(self.first.object_id)


if __name__ == '__main__':
    unittest.main()
