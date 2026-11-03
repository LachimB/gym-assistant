#!/usr/bin/env python3
"""Disposable-emulator UI regression: finalized personal-session selection and preview.

Uses the already published public synthetic Template. Refuses physical devices;
starts and saves one synthetic partial record, never clears data or authorizes Google.
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
    call('shell','am','start','-W','-n',PACKAGE+'/dev.gymassistant.m0.MainActivity')
    wait('Training Template library')
    assert not workout_rows()['unfinished_slot'], 'A previous synthetic test must finish first'
    click('Training Template library',scroll=True)
    tree=nodes()
    start=next((label for label in labels(tree) if label.startswith('Start ') and label!='Start Workout'),None)
    if start is None:
        for _ in range(12):
            call('shell','input','swipe',str(width//2),str(height*4//5),str(width//2),str(height//3),'250')
            start=next((label for label in labels(nodes()) if label.startswith('Start ') and label!='Start Workout'),None)
            if start:break
    assert start and 'Synthetic' in start, 'Only the published public synthetic Template is allowed'
    click(start,scroll=True);wait('Pause');click('Pause',scroll=True)
    click('End Workout…',scroll=True);click('Save partial workout');wait('Workout saved')
    before=workout_rows()
    assert not before['unfinished_slot']
    click('Send a finished session to personal Drive…',scroll=True);wait('Personal session delivery')
    choice=None
    for _ in range(12):
        choice=next((label for label in labels(nodes()) if 'Synthetic' in label and label.endswith('Partial')),None)
        if choice:break
        time.sleep(.2)
    assert choice, 'Expected finalized synthetic session choice'
    click(choice,scroll=True);wait('Choose another session')
    shown=labels(nodes())
    assert 'Not sent.' in shown
    assert any('bytes. Includes frozen workout instructions' in label for label in shown)
    send=[n for n in nodes() if n.get('clickable')=='true' and any(child.get('text')=='Send or retry selected session' for child in n.iter('node'))]
    assert send and all(n.get('enabled')=='false' for n in send), 'Default CI build must not authorize live personal delivery'
    assert workout_rows()==before, 'Selecting and reviewing must not enqueue or acknowledge anything'
    click('Close')
    print(json.dumps({'result':'PASS','syntheticPartialSessionSelected':True,'previewIncludesFrozenInstructionsAndBytes':True,'previouslyUnsentStatusVisible':True,'allRecordingAndDeliveryRowsUnchangedByPreview':True,'googleAuthorization':False,'appDataCleared':False}))

if __name__=='__main__':main()
