"""Offline release safety and contract checks; no credentials, phone, provider calls or sends."""
from pathlib import Path
import json,os,subprocess,tempfile,unittest,zipfile,xml.etree.ElementTree as E
ROOT=Path(__file__).resolve().parents[1]
class ReleaseContractTests(unittest.TestCase):
 def test_only_official_provider_and_no_bridge(self):
  s=(ROOT/'src/ai/heyu/agent/AgentApi.java').read_text();self.assertIn('NativeAgent.request(',s);self.assertNotIn('bridge.json',s);self.assertNotIn('127.0.0.1',s);self.assertNotIn('HttpURLConnection)new URL',s)
  d=(ROOT/'src/ai/heyu/agent/DeepSeekClient.java').read_text();self.assertIn('https://api.deepseek.com',d);self.assertIn('setInstanceFollowRedirects(false)',d);self.assertNotIn('getErrorStream',d);self.assertIn('"deepseek-flash"',d)
 def test_secure_key_entry_and_storage(self):
  s=(ROOT/'src/ai/heyu/agent/ApiCredentials.java').read_text();self.assertIn('AndroidKeyStore',s);self.assertIn('AES/GCM/NoPadding',s);self.assertNotIn('Log.',s)
  ui=(ROOT/'src/ai/heyu/agent/ApiSettingsActivity.java').read_text();self.assertIn('FLAG_SECURE',ui);self.assertIn('TYPE_TEXT_VARIATION_PASSWORD',ui);self.assertIn('setSaveEnabled(false)',ui)
 def test_private_manifest_and_https_only(self):
  a='{http://schemas.android.com/apk/res/android}';r=E.parse(ROOT/'AndroidManifest.xml').getroot();app=r.find('application');self.assertEqual(app.get(a+'allowBackup'),'false');self.assertFalse(app.findall('receiver'));self.assertFalse(r.findall('instrumentation'))
  p={x.get(a+'name') for x in r.findall('uses-permission')};self.assertNotIn('android.permission.READ_CONTACTS',p);self.assertFalse(any('STORAGE' in x for x in p))
  n=E.parse(ROOT/'res/xml/network_security.xml').getroot();self.assertEqual(n.find('base-config').get('cleartextTrafficPermitted'),'false');self.assertFalse(n.findall('domain-config'))
 def test_native_numeric_and_local_file_execution(self):
  code=(ROOT/'src/ai/heyu/agent/NativeTaskRules.java').read_text();self.assertIn('RoundingMode.HALF_UP',code);self.assertIn('containsNumber(evidence,price)',code);self.assertIn('getValidOffsets',code)
  code=(ROOT/'src/ai/heyu/agent/NativeAgent.java').read_text();self.assertIn('NativeExports.document',code);self.assertIn('NativeExports.ppt',code);self.assertIn('NativeExports.xlsx',code);self.assertIn('"executedOn","Android"',code);self.assertNotIn('ProcessBuilder',code)
 def test_no_untrusted_executable_code(self):
  allcode='\n'.join(p.read_text() for p in (ROOT/'src').rglob('*.java'))
  for bad in ['Runtime.getRuntime().exec(', 'DexClassLoader(', 'evaluateJavascript(', 'setHostnameVerifier(', 'X509TrustManager']:
   self.assertNotIn(bad,allcode)
 def test_dependencies_are_checksum_pinned(self):
  for item in json.loads((ROOT/'android-dependencies.json').read_text()):
   self.assertTrue(item['url'].startswith('https://repo.maven.apache.org/'));self.assertRegex(item['sha256'],r'^[0-9a-f]{64}$')
 def test_prompts_contain_no_key(self):
  p=json.loads((ROOT/'assets/agent-prompts.json').read_text());self.assertIn('POLISH',p);self.assertIn('PLAN',p);self.assertNotIn('api_key',p)
 def test_apk_contains_no_service_configuration_or_test_backdoor(self):
  apk=ROOT/'output/heyu-agent-1.0.0.apk'
  if not apk.exists():self.skipTest('Run build.sh for artifact verification')
  with zipfile.ZipFile(apk) as z:
   self.assertNotIn('assets/bridge.json',z.namelist());self.assertNotIn('res/raw/heyu_lan_ca.pem',z.namelist())
   for name in z.namelist():
    if name.endswith('.dex'):
     for bad in [b'RouteProbeReceiver',b'DeliveryQaReceiver',b'seed-zachary-test']:
      self.assertNotIn(bad,z.read(name))
if __name__=='__main__':unittest.main()
