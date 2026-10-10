#!/usr/bin/env python3
"""Scan Git's actual index and release APK. Never prints matched secret values."""
import hashlib,json,re,subprocess,sys,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
raw=subprocess.check_output(['git','ls-files','-z'],cwd=ROOT);names=[n for n in raw.decode().split('\0') if n]
if not names:raise SystemExit('Nothing tracked; stage the reviewed public files first')
allowed_dirs=('src/','res/','native/','third_party/','assets/','scripts/','public-tests/','docs/','.github/')
allowed_root={'README.md','README.en.md','LICENSE','NOTICE','SECURITY.md','PRIVACY.md','CONTRIBUTING.md','CHANGELOG.md','.gitignore','AndroidManifest.xml','android-dependencies.json','build.sh'}
forbidden_parts={'.runtime','.signing','evidence','backups','node_modules','qa','server','deploy','build','output'}
patterns=[re.compile(rb'\bsk-[A-Za-z0-9_-]{20,}\b'),re.compile(rb'gh[pousr]_[A-Za-z0-9]{20,}'),re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),re.compile(rb'AIza[A-Za-z0-9_-]{30,}')]
errors=[]
for name in names:
 if name not in allowed_root and not name.startswith(allowed_dirs):errors.append(name+': outside source allowlist')
 if set(Path(name).parts)&forbidden_parts:errors.append(name+': private/tool output path')
 data=subprocess.check_output(['git','show',':'+name],cwd=ROOT)
 if any(p.search(data) for p in patterns):errors.append(name+': potential secret pattern')
 if re.search(rb'/Users/[A-Za-z0-9_.-]+/',data):errors.append(name+': personal absolute path')
 if re.search(rb'192\.168\.[0-9]{1,3}\.[0-9]{1,3}',data):errors.append(name+': literal private-network address')
 if name.endswith(('keystore','.jks','.pem')):errors.append(name+': signing/certificate material')
apk=ROOT/'output/heyu-agent-1.0.0.apk'
if apk.exists():
 with zipfile.ZipFile(apk) as z:
  if 'assets/bridge.json' in z.namelist():errors.append('APK: legacy bridge configuration')
  for n in z.namelist():
   data=z.read(n)
   if any(p.search(data) for p in patterns):errors.append('APK '+n+': potential secret')
   if n.endswith('.dex') and any(v in data for v in [b'RouteProbeReceiver',b'DeliveryQaReceiver',b'seed-zachary-test']):errors.append('APK '+n+': QA/legacy payload')
if errors:
 print('\n'.join(errors));raise SystemExit(1)
print(json.dumps({'tracked_files':len(names),'index_checked':True,'apk_checked':apk.exists(),'result':'PASS'},indent=2))
