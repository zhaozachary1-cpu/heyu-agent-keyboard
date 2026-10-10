#!/usr/bin/env python3
"""Fetch pinned public Android PDF dependencies with SHA-256 verification."""
from pathlib import Path
import json,hashlib,urllib.request,zipfile,io,os
ROOT=Path(__file__).resolve().parents[1];MANIFEST=ROOT/'android-dependencies.json';OUT=ROOT/'build/deps';OUT.mkdir(parents=True,exist_ok=True)
for item in json.loads(MANIFEST.read_text()):
 path=OUT/item['name']
 if not path.exists():
  with urllib.request.urlopen(item['url'],timeout=60) as r:raw=r.read(20*1024*1024)
  if hashlib.sha256(raw).hexdigest()!=item['sha256']:raise SystemExit('Dependency checksum mismatch: '+item['name'])
  path.write_bytes(raw)
 raw=path.read_bytes()
 if hashlib.sha256(raw).hexdigest()!=item['sha256']:raise SystemExit('Cached dependency checksum mismatch: '+item['name'])
 if path.suffix=='.aar':
  with zipfile.ZipFile(io.BytesIO(raw)) as z:
   (OUT/(path.stem+'.jar')).write_bytes(z.read('classes.jar'))
   for n in z.namelist():
    if n.startswith('assets/') and not n.endswith('/'):
     parts=Path(n).parts
     if '..' in parts:raise SystemExit('Unsafe archive')
     dest=ROOT/'build/package-assets'/Path(*parts[1:]);dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(z.read(n))
 print('Verified',item['name'])
