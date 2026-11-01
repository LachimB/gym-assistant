#!/usr/bin/env python3
"""Guard against accidentally tracking obvious private files. NOT a full secret scanner."""
from pathlib import Path
import subprocess
import sys
root=Path(__file__).resolve().parent.parent
r=subprocess.run(['git','ls-files','-z'],cwd=root,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL)
if r.returncode!=0:
    files=[str(p.relative_to(root)) for p in root.rglob('*') if p.is_file() and '.git' not in p.parts]
else:
    files=[x.decode('utf-8') for x in r.stdout.split(b'\x00') if x]
forbidden_dirs=('personal-data/','private-data/','raw-imports/','original-pdfs/')
forbidden_exts=('.pdf','.xlsx','.xls','.sqlite','.sqlite3','.pem','.p8','.p12','.jks','.keystore')
forbidden_basename={'credentials.json','secrets.json','local.properties','.env'}
wrong=[]
for f in files:
    low=f.lower()
    if low.startswith(forbidden_dirs) or low.endswith(forbidden_exts) or Path(low).name in forbidden_basename or Path(low).name.startswith('.env.') and Path(low).name!='.env.example':
        wrong.append(f)
if wrong:
    print('FAIL: forbidden possibly private files in source tree:',*wrong,sep='\n- ',file=sys.stderr)
    sys.exit(1)
print('PASS: no forbidden private file types / locations detected in tracked files. (Not a complete secret scan.)')
