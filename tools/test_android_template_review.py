#!/usr/bin/env python3
"""Disposable-emulator UI regression: selected import and visible publication footer.

Only the two checked-in synthetic files are used. Refuses physical devices; never
uninstalls, clears data, starts a workout, or performs Google authorization.
"""
import argparse
import json
from pathlib import Path
import re
import sqlite3
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET

PACKAGE='dev.gymassistant.m0.debug'
ROOT=Path(__file__).resolve().parent.parent

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb',default='adb');parser.add_argument('--serial',required=True)
    args=parser.parse_args()
    def call(*command):
        return subprocess.check_output([args.adb,'-s',args.serial,*command],timeout=30)
    assert call('shell','getprop','ro.kernel.qemu').strip()==b'1', 'Only disposable emulators are permitted'
    def nodes():
        for attempt in range(3):
            call('shell','uiautomator','dump','/data/local/tmp/gym-template-review.xml')
            try:return list(ET.fromstring(call('exec-out','cat','/data/local/tmp/gym-template-review.xml')).iter('node'))
            except ET.ParseError:
                if attempt==2:raise AssertionError('Incomplete UI observation') from None
    size=call('shell','wm','size').decode()
    width,height=map(int,re.findall(r'(\d+)x(\d+)',size)[-1])
    def labels(tree):return [n.get('text') or n.get('content-desc') or '' for n in tree]
    def visible(n):
        box=list(map(int,re.findall(r'\d+',n.get('bounds',''))))
        return len(box)==4 and 0<=box[1]<box[3]<=height and box[0]<box[2]
    def wait(text):
        for _ in range(12):
            tree=nodes()
            if text in labels(tree):return tree
            time.sleep(.2)
        raise AssertionError('Expected visible control: '+text)
    def click(text,long=False,scroll=False):
        for attempt in range(10 if scroll else 1):
            found=[n for n in nodes() if (n.get('text')==text or n.get('content-desc')==text) and visible(n)]
            if found:
                x1,y1,x2,y2=map(int,re.findall(r'\d+',found[-1].get('bounds')))
                x,y=str((x1+x2)//2),str((y1+y2)//2)
                if long:call('shell','input','swipe',x,y,x,y,'800')
                else:call('shell','input','tap',x,y)
                return
            if scroll:
                start,end=(height//3,height*4//5) if attempt<5 else (height*4//5,height//3)
                call('shell','input','swipe',str(width//2),str(start),str(width//2),str(end),'250')
        raise AssertionError('Expected visible control: '+text)
    def workout_rows():
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/'workouts.db'
            for suffix in ('','-wal','-journal'):
                result=subprocess.run([args.adb,'-s',args.serial,'shell','-T','run-as',PACKAGE,'cat','databases/workouts-v1.db'+suffix],capture_output=True,timeout=30)
                if not result.returncode:Path(str(path)+suffix).write_bytes(result.stdout)
                elif not suffix:raise AssertionError('No local recording database')
            with sqlite3.connect(path) as db:
                return {t:db.execute('SELECT * FROM '+t+' ORDER BY rowid').fetchall() for t in
                        ('session_starts','checkpoints','actions','unfinished_slot','outbox','drive_connections','session_deliveries','execution_plan_revisions','personal_session_deliveries')}
    device_names={call('shell','getprop','ro.product.model').decode().strip(),call('shell','settings','get','global','device_name').decode().strip()}
    folder='/sdcard/Download/GymRuntimeM1'
    call('shell','mkdir','-p',folder)
    for source,name in [('examples/templates/timed-runtime-v2.yaml','template.yaml'),('examples/exercises/bridge-band-family.yaml','exercise.yaml')]:
        call('push',str(ROOT/source),folder+'/'+name)
    call('shell','am','start','-W','-n',PACKAGE+'/dev.gymassistant.m0.MainActivity')
    wait('Training Template library')
    for _ in range(12):
        if 'Loading saved workout…' not in labels(nodes()):break
        time.sleep(.2)
    else:raise AssertionError('Workout did not finish loading')
    before=workout_rows()
    click('Training Template library',scroll=True);click('Import selected YAML files')
    tree=wait('Show roots')
    if not {'exercise.yaml','template.yaml'}.issubset(labels(tree)):
        click('Show roots')
        choices=labels(nodes())
        storage=next((s for s in choices if s.startswith('Android SDK built for ') or s in ('Internal storage','Internal shared storage') or s in device_names),None)
        assert storage, 'No local emulator document storage root'
        click(storage);click('Download');click('GymRuntimeM1')
    wait('exercise.yaml');click('exercise.yaml',long=True);click('template.yaml')
    select=next((s for s in labels(nodes()) if s.casefold() in ('select','open')),None)
    assert select, 'No multiple-document selection control'
    click(select)
    tree=wait('Review every phase before publishing.')
    shown=labels(tree)
    assert any(s.startswith('1. Quadruped hip lift') for s in shown), 'Expected the top of the complete preview'
    assert any('1:20' in s and '10 phases' in s for s in shown), 'Expected the complete 80-second public plan'
    footer=[n for n in tree if n.get('text')=='Publish reviewed revision' and visible(n)]
    assert footer, 'Publication must be visible without scrolling through the preview'
    click('Publish reviewed revision');wait('Training Template published locally.')
    assert workout_rows()==before, 'Review/publication must not alter a workout or delivery'
    click('Close')
    click('Send a finished session to personal Drive…',scroll=True)
    wait('Personal session delivery')
    assert any('PERSONAL M1 DEV' in s for s in labels(nodes()))
    send=[n for n in nodes() if n.get('clickable')=='true' and any(child.get('text')=='Send or retry selected session' for child in n.iter('node'))]
    assert send and all(n.get('enabled')=='false' for n in send), 'No unreviewed send must be enabled'
    assert workout_rows()==before, 'Personal history preview must not enqueue or acknowledge anything'
    click('Close')
    print(json.dumps({'result':'PASS','selectedSyntheticFiles':2,'completePreviewPhases':10,'publicationFooterVisibleWithoutScrolling':True,
        'explicitPublication':True,'existingWorkoutAndDeliveryRowsUnchanged':True,'workoutStarted':False,'personalDeliveryPreviewPreservesRows':True,'googleAuthorization':False,'appDataCleared':False}))

if __name__=='__main__':main()
