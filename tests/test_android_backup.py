import json
from pathlib import Path
import sqlite3
import tempfile
import unittest
from tools.backup_android_database import BackupError,make_verified_snapshot,validate_database

class PrivateBackupTest(unittest.TestCase):
    def fixture(self, path, status='Paused'):
        with sqlite3.connect(path) as db:
            db.executescript('PRAGMA user_version=1;CREATE TABLE session_starts(sessionId TEXT,snapshotJson TEXT);CREATE TABLE checkpoints(sessionId TEXT,revision INTEGER,checkpointJson TEXT);CREATE TABLE actions(sessionId TEXT,sequence INTEGER,checkpointJson TEXT);CREATE TABLE unfinished_slot(slot INTEGER,sessionId TEXT);CREATE TABLE outbox(sessionId TEXT,revision INTEGER,status TEXT);')
            checkpoint=json.dumps({'status':status,'observedRunMs':100})
            db.execute('INSERT INTO session_starts VALUES(?,?)',('synthetic','private-looking fixture text'))
            db.execute('INSERT INTO checkpoints VALUES(?,?,?)',('synthetic',1,checkpoint))
            db.execute('INSERT INTO actions VALUES(?,?,?)',('synthetic',1,checkpoint))
            db.execute('INSERT INTO unfinished_slot VALUES(1,?)',('synthetic',))
            db.execute('INSERT INTO outbox VALUES(?,?,?)',('synthetic',1,'pending'))
    def test_standalone_snapshot_preserves_rows_and_excludes_contents_from_evidence(self):
        with tempfile.TemporaryDirectory() as temp:
            source=Path(temp)/'source.db';dest=Path(temp)/'backup.db';self.fixture(source)
            evidence=make_verified_snapshot(source,dest,1)
            self.assertEqual(evidence['integrity'],'ok')
            self.assertEqual(evidence['tableCounts']['outbox'],1)
            self.assertNotIn('private-looking',json.dumps(evidence))
            self.assertEqual(source.stat().st_size,dest.stat().st_size)
    def test_running_workout_is_rejected_before_migration(self):
        with tempfile.TemporaryDirectory() as temp:
            source=Path(temp)/'source.db';self.fixture(source,'Running')
            with self.assertRaisesRegex(BackupError,'not paused'):validate_database(source,1)
    def test_bad_schema_and_incomplete_receipts_are_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            source=Path(temp)/'source.db';self.fixture(source)
            with self.assertRaisesRegex(BackupError,'schema'):validate_database(source,2)
            with sqlite3.connect(source) as db:db.execute('UPDATE checkpoints SET revision=2')
            with self.assertRaisesRegex(BackupError,'differ'):validate_database(source,1)
