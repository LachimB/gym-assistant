#!/usr/bin/env python3
"""Approved private local backup before migration. Never prints database contents.

No restore, clear, uninstall, network upload or automatic workout resume.
"""
import argparse
import hashlib
import io
import tarfile
from pathlib import PurePosixPath
import json
import os
from pathlib import Path
import shutil
import sqlite3
import subprocess
import tempfile
from datetime import datetime, timezone

PACKAGE = 'dev.gymassistant.m0.debug'
TABLES = ['session_starts', 'checkpoints', 'actions', 'unfinished_slot', 'outbox']
class BackupError(RuntimeError):
    pass

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def validate_database(path, expected_version, require_paused=True):
    # path is a disposable host-local copy, never the active phone DB/raw bundle.
    # SQLite may need to recover its copied hot journal before read validation.
    with sqlite3.connect(path) as db:
        if db.execute('PRAGMA integrity_check').fetchall() != [('ok',)]:
            raise BackupError('SQLite integrity check failed')
        if db.execute('PRAGMA foreign_key_check').fetchone() is not None:
            raise BackupError('SQLite foreign-key check failed')
        if db.execute('PRAGMA user_version').fetchone()[0] != expected_version:
            raise BackupError('Unexpected source schema version')
        tables = TABLES + (['drive_connections','session_deliveries'] if expected_version >= 2 else [])
        if expected_version >= 3: tables += ['template_candidates','template_revisions','template_heads','execution_plan_revisions']
        counts = {table:db.execute(f'SELECT COUNT(*) FROM {table}').fetchone()[0] for table in tables}
        if db.execute('SELECT sessionId FROM actions GROUP BY sessionId HAVING MIN(sequence)!=1 OR MAX(sequence)!=COUNT(*)').fetchone():
            raise BackupError('Incomplete committed receipt prefix')
        for revision, checkpoint, sequence, receipt in db.execute(
                'SELECT c.revision,c.checkpointJson,a.sequence,a.checkpointJson FROM checkpoints c '
                'LEFT JOIN actions a ON a.sessionId=c.sessionId AND a.sequence=(SELECT MAX(sequence) FROM actions WHERE sessionId=c.sessionId)'):
            if revision != sequence or json.loads(checkpoint) != json.loads(receipt):
                raise BackupError('Checkpoint and committed receipt differ')
        paused = True
        for (checkpoint,) in db.execute('SELECT c.checkpointJson FROM checkpoints c JOIN unfinished_slot u ON u.sessionId=c.sessionId'):
            if json.loads(checkpoint).get('status') not in ('Paused','RecoveredPaused','Completed'):
                paused = False
        if require_paused and not paused:
            raise BackupError('Unfinished workout is not paused; no force-stop or migration permitted')
        logical = hashlib.sha256()
        for table in tables:
            # Host-local verification only. Values never leave this process or enter output.
            rows = db.execute(f'SELECT * FROM {table} ORDER BY rowid').fetchall()
            logical.update(json.dumps([table,rows],ensure_ascii=False,separators=(',',':')).encode())
        return {'schemaVersion':expected_version,'tableCounts':counts,'pausedOrNoUnfinishedWorkout':paused,'logicalSha256':logical.hexdigest()}

def make_verified_snapshot(source, destination, expected_version):
    before = validate_database(source,expected_version)
    with sqlite3.connect(f'file:{source}?mode=ro',uri=True) as src, sqlite3.connect(destination) as dst:
        src.backup(dst)
        dst.execute('PRAGMA journal_mode=DELETE')
    after = validate_database(destination,expected_version)
    if before != after:
        raise BackupError('Standalone snapshot does not match source')
    os.chmod(destination,0o600)
    return {**after,'standaloneSha256':digest(destination),'integrity':'ok','foreignKeys':'ok'}

