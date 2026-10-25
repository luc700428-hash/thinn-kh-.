"""Exercise upload integrity/three-way merge, plus native service wiring contracts."""
from pathlib import Path
import hashlib,importlib.util,json,subprocess,tempfile,unittest
ROOT=Path(__file__).resolve().parents[3]
BASE=ROOT/'android-project/app/src/main/java/io/atmos/weather'
def module(name,file):
    spec=importlib.util.spec_from_file_location(name,file);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
verify=module('v25_verify',ROOT/'delivery-tools/verify_package.py')
apply=module('v25_apply',ROOT/'delivery-tools/apply_update.py')
def digest(data):return hashlib.sha256(data).hexdigest()
def git(cwd,*args):
    return subprocess.run(['git',*args],cwd=cwd,check=True,text=True,capture_output=True).stdout.strip()
class IntegrityTests(unittest.TestCase):
    def fixture(self,root,path='source/a.txt'):
        p=root/'source/a.txt';p.parent.mkdir();p.write_bytes(b'correct')
        (root/'PACKAGE_MANIFEST.json').write_text(json.dumps({'schema':1,'files':[{'path':path,'sha256':digest(b'correct')}]}))
    def test_valid_snapshot(self):
        with tempfile.TemporaryDirectory() as d:
            r=Path(d);self.fixture(r);self.assertEqual(verify.verify(r)['schema'],1)
    def test_corruption_is_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            r=Path(d);self.fixture(r);(r/'source/a.txt').write_text('wrong')
            with self.assertRaisesRegex(ValueError,'integrity'):verify.verify(r)
    def test_parent_traversal_is_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            r=Path(d);self.fixture(r,'../a.txt')
            with self.assertRaisesRegex(ValueError,'Unsafe'):verify.verify(r)
    def test_symlinks_are_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            r=Path(d);self.fixture(r);p=r/'source/a.txt';p.unlink();p.symlink_to('/etc/hosts')
            with self.assertRaisesRegex(ValueError,'Missing'):verify.verify(r)
class ApplyTests(unittest.TestCase):
    def fixture(self,r,conflict=False):
        upstream=r/'remote.git';git(r,'init','--bare','--initial-branch=main',str(upstream))
        work=r/'work';work.mkdir();git(work,'init','--initial-branch=main');git(work,'config','user.name','Local test');git(work,'config','user.email','test@example.invalid')
        lines=['line %02d base\n'%i for i in range(30)];(work/'app.txt').write_text(''.join(lines));git(work,'add','.');git(work,'commit','-m','base');base=git(work,'rev-parse','HEAD')
        changed=lines[:];changed[5]='line 05 package\n';(work/'app.txt').write_text(''.join(changed))
        package=r/'package';package.mkdir();(package/'UPDATE.patch').write_text(git(work,'diff','--binary','--full-index','HEAD')+'\n')
        (package/'PACKAGE_MANIFEST.json').write_text(json.dumps({'schema':1,'base_commit':base,'changes':[{'path':'app.txt','after_sha256':digest(''.join(changed).encode())}]}))
        git(work,'checkout','--','app.txt');newer=lines[:];newer[5 if conflict else 25]='upstream modification\n';(work/'app.txt').write_text(''.join(newer));git(work,'add','.');git(work,'commit','-m','independent upstream');git(work,'remote','add','origin',str(upstream));git(work,'push','origin','main')
        checkout=r/'checkout';git(r,'clone',str(upstream),str(checkout));git(checkout,'config','user.name','Local test');git(checkout,'config','user.email','test@example.invalid')
        return package,checkout,upstream
    def test_merge_preserves_newer_upstream_edits(self):
        with tempfile.TemporaryDirectory() as d:
            p,c,u=self.fixture(Path(d));apply.apply(p,c);s=(c/'app.txt').read_text();self.assertIn('line 05 package',s);self.assertIn('upstream modification',s)
    def test_reapply_is_idempotent_after_merged_commit(self):
        with tempfile.TemporaryDirectory() as d:
            p,c,u=self.fixture(Path(d));apply.apply(p,c);git(c,'commit','-m','package');before=(c/'app.txt').read_bytes();apply.apply(p,c);self.assertEqual(before,(c/'app.txt').read_bytes());self.assertEqual(git(c,'diff','--cached','--name-only'),'')
    def test_conflict_does_not_mutate_remote(self):
        with tempfile.TemporaryDirectory() as d:
            p,c,u=self.fixture(Path(d),True);before=git(u,'rev-parse','main')
            with self.assertRaises(subprocess.CalledProcessError):apply.apply(p,c)
            self.assertEqual(before,git(u,'rev-parse','main'))
class NativeDownloadContracts(unittest.TestCase):
    def read(self,p):return (BASE/p).read_text()
    def test_service_has_permission_type_cancel_and_timeout(self):
        s=self.read('update/OtaDownloadService.kt');m=(BASE.parents[3]/'AndroidManifest.xml').read_text()
        for token in ['FOREGROUND_SERVICE_DATA_SYNC','android:foregroundServiceType="dataSync"','.update.OtaDownloadService']:self.assertIn(token,m)
        for token in ['ServiceCompat.startForeground','START_REDELIVER_INTENT','onTimeout','installWhenReady=false','scope.cancel()']:self.assertIn(token,s)
        self.assertNotIn('startActivity(',s)
    def test_resume_and_integrity_remain_real(self):
        s=self.read('update/AppUpdater.kt')
        for token in ['"Range"','"If-Range"','response==206','output.fd.sync()','part.renameTo(target)','verifyPackage(context,release,target)','currentCerts != updateCerts','packageInfo.longVersionCode <= installed.longVersionCode','APK SHA-256 驗證失敗']:self.assertIn(token,s)
    def test_drag_is_attached_to_thumb(self):
        s=self.read('alarm/WeatherAlarmRingActivity.kt');thumb=s.index('.size(56.dp)');gesture=s.index('.pointerInput(maxDrag)')
        self.assertGreater(gesture,thumb);self.assertIn('dragPx >= maxDrag * .75f',s);self.assertIn('thresholdReached = true',s);self.assertIn('if(closing) return',s)
    def test_no_default_publication(self):
        w=(ROOT/'.github/workflows/build-apk.yml').read_text();u=(ROOT/'delivery-tools/UPLOAD.sh').read_text()
        self.assertIn('default: false',w);self.assertIn('verify_next_release_gate.py',w);self.assertIn('inputs.publish_release == true',w)
        self.assertIn('publish_release=false',u);self.assertNotIn('--force',u);self.assertIn('rev-parse --is-inside-work-tree',u)
if __name__=='__main__':unittest.main()
