import json
import io
import tarfile
from pathlib import Path
import sqlite3
import tempfile
import shutil
import unittest
from tools.backup_android_database import BackupError,make_verified_snapshot,validate_database,validate_library_archive

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
    def test_copied_wal_is_recovered_only_in_the_private_working_copy(self):
        with tempfile.TemporaryDirectory() as temp:
            source=Path(temp)/'source.db';self.fixture(source)
            copy=Path(temp)/'copy.db';dest=Path(temp)/'backup.db'
            with sqlite3.connect(source) as active:
                active.execute('PRAGMA journal_mode=WAL')
                active.execute("UPDATE outbox SET status='retry'")
                active.commit()
                shutil.copyfile(source,copy)
                shutil.copyfile(Path(str(source)+'-wal'),Path(str(copy)+'-wal'))
                evidence=make_verified_snapshot(copy,dest,1)
                self.assertEqual(evidence['integrity'],'ok')
                with sqlite3.connect(dest) as backup:
                    self.assertEqual(backup.execute('SELECT status FROM outbox').fetchone()[0],'retry')
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

    def test_library_archive_is_verified_without_extraction_or_source_names_in_evidence(self):
        out=io.BytesIO()
        with tarfile.open(fileobj=out,mode='w:') as archive:
            item=tarfile.TarInfo('files/training-library/imported/private-source.yaml')
            content=b'private-looking fixture text';item.size=len(content)
            archive.addfile(item,io.BytesIO(content))
        evidence=validate_library_archive(out.getvalue())
        self.assertEqual(evidence['fileCount'],1)
        self.assertNotIn('private-',json.dumps(evidence))
        self.assertFalse(validate_library_archive(b'')['present'])
    def test_library_archive_rejects_traversal_and_links(self):
        for name,kind in [('files/training-library/../../outside',tarfile.REGTYPE),('files/training-library/link',tarfile.SYMTYPE)]:
            out=io.BytesIO()
            with tarfile.open(fileobj=out,mode='w:') as archive:
                item=tarfile.TarInfo(name);item.type=kind;archive.addfile(item)
            with self.assertRaises(BackupError):validate_library_archive(out.getvalue())