def validate_library_archive(content):
    """Inspect privately without extracting paths or printing source names/contents."""
    if not content:
        return {'present':False,'fileCount':0,'archiveSha256':hashlib.sha256(content).hexdigest()}
    files={}
    try:
        with tarfile.open(fileobj=io.BytesIO(content),mode='r:') as archive:
            for member in archive:
                path=PurePosixPath(member.name)
                if path.is_absolute() or '..' in path.parts or tuple(path.parts[:2]) != ('files','training-library'):
                    raise BackupError('Library backup contains an unexpected path')
                if member.isdir():continue
                if not member.isfile() or path.as_posix() in files:
                    raise BackupError('Library backup contains a link or duplicate file')
                stream=archive.extractfile(member)
                if stream is None:raise BackupError('Library backup entry is unreadable')
                with stream: files[path.as_posix()]=hashlib.sha256(stream.read()).hexdigest()
    except tarfile.TarError:
        raise BackupError('Library archive is invalid') from None
    logical=hashlib.sha256(json.dumps(files,sort_keys=True,separators=(',',':')).encode()).hexdigest()
    return {'present':True,'fileCount':len(files),'archiveSha256':hashlib.sha256(content).hexdigest(),'logicalSha256':logical}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb',default='adb')
    parser.add_argument('--serial',required=True)
    parser.add_argument('--output',required=True,type=Path)
    parser.add_argument('--schema-version',type=int,choices=[1,2,3],default=1)
    args=parser.parse_args()
    os.umask(0o077)
    def adb(*command, optional=False):
        result=subprocess.run([args.adb,'-s',args.serial,*command],capture_output=True,timeout=30)
        if result.returncode and not optional:
            raise BackupError('Device backup command failed; raw device output withheld')
        return None if result.returncode else result.stdout
    def capture(directory):
        path=directory/'workouts-v1.db'
        for suffix in ('','-wal','-journal'):
            content=adb('exec-out','run-as',PACKAGE,'cat','databases/workouts-v1.db'+suffix,optional=bool(suffix))
            if content is not None:
                Path(str(path)+suffix).write_bytes(content)
        latest=adb('exec-out','run-as',PACKAGE,'cat','databases/workouts-v1.db')
        if hashlib.sha256(latest).hexdigest() != digest(path):
            raise BackupError('Source checkpoint changed during capture')
        return path
    # Inspect a private copy first. Never stop an actively running workout.
    with tempfile.TemporaryDirectory(prefix='gym-backup-check-') as temp:
        source=capture(Path(temp))
        validate_database(source,args.schema_version)
    if args.output.exists():
        raise BackupError('Backup destination already exists; refusing overwrite')
    adb('shell','am','force-stop',PACKAGE)
    args.output.mkdir(parents=True,mode=0o700)
    raw=args.output/'raw'; raw.mkdir(mode=0o700)
    source=capture(raw)
    def capture_library():
        exists=adb('exec-out','run-as',PACKAGE,'ls','-d','files/training-library',optional=True)
        return b'' if exists is None else adb('exec-out','run-as',PACKAGE,'tar','-cf','-','files/training-library')
    library=capture_library()
    library_evidence=validate_library_archive(library)
    (args.output/'training-library.tar').write_bytes(library)
    repeated=capture_library()
    if validate_library_archive(repeated) != library_evidence:raise BackupError('Library files changed during capture')
    raw_hashes={p.name:digest(p) for p in raw.iterdir() if p.is_file()}
    # Verify in a disposable copy so SQLite cannot modify the retained raw bundle.
    with tempfile.TemporaryDirectory(prefix='gym-backup-verify-') as temp:
        working=Path(temp)
        for path in raw.iterdir(): shutil.copyfile(path,working/path.name)
        evidence=make_verified_snapshot(working/'workouts-v1.db',args.output/'database.sqlite',args.schema_version)
    if any(digest(raw/name)!=value for name,value in raw_hashes.items()):
        raise BackupError('Raw backup changed during verification')
    evidence.update({'capturedAt':datetime.now(timezone.utc).isoformat(),'package':PACKAGE,
                     'rawFileSha256':raw_hashes,'backupDirectory':str(args.output),'deviceStopped':True,
                     'libraryFiles':library_evidence,'contentsPrinted':False,'cloudUpload':False,'restorePerformed':False})
    (args.output/'manifest.json').write_text(json.dumps(evidence,indent=2)+'\n')
    print(json.dumps(evidence,indent=2))

if __name__=='__main__':
    try:
        main()
    except BackupError as failure:
        raise SystemExit('Backup not verified: '+str(failure)+'. No migration/restore/clear performed.') from None
    except sqlite3.Error as failure:
        raise SystemExit('Backup not verified: SQLite '+getattr(failure,'sqlite_errorname','error')+'. No migration/restore/clear performed.') from None
    except (subprocess.SubprocessError,OSError,ValueError,TypeError) as failure:
        raise SystemExit('Backup not verified: '+type(failure).__name__+'. No migration/restore/clear performed; preserve captured files.') from None
