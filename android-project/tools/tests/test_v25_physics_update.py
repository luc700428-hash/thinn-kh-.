"""Offline asset integrity, release-scope semantics and migration regression checks."""
from pathlib import Path
import hashlib,json,struct,unittest
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'app/src/main'
class PhysicsUpdateTests(unittest.TestCase):
    def test_offline_atlas_is_complete_real_vector_geometry(self):
        data=(BASE/'assets/maps/taiwan_counties.tkatlas').read_bytes()
        meta=json.loads((BASE/'assets/maps/taiwan_counties.json').read_text())
        self.assertEqual(hashlib.sha256(data).hexdigest(),meta['atlas_sha256'])
        self.assertEqual(data[:8],b'TKMAP001')
        offset=8
        def unpack(fmt):
            nonlocal offset
            value=struct.unpack_from(fmt,data,offset);offset+=struct.calcsize(fmt);return value
        count,=unpack('>i');self.assertEqual(count,3)
        totals=[]
        for level in range(count):
            tolerance,rings=unpack('>fi');total=0;all_points=[]
            for _ in range(rings):
                n,=unpack('>i');self.assertGreaterEqual(n,4);total+=n
                for __ in range(n):
                    x,y=unpack('>ff');all_points.append((x+118,30-y))
            totals.append(total)
            for lon,lat in ((121,24),(119.57,23.57),(118.38,24.44),(119.95,26.17)):
                self.assertTrue(any(abs(x-lon)<.35 and abs(y-lat)<.35 for x,y in all_points),(level,lon,lat))
        self.assertEqual(totals,meta['vertices']);self.assertGreater(totals[-1],80_000)
        self.assertEqual(offset,len(data))
        self.assertEqual(meta['license'],'政府資料開放授權條款第1版')
    def test_normal_signed_release_does_not_claim_full_milestone(self):
        w=(ROOT.parent/'.github/workflows/build-apk.yml').read_text()
        self.assertIn('inputs.publish_release == true && inputs.full_milestone == true',w)
        self.assertIn('verify_next_release_gate.py',w)
        self.assertIn('Official APK signature differs',w)
        self.assertIn('embedded-build-source-proof.json',w)
        gate=json.loads((ROOT/'tools/next_release_gate.json').read_text())
        self.assertFalse(gate['verified_full_release'])
    def test_every_range_control_uses_shared_optical_renderer(self):
        import re
        java=BASE/'java/io/atmos/weather'
        for p in java.rglob('*.kt'):
            text=p.read_text()
            self.assertNotRegex(text,r'import androidx\.compose\.material3\.Slider\s*(?:\n|$)',str(p))
            if re.search(r'\bSlider\(',text) and p.name!='GlassControls.kt':
                self.assertIn('import io.atmos.weather.ui.GlassSlider as Slider',text,str(p))
        storm=(java/'typhoon/StormTimelineOverlay.kt').read_text()
        self.assertIn('io.atmos.weather.ui.GlassSlider(value=progress',storm)
if __name__=='__main__':unittest.main()
