"""CWA open data is the primary Taiwan source; its key only ever comes from a CI secret."""
import re, unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/'app/src/main/java/io/atmos/weather'

class CwaOpenDataSourceTests(unittest.TestCase):
    def test_key_is_injected_from_a_secret_and_never_committed(self):
        workflow=(ROOT.parent/'.github/workflows/build-apk.yml').read_text()
        self.assertIn('THINN_CWA_API_KEY: ${{ secrets.CWA_API_KEY }}',workflow)
        self.assertIn('-PTHINN_CWA_API_KEY=',workflow)
        self.assertIn('"THINN_CWA_API_KEY"',(ROOT/'app/build.gradle.kts').read_text())
        pattern=re.compile(r'CWA-[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}')
        for path in list(ROOT.rglob('*.kt'))+list(ROOT.rglob('*.kts'))+list(ROOT.rglob('*.json'))+list(ROOT.parent.glob('.github/**/*.yml')):
            if '/build/' in str(path):continue
            self.assertIsNone(pattern.search(path.read_text(errors='ignore')),str(path))
    def test_provider_order_cwa_then_open_meteo_then_met_norway(self):
        s=(SRC/'AppContainer.kt').read_text()
        a=s.index('CwaForecastProvider(');b=s.index('OpenMeteoProvider(meteo');c=s.index('MetNorwayProvider(http)')
        self.assertTrue(a<b<c)
        p=(SRC/'data/remote/CwaForecastProvider.kt').read_text()
        self.assertIn('中央氣象署授權碼無效或已停用',p)
        self.assertIn('throw java.io.IOException("中央氣象署連線失敗',p)
    def test_station_feed_follows_the_cwa_mirror_only(self):
        s=(SRC/'data/remote/CwaSurfaceObservations.kt').read_text()
        self.assertIn('O-A0003-001',s);self.assertIn('MIRROR_HOST="cwaopendata.s3.ap-northeast-1.amazonaws.com"',s)

if __name__=='__main__':unittest.main()
