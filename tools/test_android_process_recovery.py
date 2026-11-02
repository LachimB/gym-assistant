#!/usr/bin/env python3
"""Synthetic force-stop/relaunch acceptance test. Refuses physical devices.

Run after installing the debug APK on a disposable, unlocked emulator. Never
clears app data; both completed and discarded synthetic sessions are retained.
"""
import argparse
from pathlib import Path
import json
import re
import subprocess
import sqlite3
import tempfile
import time
import xml.etree.ElementTree as ET

PACKAGE = 'dev.gymassistant.m0.debug'
COMPONENT = PACKAGE + '/dev.gymassistant.m0.MainActivity'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--adb', default='adb')
    args = parser.parse_args()

    def adb(*command):
        return subprocess.check_output([args.adb, '-s', args.serial, *command], text=True, timeout=30)

    if adb('shell', 'getprop', 'ro.kernel.qemu').strip() != '1':
        raise RuntimeError('This automated force-stop test is permitted only on an emulator')

    def nodes():
        adb('shell', 'uiautomator', 'dump', '/data/local/tmp/gym-recovery-ui.xml')
        raw = adb('exec-out', 'cat', '/data/local/tmp/gym-recovery-ui.xml')
        return list(ET.fromstring(raw).iter('node'))

    def wait_for_any(markers):
        deadline = time.monotonic() + 40
        while time.monotonic() < deadline:
            result = nodes()
            if any(text in node.get('text', '') for node in result for text in markers):
                return result
            time.sleep(0.5)
        print('Emulator visible texts:', [node.get('text') for node in result if node.get('text')][:20])
        print(adb('logcat', '-d', '-s', 'AndroidRuntime:E')[-6000:])
        raise AssertionError('Expected recovery UI marker was not found')

    def wait_for(text):
        return wait_for_any([text])

    dimensions = list(map(int, re.findall(r'\d+', adb('shell', 'wm', 'size'))))[-2:]
    width, height = dimensions

    def click(text):
        for _ in range(12):
            matches = [node for node in nodes() if node.get('text') == text]
            for node in matches:
                values = list(map(int, re.findall(r'\d+', node.get('bounds', ''))))
                if len(values) == 4:
                    x1, y1, x2, y2 = values
                    if x2 > x1 and y2 > y1 and 0 <= (y1+y2)//2 < height:
                        adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
                        return
            adb('shell', 'input', 'swipe', str(width//2), str(height*4//5),
                str(width//2), str(height//3), '250')
            time.sleep(0.3)
        raise AssertionError(f'Expected visible control: {text}')

    def saved_checkpoint():
        # Emulator-only synthetic data. Read a paused/stopped database and its WAL
        # into an ephemeral directory; never use the source DB as a writable file.
        with tempfile.TemporaryDirectory() as directory:
            database = Path(directory) / 'workouts.db'
            for suffix in ('', '-wal'):
                result = subprocess.run([args.adb, '-s', args.serial, 'exec-out', 'run-as', PACKAGE,
                                         'cat', 'databases/workouts-v1.db' + suffix],
                                        capture_output=True, timeout=30)
                if result.returncode == 0:
                    Path(str(database) + suffix).write_bytes(result.stdout)
                elif not suffix:
                    raise AssertionError('Expected committed local workout database')
            with sqlite3.connect(database) as connection:
                row = connection.execute(
                    'SELECT c.checkpointJson, s.snapshotJson FROM checkpoints c '
                    'JOIN session_starts s ON s.sessionId=c.sessionId '
                    'JOIN unfinished_slot u ON u.sessionId=c.sessionId WHERE u.slot=1').fetchone()
                if row is None:
                    return None
                return json.loads(row[0]), row[1]

    def launch():
        # Safe here because this entire test rejects physical devices above.
        adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
        adb('shell', 'wm', 'dismiss-keyguard')
        result = adb('shell', 'am', 'start', '-W', '-n', COMPONENT)
        if 'Error:' in result or 'Exception' in result:
            raise AssertionError('Unable to launch the installed debug activity')

    def kill():
        adb('shell', 'am', 'force-stop', PACKAGE)

    def counters(saved):
        checkpoint, _ = saved
        return tuple(checkpoint[key] for key in
                     ('scheduledElapsedMs', 'observedRunMs', 'pauseMs', 'unobservedGapMs'))

    launch()
    initial = wait_for_any(['Start Workout', 'Resume saved workout'])
    if any('Resume saved workout' == node.get('text') for node in initial):
        click('Discard workout…')
        click('Discard')
        click('New Workout')
    click('Start Workout')
    time.sleep(1.2)
    kill()
    before_restore = saved_checkpoint()
    launch()
    wait_for('Unfinished workout recovered')
    first_saved = saved_checkpoint()
    first = counters(first_saved)
    if counters(before_restore) != first or before_restore[1] != first_saved[1]:
        raise AssertionError('Restart changed committed progress or the frozen source')
    if not 0 < first[1] < 50000:
        raise AssertionError('Expected saved partial progress after real process termination')
    time.sleep(1.2)
    if counters(saved_checkpoint()) != first:
        raise AssertionError('Recovered workout advanced without explicit resume')
    click('Resume saved workout')
    time.sleep(1.2)
    kill()
    launch()
    wait_for('Unfinished workout recovered')
    second_saved = saved_checkpoint()
    second = counters(second_saved)
    if first_saved[1] != second_saved[1]:
        raise AssertionError('Frozen source snapshot changed after resume/restart')
    if second[1] <= first[1] or second[0] != second[1]:
        raise AssertionError('Explicit resumed progress was not retained accurately')
    click('Save partial workout…')
    click('Save partial workout')
    wait_for('Workout saved')
    kill()
    launch()
    wait_for('Start Workout')
    print(json.dumps({'result': 'PASS', 'firstRecoveredMs': first[1],
                      'secondRecoveredMs': second[1], 'automaticResume': False,
                      'appDataCleared': False}))


if __name__ == '__main__':
    main()
