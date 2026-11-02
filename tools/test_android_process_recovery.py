#!/usr/bin/env python3
"""Synthetic force-stop/relaunch acceptance test. Refuses physical devices.

Run after installing the debug APK on a disposable, unlocked emulator. Never
clears app data; both completed and discarded synthetic sessions are retained.
"""
import argparse
import json
import re
import subprocess
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

    def wait_for(text):
        deadline = time.monotonic() + 40
        while time.monotonic() < deadline:
            result = nodes()
            if any(text in node.get('text', '') for node in result):
                return result
            time.sleep(0.5)
        raise AssertionError(f'Expected recovery UI marker: {text}')

    def click(text):
        result = wait_for(text)
        matches = [node for node in result if node.get('text') == text]
        if len(matches) != 1:
            raise AssertionError('Expected a single matching control')
        values = list(map(int, re.findall(r'\d+', matches[0].get('bounds', ''))))
        if len(values) != 4:
            raise AssertionError('Control has no bounds')
        x1, y1, x2, y2 = values
        adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))

    def launch():
        adb('shell', 'am', 'start', '-W', '-n', COMPONENT)

    def kill():
        adb('shell', 'am', 'force-stop', PACKAGE)

    def counters(result):
        texts = '\n'.join(node.get('text', '') for node in result)
        match = re.search(r'Scheduled (\d+) ms · observed (\d+) ms', texts)
        if not match:
            raise AssertionError('Missing independent scheduled/observed counters')
        return tuple(map(int, match.groups()))

    launch()
    initial = nodes()
    if any('Resume saved workout' == node.get('text') for node in initial):
        click('Discard workout…')
        click('Discard')
        click('New Workout')
    click('Start Workout')
    time.sleep(1.2)
    kill()
    launch()
    first = counters(wait_for('Unfinished workout recovered'))
    if not 0 < first[1] < 50000:
        raise AssertionError('Expected saved partial progress after real process termination')
    time.sleep(1.2)
    if counters(nodes()) != first:
        raise AssertionError('Recovered workout advanced without explicit resume')
    click('Resume saved workout')
    time.sleep(1.2)
    kill()
    launch()
    second = counters(wait_for('Unfinished workout recovered'))
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
